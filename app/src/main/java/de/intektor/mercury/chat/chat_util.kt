package de.intektor.mercury.chat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.chat.model.ChatReceiver
import de.intektor.mercury.chat.model.GroupMember
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.database.bindUUID
import de.intektor.mercury.database.getEnum
import de.intektor.mercury.database.getUUID
import de.intektor.mercury.database.isValuePresent
import de.intektor.mercury.io.ChatMessageService
import de.intektor.mercury.io.HttpManager
import de.intektor.mercury.io.PendingMessageUtil
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.ui.overview_activity.fragment.ChatListViewAdapter
import de.intektor.mercury.util.Logger
import de.intektor.mercury.util.internalFile
import de.intektor.mercury_common.chat.*
import de.intektor.mercury_common.client_to_server.UsersRequest
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.server_to_client.UsersResponse
import de.intektor.mercury_common.util.readUUID
import de.intektor.mercury_common.util.toAESKey
import de.intektor.mercury_common.util.toKey
import de.intektor.mercury_common.util.toUUID
import java.io.DataInputStream
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */

private const val TAG = "chatutil"

fun createChat(chatInfo: ChatInfo, dataBase: SQLiteDatabase, clientUUID: UUID) {
    dataBase.compileStatement("INSERT INTO chats (chat_name, chat_uuid, type) VALUES (?, ?, ?)").use { statement ->
        statement.bindString(1, chatInfo.chatName)
        statement.bindString(2, chatInfo.chatUUID.toString())
        statement.bindLong(3, chatInfo.chatType.ordinal.toLong())
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

fun deleteChat(context: Context, chatUUID: UUID, dataBase: SQLiteDatabase) {
    dataBase.compileStatement("DELETE FROM chat_message WHERE chat_uuid = ?").use { statement ->
        statement.bindUUID(1, chatUUID)
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
    ReferenceUtil.dropChatReferences(context, dataBase, chatUUID)
}

fun hasChat(chatUUID: UUID, dataBase: SQLiteDatabase) = dataBase.isValuePresent("chats", "chat_uuid", chatUUID)

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

fun saveMessage(context: Context, dataBase: SQLiteDatabase, message: ChatMessage, chatUUID: UUID) {
    val messageCore = message.messageCore

    val existent = dataBase.isValuePresent("chat_message", "message_uuid", messageCore.messageUUID) ||
            dataBase.isValuePresent("message_data", "message_uuid", messageCore.messageUUID)

    if (existent) {
        Logger.warning(TAG, "Tried writing message to database that is already present, messageUUID=${messageCore.messageUUID}. Skipping writing")
        return
    }

    dataBase.compileStatement("INSERT INTO chat_message(message_uuid, time_created, sender_uuid, chat_uuid) VALUES(?, ?, ?, ?)").use { statement ->
        statement.bindUUID(1, messageCore.messageUUID)
        statement.bindLong(2, messageCore.timeCreated)
        statement.bindUUID(3, messageCore.senderUUID)
        statement.bindUUID(4, chatUUID)
        statement.execute()
    }

    val serializedData = genGson().toJson(message.messageData, MessageData::class.java)

    dataBase.compileStatement("INSERT INTO message_data(message_uuid, data, data_type) VALUES (?, ?, ?)").use { statement ->
        statement.bindUUID(1, messageCore.messageUUID)
        statement.bindString(2, serializedData)
        statement.bindLong(3, MessageDataRegistry.getID(message.messageData.javaClass)?.toLong()
                ?: -1L)
        statement.execute()
    }

    MessageUtil.saveMessageLookup(context, dataBase, message)
}

/**
 * Deletes a message from a chat, keep in mind that this only affects the database part, UI has to be done manually
 */
fun deleteMessage(dataBase: SQLiteDatabase, messageUUID: UUID, chatUUID: UUID) {
    dataBase.compileStatement("DELETE FROM chat_message WHERE chat_uuid = ? AND message_uuid = ?").use { statement ->
        statement.bindString(1, chatUUID.toString())
        statement.bindString(2, messageUUID.toString())
        statement.execute()
    }
    //TODO: Delete upload of reference
}

fun sendMessageToServer(context: Context, dataBase: SQLiteDatabase, pendingMessages: List<PendingMessage>) {
    for ((message, chatUUID) in pendingMessages) {
        saveMessage(context, dataBase, message, chatUUID)

        updateMessageStatus(dataBase, message.messageCore.messageUUID, MessageStatus.WAITING, System.currentTimeMillis())

        PendingMessageUtil.queueMessage(dataBase, message.messageCore.messageUUID)
    }

    ChatMessageService.ActionSendMessages.launch(context, pendingMessages)
}

fun sendMessageToServer(context: Context, pendingMessage: PendingMessage, dataBase: SQLiteDatabase) {
    sendMessageToServer(context, dataBase, listOf(pendingMessage))
}

fun readChatParticipants(dataBase: SQLiteDatabase, chatUUID: UUID): List<ChatReceiver> {
    dataBase.rawQuery("SELECT chat_participants.participant_uuid, contacts.message_key, chat_participants.is_active FROM chat_participants LEFT JOIN contacts ON contacts.user_uuid = chat_participants.participant_uuid " +
            "WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { cursor ->
        val participantsList = mutableListOf<ChatReceiver>()
        while (cursor.moveToNext()) {
            val participantUUID = cursor.getUUID(0)
            val messageKey = cursor.getString(1)
            val isActive = cursor.getInt(2) == 1
            participantsList.add(ChatReceiver(participantUUID, messageKey?.toKey(), ChatReceiver.ReceiverType.USER, isActive))
        }
        return participantsList
    }
}

fun updateMessageStatus(dataBase: SQLiteDatabase, messageUUID: UUID, messageStatus: MessageStatus, time: Long) {
    val statement = dataBase.compileStatement("INSERT INTO message_status (message_uuid, status, time) VALUES(?, ?, ?)")
    statement.bindString(1, messageUUID.toString())
    statement.bindLong(2, messageStatus.ordinal.toLong())
    statement.bindLong(3, time)
    statement.execute()
}

fun getLatestMessageStatus(dataBase: SQLiteDatabase, messageUUID: UUID): MessageStatusHolder {
    return dataBase.rawQuery("SELECT status, time FROM message_status WHERE message_uuid = ? ORDER BY time DESC LIMIT 1", arrayOf(messageUUID.toString())).use { cursor ->
        if (cursor.moveToNext()) {
            val status = cursor.getEnum<MessageStatus>(0)
            val time = cursor.getLong(1)
            MessageStatusHolder(status, time)
        } else throw IllegalStateException("A message must have a latest message status")
    }
}

data class MessageStatusHolder(val status: MessageStatus, val time: Long)

fun requestUsers(userUUIDs: List<UUID>, dataBase: SQLiteDatabase) {
    val gson = genGson()
    val r = HttpManager.post(gson.toJson(UsersRequest(userUUIDs)), UsersRequest.TARGET)
    val response = gson.fromJson(r, UsersResponse::class.java)

    for (user in response.users) {
        ContactUtil.addContact(user.userUUID, user.username, dataBase, user.messageKey)
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

fun getChatMessages(context: Context, dataBase: SQLiteDatabase, where: String, whereArgs: Array<String>? = null, order: String? = null, limit: String? = null): List<ChatMessageWrapper> {
    return dataBase.rawQuery("SELECT chat_message.message_uuid, time_created, sender_uuid, chat_uuid, data FROM chat_message " +
            "LEFT JOIN message_data ON chat_message.message_uuid = message_data.message_uuid " +
            "WHERE $where " +
            "${if (order != null) "ORDER BY $order" else ""} ${if (limit != null) "LIMIT $limit" else ""}", whereArgs).use { cursor ->
        val list = mutableListOf<ChatMessageWrapper>()
        while (cursor.moveToNext()) {
            val messageUUID = cursor.getUUID(0)
            val timeCreated = cursor.getLong(1)
            val senderUUID = cursor.getUUID(2)
            val chatUUID = cursor.getUUID(3)
            val data = cursor.getString(4)

            val (status, time) = getLatestMessageStatus(dataBase, messageUUID)

            val chatMessage = createChatMessage(messageUUID, timeCreated, senderUUID, data)
            val chatMessageInfo = ChatMessageInfo(chatMessage, ClientPreferences.getClientUUID(context) == senderUUID, chatUUID)

            list += ChatMessageWrapper(chatMessageInfo, status, time)
        }
        list
    }
}

fun createChatMessage(messageUUID: UUID, timeCreated: Long, senderUUID: UUID, data: String): ChatMessage {
    val core = MessageCore(senderUUID, timeCreated, messageUUID)
    val messageData = genGson().fromJson(data, MessageData::class.java)

    return ChatMessage(core, messageData)
}

fun getAmountChatMessages(dataBase: SQLiteDatabase, chatUUID: UUID, limit: Int = 20): Int {
    dataBase.rawQuery("SELECT COUNT(chat_uuid) AS amount FROM chat_table WHERE chat_uuid = ? LIMIT $limit", arrayOf(chatUUID.toString())).use { cursor ->
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
            val messageKey = cursor.getString(3)?.toKey()
            if (messageKey == null) {
                Logger.warning(TAG, "Tried reading a contact without a given key, are you in a test environment?")
            }
            contactList.add(Contact(username, alias, userUUID, messageKey))
        }
        contactList
    }
}

fun getContact(dataBase: SQLiteDatabase, userUUID: UUID): Contact {
    return dataBase.rawQuery("SELECT username, alias, message_key FROM contacts WHERE user_uuid = ?", arrayOf(userUUID.toString())).use { cursor ->
        if (!cursor.moveToNext()) throw IllegalStateException("No such contact found with userUUID=$userUUID")
        val username = cursor.getString(0)
        val alias: String? = cursor.getString(1)
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
    dataBase.rawQuery("SELECT chat_name, chat_uuid, type FROM chats", null).use { cursor ->
        while (cursor.moveToNext()) {
            val chatName = cursor.getString(0)
            val chatUUID = UUID.fromString(cursor.getString(1))
            val chatType = ChatType.values()[cursor.getInt(2)]

            val chatInfo = ChatInfo(chatUUID, chatName, chatType, readChatParticipants(dataBase, chatUUID))

            val messageList = getChatMessages(context, dataBase, "chat_uuid = '$chatUUID'", null, "time_created DESC", "1")

            list.add(ChatListViewAdapter.ChatItem(chatInfo, messageList.firstOrNull(), ChatUtil.getUnreadMessagesFromChat(dataBase, chatUUID), !chatInfo.hasUnitializedUser()))
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
    val hasEntry = dataBase.rawQuery("SELECT COUNT(chat_uuid) FROM chat_unread_messages WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { query ->
        query.moveToNext()
        query.getInt(0) > 0
    }
    if (hasEntry) {
        dataBase.execSQL("UPDATE chat_unread_messages SET amount = ? WHERE chat_uuid = ?", arrayOf(unreadMessages.toString(), chatUUID.toString()))
    } else {
        dataBase.execSQL("INSERT INTO chat_unread_messages (chat_uuid, amount) VALUES(?, ?)", arrayOf(chatUUID.toString(), unreadMessages.toString()))
    }
}

fun incrementUnreadMessages(dataBase: SQLiteDatabase, chatUUID: UUID) {
    dataBase.execSQL("UPDATE chats SET unread_messages = unread_messages + 1 WHERE chat_uuid = ?", arrayOf(chatUUID.toString()))
}

fun getAmountMessageStatusChange(dataBase: SQLiteDatabase): Int {
    dataBase.rawQuery("SELECT COUNT(id) FROM message_status", arrayOf()).use { cursor ->
        cursor.moveToNext()
        return cursor.getInt(0)
    }
}

fun readMessageStatusChange(dataBase: SQLiteDatabase, from: Int, limit: Int): List<MessageStatusChange> {
    dataBase.rawQuery("SELECT message_uuid, status, time FROM message_status WHERE id > $from -1 ORDER BY id ASC LIMIT $limit", null).use { cursor ->
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
    dataBase.compileStatement("INSERT INTO message_status (message_uuid, status, time) VALUES(?, ?, ?)").use { statement ->
        statement.bindString(1, msc.messageUUID.toString())
        statement.bindLong(2, msc.status.ordinal.toLong())
        statement.bindLong(3, msc.time)
        statement.execute()
    }
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

fun load20Messages(context: Context, dataBase: SQLiteDatabase, greater: Boolean, chatUUID: UUID, time: Long): List<ChatMessageWrapper> {
    val list = getChatMessages(context, dataBase, "chat_uuid = '$chatUUID' AND time_created ${if (greater) "<" else ">"} $time", null, "time_created DESC", "20")
    return list.reversed()
}

fun getUserChat(context: Context, dataBase: SQLiteDatabase, contact: Contact): ChatInfo {
    val clientUUID = ClientPreferences.getClientUUID(context)

    return dataBase.rawQuery("SELECT chat_uuid FROM user_to_chat_uuid WHERE user_uuid = ?", arrayOf(contact.userUUID.toString())).use { query ->
        val chatUUID: UUID
        val wasRandom: Boolean
        if (query.moveToNext()) {
            chatUUID = query.getString(0).toUUID()
            wasRandom = false
        } else {
            chatUUID = getChatUUIDForUserChat(clientUUID, contact.userUUID)
            wasRandom = true
        }
        val chatInfo = ChatInfo(chatUUID, contact.name, ChatType.TWO_PEOPLE,
                listOf(ChatReceiver(contact.userUUID, contact.message_key, ChatReceiver.ReceiverType.USER), ChatReceiver(clientUUID, null, ChatReceiver.ReceiverType.USER, true)))
        if (wasRandom) createChat(chatInfo, dataBase, clientUUID)
        chatInfo
    }
}

fun hasMessage(dataBase: SQLiteDatabase, messageUUID: UUID): Boolean {
    return dataBase.rawQuery("SELECT COUNT(message_uuid) FROM chat_message WHERE message_uuid = ?", arrayOf(messageUUID.toString())).use { cursor ->
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

fun getChatType(dataBase: SQLiteDatabase, chatUUID: UUID): ChatType? {
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