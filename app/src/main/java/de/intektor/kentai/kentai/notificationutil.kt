package de.intektor.kentai.kentai

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.RingtoneManager
import android.os.Build
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.app.NotificationManagerCompat
import android.support.v4.app.TaskStackBuilder
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.getName
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.firebase.NotificationBroadcastReceiver
import de.intektor.kentai.kentai.firebase.additional_information.AdditionalInfoRegistry
import de.intektor.kentai.kentai.firebase.additional_information.IAdditionalInfo
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoGroupInviteMessage
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoGroupModification
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoVideoMessage
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoVoiceMessage
import de.intektor.kentai.kentai.groups.getGroupRoleName
import de.intektor.kentai.overview_activity.OverviewActivity
import de.intektor.kentai_http_common.chat.*
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationChangeName
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationChangeRole
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
fun handleNotification(context: Context, chatUUID: UUID, chatType: ChatType, senderName: String, chatName: String, senderUUID: UUID, messageMessageId: Int, additionalInfoId: Int, additionalInfoContent: ByteArray, wrapper: ChatMessageWrapper) {
    val additionalInfo = AdditionalInfoRegistry.create(additionalInfoId)
    additionalInfo.readFromStream(DataInputStream(ByteArrayInputStream(additionalInfoContent)))

    if (wrapper.message is ChatMessageStatusChange) return
    if (wrapper.message is ChatMessageTyping) return

    handleNotification(context, chatUUID, chatType, senderName, chatName, senderUUID, messageMessageId, wrapper.message, additionalInfo)
}

fun handleNotification(context: Context, chatUUID: UUID, chatType: ChatType, senderName: String, chatName: String, senderUUID: UUID, messageType: Int, chatMessage: ChatMessage, additionalInfo: IAdditionalInfo) {
    val kentaiClient = context.applicationContext as KentaiClient
    val dataBase = kentaiClient.dataBase
    //Receive all the previously saved notifications
    val previousNotifications = readPreviousNotificationsFromDataBase(null, dataBase)

    previousNotifications.add(NotificationHolder(chatUUID, senderUUID, senderName,
            chatMessage.text.minString(0..59), messageType, chatType, chatName, System.currentTimeMillis(), additionalInfo))

    //Save the current message to the database, because we might need it next time
    writeNotificationToDatabase(chatUUID, senderUUID, chatMessage, additionalInfo, dataBase)

    popNotifications(context, previousNotifications, NotificationManagerCompat.from(context))
}

fun popNotifications(context: Context, previousNotifications: List<NotificationHolder>, notificationManager: NotificationManagerCompat) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
        popNotificationSDKUnderNougat(context, previousNotifications, notificationManager)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        popNotificationSDK24(context, previousNotifications, notificationManager)
    }
}

fun popNotificationSDKUnderNougat(context: Context, list: List<NotificationHolder>, notificationManager: NotificationManagerCompat) {
    val newMessage = list.lastOrNull() ?: return

    val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_NEW_MESSAGES)
    builder.setSmallIcon(R.drawable.message_icon)
    builder.setContentTitle(context.getString(R.string.notification_amount_you_have_new_messages, list.size))
    builder.setContentText(format(newMessage, true, true, context, newMessage.additionalInfo))

    val split = list.groupBy { it.chatName }

    val inboxStyle = NotificationCompat.InboxStyle(builder)
    for ((key, value) in split) {
        val header = SpannableString("-$key-")
        header.setSpan(StyleSpan(Typeface.BOLD), 0, "-$key-".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        inboxStyle.addLine(header)
        for (notification in value) {
            inboxStyle.addLine(format(notification, false, notification.chatType.isGroup(), context, notification.additionalInfo))
        }
    }
    inboxStyle.setBigContentTitle(format(newMessage, true, newMessage.chatType.isGroup(), context, newMessage.additionalInfo))
    inboxStyle.setSummaryText(format(newMessage, true, newMessage.chatType.isGroup(), context, newMessage.additionalInfo))
    builder.setStyle(inboxStyle)
    builder.setLights(Color.BLUE, 1, 1)
    builder.color = Color.BLUE
    builder.priority = 1
    builder.setVibrate(longArrayOf(0L, 200L, 100L, 200L, 1000L))
    builder.setAutoCancel(true)

    val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    builder.setSound(alarmSound)

    if (Build.VERSION.SDK_INT >= 21) builder.setVibrate(longArrayOf(500L))

    val resultIntent = Intent(context, OverviewActivity::class.java)
    val resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT)

    builder.setContentIntent(resultPendingIntent)

    notificationManager.notify(0, builder.build())
}

@RequiresApi(Build.VERSION_CODES.N)
fun popNotificationSDK24(context: Context, list: List<NotificationHolder>, notificationManager: NotificationManagerCompat) {
    val kentaiClient = context.applicationContext as KentaiClient
    val dataBase = kentaiClient.dataBase

    val count = getCountNotificationMessages(dataBase)

    val newMessage = list.lastOrNull() ?: return

    val sharedPreferences = getNotificationPreferences(context)

    var builder: NotificationCompat.Builder

    val groupedByChat = list.groupBy { it.chatUUID }

    for ((chatUUID, notifications) in groupedByChat) {
        val first = notifications.lastOrNull() ?: continue

        builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_NEW_MESSAGES)
        builder.setWhen(newMessage.time)
        builder.setShowWhen(true)
        builder.setSmallIcon(R.drawable.message_icon)
        builder.setAutoCancel(true)
        builder.setContentTitle(first.chatName)
        builder.setContentText(format(newMessage, false, first.chatType.isGroup(), context, first.additionalInfo))
        builder.setSubText(first.chatName)
        builder.priority = NotificationCompat.PRIORITY_HIGH
        builder.setGroup(KENTAI_NEW_MESSAGES_GROUP_KEY)
        builder.setVibrate(longArrayOf(0L, 200L, 100L, 200L, 1000L))
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)

        if (getProfilePicture(first.senderUUID, context).exists()) {
            builder.setLargeIcon(BitmapFactory.decodeFile(getProfilePicture(first.senderUUID, context).path))
        }

        val alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        builder.setSound(alarmSound)

        val inboxStyle = NotificationCompat.InboxStyle()
        inboxStyle.setBigContentTitle(context.getString(R.string.notification_amount_messages, first.chatName, notifications.size))
        inboxStyle.setSummaryText(format(newMessage, false, newMessage.chatType.isGroup(), context, first.additionalInfo))
        for (notification in notifications) {
            inboxStyle.addLine(format(notification, false, notification.chatType.isGroup(), context, notification.additionalInfo))
        }
        builder.setStyle(inboxStyle)
        builder.color = Color.WHITE

        var id = sharedPreferences.getInt(first.chatUUID.toString(), -1)
        if (id == -1) {
            id = nextNotificationID(sharedPreferences, 1)
            val edit = sharedPreferences.edit()
            edit.putInt(first.chatUUID.toString(), id)
            edit.apply()
        }

        val label = context.resources.getString(R.string.notification_reply_label)

        val chatParticipants = readChatParticipants(dataBase, chatUUID)

        val intent = Intent(context, NotificationBroadcastReceiver::class.java)
        intent.action = ACTION_NOTIFICATION_REPLY
        intent.putExtra(KEY_CHAT_UUID, chatUUID)
        intent.putExtra(KEY_CHAT_PARTICIPANTS, ArrayList(chatParticipants))
        intent.putExtra(KEY_NOTIFICATION_ID, id)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val replyPendingIntent = PendingIntent.getBroadcast(context, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        val remoteInput = android.support.v4.app.RemoteInput.Builder(KEY_NOTIFICATION_REPLY)
                .setLabel(label)
                .build()

        val action = NotificationCompat.Action.Builder(R.drawable.message_icon, label, replyPendingIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()

        builder.addAction(action)

        val resultIntent = Intent(context, ChatActivity::class.java)

        resultIntent.putExtra(KEY_CHAT_INFO, ChatInfo(chatUUID, first.chatName, first.chatType, chatParticipants))

        val stackBuilder = TaskStackBuilder.create(context)
        stackBuilder.addParentStack(ChatActivity::class.java)
        stackBuilder.addParentStack(OverviewActivity::class.java)
        stackBuilder.addNextIntent(resultIntent)

        val resultPendingIntent = PendingIntent.getActivity(context, 0, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        builder.setContentIntent(resultPendingIntent)

        notificationManager.notify(id, builder.build())
    }

//    popNotificationSDK24Summary(context, newMessage, count, notificationManager)
}

fun popNotificationSDK24Summary(context: Context, newMessage: NotificationHolder, count: Int, notificationManager: NotificationManagerCompat) {
    val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_NEW_MESSAGES)
    builder.setContentTitle(context.getString(R.string.notification_amount_you_have_new_messages, count))
    builder.setContentText(format(newMessage, true, true, context, newMessage.additionalInfo))
    builder.setSmallIcon(R.drawable.message_icon)
    builder.setGroup(KENTAI_NEW_MESSAGES_GROUP_KEY)
    builder.setGroupSummary(true)

    notificationManager.notify(KEY_NOTIFICATION_GROUP_ID, builder.build())
}

fun nextNotificationID(sharedPreferences: SharedPreferences, start: Int): Int {
    var id = sharedPreferences.getInt(SP_NOTIFICATIONS_KEY, KEY_NOTIFICATION_GROUP_ID) + start
    while (id == KEY_NOTIFICATION_GROUP_ID) {
        id++
    }
    val editor = sharedPreferences.edit()
    editor.putInt(SP_NOTIFICATIONS_KEY, id)
    editor.apply()
    return id
}

/**
 * Formats a message so it can be used in a notification
 */
fun format(notification: NotificationHolder, addGroup: Boolean, addName: Boolean, context: Context, additionalInfo: IAdditionalInfo): CharSequence {
    val chatName = notification.chatName
    val senderName = notification.senderName
    val previewText = notification.text
    val kentaiClient = context.applicationContext as KentaiClient
    return try {
        when (MessageType.values()[notification.messageType]) {
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
                    is GroupModificationChangeRole -> {
                        val affectedName = getName(getContact(kentaiClient.dataBase, groupModification.userUUID.toUUID()), context, true)
                        val oldRoleName = getGroupRoleName(context, GroupRole.values()[groupModification.oldRole.toInt()])
                        val newRoleName = getGroupRoleName(context, GroupRole.values()[groupModification.newRole.toInt()])
                        context.getString(R.string.notification_group_modification_change_role, senderName, affectedName, oldRoleName, newRoleName)
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
            MessageType.IMAGE_MESSAGE -> {
                if (addGroup && addName) {
                    context.getString(R.string.notification_image_message_group_name, chatName, senderName)
                } else if (addName) {
                    context.getString(R.string.notification_image_message_name, senderName)
                } else {
                    context.getString(R.string.notification_image_message)
                }
            }
            MessageType.VIDEO_MESSAGE -> {
                additionalInfo as AdditionalInfoVideoMessage
                if (addGroup && addName) {
                    context.getString(R.string.notification_video_message_group_name, chatName, senderName, additionalInfo.durationSeconds)
                } else if (addName) {
                    context.getString(R.string.notification_video_message_name, senderName, additionalInfo.durationSeconds)
                } else {
                    context.getString(R.string.notification_video_message, additionalInfo.durationSeconds)
                }
            }
            else -> TODO()
        }
    } catch (t: Throwable) {
        "Error: ${t.localizedMessage}"
    }
}

data class NotificationHolder(val chatUUID: UUID, val senderUUID: UUID, val senderName: String,
                              val text: String, val messageType: Int, val chatType: ChatType,
                              val chatName: String, val time: Long, val additionalInfo: IAdditionalInfo)

fun writeNotificationToDatabase(chatUUID: UUID, senderUUID: UUID, chatMessage: ChatMessage, additionalInfo: IAdditionalInfo, dataBase: SQLiteDatabase) {
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

fun readPreviousNotificationsFromDataBase(chatUUID: UUID?, dataBase: SQLiteDatabase): MutableList<NotificationHolder> {
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

fun clearPreviousNotificationOfChat(dataBase: SQLiteDatabase, chatUUID: UUID) {
    dataBase.compileStatement("DELETE FROM notification_messages WHERE chat_uuid = ?").use { statement ->
        statement.bindString(1, chatUUID.toString())
        statement.execute()
    }
}

fun getNotificationPreferences(context: Context): SharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_NOTIFICATIONS, Context.MODE_PRIVATE)

fun cancelChatNotifications(context: Context, chatUUID: UUID) {
    val kentaiClient = context.applicationContext as KentaiClient

    val notificationManager = NotificationManagerCompat.from(context)

    clearPreviousNotificationOfChat(kentaiClient.dataBase, chatUUID)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val notificationID = getNotificationPreferences(context).getInt(chatUUID.toString(), -1)
        if (notificationID != -1) {
            notificationManager.cancel(notificationID)
        }

//        val newestMessage = readPreviousNotificationsFromDataBase(null, kentaiClient.dataBase).lastOrNull()
//        if (newestMessage != null) {
//            popNotificationSDK24Summary(context, newestMessage, getCountNotificationMessages(kentaiClient.dataBase), notificationManager)
//        } else {
//            notificationManager.cancel(KEY_NOTIFICATION_GROUP_ID)
//        }
    } else {
        popNotificationSDKUnderNougat(context, readPreviousNotificationsFromDataBase(null, kentaiClient.dataBase), notificationManager)
    }
}