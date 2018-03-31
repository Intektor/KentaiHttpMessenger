package de.intektor.kentai.kentai.direct.connection.handler

import android.content.Intent
import de.intektor.kentai.KentaiClient
import de.intektor.kentai_http_common.tcp.IPacketHandler
import de.intektor.kentai_http_common.tcp.server_to_client.TypingPacketToClient
import java.net.Socket

/**
 * @author Intektor
 */
class TypingPacketToClientHandler : IPacketHandler<TypingPacketToClient> {

    override fun handlePacket(packet: TypingPacketToClient, socketFrom: Socket) {
        val typingIntent = Intent("de.intektor.kentai.typing")
        typingIntent.putExtra("chatUUID", packet.chatUUID)
        typingIntent.putExtra("senderUUID", packet.userUUID)
        KentaiClient.INSTANCE.applicationContext.sendBroadcast(typingIntent)
    }
}