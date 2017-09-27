package de.intektor.kentai.kentai.firebase.notificstion

import android.content.Context
import android.preference.PreferenceManager
import android.content.SharedPreferences
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.content.ContextCompat
import android.R.id.edit
import android.app.Notification
import android.support.v4.app.NotificationCompat
import de.intektor.kentai.R
import de.intektor.kentai.kentai.firebase.DisplayNotificationReceiver
import de.intektor.kentai.kentai.firebase.DisplayNotificationReceiver.PreviousNotification


/**
 * @author Intektor
 */
class BundledNotificationBuilder internal constructor(val context: Context, val notificationManager: NotificationManagerCompat, val sharedPreferences: SharedPreferences) {

    private val GROUP_KEY = "Kentai"
    private val NOTIFICATION_ID = "de.intektor.kentai.NOTIFICATION_ID"
    private val SUMMARY_ID = 0

    fun sendBundledNotification(message: PreviousNotification) {
//        val notification = buildNotification(message, GROUP_KEY)
//        notificationManager.notify(getNotificationId(), notification)
//        val summary = buildSummary(message, GROUP_KEY)
//        notificationManager.notify(SUMMARY_ID, summary)
    }

//    private fun buildNotification(message: PreviousNotification, groupKey: String): Notification {
//        return NotificationCompat.Builder(context, "new_message")
//                .setContentTitle(message.sender())
//                .setContentText(message.message())
//                .setWhen(message.timestamp())
//                .setSmallIcon(R.drawable.received)
//                .setShowWhen(true)
//                .setGroup(groupKey)
//                .build()
//    }
//
//    private fun buildSummary(message: PreviousNotification, groupKey: String): Notification {
//        return NotificationCompat.Builder(context, "new_message")
//                .setContentTitle("Nougat Messenger")
//                .setContentText("You have unread messages")
//                .setWhen(message.timestamp())
//                .setSmallIcon(R.drawable.ic_message)
//                .setShowWhen(true)
//                .setGroup(groupKey)
//                .setGroupSummary(true)
//                .build()
//    }

    private fun getNotificationId(): Int {
        var id = sharedPreferences.getInt(NOTIFICATION_ID, SUMMARY_ID) + 1
        while (id == SUMMARY_ID) {
            id++
        }
        val editor = sharedPreferences.edit()
        editor.putInt(NOTIFICATION_ID, id)
        editor.apply()
        return id
    }
}

fun bundledNotificationBuilder(context: Context): BundledNotificationBuilder {
    val appContext = context.applicationContext
    var safeContext: Context? = ContextCompat.createDeviceProtectedStorageContext(appContext)
    if (safeContext == null) {
        safeContext = appContext
    }
    val notificationManager = NotificationManagerCompat.from(safeContext)
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(safeContext)
    return BundledNotificationBuilder(safeContext!!, notificationManager, sharedPreferences)
}