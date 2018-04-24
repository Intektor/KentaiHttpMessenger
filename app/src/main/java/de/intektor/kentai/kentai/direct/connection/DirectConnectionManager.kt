package de.intektor.kentai.kentai.direct.connection

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.ACTION_DIRECT_CONNECTION_CONNECTED
import de.intektor.kentai.kentai.address
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

    @Volatile
    var wantConnection = false

    init {
        KentaiTCPOperator.packetRegistry.apply {
            registerHandler(UserStatusChangePacketToClient::class.java, UserStatusChangePacketToClientHandler())
            registerHandler(HeartbeatPacketToClient::class.java, HeartbeatPacketToClientHandler())
            registerHandler(TypingPacketToClient::class.java, TypingPacketToClientHandler())
            registerHandler(UserViewChatPacketToClient::class.java, UserViewChatPacketToClientHandler())
            registerHandler(ProfilePictureUpdatedPacketToClient::class.java, ProfilePictureUpdatedPacketToClientHandler())
        }
    }

    fun launchConnection(database: SQLiteDatabase) {
        if (isConnected) return
        keepConnection = true

        lastHeartbeatTime = System.currentTimeMillis()

        thread {
            while (wantConnection) {
                Thread.sleep(5000L)
                if (!isConnected && wantConnection) {
                    launchConnection(kentaiClient.dataBase)
                }
            }
        }

        LaunchThread(kentaiClient).start()
    }

    fun sendPacket(packet: IPacket) {
        sendPacketQueue.add(packet)
    }

    fun exitConnection() {
        if (!isConnected) return
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
                val connectedSocket = Socket(address, 17348)

                socket = connectedSocket

                val dataIn = DataInputStream(connectedSocket.getInputStream())

                val encryptedUserUUID = kentaiClient.userUUID.toString().encryptRSA(kentaiClient.privateAuthKey!!)

                val dataOut = DataOutputStream(connectedSocket.getOutputStream())
                sendPacket(IdentificationPacketToServer(encryptedUserUUID, kentaiClient.userUUID), dataOut)
                sendPacket(UserPreferencePacketToServer(kentaiClient.getCurrentInterestedUsers().map { userUUID ->
                    val time = getProfilePicture(userUUID, kentaiClient).lastModified()
                    InterestedUser(userUUID, time)
                }.toSet()), dataOut)

                kentaiClient.sendBroadcast(Intent(ACTION_DIRECT_CONNECTION_CONNECTED))

                thread {
                    while (keepConnection && connectedSocket.isBound) {
                        try {
                            sendPacket(HeartbeatPacketToServer(), dataOut)
                        } catch (t: Throwable) {
                            if (keepConnection && connectedSocket == socket && isConnected) {
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
                    try {
                        while (isConnected) {
                            val packet = sendPacketQueue.take()
                            if (isConnected) {
                                sendPacket(packet, DataOutputStream(connectedSocket.getOutputStream()))
                            }
                        }
                    } catch (t: Throwable) {
                        if (keepConnection && connectedSocket == socket && isConnected) {
                            exitConnection()
                        }
                    }
                }

                while (isConnected && keepConnection) {
                    try {
                        val packet = readPacket(dataIn, KentaiTCPOperator.packetRegistry, Side.CLIENT)
                        handlePacket(packet, socket!!)
                    } catch (t: Throwable) {
                        Log.e("ERROR", "Networking", t)
                        if (keepConnection && connectedSocket == socket && isConnected) {
                            exitConnection()
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.e("ERROR", "Networking", t)
            }
        }
    }
}