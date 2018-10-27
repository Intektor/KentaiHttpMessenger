package de.intektor.mercury.connection.handler

import android.content.Intent
import de.intektor.mercury.util.ACTION_DOWNLOAD_PROFILE_PICTURE
import de.intektor.mercury.util.KEY_PROFILE_PICTURE_TYPE
import de.intektor.mercury.util.KEY_USER_UUID
import de.intektor.mercury.connection.DirectConnectionManager
import de.intektor.mercury.io.ChatMessageService
import de.intektor.mercury_common.tcp.IPacketHandler
import de.intektor.mercury_common.tcp.server_to_client.ProfilePictureUpdatedPacketToClient
import de.intektor.mercury_common.users.ProfilePictureType
import java.net.Socket

/**
 * @author Intektor
 */
class ProfilePictureUpdatedPacketToClientHandler : IPacketHandler<ProfilePictureUpdatedPacketToClient> {
    override fun handlePacket(packet: ProfilePictureUpdatedPacketToClient, socketFrom: Socket) {
        val thread = Thread.currentThread() as DirectConnectionManager.LaunchThread

        val downloadIntent = Intent(thread.mercuryClient, ChatMessageService::class.java)
        downloadIntent.action = ACTION_DOWNLOAD_PROFILE_PICTURE
        downloadIntent.putExtra(KEY_USER_UUID, packet.userUUID)
        downloadIntent.putExtra(KEY_PROFILE_PICTURE_TYPE, ProfilePictureType.SMALL)
        thread.mercuryClient.startService(downloadIntent)
    }
}