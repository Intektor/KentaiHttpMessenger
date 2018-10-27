package de.intektor.mercury.connection

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.connection.handler.*
import de.intektor.mercury.io.AddressHolder
import de.intektor.mercury.util.ACTION_DIRECT_CONNECTION_CONNECTED
import de.intektor.mercury.util.getProfilePicture
import de.intektor.mercury_common.tcp.*
import de.intektor.mercury_common.tcp.client_to_server.HeartbeatPacketToServer
import de.intektor.mercury_common.tcp.client_to_server.IdentificationPacketToServer
import de.intektor.mercury_common.tcp.client_to_server.InterestedUser
import de.intektor.mercury_common.tcp.client_to_server.UserPreferencePacketToServer
import de.intektor.mercury_common.tcp.server_to_client.*
import de.intektor.mercury_common.util.encryptRSA
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * @author Intektor
 */
class DirectConnectionManager(val mercuryClient: MercuryClient) {

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
        MercuryTCPOperator.packetRegistry.apply {
            registerHandler(UserStatusChangePacketToClient::class.java, UserStatusChangePacketToClientHandler())
            registerHandler(HeartbeatPacketToClient::class.java, HeartbeatPacketToClientHandler())
            registerHandler(TypingPacketToClient::class.java, TypingPacketToClientHandler())
            registerHandler(UserViewChatPacketToClient::class.java, UserViewChatPacketToClientHandler())
            registerHandler(ProfilePictureUpdatedPacketToClient::class.java, ProfilePictureUpdatedPacketToClientHandler())
        }

        thread {
            while (true) {
                Thread.sleep(5000L)
                if (!isConnected && wantConnection) {
                    launchConnection(mercuryClient.dataBase)
                }
            }
        }
    }

    fun launchConnection(database: SQLiteDatabase) {
        if (isConnected) return
        keepConnection = true

        lastHeartbeatTime = System.currentTimeMillis()

        LaunchThread(mercuryClient).start()
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
        mercuryClient.userStatusMap.clear()

        val i = Intent("de.intektor.mercury.tcp_closed")
        mercuryClient.applicationContext.sendBroadcast(i)
    }

    inner class LaunchThread(val mercuryClient: MercuryClient) : Thread() {

        override fun run() {
            try {
                val connectedSocket = Socket(AddressHolder.ADDRESS, 17348)

                socket = connectedSocket

                val dataIn = DataInputStream(connectedSocket.getInputStream())

                val client = ClientPreferences.getClientUUID(mercuryClient)

                val encryptedUserUUID = client.toString().encryptRSA(ClientPreferences.getPrivateAuthKey(mercuryClient))

                val dataOut = DataOutputStream(connectedSocket.getOutputStream())
                sendPacket(IdentificationPacketToServer(encryptedUserUUID, client), dataOut)
                sendPacket(UserPreferencePacketToServer(mercuryClient.getCurrentInterestedUsers().map { userUUID ->
                    val time = getProfilePicture(userUUID, mercuryClient).lastModified()
                    InterestedUser(userUUID, time)
                }.toSet()), dataOut)

                mercuryClient.sendBroadcast(Intent(ACTION_DIRECT_CONNECTION_CONNECTED))

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
                        val packet = readPacket(dataIn, MercuryTCPOperator.packetRegistry, Side.CLIENT)
                        handlePacket(packet, socket!!)
                    } catch (t: Throwable) {
                        if (keepConnection && connectedSocket == socket && isConnected) {
                            exitConnection()
                        }
                    }
                }
            } catch (t: Throwable) {
            }
        }
    }
}