package de.intektor.kentai.kentai.firebase

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.IBinder
import android.os.Parcelable
import android.util.Log
import com.google.common.io.BaseEncoding
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.android.readMessageWrapper
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.references.UploadState
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.client_to_server.SendChatMessageRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.reference.UploadResponse
import de.intektor.kentai_http_common.server_to_client.SendChatMessageResponse
import de.intektor.kentai_http_common.util.*
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.*
import java.security.Key
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
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
    private lateinit var userUUID: UUID

    @Volatile
    private lateinit var username: String

    @Volatile
    private lateinit var dataBase: SQLiteDatabase

    companion object {
        val MEDIA_TYPE_OCTET_STREAM = MediaType.parse("application/octet-stream")
    }

    override fun onCreate() {
        super.onCreate()
        val dBHelper = DbHelper(this)
        dataBase = dBHelper.writableDatabase
        val userInfo = internalFile("username.info")
        if (userInfo.exists()) {
            val input = DataInputStream(userInfo.inputStream())
            username = input.readUTF()
            userUUID = input.readUTF().toUUID()
            input.close()
        }

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

                referenceUploadQueue.add(buildReferenceInfo(chatUUID, fileType, referenceFile, referenceUUID))
            }
        }
        registerReceiver(referenceMessageReceiver, IntentFilter("de.intektor.kentai.referenceUpload"))

        val connectionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val extras = intent.extras
                val info = extras.getParcelable<Parcelable>("networkInfo") as NetworkInfo
                val state = info.state

                if (state == NetworkInfo.State.CONNECTED) {
                    chatMessageSendingQueue.addAll(buildPendingMessages())
                    referenceUploadQueue.addAll(buildReferencesToSend())
                }
            }
        }
        registerReceiver(connectionReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        thread {
            while (true) {
                val pendingMessage = chatMessageSendingQueue.take()

                val list = mutableListOf<PendingMessage>(pendingMessage)

                if (chatMessageSendingQueue.isNotEmpty()) {
                    chatMessageSendingQueue.drainTo(list)
                }

                try {
                    sendPendingMessages(list)
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

    fun buildPendingMessages(): List<PendingMessage> {
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

    fun buildReferencesToSend(): List<ReferenceInfo> {
        val list = mutableListOf<ReferenceInfo>()
        dataBase.rawQuery("SELECT chat_uuid, reference_uuid, file_type FROM reference_upload_table WHERE state != 1", arrayOf()).use { query ->
            while (query.moveToNext()) {
                val chatUUID = query.getString(0).toUUID()
                val referenceUUID = query.getString(1).toUUID()
                val fileType = FileType.values()[query.getInt(2)]
                val referenceFile = getReferenceFile(chatUUID, referenceUUID, fileType, filesDir, this@SendService)
                list.add(buildReferenceInfo(chatUUID, fileType, referenceFile, referenceUUID))
            }
        }
        return list
    }

    private fun buildReferenceInfo(chatUUID: UUID, fileType: FileType, referenceFile: File, referenceUUID: UUID): ReferenceInfo {
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

    private fun sendPendingMessages(list: MutableList<PendingMessage>) {
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
        val intent = Intent("de.intektor.kentai.uploadReferenceStarted")
        intent.putExtra("referenceUUID", referenceInfo.referenceUUID)
        sendBroadcast(intent)

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
                DataOutputStream(sink.outputStream()).use { dataOut ->
                    dataOut.writeUTF(referenceInfo.referenceUUID.toString())

                    if (referenceInfo.chatType == ChatType.TWO_PEOPLE) {
                        dataOut.writeUTF(BaseEncoding.base64().encode(key.encoded).encryptRSA(referenceInfo.encryptionKey))
                    }

                    val iV = generateInitVector()
                    dataOut.writeInt(iV.size)
                    sink.outputStream().write(iV)

                    cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iV))

                    referenceInfo.toUpload.inputStream().use { input ->
                        val responseStream = ResponseOutputStream(CipherOutputStream(dataOut, cipher), referenceInfo.toUpload.length(), referenceInfo)
                        responseStream.use { output ->
                            input.copyTo(output)
                        }
                    }
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
                    dataBase.compileStatement("UPDATE reference_upload_table SET state = ? WHERE reference_uuid = ?").use { statement ->
                        statement.bindLong(1, UploadState.UPLOADED.ordinal.toLong())
                        statement.bindString(2, referenceInfo.referenceUUID.toString())
                        val i = statement.executeUpdateDelete()
                        Log.i("INFO", i.toString())
                    }
                }

                val i = Intent("de.intektor.kentai.uploadReferenceFinished")
                i.putExtra("referenceUUID", referenceInfo.referenceUUID.toString())
                i.putExtra("successful", response.isSuccessful)
                sendBroadcast(i)
            }
        } catch (t: Throwable) {
            //Clear everything from the queue so we start sending when we have an internet connection next time
            referenceUploadQueue.clear()

            val i = Intent("de.intektor.kentai.uploadReferenceFinished")
            i.putExtra("referenceUUID", referenceInfo.referenceUUID.toString())
            i.putExtra("successful", false)
            sendBroadcast(i)
        }
    }

    override fun onBind(p0: Intent?): IBinder? = null

    data class ReferenceInfo(val chatUUID: UUID, val referenceUUID: UUID, val encryptionKey: Key, val toUpload: File, val fileType: FileType, val chatType: ChatType, val sendTo: List<ChatReceiver>)

    inner class ResponseOutputStream(outputStream: OutputStream, private val totalToSend: Long, val info: ReferenceInfo) : FilterOutputStream(outputStream) {

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
            if (prefSent + 10 < currentPercent) {
                prefSent = currentPercent
                val i = Intent("de.intektor.kentai.uploadProgress")
                i.putExtra("referenceUUID", info.referenceUUID)
                i.putExtra("progress", currentPercent)
                sendBroadcast(i)
            }
        }
    }
}