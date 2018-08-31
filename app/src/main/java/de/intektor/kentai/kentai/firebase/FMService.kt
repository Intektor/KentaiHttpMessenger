package de.intektor.kentai.kentai.firebase

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.google.common.io.BaseEncoding
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.android.writeMessageWrapper
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.contacts.addContact
import de.intektor.kentai.kentai.firebase.additional_information.AdditionalInfoRegistry
import de.intektor.kentai.kentai.firebase.additional_information.IAdditionalInfo
import de.intektor.kentai.kentai.firebase.additional_information.info.*
import de.intektor.kentai.kentai.groups.*
import de.intektor.kentai.kentai.references.downloadAudio
import de.intektor.kentai.kentai.references.downloadImage
import de.intektor.kentai.kentai.references.downloadVideo
import de.intektor.kentai_http_common.chat.*
import de.intektor.kentai_http_common.chat.group_modification.ChatMessageGroupModification
import de.intektor.kentai_http_common.chat.group_modification.GroupModification
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationAddUser
import de.intektor.kentai_http_common.client_to_server.FetchMessageRequest
import de.intektor.kentai_http_common.client_to_server.HandledMessagesRequest
import de.intektor.kentai_http_common.client_to_server.UsersRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.FetchMessageResponse
import de.intektor.kentai_http_common.server_to_client.UsersResponse
import de.intektor.kentai_http_common.util.*
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.Key
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import javax.crypto.spec.SecretKeySpec

/**
 * @author Intektor
 */
class FMService : FirebaseMessagingService() {

    private var privateAuthKey: Key? = null
    private var privateMessageKey: RSAPrivateKey? = null
    private var publicMessageKey: Key? = null
    private var userUUID: UUID? = null
    private var username: String? = null

    override fun onCreate() {
        super.onCreate()

        val pKeyFile = File(filesDir.path + "/" + "keys/authKeyPrivate.key")
        if (pKeyFile.exists()) {
            privateAuthKey = readPrivateKey(DataInputStream(pKeyFile.inputStream()))
            privateMessageKey = readPrivateKey(DataInputStream(File(filesDir.path + "/" + "keys/encryptionKeyPrivate.key").inputStream()))
            publicMessageKey = readKey(DataInputStream(File(filesDir.path + "/keys/encryptionKeyPublic.key").inputStream()))
        }

        val userInfoFile = File(filesDir.path + "/" + "username.info")
        if (userInfoFile.exists()) {
            val dataIn = DataInputStream(userInfoFile.inputStream())
            username = dataIn.readUTF()
            userUUID = dataIn.readUUID()
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val type = FCMMessageType.values()[remoteMessage.data["type"]!!.toInt()]
        when (type) {
            FCMMessageType.CHAT_MESSAGE -> try {
                fetchAndProcessMessageData()
            } catch (t: Throwable) {
                Log.e("ERROR", "ERROR", t)
            }
            FCMMessageType.ONLINE_MESSAGE -> {

            }
            FCMMessageType.SERVER_NOTIFICATION -> {

            }
        }
    }

    private fun fetchAndProcessMessageData() {
        val userUUIDEncrypted = userUUID.toString().encryptRSA(privateAuthKey!!)

        val gson = genGson()
        val res = httpPost(gson.toJson(FetchMessageRequest(userUUID.toString(), userUUIDEncrypted)), FetchMessageRequest.TARGET)

        val response = gson.fromJson(res, FetchMessageResponse::class.java)
        val handled = processChatMessage(response)
        if (handled.isNotEmpty()) {
            httpPost(gson.toJson(HandledMessagesRequest(handled, userUUIDEncrypted, userUUID.toString())), HandledMessagesRequest.TARGET)
        }
    }

    private fun processChatMessage(fetched: FetchMessageResponse): List<String> {
        val kentaiClient = applicationContext as KentaiClient
        val dataBase = kentaiClient.dataBase

        val privateMessageKey = privateMessageKey!!

        val chats = fetched.list.groupBy { it.chatUUID.decryptRSA(privateMessageKey).toUUID() }.map { Pair(it.key, it.value.sortedBy { it.timeSent }) }

        val finished = mutableListOf<String>()

        val receivedGroupModificationsAsAdmin = mutableListOf<Triple<GroupModification, Long, UUID>>()

        for (chat in chats) {
            a@ for (fetchedMessage in chat.second) {
                val registryID = fetchedMessage.messageRegistryID.decryptRSA(privateMessageKey).toInt()
                val message = ChatMessageRegistry.create(registryID)
                message.id = fetchedMessage.messageUUID
                message.senderUUID = fetchedMessage.senderUUID.toUUID()
                message.text = fetchedMessage.text
                message.timeSent = fetchedMessage.timeSent
                message.aesKey = fetchedMessage.aesKey
                message.initVector = fetchedMessage.initVector
                message.signature = fetchedMessage.signature
                message.referenceUUID = fetchedMessage.referenceUUID.toUUID()
                val isSmallData = message.isSmallData()
                if (isSmallData) {
                    message.processAdditionalInfo(BaseEncoding.base64().decode(fetchedMessage.smallAdditionalInfo!!))
                }

                val chatUUID = fetchedMessage.chatUUID.decryptRSA(privateMessageKey).toUUID()
                val senderUUID = fetchedMessage.senderUUID.toUUID()
                val senderUsername = fetchedMessage.senderUsername

                val cI = getChatInfo(chatUUID, dataBase)
                val chatInfo = if (cI != null) cI else {
                    addContact(senderUUID, senderUsername, dataBase)
                    sendKeyRequest(senderUUID, dataBase)
                    val newChat = ChatInfo(chatUUID, senderUsername, ChatType.TWO_PEOPLE, listOf(ChatReceiver(userUUID!!, publicMessageKey!!,
                            ChatReceiver.ReceiverType.USER), ChatReceiver(senderUUID, null, ChatReceiver.ReceiverType.USER)))
                    createChat(newChat, dataBase, kentaiClient.userUUID)
                    newChat
                }

                fun tryFetchKey(userUUID: UUID): RSAPublicKey? {
                    return try {
                        requestPublicKey(listOf(userUUID), dataBase)[userUUID]!!
                    } catch (t: Throwable) {
                        null
                    }
                }

                val senderPublic = dataBase.rawQuery("SELECT message_key FROM contacts WHERE user_uuid = ?", arrayOf(senderUUID.toString())).use { query ->
                    if (query.moveToNext()) {
                        if (query.isNull(0)) {
                            tryFetchKey(senderUUID)
                        } else {
                            val messageKeyString = query.getString(0)
                            if (messageKeyString.isNotEmpty()) {
                                messageKeyString.toKey() as RSAPublicKey
                            } else {
                                tryFetchKey(senderUUID)
                            }
                        }
                    } else {
                        tryFetchKey(senderUUID)
                    }
                } ?: break@a

                message.decrypt(senderPublic, privateMessageKey)

                val messageUUID = message.id.toUUID()

                if (hasMessage(dataBase, messageUUID)) {
                    finished += fetchedMessage.messageUUID
                    continue@a
                }

                if (message !is ChatMessageGroupModification || chatInfo.chatType.isGroup()) {
                    saveMessage(chatUUID, ChatMessageWrapper(message, MessageStatus.RECEIVED, false, 0L, chatUUID), dataBase)
                }

                val isActive = dataBase.rawQuery("SELECT is_active FROM chat_participants WHERE chat_uuid = ? AND participant_uuid = ?", arrayOf(chatUUID.toString(), senderUUID.toString())).use { query ->
                    if (query.moveToNext()) {
                        query.getInt(0) == 1
                    } else false
                }

                if (!isActive) continue@a

                if (message.shouldBeStored()) {
                    val wrapper = ChatMessageWrapper(ChatMessageStatusChange(chatUUID, messageUUID, MessageStatus.RECEIVED, System.currentTimeMillis(), userUUID!!, System.currentTimeMillis()),
                            MessageStatus.RECEIVED, true, System.currentTimeMillis(), chatUUID)
                    sendMessageToServer(this, PendingMessage(wrapper, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != userUUID }), kentaiClient.dataBase)
                }

                val messageType = MessageType.values()[ChatMessageRegistry.getID(message.javaClass)]
                when (messageType) {
                    MessageType.TEXT_MESSAGE -> updateChatAndSendBroadcast(message, chatInfo, senderUUID, registryID, AdditionalInfoEmpty())
                    MessageType.MESSAGE_STATUS_CHANGE -> {
                        message as ChatMessageStatusChange
                        val changeStatusIntent = Intent(ACTION_MESSAGE_STATUS_CHANGE)
                        changeStatusIntent.putExtra(KEY_AMOUNT, 1)

                        saveMessageStatusChange(dataBase, MessageStatusChange(message.messageUUID.toUUID(), MessageStatus.values()[message.status.toInt()], System.currentTimeMillis()))

                        changeStatusIntent.putExtra("$KEY_MESSAGE_UUID${0}", message.messageUUID.toUUID())
                        changeStatusIntent.putExtra("$KEY_CHAT_UUID${0}", chatUUID)
                        changeStatusIntent.putExtra("$KEY_MESSAGE_STATUS${0}", message.status.toInt())
                        changeStatusIntent.putExtra("$KEY_TIME${0}", System.currentTimeMillis())

                        sendBroadcast(changeStatusIntent)

                        updateChatAndSendBroadcast(message, chatInfo, senderUUID, registryID, AdditionalInfoEmpty())
                    }
                    MessageType.GROUP_INVITE -> {
                        message as ChatMessageGroupInvite

                        val groupInvite = message.groupInvite
                        if (groupInvite is ChatMessageGroupInvite.GroupInviteDecentralizedChat) {

                            var exists = false
                            val newInfo = ChatInfo(groupInvite.chatUUID.toUUID(), groupInvite.groupName, ChatType.GROUP_DECENTRALIZED, groupInvite.roleMap.keys.map { ChatReceiver(it, null, ChatReceiver.ReceiverType.USER) })

                            putGroupRoles(groupInvite.roleMap, groupInvite.chatUUID.toUUID(), dataBase)

                            dataBase.rawQuery("SELECT chat_uuid FROM chats WHERE chat_uuid = ?", arrayOf(groupInvite.chatUUID)).use { query ->
                                exists = query.moveToNext()
                            }
                            if (!exists) {
                                createGroupChat(newInfo, groupInvite.roleMap, SecretKeySpec(BaseEncoding.base64().decode(groupInvite.groupKey), "AES"), dataBase, userUUID!!)

                                val messageIntent = Intent("de.intektor.kentai.groupInvite")

                                messageIntent.putExtra("groupName", groupInvite.groupName)
                                messageIntent.putExtra("senderUUID", senderUUID)
                                messageIntent.putExtra("chatInfo", newInfo)
                                messageIntent.putExtra("sentToChatUUID", chatUUID)
                                messageIntent.writeMessageWrapper(ChatMessageWrapper(message, MessageStatus.RECEIVED, false, System.currentTimeMillis(), chatUUID), 0)

                                sendOrderedBroadcast(messageIntent, null)

                                val missing = groupInvite.roleMap.keys.filterNot { hasContact(dataBase, it) }
                                if (missing.isNotEmpty()) {
                                    try {
                                        val gson = genGson()
                                        val r = httpPost(gson.toJson(UsersRequest(missing)), UsersRequest.TARGET)
                                        val response = gson.fromJson(r, UsersResponse::class.java)
                                        for (user in response.users) {
                                            addContact(user.userUUID, user.username, dataBase, user.messageKey)
                                        }
                                    } catch (t: Throwable) {
                                        break@a
                                    }
                                }
                            } else {
                                addChatParticipant(groupInvite.chatUUID.toUUID(), userUUID!!, dataBase)
                            }
                            updateChatAndSendBroadcast(message, chatInfo, senderUUID, registryID, AdditionalInfoGroupInviteMessage(senderUsername, groupInvite.groupName))
                        }
                    }
                    MessageType.GROUP_MODIFICATION -> {
                        message as ChatMessageGroupModification
                        val groupModification = message.groupModification

                        if (groupModification.chatUUID.toUUID() == chatUUID) {
                            val senderRole = getGroupRole(dataBase, chatUUID, senderUUID)
                            if (chatInfo.chatType == ChatType.GROUP_DECENTRALIZED && senderRole == GroupRole.ADMIN) {
                                handleGroupModification(groupModification, senderUUID, dataBase, kentaiClient.userUUID)

                                updateChatAndSendBroadcast(message, chatInfo, senderUUID, registryID, AdditionalInfoGroupModification(groupModification))

                                sendGroupModificationBroadcast(this, groupModification)

                                removePendingGroupModification(groupModification.modificationUUID.toUUID(), dataBase)
                            }
                        } else {
                            if (minimumGroupRole(groupModification, dataBase, kentaiClient.userUUID).isLessOrEqual(getGroupRole(dataBase, groupModification.chatUUID.toUUID(), senderUUID))) {
                                //This is a group modification sent by a moderator to the admin, we do not show this to the user because this is not interesting for him
                                receivedGroupModificationsAsAdmin += Triple(groupModification, message.timeSent, senderUUID)
                            }
                        }
                    }
                    MessageType.VOICE_MESSAGE -> {
                        message as ChatMessageVoiceMessage
                        downloadAudio(this, dataBase, chatInfo.chatUUID, message.referenceUUID, chatInfo.chatType, message.fileHash, privateMessageKey)
                        updateChatAndSendBroadcast(message, chatInfo, senderUUID, registryID, AdditionalInfoVoiceMessage(message.durationSeconds.toInt()))
                    }
                    MessageType.IMAGE_MESSAGE -> {
                        message as ChatMessageImage
                        downloadImage(this, dataBase, chatInfo.chatUUID, message.referenceUUID, chatInfo.chatType, message.hash, privateMessageKey)
                        updateChatAndSendBroadcast(message, chatInfo, senderUUID, registryID, AdditionalInfoEmpty())
                    }
                    MessageType.VIDEO_MESSAGE -> {
                        message as ChatMessageVideo
                        downloadVideo(this, dataBase, chatInfo.chatUUID, message.referenceUUID, chatInfo.chatType, message.hash, privateMessageKey)
                        updateChatAndSendBroadcast(message, chatInfo, senderUUID, registryID, AdditionalInfoVideoMessage(message.durationSeconds.toInt()))
                    }
                    MessageType.TYPING_MESSAGE -> {

                    }
                }
                finished += fetchedMessage.messageUUID
            }
        }

        val sortedReceivedGroupModificationsAsAdmin = receivedGroupModificationsAsAdmin.sortedBy { it.second }
        for ((modification, _, senderUUID) in sortedReceivedGroupModificationsAsAdmin) {
            handleGroupModification(modification, senderUUID, dataBase, kentaiClient.userUUID)
        }

        val chatParticipantMap = mutableMapOf<UUID, List<ChatReceiver>>()

        sortedReceivedGroupModificationsAsAdmin.forEach { (groupModification, _, _) ->
            val chatUUID = groupModification.chatUUID.toUUID()
            val pendingMessage = PendingMessage(ChatMessageWrapper(ChatMessageGroupModification(groupModification, kentaiClient.userUUID, System.currentTimeMillis()), MessageStatus.WAITING, true,
                    System.currentTimeMillis(), chatUUID), chatUUID,
                    chatParticipantMap.getOrPut(chatUUID) { readChatParticipants(dataBase, chatUUID).filter { it.receiverUUID != kentaiClient.userUUID } })

            sendMessageToServer(this, pendingMessage, dataBase)

            sendGroupModificationBroadcast(this, groupModification)

            if (groupModification is GroupModificationAddUser) {
                val chatInfo = getChatInfo(chatUUID, dataBase)
                val groupKey = getGroupKey(chatUUID, dataBase)

                if (chatInfo != null && groupKey != null) {
                    val roleMap = hashMapOf<UUID, GroupRole>()

                    getGroupMembers(dataBase, chatUUID).forEach {
                        roleMap[it.contact.userUUID] = it.role
                    }

                    roleMap += Pair(groupModification.userUUID.toUUID(), GroupRole.DEFAULT)

                    val groupInvite = if (chatInfo.chatType == ChatType.GROUP_DECENTRALIZED)
                        ChatMessageGroupInvite.GroupInviteDecentralizedChat(roleMap, chatUUID, chatInfo.chatName, groupKey)
                    else ChatMessageGroupInvite.GroupInviteCentralizedChat(chatUUID, chatInfo.chatName, groupKey)

                    inviteUserToGroupChat(groupModification.userUUID.toUUID(), chatInfo, groupInvite, kentaiClient.userUUID, this, dataBase)
                }
            }
        }

        return finished
    }

    private fun updateChatAndSendBroadcast(message: ChatMessage, chatInfo: ChatInfo, senderUUID: UUID, registryID: Int, additionalInformation: IAdditionalInfo) {
        val kentaiClient = applicationContext as KentaiClient
        val dataBase = kentaiClient.dataBase
        dataBase.rawQuery("SELECT unread_messages, chat_name, type FROM chats WHERE chat_uuid = ? LIMIT 1", arrayOf(chatInfo.chatUUID.toString())).use { cursor ->
            if (cursor.moveToNext()) {
                val unreadMessages = cursor.getInt(0)
                val chatName = cursor.getString(1)
                val chatType = cursor.getInt(2)
                cursor.close()

                if (message !is ChatMessageStatusChange) {
                    incrementUnreadMessages(dataBase, chatInfo.chatUUID)
                }

                val byteOut = ByteArrayOutputStream()
                val dataOut = DataOutputStream(byteOut)
                additionalInformation.writeToStream(dataOut)

                val newUnreadMessages = if (message !is ChatMessageStatusChange) unreadMessages + 1 else unreadMessages

                val intent = Intent(ACTION_CHAT_NOTIFICATION)
                intent.putExtra(KEY_UNREAD_MESSAGES, newUnreadMessages)
                intent.putExtra(KEY_CHAT_TYPE, chatType)
                intent.putExtra(KEY_CHAT_UUID, chatInfo.chatUUID.toString())
                intent.putExtra(KEY_CHAT_NAME, chatName)
                intent.putExtra(KEY_USER_UUID, senderUUID.toString())
                intent.putExtra(KEY_ADDITIONAL_INFO_REGISTRY_ID, AdditionalInfoRegistry.getID(additionalInformation.javaClass))
                intent.putExtra(KEY_ADDITIONAL_INFO_CONTENT, byteOut.toByteArray())
                intent.putExtra(KEY_MESSAGE_REGISTRY_ID, registryID)
                intent.writeMessageWrapper(ChatMessageWrapper(message, MessageStatus.RECEIVED, false, System.currentTimeMillis(), chatInfo.chatUUID), 0)
                sendOrderedBroadcast(intent, null)
            } else {
                Log.w("WARNING", "NO CHAT FOUND, THIS SHOULD NEVER HAPPEN")
            }
        }
    }

    private fun sendKeyRequest(requestedUUID: UUID, dataBase: SQLiteDatabase) {
        object : Thread() {
            override fun run() {
                requestPublicKey(listOf(requestedUUID), dataBase)
            }
        }.start()
    }

    override fun onDeletedMessages() {

    }
}