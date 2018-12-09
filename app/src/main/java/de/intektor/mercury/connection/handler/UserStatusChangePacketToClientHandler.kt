package de.intektor.mercury.connection.handler

import de.intektor.mercury.action.user.ActionUserStatusChange
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.connection.ClientContext
import de.intektor.mercury_common.tcp.IPacketHandler
import de.intektor.mercury_common.tcp.server_to_client.UserStatusChangePacketToClient
import java.net.Socket

/**
 * @author Intektor
 */
class UserStatusChangePacketToClientHandler : IPacketHandler<UserStatusChangePacketToClient, ClientContext> {

    override fun handlePacket(packet: UserStatusChangePacketToClient, socketFrom: Socket, context: ClientContext) {
        context.directConnectionService.scheduleTask {
            val mercuryClient = context.directConnectionService.mercuryClient()

            for (userChange in packet.list) {
                mercuryClient.userStatusMap[userChange.userUUID] = userChange

                ActionUserStatusChange.launch(mercuryClient, userChange.userUUID, userChange.status, userChange.time)
            }
        }
    }
}