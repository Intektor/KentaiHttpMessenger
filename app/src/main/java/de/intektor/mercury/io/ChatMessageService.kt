package de.intektor.mercury.io

import android.annotation.TargetApi
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.*
import android.os.AsyncTask
import android.os.Build
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.action.ActionMessageStatusChange
import de.intektor.mercury.action.chat.ActionInitChatFinished
import de.intektor.mercury.action.profpic.ActionProfilePictureUpdate
import de.intektor.mercury.action.profpic.ActionProfilePictureUploaded
import de.intektor.mercury.android.getUUIDExtra
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.*
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.util.*
import de.intektor.mercury_common.chat.ChatSerializer
import de.intektor.mercury_common.chat.MessageStatus
import de.intektor.mercury_common.client_to_server.DownloadProfilePictureRequest
import de.intektor.mercury_common.client_to_server.SendChatMessageRequest
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.server_to_client.SendChatMessageResponse
import de.intektor.mercury_common.server_to_client.UploadProfilePictureResponse
import de.intektor.mercury_common.users.ProfilePictureType
import de.intektor.mercury_common.util.*
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.*
import java.util.*
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * @author Intektor
 */
class ChatMessageService : Service() {

    private val chatMessageSendingQueue: LinkedBlockingQueue<PendingMessage> = LinkedBlockingQueue()

    @Volatile
    private lateinit var dataBase: SQLiteDatabase

    @Volatile
    private var profilePictureUploadStream: OutputStream? = null

    private val initializingChats = mutableSetOf<UUID>()

    companion object {
        val MEDIA_TYPE_OCTET_STREAM = MediaType.parse("application/octet-stream")

        private const val TAG = "ChatMessageService"
    }

    @TargetApi(21)
    private val networkCallback: NetworkCallback = NetworkCallback()

    private val networkListener: BroadcastReceiver = NetworkListener()

    override fun onCreate() {
        super.onCreate()

        val mercuryClient = applicationContext as MercuryClient

        dataBase = mercuryClient.dataBase

        val userUUID = ClientPreferences.getClientUUID(this)
//
//        val abortUploadProfilePicture: BroadcastReceiver = object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                profilePictureUploadStream?.close()
//            }
//        }
//
//        registerReceiver(abortUploadProfilePicture, IntentFilter(ACTION_CANCEL_UPLOAD_PROFILE_PICTURE))

        ConnectivityManager.CONNECTIVITY_ACTION

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= 21) {
            connectivityManager.registerNetworkCallback(NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build(), networkCallback)
        } else {
            registerReceiver(networkListener, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        }

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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Logger.warning(TAG, "Started service with a intent that is null")
            return super.onStartCommand(intent, flags, startId)
        }

        val mercuryClient = mercuryClient()
        when {
            ActionUploadProfilePicture.isAction(intent) -> {
                val (dataUri) = ActionUploadProfilePicture.getData(intent)
                uploadProfilePicture(dataUri, mercuryClient)
            }
            ActionDownloadProfilePicture.isAction(intent) -> {
                val (profilePictureType, userUUID) = ActionDownloadProfilePicture.getData(intent)
                downloadProfilePicture(userUUID, profilePictureType)
            }
            ActionDownloadNineGag.isAction(intent) -> {
                val (gagId, gagUUID) = ActionDownloadNineGag.getData(intent)
//                downloadNineGag(gagId, gagUUID)
            }
            ActionInitializeChat.isAction(intent) -> {
                val (userUUIDs, chatUUID) = ActionInitializeChat.getData(intent)
                initializeChat(userUUIDs, chatUUID)

            }
            ActionSendMessages.isAction(intent) -> {
                val (list) = ActionSendMessages.getData(intent)
                chatMessageSendingQueue.addAll(list)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    @TargetApi(21)
    private inner class NetworkCallback : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network?) {
            collectAndSendPendingMessages()
        }
    }

    private inner class NetworkListener : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val extras = intent.extras
            val info = extras.getParcelable<Parcelable>("networkInfo") as NetworkInfo
            val state = info.state

            if (state == NetworkInfo.State.CONNECTED) {
                collectAndSendPendingMessages()
            }
        }
    }

    /**
     * This method collects all messages currently saved in the pending messages table and tries to send them to the server
     */
    private fun collectAndSendPendingMessages() {
        val pendingMessageUUIDs = PendingMessageUtil.getQueueMessages(mercuryClient().dataBase)

        val query = "(${pendingMessageUUIDs.joinToString { "'$it'" }})"

        val pendingMessages = getChatMessages(this, mercuryClient().dataBase, "chat_message.message_uuid IN $query")
                .map { PendingMessage(it.message, it.chatMessageInfo.chatUUID, readChatParticipants(dataBase, it.chatMessageInfo.chatUUID)) }

        sendPendingMessages(pendingMessages.toMutableList(), ClientPreferences.getClientUUID(this))
    }

    private fun sendPendingMessages(list: MutableList<PendingMessage>, userUUID: UUID) {
        val gson = genGson()
        val sendingMap = mutableListOf<SendChatMessageRequest.SendingMessage>()

        val privateAuthKey = ClientPreferences.getPrivateAuthKey(this)
        val privateMessageKey = ClientPreferences.getPrivateMessageKey(this)

        val signatureAuth = signUserUUID(userUUID, privateAuthKey)

        messages@ for ((message, chatUUID, sendTo) in list) {
            for (receiver in sendTo) {
                if (receiver.isActive) {
                    val key = receiver.publicKey
                            ?: throw IllegalStateException("public key is null")

                    val aesKey = generateAESKey()
                    val initVector = generateInitVector()

                    val messageJson = ChatSerializer.serializeChatMessage(message.messageCore, message.messageData, aesKey, initVector)

                    val signatureMessage = sign(messageJson, privateMessageKey)

                    val sendingMessage =
                            SendChatMessageRequest.SendingMessage(messageJson,
                                    message.messageCore.messageUUID,
                                    receiver.receiverUUID,
                                    chatUUID.toString().encryptRSA(key),
                                    aesKey.encoded.base64().encryptRSA(key),
                                    initVector.base64().encryptRSA(key),
                                    signatureMessage)
                    sendingMap.add(sendingMessage)
                }
            }
        }

        try {
            val response = HttpManager.post(gson.toJson(SendChatMessageRequest(sendingMap, userUUID, signatureAuth)), SendChatMessageRequest.TARGET)
            val res = gson.fromJson(response, SendChatMessageResponse::class.java)
            if (res.id == 0) {
                for (message in list) {
                    updateMessageStatus(dataBase, message.message.messageCore.messageUUID, MessageStatus.SENT, System.currentTimeMillis())

                    PendingMessageUtil.removeMessage(dataBase, message.message.messageCore.messageUUID)

                    ActionMessageStatusChange.launch(this, message.chatUUID, message.message.messageCore.messageUUID, MessageStatus.SENT)
                }
                list.clear()
            }
        } catch (t: Throwable) {
            Logger.error(TAG, "Error while trying to send chat message", t)
        }
    }

    override fun onBind(p0: Intent?): IBinder? = null

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

    private fun uploadProfilePicture(data: Uri, mercuryClient: MercuryClient) {
        profilePictureUploadStream?.close()
        profilePictureUploadStream = null

        UploadProfilePictureTask(data, mercuryClient, getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager, contentResolver) { profilePictureUploadStream = it }.execute()
    }

    private class UploadProfilePictureTask(private val data: Uri, private val mercuryClient: MercuryClient,
                                           private val notManager: NotificationManager, private val contentResolver: ContentResolver,
                                           private val setUploading: (OutputStream?) -> Unit) : AsyncTask<Unit, Unit, Int>() {

        lateinit var notification: NotificationCompat.Builder

        override fun onPreExecute() {
            val cancelAction = PendingIntent.getBroadcast(mercuryClient, 0, Intent(ACTION_CANCEL_UPLOAD_PROFILE_PICTURE), 0)
            notification = NotificationCompat.Builder(mercuryClient, NOTIFICATION_CHANNEL_UPLOAD_PROFILE_PICTURE)
                    .setContentTitle(mercuryClient.getString(R.string.notification_upload_profile_picture_title))
                    .setContentText(mercuryClient.getString(R.string.notification_upload_profile_picture_text))
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setSmallIcon(android.R.drawable.stat_sys_upload)
                    .setProgress(100, 0, false)
                    .addAction(R.drawable.ic_cancel_white_24dp, mercuryClient.getString(R.string.notification_upload_profile_picture_cancel_upload), cancelAction)

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

                        dataOut.writeUUID(ClientPreferences.getClientUUID(mercuryClient))

                        val picture = byteOut.toByteArray()

                        val signed = sign(picture, ClientPreferences.getPrivateAuthKey(mercuryClient))

                        dataOut.writeInt(signed.size)
                        dataOut.write(signed)

                        dataOut.writeInt(picture.size)

                        val response = DataOutputStream(ResponseOutputStream(outputStream, picture.size.toLong()) { progress ->
                            notification.setProgress(100, (progress * 100).toInt(), false)
                            notManager.notify(NOTIFICATION_ID_UPLOAD_PROFILE_PICTURE, notification.build())
                        })
                        response.write(picture)
                    }
                }

                val request = Request.Builder()
                        .url(AddressHolder.HTTP_ADDRESS + "uploadProfilePicture")
                        .post(requestBody)
                        .build()

                val res = HttpManager.httpClient.newCall(request).execute().use { response ->
                    val gson = genGson()
                    gson.fromJson(response.body()?.string(), UploadProfilePictureResponse::class.java).type
                }

                val newText = when (res) {
                    UploadProfilePictureResponse.Type.SUCCESS -> R.string.notification_upload_profile_picture_text_success
                    UploadProfilePictureResponse.Type.FAILED_UNKNOWN_USER -> R.string.notification_upload_profile_picture_text_failed
                    UploadProfilePictureResponse.Type.FAILED_VERIFY_SIGNATURE -> R.string.notification_upload_profile_picture_text_failed
                }

                if (res == UploadProfilePictureResponse.Type.SUCCESS) {
                    ProfilePictureUtil.setProfilePicture(bitmap, ClientPreferences.getClientUUID(mercuryClient), mercuryClient, ProfilePictureType.NORMAL)
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
                notification.setContentText(mercuryClient.getString(result))
                notification.setOngoing(false)
                notification.setSmallIcon(R.drawable.ic_file_upload_white_24dp)

                notification.mActions.clear()

                notManager.notify(NOTIFICATION_ID_UPLOAD_PROFILE_PICTURE, notification.build())
            } else {
                notManager.cancel(NOTIFICATION_ID_UPLOAD_PROFILE_PICTURE)

                ActionProfilePictureUploaded.launch(mercuryClient)
            }
        }
    }

    private fun downloadProfilePicture(userUUID: UUID, type: ProfilePictureType) {
        DownloadProfilePictureTask({ uU ->
            ProfilePictureUtil.getProfilePicture(userUUID, this, ProfilePictureType.NORMAL).delete()

            ActionProfilePictureUpdate.launch(this, uU)
        }, userUUID, type, applicationContext as MercuryClient).execute()
    }

    private class DownloadProfilePictureTask(val callback: (UUID) -> Unit, val userUUID: UUID, val type: ProfilePictureType, val mercuryClient: MercuryClient) : AsyncTask<Unit, Unit, Boolean>() {

        override fun doInBackground(vararg params: Unit): Boolean {
            return try {
                val gson = genGson()
                val requestBody = RequestBody.create(MediaType.parse("JSON"), gson.toJson(DownloadProfilePictureRequest(userUUID, type)))
                val request = Request.Builder()
                        .url(AddressHolder.HTTP_ADDRESS + DownloadProfilePictureRequest.TARGET)
                        .post(requestBody)
                        .build()

                HttpManager.httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        DataInputStream(response.body()!!.byteStream()).use { inputStream ->
                            ProfilePictureUtil.getProfilePicture(userUUID, mercuryClient, type).outputStream().use { out ->
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

    private fun initializeChat(users: List<UUID>, chatUUID: UUID) {
        if (initializingChats.any { it == chatUUID }) return

        initializingChats += chatUUID

        val notManager = NotificationManagerCompat.from(this)

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_MISC)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(getString(R.string.notification_initialize_chat_title))
                .setContentText(getString(R.string.notification_initialize_chat_title_description))
                .setProgress(0, 100, true)
                .setOngoing(true)

        notManager.notify(KEY_NOTIFICATION_INITIALZE_CHAT_ID, notification.build())

        thread {
            val successful = try {
                requestUsers(users, dataBase)
                notManager.cancel(KEY_NOTIFICATION_INITIALZE_CHAT_ID)
                true
            } catch (t: Throwable) {
                notification.setContentText(getString(R.string.notification_initialize_chat_title_description_failed))
                notification.setOngoing(false)
                notification.setSmallIcon(R.drawable.ic_file_download_white_24dp)
                notification.setProgress(0, 0, false)
                notManager.notify(KEY_NOTIFICATION_INITIALZE_CHAT_ID, notification.build())
                false
            }
            ActionInitChatFinished.launch(this@ChatMessageService, chatUUID, successful)

            initializingChats -= chatUUID
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= 21) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } else {
            unregisterReceiver(networkListener)
        }
    }

    object ActionUploadProfilePicture {

        private const val ACTION = "de.intektor.mercury.ACTION_UPLOAD_PROFILE_PICTURE"

        private const val EXTRA_DATA_URI = "de.intektor.mercury.EXTRA_DATA_URI"

        fun isAction(intent: Intent) = intent.action == ACTION

        private fun createIntent(context: Context, dataUri: Uri) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, ChatMessageService::class.java))
                        .putExtra(EXTRA_DATA_URI, dataUri)

        fun launch(context: Context, dataUri: Uri) {
            context.startService(createIntent(context, dataUri))
        }

        fun getData(intent: Intent): Holder {
            val dataUri = intent.getParcelableExtra<Uri>(EXTRA_DATA_URI)
            return Holder(dataUri)
        }

        data class Holder(val dataUri: Uri)
    }

    object ActionDownloadProfilePicture {

        private const val ACTION = "de.intektor.mercury.ACTION_DOWNLOAD_PROFILE_PICTURE"

        private const val EXTRA_PROFILE_PICTURE_TYPE: String = "de.intektor.mercury.EXTRA_PROFILE_PICTURE_TYPE"
        private const val EXTRA_USER_UUID: String = "de.intektor.mercury.EXTRA_USER_UUID"

        fun isAction(intent: Intent) = intent.action == ACTION

        private fun createIntent(context: Context, profilePictureType: ProfilePictureType, userUUID: UUID) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, ChatMessageService::class.java))
                        .putExtra(EXTRA_PROFILE_PICTURE_TYPE, profilePictureType)
                        .putExtra(EXTRA_USER_UUID, userUUID)

        fun launch(context: Context, profilePictureType: ProfilePictureType, userUUID: UUID) {
            context.startService(createIntent(context, profilePictureType, userUUID))
        }

        fun getData(intent: Intent): Holder {
            val profilePictureType: ProfilePictureType = intent.getSerializableExtra(EXTRA_PROFILE_PICTURE_TYPE) as ProfilePictureType
            val userUUID: UUID = intent.getSerializableExtra(EXTRA_USER_UUID) as UUID
            return Holder(profilePictureType, userUUID)
        }

        data class Holder(val profilePictureType: ProfilePictureType, val userUUID: UUID)
    }

    object ActionDownloadNineGag {

        private const val ACTION = "de.intektor.mercury.ACTION_DOWNLOAD_NINE_GAG"

        private const val EXTRA_GAG_ID: String = "de.intektor.mercury.EXTRA_GAG_ID"
        private const val EXTRA_GAG_U_U_I_D: String = "de.intektor.mercury.EXTRA_GAG_U_U_I_D"

        fun isAction(intent: Intent) = intent.action == ACTION

        private fun createIntent(context: Context, gagId: String, gagUUID: UUID) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, ChatMessageService::class.java))
                        .putExtra(EXTRA_GAG_ID, gagId)
                        .putExtra(EXTRA_GAG_U_U_I_D, gagUUID)

        fun launch(context: Context, gagId: String, gagUUID: UUID) {
            context.startService(createIntent(context, gagId, gagUUID))
        }

        fun getData(intent: Intent): Holder {
            val gagId: String = intent.getStringExtra(EXTRA_GAG_ID)
            val gagUUID: UUID = intent.getSerializableExtra(EXTRA_GAG_U_U_I_D) as UUID
            return Holder(gagId, gagUUID)
        }

        data class Holder(val gagId: String, val gagUUID: UUID)
    }

    object ActionInitializeChat {

        private const val ACTION = "de.intektor.mercury.ACTION_INITIALIZE_CHAT"

        private const val EXTRA_USER_U_U_I_D_S: String = "de.intektor.mercury.EXTRA_USER_U_U_I_D_S"
        private const val EXTRA_CHAT_U_U_I_D: String = "de.intektor.mercury.EXTRA_CHAT_U_U_I_D"

        fun isAction(intent: Intent) = intent.action == ACTION

        private fun createIntent(context: Context, userUUIDS: List<UUID>, chatUUID: UUID) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, ChatMessageService::class.java))
                        .putStringArrayListExtra(EXTRA_USER_U_U_I_D_S, ArrayList(userUUIDS.map { it.toString() }))
                        .putExtra(EXTRA_CHAT_U_U_I_D, chatUUID)

        fun launch(context: Context, userUUIDs: List<UUID>, chatUUID: UUID) {
            context.startService(createIntent(context, userUUIDs, chatUUID))
        }

        fun getData(intent: Intent): Holder {
            val userUUIDS: List<UUID> = intent.getStringArrayListExtra(EXTRA_USER_U_U_I_D_S).map { it.toUUID() }
            val chatUUID: UUID = intent.getUUIDExtra(EXTRA_CHAT_U_U_I_D)
            return Holder(userUUIDS, chatUUID)
        }

        data class Holder(val userUUIDs: List<UUID>, val chatUUID: UUID)
    }

    object ActionSendMessages {

        private const val ACTION = "de.intektor.mercury.ACTION_SEND_MESSAGES"

        private const val EXTRA_MESSAGES: String = "de.intektor.mercury.EXTRA_MESSAGES"

        fun isAction(intent: Intent) = intent.action == ACTION

        private fun createIntent(context: Context, messages: List<PendingMessage>) =
                Intent()
                        .setAction(ACTION)
                        .setComponent(ComponentName(context, ChatMessageService::class.java))
                        .putExtra(EXTRA_MESSAGES, ArrayList(messages))

        fun launch(context: Context, messages: List<PendingMessage>) {
            context.startService(createIntent(context, messages))
        }

        fun getData(intent: Intent): Holder {
            val messages: List<PendingMessage> = intent.getParcelableArrayListExtra(EXTRA_MESSAGES)
            return Holder(messages)
        }


        data class Holder(val messages: List<PendingMessage>)
    }
}
