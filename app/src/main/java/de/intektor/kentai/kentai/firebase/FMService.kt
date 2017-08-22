package de.intektor.kentai.kentai.firebase

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.google.common.io.BaseEncoding
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.intektor.kentai.kentai.DbHelper
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.createChat
import de.intektor.kentai.kentai.chat.saveMessage
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.client_to_server.FetchMessageRequest
import de.intektor.kentai_http_common.client_to_server.KeyRequest
import de.intektor.kentai_http_common.client_to_server.SendChatMessageRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.FetchMessageResponse
import de.intektor.kentai_http_common.server_to_client.KeyResponse
import de.intektor.kentai_http_common.util.FCMMessageType
import de.intektor.kentai_http_common.util.decryptRSA
import de.intektor.kentai_http_common.util.encryptRSA
import de.intektor.kentai_http_common.util.readPrivateKey
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
class FMService : FirebaseMessagingService() {

    private val dataBase: SQLiteDatabase
    private var pAuthKey: Key? = null
    private var pMessageKey: Key? = null
    private var userUUID: UUID? = null
    private var username: String? = null

    init {
        val dbHelper = DbHelper(applicationContext)
        dataBase = dbHelper.writableDatabase

        val pKeyFile = File(filesDir.path + "/" + "keys/authKeyPrivate.key")
        if (pKeyFile.exists()) {
            pAuthKey = readPrivateKey(DataInputStream(pKeyFile.inputStream()))
            pMessageKey = readPrivateKey(DataInputStream(File(filesDir.path + "/" + "keys/messageKeyPrivate.key").inputStream()))
        }

        val userInfoFile = File(filesDir.path + "/" + "username.info")
        if (userInfoFile.exists()) {
            val dataIn = DataInputStream(userInfoFile.inputStream())
            username = dataIn.readUTF()
            userUUID = UUID.fromString(dataIn.readUTF())
        }

    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (pAuthKey == null) {
            val file = File(filesDir.path + "/" + "keys/authKeyPrivate.key")
            if (file.exists()) {
                pAuthKey = readPrivateKey(DataInputStream(file.inputStream()))
                pMessageKey = readPrivateKey(DataInputStream(File(filesDir.path + "/" + "keys/messageKeyPrivate.key").inputStream()))
            }
        }
        if (userUUID == null) {
            val dataIn = DataInputStream(File(filesDir.path + "/" + "username.info").inputStream())
            username = dataIn.readUTF()
            userUUID = UUID.fromString(dataIn.readUTF())
        }

        val type = FCMMessageType.values()[remoteMessage.data["type"]!!.toInt()]
        if (type == FCMMessageType.CHAT_MESSAGE) {
            val chatUUID = UUID.fromString(remoteMessage.data["chat_uuid"])
            val messageUUID = UUID.fromString(remoteMessage.data["message_uuid"])
            val senderUUID = UUID.fromString(remoteMessage.data["sender_uuid"])
            val senderUsername = remoteMessage.data["sender_username"]
            val messageRegistryId = remoteMessage.data["message_registry_id"]
            fetchAndProcessMessageData(chatUUID, messageUUID, senderUUID, senderUsername!!, messageRegistryId!!)
        }
    }

    private fun fetchAndProcessMessageData(chatUUID: UUID, messageUUID: UUID, senderUUID: UUID, senderUsername: String, messageRegistryId: String) {
        val connection: URLConnection = URL("localhost/" + FetchMessageRequest.TARGET).openConnection()
        connection.readTimeout = 15000
        connection.connectTimeout = 15000
        connection.doInput = true
        connection.doOutput = true

        val encryptedMessageUUID = messageUUID.toString().encryptRSA(pAuthKey!!)

        val gson = genGson()
        gson.toJson(FetchMessageRequest(chatUUID, encryptedMessageUUID, userUUID!!), BufferedWriter(OutputStreamWriter(connection.getOutputStream())))

        val response = gson.fromJson(InputStreamReader(connection.getInputStream()), FetchMessageResponse::class.java)
        processChatMessage(chatUUID, messageUUID, senderUUID, senderUsername, response, messageRegistryId)
    }

    private fun processChatMessage(chatUUID: UUID, messageUUID: UUID, senderUUID: UUID, senderUsername: String, fetched: FetchMessageResponse, messageRegistryId: String) {
        val db = dataBase

        val chatInfo = ChatInfo(chatUUID, senderUsername, ChatType.TWO_PEOPLE, listOf(userUUID.toString(), senderUUID.toString()))

        val cursor = db.rawQuery("SELECT username FROM contacts WHERE user_uuid = ?", arrayOf(senderUUID.toString()))

        if (!cursor.moveToNext()) {
            val statement = db.compileStatement("INSERT INTO contacts (user_uuid, username, alias) VALUES (?, ?)")
            statement.bindString(1, senderUUID.toString())
            statement.bindString(2, senderUsername)
            statement.bindString(3, senderUsername)
            statement.execute()

            sendKeyRequest(senderUUID)

            createChat(chatInfo, db)
        }

        cursor.close()

//        sendPacket(ChangeChatMessageStatusPacketToServer(packet.message.id, MessageStatus.RECEIVED, System.currentTimeMillis(), ConnectionService.INSTANCE.username!!, packet.from, packet.chatUUID))

        val registryID = messageRegistryId.decryptRSA(pMessageKey!!).toInt()
        val message = ChatMessageRegistry.create(registryID)
        message.id = messageUUID
        message.senderUUID = senderUUID
        message.text = fetched.text.decryptRSA(pMessageKey!!)
        message.timeSent = fetched.timeSent

        saveMessage(chatInfo, ChatMessageWrapper(message, MessageStatus.RECEIVED, false, 0L), db)

        val cursor2 = db.rawQuery("SELECT unread_messages FROM chats WHERE chat_uuid = '${chatInfo.chatUUID}' LIMIT 1", null)

        if (cursor2.moveToNext()) {
            val unread_messages = cursor2.getInt(0)
            cursor2.close()

            db.execSQL("UPDATE chats SET unread_messages = unread_messages + 1 WHERE chat_uuid = '${chatInfo.chatUUID}'")

            val intent = Intent(SendChatMessageRequest.TARGET)
            intent.putExtra("chatUUID", chatUUID.toString())
            intent.putExtra("senderName", senderUsername)
            intent.putExtra("senderUUID", senderUUID.toString())
            intent.putExtra("unreadMessages", unread_messages + 1)
            intent.putExtra("message.id", message.id.toString())
            intent.putExtra("message.senderUUID", message.senderUUID)
            intent.putExtra("message.text", message.text)
            intent.putExtra("message.additionalInfo", message.getAdditionalInfo())
            intent.putExtra("message.timeSent", message.timeSent)
            intent.putExtra("message.messageID", registryID)
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        } else {
            Log.w("WARNING", "NO CHAT FOUND, THIS SHOULD NEVER HAPPEN")
        }
    }

    private fun sendKeyRequest(requestedUUID: UUID) {
        object : Thread() {
            override fun run() {
                val connection: URLConnection = URL("localhost/" + KeyRequest.TARGET).openConnection()
                connection.readTimeout = 15000
                connection.connectTimeout = 15000
                connection.doInput = true
                connection.doOutput = true

                val gson = genGson()
                gson.toJson(KeyRequest(requestedUUID), BufferedWriter(OutputStreamWriter(connection.getOutputStream())))

                val response = gson.fromJson(InputStreamReader(connection.getInputStream()), KeyResponse::class.java)
                val statement = dataBase.compileStatement("UPDATE contacts SET message_key = ? WHERE user_uuid = ?")
                statement.bindString(1, BaseEncoding.base64().encode(response.publicMessageKey.encoded))
                statement.bindString(2, requestedUUID.toString())
                statement.execute()
            }
        }.start()
    }
}