package de.intektor.kentai.kentai

import android.content.Context
import android.support.v4.app.NotificationCompat
import de.intektor.kentai.R
import de.intektor.kentai_http_common.chat.ChatMessage
import de.intektor.kentai_http_common.chat.ChatMessageText
import android.app.NotificationManager
import de.intektor.kentai_http_common.chat.ChatType


/**
 * @author Intektor
 */

fun popNotification(context: Context, messageList: List<NotificationMessage>) {
    val builder = NotificationCompat.Builder(context)
    builder.setSmallIcon(R.drawable.waiting)
    builder.setContentTitle("Kentai")
    builder.setContentText("You have ${messageList.size} new messages!")

    val inboxStyle = NotificationCompat.InboxStyle()
    inboxStyle.setBigContentTitle("You have ${messageList.size} new messages!")
    for ((_, senderName, chatName, chatType, previewText) in messageList) {
        val line: String = when (chatType) {
            ChatType.TWO_PEOPLE -> "$senderName: $previewText"
            ChatType.GROUP -> "$chatName-$senderName: $previewText"
            else -> throw RuntimeException()
        }
        inboxStyle.addLine(line)
    }

    builder.setStyle(inboxStyle)

    val built = builder.build()
    val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    mNotificationManager.notify(0, built)
}

data class NotificationMessage(val message: ChatMessage, val senderName: String, val chatName: String, val chatType: ChatType, val previewText: String)