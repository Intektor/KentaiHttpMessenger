package de.intektor.kentai.kentai.direct.connection.handler

import de.intektor.kentai.kentai.direct.connection.DirectConnectionManager
import de.intektor.kentai_http_common.tcp.IPacketHandler
import de.intektor.kentai_http_common.tcp.server_to_client.HeartbeatPacketToClient
import java.net.Socket

/**
 * @author Intektor
 */
class HeartbeatPacketToClientHandler : IPacketHandler<HeartbeatPacketToClient> {

    override fun handlePacket(packet: HeartbeatPacketToClient, socketFrom: Socket) {
        DirectConnectionManager.lastHeartbeatTime = System.currentTimeMillis()
    }
}