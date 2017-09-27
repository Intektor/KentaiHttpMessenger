package de.intektor.kentai

import android.app.Activity
import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import de.intektor.kentai.fragment.ViewAdapter
import de.intektor.kentai.kentai.DbHelper
import de.intektor.kentai.kentai.android.readMessageWrapper
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai.kentai.internalFile
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatMessageText
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationChangeName
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationRegistry
import de.intektor.kentai_http_common.util.readKey
import de.intektor.kentai_http_common.util.readPrivateKey
import de.intektor.kentai_http_common.util.toKey
import de.intektor.kentai_http_common.util.toUUID
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.security.Key
import java.util.*


/**
 * @author Intektor
 */
class KentaiClient : Application() {

    lateinit var username: String
    lateinit var userUUID: UUID

    lateinit var dbHelper: DbHelper

    lateinit var dataBase: SQLiteDatabase

    var currentActivity: Activity? = null

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

        File(filesDir.path + "/resources/").mkdirs()

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
                "is_active INT NOT NULL, " +
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
                "message_uuid VARCHAR(40) NOT NULL," +
                "additional_info VARBINARY(2048)," +
                "text VARCHAR(2000) NOT NULL," +
                "time BIGINT NOT NULL," +
                "type INT NOT NULL," +
                "sender_uuid VARCHAR(20) NOT NULL," +
                "client INT NOT NULL," +
                "chat_uuid VARCHAR(40) NOT NULL, " +
                "reference VARCHAR(40) NOT NULL, " +
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
                "additional_info_id INT NOT NULL, " +
                "additional_info_content VARBINARY(1024), " +
                "time BIGINT NOT NULL, " +
                "PRIMARY KEY (message_uuid))")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS group_role_table (" +
                "id INT, " +
                "chat_uuid VARCHAR(40) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "user_uuid VARCHAR(40) NOT NULL, " +
                "role INT NOT NULL, " +
                "PRIMARY KEY(id));" +
                "CREATE INDEX group_role_table_chat_uuid_index ON group_role_table (chat_uuid);")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS user_to_chat_uuid (" +
                "user_uuid VARCHAR(40) REFERENCES contacts(user_uuid) ON DELETE CASCADE, " +
                "chat_uuid VARCHAR(40) REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "PRIMARY KEY(user_uuid));")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS user_color_table (" +
                "user_uuid VARCHAR(40) NOT NULL, " +
                "color VARCHAR(6) NOT NULL, " +
                "PRIMARY KEY(user_uuid));")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS group_key_table (" +
                "chat_uuid VARCHAR(40) REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "group_key VARCHAR(400) NOT NULL, " +
                "PRIMARY KEY(chat_uuid));")

        dataBase.execSQL("CREATE TABLE IF NOT EXISTS reference_upload_table (" +
                "chat_uuid VARCHAR(40) REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "reference_uuid VARCHAR(40) NOT NULL, " +
                "file_type INT NOT NULL, " +
                "state INT NOT NULL, " +
                "PRIMARY KEY(reference_uuid));")

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
                    activity.addMessage(wrapper, back = false)
                    if (activity.onBottom) {
                        activity.scrollToBottom()
                    }
                } else if (activity is OverviewActivity) {
                    val currentChats = activity.getCurrentChats()
                    if (!currentChats.any { it.chatInfo.chatUUID == chatUUID }) {
                        val cursor = dataBase.rawQuery("SELECT message_key FROM contacts WHERE user_uuid = ?", arrayOf(senderUUID.toString()))
                        cursor.moveToNext()
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

        val updateMessageStatusBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val activity = KentaiClient.INSTANCE.currentActivity

                val amount = intent.getIntExtra("amount", 0)

                for (i in 0 until amount) {
                    val chatUUID = intent.getStringExtra("chatUUID$i").toUUID()
                    val messageUUID = intent.getStringExtra("messageUUID$i").toUUID()
                    val status = MessageStatus.values()[intent.getIntExtra("status$i", 0)]
                    val time = intent.getLongExtra("time$i", 0L)

                    if (activity is ChatActivity && activity.chatInfo.chatUUID == chatUUID) {
                        activity.updateMessageStatus(messageUUID, status)
                    } else if (activity is OverviewActivity) {
                        activity.updateLatestChatMessageStatus(chatUUID, status, messageUUID)
                    }
                }
            }
        }

        val groupInviteBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val groupName = intent.getStringExtra("groupName")
                val senderUUID = intent.getSerializableExtra("senderUUID") as UUID
                val sentToChatUUID = intent.getSerializableExtra("sentToChatUUID") as UUID
                val chatInfo = intent.getParcelableExtra<ChatInfo>("chatInfo")
                val message = intent.readMessageWrapper(0)

                val activity = currentActivity

                if (activity is OverviewActivity) {
                    activity.addChat(ViewAdapter.ChatItem(chatInfo, ChatMessageWrapper(ChatMessageText("", senderUUID, System.currentTimeMillis()), MessageStatus.WAITING, false, System.currentTimeMillis()), 0))
                    //TODO: set the correct unread messages
                    activity.updateLatestChatMessage(sentToChatUUID, message, 1)
                } else if (activity is ChatActivity && activity.chatInfo.chatUUID == sentToChatUUID) {
                    activity.addMessage(message, true, false)
                    if (activity.onBottom) {
                        activity.scrollToBottom()
                    }
                }
            }
        }

        val groupModificationBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val chatUUID = intent.getStringExtra("chatUUID").toUUID()
                val modification = GroupModificationRegistry.create(intent.getIntExtra("groupModificationID", 0), chatUUID)
                val input = intent.getByteArrayExtra("groupModification")
                modification.read(DataInputStream(ByteArrayInputStream(input)))

                if (modification is GroupModificationChangeName) {
                    val activity = currentActivity
                    if (activity is OverviewActivity) {
                        activity.updateChatName(chatUUID, modification.newName)
                    } else if (activity is ChatActivity) {
                        activity.supportActionBar?.title = modification.newName
                    }
                }
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
                    //Register receiver, so we can catch FCM messages in the application instead of letting the DisplayNotificationReceiver do the job
                    var filter = IntentFilter("de.intektor.kentai.chatNotification")
                    filter.priority = 1

                    this@KentaiClient.registerReceiver(chatNotificationBroadcastReceiver, filter)

                    filter = IntentFilter("de.intektor.kentai.messageStatusUpdate")
                    filter.priority = 1

                    this@KentaiClient.registerReceiver(updateMessageStatusBroadcastReceiver, filter)

                    filter = IntentFilter("de.intektor.kentai.groupInvite")
                    filter.priority = 1

                    this@KentaiClient.registerReceiver(groupInviteBroadcastReceiver, filter)

                    filter = IntentFilter("de.intektor.kentai.groupModification")
                    filter.priority = 1
                    this@KentaiClient.registerReceiver(groupModificationBroadcastReceiver, filter)

                    //The user has opened the app, that means we know that he has seen all the new notifications, we can clear the database
                    dataBase.execSQL("DELETE FROM notification_messages WHERE 1")
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
                    this@KentaiClient.unregisterReceiver(updateMessageStatusBroadcastReceiver)
                    this@KentaiClient.unregisterReceiver(groupInviteBroadcastReceiver)
                    this@KentaiClient.unregisterReceiver(groupModificationBroadcastReceiver)
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
    }
}