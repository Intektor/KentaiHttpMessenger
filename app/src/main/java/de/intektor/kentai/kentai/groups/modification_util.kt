package de.intektor.kentai.kentai.groups

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import de.intektor.kentai.kentai.chat.addChatParticipant
import de.intektor.kentai.kentai.chat.requestPublicKey
import de.intektor.kentai.kentai.contacts.addContact
import de.intektor.kentai_http_common.chat.GroupRole
import de.intektor.kentai_http_common.chat.group_modification.*
import de.intektor.kentai_http_common.util.toUUID

/**
 * @author Intektor
 */

/**
 * This only handles the database part of a group modification, any UI changes have to be made manually
 */
fun handleGroupModification(modification: GroupModification, dataBase: SQLiteDatabase) {
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