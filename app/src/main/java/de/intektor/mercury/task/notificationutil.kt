package de.intektor.mercury.task

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.media.RingtoneManager
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.StyleSpan
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.TaskStackBuilder
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.action.notification.ActionNotificationReply
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.*
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.database.bindUUID
import de.intektor.mercury.database.getUUID
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.overview_activity.OverviewActivity
import de.intektor.mercury.util.*
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.ChatType
import de.intektor.mercury_common.chat.data.*
import de.intektor.mercury_common.chat.data.group_modification.GroupModificationChangeName
import de.intektor.mercury_common.chat.data.group_modification.GroupModificationChangeRole
import de.intektor.mercury_common.chat.data.group_modification.MessageGroupModification
import java.util.*

/**
 * @author Intektor
 */
fun handleNotification(context: Context, chatUUID: UUID, chatMessage: ChatMessage) {
    val mercuryClient = context.applicationContext as MercuryClient
    val dataBase = mercuryClient.dataBase
    //Receive all the previously saved notifications
    val previousNotifications = readPreviousNotificationsFromDataBase(null, dataBase)

    previousNotifications += NotificationHolder(chatUUID, System.currentTimeMillis(), chatMessage.messageCore.messageUUID)

    //Save the current message to the database, because we might need it next time
    writeNotificationToDatabase(dataBase, chatMessage, chatUUID, System.currentTimeMillis())

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

    val dataBase = context.mercuryClient().dataBase
    val (_, _, _, chatType) = newMessage.getData(context, dataBase)

    val split = list.groupBy { it.chatUUID }

    val inboxStyle = NotificationCompat.InboxStyle()
    for ((key, value) in split) {
        val header = SpannableString("-$key-")
        header.setSpan(StyleSpan(Typeface.BOLD), 0, "-$key-".length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        inboxStyle.addLine(header)
        for (notification in value) {
            val data = notification.getData(context, dataBase)
            inboxStyle.addLine(format(context, false, data.chatType.isGroup(), notification, data))
        }
    }
    inboxStyle.setBigContentTitle(format(context, true, chatType.isGroup(), newMessage))
            .setSummaryText(format(context, true, chatType.isGroup(), newMessage))


    val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_NEW_MESSAGES)
            .setSmallIcon(R.drawable.message_icon)
            .setContentTitle(context.getString(R.string.notification_amount_you_have_new_messages, list.size))
            .setContentText(format(context, true, true, newMessage))
            .setStyle(inboxStyle)
            .setLights(Color.BLUE, 1, 1)
            .setColor(Color.BLUE)
            .setPriority(1)
            .setVibrate(longArrayOf(0L, 200L, 100L, 200L, 1000L))
            .setAutoCancel(true)


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
    val mercuryClient = context.applicationContext as MercuryClient
    val dataBase = mercuryClient.dataBase

    val newMessage = list.lastOrNull() ?: return

    val groupedByChat = list.groupBy { it.chatUUID }

    for ((chatUUID, notifications) in groupedByChat) {
        val firstNotification = notifications.lastOrNull() ?: continue

        val (chatMessage, chatName, senderName, chatType) = firstNotification.getData(context, dataBase)

        val inboxStyle = NotificationCompat.InboxStyle()
        inboxStyle.setBigContentTitle(context.getString(R.string.notification_amount_messages, chatName, notifications.size))
        inboxStyle.setSummaryText(format(context, false, chatType.isGroup(), newMessage))
        for (notification in notifications) {
            inboxStyle.addLine(format(context, false, chatType.isGroup(), notification))
        }

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_NEW_MESSAGES)
                .setWhen(newMessage.time)
                .setShowWhen(true)
                .setSmallIcon(R.drawable.message_icon)
                .setAutoCancel(true)
                .setContentTitle(chatName)
                .setContentText(format(context, false, chatType.isGroup(), newMessage))
                .setSubText(chatName)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(KENTAI_NEW_MESSAGES_GROUP_KEY)
                .setVibrate(longArrayOf(0L, 200L, 100L, 200L, 1000L))
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setColor(Color.WHITE)
                .setStyle(inboxStyle)
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))

        if (ProfilePictureUtil.getProfilePicture(chatMessage.messageCore.senderUUID, context).exists()) {
            builder.setLargeIcon(BitmapFactory.decodeFile(ProfilePictureUtil.getProfilePicture(chatMessage.messageCore.senderUUID, context).path))
        }

        val id = getNotificationIdForChat(context, chatUUID)

        val label = context.resources.getString(R.string.notification_reply_label)

        val chatParticipants = readChatParticipants(dataBase, chatUUID)

        val replyPendingIntent = PendingIntent.getBroadcast(context, 100, ActionNotificationReply.createIntent(context, chatUUID, id), PendingIntent.FLAG_UPDATE_CURRENT)

        val remoteInput = androidx.core.app.RemoteInput.Builder(KEY_NOTIFICATION_REPLY)
                .setLabel(label)
                .build()

        val action = NotificationCompat.Action.Builder(R.drawable.message_icon, label, replyPendingIntent)
                .addRemoteInput(remoteInput)
                .setAllowGeneratedReplies(true)
                .build()

        builder.addAction(action)

        val resultIntent = Intent(context, ChatActivity::class.java)

        resultIntent.putExtra(KEY_CHAT_INFO, ChatInfo(chatUUID, chatName, chatType, chatParticipants))

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
    builder.setContentText(format(context, true, true, newMessage))
    builder.setSmallIcon(R.drawable.message_icon)
    builder.setGroup(KENTAI_NEW_MESSAGES_GROUP_KEY)
    builder.setGroupSummary(true)

    notificationManager.notify(KEY_NOTIFICATION_GROUP_ID, builder.build())
}

fun getNextFreeNotificationId(context: Context): Int {
    val preferences = getFreeNotificationSharedPreference(context)
    val currentNotification = preferences.getInt(KEY_CURRENT_NOTIFICATION, 0)

    if (currentNotification >= 20_000_000) {
        preferences.edit().clear().apply()
    }

    val nextNotification = currentNotification + 1 % 20_000_001
    preferences.edit().putInt(KEY_CURRENT_NOTIFICATION, nextNotification).apply()
    return nextNotification
}

private const val SP_FREE_NOTIFICATION = "de.intektor.mercury.shared_preferences.FREE_NOTIFICATION"
private const val KEY_CURRENT_NOTIFICATION = "de.intektor.mercury.CURRENT_NOTIFICATION"
private fun keyChatNotification(chatUUID: UUID) = "de.intektor.mercury.CHAT_NOTIFICATION_$chatUUID"

private fun getFreeNotificationSharedPreference(context: Context) = context.getSharedPreferences(SP_FREE_NOTIFICATION, Context.MODE_PRIVATE)

/**
 * Formats a message so it can be used in a notification
 */
fun format(context: Context, addGroup: Boolean, addName: Boolean, notification: NotificationHolder,
           data: NotificationHolder.NotificationHolderData = notification.getData(context, context.mercuryClient().dataBase)): CharSequence {
    val mercuryClient = context.applicationContext as MercuryClient

    val messageData = data.chatMessage.messageData

    return try {
        when (messageData) {
            is MessageText -> {
                when {
                    addGroup -> context.getString(R.string.notification_text_message_group_name, data.chatName, data.senderName, messageData.message)
                    addName -> context.getString(R.string.notification_text_message_name, data.senderName, messageData.message)
                    else -> context.getString(R.string.notification_text_message, messageData.message)
                }
            }
            is MessageGroupInvite -> {
                context.getString(R.string.notification_group_invite, data.senderName, messageData.groupInvite.groupName)
            }
            is MessageGroupModification -> {
                val groupModification = messageData.groupModification
                when (groupModification) {
                    is GroupModificationChangeName -> {
                        context.getString(R.string.notification_group_modification_change_name, data.senderName, groupModification.oldName, groupModification.newName)
                    }
                    is GroupModificationChangeRole -> {
                        val affectedName = ContactUtil.getDisplayName(context, mercuryClient.dataBase, getContact(mercuryClient.dataBase, groupModification.affectedUser))
                        val oldRoleName = getGroupRoleName(context, groupModification.oldRole)
                        val newRoleName = getGroupRoleName(context, groupModification.newRole)
                        context.getString(R.string.notification_group_modification_change_role, data.senderName, affectedName, oldRoleName, newRoleName)
                    }
                    else -> TODO()
                }
            }
            is MessageVoiceMessage -> {
                when {
                    addGroup -> context.getString(R.string.notification_voice_message_group_name, data.chatName, data.senderName, messageData.durationSeconds)
                    addName -> context.getString(R.string.notification_voice_message_name, data.senderName, messageData.durationSeconds)
                    else -> context.getString(R.string.notification_voice_message, messageData.durationSeconds)
                }
            }
            is MessageImage -> {
                when {
                    addGroup -> context.getString(R.string.notification_image_message_group_name, data.chatName, data.senderName)
                    addName -> context.getString(R.string.notification_image_message_name, data.senderName)
                    else -> context.getString(R.string.notification_image_message)
                }
            }
            is MessageVideo -> {
                when {
                    addGroup -> context.getString(R.string.notification_video_message_group_name, data.chatName, data.senderName, messageData.durationInSeconds)
                    addName -> context.getString(R.string.notification_video_message_name, data.senderName, messageData.durationInSeconds)
                    else -> context.getString(R.string.notification_video_message, messageData.durationInSeconds)
                }
            }
            else -> TODO()
        }
    } catch (t: Throwable) {
        "Error: ${t.localizedMessage}"
    }
}

data class NotificationHolder(val chatUUID: UUID, val time: Long, val messageUUID: UUID) {
    fun getData(context: Context, dataBase: SQLiteDatabase): NotificationHolderData {
        val message = (getChatMessages(context, dataBase, "message_uuid = ?", arrayOf(messageUUID.toString()), limit = "1").firstOrNull()
                ?: throw IllegalStateException("No message found matching messageUUID=?$messageUUID")).chatMessageInfo.message

        val chatName = ChatUtil.getChatName(context, dataBase, chatUUID)
        val senderName = ContactUtil.getDisplayName(context, dataBase, getContact(dataBase, message.messageCore.senderUUID))

        val chatType = getChatType(dataBase, chatUUID)
                ?: throw IllegalArgumentException("No such chat found matching chatUUID=$chatUUID")

        return NotificationHolderData(message, chatName, senderName, chatType)
    }

    data class NotificationHolderData(val chatMessage: ChatMessage, val chatName: String, val senderName: String, val chatType: ChatType)
}

fun writeNotificationToDatabase(dataBase: SQLiteDatabase, chatMessage: ChatMessage, chatUUID: UUID, time: Long) {
    dataBase.compileStatement("INSERT INTO notification_messages (chat_uuid, message_uuid, time) VALUES(?, ?, ?)").use { statement ->
        statement.bindUUID(1, chatUUID)
        statement.bindUUID(2, chatMessage.messageCore.messageUUID)
        statement.bindLong(3, time)

        statement.execute()
    }
}

fun readPreviousNotificationsFromDataBase(chatUUID: UUID?, dataBase: SQLiteDatabase): MutableList<NotificationHolder> {
    return dataBase.rawQuery("SELECT message_uuid, time, chat_uuid FROM notification_messages WHERE chat_uuid = ?", arrayOf((chatUUID
            ?: "*").toString())).use { cursor ->
        val previousNotifications = mutableListOf<NotificationHolder>()

        while (cursor.moveToNext()) {
            val messageUUID = cursor.getUUID(0)
            val time = cursor.getLong(1)
            val cUUID = cursor.getUUID(2)

            previousNotifications += NotificationHolder(cUUID, time, messageUUID)
        }

        previousNotifications
    }
}

fun clearPreviousNotificationOfChat(dataBase: SQLiteDatabase, chatUUID: UUID) {
    dataBase.compileStatement("DELETE FROM notification_messages WHERE chat_uuid = ?").use { statement ->
        statement.bindString(1, chatUUID.toString())
        statement.execute()
    }
}

fun getNotificationIdForChat(context: Context, chatUUID: UUID): Int {
    val preference = getFreeNotificationSharedPreference(context)

    val id = preference.getInt(keyChatNotification(chatUUID), -1)

    if (id != -1) return id

    val newId = getNextFreeNotificationId(context)

    preference.edit().putInt(keyChatNotification(chatUUID), newId).apply()

    return newId
}

fun cancelChatNotifications(context: Context, chatUUID: UUID) {
    val mercuryClient = context.applicationContext as MercuryClient

    val notificationManager = NotificationManagerCompat.from(context)

    clearPreviousNotificationOfChat(mercuryClient.dataBase, chatUUID)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        val notificationID = getNotificationIdForChat(context, chatUUID)
        if (notificationID != -1) {
            notificationManager.cancel(notificationID)
        }

//        val newestMessage = readPreviousNotificationsFromDataBase(null, mercuryClient.dataBase).lastOrNull()
//        if (newestMessage != null) {
//            popNotificationSDK24Summary(context, newestMessage, getCountNotificationMessages(mercuryClient.dataBase), notificationManager)
//        } else {
//            notificationManager.cancel(KEY_NOTIFICATION_GROUP_ID)
//        }
    } else {
        popNotificationSDKUnderNougat(context, readPreviousNotificationsFromDataBase(null, mercuryClient.dataBase), notificationManager)
    }
}