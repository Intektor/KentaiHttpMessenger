package de.intektor.mercury.chat

import android.database.sqlite.SQLiteDatabase
import java.util.*

/**
 * @author Intektor
 */
object ChatParticipantUtil {

    fun isUserActive(chatUUID: UUID, userUUID: UUID, dataBase: SQLiteDatabase): Boolean {
        return dataBase.rawQuery("SELECT is_active FROM chat_participants WHERE chat_uuid = ? AND participant_uuid = ?", arrayOf(chatUUID.toString(), userUUID.toString())).use { query ->
            if (query.moveToNext()) {
                query.getInt(0) == 1
            } else false
        }
    }
}