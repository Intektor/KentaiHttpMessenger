package de.intektor.mercury.task

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import de.intektor.mercury.R
import de.intektor.mercury.action.group.ActionGroupModificationReceived
import de.intektor.mercury.chat.*
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.database.bindEnum
import de.intektor.mercury.database.bindUUID
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.GroupRole
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageGroupInvite
import de.intektor.mercury_common.chat.data.group_modification.*
import de.intektor.mercury_common.gson.genGson
import java.util.*

/**
 * @author Intektor
 */

/**
 * This only handles the database part of a group modification, any UI changes have to be made manually
 */
fun handleGroupModification(modification: GroupModification, senderUserUUID: UUID, dataBase: SQLiteDatabase, userUUID: UUID) {
    val senderGroupRole = getGroupRole(dataBase, modification.chatUUID, senderUserUUID)

    //Check if the sender's permission is high enough to perform this action
    if (!minimumGroupRole(modification, dataBase, userUUID).isLessOrEqual(senderGroupRole)) return

    when (modification) {
        is GroupModificationChangeName -> dataBase.compileStatement("UPDATE chats SET chat_name = ? WHERE chat_uuid = ?").use { statement ->
            statement.bindString(1, modification.newName)
            statement.bindUUID(2, modification.chatUUID)
            statement.execute()
        }
        is GroupModificationChangeRole -> dataBase.compileStatement("UPDATE group_role_table SET role = ? WHERE user_uuid = ? AND chat_uuid = ?").use { statement ->
            statement.bindEnum(1, modification.newRole)
            statement.bindUUID(2, modification.affectedUser)
            statement.bindUUID(3, modification.chatUUID)
            statement.execute()
        }
        is GroupModificationKickUser -> {
            dataBase.compileStatement("DELETE FROM group_role_table WHERE user_uuid = ? AND chat_uuid = ?").use { statement ->
                statement.bindUUID(1, modification.kickedUser)
                statement.bindUUID(2, modification.chatUUID)
                statement.execute()
            }
            dataBase.compileStatement("UPDATE chat_participants SET is_active = 0 WHERE participant_uuid = ? AND chat_uuid = ?").use { statement ->
                statement.bindUUID(1, modification.kickedUser)
                statement.bindUUID(2, modification.chatUUID)
                statement.execute()
            }
        }
        is GroupModificationAddUser -> {
            addChatParticipant(modification.chatUUID, modification.addedUser, dataBase)

            dataBase.compileStatement("INSERT INTO group_role_table (chat_uuid, user_uuid, role) VALUES (?, ?, ?)").use { statement ->
                statement.bindUUID(1, modification.chatUUID)
                statement.bindUUID(2, modification.addedUser)
                statement.bindLong(3, GroupRole.DEFAULT.ordinal.toLong())
                statement.execute()
            }

            var hasContact = false
            dataBase.rawQuery("SELECT user_uuid FROM contacts WHERE user_uuid = ?", arrayOf(modification.addedUser.toString())).use { query ->
                hasContact = query.moveToNext()
            }
            if (!hasContact) {
                ContactUtil.addContact(modification.addedUser, "", dataBase)
                try {
                    requestUsers(listOf(modification.addedUser), dataBase)
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
    val chatUUID = modification.chatUUID
    when (modification) {
        is GroupModificationChangeRole -> {
            val originalRole = getGroupRole(dataBase, chatUUID, modification.affectedUser)
            val futureRole = modification.newRole
            if (futureRole == GroupRole.ADMIN || originalRole == GroupRole.ADMIN) return null
            if (originalRole == GroupRole.MODERATOR) return GroupRole.ADMIN
            return GroupRole.MODERATOR
        }
        is GroupModificationKickUser -> {
            val originalRole = getGroupRole(dataBase, chatUUID, modification.kickedUser)
            if (userUUID == modification.kickedUser) return GroupRole.DEFAULT
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

    dataBase.compileStatement("INSERT INTO pending_group_modifications (chat_uuid, modification_id, modification_data) VALUES (?, ?, ?, ?)").use { statement ->
        statement.bindUUID(1, chatUUID)
        statement.bindUUID(2, modificationID)
        statement.bindString(3, genGson().toJson(modification))
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
    return dataBase.rawQuery("SELECT modification_data FROM pending_group_modifications WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { cursor ->
        val list = mutableListOf<GroupModification>()
        while (cursor.moveToNext()) {
            val modificationData = cursor.getString(0)

            list += genGson().fromJson(modificationData, GroupModification::class.java)
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
    ActionGroupModificationReceived.launch(context, groupModification.chatUUID, groupModification)
}

fun inviteUserToGroupChat(context: Context, dataBase: SQLiteDatabase, userUUID: UUID, toInvite: UUID, groupInvite: MessageGroupInvite.GroupInvite) {
    val core = MessageCore(ClientPreferences.getClientUUID(context), System.currentTimeMillis(), UUID.randomUUID())
    val data = MessageGroupInvite(groupInvite)

    sendMessageToServer(context, PendingMessage(ChatMessage(core, data), getChatUUIDForUserChat(toInvite, userUUID), listOf(ChatReceiver.fromContact(getContact(dataBase, toInvite)))), dataBase)
}