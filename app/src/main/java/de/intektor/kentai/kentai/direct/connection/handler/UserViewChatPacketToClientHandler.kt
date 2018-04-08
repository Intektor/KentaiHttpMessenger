package de.intektor.kentai.kentai.direct.connection.handler

import android.content.Intent
import de.intektor.kentai.kentai.ACTION_USER_VIEW_CHAT
import de.intektor.kentai.kentai.KEY_CHAT_UUID
import de.intektor.kentai.kentai.KEY_USER_UUID
import de.intektor.kentai.kentai.KEY_USER_VIEW
import de.intektor.kentai.kentai.direct.connection.DirectConnectionManager
import de.intektor.kentai_http_common.tcp.IPacketHandler
import de.intektor.kentai_http_common.tcp.server_to_client.UserViewChatPacketToClient
import java.net.Socket

class UserViewChatPacketToClientHandler : IPacketHandler<UserViewChatPacketToClient> {

    override fun handlePacket(packet: UserViewChatPacketToClient, socketFrom: Socket) {
        val thread = Thread.currentThread() as DirectConnectionManager.LaunchThread

        val i = Intent(ACTION_USER_VIEW_CHAT)

        i.putExtra(KEY_USER_UUID, packet.userUUID)
        i.putExtra(KEY_CHAT_UUID, packet.chatUUID)
        i.putExtra(KEY_USER_VIEW, packet.view)

        thread.kentaiClient.sendBroadcast(i)
    }
}