package de.intektor.kentai.kentai.direct.connection

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.address
import de.intektor.kentai.kentai.chat.readContacts
import de.intektor.kentai.kentai.direct.connection.handler.HeartbeatPacketToClientHandler
import de.intektor.kentai.kentai.direct.connection.handler.TypingPacketToClientHandler
import de.intektor.kentai.kentai.direct.connection.handler.UserStatusChangePacketToClientHandler
import de.intektor.kentai_http_common.tcp.*
import de.intektor.kentai_http_common.tcp.client_to_server.HeartbeatPacketToServer
import de.intektor.kentai_http_common.tcp.client_to_server.IdentificationPacketToServer
import de.intektor.kentai_http_common.tcp.client_to_server.UserPreferencePacketToServer
import de.intektor.kentai_http_common.tcp.server_to_client.HeartbeatPacketToClient
import de.intektor.kentai_http_common.tcp.server_to_client.TypingPacketToClient
import de.intektor.kentai_http_common.tcp.server_to_client.UserStatusChangePacketToClient
import de.intektor.kentai_http_common.util.encryptRSA
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import kotlin.concurrent.thread

/**
 * @author Intektor
 */
object DirectConnectionManager {

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
        }

        thread {
            while (true) {
                Thread.sleep(5000L)
                if (!isConnected) {
                    launchConnection(KentaiClient.INSTANCE.dataBase)
                }
            }
        }
    }

    fun launchConnection(database: SQLiteDatabase) {
        if (keepConnection) return
        keepConnection = true
        thread {
            try {
                val contactList = readContacts(database)

                this.socket = Socket(address, 17348)

                val dataIn = DataInputStream(socket?.getInputStream())

                val encryptedUserUUID = KentaiClient.INSTANCE.userUUID.toString().encryptRSA(KentaiClient.INSTANCE.privateAuthKey!!)

                sendPacket(IdentificationPacketToServer(encryptedUserUUID, KentaiClient.INSTANCE.userUUID), DataOutputStream(socket?.getOutputStream()))
                sendPacket(UserPreferencePacketToServer(contactList.map { it.userUUID }.toMutableList()), DataOutputStream(socket?.getOutputStream()))

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
                            de.intektor.kentai_http_common.tcp.sendPacket(packet, DataOutputStream(socket!!.getOutputStream()))
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
                Log.e("ERROR", "Network", t)
            }
        }
    }

    fun sendPacket(packet: IPacket) {
        sendPacketQueue.add(packet)
    }

    fun exitConnection() {
        keepConnection = false
        if (socket?.isBound == true) {
            socket?.close()
        }
        KentaiClient.INSTANCE.userStatusMap.clear()

        val i = Intent("de.intektor.kentai.tcp_closed")
        KentaiClient.INSTANCE.applicationContext.sendBroadcast(i)
    }
}