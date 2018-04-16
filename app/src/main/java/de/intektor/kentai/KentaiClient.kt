package de.intektor.kentai

import android.annotation.TargetApi
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.AsyncTask
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AlertDialog
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import de.intektor.kentai.fragment.ChatListViewAdapter
import de.intektor.kentai.group_info_activity.GroupInfoActivity
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.android.readMessageWrapper
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.direct.connection.DirectConnectionManager
import de.intektor.kentai.overview_activity.OverviewActivity
import de.intektor.kentai_http_common.chat.ChatMessageText
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationChangeName
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationRegistry
import de.intektor.kentai_http_common.client_to_server.CurrentVersionRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.CurrentVersionResponse
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

    lateinit var directConnectionManager: DirectConnectionManager

    val currentLoadingTable = mutableMapOf<UUID, Double>()

    override fun onCreate() {
        super.onCreate()


        if (hasClient(this)) {
            val client = readClientContact(this)
            username = client.name
            userUUID = client.userUUID
        }

        File(filesDir.path + "/resources/").mkdirs()

        dbHelper = DbHelper(applicationContext)
        dataBase = dbHelper.writableDatabase

        directConnectionManager = DirectConnectionManager(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            createNotificationChannel(R.string.notification_channel_new_messages_name, R.string.notification_channel_new_messages_description, NotificationManagerCompat.IMPORTANCE_MAX,
                    NOTIFICATION_CHANNEL_NEW_MESSAGES, notificationManager)

            createNotificationChannel(R.string.notification_channel_upload_profile_picture_name, R.string.notification_channel_upload_profile_picture_description,
                    NotificationManagerCompat.IMPORTANCE_LOW, NOTIFICATION_CHANNEL_UPLOAD_PROFILE_PICTURE, notificationManager)
        }

        val chatNotificationBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val unreadMessages = intent.getIntExtra("unreadMessages", 0)

                val activity = currentActivity
                val chatUUID = UUID.fromString(intent.getStringExtra("chatUUID"))
                val chatType = ChatType.values()[intent.getIntExtra("chatType", 0)]
                val senderName = intent.getStringExtra("senderName")
                val chatName = intent.getStringExtra("chatName")
                val senderUUID = UUID.fromString(intent.getStringExtra("senderUUID"))
                val messageMessageID = intent.getIntExtra("message.messageID", 0)
                val additionalInfoID = intent.getIntExtra("additionalInfoID", 0)
                val additionalInfoContent = intent.getByteArrayExtra("additionalInfoContent")

                val wrapper = intent.readMessageWrapper(0)

                if (activity is ChatActivity && activity.chatInfo.chatUUID == chatUUID) {
                    activity.addMessage(wrapper, true)
                    if (activity.onBottom) {
                        activity.scrollToBottom()
                    }
                } else if (activity is OverviewActivity) {
                    handleNotification(context, chatUUID, chatType, senderName, chatName, senderUUID, messageMessageID, additionalInfoID, additionalInfoContent, wrapper)

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
                } else {
                    handleNotification(context, chatUUID, chatType, senderName, chatName, senderUUID, messageMessageID, additionalInfoID, additionalInfoContent, wrapper)
                }
            }
        }

        val updateMessageStatusBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val activity = currentActivity

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
                    activity.addMessage(message, true)
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

        this@KentaiClient.registerReceiver(chatNotificationBroadcastReceiver, IntentFilter("de.intektor.kentai.chatNotification"))

        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityPaused(activity: Activity?) {

            }

            override fun onActivityResumed(activity: Activity?) {
                currentActivity = activity
            }

            override fun onActivityStarted(activity: Activity?) {
                currentActivity = activity
                if (activitiesStarted == 0) {
                    var filter = IntentFilter("de.intektor.kentai.messageStatusUpdate")

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

                    //Establish a TCP connection to the kentai server
                    launchTCPConnection()

                    CheckForNewVersionTask().execute(this@KentaiClient)
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
//                    this@KentaiClient.unregisterReceiver(chatNotificationBroadcastReceiver)
                    this@KentaiClient.unregisterReceiver(updateMessageStatusBroadcastReceiver)
                    this@KentaiClient.unregisterReceiver(groupInviteBroadcastReceiver)
                    this@KentaiClient.unregisterReceiver(groupModificationBroadcastReceiver)
                    this@KentaiClient.unregisterReceiver(typingBroadcastReceiver)

                    //Exit the TCP connection to the kentai server
                    exitTCPConnection()
                    currentActivity = null
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
        directConnectionManager.launchConnection(dataBase)
    }

    fun exitTCPConnection() {
        directConnectionManager.exitConnection()
    }

    class CheckForNewVersionTask : AsyncTask<KentaiClient, Unit, Pair<CurrentVersionResponse?, KentaiClient>>() {
        override fun doInBackground(vararg params: KentaiClient): Pair<CurrentVersionResponse?, KentaiClient> {
            val kentaiClient = params[0]
            return try {
                val pInfo = kentaiClient.packageManager.getPackageInfo(kentaiClient.packageName, 0)
                val gson = genGson()
                val response = httpPost(gson.toJson(CurrentVersionRequest(pInfo.versionCode.toLong())), CurrentVersionRequest.TARGET)
                gson.fromJson<CurrentVersionResponse>(response, CurrentVersionResponse::class.java) to kentaiClient
            } catch (t: Throwable) {
                null to kentaiClient
            }
        }

        override fun onPostExecute(response: Pair<CurrentVersionResponse?, KentaiClient>) {
            val versionResponse = response.first
            val kentaiClient = response.second
            if (versionResponse == null) {
                Log.e("ERROR", "Fetching the most recent version of kentai failed")
                return
            }

            val pInfo = kentaiClient.packageManager.getPackageInfo(kentaiClient.packageName, 0)
            if (versionResponse.changes.isNotEmpty()) {
                val mostRecent = versionResponse.changes.first()
                if (mostRecent.versionCode > pInfo.versionCode) {
                    val sB = StringBuilder()
                    for (change in versionResponse.changes) {
                        val title = SpannableString(change.versionName)
                        title.setSpan(StyleSpan(Typeface.BOLD), 0, title.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sB.append(title)

                        val additions = change.changes.filter { it.changeType == CurrentVersionResponse.ChangeType.ADDITION }
                        val fixes = change.changes.filter { it.changeType == CurrentVersionResponse.ChangeType.FIX }
                        val removals = change.changes.filter { it.changeType == CurrentVersionResponse.ChangeType.REMOVAL }
                        val tweaks = change.changes.filter { it.changeType == CurrentVersionResponse.ChangeType.TWEAK }

                        fun listChanges(headline: Int, list: List<CurrentVersionResponse.Change>) {
                            if (list.isNotEmpty()) {
                                sB.newLine()
                                sB.append(kentaiClient.getString(headline)).newLine()
                                for (change in list) {
                                    sB.append(kentaiClient.getString(R.string.new_kentai_version_alert_change, change.text)).newLine()
                                }
                            }
                        }

                        listChanges(R.string.new_kentai_version_alert_addition, additions)
                        listChanges(R.string.new_kentai_version_alert_removals, removals)
                        listChanges(R.string.new_kentai_version_alert_fixes, fixes)
                        listChanges(R.string.new_kentai_version_alert_tweaks, tweaks)
                    }

                    AlertDialog.Builder(kentaiClient.currentActivity!!)
                            .setTitle(R.string.new_kentai_version_alert_title)
                            .setMessage(sB.toString())
                            .setPositiveButton(R.string.new_kentai_version_alert_install, { _, _ ->
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mostRecent.downloadLink))
                                kentaiClient.startActivity(browserIntent)
                            })
                            .setNegativeButton(R.string.new_kentai_version_alert_dismiss, { _, _ ->

                            })
                            .show()
                }
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun createNotificationChannel(name: Int, description: Int, importance: Int, id: String, notificationManagerCompat: NotificationManager) {
        val nameAct = getString(name)
        val descriptionAct = getString(description)
        val channel = NotificationChannel(id, nameAct, importance)
        channel.description = descriptionAct
        notificationManagerCompat.createNotificationChannel(channel)
    }
}