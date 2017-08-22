package de.intektor.kentai.kentai.chat

import android.database.sqlite.SQLiteDatabase
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.MessageStatus

/**
 * @author Intektor
 */
fun createChat(chatInfo: ChatInfo, dataBase: SQLiteDatabase) {
    var statement = dataBase.compileStatement("INSERT INTO chats (chat_name, chat_uuid, type, unread_messages) VALUES (?, ?, ?, ?)")
    statement.bindString(1, chatInfo.chatName)
    statement.bindString(2, chatInfo.chatUUID.toString())
    statement.bindLong(3, chatInfo.chatType.ordinal.toLong())
    statement.bindLong(4, 0L)
    statement.execute()


    for (i in 0 until chatInfo.participants.size) {
        statement = dataBase.compileStatement("INSERT INTO chat_participants (chat_uuid, participant_uuid) VALUES(?, ?)")
        statement.bindString(1, chatInfo.chatUUID.toString())
        statement.bindString(2, (chatInfo.participants[i]))
        statement.execute()
    }
}

fun saveMessage(chatInfo: ChatInfo, wrapper: ChatMessageWrapper, dataBase: SQLiteDatabase) {
    val message = wrapper.message
    val additionalInfo = message.getAdditionalInfo()
    var statement = dataBase.compileStatement("INSERT INTO chat_table (message_uuid, text, time, type, sender_uuid, chat_uuid, client" +
            "${if (additionalInfo != null) ", `additional_info`" else ""}) VALUES (?, ?, ?, ?, ?, ?, ?${if (additionalInfo != null) ", ?" else ""})")
    statement.bindString(1, message.id.toString())
    statement.bindString(2, message.text)
    statement.bindLong(3, System.currentTimeMillis())
    statement.bindLong(4, ChatMessageRegistry.getID(message.javaClass).toLong())
    statement.bindString(5, message.senderUUID.toString())
    statement.bindString(6, chatInfo.chatUUID.toString())
    statement.bindLong(7, if (!wrapper.client) 0L else 1L)
    if (additionalInfo != null) {
        statement.bindBlob(8, additionalInfo)
    }
    statement.execute()

    statement = dataBase.compileStatement("INSERT INTO message_status_change (message_uuid, status, time) VALUES(?, ?, ?)")

    statement.bindString(1, wrapper.message.id.toString())
    statement.bindLong(2, MessageStatus.WAITING.ordinal.toLong())
    statement.bindLong(3, System.currentTimeMillis())
    statement.execute()
}