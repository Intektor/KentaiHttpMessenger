package de.intektor.mercury.chat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.util.*

/**
 * @author Intektor
 */
object ChatUtil {

    fun getChatName(context: Context, database: SQLiteDatabase, chatUUID: UUID): String {
        val chatInfo = getChatInfo(chatUUID, database)

        return chatInfo?.chatName ?: "No chat found"
    }

    fun getUnreadMessagesFromChat(database: SQLiteDatabase, chatUUID: UUID): Int {
        return database.rawQuery("SELECT amount FROM chat_unread_messages WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { cursor ->
            if (!cursor.moveToNext()) 0 else cursor.getInt(0)
        }
    }

    fun isChatInitialized(database: SQLiteDatabase, chatUUID: UUID): Boolean {
        return !(getChatInfo(chatUUID, database)?.hasUnitializedUser()
                ?: throw IllegalStateException("No such chat found with id=$chatUUID"))
    }
}