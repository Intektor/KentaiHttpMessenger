package de.intektor.kentai.kentai.firebase

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.AsyncTask
import android.os.IBinder
import android.os.Parcelable
import android.support.v4.app.NotificationCompat
import android.util.Log
import com.google.common.io.BaseEncoding
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.android.readMessageWrapper
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.nine_gag.NineGagType
import de.intektor.kentai.kentai.nine_gag.getNineGagFile
import de.intektor.kentai.kentai.references.UploadState
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.client_to_server.DownloadProfilePictureRequest
import de.intektor.kentai_http_common.client_to_server.SendChatMessageRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.reference.UploadResponse
import de.intektor.kentai_http_common.server_to_client.SendChatMessageResponse
import de.intektor.kentai_http_common.server_to_client.UploadProfilePictureResponse
import de.intektor.kentai_http_common.users.ProfilePictureType
import de.intektor.kentai_http_common.util.*
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.*
import java.security.Key
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.IvParameterSpec
import kotlin.concurrent.thread

/**
 * @author Intektor
 */
class SendService : Service() {

    private val chatMessageSendingQueue: LinkedBlockingQueue<PendingMessage> = LinkedBlockingQueue()

    private val referenceUploadQueue: LinkedBlockingQueue<ReferenceInfo> = LinkedBlockingQueue()

    @Volatile
    private var privateAuthKey: RSAPrivateKey? = null

    @Volatile
    private var publicAuthKey: RSAPublicKey? = null

    @Volatile
    private var privateMessageKey: RSAPrivateKey? = null

    @Volatile
    private lateinit var dataBase: SQLiteDatabase

    @Volatile
    private var profilePictureUploadStream: OutputStream? = null

    private val downloadStreams = ConcurrentHashMap<UUID, InputStream>()
    private val uploadStreams = ConcurrentHashMap<UUID, OutputStream>()


    companion object {
        val MEDIA_TYPE_OCTET_STREAM = MediaType.parse("application/octet-stream")
    }

    override fun onCreate() {
        super.onCreate()

        val kentaiClient = applicationContext as KentaiClient

        dataBase = kentaiClient.dataBase

        val userUUID = kentaiClient.userUUID

        val chatMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val amount = intent.getIntExtra("amount", 0)

                val list = mutableListOf<PendingMessage>()

                for (i in 0 until amount) {
                    val chatUUID = intent.getSerializableExtra("chatUUID$i") as UUID
                    val receiver = intent.getParcelableArrayListExtra<ChatReceiver>("receiver$i")
                    val wrapper = intent.readMessageWrapper(i)
                    list.add(PendingMessage(wrapper, chatUUID, receiver))
                }

                chatMessageSendingQueue.addAll(list)
            }
        }
        registerReceiver(chatMessageReceiver, IntentFilter("de.intektor.kentai.sendChatMessage"))

        val referenceMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val chatUUID = intent.getSerializableExtra("chatUUID") as UUID
                val referenceFile = File(intent.getStringExtra("referenceFile"))
                val fileType = FileType.values()[intent.getIntExtra("fileType", 0)]
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()

                referenceUploadQueue.add(buildReferenceInfo(chatUUID, fileType, referenceFile, referenceUUID, userUUID))
            }
        }
        registerReceiver(referenceMessageReceiver, IntentFilter("de.intektor.kentai.referenceUpload"))

        val connectionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val extras = intent.extras
                val info = extras.getParcelable<Parcelable>("networkInfo") as NetworkInfo
                val state = info.state

                if (state == NetworkInfo.State.CONNECTED) {
                    chatMessageSendingQueue.addAll(buildPendingMessages(userUUID))
                    referenceUploadQueue.addAll(buildReferencesToSend(userUUID))
                }
            }
        }
        registerReceiver(connectionReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        val abortUploadProfilePicture: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                profilePictureUploadStream?.close()
            }
        }
        registerReceiver(abortUploadProfilePicture, IntentFilter(ACTION_CANCEL_UPLOAD_PROFILE_PICTURE))

        val abortDownloadMediaReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val referenceUUID = intent.getSerializableExtra(KEY_REFERENCE_UUID) as UUID
                val inputStream = downloadStreams[referenceUUID]
                inputStream?.close()
            }
        }
        registerReceiver(abortDownloadMediaReceiver, IntentFilter(ACTION_CANCEL_DOWNLOAD_MEDIA))

        thread {
            while (true) {
                val pendingMessage = chatMessageSendingQueue.take()

                val list = mutableListOf<PendingMessage>(pendingMessage)

                if (chatMessageSendingQueue.isNotEmpty()) {
                    chatMessageSendingQueue.drainTo(list)
                }

                try {
                    sendPendingMessages(list, userUUID)
                } catch (t: Throwable) {
                    Log.e("ERROR", "", t)
                    //read all entries from pending messages
                }
            }
        }

        thread {
            while (true) {
                val toUpload = referenceUploadQueue.take()
                sendReferenceToServer(toUpload)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val kentaiClient = applicationContext as KentaiClient
        when (intent?.action) {
            ACTION_UPLOAD_PROFILE_PICTURE -> {
                uploadProfilePicture(intent.getParcelableExtra(KEY_PICTURE), applicationContext as KentaiClient)
            }
            ACTION_DOWNLOAD_PROFILE_PICTURE -> {
                val type = intent.getSerializableExtra(KEY_PROFILE_PICTURE_TYPE) as ProfilePictureType
                val userUUID = intent.getSerializableExtra(KEY_USER_UUID) as UUID
                downloadProfilePicture(userUUID, type)
            }
            ACTION_DOWNLOAD_NINE_GAG -> {
                val gagID = intent.getStringExtra(KEY_NINE_GAG_ID)
                val gagUUID = intent.getSerializableExtra(KEY_NINE_GAG_UUID) as UUID
                val chatUUID = intent.getSerializableExtra(KEY_CHAT_UUID) as UUID
                downloadNineGag(gagID, gagUUID, chatUUID)
            }
            ACTION_UPLOAD_REFERENCE -> {
                val chatUUID = intent.getSerializableExtra(KEY_CHAT_UUID) as UUID
                val referencePath = intent.getStringExtra(KEY_MEDIA_URL)
                val fileType = intent.getSerializableExtra(KEY_MEDIA_TYPE) as FileType
                val referenceUUID = intent.getSerializableExtra(KEY_REFERENCE_UUID) as UUID
                referenceUploadQueue.add(buildReferenceInfo(chatUUID, fileType, File(referencePath), referenceUUID, kentaiClient.userUUID))
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    fun buildPendingMessages(userUUID: UUID): List<PendingMessage> {
        val list = mutableListOf<PendingMessage>()
        dataBase.rawQuery("SELECT chat_uuid, message_uuid FROM pending_messages;", null).use { query ->
            while (query.moveToNext()) {
                val chatUUID = query.getString(0).toUUID()
                val messageUUID = query.getString(1).toUUID()
                val sendTo = readChatParticipants(dataBase, chatUUID)

                val wrapperList = readChatMessageWrappers(dataBase, "message_uuid = '$messageUUID'")
                if (wrapperList.isNotEmpty()) {
                    list.add(PendingMessage(wrapperList.first(), chatUUID, sendTo.filter { it.receiverUUID != userUUID }))
                }
            }
        }
        return list
    }

    fun buildReferencesToSend(userUUID: UUID): List<ReferenceInfo> {
        val list = mutableListOf<ReferenceInfo>()
        dataBase.rawQuery("SELECT chat_uuid, reference_uuid, file_type FROM reference_upload_table WHERE state != 1", arrayOf()).use { query ->
            while (query.moveToNext()) {
                val chatUUID = query.getString(0).toUUID()
                val referenceUUID = query.getString(1).toUUID()
                val fileType = FileType.values()[query.getInt(2)]
                val referenceFile = getReferenceFile(referenceUUID, fileType, filesDir, this@SendService)
                list.add(buildReferenceInfo(chatUUID, fileType, referenceFile, referenceUUID, userUUID))
            }
        }
        return list
    }

    private fun buildReferenceInfo(chatUUID: UUID, fileType: FileType, referenceFile: File, referenceUUID: UUID, userUUID: UUID): ReferenceInfo {
        var chatType: ChatType = ChatType.TWO_PEOPLE

        dataBase.rawQuery("SELECT type FROM chats WHERE chat_uuid = ?;", arrayOf(chatUUID.toString())).use { cursor ->
            cursor.moveToNext()
            chatType = ChatType.values()[cursor.getInt(0)]
        }

        val participants = readChatParticipants(dataBase, chatUUID)

        return when (chatType) {
            ChatType.TWO_PEOPLE -> {
                val sendTo = participants.first { it.receiverUUID != userUUID }
                ReferenceInfo(chatUUID, referenceUUID, sendTo.publicKey!!, referenceFile, fileType, ChatType.TWO_PEOPLE, participants.filter { it.isActive && it.receiverUUID != userUUID })
            }
            ChatType.GROUP -> {
                var groupKey: Key? = null
                dataBase.rawQuery("SELECT group_key FROM group_key_table WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { query ->
                    query.moveToNext()
                    groupKey = query.getString(0).toAESKey()
                }
                ReferenceInfo(chatUUID, referenceUUID, groupKey!!, referenceFile, fileType, ChatType.GROUP, participants.filter { it.isActive && it.receiverUUID != userUUID })
            }
            else -> throw NotImplementedError()
        }
    }

    private fun sendPendingMessages(list: MutableList<PendingMessage>, userUUID: UUID) {
        if (privateAuthKey == null) {
            privateAuthKey = readPrivateKey(DataInputStream(File(filesDir.path + "/keys/authKeyPrivate.key").inputStream()))
            publicAuthKey = readKey(DataInputStream(File(filesDir.path + "/keys/authKeyPublic.key").inputStream()))
            privateMessageKey = readPrivateKey(DataInputStream(File(filesDir.path + "/keys/encryptionKeyPrivate.key").inputStream()))
        }

        val gson = genGson()
        val sendingMap = mutableListOf<SendChatMessageRequest.SendingMessage>()

        for ((message, chatUUID, sendTo) in list) {
            val toProcess = message.copy(message = message.message.copy())
            val previewText = ""
            val registryId = ChatMessageRegistry.getID(toProcess.message.javaClass).toString()
            for (receiver in sendTo) {
                if (receiver.isActive) {
                    if (receiver.publicKey == null) {
                        receiver.publicKey = requestPublicKey(receiver.receiverUUID, dataBase)
                    }
                    val encryptedPreviewText = previewText.encryptRSA(receiver.publicKey!!)
                    val encryptedReceiverUUID = receiver.receiverUUID.toString().encryptRSA(privateAuthKey!!)
                    val sendingMessage = SendChatMessageRequest.SendingMessage(toProcess.message, userUUID, encryptedReceiverUUID, chatUUID, registryId, encryptedPreviewText)
                    sendingMessage.encrypt(privateMessageKey!!, receiver.publicKey!! as RSAPublicKey)
                    sendingMap.add(sendingMessage)
                }
            }
        }

        val response = httpPost(gson.toJson(SendChatMessageRequest(sendingMap)), SendChatMessageRequest.TARGET)
        val res = gson.fromJson(response, SendChatMessageResponse::class.java)
        if (res.id == 0) {
            val changeStatusIntent = Intent("de.intektor.kentai.messageStatusUpdate")
            changeStatusIntent.putExtra("amount", list.count { it.message.message.shouldBeStored() })
            for ((index, message) in list.withIndex()) {
                dataBase.compileStatement("DELETE FROM pending_messages WHERE message_uuid = ?;").use { statement ->
                    statement.bindString(1, message.message.message.id.toString())
                    statement.execute()
                }
                dataBase.compileStatement("INSERT INTO message_status_change (message_uuid, status, time) VALUES(?, ?, ?)").use { statement ->
                    statement.bindString(1, message.message.message.id.toString())
                    statement.bindLong(2, MessageStatus.SENT.ordinal.toLong())
                    statement.bindLong(3, System.currentTimeMillis())
                    statement.execute()
                }

                if (message.message.message.shouldBeStored()) {
                    changeStatusIntent.putExtra("messageUUID$index", message.message.message.id.toString())
                    changeStatusIntent.putExtra("chatUUID$index", message.chatUUID.toString())
                    changeStatusIntent.putExtra("status$index", MessageStatus.SENT.ordinal)
                    changeStatusIntent.putExtra("time$index", System.currentTimeMillis())
                }
            }
            sendOrderedBroadcast(changeStatusIntent, null)

            list.clear()
        }
    }

    private fun sendReferenceToServer(referenceInfo: ReferenceInfo) {
        val intent = Intent(ACTION_UPLOAD_REFERENCE_STARTED)
        intent.putExtra(KEY_REFERENCE_UUID, referenceInfo.referenceUUID)
        sendBroadcast(intent)

        val notManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val cancelIntent = Intent(ACTION_CANCEL_DOWNLOAD_MEDIA)
        cancelIntent.putExtra(KEY_REFERENCE_UUID, referenceInfo.referenceUUID)

        val cancelAction = PendingIntent.getBroadcast(this, 0, cancelIntent, 0)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_UPLOAD_MEDIA)
                .setContentTitle(getString(R.string.notification_upload_media_title))
                .setContentText(getString(R.string.notification_upload_media_description))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setProgress(100, 0, false)
                .addAction(R.drawable.ic_cancel_white_24dp, getString(R.string.notification_upload_profile_picture_cancel_upload), cancelAction)

        val sharedPreferences = getSharedPreferences(DisplayNotificationReceiver.NOTIFICATION_FILE, Context.MODE_PRIVATE)

        val notId = nextNotificationID(sharedPreferences)

        notManager.notify(notId, notification.build())

        val kentaiClient = applicationContext as KentaiClient

        val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")

        val key: Key

        key = if (referenceInfo.chatType == ChatType.TWO_PEOPLE) {
            val aes = generateAESKey()
            aes
        } else {
            referenceInfo.encryptionKey
        }

        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? = MEDIA_TYPE_OCTET_STREAM

            override fun writeTo(sink: BufferedSink) {
                try {
                    uploadStreams[referenceInfo.referenceUUID] = sink.outputStream()
                    DataOutputStream(sink.outputStream()).use { dataOut ->
                        dataOut.writeUTF(referenceInfo.referenceUUID.toString())

                        if (referenceInfo.chatType == ChatType.TWO_PEOPLE) {
                            dataOut.writeUTF(BaseEncoding.base64().encode(key.encoded).encryptRSA(referenceInfo.encryptionKey))
                        }

                        val iV = generateInitVector()
                        dataOut.writeInt(iV.size)
                        sink.outputStream().write(iV)

                        cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iV))

                        BufferedInputStream(referenceInfo.toUpload.inputStream()).use { input ->
                            val responseStream = ResponseOutputStream(BufferedOutputStream(CipherOutputStream(dataOut, cipher)), referenceInfo.toUpload.length(), { current ->
                                val i = Intent(ACTION_UPLOAD_REFERENCE_PROGRESS)
                                i.putExtra(KEY_REFERENCE_UUID, referenceInfo.referenceUUID)
                                i.putExtra(KEY_PROGRESS, current)
                                sendBroadcast(i)

                                kentaiClient.currentLoadingTable[referenceInfo.referenceUUID] = current

                                notification.setProgress(100, (current * 100).toInt(), false)
                                notManager.notify(notId, notification.build())
                            })
                            responseStream.use { output ->
                                input.copyFully(output, 1024 * 1024)
                            }
                        }
                    }
                    kentaiClient.currentLoadingTable -= referenceInfo.referenceUUID
                } catch (t: Throwable) {
                    notification.setProgress(0, 0, false)
                    notification.setSmallIcon(R.drawable.ic_error_outline_white_24dp)
                    notification.setOngoing(false)
                    notification.setContentText(kentaiClient.getString(R.string.notification_upload_media_description_failed))
                    notification.mActions.clear()
                    notManager.notify(notId, notification.build())
                }
            }
        }

        val request = Request.Builder()
                .url(httpAddress + "uploadReference")
                .post(requestBody)
                .build()

        try {
            httpClient.newCall(request).execute().use { response ->
                val uploadResponse = DataInputStream(response.body()!!.byteStream()).use { dataIn ->
                    UploadResponse.values()[dataIn.readInt()]
                }
                if (uploadResponse == UploadResponse.NOW_UPLOADED || uploadResponse == UploadResponse.ALREADY_UPLOADED) {
                    setReferenceState(dataBase, referenceInfo.chatUUID, referenceInfo.referenceUUID, referenceInfo.fileType, UploadState.FINISHED)
                    notManager.cancel(notId)
                } else {
                    notification.setContentText(getString(R.string.notification_upload_media_description_failed))
                    notification.setProgress(0, 0, false)
                    notManager.notify(notId, notification.build())
                }

                val i = Intent(ACTION_UPLOAD_REFERENCE_FINISHED)
                i.putExtra(KEY_REFERENCE_UUID, referenceInfo.referenceUUID)
                i.putExtra(KEY_SUCCESSFUL, response.isSuccessful)
                sendBroadcast(i)
            }
        } catch (t: Throwable) {
            //Clear everything from the queue so we start sending when we have an internet connection next time
            referenceUploadQueue.clear()

            val i = Intent(ACTION_UPLOAD_REFERENCE_FINISHED)
            i.putExtra(KEY_REFERENCE_UUID, referenceInfo.referenceUUID)
            i.putExtra(KEY_SUCCESSFUL, false)
            sendBroadcast(i)

            notification.setContentText(getString(R.string.notification_upload_media_description_failed))
            notification.setProgress(0, 0, false)
            notManager.notify(notId, notification.build())
        }
        kentaiClient.currentLoadingTable -= referenceInfo.referenceUUID
    }

    override fun onBind(p0: Intent?): IBinder? = null

    data class ReferenceInfo(val chatUUID: UUID, val referenceUUID: UUID, val encryptionKey: Key, val toUpload: File, val fileType: FileType, val chatType: ChatType, val sendTo: List<ChatReceiver>)

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
            if (prefSent + 0.01 < currentPercent) {
                prefSent = currentPercent
                onUpdateProgress.invoke(currentPercent)
            }
        }
    }

    private class ResponseInputStream(inputStream: InputStream, private val totalToReceive: Long, private val onUpdateProgress: (Double) -> Unit) : FilterInputStream(inputStream) {

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
            if (prefRead + 0.01 < currentPercent) {
                prefRead = currentPercent
                onUpdateProgress.invoke(currentPercent)
            }
        }
    }

    private fun uploadProfilePicture(data: Uri, kentaiClient: KentaiClient) {
        profilePictureUploadStream?.close()
        profilePictureUploadStream = null

        UploadProfilePictureTask(data, kentaiClient, getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager, contentResolver, { profilePictureUploadStream = it }).execute()
    }

    private class UploadProfilePictureTask(private val data: Uri, private val kentaiClient: KentaiClient,
                                           private val notManager: NotificationManager, private val contentResolver: ContentResolver,
                                           private val setUploading: (OutputStream?) -> Unit) : AsyncTask<Unit, Unit, Int>() {

        lateinit var notification: NotificationCompat.Builder

        override fun onPreExecute() {
            val cancelAction = PendingIntent.getBroadcast(kentaiClient, 0, Intent(ACTION_CANCEL_UPLOAD_PROFILE_PICTURE), 0)
            notification = NotificationCompat.Builder(kentaiClient, NOTIFICATION_CHANNEL_UPLOAD_PROFILE_PICTURE)
                    .setContentTitle(kentaiClient.getString(R.string.notification_upload_profile_picture_title))
                    .setContentText(kentaiClient.getString(R.string.notification_upload_profile_picture_text))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setProgress(100, 0, false)
                    .addAction(R.drawable.ic_cancel_white_24dp, kentaiClient.getString(R.string.notification_upload_profile_picture_cancel_upload), cancelAction)

            notManager.notify(NOTIFICATION_ID_UPLOAD_PROFILE_PICTURE, notification.build())
        }

        override fun doInBackground(vararg params: Unit?): Int {
            return try {
                val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(data))
                val requestBody = object : RequestBody() {
                    override fun contentType(): MediaType? = MediaType.parse("application/octet-stream")

                    override fun writeTo(sink: BufferedSink) {
                        val outputStream = sink.outputStream()
                        setUploading.invoke(outputStream)

                        val dataOut = DataOutputStream(outputStream)
                        val byteOut = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteOut)

                        dataOut.writeUUID(kentaiClient.userUUID)

                        val picture = byteOut.toByteArray()

                        val signer = Signature.getInstance("SHA1WithRSA")
                        signer.initSign(kentaiClient.privateAuthKey!! as PrivateKey)
                        signer.update(picture)
                        val signed = signer.sign()

                        dataOut.writeInt(signed.size)
                        dataOut.write(signed)

                        dataOut.writeInt(picture.size)

                        val response = DataOutputStream(ResponseOutputStream(outputStream, picture.size.toLong(), { progress ->
                            notification.setProgress(100, (progress * 100).toInt(), false)
                            notManager.notify(NOTIFICATION_ID_UPLOAD_PROFILE_PICTURE, notification.build())
                        }))
                        response.write(picture)
                    }
                }

                val request = Request.Builder()
                        .url(httpAddress + "uploadProfilePicture")
                        .post(requestBody)
                        .build()

                val res = httpClient.newCall(request).execute().use { response ->
                    val gson = genGson()
                    gson.fromJson(response.body()?.string(), UploadProfilePictureResponse::class.java).type
                }

                val newText = when (res) {
                    UploadProfilePictureResponse.Type.SUCCESS -> R.string.notification_upload_profile_picture_text_success
                    UploadProfilePictureResponse.Type.FAILED_UNKNOWN_USER -> R.string.notification_upload_profile_picture_text_failed
                    UploadProfilePictureResponse.Type.FAILED_VERIFY_SIGNATURE -> R.string.notification_upload_profile_picture_text_failed
                }

                if (res == UploadProfilePictureResponse.Type.SUCCESS) {
                    setProfilePicture(bitmap, kentaiClient.userUUID, kentaiClient, ProfilePictureType.NORMAL)
                }

                newText
            } catch (t: Throwable) {
                R.string.notification_upload_profile_picture_text_failed
            }
        }

        override fun onPostExecute(result: Int) {
            setUploading.invoke(null)

            Thread.sleep(500)

            if (result == R.string.notification_upload_profile_picture_text_failed) {
                notification.setProgress(0, 0, false)
                notification.setContentText(kentaiClient.getString(result))
                notification.setOngoing(false)
                notification.setSmallIcon(R.drawable.ic_file_upload_white_24dp)

                notification.mActions.clear()

                notManager.notify(NOTIFICATION_ID_UPLOAD_PROFILE_PICTURE, notification.build())
            } else {
                notManager.cancel(NOTIFICATION_ID_UPLOAD_PROFILE_PICTURE)

                kentaiClient.sendBroadcast(Intent(ACTION_PROFILE_PICTURE_UPLOADED))
            }
        }
    }

    private fun downloadProfilePicture(userUUID: UUID, type: ProfilePictureType) {
        getProfilePicture(userUUID, this, ProfilePictureType.NORMAL).delete()
        DownloadProfilePictureTask({ uU ->
            val broadcast = Intent(ACTION_PROFILE_PICTURE_UPDATED)
            broadcast.putExtra(KEY_USER_UUID, uU)
            sendBroadcast(broadcast)
        }, userUUID, type, applicationContext as KentaiClient).execute()
    }

    private class DownloadProfilePictureTask(val callback: (UUID) -> Unit, val userUUID: UUID, val type: ProfilePictureType, val kentaiClient: KentaiClient) : AsyncTask<Unit, Unit, Boolean>() {

        override fun doInBackground(vararg params: Unit): Boolean {
            return try {
                val gson = genGson()
                val requestBody = RequestBody.create(MediaType.parse("JSON"), gson.toJson(DownloadProfilePictureRequest(userUUID, type)))
                val request = Request.Builder()
                        .url(httpAddress + DownloadProfilePictureRequest.TARGET)
                        .post(requestBody)
                        .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        DataInputStream(response.body()!!.byteStream()).use { inputStream ->
                            getProfilePicture(userUUID, kentaiClient, type).outputStream().use { out ->
                                inputStream.copyFully(out)
                            }
                        }
                    }
                }
                true
            } catch (t: Throwable) {
                false
            }
        }

        override fun onPostExecute(result: Boolean) {
            if (result) callback.invoke(userUUID)
        }
    }

    private fun downloadNineGag(gagId: String, gagUUID: UUID, chatUUID: UUID) {
        val kentaiClient = applicationContext as KentaiClient

        thread {
            val notManager = kentaiClient.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val cancelIntent = Intent(ACTION_CANCEL_DOWNLOAD_MEDIA)
            cancelIntent.putExtra(KEY_REFERENCE_UUID, gagUUID)

            val cancelAction = PendingIntent.getBroadcast(kentaiClient, 0, cancelIntent, 0)

            val notification = NotificationCompat.Builder(kentaiClient, NOTIFICATION_CHANNEL_DOWNLOAD_MEDIA)
                    .setContentTitle(getString(R.string.notification_download_media_title))
                    .setContentText(getString(R.string.notification_download_media_description))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSmallIcon(android.R.drawable.stat_sys_download)
                    .setProgress(100, 0, false)
                    .addAction(R.drawable.ic_cancel_white_24dp, getString(R.string.notification_download_media_description_cancel), cancelAction)

            val notId = nextNotificationID(getSharedPreferences(DisplayNotificationReceiver.NOTIFICATION_FILE, Context.MODE_PRIVATE))

            notManager.notify(notId, notification.build())

            var worked = false

            a@ for (type in NineGagType.values()) {
                val result = downloadNineGagReference(type, gagId, gagUUID, notification, notManager, notId, kentaiClient, chatUUID)
                when (result) {
                    SendService.NineGagDownloadResponseType.FAILED -> {
                        break@a
                    }
                    SendService.NineGagDownloadResponseType.SUCCESS -> {
                        worked = true
                        break@a
                    }
                    SendService.NineGagDownloadResponseType.NOT_FOUND -> {
                    }
                }
            }

            if (worked) {
                notManager.cancel(notId)

                val updateBroadcast = Intent(ACTION_DOWNLOAD_REFERENCE_FINISHED)
                updateBroadcast.putExtra(KEY_REFERENCE_UUID, gagUUID)
                updateBroadcast.putExtra(KEY_SUCCESSFUL, true)
                kentaiClient.sendBroadcast(updateBroadcast)
            } else {
                notification.setSmallIcon(R.drawable.ic_error_outline_white_24dp)
                notification.setProgress(0, 0, false)
                notification.setOngoing(false)
                notification.setContentText(kentaiClient.getString(R.string.notification_upload_media_description_failed))
                notManager.notify(notId, notification.build())

                val updateBroadcast = Intent(ACTION_DOWNLOAD_REFERENCE_FINISHED)
                updateBroadcast.putExtra(KEY_REFERENCE_UUID, gagUUID)
                updateBroadcast.putExtra(KEY_SUCCESSFUL, false)
                kentaiClient.sendBroadcast(updateBroadcast)
            }
        }
    }

    private fun downloadNineGagReference(type: NineGagType, gagId: String, gagUUID: UUID, notification: NotificationCompat.Builder,
                                         notManager: NotificationManager, notId: Int, kentaiClient: KentaiClient, chatUUID: UUID): NineGagDownloadResponseType {

        val request = Request.Builder()
                .url("https://img-9gag-fun.9cache.com/photo/$gagId${type.extension}")
                .build()

        return httpClient.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                setReferenceState(dataBase, chatUUID, gagUUID, type.fileType, UploadState.IN_PROGRESS)
                downloadStreams[gagUUID] = response.body()!!.byteStream()
                try {
                    ResponseInputStream(response.body()!!.byteStream(), response.body()!!.contentLength(), { progress ->
                        notification.setProgress(100, (progress * 100).toInt(), false)
                        notManager.notify(notId, notification.build())

                        val updateBroadcast = Intent(ACTION_DOWNLOAD_REFERENCE_PROGRESS)
                        updateBroadcast.putExtra(KEY_REFERENCE_UUID, gagUUID)
                        updateBroadcast.putExtra(KEY_PROGRESS, (progress * 100).toInt())
                        kentaiClient.sendBroadcast(updateBroadcast)
                    }).use { inputStream ->
                        getNineGagFile(gagId, type, kentaiClient).outputStream().use { outputStream ->
                            inputStream.copyFully(outputStream)
                        }
                    }
                    setReferenceState(dataBase, chatUUID, gagUUID, type.fileType, UploadState.FINISHED)
                    NineGagDownloadResponseType.SUCCESS
                } catch (t: Throwable) {
                    setReferenceState(dataBase, chatUUID, gagUUID, type.fileType, UploadState.NOT_STARTED)
                    NineGagDownloadResponseType.FAILED
                }
            } else {
                NineGagDownloadResponseType.NOT_FOUND
            }
        }
    }

    private enum class NineGagDownloadResponseType {
        FAILED,
        SUCCESS,
        NOT_FOUND

    }
}
