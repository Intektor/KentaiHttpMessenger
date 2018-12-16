package de.intektor.mercury.connection.handler

import de.intektor.mercury.action.profpic.ActionProfilePictureUpdate
import de.intektor.mercury.connection.ClientContext
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury_common.tcp.IPacketHandler
import de.intektor.mercury_common.tcp.server_to_client.ProfilePictureUpdatedPacketToClient
import java.net.Socket

/**
 * @author Intektor
 */
class ProfilePictureUpdatedPacketToClientHandler : IPacketHandler<ProfilePictureUpdatedPacketToClient, ClientContext> {
    override fun handlePacket(packet: ProfilePictureUpdatedPacketToClient, socketFrom: Socket, context: ClientContext) {
        context.directConnectionService.scheduleTask {
            ProfilePictureUtil.markProfilePictureInvalid(context.directConnectionService, packet.userUUID, true)
            ActionProfilePictureUpdate.launch(context.directConnectionService, packet.userUUID)
        }
    }
}