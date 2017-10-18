package de.intektor.kentai.kentai.direct.connection

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.address
import de.intektor.kentai.kentai.chat.readContacts
import de.intektor.kentai.kentai.direct.connection.handler.HeartbeatPacketToClientHandler
import de.intektor.kentai.kentai.direct.connection.handler.UserStatusChangePacketToClientHandler
import de.intektor.kentai_http_common.tcp.*
import de.intektor.kentai_http_common.tcp.client_to_server.HeartbeatPacketToServer
import de.intektor.kentai_http_common.tcp.client_to_server.IdentificationPacketToServer
import de.intektor.kentai_http_common.tcp.client_to_server.UserPreferencePacketToServer
import de.intektor.kentai_http_common.tcp.server_to_client.HeartbeatPacketToClient
import de.intektor.kentai_http_common.tcp.server_to_client.UserStatusChangePacketToClient
import de.intektor.kentai_http_common.util.encryptRSA
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket
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
    private var context: Context? = null

    @Volatile
    var lastHearbeatTime: Long = System.currentTimeMillis()

    init {
        KentaiTCPOperator.packetRegistry.apply {
            registerHandler(UserStatusChangePacketToClient::class.java, UserStatusChangePacketToClientHandler())
            registerHandler(HeartbeatPacketToClient::class.java, HeartbeatPacketToClientHandler())
        }
    }

    fun launchConnection(database: SQLiteDatabase, context: Context) {
        if (keepConnection) return
        this.context = context
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
                        if (System.currentTimeMillis() - 10000 > lastHearbeatTime) {
                            exitConnection()
                        }
                    }
                }

                while (socket?.isBound == true && keepConnection) {
                    try {
                        val packet = readPacket(dataIn, KentaiTCPOperator.packetRegistry, Side.CLIENT)
                        handlePacket(packet, socket!!)
                    } catch (t: Throwable) {
                        Log.e("ERROR", "Networking", t)
                    }
                }
            } catch (t: Throwable) {
                Log.e("ERROR", "Network", t)
            }
        }
    }

    fun exitConnection() {
        keepConnection = false
        if (socket?.isBound == true) {
            socket?.close()
        }
        KentaiClient.INSTANCE.userStatusMap.clear()

        val i = Intent("de.intektor.kentai.tcp_closed")
        context?.sendBroadcast(i)
    }
}