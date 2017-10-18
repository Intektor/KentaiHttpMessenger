package de.intektor.kentai

import android.app.Activity
import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.os.Bundle
import android.os.Parcelable
import de.intektor.kentai.fragment.ChatListViewAdapter
import de.intektor.kentai.kentai.DbHelper
import de.intektor.kentai.kentai.android.readMessageWrapper
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai.kentai.direct.connection.DirectConnectionManager
import de.intektor.kentai.kentai.internalFile
import de.intektor.kentai_http_common.chat.ChatMessageText
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationChangeName
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationRegistry
import de.intektor.kentai_http_common.tcp.server_to_client.Status
import de.intektor.kentai_http_common.tcp.server_to_client.UserChange
import de.intektor.kentai_http_common.util.readKey
import de.intektor.kentai_http_common.util.readPrivateKey
import de.intektor.kentai_http_common.util.toKey
import de.intektor.kentai_http_common.util.toUUID
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.security.Key
import java.util.*
import kotlin.collections.HashMap


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

    val userStatusMap: HashMap<UUID, UserChange> = HashMap()

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

        val chatNotificationBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val activity = currentActivity
                val chatUUID = UUID.fromString(intent.getStringExtra("chatUUID"))
                val senderName = intent.getStringExtra("senderName")
                val senderUUID = UUID.fromString(intent.getStringExtra("senderUUID"))
                val unreadMessages = intent.getIntExtra("unreadMessages", 0)

                val wrapper = intent.readMessageWrapper(0)

                if (activity is ChatActivity && activity.chatInfo.chatUUID == chatUUID) {
                    activity.addMessages(wrapper, false)
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

                        activity.addChat(ChatListViewAdapter.ChatItem(ChatInfo(chatUUID, senderName, ChatType.TWO_PEOPLE, listOf(ChatReceiver(senderUUID, senderKey, ChatReceiver.ReceiverType.USER), ChatReceiver(userUUID, publicMessageKey!!, ChatReceiver.ReceiverType.USER))), wrapper, unreadMessages))
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
                    activity.addChat(ChatListViewAdapter.ChatItem(chatInfo, ChatMessageWrapper(ChatMessageText("", senderUUID, System.currentTimeMillis()), MessageStatus.WAITING, false, System.currentTimeMillis()), 0))
                    //TODO: set the correct unread messages
                    activity.updateLatestChatMessage(sentToChatUUID, message, 1)
                } else if (activity is ChatActivity && activity.chatInfo.chatUUID == sentToChatUUID) {
                    activity.addMessages(message, false)
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

        val typingBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val chatUUID = intent.getSerializableExtra("chatUUID") as UUID
                val senderUUID = intent.getSerializableExtra("senderUUID") as UUID

                val activity = currentActivity
                when (activity) {
                    is OverviewActivity -> {

                    }
                    is ChatActivity -> {
                        if (activity.chatInfo.chatUUID == chatUUID) {
                            activity.userTyping(senderUUID)
                        }
                    }
                    is GroupInfoActivity -> {

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

                    filter = IntentFilter("de.intektor.kentai.typing")
                    this@KentaiClient.registerReceiver(typingBroadcastReceiver, filter)

                    //The user has opened the app, that means we know that he has seen all the new notifications, we can clear the database
                    dataBase.execSQL("DELETE FROM notification_messages WHERE 1")

                    //Remove all notifications
                    val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(0)

                    //Establish a TCP connection to the kentai server
                    launchTCPConnection()
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
                    this@KentaiClient.unregisterReceiver(typingBroadcastReceiver)

                    //Exit the TCP connection to the kentai server
                    exitTCPConnection()
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

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val currentActivity = this@KentaiClient.currentActivity

                val amount = intent.getIntExtra("amount", 0)
                for (i in 0 until amount) {
                    val status = intent.getSerializableExtra("status$i") as Status
                    val userUUID = intent.getSerializableExtra("userUUID$i") as UUID
                    val time = intent.getLongExtra("time$i", 0L)

                    val userChange = UserChange(userUUID, status, time)

                    userStatusMap.put(userUUID, userChange)

                    if (currentActivity is ChatActivity) {
                        currentActivity.userStatusChange(userChange)
                    } else if (currentActivity is GroupInfoActivity) {

                    }
                }
            }
        }, IntentFilter("de.intektor.kentai.user_status_change"))

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val extras = intent.extras
                val info = extras.getParcelable<Parcelable>("networkInfo") as NetworkInfo
                val state = info.state

                if (state == NetworkInfo.State.CONNECTED) {
                    launchTCPConnection()
                }
            }

        }, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val currentActivity = this@KentaiClient.currentActivity
                if (currentActivity is ChatActivity) {
                   currentActivity.connectionClosed()
                }
            }

        }, IntentFilter("de.intektor.kentai.tcp_closed"))
    }

    fun launchTCPConnection() {
        DirectConnectionManager.launchConnection(dataBase, applicationContext)
    }

    fun exitTCPConnection() {
        DirectConnectionManager.exitConnection()
    }
}