package de.intektor.mercury.connection.handler

import de.intektor.mercury.action.user.ActionTyping
import de.intektor.mercury.connection.ClientContext
import de.intektor.mercury_common.tcp.IPacketHandler
import de.intektor.mercury_common.tcp.server_to_client.TypingPacketToClient
import java.net.Socket

/**
 * @author Intektor
 */
class TypingPacketToClientHandler : IPacketHandler<TypingPacketToClient, ClientContext> {

    override fun handlePacket(packet: TypingPacketToClient, socketFrom: Socket, context: ClientContext) {
        context.directConnectionService.scheduleTask {
            ActionTyping.launch(context.directConnectionService, packet.chatUUID, packet.userUUID)
        }
    }
}