package de.intektor.mercury

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
import android.os.*
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AlertDialog
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import android.util.Log
import com.squareup.picasso.Picasso
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.connection.DirectConnectionManager
import de.intektor.mercury.database.DbHelper
import de.intektor.mercury.io.HttpManager
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.util.*
import de.intektor.mercury_common.client_to_server.CurrentVersionRequest
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.server_to_client.CurrentVersionResponse
import de.intektor.mercury_common.tcp.client_to_server.InterestedUser
import de.intektor.mercury_common.tcp.client_to_server.InterestedUserPacketToServer
import de.intektor.mercury_common.tcp.server_to_client.UserChange
import java.io.File
import java.util.*
import kotlin.collections.HashMap

/**
 * @author Intektor
 */
class MercuryClient : Application() {

    lateinit var dbHelper: DbHelper

    lateinit var dataBase: SQLiteDatabase

    var currentActivity: Activity? = null

    private var activitiesStarted = 0

    val userStatusMap: HashMap<UUID, UserChange> = HashMap()

    lateinit var directConnectionManager: DirectConnectionManager

    val currentLoadingTable = mutableMapOf<UUID, Double>()

    private val interestedUsers = mutableSetOf<UUID>()

    override fun onCreate() {
        super.onCreate()

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

            createNotificationChannel(R.string.notification_channel_upload_media_name, R.string.notification_channel_upload_media_description,
                    NotificationManagerCompat.IMPORTANCE_LOW, NOTIFICATION_CHANNEL_UPLOAD_MEDIA, notificationManager)

            createNotificationChannel(R.string.notification_channel_download_media_name, R.string.notification_channel_download_media_description,
                    NotificationManagerCompat.IMPORTANCE_LOW, NOTIFICATION_CHANNEL_DOWNLOAD_MEDIA, notificationManager)

            createNotificationChannel(R.string.notification_channel_miscellaneous_name, R.string.notification_channel_miscellaneous_description,
                    NotificationManagerCompat.IMPORTANCE_LOW, NOTIFICATION_CHANNEL_MISC, notificationManager)
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
                    if (isScreenOn()) {
                        //Establish a TCP connection to the mercury server
                        directConnectionManager.wantConnection = true
                        launchTCPConnection()

                        CheckForNewVersionTask().execute(this@MercuryClient)
                    }
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

                    //Exit the TCP connection to the mercury server
                    directConnectionManager.wantConnection = false
                    exitTCPConnection()
                    currentActivity = null
                }
            }

            override fun onActivityCreated(activity: Activity?, savedInstanceState: Bundle?) {
                currentActivity = activity
            }

        })

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val extras = intent.extras
                val info = extras.getParcelable<Parcelable>("networkInfo") as NetworkInfo
                val state = info.state

                if (state == NetworkInfo.State.CONNECTED && !directConnectionManager.isConnected && isScreenOn() && activitiesStarted > 0) {
                    launchTCPConnection()
                }
            }

        }, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))

        registerReceiver(object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val currentActivity = this@MercuryClient.currentActivity
                if (currentActivity is ChatActivity) {
                    currentActivity.connectionClosed()
                }
            }

        }, IntentFilter("de.intektor.mercury.tcp_closed"))

        Picasso.setSingletonInstance(PicassoUtil.buildPicasso(this))
    }

    fun launchTCPConnection() {
        if (internalFile("username.info", this).exists()) {
            directConnectionManager.launchConnection(dataBase)
        }
    }

    fun exitTCPConnection() {
        if (internalFile("username.info", this).exists()) {
            directConnectionManager.exitConnection()
        }
    }

    class CheckForNewVersionTask : AsyncTask<MercuryClient, Unit, Pair<CurrentVersionResponse?, MercuryClient>>() {
        override fun doInBackground(vararg params: MercuryClient): Pair<CurrentVersionResponse?, MercuryClient> {
            val mercuryClient = params[0]
            return try {
                val pInfo = mercuryClient.packageManager.getPackageInfo(mercuryClient.packageName, 0)
                val gson = genGson()
                val response = HttpManager.httpPost(gson.toJson(CurrentVersionRequest(pInfo.versionCode.toLong())), CurrentVersionRequest.TARGET)
                gson.fromJson<CurrentVersionResponse>(response, CurrentVersionResponse::class.java) to mercuryClient
            } catch (t: Throwable) {
                null to mercuryClient
            }
        }

        override fun onPostExecute(response: Pair<CurrentVersionResponse?, MercuryClient>) {
            val versionResponse = response.first
            val mercuryClient = response.second
            if (versionResponse == null) {
                Log.e("ERROR", "Fetching the most recent version of mercury failed")
                return
            }

            val pInfo = mercuryClient.packageManager.getPackageInfo(mercuryClient.packageName, 0)
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
                                sB.append(mercuryClient.getString(headline)).newLine()
                                for (change in list) {
                                    sB.append(mercuryClient.getString(R.string.new_mercury_version_alert_change, change.text)).newLine()
                                }
                            }
                        }

                        listChanges(R.string.new_mercury_version_alert_addition, additions)
                        listChanges(R.string.new_mercury_version_alert_removals, removals)
                        listChanges(R.string.new_mercury_version_alert_fixes, fixes)
                        listChanges(R.string.new_mercury_version_alert_tweaks, tweaks)
                    }

                    AlertDialog.Builder(mercuryClient.currentActivity!!)
                            .setTitle(R.string.new_mercury_version_alert_title)
                            .setMessage(sB.toString())
                            .setPositiveButton(R.string.new_mercury_version_alert_install) { _, _ ->
                                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(mostRecent.downloadLink))
                                mercuryClient.startActivity(browserIntent)
                            }
                            .setNegativeButton(R.string.new_mercury_version_alert_dismiss, null)
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

    fun addInterestedUser(userUUID: UUID) {
        val client = ClientPreferences.getClientUUID(this)

        if (userUUID == client) return
        interestedUsers += userUUID
        if (directConnectionManager.isConnected) {
            val time = getProfilePicture(userUUID, this).lastModified()
            directConnectionManager.sendPacket(InterestedUserPacketToServer(InterestedUser(userUUID, time), true))
        }
    }

    fun removeInterestedUser(userUUID: UUID) {
        interestedUsers -= userUUID
        if (directConnectionManager.isConnected) {
            val time = getProfilePicture(userUUID, this).lastModified()
            directConnectionManager.sendPacket(InterestedUserPacketToServer(InterestedUser(userUUID, time), false))
        }
    }

    fun getCurrentInterestedUsers(): Set<UUID> = interestedUsers

    fun isScreenOn(): Boolean {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        return powerManager.isScreenOn
    }
}