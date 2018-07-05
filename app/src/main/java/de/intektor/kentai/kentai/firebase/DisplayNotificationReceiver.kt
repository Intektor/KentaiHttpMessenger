package de.intektor.kentai.kentai.firebase

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import de.intektor.kentai.kentai.android.readMessageWrapper
import de.intektor.kentai.kentai.handleNotification
import de.intektor.kentai_http_common.chat.ChatType
import java.util.*


/**
 * @author Intektor
 */
class DisplayNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val unreadMessages = intent.getIntExtra("unreadMessages", 0)

        val chatUUID = UUID.fromString(intent.getStringExtra("chatUUID"))
        val chatType = ChatType.values()[intent.getIntExtra("chatType", 0)]
        val senderName = intent.getStringExtra("senderName")
        val chatName = intent.getStringExtra("chatName")
        val senderUUID = UUID.fromString(intent.getStringExtra("senderUUID"))
        val message_messageID = intent.getIntExtra("message.messageID", 0)
        val additionalInfoID = intent.getIntExtra("additionalInfoID", 0)
        val additionalInfoContent = intent.getByteArrayExtra("additionalInfoContent")

        val wrapper = intent.readMessageWrapper(0)

        handleNotification(context, chatUUID, chatType, senderName, chatName, senderUUID, message_messageID, additionalInfoID, additionalInfoContent, wrapper)
    }
}

