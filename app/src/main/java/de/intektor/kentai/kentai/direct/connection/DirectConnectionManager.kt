package de.intektor.kentai.kentai.direct.connection

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.ACTION_DIRECT_CONNECTION_CONNECTED
import de.intektor.kentai.kentai.address
import de.intektor.kentai.kentai.chat.readContacts
import de.intektor.kentai.kentai.direct.connection.handler.*
import de.intektor.kentai.kentai.getProfilePicture
import de.intektor.kentai_http_common.tcp.*
import de.intektor.kentai_http_common.tcp.client_to_server.HeartbeatPacketToServer
import de.intektor.kentai_http_common.tcp.client_to_server.IdentificationPacketToServer
import de.intektor.kentai_http_common.tcp.client_to_server.InterestedUser
import de.intektor.kentai_http_common.tcp.client_to_server.UserPreferencePacketToServer
import de.intektor.kentai_http_common.tcp.server_to_client.*
import de.intektor.kentai_http_common.util.encryptRSA
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * @author Intektor
 */
class DirectConnectionManager(val kentaiClient: KentaiClient) {

    @Volatile
    private var keepConnection: Boolean = false

    @Volatile
    private var socket: Socket? = null

    @Volatile
    var lastHeartbeatTime: Long = System.currentTimeMillis()

    val isConnected: Boolean
        get() = keepConnection && socket?.isBound == true && socket?.isClosed == false

    private val sendPacketQueue = LinkedBlockingQueue<IPacket>()

    init {
        KentaiTCPOperator.packetRegistry.apply {
            registerHandler(UserStatusChangePacketToClient::class.java, UserStatusChangePacketToClientHandler())
            registerHandler(HeartbeatPacketToClient::class.java, HeartbeatPacketToClientHandler())
            registerHandler(TypingPacketToClient::class.java, TypingPacketToClientHandler())
            registerHandler(UserViewChatPacketToClient::class.java, UserViewChatPacketToClientHandler())
            registerHandler(ProfilePictureUpdatedPacketToClient::class.java, ProfilePictureUpdatedPacketToClientHandler())
        }

        CheckThread().start()
    }

    inner class CheckThread : Thread() {
        override fun run() {
            while (true) {
                Thread.sleep(5000L)
                if (!isConnected) {
                    launchConnection(kentaiClient.dataBase)
                }
            }
        }
    }

    fun launchConnection(database: SQLiteDatabase) {
        if (keepConnection) return
        keepConnection = true
        LaunchThread(kentaiClient).start()
    }

    fun sendPacket(packet: IPacket) {
        sendPacketQueue.add(packet)
    }

    fun exitConnection() {
        keepConnection = false
        if (socket?.isBound == true) {
            socket?.close()
        }
        kentaiClient.userStatusMap.clear()

        val i = Intent("de.intektor.kentai.tcp_closed")
        kentaiClient.applicationContext.sendBroadcast(i)
    }

    inner class LaunchThread(val kentaiClient: KentaiClient) : Thread() {

        override fun run() {
            try {
                val contactList = readContacts(kentaiClient.dataBase)

                socket = Socket(address, 17348)

                val dataIn = DataInputStream(socket?.getInputStream())

                val encryptedUserUUID = kentaiClient.userUUID.toString().encryptRSA(kentaiClient.privateAuthKey!!)

                sendPacket(IdentificationPacketToServer(encryptedUserUUID, kentaiClient.userUUID), DataOutputStream(socket?.getOutputStream()))
                sendPacket(UserPreferencePacketToServer(kentaiClient.getCurrentInterestedUsers().map { userUUID ->
                    val time = getProfilePicture(userUUID, kentaiClient).lastModified()
                    InterestedUser(userUUID, time)
                }), DataOutputStream(socket?.getOutputStream()))

                kentaiClient.sendBroadcast(Intent(ACTION_DIRECT_CONNECTION_CONNECTED))

                thread {
                    while (keepConnection && socket?.isBound == true) {
                        try {
                            sendPacket(HeartbeatPacketToServer(), DataOutputStream(socket?.getOutputStream()))
                        } catch (t: Throwable) {
                            if (keepConnection) {
                                exitConnection()
                            }
                        }
                        Thread.sleep(5000L)
                    }
                }

                thread {
                    while (keepConnection && socket?.isBound == true) {
                        Thread.sleep(10000)
                        if (System.currentTimeMillis() - 10000 > lastHeartbeatTime) {
                            exitConnection()
                        }
                    }
                }

                thread {
                    while (isConnected) {
                        val packet = sendPacketQueue.take()
                        if (isConnected) {
                            sendPacket(packet, DataOutputStream(socket!!.getOutputStream()))
                        }
                    }
                }

                while (isConnected && keepConnection) {
                    try {
                        val packet = readPacket(dataIn, KentaiTCPOperator.packetRegistry, Side.CLIENT)
                        handlePacket(packet, socket!!)
                    } catch (t: Throwable) {
                        Log.e("ERROR", "Networking", t)
                        exitConnection()
                    }
                }
            } catch (t: Throwable) {
                Log.e("ERROR", "Networking", t)
            }
        }
    }
}