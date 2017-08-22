package de.intektor.kentai

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.AsyncTask
import android.os.Parcelable
import android.support.v4.content.LocalBroadcastManager
import de.intektor.kentai.fragment.ViewAdapter
import de.intektor.kentai.kentai.DbHelper
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai.kentai.internalFile
import de.intektor.kentai_http_common.chat.ChatMessage
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.client_to_server.FetchMessageRequest
import de.intektor.kentai_http_common.client_to_server.SendChatMessageRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.SendChatMessageResponse
import de.intektor.kentai_http_common.util.encryptRSA
import de.intektor.kentai_http_common.util.toKey
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.util.*


/**
 * @author Intektor
 */
class KentaiClient : Application() {

    lateinit var username: String
    lateinit var userUUID: UUID
    lateinit var internalStorage: File

    var currentActivity: Activity? = null

    lateinit var dbHelper: DbHelper

    lateinit var dataBase: SQLiteDatabase

    val pendingMessages: MutableList<PendingMessage> = mutableListOf()

    companion object {
        lateinit var INSTANCE: KentaiClient
    }

    init {
        INSTANCE = this
    }

    override fun onCreate() {
        super.onCreate()

        val userInfo = internalFile("username.info")
        if (userInfo.exists()) {
            val input = DataInputStream(userInfo.inputStream())
            username = input.readUTF()
            userUUID = UUID.fromString(input.readUTF())
            input.close()
        }

        dbHelper = DbHelper(applicationContext)
        dataBase = dbHelper.writableDatabase

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS chats (" +
                "chat_name VARCHAR(20) NOT NULL, " +
                "chat_uuid VARCHAR(45) NOT NULL, " +
                "type INT NOT NULL, " +
                "unread_messages INT NOT NULL, " +
                "PRIMARY KEY (chat_uuid));")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS chat_participants (" +
                "id INT," +
                "chat_uuid VARCHAR(45) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "participant_uuid VARCHAR(40) NOT NULL, " +
                "PRIMARY KEY (id));")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS pending_messages (" +
                "id INT, " +
                "chat_uuid VARCHAR(45) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "message_uuid VARCHAR(45) NOT NULL, " +
                "PRIMARY KEY (id));")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS contacts (" +
                "username VARCHAR(20) NOT NULL, " +
                "user_uuid VARCHAR(40) NOT NULL, " +
                "message_key VARCHAR(400), " +
                "alias VARCHAR(30) NOT NULL, " +
                "PRIMARY KEY (username));")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS message_status_change (" +
                "id INT, " +
                "message_uuid VARCHAR(45) NOT NULL REFERENCES chat_table(message_uuid) ON DELETE CASCADE, " +
                "status INT NOT NULL, " +
                "time BIGINT NOT NULL, " +
                "PRIMARY KEY(id));")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS chat_table (" +
                "message_uuid VARCHAR(45) NOT NULL," +
                "additional_info VARBINARY(2048)," +
                "text VARCHAR(2000) NOT NULL," +
                "time BIGINT NOT NULL," +
                "type INT NOT NULL," +
                "sender_uuid VARCHAR(20) NOT NULL," +
                "client INT NOT NULL," +
                "chat_uuid VARCHAR(45) NOT NULL," +
                "PRIMARY KEY (message_uuid));" +
                "CREATE INDEX chat_uuid_index ON chat_table (chat_uuid);")

        pendingMessages.addAll(buildPendingMessages())
        sendPendingMessages(pendingMessages)

        val connectionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val extras = intent.extras
                val info = extras.getParcelable<Parcelable>("networkInfo") as NetworkInfo
                val state = info.state

                if (state == NetworkInfo.State.CONNECTED) {
                    sendPendingMessages(buildPendingMessages())
                }
            }
        }
        registerReceiver(connectionReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        LocalBroadcastManager.getInstance(this).registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val activity = KentaiClient.INSTANCE.currentActivity

                val chatUUID = UUID.fromString(intent.getStringExtra("chatUUID"))
                val senderName = intent.getStringExtra("senderName")
                val senderUUID = UUID.fromString(intent.getStringExtra("senderUUID"))
                val unreadMessages = intent.getIntExtra("unreadMessages", 0)
                val message_id = UUID.fromString(intent.getStringExtra("message.id"))
                val message_senderUUID = UUID.fromString(intent.getStringExtra("message.senderUUID"))
                val message_text = intent.getStringExtra("message.text")
                val message_additionalInfo = intent.getByteArrayExtra("message.additionalInfo")
                val message_timeSent = intent.getLongExtra("message.timeSent", 0L)
                val message_messageID = intent.getIntExtra("message.messageID", 0)

                val chatMessage = ChatMessageRegistry.create(message_messageID)
                chatMessage.id = message_id
                chatMessage.senderUUID = senderUUID
                chatMessage.text = message_text
                chatMessage.timeSent = message_timeSent
                chatMessage.processAdditionalInfo(message_additionalInfo)

                val wrapper = ChatMessageWrapper(chatMessage, MessageStatus.WAITING, false, 0L)
                if (activity is ChatActivity && activity.chatInfo.chatUUID == chatUUID) {
                    activity.addMessage(wrapper)
                    activity.scrollToBottom()
                } else if (activity is OverviewActivity) {
                    val currentChats = activity.getCurrentChats()
                    if (!currentChats.any { it.chatInfo.chatUUID == chatUUID }) {
                        activity.addChat(ViewAdapter.ChatItem(ChatInfo(chatUUID, senderName, ChatType.TWO_PEOPLE, listOf(senderUUID.toString(), userUUID.toString())), wrapper, unreadMessages))
                    } else {
                        activity.updateLatestChatMessage(chatUUID, wrapper, unreadMessages)
                    }
                }
            }
        }, IntentFilter(FetchMessageRequest.TARGET))

//        LocalBroadcastManager.getInstance(this).registerReceiver(object : BroadcastReceiver() {
//            override fun onReceive(context: Context, intent: Intent) {
//                val activity = KentaiClient.INSTANCE.currentActivity
//
//                val chatUUID = UUID.fromString(intent.getStringExtra("chatUUID"))
//                val status = MessageStatus.values()[intent.getIntExtra("status", 0)]
//                val uuid = UUID.fromString(intent.getStringExtra("uuid"))
//
//                if (activity is ChatActivity && activity.chatInfo.chatUUID == chatUUID) {
//                    activity.updateMessageStatus(uuid, status)
//                } else if (activity is OverviewActivity) {
//                    activity.updateLatestChatMessageStatus(chatUUID, status, uuid)
//                }
//            }
//        }, IntentFilter(ChangeMessageStatusPacketToClientHandler::class.java.simpleName))
    }

    fun buildPendingMessages(): List<PendingMessage> {
        val list = mutableListOf<PendingMessage>()
        val cursor: Cursor = dataBase.rawQuery("SELECT chat_uuid, message_uuid FROM waiting_to_send;", null)
        while (cursor.moveToNext()) {
            val chatUUID = UUID.fromString(cursor.getString(0))
            val messageUUID = UUID.fromString(cursor.getString(1))

            val sendTo = mutableListOf<ChatReceiver>()

            val cursor4 = dataBase.rawQuery("SELECT participant_uuid, B.message_key FROM chat_participants A WHERE message_uuid = '$messageUUID' LEFT JOIN kentai.contacts B ON A.user_uuid = B.participant_uuid", null)
            while (cursor.moveToNext()) {
                sendTo.add(ChatReceiver(UUID.fromString(cursor4.getString(0)), cursor.getString(1).toKey(), ChatReceiver.ReceiverType.USER))
            }
            cursor4.close()

            val cursor2 = dataBase.rawQuery("SELECT message_uuid, additional_info, text, time, type, sender_uuid FROM chat_table WHERE message_uuid = '$messageUUID';", null)

            val message: ChatMessage
            val sender: String

            if (cursor2.moveToNext()) {
                val uuid = UUID.fromString(cursor2.getString(0))
                val blob = cursor2.getBlob(1)
                val text = cursor2.getString(2)
                val time = cursor2.getLong(3)
                val type = cursor2.getInt(4)
                sender = cursor2.getString(5)

                message = ChatMessageRegistry.create(type)
                message.id = uuid
                message.senderUUID = UUID.fromString(sender)
                message.text = text
                message.timeSent = time
                message.processAdditionalInfo(blob)

                val wrapper = ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis())
                list.add(PendingMessage(wrapper, chatUUID, sendTo))
            }
            cursor2.close()
        }
        cursor.close()
        return list
    }

    data class PendingMessage(val message: ChatMessageWrapper, val chatUUID: UUID, val sendTo: List<ChatReceiver>)

    fun sendPendingMessages(list: List<PendingMessage>) {
        object : AsyncTask<Unit, Unit, Unit>() {
            override fun doInBackground(vararg params: Unit?) {
                val connection: URLConnection = URL("localhost/" + SendChatMessageRequest.TARGET).openConnection()
                connection.readTimeout = 15000
                connection.connectTimeout = 15000
                connection.doInput = true
                connection.doOutput = true

                val gson = genGson()
                val sendingMap = mutableListOf<SendChatMessageRequest.SendingMessage>()

                for ((message, chatUUID, sendTo) in list) {
                    val previewText = message.message.text.substring(0..Math.min(message.message.text.length, 20))
                    val registryId = ChatMessageRegistry.getID(message.message.javaClass).toString()
                    for ((receiverUUID, publicKey) in sendTo) {
                        val encryptedPreviewText = previewText.encryptRSA(publicKey)
                        val encryptedRegistryID = registryId.encryptRSA(publicKey)
                        val encryptedReceiverUUID = receiverUUID.toString().encryptRSA(publicKey)
                        val sendingMessage = SendChatMessageRequest.SendingMessage(message.message, userUUID, encryptedReceiverUUID, chatUUID, encryptedRegistryID, encryptedPreviewText)
                        sendingMessage.encrypt(publicKey)
                        sendingMap.add(sendingMessage)
                    }
                }

                gson.toJson(SendChatMessageRequest(sendingMap), BufferedWriter(OutputStreamWriter(connection.getOutputStream())))
                val response = gson.fromJson(InputStreamReader(connection.getInputStream()), SendChatMessageResponse::class.java)
                if (response.id == 0) {
                    dataBase.compileStatement("DELETE FROM pending_messages WHERE message_uuid = ?;".repeat(list.size)).use { statement ->
                        for ((i, pendingMessage) in list.withIndex()) {
                            statement.bindString(i + 1, pendingMessage.message.message.id.toString())
                        }
                        statement.execute()
                    }
                }
            }
        }.execute()
    }

    override fun onTerminate() {
        super.onTerminate()
    }
}