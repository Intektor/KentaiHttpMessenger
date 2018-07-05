package de.intektor.kentai.kentai.chat

import de.intektor.kentai_http_common.chat.ChatMessage
import de.intektor.kentai_http_common.chat.MessageStatus
import java.util.*

/**
 * @author Intektor
 */
data class ChatMessageWrapper(var message: ChatMessage, var status: MessageStatus, var client: Boolean, var statusChangeTime: Long, var chatUUID: UUID)