package de.intektor.kentai.kentai.firebase

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.google.common.io.BaseEncoding
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.intektor.kentai.kentai.DbHelper
import de.intektor.kentai.kentai.android.writeMessageWrapper
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.contacts.addContact
import de.intektor.kentai.kentai.firebase.additional_information.AdditionalInfoRegistry
import de.intektor.kentai.kentai.firebase.additional_information.IAdditionalInfo
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoEmpty
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoGroupInviteMessage
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoGroupModification
import de.intektor.kentai.kentai.firebase.additional_information.info.AdditionalInfoVoiceMessage
import de.intektor.kentai.kentai.groups.handleGroupModification
import de.intektor.kentai.kentai.httpPost
import de.intektor.kentai_http_common.chat.*
import de.intektor.kentai_http_common.chat.group_modification.ChatMessageGroupModification
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationRegistry
import de.intektor.kentai_http_common.client_to_server.FetchMessageRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.FetchMessageResponse
import de.intektor.kentai_http_common.util.*
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.Key
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*

/**
 * @author Intektor
 */
class FMService : FirebaseMessagingService() {

    private lateinit var dataBase: SQLiteDatabase
    private var privateAuthKey: Key? = null
    private var privateMessageKey: RSAPrivateKey? = null
    private var publicMessageKey: Key? = null
    private var userUUID: UUID? = null
    private var username: String? = null

    override fun onCreate() {
        super.onCreate()
        val dbHelper = DbHelper(this)
        dataBase = dbHelper.writableDatabase

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
            userUUID = UUID.fromString(dataIn.readUTF())
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        val type = FCMMessageType.values()[remoteMessage.data["type"]!!.toInt()]
        if (type == FCMMessageType.CHAT_MESSAGE) {
            val chatUUID = UUID.fromString(remoteMessage.data["chat_uuid"])
            val messageUUID = UUID.fromString(remoteMessage.data["message_uuid"])
            val senderUUID = UUID.fromString(remoteMessage.data["sender_uuid"])
            val senderUsername = remoteMessage.data["sender_username"]
            val messageRegistryId = remoteMessage.data["message_registry_id"]
            val previewText = remoteMessage.data["message_registry_id"]

            val cursor = dataBase.rawQuery("SELECT username, message_key FROM contacts WHERE user_uuid = ?", arrayOf(senderUUID.toString()))

            val chatInfo = ChatInfo(chatUUID, senderUsername!!, ChatType.TWO_PEOPLE, listOf(ChatReceiver(userUUID!!, publicMessageKey!!, ChatReceiver.ReceiverType.USER), ChatReceiver(senderUUID, null, ChatReceiver.ReceiverType.USER)))

            if (!cursor.moveToNext()) {
                addContact(senderUUID, senderUsername, dataBase)

                sendKeyRequest(senderUUID)

                createChat(chatInfo, dataBase, userUUID!!)
            }

            cursor.close()

            dataBase.compileStatement("INSERT INTO fetching_messages (chat_uuid, message_uuid) VALUES (?, ?)").use { statement ->
                statement.bindString(1, chatUUID.toString())
                statement.bindString(2, messageUUID.toString())
                statement.execute()
            }

            try {
                fetchAndProcessMessageData(chatUUID, messageUUID, senderUUID, senderUsername, messageRegistryId!!, chatInfo)
                dataBase.compileStatement("DELETE FROM fetching_messages WHERE message_uuid = ?").use { statement ->
                    statement.bindString(1, messageUUID.toString())
                    statement.execute()
                }
            } catch (t: Throwable) {
                Log.e("ERROR", "ERROR", t)
                TODO("Display notification")
            }
        } else if (type == FCMMessageType.MESSAGE_STATUS_UPDATE) {
//            updateMessageStatus(dataBase, )
//            val intent = Intent(ChangeMessageStatusPacketToClientHandler::class.java.simpleName)
//            intent.putExtra("chatUUID", packet.chatUUID.toString())
//            intent.putExtra("status", packet.status.ordinal)
//            intent.putExtra("uuid", packet.uuid.toString())
//            LocalBroadcastManager.getInstance(ConnectionService.INSTANCE).sendBroadcast(intent)

        }
    }

    private fun fetchAndProcessMessageData(chatUUID: UUID, messageUUID: UUID, senderUUID: UUID, senderUsername: String, messageRegistryId: String, chatInfo: ChatInfo) {
        val encryptedMessageUUID = messageUUID.toString().encryptRSA(privateAuthKey!!)

        val gson = genGson()
        val res = httpPost(gson.toJson(FetchMessageRequest(chatUUID, encryptedMessageUUID, userUUID!!)), FetchMessageRequest.TARGET)

        val response = gson.fromJson(res, FetchMessageResponse::class.java)
        processChatMessage(chatUUID, messageUUID, senderUUID, senderUsername, response, messageRegistryId, chatInfo)
    }

    private fun processChatMessage(chatUUID: UUID, messageUUID: UUID, senderUUID: UUID, senderUsername: String, fetched: FetchMessageResponse, messageRegistryId: String, chatInfo: ChatInfo) {
        val db = dataBase

        val registryID = messageRegistryId.decryptRSA(privateMessageKey!!).toInt()
        val message = ChatMessageRegistry.create(registryID)
        message.id = messageUUID
        message.senderUUID = senderUUID
        message.text = fetched.text
        message.timeSent = fetched.timeSent
        message.aesKey = fetched.aesKey
        message.initVector = fetched.initVector
        message.signature = fetched.signature
        message.referenceUUID = fetched.referenceUUID.toUUID()
        if (message.isSmallData()) {
            message.processAdditionalInfo(BaseEncoding.base64().decode(fetched.smallAdditionalInfo!!))
        }

        fun tryFetchKey(): RSAPublicKey {
            try {
                return requestPublicKey(listOf(senderUUID), db)[senderUUID]!!
            } catch (t: Throwable) {
                throw RuntimeException("No key could be caught, it is impossible to decrypt the message! Try again later", t)
            }
        }

        db.rawQuery("SELECT message_key FROM contacts WHERE user_uuid = ?", arrayOf(senderUUID.toString())).use { query ->
            val senderPublic: RSAPublicKey
            senderPublic = if (query.moveToNext()) {
                if (query.isNull(0)) {
                    tryFetchKey()
                } else {
                    val messageKeyString = query.getString(0)
                    if (messageKeyString.isNotEmpty()) {
                        messageKeyString.toKey() as RSAPublicKey
                    } else {
                        tryFetchKey()
                    }
                }
            } else {
                tryFetchKey()
            }

            message.decrypt(senderPublic, privateMessageKey!!)
        }

        db.rawQuery("SELECT is_active FROM chat_participants WHERE chat_uuid = ? AND participant_uuid = ?", arrayOf(chatUUID.toString(), senderUUID.toString())).use { query ->
            if (query.moveToNext()) {
                if (query.getInt(0) != 1) return
            }
        }

        if (message.shouldBeStored()) {
            val wrapper = ChatMessageWrapper(ChatMessageStatusChange(chatUUID, messageUUID, MessageStatus.RECEIVED, System.currentTimeMillis(), userUUID!!, System.currentTimeMillis()),
                    MessageStatus.RECEIVED, true, System.currentTimeMillis())
            sendMessageToServer(this, PendingMessage(wrapper, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != userUUID }))
        }

        saveMessage(chatUUID, ChatMessageWrapper(message, MessageStatus.RECEIVED, false, 0L), db)

        val messageType = MessageType.values()[ChatMessageRegistry.getID(message.javaClass)]
        when (messageType) {
            MessageType.TEXT_MESSAGE -> updateChatAndSendBroadcast(message, chatInfo, senderUsername, senderUUID, registryID, AdditionalInfoEmpty())
            MessageType.MESSAGE_STATUS_CHANGE -> {
                message as ChatMessageStatusChange
                val changeStatusIntent = Intent("de.intektor.kentai.messageStatusUpdate")
                changeStatusIntent.putExtra("amount", 1)

                dataBase.compileStatement("INSERT INTO message_status_change (message_uuid, status, time) VALUES(?, ?, ?)").use { statement ->
                    statement.bindString(1, message.messageUUID)
                    statement.bindLong(2, message.status.toInt().toLong())
                    statement.bindLong(3, System.currentTimeMillis())
                    statement.execute()
                }

                changeStatusIntent.putExtra("messageUUID0", message.messageUUID)
                changeStatusIntent.putExtra("chatUUID0", chatUUID.toString())
                changeStatusIntent.putExtra("status0", message.status.toInt())
                changeStatusIntent.putExtra("time0", System.currentTimeMillis())

                sendOrderedBroadcast(changeStatusIntent, null)

                updateChatAndSendBroadcast(message, chatInfo, senderUsername, senderUUID, registryID, AdditionalInfoEmpty())
            }
            MessageType.GROUP_INVITE -> {
                message as ChatMessageGroupInvite
                var exists = false
                val newInfo = ChatInfo(message.chatUUID.toUUID(), message.groupName, ChatType.GROUP, message.roleMap.keys.map { ChatReceiver(it, null, ChatReceiver.ReceiverType.USER) }.plus(ChatReceiver(userUUID!!, null, ChatReceiver.ReceiverType.USER)))

                putGroupRoles(message.roleMap, message.chatUUID.toUUID(), dataBase)

                db.rawQuery("SELECT chat_uuid FROM chats WHERE chat_uuid = ?", arrayOf(message.chatUUID)).use { query ->
                    exists = query.moveToNext()
                }
                if (!exists) {
                    createGroupChat(newInfo, message.roleMap, message.groupKey.toAESKey(), dataBase, userUUID!!)

                    val messageIntent = Intent("de.intektor.kentai.groupInvite")

                    messageIntent.putExtra("groupName", message.groupName)
                    messageIntent.putExtra("senderUUID", senderUUID)
                    messageIntent.putExtra("chatInfo", newInfo)
                    messageIntent.putExtra("sentToChatUUID", chatUUID)
                    messageIntent.writeMessageWrapper(ChatMessageWrapper(message, MessageStatus.RECEIVED, false, System.currentTimeMillis()), 0)

                    sendOrderedBroadcast(messageIntent, null)
                } else {
                    addChatParticipant(message.chatUUID.toUUID(), userUUID!!, db)
                }
                updateChatAndSendBroadcast(message, chatInfo, senderUsername, senderUUID, registryID, AdditionalInfoGroupInviteMessage(senderUsername, message.groupName))
            }
            MessageType.GROUP_MODIFICATION -> {
                message as ChatMessageGroupModification
                val groupModification = message.groupModification
                handleGroupModification(groupModification, db)

                updateChatAndSendBroadcast(message, chatInfo, senderUsername, senderUUID, registryID, AdditionalInfoGroupModification(groupModification))

                val byteOut = ByteArrayOutputStream()
                val dataOut = DataOutputStream(byteOut)
                message.groupModification.write(dataOut)

                val messageIntent = Intent("de.intektor.kentai.groupModification")
                messageIntent.putExtra("chatUUID", message.groupModification.chatUUID)
                messageIntent.putExtra("groupModificationID", GroupModificationRegistry.getID(message.groupModification::class.java))
                messageIntent.putExtra("groupModification", byteOut.toByteArray())

                sendOrderedBroadcast(messageIntent, null)
            }
            MessageType.VOICE_MESSAGE -> {
                message as ChatMessageVoiceMessage
                updateChatAndSendBroadcast(message, chatInfo, senderUsername, senderUUID, registryID, AdditionalInfoVoiceMessage(message.durationSeconds.toInt()))
            }
        }
    }

    private fun updateChatAndSendBroadcast(message: ChatMessage, chatInfo: ChatInfo, senderUsername: String, senderUUID: UUID, registryID: Int, additionalInformation: IAdditionalInfo) {
        val cursor2 = dataBase.rawQuery("SELECT unread_messages, chat_name, type FROM chats WHERE chat_uuid = '${chatInfo.chatUUID}' LIMIT 1", null)

        if (cursor2.moveToNext()) {
            val unreadMessages = cursor2.getInt(0)
            val chatName = cursor2.getString(1)
            val chatType = cursor2.getInt(2)
            cursor2.close()

            dataBase.execSQL("UPDATE chats SET unread_messages = unread_messages + 1 WHERE chat_uuid = '${chatInfo.chatUUID}'")

            val byteOut = ByteArrayOutputStream()
            val dataOut = DataOutputStream(byteOut)
            additionalInformation.writeToStream(dataOut)

            val intent = Intent("de.intektor.kentai.chatNotification")
            intent.putExtra("unreadMessages", unreadMessages + 1)
            intent.putExtra("chatType", chatType)
            intent.putExtra("chatUUID", chatInfo.chatUUID.toString())
            intent.putExtra("chatName", chatName)
            intent.putExtra("senderName", senderUsername)
            intent.putExtra("senderUUID", senderUUID.toString())
            intent.putExtra("message.id", message.id.toString())
            intent.putExtra("message.senderUUID", message.senderUUID)
            intent.putExtra("message.text", message.text)
            intent.putExtra("message.additionalInfo", message.getAdditionalInfo())
            intent.putExtra("message.timeSent", message.timeSent)
            intent.putExtra("message.messageID", registryID)
            intent.putExtra("additionalInfoID", AdditionalInfoRegistry.getID(additionalInformation.javaClass))
            intent.putExtra("additionalInfoContent", byteOut.toByteArray())
            sendOrderedBroadcast(intent, null)
        } else {
            Log.w("WARNING", "NO CHAT FOUND, THIS SHOULD NEVER HAPPEN")
        }
    }

    private fun sendKeyRequest(requestedUUID: UUID) {
        object : Thread() {
            override fun run() {
                requestPublicKey(listOf(requestedUUID), dataBase)
            }
        }.start()
    }

    override fun onDeletedMessages() {

    }

    override fun onDestroy() {
        super.onDestroy()
        dataBase.close()
    }
}