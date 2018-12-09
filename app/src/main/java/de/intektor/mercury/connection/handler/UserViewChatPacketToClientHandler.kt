package de.intektor.mercury.connection.handler

import de.intektor.mercury.action.chat.ActionUserViewChat
import de.intektor.mercury.connection.ClientContext
import de.intektor.mercury_common.tcp.IPacketHandler
import de.intektor.mercury_common.tcp.server_to_client.UserViewChatPacketToClient
import java.net.Socket

class UserViewChatPacketToClientHandler : IPacketHandler<UserViewChatPacketToClient, ClientContext> {

    override fun handlePacket(packet: UserViewChatPacketToClient, socketFrom: Socket, context: ClientContext) {
        context.directConnectionService.scheduleTask {
            ActionUserViewChat.launch(context.directConnectionService, packet.chatUUID, packet.userUUID, packet.view)
        }
    }
}