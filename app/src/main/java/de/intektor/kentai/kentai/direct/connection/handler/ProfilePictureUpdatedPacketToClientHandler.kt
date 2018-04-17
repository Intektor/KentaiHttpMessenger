package de.intektor.kentai.kentai.direct.connection.handler

import android.content.Intent
import de.intektor.kentai.kentai.ACTION_DOWNLOAD_PROFILE_PICTURE
import de.intektor.kentai.kentai.KEY_PROFILE_PICTURE_TYPE
import de.intektor.kentai.kentai.KEY_USER_UUID
import de.intektor.kentai.kentai.direct.connection.DirectConnectionManager
import de.intektor.kentai.kentai.firebase.SendService
import de.intektor.kentai_http_common.tcp.IPacketHandler
import de.intektor.kentai_http_common.tcp.server_to_client.ProfilePictureUpdatedPacketToClient
import de.intektor.kentai_http_common.users.ProfilePictureType
import java.net.Socket

/**
 * @author Intektor
 */
class ProfilePictureUpdatedPacketToClientHandler : IPacketHandler<ProfilePictureUpdatedPacketToClient> {
    override fun handlePacket(packet: ProfilePictureUpdatedPacketToClient, socketFrom: Socket) {
        val thread = Thread.currentThread() as DirectConnectionManager.LaunchThread

        val downloadIntent = Intent(thread.kentaiClient, SendService::class.java)
        downloadIntent.action = ACTION_DOWNLOAD_PROFILE_PICTURE
        downloadIntent.putExtra(KEY_USER_UUID, packet.userUUID)
        downloadIntent.putExtra(KEY_PROFILE_PICTURE_TYPE, ProfilePictureType.SMALL)
        thread.kentaiClient.startService(downloadIntent)
    }
}