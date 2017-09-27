package de.intektor.kentai.kentai.chat

import java.util.*

data class PendingMessage(val message: ChatMessageWrapper, val chatUUID: UUID, val sendTo: List<ChatReceiver>)