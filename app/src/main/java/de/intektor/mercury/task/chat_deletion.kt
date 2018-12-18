package de.intektor.mercury.task

import android.os.AsyncTask
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.chat.ChatMessageInfo
import de.intektor.mercury.chat.deleteMessage
import de.intektor.mercury.util.NOTIFICATION_CHANNEL_MISC
import de.intektor.mercury.util.NOTIFICATION_ID_DELETING_CHAT_MESSAGES
import java.util.*

/**
 * @author Intektor
 */
class DeleteMessagesTask(private val toRemove: List<ChatMessageInfo>, private val chatUUID: UUID, private val mercuryClient: MercuryClient, private val notificationManager: NotificationManagerCompat)
    : AsyncTask<Unit, Unit, Pair<Int, Boolean>>() {

    private lateinit var notification: NotificationCompat.Builder
    private var notificationID: Int = 0

    override fun onPreExecute() {
        notification = NotificationCompat.Builder(mercuryClient, NOTIFICATION_CHANNEL_MISC)
                .setContentTitle(mercuryClient.getString(R.string.chat_deletion_notification_title))
                .setContentText(mercuryClient.getString(R.string.chat_deletion_notification_content))
                .setProgress(toRemove.size, 0, false)
                .setSmallIcon(R.drawable.baseline_delete_white_24)
                .setOngoing(true)

        notificationID = PushNotificationUtil.getNextFreeNotificationId(mercuryClient)

        notificationManager.notify(NOTIFICATION_ID_DELETING_CHAT_MESSAGES, notification.build())
    }

    override fun doInBackground(vararg params: Unit?): Pair<Int, Boolean> {
        var done = 0
        try {
            toRemove.forEach { chatMessageInfo ->
                val messageUUID = chatMessageInfo.message.messageCore.messageUUID

                deleteMessage(mercuryClient.dataBase, chatUUID, messageUUID)
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
            notification.setContentText(mercuryClient.getString(R.string.chat_deletion_notification_content_failed, done))
            notification.setProgress(0, 0, false)
            notification.setOngoing(false)
            notification.setSmallIcon(R.drawable.baseline_warning_white_24)
            notificationManager.notify(notificationID, notification.build())
        }
    }
}