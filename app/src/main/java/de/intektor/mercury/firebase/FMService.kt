package de.intektor.mercury.firebase

import android.content.Context
import com.google.common.io.BaseEncoding
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.intektor.mercury.action.ActionMessageStatusChange
import de.intektor.mercury.action.chat.ActionChatMessageNotification
import de.intektor.mercury.action.chat.ActionChatMessageReceived
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.*
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.chat.model.ChatReceiver
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.io.HttpManager
import de.intektor.mercury.io.download.IOService
import de.intektor.mercury.media.MediaType
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.task.*
import de.intektor.mercury.util.Logger
import de.intektor.mercury_common.chat.*
import de.intektor.mercury_common.chat.data.*
import de.intektor.mercury_common.chat.data.group_modification.GroupModification
import de.intektor.mercury_common.chat.data.group_modification.GroupModificationAddUser
import de.intektor.mercury_common.chat.data.group_modification.MessageGroupModification
import de.intektor.mercury_common.client_to_server.FetchMessageRequest
import de.intektor.mercury_common.client_to_server.HandledMessagesRequest
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.server_to_client.FetchMessageResponse
import de.intektor.mercury_common.util.*
import java.security.interfaces.RSAPublicKey
import java.util.*
import javax.crypto.BadPaddingException

/**
 * @author Intektor
 */
class FMService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FMService"

        private const val SHARED_PREFERENCES_TOKEN_REFRESH = "de.intektor.mercury.shared_preferences.TOKEN_REFRESH"
        private const val KEY_TOKEN_INVALID = "de.intektor.mercury.KEY_TOKEN_INVALID"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val type = FCMMessageType.values()[remoteMessage.data["type"]!!.toInt()]
        when (type) {
            FCMMessageType.CHAT_MESSAGE -> try {
                fetchAndProcessMessageData()
            } catch (t: Throwable) {
                Logger.error(TAG, "Error while processing messages", t)
            }
            FCMMessageType.ONLINE_MESSAGE -> {

            }
            FCMMessageType.SERVER_NOTIFICATION -> {

            }
        }
    }

    private fun fetchAndProcessMessageData() {
        val userUUID = ClientPreferences.getClientUUID(this)
        val privateAuthKey = ClientPreferences.getPrivateAuthKey(this)

        val gson = genGson()
        val res = HttpManager.post(gson.toJson(FetchMessageRequest(userUUID, signUserUUID(userUUID, privateAuthKey))), FetchMessageRequest.TARGET)

        val response = gson.fromJson(res, FetchMessageResponse::class.java)
        val handled = processChatMessage(response)
        if (handled.isNotEmpty()) {
            HttpManager.post(gson.toJson(HandledMessagesRequest(handled, signUserUUID(userUUID, privateAuthKey), userUUID)), HandledMessagesRequest.TARGET)
        }
    }

    private fun processChatMessage(fetched: FetchMessageResponse): List<UUID> {
        val mercuryClient = mercuryClient()
        val dataBase = mercuryClient.dataBase

        val privateMessageKey = ClientPreferences.getPrivateMessageKey(this)

        val finishedMessages = mutableListOf<UUID>()

        val temp = fetched.list
                .asSequence()
                .groupBy { message ->
                    try {
                        message.chatUUID.decryptRSA(privateMessageKey).toUUID()
                    } catch (e: BadPaddingException) {
                        null
                    }
                }
        finishedMessages += temp.filter { it.key == null }.map { it.value.map { it.messageUUID } }.flatMap { it }

        val chats = temp.filter { it.key != null }.map {
            MessagesForChat(it.key ?: throw IllegalStateException(), it.value)
        }

        val receivedGroupModificationsAsAdmin = mutableListOf<ReceivedGroupModificationToAdmin>()

        fun tryFetchKey(userUUID: UUID): RSAPublicKey? {
            return try {
                requestUsers(listOf(userUUID), dataBase)
                getContact(dataBase, userUUID).message_key as RSAPublicKey?
                        ?: throw IllegalStateException("Message key must be not null here.")
            } catch (t: Throwable) {
                null
            }
        }

        val getSenderPublic = { senderUUID: UUID ->
            dataBase.rawQuery("SELECT message_key FROM contacts WHERE user_uuid = ?", arrayOf(senderUUID.toString())).use { query ->
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
            }
        }


        val clientUUID = ClientPreferences.getClientUUID(this)

        for (chat in chats) {
            val chatUUID = chat.chatUUID

            val messagesToProcess = chat.receivedMessages
                    .asSequence()
                    .filter { message -> getSenderPublic(message.senderUUID) != null }
                    .filter { message ->
                        val verified = verify(message.message, message.signature, getSenderPublic(message.senderUUID)
                                ?: throw IllegalStateException("We just checked that we got a public key but now we don't: messageUUID=${message.senderUUID}"))

                        if (!verified) Logger.warning(TAG, "Received a message with a wrong signature, skipping. Received from userUUID=${message.senderUUID}")

                        verified
                    }
                    .map { message ->
                        val aesKey = BaseEncoding.base64().decode(message.aesKey.decryptRSA(privateMessageKey)).toAESKey()
                        val initVector = BaseEncoding.base64().decode(message.initVector.decryptRSA(privateMessageKey))

                        val chatMessage = ChatSerializer.deserializeChatMessage(message.message, aesKey, initVector)

                        MessageToProcess(chatUUID, message.senderUUID, chatMessage)
                    }
                    .toList()

            a@ for (message in messagesToProcess) {
                val chatInfo = getChatInfo(chatUUID, dataBase) ?: aswell {
                    val senderContact = getContact(dataBase, message.senderUUID)

                    val newChat = ChatInfo(chatUUID,
                            ChatType.TWO_PEOPLE,
                            listOf(ChatReceiver.fromContact(senderContact),
                                    ChatReceiver(clientUUID, null, ChatReceiver.ReceiverType.USER))
                    )
                    createChat(newChat, dataBase, clientUUID)
                    newChat
                }


                val chatMessage = message.message
                val messageUUID = chatMessage.messageCore.messageUUID

                if (hasMessage(dataBase, messageUUID)) {
                    finishedMessages += messageUUID
                    continue@a
                }

                if ((chatMessage.messageData !is MessageGroupModification || chatInfo.chatType.isGroup()) && chatMessage.messageData !is MessageStatusUpdate) {
                    saveMessage(this, dataBase, chatMessage, chatUUID)
                    updateMessageStatus(dataBase, messageUUID, MessageStatus.RECEIVED, System.currentTimeMillis())
                }

                if (!ChatParticipantUtil.isUserActive(chatInfo.chatUUID, message.senderUUID, dataBase)) continue@a

                if (chatMessage.messageData !is MessageStatusUpdate) {
                    val receivedData = MessageStatusUpdate(messageUUID, MessageStatus.RECEIVED, System.currentTimeMillis())
                    val receivedCore = MessageCore(clientUUID, System.currentTimeMillis(), UUID.randomUUID())

                    sendMessageToServer(this, PendingMessage(ChatMessage(receivedCore, receivedData), chatInfo.chatUUID, chatInfo.getOthers(clientUUID)), mercuryClient.dataBase)
                }

                val messageData = chatMessage.messageData

                when (messageData) {
                    is MessageText -> updateChatAndSendBroadcast(chatMessage, chatInfo)
                    is MessageStatusUpdate -> {
                        updateMessageStatus(dataBase, messageData.messageUUID, messageData.status, System.currentTimeMillis())
                        ActionMessageStatusChange.launch(this, chatUUID, messageData.messageUUID, messageData.status)
                    }
                    is MessageGroupInvite -> {
                        val groupInvite = messageData.groupInvite

                        if (groupInvite is MessageGroupInvite.GroupInviteDecentralizedChat) {

//                            val newInfo = ChatInfo(groupInvite.chatUUID, groupInvite.groupName,
//                                    ChatType.GROUP_DECENTRALIZED, groupInvite.roleMap.keys.map { ChatReceiver(it, null, ChatReceiver.ReceiverType.USER) })
//
//                            putGroupRoles(groupInvite.roleMap, groupInvite.chatUUID, dataBase)
//
//                            if (!hasChat(chatUUID, dataBase)) {
//                                createGroupChat(newInfo, groupInvite.roleMap, groupInvite.groupKey, dataBase, clientUUID)
//
//                                ActionGroupInvite.launch(this, groupInvite.groupName, message.senderUUID, newInfo, chatUUID)
//
//                                val missing = groupInvite.roleMap.keys.filterNot { hasContact(dataBase, it) }
//                                if (missing.isNotEmpty()) {
//                                    thread {
//                                        requestUsers(missing, dataBase)
//                                    }
//                                }
//                            } else {
//                                addChatParticipant(groupInvite.chatUUID, clientUUID, dataBase)
//                            }
//                            updateChatAndSendBroadcast(chatMessage, chatInfo)
                        }
                    }
                    is MessageGroupModification -> {
                        val groupModification = messageData.groupModification

                        if (groupModification.chatUUID == chatUUID) {
                            val senderRole = getGroupRole(dataBase, chatUUID, message.senderUUID)
                            if (chatInfo.chatType == ChatType.GROUP_DECENTRALIZED && senderRole == GroupRole.ADMIN) {
                                handleGroupModification(groupModification, message.senderUUID, dataBase, clientUUID)

                                updateChatAndSendBroadcast(chatMessage, chatInfo)

                                sendGroupModificationBroadcast(this, groupModification)

                                removePendingGroupModification(groupModification.modificationUUID, dataBase)
                            }
                        } else {
                            if (minimumGroupRole(groupModification, dataBase, clientUUID).isLessOrEqual(getGroupRole(dataBase, groupModification.chatUUID, message.senderUUID))) {
                                //This is a group modification sent by a moderator to the admin, we do not show this to the user because this is not interesting for him
                                receivedGroupModificationsAsAdmin += ReceivedGroupModificationToAdmin(groupModification, chatMessage.messageCore.timeCreated, message.senderUUID)
                            }
                        }
                    }
                    is MessageVoiceMessage -> {
                        IOService.ActionDownloadReference.launch(this, messageData.reference, messageData.aesKey, messageData.initVector, MediaType.MEDIA_TYPE_AUDIO)
                        updateChatAndSendBroadcast(chatMessage, chatInfo)
                    }
                    is MessageImage -> {
                        IOService.ActionDownloadReference.launch(this, messageData.reference, messageData.aesKey, messageData.initVector, MediaType.MEDIA_TYPE_IMAGE)
                        updateChatAndSendBroadcast(chatMessage, chatInfo)

                        ReferenceUtil.addReference(mercuryClient.dataBase, chatUUID, messageData.reference, messageUUID, MediaType.MEDIA_TYPE_IMAGE, System.currentTimeMillis())
                    }
                    is MessageVideo -> {
                        IOService.ActionDownloadReference.launch(this, messageData.reference, messageData.aesKey, messageData.initVector, MediaType.MEDIA_TYPE_VIDEO)
                        updateChatAndSendBroadcast(chatMessage, chatInfo)

                        ReferenceUtil.addReference(mercuryClient.dataBase, chatUUID, messageData.reference, messageUUID, MediaType.MEDIA_TYPE_VIDEO, System.currentTimeMillis())
                    }
                }
                finishedMessages += chatMessage.messageCore.messageUUID
            }
        }

        val sortedReceivedGroupModificationsAsAdmin = receivedGroupModificationsAsAdmin.sortedBy { it.timeCreated }
        for ((modification, _, senderUUID) in sortedReceivedGroupModificationsAsAdmin) {
            handleGroupModification(modification, senderUUID, dataBase, clientUUID)
        }

        val chatParticipantMap = mutableMapOf<UUID, List<ChatReceiver>>()

        sortedReceivedGroupModificationsAsAdmin.forEach { (groupModification, _, _) ->
            val chatUUID = groupModification.chatUUID

            val messageCore = MessageCore(clientUUID, System.currentTimeMillis(), UUID.randomUUID())
            val messageData = MessageGroupModification(groupModification)

            val pendingMessage = PendingMessage(ChatMessage(messageCore, messageData), chatUUID,
                    chatParticipantMap.getOrPut(chatUUID) { readChatParticipants(dataBase, chatUUID).filter { it.receiverUUID != clientUUID } })

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

                    roleMap += Pair(groupModification.addedUser, GroupRole.DEFAULT)

//                    val groupInvite = if (chatInfo.chatType == ChatType.GROUP_DECENTRALIZED)
//                        MessageGroupInvite.GroupInviteDecentralizedChat(roleMap, chatUUID, chatInfo.chatName, groupKey)
//                    else MessageGroupInvite.GroupInviteCentralizedChat(chatUUID, chatInfo.chatName, groupKey)
//
//                    inviteUserToGroupChat(this, dataBase, clientUUID, groupModification.addedUser, groupInvite)
                }
            }
        }

        return finishedMessages
    }

    private fun updateChatAndSendBroadcast(message: ChatMessage, chatInfo: ChatInfo) {
        ActionChatMessageReceived.launch(this, message, chatInfo.chatUUID)
        ActionChatMessageNotification.launch(this, chatInfo.chatUUID, message.messageCore.messageUUID)
    }

    override fun onDeletedMessages() {

    }

    override fun onNewToken(token: String) {
        if (hasClient(this)) {
            markTokenInvalid(true)

            if (UploadTokenTask.uploadToken(this, token)) {
                markTokenInvalid(false)
            }
        }
    }

    private fun tokenPreference() = getSharedPreferences(SHARED_PREFERENCES_TOKEN_REFRESH, Context.MODE_PRIVATE)

    private fun markTokenInvalid(invalid: Boolean) {
        tokenPreference().edit().putBoolean(KEY_TOKEN_INVALID, invalid).apply()
    }

    private data class MessagesForChat(val chatUUID: UUID, val receivedMessages: List<FetchMessageResponse.Message>)

    private data class MessageToProcess(val chatUUID: UUID, val senderUUID: UUID, val message: ChatMessage)

    private data class ReceivedGroupModificationToAdmin(val groupModification: GroupModification, val timeCreated: Long, val senderUUID: UUID)
}