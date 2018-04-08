package de.intektor.kentai.kentai.firebase

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.ACTION_NOTIFICATION_REPLY
import de.intektor.kentai.kentai.KEY_NOTIFICATION_REPLY
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai.kentai.chat.PendingMessage
import de.intektor.kentai.kentai.chat.sendMessageToServer
import de.intektor.kentai_http_common.chat.ChatMessageText
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import java.util.*

/**
 * @author Intektor
 */
class NotificationBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val kentaiClient = context.applicationContext as KentaiClient
        if (intent.action == ACTION_NOTIFICATION_REPLY) {
            val chatUUID = UUID.fromString(intent.getStringExtra("chatUUID"))
            val chatName = intent.getStringExtra("chatName")
            val chatType = ChatType.values()[intent.getIntExtra("chatType", 0)]
            val participants = intent.getParcelableArrayListExtra<ChatReceiver>("participants")
            val remoteInput = getRemoteInput(intent)
            val chatMessage = ChatMessageText(remoteInput.toString(), kentaiClient.userUUID, System.currentTimeMillis())
            val wrapper = ChatMessageWrapper(chatMessage, MessageStatus.WAITING, true, System.currentTimeMillis())
            wrapper.message.referenceUUID = UUID.randomUUID()
            sendMessageToServer(context, PendingMessage(wrapper, chatUUID, participants.filter { it.receiverUUID != kentaiClient.userUUID }), kentaiClient.dataBase)
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