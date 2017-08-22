package de.intektor.kentai.kentai.chat

import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
data class ChatReceiver(val receiverUUID: UUID, val publicKey: Key, val type: ReceiverType) {
    enum class ReceiverType {
        USER,
        GROUP
    }
}