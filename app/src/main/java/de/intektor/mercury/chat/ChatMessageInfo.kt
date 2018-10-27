package de.intektor.mercury.chat

import de.intektor.mercury_common.chat.ChatMessage
import java.util.*

/**
 * @author Intektor
 */
data class ChatMessageInfo(val message: ChatMessage, val client: Boolean, val chatUUID: UUID)