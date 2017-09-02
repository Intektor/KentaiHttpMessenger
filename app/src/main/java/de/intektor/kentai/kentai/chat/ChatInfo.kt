package de.intektor.kentai.kentai.chat

import de.intektor.kentai_http_common.chat.ChatType
import java.io.Serializable
import java.util.*

/**
 * @author Intektor
 */
class ChatInfo(val chatUUID: UUID, val chatName: String, val chatType: ChatType, val participants: List<ChatReceiver>)