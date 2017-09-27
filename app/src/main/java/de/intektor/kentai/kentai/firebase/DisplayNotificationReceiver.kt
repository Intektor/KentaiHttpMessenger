package de.intektor.kentai.kentai.firebase

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.app.TaskStackBuilder
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.OverviewActivity
import de.intektor.kentai.R
import de.intektor.kentai.kentai.DbHelper
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.readChatParticipants
import de.intektor.kentai.kentai.firebase.additional_information.AdditionalInfoRegistry
import de.intektor.kentai.kentai.firebase.additional_information.IAdditionalInfo
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoGroupInviteMessage
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoGroupModification
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoVoiceMessage
import de.intektor.kentai_http_common.chat.ChatMessage
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageType
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationChangeName
import de.intektor.kentai_http_common.util.minString
import de.intektor.kentai_http_common.util.toUUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.*


/**
 * @author Intektor
 */
class DisplayNotificationReceiver : BroadcastReceiver() {

    private companion object {
        private val NOTIFICATION_FILE: String = "de.intektor.kentai.messenger.NOTIFICATION_FILE_KEY"
        private val GROUP_KEY = "Kentai"
        private val NOTIFICATION_ID = "de.intektor.kentai.NOTIFICATION_ID"
        private val SUMMARY_ID = 0
    }

    @RequiresApi(Build.VERSION_CODES.N)
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
        val additionalInfoID = intent.getIntExtra("additionalInfoID", 0)
        val additionalInfoContent = intent.getByteArrayExtra("additionalInfoContent")

        val additionalInfo = AdditionalInfoRegistry.create(additionalInfoID)
        additionalInfo.readFromStream(DataInputStream(ByteArrayInputStream(additionalInfoContent)))

        val chatMessage = ChatMessageRegistry.create(message_messageID)
        chatMessage.id = message_id
        chatMessage.senderUUID = senderUUID
        chatMessage.text = message_text
        chatMessage.timeSent = message_timeSent
        chatMessage.processAdditionalInfo(message_additionalInfo)

        if (Build.VERSION.SDK_INT < 24) {
            handleNotificationUnderSDK24(context, chatUUID, chatType, senderName, chatName, senderUUID, message_messageID, chatMessage, additionalInfo)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            handleNotificationSDK24(context, chatUUID, chatType, senderName, chatName, senderUUID, message_messageID, chatMessage, additionalInfo)
        }
    }

    private fun handleNotificationUnderSDK24(context: Context, chatUUID: UUID, chatType: ChatType, senderName: String, chatName: String, senderUUID: UUID, messageType: Int, chatMessage: ChatMessage, additionalInfo: IAdditionalInfo) {
        val dBHelper = DbHelper(context)
        dBHelper.readableDatabase.use { dataBase ->
            //Receive all the previously saved notifications
            val previousNotifications = readPreviousNotificationsFromDataBase(null, dataBase)

            previousNotifications.add(NotificationHolder(chatUUID, senderUUID, senderName,
                    chatMessage.text.minString(0..59), messageType, chatType, chatName, System.currentTimeMillis(), additionalInfo))

            //Save the current message to the database, because we might need it next time
            writeNotificationToDatabase(chatUUID, senderUUID, chatMessage, additionalInfo, dataBase)

            popNotificationSDKUnderNougat(context, previousNotifications, context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager, additionalInfo)
        }
    }

    private fun popNotificationSDKUnderNougat(context: Context, list: List<NotificationHolder>, notificationManager: NotificationManager, additionalInfo: IAdditionalInfo) {
        val builder = NotificationCompat.Builder(context, "new_messages")
        builder.setSmallIcon(R.drawable.received)
        builder.setContentTitle("You have ${list.size} new messages!")
        builder.setContentText(format(list.last(), true, true, context, additionalInfo))

        val split = list.groupBy { it.chatName }

        val inboxStyle = NotificationCompat.InboxStyle(builder)
        for ((key, value) in split) {
            val header = SpannableString("-$key-")
            header.setSpan(StyleSpan(Typeface.BOLD), 0, "-$key-".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            inboxStyle.addLine(header)
            for (notification in value) {
                inboxStyle.addLine(format(notification, false, notification.chatType == ChatType.GROUP, context, additionalInfo))
            }
        }
        inboxStyle.setBigContentTitle(format(list.last(), true, list.last().chatType == ChatType.GROUP, context, additionalInfo))
        inboxStyle.setSummaryText(format(list.last(), true, list.last().chatType == ChatType.GROUP, context, additionalInfo))
        builder.setStyle(inboxStyle)
        builder.setLights(Color.BLUE, 1, 1)
        builder.color = Color.BLUE
        builder.priority = 1
        builder.setAutoCancel(true)
        if (Build.VERSION.SDK_INT >= 21) builder.setVibrate(LongArray(0))

        val resultIntent = Intent(context, OverviewActivity::class.java)
        val resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        builder.setContentIntent(resultPendingIntent)

        notificationManager.notify(0, builder.build())
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private fun handleNotificationSDK24(context: Context, chatUUID: UUID, chatType: ChatType, senderName: String, chatName: String, senderUUID: UUID, messageType: Int, chatMessage: ChatMessage, additionalInfo: IAdditionalInfo) {
        val newMessage = NotificationHolder(chatUUID, senderUUID, senderName, chatMessage.text.minString(0..59), messageType, chatType, chatName, System.currentTimeMillis(), additionalInfo)
        val dBHelper = DbHelper(context)
        dBHelper.writableDatabase.use { dataBase ->

            val previousNotifications = readPreviousNotificationsFromDataBase(chatUUID, dataBase)

            previousNotifications.add(newMessage)

            //Save the current message to the database, because we might need it next time
            writeNotificationToDatabase(chatUUID, senderUUID, chatMessage, additionalInfo, dataBase)

            var count = 0

            dataBase.rawQuery("SELECT COUNT(message_uuid) FROM notification_messages WHERE chat_uuid = '$chatUUID'", null).use { query2 ->
                if (query2.moveToNext()) {
                    count = query2.getInt(0)
                }
            }

            val sharedPreferences = context.getSharedPreferences(NOTIFICATION_FILE, Context.MODE_PRIVATE)

            var builder = NotificationCompat.Builder(context, "new_messages")
            builder.mContentTitle = "You have $count new messages!"
            builder.mContentText = format(newMessage, true, true, context, additionalInfo)
            builder.color = Color.WHITE
            builder.setSmallIcon(R.drawable.received)
            builder.setAutoCancel(true)
            builder.setGroupSummary(true)
            builder.setGroup(GROUP_KEY)
            builder.setShowWhen(true)
            builder.setWhen(newMessage.time)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(SUMMARY_ID, builder.build())

            builder = NotificationCompat.Builder(context, "new_messages")
            builder.setWhen(newMessage.time)
            builder.setShowWhen(true)
            builder.setGroup(GROUP_KEY)
            builder.setSmallIcon(R.drawable.received)
            builder.setAutoCancel(true)
            builder.mContentTitle = chatName
            builder.mContentText = format(newMessage, false, chatType == ChatType.GROUP, context, additionalInfo)
            builder.setSubText(chatName)
            builder.priority = 1
            builder.setVibrate(kotlin.LongArray(0))
            val inboxStyle = NotificationCompat.InboxStyle()
            inboxStyle.setBigContentTitle("$chatName: $count messages")
            inboxStyle.setSummaryText(format(newMessage, false, newMessage.chatType == ChatType.GROUP, context, additionalInfo))
            for (notification in previousNotifications) {
                inboxStyle.addLine(format(notification, false, notification.chatType == ChatType.GROUP, context, additionalInfo))
            }
            builder.setStyle(inboxStyle)
            builder.color = Color.WHITE

            var id = sharedPreferences.getInt(newMessage.chatUUID.toString(), -1)
            if (id == -1) {
                id = nextNotificationID(sharedPreferences)
                val edit = sharedPreferences.edit()
                edit.putInt(newMessage.chatUUID.toString(), id)
                edit.apply()
            }

            val label = context.resources.getString(R.string.notification_reply_label)

            val chatParticipants = readChatParticipants(dataBase, chatUUID)

            val intent = Intent(context, NotificationBroadcastReceiver::class.java)
            intent.action = REPLY_ACTION
            intent.putExtra("chatName", chatName)
            intent.putExtra("chatType", chatType.ordinal)
            intent.putExtra("chatUUID", chatUUID.toString())
            intent.putExtra("participants", ArrayList(chatParticipants))
            intent.putExtra(KEY_NOTIFICATION_ID, id)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val replyPendingIntent = PendingIntent.getBroadcast(context, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT)

            val remoteInput = android.support.v4.app.RemoteInput.Builder(NOTIFICATION_REPLY_KEY)
                    .setLabel(label)
                    .build()

            val action = NotificationCompat.Action.Builder(R.drawable.received, label, replyPendingIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(true)
                    .build()

            builder.addAction(action)

            val resultIntent = Intent(context, ChatActivity::class.java)

            resultIntent.putExtra("chatInfo", ChatInfo(chatUUID, chatName, chatType, chatParticipants))

            val stackBuilder = TaskStackBuilder.create(context)
            stackBuilder.addParentStack(ChatActivity::class.java)
            stackBuilder.addNextIntent(resultIntent)

            val resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT)

            builder.setContentIntent(resultPendingIntent)

            notificationManager.notify(id, builder.build())
        }
    }

    private fun nextNotificationID(sharedPreferences: SharedPreferences): Int {
        var id = sharedPreferences.getInt(NOTIFICATION_ID, SUMMARY_ID) + 1
        while (id == SUMMARY_ID) {
            id++
        }
        val editor = sharedPreferences.edit()
        editor.putInt(NOTIFICATION_ID, id)
        editor.apply()
        return id

    }

    /**
     * Formats a message so it can be used in a notification
     */
    private fun format(notification: NotificationHolder, addGroup: Boolean, addName: Boolean, context: Context, additionalInfo: IAdditionalInfo): CharSequence {
        val chatName = notification.chatName
        val senderName = notification.senderName
        val previewText = notification.text
        return when (MessageType.values()[notification.messageType]) {
            MessageType.TEXT_MESSAGE -> {
                val text = SpannableString((if (addGroup) "$chatName - " else "") + (if (addName) "$senderName: " else "") + previewText)
                text.setSpan(StyleSpan(Typeface.BOLD), 0, ((if (addGroup) "$chatName - " else "") + (if (addName) "$senderName: " else "") + "").length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                text
            }
            MessageType.GROUP_INVITE -> {
                additionalInfo as AdditionalInfoGroupInviteMessage
                context.getString(R.string.notification_group_invite, additionalInfo.invitedByUsername, additionalInfo.groupName)
            }
            MessageType.GROUP_MODIFICATION -> {
                additionalInfo as AdditionalInfoGroupModification
                val groupModification = additionalInfo.groupModification
                when (groupModification) {
                    is GroupModificationChangeName -> {
                        context.getString(R.string.notification_group_modification_change_name, senderName, groupModification.oldName, groupModification.newName)
                    }
                    else -> TODO()
                }
            }
            MessageType.VOICE_MESSAGE -> {
                additionalInfo as AdditionalInfoVoiceMessage
                if (addGroup && addName) {
                    context.getString(R.string.notification_voice_message_group_name, chatName, senderName, additionalInfo.lengthSeconds)
                } else if (addName) {
                    context.getString(R.string.notification_voice_message_name, senderName, additionalInfo.lengthSeconds)
                } else {
                    context.getString(R.string.notification_voice_message, additionalInfo.lengthSeconds)
                }
            }
            else -> TODO()
        }
    }

    data class NotificationHolder(val chatUUID: UUID, val senderUUID: UUID, val senderName: String,
                                  val text: String, val messageType: Int, val chatType: ChatType,
                                  val chatName: String, val time: Long, val additionalInfo: IAdditionalInfo)

    private fun writeNotificationToDatabase(chatUUID: UUID, senderUUID: UUID, chatMessage: ChatMessage, additionalInfo: IAdditionalInfo, dataBase: SQLiteDatabase) {
        dataBase.compileStatement("INSERT INTO notification_messages (chat_uuid, sender_uuid, message_uuid, preview_text, time, additional_info_id, additional_info_content) VALUES(?, ?, ?, ?, ?, ?, ?)").use { statement ->
            val byteOut = ByteArrayOutputStream()
            val dataOut = DataOutputStream(byteOut)
            additionalInfo.writeToStream(dataOut)

            statement.bindString(1, chatUUID.toString())
            statement.bindString(2, senderUUID.toString())
            statement.bindString(3, chatMessage.id.toString())
            statement.bindString(4, chatMessage.text.minString(0..59))
            statement.bindLong(5, System.currentTimeMillis())
            statement.bindLong(6, AdditionalInfoRegistry.getID(additionalInfo.javaClass).toLong())
            statement.bindBlob(7, byteOut.toByteArray())

            statement.execute()
        }
    }

    private fun readPreviousNotificationsFromDataBase(chatUUID: UUID?, dataBase: SQLiteDatabase): MutableList<NotificationHolder> {
        val query = dataBase.rawQuery(
                "SELECT " +
                        "notification_messages.chat_uuid, notification_messages.sender_uuid, notification_messages.preview_text, notification_messages.additional_info_id, notification_messages.additional_info_content, " +
                        "chat_table.text, chat_table.type, " +
                        "contacts.username, contacts.alias, " +
                        "chats.type, chats.chat_name, notification_messages.time " +
                        "FROM notification_messages " +
                        "LEFT JOIN chat_table ON notification_messages.message_uuid = chat_table.message_uuid " +
                        "LEFT JOIN contacts ON contacts.user_uuid = notification_messages.sender_uuid " +
                        "LEFT JOIN chats ON chats.chat_uuid = notification_messages.chat_uuid " +
                        (if (chatUUID != null) "WHERE notification_messages.chat_uuid = '$chatUUID' " else "") +
                        "ORDER BY notification_messages.time ASC"
                , null)

        val previousNotifications = mutableListOf<NotificationHolder>()

        while (query.moveToNext()) {
            val pChatUUID = query.getString(0).toUUID()
            val pSenderUUID = query.getString(1).toUUID()
            val pPreviewText = query.getString(2)
            val additionalInfoID = query.getInt(3)
            val additionalInfoContent = query.getBlob(4)
            val pMessageText = query.getString(5)
            val pType = query.getInt(6)
            val pUsername = query.getString(7)
            val pAlias = query.getString(8)
            val pChatType = ChatType.values()[query.getInt(9)]
            val pChatName = query.getString(10)
            val pTime = query.getLong(11)

            val showingName = if (pUsername == pAlias) pUsername else pAlias
            val showingText = if (pPreviewText.isEmpty()) pMessageText.minString(0..59) else pPreviewText

            val additionalInfo = AdditionalInfoRegistry.create(additionalInfoID)
            additionalInfo.readFromStream(DataInputStream(ByteArrayInputStream(additionalInfoContent)))
            previousNotifications.add(NotificationHolder(pChatUUID, pSenderUUID, showingName, showingText, pType, pChatType, pChatName, pTime,
                    additionalInfo))
        }
        query.close()
        return previousNotifications
    }
}