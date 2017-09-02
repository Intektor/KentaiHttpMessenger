package de.intektor.kentai.kentai.firebase

import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import com.google.common.io.BaseEncoding
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import de.intektor.kentai.R
import de.intektor.kentai.kentai.DbHelper
import de.intektor.kentai.kentai.address
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.httpPost
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.client_to_server.FetchMessageRequest
import de.intektor.kentai_http_common.client_to_server.KeyRequest
import de.intektor.kentai_http_common.client_to_server.SendChatMessageRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.FetchMessageResponse
import de.intektor.kentai_http_common.server_to_client.KeyResponse
import de.intektor.kentai_http_common.util.*
import java.io.*
import java.net.URL
import java.net.URLConnection
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
class FMService : FirebaseMessagingService() {

    private lateinit var dataBase: SQLiteDatabase
    private var privateAuthKey: Key? = null
    private var privateMessageKey: Key? = null
    private var publicMessageKey: Key? = null
    private var userUUID: UUID? = null
    private var username: String? = null

    override fun onCreate() {
        super.onCreate()
//        val dbHelper = DbHelper(this)
//        dataBase = dbHelper.writableDatabase
//
//        val pKeyFile = File(filesDir.path + "/" + "keys/authKeyPrivate.key")
//        if (pKeyFile.exists()) {
//            privateAuthKey = readPrivateKey(DataInputStream(pKeyFile.inputStream()))
//            privateMessageKey = readPrivateKey(DataInputStream(File(filesDir.path + "/" + "keys/encryptionKeyPrivate.key").inputStream()))
//            publicMessageKey = readKey(DataInputStream(File(filesDir.path + "/keys/encryptionKeyPublic.key").inputStream()))
//        }
//
//        val userInfoFile = File(filesDir.path + "/" + "username.info")
//        if (userInfoFile.exists()) {
//            val dataIn = DataInputStream(userInfoFile.inputStream())
//            username = dataIn.readUTF()
//            userUUID = UUID.fromString(dataIn.readUTF())
//        }

    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
//        val type = FCMMessageType.values()[remoteMessage.data["type"]!!.toInt()]
//        if (type == FCMMessageType.CHAT_MESSAGE) {
//            val chatUUID = UUID.fromString(remoteMessage.data["chat_uuid"])
//            val messageUUID = UUID.fromString(remoteMessage.data["message_uuid"])
//            val senderUUID = UUID.fromString(remoteMessage.data["sender_uuid"])
//            val senderUsername = remoteMessage.data["sender_username"]
//            val messageRegistryId = remoteMessage.data["message_registry_id"]
//            val previewText = remoteMessage.data["message_registry_id"]
//
//            val cursor = dataBase.rawQuery("SELECT username, message_key FROM contacts WHERE user_uuid = ?", arrayOf(senderUUID.toString()))
//
//            val chatInfo = ChatInfo(chatUUID, senderUsername!!, ChatType.TWO_PEOPLE, listOf(ChatReceiver(userUUID!!, publicMessageKey!!, ChatReceiver.ReceiverType.USER), ChatReceiver(senderUUID, null, ChatReceiver.ReceiverType.USER)))
//
//            if (!cursor.moveToNext()) {
//                val statement = dataBase.compileStatement("INSERT INTO contacts (user_uuid, username, alias) VALUES (?, ?, ?)")
//                statement.bindString(1, senderUUID.toString())
//                statement.bindString(2, senderUsername)
//                statement.bindString(3, senderUsername)
//                statement.execute()
//
//                sendKeyRequest(senderUUID)
//
//                createChat(chatInfo, dataBase)
//            }
//
//            cursor.close()
//
//            dataBase.compileStatement("INSERT INTO fetching_messages (chat_uuid, message_uuid) VALUES (?, ?)").use { statement ->
//                statement.bindString(1, chatUUID.toString())
//                statement.bindString(2, messageUUID.toString())
//                statement.execute()
//            }
//
//            try {
//                fetchAndProcessMessageData(chatUUID, messageUUID, senderUUID, senderUsername, messageRegistryId!!, chatInfo)
//                dataBase.compileStatement("DELETE FROM fetching_messages WHERE message_uuid = ?").use { statement ->
//                    statement.bindString(1, messageUUID.toString())
//                    statement.execute()
//                }
//            } catch (t: Throwable) {
//                TODO("Display notification")
//            }
//        }
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

//        sendPacket(ChangeChatMessageStatusPacketToServer(packet.message.id, MessageStatus.RECEIVED, System.currentTimeMillis(), ConnectionService.INSTANCE.username!!, packet.from, packet.chatUUID))

        val registryID = messageRegistryId.decryptRSA(privateMessageKey!!).toInt()
        val message = ChatMessageRegistry.create(registryID)
        message.id = messageUUID
        message.senderUUID = senderUUID
        message.text = fetched.text
        message.timeSent = fetched.timeSent
        message.aesKey = fetched.aesKey
        message.initVector = fetched.initVector

        message.decrypt(privateMessageKey!!)

        saveMessage(chatInfo, ChatMessageWrapper(message, MessageStatus.RECEIVED, false, 0L), db)

        val cursor2 = db.rawQuery("SELECT unread_messages, chat_name, type FROM chats WHERE chat_uuid = '${chatInfo.chatUUID}' LIMIT 1", null)

        if (cursor2.moveToNext()) {
            val unread_messages = cursor2.getInt(0)
            val chatName = cursor2.getString(1)
            val chatType = cursor2.getInt(2)
            cursor2.close()

            db.execSQL("UPDATE chats SET unread_messages = unread_messages + 1 WHERE chat_uuid = '${chatInfo.chatUUID}'")

            val intent = Intent("de.intektor.kentai.chatNotification")
            intent.putExtra("unreadMessages", unread_messages + 1)
            intent.putExtra("chatType", chatType)
            intent.putExtra("chatUUID", chatUUID.toString())
            intent.putExtra("chatName", chatName)
            intent.putExtra("senderName", senderUsername)
            intent.putExtra("senderUUID", senderUUID.toString())
            intent.putExtra("message.id", message.id.toString())
            intent.putExtra("message.senderUUID", message.senderUUID)
            intent.putExtra("message.text", message.text)
            intent.putExtra("message.additionalInfo", message.getAdditionalInfo())
            intent.putExtra("message.timeSent", message.timeSent)
            intent.putExtra("message.messageID", registryID)

            sendOrderedBroadcast(intent, null)
        } else {
            Log.w("WARNING", "NO CHAT FOUND, THIS SHOULD NEVER HAPPEN")
        }
    }

    private fun sendKeyRequest(requestedUUID: UUID) {
        object : Thread() {
            override fun run() {
                val gson = genGson()
                val response = gson.fromJson(httpPost(gson.toJson(KeyRequest(listOf(requestedUUID))), KeyRequest.TARGET), KeyResponse::class.java)

                for ((key, entry) in response.keys) {
                    val statement = dataBase.compileStatement("UPDATE contacts SET message_key = ? WHERE user_uuid = ?")
                    statement.bindString(1, BaseEncoding.base64().encode(entry.encoded))
                    statement.bindString(2, key.toString())
                    statement.execute()
                }
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        dataBase.close()
    }
}