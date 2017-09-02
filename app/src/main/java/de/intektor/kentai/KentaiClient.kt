package de.intektor.kentai

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.AsyncTask
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.content.LocalBroadcastManager
import de.intektor.kentai.fragment.ViewAdapter
import de.intektor.kentai.kentai.DbHelper
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai.kentai.httpPost
import de.intektor.kentai.kentai.internalFile
import de.intektor.kentai_http_common.chat.ChatMessage
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.client_to_server.FetchMessageRequest
import de.intektor.kentai_http_common.client_to_server.SendChatMessageRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.SendChatMessageResponse
import de.intektor.kentai_http_common.util.*
import java.io.*
import java.security.Key
import java.util.*
import android.app.NotificationManager
import android.graphics.Color


/**
 * @author Intektor
 */
class KentaiClient : Application() {

    lateinit var username: String
    lateinit var userUUID: UUID

    lateinit var dbHelper: DbHelper

    lateinit var dataBase: SQLiteDatabase

    var currentActivity: Activity? = null

    val pendingMessages: MutableList<PendingMessage> = mutableListOf()

    var privateAuthKey: Key? = null
    var publicAuthKey: Key? = null

    var privateMessageKey: Key? = null
    var publicMessageKey: Key? = null

    private var activitiesStarted = 0

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

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS fetching_messages (" +
                "chat_uuid VARCHAR(45) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "message_uuid VARCHAR(45) NOT NULL, " +
                "PRIMARY KEY (message_uuid));")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS notification_messages (" +
                "chat_uuid VARCHAR(40) NOT NULL, " +
                "sender_uuid VARCHAR(40) NOT NULL, " +
                "message_uuid VARCHAR(40) NOT NULL, " +
                "preview_text VARCHAR(60) NOT NULL, " +
                "time BIGINT NOT NULL, " +
                "PRIMARY KEY (message_uuid))")

        val chatNotificationBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val activity = currentActivity
                val chatUUID = UUID.fromString(intent.getStringExtra("chatUUID"))
                val senderName = intent.getStringExtra("senderName")
                val senderUUID = UUID.fromString(intent.getStringExtra("senderUUID"))
                val unreadMessages = intent.getIntExtra("unreadMessages", 0)
                val message_id = UUID.fromString(intent.getStringExtra("message.id"))
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
                        val cursor = dataBase.rawQuery("SELECT message_key FROM contacts WHERE user_uuid = ?", arrayOf(senderUUID.toString()))
                        val senderKey = cursor.getString(0)?.toKey()
                        cursor.close()

                        activity.addChat(ViewAdapter.ChatItem(ChatInfo(chatUUID, senderName, ChatType.TWO_PEOPLE, listOf(ChatReceiver(senderUUID, senderKey, ChatReceiver.ReceiverType.USER), ChatReceiver(userUUID, publicMessageKey!!, ChatReceiver.ReceiverType.USER))), wrapper, unreadMessages))
                    } else {
                        activity.updateLatestChatMessage(chatUUID, wrapper, unreadMessages)
                    }
                }
                abortBroadcast()
            }
        }

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity?) {

            }

            override fun onActivityResumed(activity: Activity?) {
                currentActivity = activity
            }

            override fun onActivityStarted(activity: Activity?) {
                currentActivity = activity
                if (activitiesStarted == 0) {
                    val filter = IntentFilter("de.intektor.kentai.chatNotification")
                    filter.priority = 1
                    this@KentaiClient.registerReceiver(chatNotificationBroadcastReceiver, filter)
                }
                activitiesStarted++
            }

            override fun onActivityDestroyed(activity: Activity?) {
            }

            override fun onActivitySaveInstanceState(activity: Activity?, outState: Bundle?) {
            }

            override fun onActivityStopped(activity: Activity?) {
                activitiesStarted--
                if (activitiesStarted == 0) {
                    this@KentaiClient.unregisterReceiver(chatNotificationBroadcastReceiver)
                }
            }

            override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
                currentActivity = activity
            }

        })

        if (privateAuthKey == null) {
            val messagePublic = File(filesDir.path + "/keys/encryptionKeyPublic.key")
            val messagePrivate = File(filesDir.path + "/keys/encryptionKeyPrivate.key")
            val authPublic = File(filesDir.path + "/keys/authKeyPublic.key")
            val authPrivate = File(filesDir.path + "/keys/authKeyPrivate.key")

            if (messagePublic.exists()) {
                this.publicMessageKey = readKey(DataInputStream(messagePublic.inputStream()))
                this.privateMessageKey = readPrivateKey(DataInputStream(messagePrivate.inputStream()))

                this.publicAuthKey = readKey(DataInputStream(authPublic.inputStream()))
                this.privateAuthKey = readPrivateKey(DataInputStream(authPrivate.inputStream()))
            }
        }

        pendingMessages.addAll(buildPendingMessages())
        if (pendingMessages.isNotEmpty()) {
            sendPendingMessages(pendingMessages)
        }
        val connectionReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val extras = intent.extras
                val info = extras.getParcelable<Parcelable>("networkInfo") as NetworkInfo
                val state = info.state

                if (state == NetworkInfo.State.CONNECTED) {
                    pendingMessages.addAll(buildPendingMessages())
                    if (pendingMessages.isNotEmpty()) {
                        sendPendingMessages(pendingMessages)
                    }
                }
            }
        }
        registerReceiver(connectionReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

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

        if (!File(filesDir.path + "/username.info").exists()) {
            val intent = Intent(applicationContext, RegisterActivity::class.java)
            startActivity(intent)
        } else {
            val intent = Intent(applicationContext, OverviewActivity::class.java)
            startActivity(intent)
        }
    }

    fun buildPendingMessages(): List<PendingMessage> {
        val list = mutableListOf<PendingMessage>()
        val cursor: Cursor = dataBase.rawQuery("SELECT chat_uuid, message_uuid FROM pending_messages;", null)
        while (cursor.moveToNext()) {
            val chatUUID = UUID.fromString(cursor.getString(0))
            val messageUUID = UUID.fromString(cursor.getString(1))

            val sendTo = mutableListOf<ChatReceiver>()

            val cursor4 = dataBase.rawQuery("SELECT chat_participants.participant_uuid, contacts.message_key FROM chat_participants LEFT JOIN contacts ON contacts.user_uuid = chat_participants.participant_uuid WHERE chat_participants.chat_uuid = '$chatUUID'", null)
            while (cursor4.moveToNext()) {
                val participantUUID = UUID.fromString(cursor4.getString(0))
                val key = cursor4.getString(1)
                sendTo.add(ChatReceiver(participantUUID, key.toKey(), ChatReceiver.ReceiverType.USER))
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
        if (privateAuthKey == null) {
            privateAuthKey = readPrivateKey(DataInputStream(File(filesDir.path + "/keys/authKeyPrivate.key").inputStream()))
        }
        object : AsyncTask<Unit, Unit, Unit>() {
            override fun doInBackground(vararg params: Unit?) {
                val gson = genGson()
                val sendingMap = mutableListOf<SendChatMessageRequest.SendingMessage>()

                for ((message, chatUUID, sendTo) in list) {
                    val previewText = message.message.text.substring(0 until Math.min(message.message.text.length, 20))
                    val registryId = ChatMessageRegistry.getID(message.message.javaClass).toString()
                    for ((receiverUUID, publicKey) in sendTo) {
                        val encryptedPreviewText = previewText.encryptRSA(publicKey!!)
                        val encryptedReceiverUUID = receiverUUID.toString().encryptRSA(privateAuthKey!!)
                        val sendingMessage = SendChatMessageRequest.SendingMessage(message.message, userUUID, encryptedReceiverUUID, chatUUID, registryId, encryptedPreviewText)
                        sendingMessage.encrypt(publicKey)
                        sendingMap.add(sendingMessage)
                    }
                }

                val response = httpPost(gson.toJson(SendChatMessageRequest(sendingMap)), SendChatMessageRequest.TARGET)
                val res = gson.fromJson(response, SendChatMessageResponse::class.java)
                if (res.id == 0) {
                    for (pendingMessage in list) {
                        dataBase.compileStatement("DELETE FROM pending_messages WHERE message_uuid = ?;").use { statement ->
                            statement.bindString(1, pendingMessage.message.message.id.toString())
                            statement.execute()
                        }
                    }
                    pendingMessages.clear()
                }
            }
        }.execute()
    }

    override fun onTerminate() {
        super.onTerminate()
    }
}