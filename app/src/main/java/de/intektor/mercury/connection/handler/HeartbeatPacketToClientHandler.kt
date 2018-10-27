package de.intektor.mercury.connection.handler

import de.intektor.mercury.connection.DirectConnectionManager
import de.intektor.mercury_common.tcp.IPacketHandler
import de.intektor.mercury_common.tcp.server_to_client.HeartbeatPacketToClient
import java.net.Socket

/**
 * @author Intektor
 */
class HeartbeatPacketToClientHandler : IPacketHandler<HeartbeatPacketToClient> {

    override fun handlePacket(packet: HeartbeatPacketToClient, socketFrom: Socket) {
        val thread = Thread.currentThread() as DirectConnectionManager.LaunchThread

        thread.mercuryClient.directConnectionManager.lastHeartbeatTime = System.currentTimeMillis()
    }
}