package de.intektor.kentai.kentai.chat

import android.content.Context
import android.os.AsyncTask
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.NOTIFICATION_CHANNEL_MISC
import de.intektor.kentai.kentai.NOTIFICATION_ID_DELETING_CHAT_MESSAGES
import de.intektor.kentai.kentai.SP_DELETING_CHAT_MESSAGES
import de.intektor.kentai.kentai.nextNotificationID
import de.intektor.kentai_http_common.util.toUUID
import java.util.*

/**
 * @author Intektor
 */
class DeleteMessagesTask(private val toRemove: List<ChatMessageWrapper>, private val chatUUID: UUID, private val kentaiClient: KentaiClient, private val notificationManager: NotificationManagerCompat)
    : AsyncTask<Unit, Unit, Pair<Int, Boolean>>() {

    private lateinit var notification: NotificationCompat.Builder
    private var notificationID: Int = 0

    override fun onPreExecute() {
        notification = NotificationCompat.Builder(kentaiClient, NOTIFICATION_CHANNEL_MISC)
                .setContentTitle(kentaiClient.getString(R.string.chat_deletion_notification_title))
                .setContentText(kentaiClient.getString(R.string.chat_deletion_notification_content))
                .setProgress(toRemove.size, 0, false)
                .setSmallIcon(R.drawable.baseline_delete_white_24)
                .setOngoing(true)

        notificationID = nextNotificationID(kentaiClient.getSharedPreferences(SP_DELETING_CHAT_MESSAGES, Context.MODE_PRIVATE), NOTIFICATION_ID_DELETING_CHAT_MESSAGES)

        notificationManager.notify(NOTIFICATION_ID_DELETING_CHAT_MESSAGES, notification.build())
    }

    override fun doInBackground(vararg params: Unit?): Pair<Int, Boolean> {
        var done = 0
        try {
            toRemove.forEach {
                deleteMessage(chatUUID, it.message.id.toUUID(), it.message.referenceUUID, kentaiClient.dataBase)
                done++
                notification.setProgress(toRemove.size, done, false)

                notificationManager.notify(notificationID, notification.build())
            }
        } catch (t: Throwable) {
            return done to false
        }
        return done to true
    }

    override fun onPostExecute(result: Pair<Int, Boolean>) {
        val done = result.first
        val finished = result.second

        if (finished) {
            notificationManager.cancel(NOTIFICATION_ID_DELETING_CHAT_MESSAGES)
        } else {
            notification.setContentText(kentaiClient.getString(R.string.chat_deletion_notification_content_failed, done))
            notification.setProgress(0, 0, false)
            notification.setOngoing(false)
            notification.setSmallIcon(R.drawable.baseline_warning_white_24)
            notificationManager.notify(notificationID, notification.build())
        }
    }
}