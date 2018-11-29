package de.intektor.mercury.chat.adapter

import de.intektor.mercury.chat.ChatMessageWrapper
import de.intektor.mercury.task.ReferenceState

open class ReferenceHolder(chatMessageInfo: ChatMessageWrapper, var referenceState: ReferenceState, var progress: Int = 0) : ChatAdapterMessage(chatMessageInfo)