package de.intektor.kentai.kentai.network.handler

/**
 * @author Intektor
 */
//class ChangeMessageStatusPacketToClientHandler : IPacketHandler<ChangeMessageStatusPacketToClient> {
//
//    override fun handlePacket(packet: ChangeMessageStatusPacketToClient, socketFrom: Socket) {
//        val database = ConnectionService.INSTANCE.database
//        val statement = database.compileStatement("INSERT INTO message_status_change (message_uuid, status, time) VALUES(?, ?, ?)")
//        statement.bindString(1, packet.uuid.toString())
//        statement.bindLong(2, packet.status.ordinal.toLong())
//        statement.bindLong(3, packet.time)
//        statement.execute()
//
//        if (ConnectionService.INSTANCE.appOpen) {
//            val intent = Intent(ChangeMessageStatusPacketToClientHandler::class.java.simpleName)
//            intent.putExtra("chatUUID", packet.chatUUID.toString())
//            intent.putExtra("status", packet.status.ordinal)
//            intent.putExtra("uuid", packet.uuid.toString())
//            LocalBroadcastManager.getInstance(ConnectionService.INSTANCE).sendBroadcast(intent)
//        }
//    }
//}