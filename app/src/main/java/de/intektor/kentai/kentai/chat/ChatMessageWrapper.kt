package de.intektor.kentai.kentai.chat

import de.intektor.kentai_http_common.chat.ChatMessage
import de.intektor.kentai_http_common.chat.MessageStatus

/**
 * @author Intektor
 */
class ChatMessageWrapper {

    var message: ChatMessage
    var status: MessageStatus
    var client: Boolean = false
    var statusChangeTime: Long = 0L

    constructor(message: ChatMessage, status: MessageStatus, client: Boolean, statusChangeTime: Long) {
        this.message = message
        this.status = status
        this.client = client
        this.statusChangeTime = statusChangeTime
    }
}