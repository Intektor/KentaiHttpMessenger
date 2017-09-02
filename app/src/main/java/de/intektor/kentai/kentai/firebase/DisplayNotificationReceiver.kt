package de.intektor.kentai.kentai.firebase

import android.app.Notification
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.support.v4.app.NotificationCompat
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.R
import de.intektor.kentai.kentai.DbHelper
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageType
import de.intektor.kentai_http_common.util.minString
import de.intektor.kentai_http_common.util.toUUID
import java.nio.channels.InterruptedByTimeoutException
import java.util.*
import android.app.PendingIntent
import de.intektor.kentai.OverviewActivity


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
        val message_id = UUID.fromString(intent.getStringExtra("message.id"))
        val message_text = intent.getStringExtra("message.text")
        val message_additionalInfo = intent.getByteArrayExtra("message.additionalInfo")
        val message_timeSent = intent.getLongExtra("message.timeSent", 0L)
        val message_messageID = intent.getIntExtra("message.messageID", 0)

        val chatMessage = ChatMessageRegistry.create(message_messageID)
        chatMessage.id = message_id
        chatMessage.senderUUID = senderUUID
        chatMessage.text = message_text
        chatMessage.timeSent = message_timeSent
        chatMessage.processAdditionalInfo(message_additionalInfo)

        if (Build.VERSION.SDK_INT < 23 || true) {
            val dBHelper = DbHelper(context)
            dBHelper.readableDatabase.use { dataBase ->
                //Receive all the previously saved notifications
                val query = dataBase.rawQuery(
                        "SELECT " +
                                "notification_messages.chat_uuid, notification_messages.sender_uuid, notification_messages.preview_text, " +
                                "chat_table.text, chat_table.type, " +
                                "contacts.username, contacts.alias, " +
                                "chats.type, chats.chat_name, notification_messages.time " +
                                "FROM notification_messages " +
                                "LEFT JOIN chat_table ON notification_messages.message_uuid = chat_table.message_uuid " +
                                "LEFT JOIN contacts ON contacts.user_uuid = notification_messages.sender_uuid " +
                                "LEFT JOIN chats ON chats.chat_uuid = notification_messages.chat_uuid " +
                                "ORDER BY notification_messages.time ASC"
                        , null)

                val previousNotifications = mutableListOf<PreviousNotification>()

                while (query.moveToNext()) {
                    val pChatUUID = query.getString(0).toUUID()
                    val pSenderUUID = query.getString(1).toUUID()
                    val pPreviewText = query.getString(2)
                    val pMessageText = query.getString(3)
                    val pType = query.getInt(4)
                    val pUsername = query.getString(5)
                    val pAlias = query.getString(6)
                    val pChatType = ChatType.values()[query.getInt(7)]
                    val pChatName = query.getString(8)
                    val pTime = query.getLong(9)

                    val showingName = if (pUsername == pAlias) pUsername else pAlias
                    val showingText = if (pPreviewText.isEmpty()) pMessageText.minString(0..59) else pPreviewText

                    previousNotifications.add(PreviousNotification(pChatUUID, pSenderUUID, showingName, showingText, pType, pChatType, pChatName, pTime))
                }
                query.close()

                previousNotifications.add(PreviousNotification(chatUUID, senderUUID, senderName,
                        message_text.minString(0..59), message_messageID, chatType, chatName, System.currentTimeMillis()))

                //Save the current message to the database, because we might need it next time
                dataBase.compileStatement("INSERT INTO notification_messages (chat_uuid, sender_uuid, message_uuid, preview_text, time) VALUES(?, ?, ?, ?, ?)").use { statement ->
                    statement.bindString(1, chatUUID.toString())
                    statement.bindString(2, senderUUID.toString())
                    statement.bindString(3, message_id.toString())
                    statement.bindString(4, message_text.minString(0..59))
                    statement.bindLong(5, System.currentTimeMillis())
                    statement.execute()
                }

                popNotificationSDKUnderNougat(context, previousNotifications)
            }

        }
    }

    private fun popNotificationSDKUnderNougat(context: Context, list: List<PreviousNotification>) {
        val builder = NotificationCompat.Builder(context, "new_messages")
        builder.setSmallIcon(R.drawable.received)
        builder.setContentTitle("You have ${list.size} new messages!")
        builder.setContentText(formatUnderNougat(list.last(), true))

        val split = list.groupBy { it.chatName }

        val inboxStyle = NotificationCompat.InboxStyle(builder)
        for ((key, value) in split) {
            val header = SpannableString("-$key-")
            header.setSpan(StyleSpan(Typeface.BOLD), 0, "-$key-".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            inboxStyle.addLine(header)
            for (notification in value) {
                inboxStyle.addLine(formatUnderNougat(notification, false))
            }
        }
        inboxStyle.setBigContentTitle("")
        inboxStyle.setSummaryText(formatUnderNougat(list.last(), true))
        builder.setStyle(inboxStyle)
        builder.setLights(Color.BLUE, 1, 1)
        builder.color = Color.BLUE
        builder.priority = 1

        val resultIntent = Intent(context, OverviewActivity::class.java)
        val resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        builder.setContentIntent(resultPendingIntent)

        val mNotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.notify(0, builder.build())
    }

    /**
     * Formats a message so it can be used in a notification
     */
    private fun formatUnderNougat(notification: PreviousNotification, addGroup: Boolean): CharSequence {
        val chatName = notification.chatName
        val senderName = notification.senderName
        val previewText = notification.text
        return when (MessageType.values()[notification.messageType]) {
            MessageType.TEXT_MESSAGE -> {
                val text = SpannableString(if (addGroup) "$chatName - " else "" + "$senderName: $previewText")
                text.setSpan(StyleSpan(Typeface.BOLD), 0, (if (addGroup) "$chatName - " else "" + "$senderName:").length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                text
            }
            else -> TODO()
        }
    }

    data class PreviousNotification(val chatUUID: UUID, val senderUUID: UUID, val senderName: String,
                                    val text: String, val messageType: Int, val chatType: ChatType,
                                    val chatName: String, val time: Long)
}