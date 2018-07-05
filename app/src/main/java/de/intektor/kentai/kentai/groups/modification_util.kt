package de.intektor.kentai.kentai.groups

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import de.intektor.kentai.R
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.contacts.addContact
import de.intektor.kentai_http_common.chat.ChatMessageGroupInvite
import de.intektor.kentai_http_common.chat.GroupRole
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.chat.group_modification.*
import de.intektor.kentai_http_common.util.toUUID
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.*

/**
 * @author Intektor
 */

/**
 * This only handles the database part of a group modification, any UI changes have to be made manually
 */
fun handleGroupModification(modification: GroupModification, senderUserUUID: UUID, dataBase: SQLiteDatabase, userUUID: UUID) {
    val senderGroupRole = getGroupRole(dataBase, modification.chatUUID.toUUID(), senderUserUUID)

    //Check if the sender's permission is high enough to perform this action
    if (!minimumGroupRole(modification, dataBase, userUUID).isLessOrEqual(senderGroupRole)) return

    when (modification) {
        is GroupModificationChangeName -> dataBase.compileStatement("UPDATE chats SET chat_name = ? WHERE chat_uuid = ?").use { statement ->
            statement.bindString(1, modification.newName)
            statement.bindString(2, modification.chatUUID)
            statement.execute()
        }
        is GroupModificationChangeRole -> dataBase.compileStatement("UPDATE group_role_table SET role = ? WHERE user_uuid = ? AND chat_uuid = ?").use { statement ->
            statement.bindLong(1, modification.newRole.toLong())
            statement.bindString(2, modification.userUUID)
            statement.bindString(3, modification.chatUUID)
            statement.execute()
        }
        is GroupModificationKickUser -> {
            dataBase.compileStatement("DELETE FROM group_role_table WHERE user_uuid = ? AND chat_uuid = ?").use { statement ->
                statement.bindString(1, modification.toKick)
                statement.bindString(2, modification.chatUUID)
                statement.execute()
            }
            dataBase.compileStatement("UPDATE chat_participants SET is_active = 0 WHERE participant_uuid = ? AND chat_uuid = ?").use { statement ->
                statement.bindString(1, modification.toKick)
                statement.bindString(2, modification.chatUUID)
                statement.execute()
            }
        }
        is GroupModificationAddUser -> {
            addChatParticipant(modification.chatUUID.toUUID(), modification.userUUID.toUUID(), dataBase)

            dataBase.compileStatement("INSERT INTO group_role_table (chat_uuid, user_uuid, role) VALUES (?, ?, ?)").use { statement ->
                statement.bindString(1, modification.chatUUID)
                statement.bindString(2, modification.userUUID)
                statement.bindLong(3, GroupRole.DEFAULT.ordinal.toLong())
                statement.execute()
            }

            var hasContact = false
            dataBase.rawQuery("SELECT user_uuid FROM contacts WHERE user_uuid = ?", arrayOf(modification.userUUID)).use { query ->
                hasContact = query.moveToNext()
            }
            if (!hasContact) {
                addContact(modification.userUUID.toUUID(), "", dataBase)
                try {
                    requestPublicKey(listOf(modification.userUUID.toUUID()), dataBase)
                } catch (t: Throwable) {
                    Log.e("ERROR", "Fetching keys after added user to group", t)
                }
            }
        }
    }
}

/**
 * Returns the minimum group role required, null means never possible, DEFAULT means for everyone possible
 */
fun minimumGroupRole(modification: GroupModification, dataBase: SQLiteDatabase, userUUID: UUID): GroupRole? {
    val chatUUID = modification.chatUUID.toUUID()
    when (modification) {
        is GroupModificationChangeRole -> {
            val originalRole = getGroupRole(dataBase, chatUUID, modification.userUUID.toUUID())
            val futureRole = GroupRole.values()[modification.newRole.toInt()]
            if (futureRole == GroupRole.ADMIN || originalRole == GroupRole.ADMIN) return null
            if (originalRole == GroupRole.MODERATOR) return GroupRole.ADMIN
            return GroupRole.MODERATOR
        }
        is GroupModificationKickUser -> {
            val originalRole = getGroupRole(dataBase, chatUUID, modification.toKick.toUUID())
            if (userUUID == modification.toKick.toUUID()) return GroupRole.DEFAULT
            if (originalRole == GroupRole.ADMIN) return null
            if (originalRole == GroupRole.MODERATOR) return GroupRole.ADMIN
            return GroupRole.MODERATOR
        }
        is GroupModificationAddUser -> return GroupRole.MODERATOR
        is GroupModificationChangeName -> return GroupRole.MODERATOR
        else -> throw IllegalArgumentException()
    }
}

fun GroupRole?.isLessOrEqual(other: GroupRole): Boolean = when (this) {
    GroupRole.ADMIN -> other == GroupRole.ADMIN
    GroupRole.MODERATOR -> other == GroupRole.MODERATOR || other == GroupRole.ADMIN
    GroupRole.DEFAULT -> other == GroupRole.DEFAULT || other == GroupRole.MODERATOR || other == GroupRole.ADMIN
    null -> false
}

fun handleGroupModificationPending(modification: GroupModification, dataBase: SQLiteDatabase) {
    val chatUUID = modification.chatUUID
    val modificationID = modification.modificationUUID
    val modificationType = GroupModificationRegistry.getID(modification::class.java)

    val byteOut = ByteArrayOutputStream()
    val dataOut = DataOutputStream(byteOut)
    modification.write(dataOut)

    val modificationData = byteOut.toByteArray()

    byteOut.close()

    dataBase.compileStatement("INSERT INTO pending_group_modifications (chat_uuid, modification_id, modification_type, modification_data) VALUES (?, ?, ?, ?)").use { statement ->
        statement.bindString(1, chatUUID)
        statement.bindString(2, modificationID)
        statement.bindLong(3, modificationType.toLong())
        statement.bindBlob(4, modificationData)
        statement.execute()
    }
}

fun removePendingGroupModification(modificationUUID: UUID, dataBase: SQLiteDatabase) {
    dataBase.compileStatement("DELETE FROM pending_group_modifications WHERE modification_id = ?").use { statement ->
        statement.bindString(1, modificationUUID.toString())
        statement.execute()
    }
}

fun getPendingModifications(chatUUID: UUID, dataBase: SQLiteDatabase): List<GroupModification> {
    return dataBase.rawQuery("SELECT modification_id, modification_type, modification_data FROM pending_group_modifications WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { cursor ->
        val list = mutableListOf<GroupModification>()
        while (cursor.moveToNext()) {
            val uuid = cursor.getString(0)
            val modificationType = cursor.getInt(1)
            val modificationData = cursor.getBlob(2)

            val modification = GroupModificationRegistry.create(modificationType, chatUUID, uuid.toUUID())
            modification.read(DataInputStream(ByteArrayInputStream(modificationData)))

            list += modification
        }
        list
    }
}

fun getGroupRoleName(context: Context, groupRole: GroupRole): String = when (groupRole) {
    GroupRole.ADMIN -> context.getString(R.string.group_role_admin)
    GroupRole.MODERATOR -> context.getString(R.string.group_role_moderator)
    GroupRole.DEFAULT -> context.getString(R.string.group_role_default)
}

fun sendGroupModificationBroadcast(context: Context, groupModification: GroupModification) {
    val byteOut = ByteArrayOutputStream()
    val dataOut = DataOutputStream(byteOut)
    groupModification.write(dataOut)

    val messageIntent = Intent(ACTION_GROUP_MODIFICATION_RECEIVED)
    messageIntent.putExtra(KEY_CHAT_UUID, groupModification.chatUUID.toUUID())
    messageIntent.putExtra(KEY_GROUP_MODIFICATION_TYPE_ID, GroupModificationRegistry.getID(groupModification::class.java))
    messageIntent.putExtra(KEY_GROUP_MODIFICATION_UUID, groupModification.modificationUUID.toUUID())
    messageIntent.putExtra(KEY_GROUP_MODIFICATION, byteOut.toByteArray())

    context.sendBroadcast(messageIntent)
}

fun inviteUserToGroupChat(toInvite: UUID, chatInfo: ChatInfo, groupInvite: ChatMessageGroupInvite.GroupInvite, userUUID: UUID, context: Context, dataBase: SQLiteDatabase) {
    val message = ChatMessageGroupInvite(groupInvite, userUUID, System.currentTimeMillis())

    val wrapper = ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis(), chatInfo.chatUUID)

    sendMessageToServer(context, PendingMessage(wrapper, getChatUUIDForUserChat(toInvite, userUUID), listOf(ChatReceiver.fromContact(getContact(dataBase, toInvite)))), dataBase)
}