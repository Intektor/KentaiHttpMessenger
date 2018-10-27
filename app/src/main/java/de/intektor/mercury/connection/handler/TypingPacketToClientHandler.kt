package de.intektor.mercury.connection.handler

import de.intektor.mercury.action.user.ActionTyping
import de.intektor.mercury.connection.DirectConnectionManager
import de.intektor.mercury_common.tcp.IPacketHandler
import de.intektor.mercury_common.tcp.server_to_client.TypingPacketToClient
import java.net.Socket

/**
 * @author Intektor
 */
class TypingPacketToClientHandler : IPacketHandler<TypingPacketToClient> {

    override fun handlePacket(packet: TypingPacketToClient, socketFrom: Socket) {
        val thread = Thread.currentThread() as DirectConnectionManager.LaunchThread

        ActionTyping.launch(thread.mercuryClient, packet.chatUUID, packet.userUUID)
    }
}