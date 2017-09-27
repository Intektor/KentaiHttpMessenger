package de.intektor.kentai.kentai.chat

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import com.google.common.io.BaseEncoding
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.android.writeMessageWrapper
import de.intektor.kentai.kentai.firebase.SendService
import de.intektor.kentai.kentai.httpPost
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.GroupRole
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.client_to_server.KeyRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.KeyResponse
import de.intektor.kentai_http_common.util.toKey
import java.security.Key
import java.security.interfaces.RSAPublicKey
import java.util.*
import kotlin.collections.ArrayList

/**
 * @author Intektor
 */
fun createChat(chatInfo: ChatInfo, dataBase: SQLiteDatabase, clientUUID: UUID) {
    var statement = dataBase.compileStatement("INSERT INTO chats (chat_name, chat_uuid, type, unread_messages) VALUES (?, ?, ?, ?)")
    statement.bindString(1, chatInfo.chatName)
    statement.bindString(2, chatInfo.chatUUID.toString())
    statement.bindLong(3, chatInfo.chatType.ordinal.toLong())
    statement.bindLong(4, 0L)
    statement.execute()

    if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
        dataBase.compileStatement("INSERT INTO user_to_chat_uuid (user_uuid, chat_uuid) VALUES(?, ?)").use { statement ->
            statement.bindString(1, chatInfo.participants.first { it.receiverUUID != clientUUID }.receiverUUID.toString())
            statement.bindString(2, chatInfo.chatUUID.toString())
            statement.execute()
        }
    }

    for (i in 0 until chatInfo.participants.size) {
        statement = dataBase.compileStatement("INSERT INTO chat_participants (chat_uuid, participant_uuid, is_active) VALUES(?, ?, ?)")
        statement.bindString(1, chatInfo.chatUUID.toString())
        statement.bindString(2, chatInfo.participants[i].receiverUUID.toString())
        statement.bindLong(3, 1L)
        statement.execute()
    }
}

fun createGroupChat(chatInfo: ChatInfo, roleMap: Map<UUID, GroupRole>, groupKey: Key, dataBase: SQLiteDatabase, clientUUID: UUID) {
    createChat(chatInfo, dataBase, clientUUID)
    putGroupRoles(roleMap, chatInfo.chatUUID, dataBase)

    dataBase.compileStatement("INSERT INTO group_key_table (chat_uuid, group_key) VALUES(?, ?)").use { statement ->
        statement.bindString(1, chatInfo.chatUUID.toString())
        statement.bindString(2, BaseEncoding.base64().encode(groupKey.encoded))
        statement.execute()
    }
}

fun putGroupRoles(roleMap: Map<UUID, GroupRole>, chatUUID: UUID, dataBase: SQLiteDatabase) {
    dataBase.execSQL("DELETE FROM group_role_table WHERE chat_uuid = ?", arrayOf(chatUUID.toString()))
    for ((userUUID, role) in roleMap) {
        dataBase.compileStatement("INSERT INTO group_role_table (chat_uuid, user_uuid, role) VALUES (?, ?, ?)").use { statement ->
            statement.bindString(1, chatUUID.toString())
            statement.bindString(2, userUUID.toString())
            statement.bindLong(3, role.ordinal.toLong())
            statement.execute()
        }
    }
}

fun saveMessage(chatUUID: UUID, wrapper: ChatMessageWrapper, dataBase: SQLiteDatabase) {
    val message = wrapper.message
    if (!message.shouldBeStored()) return
    val additionalInfo = message.getAdditionalInfo()
    var statement = dataBase.compileStatement("INSERT INTO chat_table (message_uuid, text, time, type, sender_uuid, chat_uuid, client, reference" +
            "${if (additionalInfo != null) ", `additional_info`" else ""}) VALUES (?, ?, ?, ?, ?, ?, ?, ?${if (additionalInfo != null) ", ?" else ""})")
    statement.bindString(1, message.id.toString())
    statement.bindString(2, message.text)
    statement.bindLong(3, System.currentTimeMillis())
    statement.bindLong(4, ChatMessageRegistry.getID(message.javaClass).toLong())
    statement.bindString(5, message.senderUUID.toString())
    statement.bindString(6, chatUUID.toString())
    statement.bindLong(7, if (!wrapper.client) 0L else 1L)
    statement.bindString(8, wrapper.message.referenceUUID.toString())
    if (additionalInfo != null) {
        statement.bindBlob(9, additionalInfo)
    }
    statement.execute()

    statement = dataBase.compileStatement("INSERT INTO message_status_change (message_uuid, status, time) VALUES(?, ?, ?)")

    statement.bindString(1, wrapper.message.id.toString())
    statement.bindLong(2, MessageStatus.WAITING.ordinal.toLong())
    statement.bindLong(3, System.currentTimeMillis())
    statement.execute()
}

/**
 * Deletes a message from a chat, keep in mind that this only affects the database part, UI has to be done manually
 */
fun deleteMessage(chatUUID: UUID, messageUUID: UUID, dataBase: SQLiteDatabase) {
    dataBase.compileStatement("DELETE FROM chat_table WHERE chat_uuid = ? AND message_uuid = ?").use { statement ->
        statement.bindString(1, chatUUID.toString())
        statement.bindString(2, messageUUID.toString())
        statement.execute()
    }
}

fun sendMessageToServer(context: Context, pendingMessages: List<PendingMessage>) {
    for ((message, chatUUID) in pendingMessages) {
        saveMessage(chatUUID, message, KentaiClient.INSTANCE.dataBase)

        val statement = KentaiClient.INSTANCE.dataBase.compileStatement("INSERT INTO pending_messages (chat_uuid, message_uuid) VALUES (?, ?)")
        statement.bindString(1, chatUUID.toString())
        statement.bindString(2, message.message.id.toString())

        statement.execute()
    }

    val startServiceIntent = Intent(context, SendService::class.java)
    context.startService(startServiceIntent)

    val broadcastIntent = Intent("de.intektor.kentai.sendChatMessage")
    broadcastIntent.putExtra("amount", pendingMessages.size)
    for (i in 0 until pendingMessages.size) {
        broadcastIntent.putExtra("chatUUID$i", pendingMessages[i].chatUUID)
        broadcastIntent.putExtra("receiver$i", ArrayList(pendingMessages[i].sendTo))
        broadcastIntent.writeMessageWrapper(pendingMessages[i].message, i)
    }

    context.sendBroadcast(broadcastIntent)
}

fun sendMessageToServer(context: Context, pendingMessage: PendingMessage) {
    sendMessageToServer(context, listOf(pendingMessage))
}

fun readChatParticipants(dataBase: SQLiteDatabase, chatUUID: UUID): List<ChatReceiver> {
    dataBase.rawQuery("SELECT chat_participants.participant_uuid, contacts.message_key, chat_participants.is_active FROM chat_participants LEFT JOIN contacts ON contacts.user_uuid = chat_participants.participant_uuid " +
            "WHERE chat_uuid = '$chatUUID'", null).use { cursor ->
        val participantsList = mutableListOf<ChatReceiver>()
        while (cursor.moveToNext()) {
            val participantUUID = UUID.fromString(cursor.getString(0))
            val messageKey = cursor.getString(1)
            val isActive = cursor.getInt(2) == 1
            participantsList.add(ChatReceiver(participantUUID, messageKey?.toKey(), ChatReceiver.ReceiverType.USER, isActive))
        }
        cursor.close()
        return participantsList
    }
}

fun updateMessageStatus(dataBase: SQLiteDatabase, messageUUID: UUID, messageStatus: MessageStatus, time: Long) {
    val statement = dataBase.compileStatement("INSERT INTO message_status_change (message_uuid, status, time) VALUES(?, ?, ?)")
    statement.bindString(1, messageUUID.toString())
    statement.bindLong(2, messageStatus.ordinal.toLong())
    statement.bindLong(3, time)
    statement.execute()
}

fun requestPublicKey(userUUIDs: List<UUID>, dataBase: SQLiteDatabase): Map<UUID, RSAPublicKey> {
    val gson = genGson()
    val response = gson.fromJson(httpPost(gson.toJson(KeyRequest(userUUIDs)), KeyRequest.TARGET), KeyResponse::class.java)

    for ((key, entry) in response.keys) {
        val statement = dataBase.compileStatement("UPDATE contacts SET message_key = ? WHERE user_uuid = ?")
        statement.bindString(1, BaseEncoding.base64().encode(entry.encoded))
        statement.bindString(2, key.toString())
        statement.execute()
    }
    return response.keys
}

fun addChatParticipant(chatUUID: UUID, userUUID: UUID, dataBase: SQLiteDatabase) {
    var exists = false
    dataBase.rawQuery("SELECT chat_uuid FROM chat_participants WHERE chat_uuid = ? AND participant_uuid = ?", arrayOf(chatUUID.toString(), userUUID.toString())).use { query ->
        exists = query.moveToNext()
    }

    if (exists) {
        dataBase.compileStatement("UPDATE chat_participants SET is_active = 1 WHERE chat_uuid = ? AND participant_uuid = ?").use { statement ->
            statement.bindString(1, chatUUID.toString())
            statement.bindString(2, userUUID.toString())
            statement.execute()
        }
    } else {
        dataBase.compileStatement("INSERT INTO chat_participants (chat_uuid, participant_uuid, is_active) VALUES(?, ?, ?)").use { statement ->
            statement.bindString(1, chatUUID.toString())
            statement.bindString(2, userUUID.toString())
            statement.bindLong(3, 1L)
            statement.execute()
        }
    }
}