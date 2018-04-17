package de.intektor.kentai.kentai.direct.connection.handler

import android.content.Intent
import de.intektor.kentai.kentai.direct.connection.DirectConnectionManager
import de.intektor.kentai_http_common.tcp.IPacketHandler
import de.intektor.kentai_http_common.tcp.server_to_client.UserStatusChangePacketToClient
import java.net.Socket

/**
 * @author Intektor
 */
class UserStatusChangePacketToClientHandler : IPacketHandler<UserStatusChangePacketToClient> {

    override fun handlePacket(packet: UserStatusChangePacketToClient, socketFrom: Socket) {
        val thread = Thread.currentThread() as DirectConnectionManager.LaunchThread

        val i = Intent("de.intektor.kentai.user_status_change")
        i.putExtra("amount", packet.list.size)

        for ((index, userChange) in packet.list.withIndex()) {
            i.putExtra("status$index", userChange.status)
            i.putExtra("userUUID$index", userChange.userUUID)
            i.putExtra("time$index", userChange.time)
        }

        thread.kentaiClient.sendBroadcast(i)
    }
}