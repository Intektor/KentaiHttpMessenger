package de.intektor.mercury.io

import android.database.sqlite.SQLiteDatabase
import de.intektor.mercury.database.bindUUID
import de.intektor.mercury.database.getUUID
import java.util.*

/**
 * @author Intektor
 */
object PendingMessageUtil {

    fun queueMessage(database: SQLiteDatabase, messageUUID: UUID) {
        database.compileStatement("INSERT INTO pending_message (message_uuid) VALUES(?)").use { statement ->
            statement.bindUUID(1, messageUUID)

            statement.execute()
        }
    }

    fun removeMessage(database: SQLiteDatabase, messageUUID: UUID) {
        database.compileStatement("DELETE FROM pending_message WHERE message_uuid = ?").use { statement ->
            statement.bindUUID(1, messageUUID)
            statement.execute()
        }
    }

    fun getQueueMessages(database: SQLiteDatabase): List<UUID> {
        val list = mutableListOf<UUID>()
        database.rawQuery("SELECT message_uuid FROM pending_message", arrayOf()).use { query ->
            while (query.moveToNext()) {
                val messageUUID = query.getUUID(0)
                list += messageUUID
            }
        }
        return list
    }
}