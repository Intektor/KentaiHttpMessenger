package de.intektor.mercury.chat

import android.database.sqlite.SQLiteDatabase
import de.intektor.mercury.database.getUUID
import de.intektor.mercury_common.chat.MessageStatus

/**
 * @author Intektor
 */
object PendingMessageUtil {

    fun getWaitingMessages(dataBase: SQLiteDatabase): List<ChatMessageInfo> {
        return dataBase.rawQuery("SELECT chat_message.message_uuid, time_created, sender_uuid, chat_uuid, message_data.data, time FROM message_status LEFT JOIN chat_message ON chat_message.message_uuid = message_status.message_uuid LEFT JOIN message_data ON message_data.message_uuid = chat_message.message_uuid WHERE status = ?",
                arrayOf(MessageStatus.WAITING.ordinal.toString())).use { cursor ->
            val list = mutableListOf<ChatMessageInfo>()
            while (cursor.moveToNext()) {
                val messageUUID = cursor.getUUID(0)
                val timeCreated = cursor.getLong(1)
                val senderUUID = cursor.getUUID(2)
                val chatUUID = cursor.getUUID(3)
                val data = cursor.getString(4)

                val message = createChatMessage(messageUUID, timeCreated, senderUUID, data)

                list += ChatMessageInfo(message, true, chatUUID)
            }
            list
        }
    }
}