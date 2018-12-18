package de.intektor.mercury.firebase

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.action.notification.ActionNotificationReply
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.PendingMessage
import de.intektor.mercury.chat.getChatInfo
import de.intektor.mercury.chat.sendMessageToServer
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.task.PushNotificationUtil
import de.intektor.mercury.util.KEY_NOTIFICATION_REPLY
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageText
import java.util.*

/**
 * @author Intektor
 */
object NotificationBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val mercuryClient = context.applicationContext as MercuryClient
        if (ActionNotificationReply.isAction(intent)) {
            val (chatUuid, notificationId) = ActionNotificationReply.getData(intent)

            val clientUUID = ClientPreferences.getClientUUID(context)

            val chatInfo = getChatInfo(chatUuid, context.mercuryClient().dataBase) ?: return

            val remoteInput = getRemoteInput(intent)
            val core = MessageCore(clientUUID, System.currentTimeMillis(), UUID.randomUUID())
            val data = MessageText(remoteInput.toString())

            val message = ChatMessage(core, data)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(notificationId)

            PushNotificationUtil.cancelChatNotifications(context, chatUuid)

            sendMessageToServer(context, PendingMessage(message, chatUuid, chatInfo.getOthers(clientUUID)), mercuryClient.dataBase)
        }
    }

    private fun getRemoteInput(intent: Intent): CharSequence {
        val remoteInput = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
        if (remoteInput != null) {
            return remoteInput.getCharSequence(KEY_NOTIFICATION_REPLY) ?: "Exception: No notification input found"
        }
        throw RuntimeException("No input found")
    }

}