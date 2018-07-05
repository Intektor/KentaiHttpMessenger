package de.intektor.kentai.kentai.chat

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.fragment.ChatListViewAdapter
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.android.writeMessageWrapper
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.contacts.addContact
import de.intektor.kentai.kentai.firebase.SendService
import de.intektor.kentai.kentai.references.UploadState
import de.intektor.kentai_http_common.chat.*
import de.intektor.kentai_http_common.client_to_server.KeyRequest
import de.intektor.kentai_http_common.client_to_server.UsersRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.server_to_client.KeyResponse
import de.intektor.kentai_http_common.server_to_client.UsersResponse
import de.intektor.kentai_http_common.util.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.Key
import java.security.interfaces.RSAPublicKey
import java.util.*
import kotlin.collections.ArrayList

/**
 * @author Intektor
 */
fun createChat(chatInfo: ChatInfo, dataBase: SQLiteDatabase, clientUUID: UUID) {
    dataBase.compileStatement("INSERT INTO chats (chat_name, chat_uuid, type, unread_messages) VALUES (?, ?, ?, ?)").use { statement ->
        statement.bindString(1, chatInfo.chatName)
        statement.bindString(2, chatInfo.chatUUID.toString())
        statement.bindLong(3, chatInfo.chatType.ordinal.toLong())
        statement.bindLong(4, 0L)
        statement.execute()
    }

    if (chatInfo.chatType == ChatType.TWO_PEOPLE && chatInfo.isUserParticipant(clientUUID)) {
        dataBase.compileStatement("INSERT INTO user_to_chat_uuid (user_uuid, chat_uuid) VALUES(?, ?)").use { statement ->
            statement.bindString(1, chatInfo.participants.first { it.receiverUUID != clientUUID }.receiverUUID.toString())
            statement.bindString(2, chatInfo.chatUUID.toString())
            statement.execute()
        }
    }

    for (i in 0 until chatInfo.participants.size) {
        dataBase.compileStatement("INSERT INTO chat_participants (chat_uuid, participant_uuid, is_active) VALUES(?, ?, ?)").use { statement ->
            statement.bindString(1, chatInfo.chatUUID.toString())
            statement.bindString(2, chatInfo.participants[i].receiverUUID.toString())
            statement.bindLong(3, 1L)
            statement.execute()
        }
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

fun deleteChat(chatUUID: UUID, dataBase: SQLiteDatabase) {
    dataBase.compileStatement("DELETE FROM chat_table WHERE chat_uuid = ?").use { statement ->
        statement.bindString(1, chatUUID.toString())
        statement.execute()
    }
    dataBase.compileStatement("DELETE FROM chats WHERE chat_uuid = ?").use { statement ->
        statement.bindString(1, chatUUID.toString())
        statement.execute()
    }
    dataBase.compileStatement("DELETE FROM user_to_chat_uuid WHERE chat_uuid = ?").use { statement ->
        statement.bindString(1, chatUUID.toString())
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
    statement.bindString(1, message.id)
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

    statement.bindString(1, wrapper.message.id)
    statement.bindLong(2, MessageStatus.WAITING.ordinal.toLong())
    statement.bindLong(3, System.currentTimeMillis())
    statement.execute()
}

/**
 * Deletes a message from a chat, keep in mind that this only affects the database part, UI has to be done manually
 */
fun deleteMessage(chatUUID: UUID, messageUUID: UUID, referenceUUID: UUID, dataBase: SQLiteDatabase) {
    dataBase.compileStatement("DELETE FROM chat_table WHERE chat_uuid = ? AND message_uuid = ?").use { statement ->
        statement.bindString(1, chatUUID.toString())
        statement.bindString(2, messageUUID.toString())
        statement.execute()
    }
    dataBase.compileStatement("DELETE FROM reference_upload_table WHERE reference_uuid = ?").use { statement ->
        statement.bindString(1, referenceUUID.toString())
        statement.execute()
    }
}

fun sendMessageToServer(context: Context, pendingMessages: List<PendingMessage>, dataBase: SQLiteDatabase) {
    for ((message, chatUUID) in pendingMessages) {
        saveMessage(chatUUID, message, dataBase)

        val statement = dataBase.compileStatement("INSERT INTO pending_messages (chat_uuid, message_uuid) VALUES (?, ?)")
        statement.bindString(1, chatUUID.toString())
        statement.bindString(2, message.message.id)

        statement.execute()
    }

    val startServiceIntent = Intent(context, SendService::class.java)
    startServiceIntent.action = ACTION_SEND_MESSAGES

    startServiceIntent.putExtra(KEY_AMOUNT, pendingMessages.size)
    for (i in 0 until pendingMessages.size) {
        startServiceIntent.putExtra("$KEY_CHAT_UUID$i", pendingMessages[i].chatUUID)
        startServiceIntent.putExtra("$KEY_RECEIVER$i", ArrayList(pendingMessages[i].sendTo))
        startServiceIntent.writeMessageWrapper(pendingMessages[i].message, i)
    }

    context.startService(startServiceIntent)
}

fun sendMessageToServer(context: Context, pendingMessage: PendingMessage, dataBase: SQLiteDatabase) {
    sendMessageToServer(context, listOf(pendingMessage), dataBase)
}

fun readChatParticipants(dataBase: SQLiteDatabase, chatUUID: UUID): List<ChatReceiver> {
    dataBase.rawQuery("SELECT chat_participants.participant_uuid, contacts.message_key, chat_participants.is_active FROM chat_participants LEFT JOIN contacts ON contacts.user_uuid = chat_participants.participant_uuid " +
            "WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { cursor ->
        val participantsList = mutableListOf<ChatReceiver>()
        while (cursor.moveToNext()) {
            val participantUUID = UUID.fromString(cursor.getString(0))
            val messageKey = cursor.getString(1)
            val isActive = cursor.getInt(2) == 1
            participantsList.add(ChatReceiver(participantUUID, messageKey?.toKey(), ChatReceiver.ReceiverType.USER, isActive))
        }
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

/**
 * This method always asks the server for the keys, no checking in the database is done.
 * The keys are then written to the database
 */
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

fun requestUsers(userUUIDs: List<UUID>, dataBase: SQLiteDatabase) {
    val gson = genGson()
    val r = httpPost(gson.toJson(UsersRequest(userUUIDs)), UsersRequest.TARGET)
    val response = gson.fromJson(r, UsersResponse::class.java)

    for (user in response.users) {
        addContact(user.userUUID, user.username, dataBase, user.messageKey)
    }
}

/**
 * This method first tries reading the key from the database, if this fails it asks the server for the key.
 * If a new key was found this key gets written to the database
 * @see requestPublicKey
 */
fun requestPublicKey(userUUID: UUID, dataBase: SQLiteDatabase): RSAPublicKey {
    return dataBase.rawQuery("SELECT message_key FROM contacts WHERE user_uuid = ?", arrayOf(userUUID.toString())).use { query ->
        if (query.moveToNext()) {
            if (!query.isNull(0)) {
                val messageKey = query.getString(0)
                if (messageKey.isNotEmpty()) {
                    messageKey.toKey() as RSAPublicKey
                } else {
                    requestPublicKey(listOf(userUUID), dataBase)[userUUID]!!
                }
            } else {
                requestPublicKey(listOf(userUUID), dataBase)[userUUID]!!
            }
        } else {
            requestPublicKey(listOf(userUUID), dataBase)[userUUID]!!
        }
    }
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

fun readChatMessageWrappers(dataBase: SQLiteDatabase, where: String, whereArgs: Array<String>? = null, order: String? = null, limit: String? = null): List<ChatMessageWrapper> {
    dataBase.query("chat_table", arrayOf("message_uuid", "additional_info", "text", "time", "type", "sender_uuid", "client", "reference", "chat_uuid"),
            where, whereArgs, null, null, order, limit).use { query ->
        val list = mutableListOf<ChatMessageWrapper>()
        while (query.moveToNext()) {
            val uuid = query.getString(0)
            val blob = query.getBlob(1)
            val text = query.getString(2)
            val time = query.getLong(3)
            val type = query.getInt(4)
            val sender = query.getString(5)
            val client: Boolean = query.getInt(6) != 0
            val reference = query.getString(7).toUUID()
            val chatUUID = query.getString(8).toUUID()

            val cursor = dataBase.rawQuery("SELECT status, time FROM message_status_change WHERE message_uuid = '$uuid' ORDER BY time DESC LIMIT 1", null)
            cursor.moveToNext()
            val status = MessageStatus.values()[cursor.getInt(0)]
            val timeChange = cursor.getLong(1)
            cursor.close()

            val message = ChatMessageRegistry.create(type)
            message.id = uuid
            message.senderUUID = sender.toUUID()
            message.text = text
            message.timeSent = time
            message.referenceUUID = reference
            message.processAdditionalInfo(blob)

            list.add(ChatMessageWrapper(message, status, client, timeChange, chatUUID))
        }
        return list
    }
}

fun getAmountChatMessages(dataBase: SQLiteDatabase, chatUUID: UUID): Int {
    dataBase.rawQuery("SELECT COUNT(chat_uuid) AS amount FROM chat_table WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { cursor ->
        cursor.moveToNext()
        return cursor.getInt(0)
    }
}

fun readContacts(dataBase: SQLiteDatabase): List<Contact> {
    return dataBase.rawQuery("SELECT username, alias, user_uuid, message_key FROM contacts;", null).use { cursor ->
        val contactList = mutableListOf<Contact>()
        while (cursor.moveToNext()) {
            val username = cursor.getString(0)
            val alias = cursor.getString(1)
            val userUUID = UUID.fromString(cursor.getString(2))
            val messageKey = cursor.getString(3).toKey()
            contactList.add(Contact(username, alias, userUUID, messageKey))
        }
        contactList
    }
}

fun getContact(dataBase: SQLiteDatabase, userUUID: UUID): Contact {
    return dataBase.rawQuery("SELECT username, alias, message_key FROM contacts WHERE user_uuid = ?", arrayOf(userUUID.toString())).use { cursor ->
        cursor.moveToNext()
        val username = cursor.getString(0)
        val alias = cursor.getString(1)
        val messageKey = cursor.getString(2).toKey()
        Contact(username, alias, userUUID, messageKey)
    }
}

fun hasContact(dataBase: SQLiteDatabase, userUUID: UUID): Boolean {
    return dataBase.rawQuery("SELECT COUNT(user_uuid) FROM contacts WHERE user_uuid = ?", arrayOf(userUUID.toString())).use { cursor ->
        cursor.moveToNext()
        cursor.getInt(0) == 1
    }
}

fun readChats(dataBase: SQLiteDatabase, context: Context): List<ChatListViewAdapter.ChatItem> {
    val list = mutableListOf<ChatListViewAdapter.ChatItem>()
    dataBase.rawQuery("SELECT chat_name, chat_uuid, type, unread_messages FROM chats", null).use { cursor ->
        while (cursor.moveToNext()) {
            val chatName = cursor.getString(0)
            val chatUUID = UUID.fromString(cursor.getString(1))
            val chatType = ChatType.values()[cursor.getInt(2)]
            val unreadMessages = cursor.getInt(3)

            val chatInfo = ChatInfo(chatUUID, chatName, chatType, readChatParticipants(dataBase, chatUUID))

            val wrapperList = readChatMessageWrappers(dataBase, "chat_uuid = '$chatUUID'", null, "time DESC", "1")
            val finalWrapper = if (wrapperList.isEmpty()) {
                ChatMessageWrapper(ChatMessageText(context.getString(R.string.overview_activity_no_message), UUID.randomUUID(), 0L), MessageStatus.WAITING, true, 0L, chatUUID)
            } else {
                wrapperList.first()
            }
            list.add(ChatListViewAdapter.ChatItem(chatInfo, finalWrapper, unreadMessages, !chatInfo.hasUnitializedUser()))
        }
    }
    return list
}

fun getChatInfo(chatUUID: UUID, dataBase: SQLiteDatabase): ChatInfo? {
    return dataBase.rawQuery("SELECT chat_name, type FROM chats WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { cursor ->
        if (cursor.moveToNext()) {
            val chatName = cursor.getString(0)
            val chatType = ChatType.values()[cursor.getInt(1)]
            ChatInfo(chatUUID, chatName, chatType, readChatParticipants(dataBase, chatUUID))
        } else null
    }
}

fun setUnreadMessages(dataBase: SQLiteDatabase, chatUUID: UUID, unreadMessages: Int) {
    dataBase.execSQL("UPDATE chats SET unread_messages = $unreadMessages WHERE chat_uuid = ?", arrayOf(chatUUID.toString()))
}

fun incrementUnreadMessages(dataBase: SQLiteDatabase, chatUUID: UUID) {
    dataBase.execSQL("UPDATE chats SET unread_messages = unread_messages + 1 WHERE chat_uuid = ?", arrayOf(chatUUID.toString()))
}

fun writeMessageWrapper(dataOut: DataOutputStream, wrapper: ChatMessageWrapper) {
    dataOut.writeInt(ChatMessageRegistry.getID(wrapper.message.javaClass))

    dataOut.writeUTF(wrapper.message.aesKey)
    dataOut.writeUTF(wrapper.message.initVector)
    dataOut.writeUTF(wrapper.message.signature)
    dataOut.writeUTF(wrapper.message.text)
    dataOut.writeUTF(wrapper.message.id)

    dataOut.writeUUID(wrapper.message.senderUUID)
    dataOut.writeUUID(wrapper.message.referenceUUID)

    dataOut.writeLong(wrapper.message.timeSent)
    val additionalInfo = wrapper.message.getAdditionalInfo()
    dataOut.writeBoolean(additionalInfo != null)
    if (additionalInfo != null) {
        dataOut.writeInt(additionalInfo.size)
        dataOut.write(additionalInfo)
    }

    dataOut.writeBoolean(wrapper.client)
    dataOut.writeInt(wrapper.status.ordinal)
    dataOut.writeLong(wrapper.statusChangeTime)
    dataOut.writeUUID(wrapper.chatUUID)
}

fun readMessageWrapper(dataIn: DataInputStream): ChatMessageWrapper {
    val message = ChatMessageRegistry.create(dataIn.readInt())
    message.aesKey = dataIn.readUTF()
    message.initVector = dataIn.readUTF()
    message.signature = dataIn.readUTF()
    message.text = dataIn.readUTF()
    message.id = dataIn.readUTF()

    message.senderUUID = dataIn.readUUID()
    message.referenceUUID = dataIn.readUUID()

    message.timeSent = dataIn.readLong()

    if (dataIn.readBoolean()) {
        val additionalInfo = ByteArray(dataIn.readInt())
        dataIn.read(additionalInfo)
        message.processAdditionalInfo(additionalInfo)
    }

    val client = dataIn.readBoolean()
    val status = MessageStatus.values()[dataIn.readInt()]
    val statusChangeTime = dataIn.readLong()
    val chatUUID = dataIn.readUUID()
    return ChatMessageWrapper(message, status, client, statusChangeTime, chatUUID)
}

fun getAmountMessageStatusChange(dataBase: SQLiteDatabase): Int {
    dataBase.rawQuery("SELECT COUNT(id) FROM message_status_change", arrayOf()).use { cursor ->
        cursor.moveToNext()
        return cursor.getInt(0)
    }
}

fun readMessageStatusChange(dataBase: SQLiteDatabase, from: Int, limit: Int): List<MessageStatusChange> {
    dataBase.rawQuery("SELECT message_uuid, status, time FROM message_status_change WHERE id > $from -1 ORDER BY id ASC LIMIT $limit", null).use { cursor ->
        val list = mutableListOf<MessageStatusChange>()
        while (cursor.moveToNext()) {
            list.add(MessageStatusChange(cursor.getString(0).toUUID(), MessageStatus.values()[cursor.getInt(1)], cursor.getLong(2)))
        }
        return list
    }
}

data class MessageStatusChange(val messageUUID: UUID, val status: MessageStatus, val time: Long)

fun getGroupMembers(dataBase: SQLiteDatabase, chatUUID: UUID): List<GroupMember> {
    dataBase.rawQuery("SELECT group_role_table.user_uuid, group_role_table.role, contacts.username, contacts.alias " +
            "FROM group_role_table " +
            "LEFT JOIN contacts ON group_role_table.user_uuid = contacts.user_uuid " +
            "WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { query ->
        val list = mutableListOf<GroupMember>()
        while (query.moveToNext()) {
            val userUUID = query.getString(0).toUUID()
            val role = GroupRole.values()[query.getInt(1)]
            val username = query.getString(2)
            val alias = query.getString(3)
            list.add(GroupMember(Contact(username, alias, userUUID, null), role))
        }
        return list
    }
}

fun getGroupRole(dataBase: SQLiteDatabase, chatUUID: UUID, userUUID: UUID): GroupRole {
    return dataBase.rawQuery("SELECT role FROM group_role_table WHERE chat_uuid = ? AND user_uuid = ?", arrayOf(chatUUID.toString(), userUUID.toString())).use { cursor ->
        if (cursor.moveToNext()) {
            GroupRole.values()[cursor.getInt(0)]
        } else throw IllegalArgumentException("No user found!")
    }
}

fun saveMessageStatusChange(dataBase: SQLiteDatabase, msc: MessageStatusChange) {
    dataBase.compileStatement("INSERT INTO message_status_change (message_uuid, status, time) VALUES(?, ?, ?)").use { statement ->
        statement.bindString(1, msc.messageUUID.toString())
        statement.bindLong(2, msc.status.ordinal.toLong())
        statement.bindLong(3, msc.time)
        statement.execute()
    }
}

fun setReferenceState(dataBase: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, fileType: FileType, state: UploadState) {
    try {
        dataBase.compileStatement("INSERT INTO reference_upload_table (chat_uuid, reference_uuid, file_type, state) VALUES(?, ?, ?, ?)").use { statement ->
            statement.bindString(1, chatUUID.toString())
            statement.bindString(2, referenceUUID.toString())
            statement.bindLong(3, fileType.ordinal.toLong())
            statement.bindLong(4, state.ordinal.toLong())
            statement.execute()
        }
    } catch (t: Throwable) {
        dataBase.compileStatement("UPDATE reference_upload_table SET state = ? WHERE reference_uuid = ?").use { statement ->
            statement.bindLong(1, state.ordinal.toLong())
            statement.bindString(2, referenceUUID.toString())
            statement.execute()
        }
    }
}

fun getReferenceState(dataBase: SQLiteDatabase, chatUUID: UUID?, referenceUUID: UUID): UploadState {
    if (chatUUID != null) {
        dataBase.rawQuery("SELECT state FROM reference_upload_table WHERE chat_uuid = ? AND reference_uuid = ?", arrayOf(chatUUID.toString(), referenceUUID.toString())).use { cursor ->
            if (cursor.moveToNext()) {
                return UploadState.values()[cursor.getInt(0)]
            }
        }
    } else {
        dataBase.rawQuery("SELECT state FROM reference_upload_table WHERE reference_uuid = ?", arrayOf(referenceUUID.toString())).use { cursor ->
            if (cursor.moveToNext()) {
                return UploadState.values()[cursor.getInt(0)]
            }
        }
    }
    return UploadState.NOT_STARTED
}

fun readClientContact(context: Context): Contact {
    val userInfo = internalFile("username.info", context)
    return if (userInfo.exists()) {
        DataInputStream(userInfo.inputStream()).use { input ->
            Contact(input.readUTF(), "", input.readUUID(), null)
        }
    } else throw IllegalStateException("No client user!")
}

fun hasClient(context: Context) = internalFile("username.info", context).exists()

fun load20Messages(kentaiClient: KentaiClient, chatUUID: UUID, time: Long, greater: Boolean): List<ChatMessageWrapper> {
    val list = readChatMessageWrappers(kentaiClient.dataBase, "chat_uuid = '$chatUUID' AND time ${if (greater) "<" else ">"} $time", null, "time DESC", "20")
    return list.reversed()
}

fun getUserChat(dataBase: SQLiteDatabase, contact: Contact, kentaiClient: KentaiClient): ChatInfo {
    return dataBase.rawQuery("SELECT chat_uuid FROM user_to_chat_uuid WHERE user_uuid = ?", arrayOf(contact.userUUID.toString())).use { query ->
        val chatUUID: UUID
        val wasRandom: Boolean
        if (query.moveToNext()) {
            chatUUID = query.getString(0).toUUID()
            wasRandom = false
        } else {
            chatUUID = getChatUUIDForUserChat(kentaiClient.userUUID, contact.userUUID)
            wasRandom = true
        }
        val chatInfo = ChatInfo(chatUUID, contact.name, ChatType.TWO_PEOPLE,
                listOf(ChatReceiver(contact.userUUID, contact.message_key, ChatReceiver.ReceiverType.USER), ChatReceiver(kentaiClient.userUUID, null, ChatReceiver.ReceiverType.USER, true)))
        if (wasRandom) createChat(chatInfo, kentaiClient.dataBase, kentaiClient.userUUID)
        chatInfo
    }
}

fun hasMessage(dataBase: SQLiteDatabase, messageUUID: UUID): Boolean {
    return dataBase.rawQuery("SELECT COUNT(message_uuid) FROM chat_table WHERE message_uuid = ?", arrayOf(messageUUID.toString())).use { cursor ->
        cursor.moveToNext()
        cursor.getInt(0) == 1
    }
}

fun getChatUUIDForUserChat(firstUserUUID: UUID, secondUserUUID: UUID): UUID {
    val f = if (firstUserUUID.hashCode() < secondUserUUID.hashCode()) firstUserUUID else secondUserUUID
    val s = if (firstUserUUID.hashCode() < secondUserUUID.hashCode()) secondUserUUID else firstUserUUID
    return UUID.nameUUIDFromBytes(Hashing.sha256().hashBytes((f.toString() + s.toString()).toByteArray()).asBytes())
}

fun getCountNotificationMessages(dataBase: SQLiteDatabase): Int {
    return dataBase.rawQuery("SELECT COUNT(message_uuid) FROM notification_messages", null).use { query2 ->
        if (query2.moveToNext()) {
            query2.getInt(0)
        } else 0
    }
}

fun ChatType.isGroup(): Boolean = this == ChatType.GROUP_CENTRALIZED || this == ChatType.GROUP_DECENTRALIZED

/**
 * @param clientMessageUUID the message uuid this client has saved
 * @param userMessageUUID the message uuid that was sent to the receiver
 */
fun setGroupMessageClientMessageUUID(clientMessageUUID: UUID, userUUID: UUID, userMessageUUID: UUID, dataBase: SQLiteDatabase) {
    val count = dataBase.rawQuery("SELECT COUNT(message_uuid) FROM group_message_uuid WHERE message_uuid = ?", arrayOf(userMessageUUID.toString())).use { cursor ->
        cursor.moveToNext()
        cursor.getInt(0)
    }

    if (count == 0) {
        dataBase.compileStatement("INSERT INTO group_message_uuid (message_uuid, user_uuid, client_message_uuid) VALUES (?, ?, ?)").use { statement ->
            statement.bindString(1, userMessageUUID.toString())
            statement.bindString(2, userUUID.toString())
            statement.bindString(3, clientMessageUUID.toString())
            statement.execute()
        }
    } else {
        dataBase.compileStatement("UPDATE group_message_uuid SET message_uuid = ? WHERE user_uuid = ? AND client_message_uuid = ?").use { statement ->
            statement.bindString(1, userMessageUUID.toString())
            statement.bindString(2, userUUID.toString())
            statement.bindString(3, clientMessageUUID.toString())
            statement.execute()
        }
    }
}

fun getGroupMessageClientMessageUUID(userUUID: UUID, userMessageUUID: UUID, dataBase: SQLiteDatabase): UUID? {
    return dataBase.rawQuery("SELECT message_uuid FROM group_message_uuid WHERE user_uuid = ? AND client_message_uuid = ?", arrayOf(userUUID.toString(), userMessageUUID.toString())).use { cursor ->
        if (cursor.moveToNext()) {
            cursor.getString(0).toUUID()
        } else null
    }
}

fun getChatType(chatUUID: UUID, dataBase: SQLiteDatabase): ChatType? {
    return dataBase.rawQuery("SELECT type FROM chats WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { cursor ->
        if (cursor.moveToNext()) {
            ChatType.values()[cursor.getInt(0)]
        } else null
    }
}

fun getGroupKey(chatUUID: UUID, dataBase: SQLiteDatabase): Key? {
    return dataBase.rawQuery("SELECT group_key FROM group_key_table WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { cursor ->
        if (cursor.moveToNext()) {
            cursor.getString(0).toAESKey()
        } else null
    }
}