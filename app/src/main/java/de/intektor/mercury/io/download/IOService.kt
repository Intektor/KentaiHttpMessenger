package de.intektor.mercury.io.download

import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.action.reference.ActionDownloadReferenceFinished
import de.intektor.mercury.action.reference.ActionDownloadReferenceProgress
import de.intektor.mercury.action.reference.ActionUploadReferenceFinished
import de.intektor.mercury.action.reference.ActionUploadReferenceProgress
import de.intektor.mercury.android.*
import de.intektor.mercury.io.ChatMessageService
import de.intektor.mercury.io.AddressHolder
import de.intektor.mercury.io.HttpManager
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.task.getNextFreeNotificationId
import de.intektor.mercury.util.NOTIFICATION_CHANNEL_DOWNLOAD_MEDIA
import de.intektor.mercury_common.client_to_server.DownloadReferenceRequest
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.reference.FileType
import de.intektor.mercury_common.reference.UploadResponse
import de.intektor.mercury_common.util.copyFully
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.*
import java.security.Key
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import kotlin.concurrent.thread

class IOService : Service() {

    private val downloadQueue = LinkedBlockingQueue<IORequest>()
    private val uploadQueue = LinkedBlockingQueue<IORequest>()

    private val downloadStreams = mutableListOf<InputStream>()
    private val uploadStreams = mutableListOf<OutputStream>()

    private var currentDownloadNotificationId: Int? = null
    private var currentUploadNotificationId: Int? = null

    private val stopIOListener: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (notificationId, io) = ActionStopIO.getData(intent)

            NotificationManagerCompat.from(context).cancel(notificationId)

            accessDownloadStreams().forEach {
                try {
                    it.close()
                } catch (t: Throwable) {
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        registerReceiver(stopIOListener, ActionStopIO.getFilter())

        thread {
            while (true) {
                val request = downloadQueue.take()

                popUpdateIOReference(0, IO.DOWNLOAD)

                performDownload(request)
            }
        }

        thread {
            while (true) {
                val request = uploadQueue.take()

                popUpdateIOReference(0, IO.UPLOAD)

                performUpload(request)
            }
        }
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        when {
            ActionDownloadReference.isAction(intent) -> {
                val (referenceUuid, aesKey, initVector, fileType) = ActionDownloadReference.getData(intent)
                downloadReference(referenceUuid, aesKey, initVector, fileType)
            }
        }


        return super.onStartCommand(intent, flags, startId)
    }

    private fun downloadReference(reference: UUID, aesKey: Key, initVector: ByteArray, fileType: FileType) {
        downloadQueue += IORequest(reference, aesKey, initVector, fileType)

        mercuryClient().currentLoadingTable[reference] = 0.0
    }

    private fun popUpdateIOReference(progress: Int, io: IO) {
        val notificationID = (if (io == IO.DOWNLOAD) currentDownloadNotificationId else currentUploadNotificationId)
                ?: getNextFreeNotificationId(this)

        when (io) {
            IOService.IO.UPLOAD -> currentUploadNotificationId = notificationID
            IOService.IO.DOWNLOAD -> currentDownloadNotificationId = notificationID
        }

        val cancelAction = PendingIntent.getBroadcast(this, 0, ActionStopIO.createIntent(this, notificationID, io), 0)

        val downloadNotification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_DOWNLOAD_MEDIA)
                .setContentTitle(getString(if (io == IO.DOWNLOAD) R.string.notification_download_media_title else R.string.notification_upload_media_title))
                .setContentText(getString(if (io == IO.DOWNLOAD) R.string.notification_download_media_description else R.string.notification_upload_media_description, downloadQueue.size + 1))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, false)
                .addAction(R.drawable.ic_cancel_white_24dp, getString(if (io == IO.DOWNLOAD) R.string.notification_download_media_description_cancel else R.string.notification_upload_media_description_cancel), cancelAction)

        val notificationManager = NotificationManagerCompat.from(this)

        notificationManager.notify(notificationID, downloadNotification.build())
    }

    private fun performDownload(request: IORequest) {
        val gson = genGson()

        val requestBody = RequestBody.create(MediaType.parse("JSON"), gson.toJson(DownloadReferenceRequest(request.reference)))

        val httpRequest = Request.Builder()
                .url(AddressHolder.HTTP_ADDRESS + DownloadReferenceRequest.TARGET)
                .post(requestBody).build()

        val mercuryClient = mercuryClient()

        val referenceFile = ReferenceUtil.getFileForReference(this, request.reference)

        try {
            HttpManager.httpClient.newCall(httpRequest).execute().use { response ->
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    return
                }

                accessDownloadStreams() += body.byteStream()

                DataInputStream(body.byteStream()).use { input ->
                    val responseCode = DownloadReferenceRequest.Response.values()[input.readInt()]
                    val totalToReceive = input.readLong()

                    when (responseCode) {
                        DownloadReferenceRequest.Response.NOT_FOUND, DownloadReferenceRequest.Response.DELETED, DownloadReferenceRequest.Response.IN_PROGRESS -> {
                            ActionDownloadReferenceFinished.launch(this, request.reference, false)

                            mercuryClient.currentLoadingTable -= request.reference
                            return
                        }
                        DownloadReferenceRequest.Response.SUCCESS -> {
                            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                            cipher.init(Cipher.DECRYPT_MODE, request.aesKey, IvParameterSpec(request.initVector))

                            val responseInputStream = ResponseInputStream(input, totalToReceive) { progress ->
                                ActionDownloadReferenceProgress.launch(this, request.reference, progress)
                            }

                            BufferedInputStream(CipherInputStream(responseInputStream, cipher)).use { cipherIn ->
                                cipherIn.copyFully(referenceFile.outputStream(), 1024 * 1024)
                            }

                            mercuryClient.currentLoadingTable -= request.reference
                            ReferenceUtil.setFileTypeForReference(request.reference, request.fileType, mercuryClient.dataBase)

                            ActionDownloadReferenceFinished.launch(this, request.reference, true)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            ActionDownloadReferenceFinished.launch(this, request.reference, false)

            mercuryClient.currentLoadingTable -= request.reference

            referenceFile.delete()
        }
    }

    private fun performUpload(request: IORequest) {
        val mercuryClient = applicationContext as MercuryClient

        val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

        val key: Key = request.aesKey

        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = ChatMessageService.MEDIA_TYPE_OCTET_STREAM

            override fun writeTo(sink: BufferedSink) {
                val referenceUUID = request.reference

                val file = ReferenceUtil.getFileForReference(this@IOService, referenceUUID)
                try {
                    accessUploadStreams() += sink.outputStream()

                    DataOutputStream(sink.outputStream()).use { dataOut ->
                        dataOut.writeUTF(referenceUUID.toString())

                        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(request.initVector))


                        BufferedInputStream(file.inputStream()).use { input ->
                            val responseStream = ResponseOutputStream(BufferedOutputStream(CipherOutputStream(dataOut, cipher)), file.length()) { current ->
                                ActionUploadReferenceProgress.launch(this@IOService, referenceUUID, current)

                                mercuryClient.currentLoadingTable[referenceUUID] = current

                                popUpdateIOReference((current * 100).toInt(), IO.UPLOAD)
                            }
                            responseStream.use { output ->
                                input.copyFully(output, 1024 * 1024)
                            }
                        }
                    }
                    mercuryClient.currentLoadingTable -= referenceUUID
                } catch (t: Throwable) {
                    ActionUploadReferenceFinished.launch(this@IOService, request.reference, false)

                    mercuryClient.currentLoadingTable -= request.reference
                }
            }
        }

        val httpRequest = Request.Builder()
                .url(AddressHolder.HTTP_ADDRESS + "uploadReference")
                .post(requestBody)
                .build()

        try {
            HttpManager.httpClient.newCall(httpRequest).execute().use { response ->
                val uploadResponse = DataInputStream(response.body()!!.byteStream()).use { dataIn ->
                    UploadResponse.values()[dataIn.readInt()]
                }

                ReferenceUtil.setReferenceUploaded(mercuryClient.dataBase, request.reference, uploadResponse == UploadResponse.NOW_UPLOADED || uploadResponse == UploadResponse.ALREADY_UPLOADED)

                ActionUploadReferenceFinished.launch(this, request.reference, response.isSuccessful)
            }
        } catch (t: Throwable) {
            ActionUploadReferenceFinished.launch(this, request.reference, false)

            ReferenceUtil.setReferenceUploaded(mercuryClient.dataBase, request.reference, false)

        }

        mercuryClient.currentLoadingTable -= request.reference
    }

    @Synchronized
    private fun accessDownloadStreams() = downloadStreams

    @Synchronized
    private fun accessUploadStreams() = uploadStreams

    @Suppress("ArrayInDataClass")
    private data class IORequest(val reference: UUID, val aesKey: Key, val initVector: ByteArray, val fileType: FileType)

    enum class IO {
        UPLOAD,
        DOWNLOAD
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(stopIOListener)
    }

    object ActionStopIO {

        private const val ACTION = "de.intektor.mercury.ACTION_STOP_IO"

        private const val EXTRA_NOTIFICATION_ID: String = "de.intektor.mercury.EXTRA_NOTIFICATION_ID"
        private const val EXTRA_IO: String = "de.intektor.mercury.EXTRA_IO"

        fun isAction(intent: Intent) = intent.action == ACTION

        fun getFilter(): IntentFilter = IntentFilter(ACTION)

        fun createIntent(context: Context, notificationId: Int, io: IO) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, IOService::class.java))
                        .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                        .putEnumExtra(EXTRA_IO, io)

        fun launch(context: Context, notificationId: Int, io: IO) {
            context.sendBroadcast(createIntent(context, notificationId, io))
        }

        fun getData(intent: Intent): Holder {
            val notificationId: Int = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
            val io: IO = intent.getEnumExtra(EXTRA_IO)
            return Holder(notificationId, io)
        }

        data class Holder(val notificationId: Int, val io: IO)
    }

    object ActionDownloadReference {

        private const val ACTION = "de.intektor.mercury.ACTION_DOWNLOAD_REFERENCE"

        private const val EXTRA_REFERENCE_UUID: String = "de.intektor.mercury.EXTRA_REFERENCE_UUID"
        private const val EXTRA_AES_KEY: String = "de.intektor.mercury.EXTRA_AES_KEY"
        private const val EXTRA_INIT_VECTOR: String = "de.intektor.mercury.EXTRA_INIT_VECTOR"
        private const val EXTRA_FILE_TYPE: String = "de.intektor.mercury.EXTRA_FILE_TYPE"

        fun isAction(intent: Intent) = intent.action == ACTION

        fun getFilter(): IntentFilter = IntentFilter(ACTION)

        private fun createIntent(context: Context, referenceUuid: UUID, aesKey: Key, initVector: ByteArray, fileType: FileType) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, IOService::class.java))
                        .putExtra(EXTRA_REFERENCE_UUID, referenceUuid)
                        .putExtra(EXTRA_AES_KEY, aesKey)
                        .putExtra(EXTRA_INIT_VECTOR, initVector)
                        .putExtra(EXTRA_FILE_TYPE, fileType)

        fun launch(context: Context, referenceUuid: UUID, aesKey: Key, initVector: ByteArray, fileType: FileType) {
            context.startService(createIntent(context, referenceUuid, aesKey, initVector, fileType))
        }

        fun getData(intent: Intent): Holder {
            val referenceUuid: UUID = intent.getUUIDExtra(EXTRA_REFERENCE_UUID)
            val aesKey: Key = intent.getKeyExtra(EXTRA_AES_KEY)
            val initVector: ByteArray = intent.getByteArrayExtra(EXTRA_INIT_VECTOR)
            val fileType: FileType = intent.getFileTypeExtra(EXTRA_FILE_TYPE)
            return Holder(referenceUuid, aesKey, initVector, fileType)
        }

        data class Holder(val referenceUuid: UUID, val aesKey: Key, val initVector: ByteArray, val fileType: FileType)
    }

    object ActionUploadReference {

        private const val ACTION = "de.intektor.mercury.ACTION_UPLOAD_REFERENCE"

        private const val EXTRA_REFERENCE_UUID: String = "de.intektor.mercury.EXTRA_REFERENCE_UUID"
        private const val EXTRA_AES_KEY: String = "de.intektor.mercury.EXTRA_AES_KEY"
        private const val EXTRA_INIT_VECTOR: String = "de.intektor.mercury.EXTRA_INIT_VECTOR"
        private const val EXTRA_FILE_TYPE: String = "de.intektor.mercury.EXTRA_FILE_TYPE"

        fun isAction(intent: Intent) = intent.action == ACTION

        fun getFilter(): IntentFilter = IntentFilter(ACTION)

        private fun createIntent(context: Context, referenceUuid: UUID, aesKey: Key, initVector: ByteArray, fileType: FileType) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, IOService::class.java))
                        .putExtra(EXTRA_REFERENCE_UUID, referenceUuid)
                        .putExtra(EXTRA_AES_KEY, aesKey)
                        .putExtra(EXTRA_INIT_VECTOR, initVector)
                        .putExtra(EXTRA_FILE_TYPE, fileType)

        fun launch(context: Context, referenceUuid: UUID, aesKey: Key, initVector: ByteArray, fileType: FileType) {
            context.startService(createIntent(context, referenceUuid, aesKey, initVector, fileType))
        }

        fun getData(intent: Intent): Holder {
            val referenceUuid: UUID = intent.getUUIDExtra(EXTRA_REFERENCE_UUID)
            val aesKey: Key = intent.getKeyExtra(EXTRA_AES_KEY)
            val initVector: ByteArray = intent.getByteArrayExtra(EXTRA_INIT_VECTOR)
            val fileType: FileType = intent.getFileTypeExtra(EXTRA_FILE_TYPE)
            return Holder(referenceUuid, aesKey, initVector, fileType)
        }

        data class Holder(val referenceUuid: UUID, val aesKey: Key, val initVector: ByteArray, val fileType: FileType)
    }

    private class ResponseInputStream(inputStream: InputStream, private val totalToReceive: Long, private val progressCallback: (Double) -> Unit) : FilterInputStream(inputStream) {

        var bytesRead = 0L
        var prefRead = 0.0

        override fun read(): Int {
            bytesRead++
            update()
            return super.read()
        }

        override fun read(b: ByteArray?, off: Int, len: Int): Int {
            bytesRead += len
            update()
            return super.read(b, off, len)
        }

        private fun update() {
            val currentPercent = bytesRead.toDouble() / totalToReceive.toDouble()
            if (prefRead + 0.10 < currentPercent) {
                prefRead = currentPercent
                progressCallback(currentPercent)
            }
        }
    }

    private class ResponseOutputStream(outputStream: OutputStream, private val totalToSend: Long, private val onUpdateProgress: (Double) -> Unit) : FilterOutputStream(outputStream) {

        var bytesWritten = 0L
        var prefSent = 0.0

        override fun write(b: Int) {
            super.write(b)
            bytesWritten++
            update()
        }

        override fun write(b: ByteArray?, off: Int, len: Int) {
            super.write(b, off, len)
            bytesWritten += len
            update()
        }

        private fun update() {
            val currentPercent = bytesWritten.toDouble() / totalToSend.toDouble()
            if (prefSent + 0.1 < currentPercent) {
                prefSent = currentPercent
                onUpdateProgress.invoke(currentPercent)
            }
        }
    }
}