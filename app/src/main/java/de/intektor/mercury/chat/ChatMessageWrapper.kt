package de.intektor.mercury.chat

import de.intektor.mercury_common.chat.MessageStatus

/**
 * @author Intektor
 */
data class ChatMessageWrapper(val chatMessageInfo: ChatMessageInfo, var latestStatus: MessageStatus, var latestUpdateTime: Long) {
    val message = chatMessageInfo.message
}