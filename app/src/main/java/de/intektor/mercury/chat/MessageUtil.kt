package de.intektor.mercury.chat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.database.bindUUID
import de.intektor.mercury.database.getUUID
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.data.MessageText

/**
 * @author Intektor
 */
object MessageUtil {

    fun getPreviewText(context: Context, message: ChatMessage): String {
        val data = message.messageData

        return when (data) {
            is MessageText -> data.message

            else -> "No preview text found"
        }
    }

    fun saveMessageLookup(context: Context, database: SQLiteDatabase, message: ChatMessage) {
        database.compileStatement("INSERT INTO lookup (message_uuid, query_lookup) VALUES(?, ?)").use { statement ->
            statement.bindUUID(1, message.messageCore.messageUUID)
            statement.bindString(2, MessageUtil.getPreviewText(context, message))
            statement.execute()
        }
    }

    fun lookupMessages(context: Context, database: SQLiteDatabase, query: String, limit: Long, start: Int): List<ChatMessageWrapper> {
        val client = ClientPreferences.getClientUUID(context)

        val list = mutableListOf<ChatMessageWrapper>()

        database.rawQuery("SELECT chat_message.message_uuid, time_created, sender_uuid, chat_uuid, data FROM chat_message " +
                "LEFT JOIN message_data ON chat_message.message_uuid = message_data.message_uuid " +
                "LEFT JOIN lookup on lookup.message_uuid = chat_message.message_uuid " +
                "WHERE query_lookup LIKE ?" +
                "ORDER BY time_created DESC LIMIT $start, $limit", arrayOf("%$query%")).use { cursor ->
            while (cursor.moveToNext()) {
                val messageUUID = cursor.getUUID(0)
                val timeCreated = cursor.getLong(1)
                val senderUUID = cursor.getUUID(2)
                val chatUUID = cursor.getUUID(3)
                val data = cursor.getString(4)

                val (status, time) = getLatestMessageStatus(database, messageUUID)

                val chatMessage = createChatMessage(messageUUID, timeCreated, senderUUID, data)
                val chatMessageInfo = ChatMessageInfo(chatMessage, client == senderUUID, chatUUID)

                list += ChatMessageWrapper(chatMessageInfo, status, time)
            }
        }

        return list
    }
}