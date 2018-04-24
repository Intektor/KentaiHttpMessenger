package de.intektor.kentai.kentai.firebase

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai.kentai.chat.PendingMessage
import de.intektor.kentai.kentai.chat.sendMessageToServer
import de.intektor.kentai_http_common.chat.ChatMessageText
import de.intektor.kentai_http_common.chat.MessageStatus
import java.util.*

/**
 * @author Intektor
 */
class NotificationBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kentaiClient = context.applicationContext as KentaiClient
        if (intent.action == ACTION_NOTIFICATION_REPLY) {
            val chatUUID = intent.getSerializableExtra(KEY_CHAT_UUID) as UUID
            val participants = intent.getParcelableArrayListExtra<ChatReceiver>(KEY_CHAT_PARTICIPANTS)
            val id = intent.getIntExtra(KEY_NOTIFICATION_ID, 0)
            val remoteInput = getRemoteInput(intent)
            val chatMessage = ChatMessageText(remoteInput.toString(), kentaiClient.userUUID, System.currentTimeMillis())
            val wrapper = ChatMessageWrapper(chatMessage, MessageStatus.WAITING, true, System.currentTimeMillis())
            wrapper.message.referenceUUID = UUID.randomUUID()
            sendMessageToServer(context, PendingMessage(wrapper, chatUUID, participants.filter { it.receiverUUID != kentaiClient.userUUID }), kentaiClient.dataBase)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)

            clearPreviousNotificationOfChat(kentaiClient.dataBase, chatUUID)

            val prev = readPreviousNotificationsFromDataBase(null, kentaiClient.dataBase)

            notificationManager.cancel(KEY_NOTIFICATION_GROUP_ID)

            popNotifications(context, prev, notificationManager)
        }
    }

    private fun getRemoteInput(intent: Intent): CharSequence {
        val remoteInput = android.support.v4.app.RemoteInput.getResultsFromIntent(intent)
        if (remoteInput != null) {
            return remoteInput.getCharSequence(KEY_NOTIFICATION_REPLY)
        }
        throw RuntimeException("No input found")
    }

}