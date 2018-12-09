package de.intektor.mercury.connection.handler

import de.intektor.mercury.connection.ClientContext
import de.intektor.mercury_common.tcp.IPacketHandler
import de.intektor.mercury_common.tcp.server_to_client.HeartbeatPacketToClient
import java.net.Socket

/**
 * @author Intektor
 */
class HeartbeatPacketToClientHandler : IPacketHandler<HeartbeatPacketToClient, ClientContext> {

    override fun handlePacket(packet: HeartbeatPacketToClient, socketFrom: Socket, context: ClientContext) {
        context.directConnectionService.heartbeatReceived()
    }
}