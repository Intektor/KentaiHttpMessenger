package de.intektor.mercury.connection.handler

import de.intektor.mercury.action.user.ActionUserStatusChange
import de.intektor.mercury.connection.DirectConnectionManager
import de.intektor.mercury_common.tcp.IPacketHandler
import de.intektor.mercury_common.tcp.server_to_client.UserStatusChangePacketToClient
import java.net.Socket

/**
 * @author Intektor
 */
class UserStatusChangePacketToClientHandler : IPacketHandler<UserStatusChangePacketToClient> {

    override fun handlePacket(packet: UserStatusChangePacketToClient, socketFrom: Socket) {
        val thread = Thread.currentThread() as DirectConnectionManager.LaunchThread

        for (userChange in packet.list) {
            thread.mercuryClient.userStatusMap[userChange.userUUID] = userChange

            ActionUserStatusChange.launch(thread.mercuryClient, userChange.userUUID, userChange.status, userChange.time)
        }
    }
}