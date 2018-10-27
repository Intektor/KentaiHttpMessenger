package de.intektor.mercury.chat

import de.intektor.mercury.task.ReferenceState

open class ReferenceHolder(val chatMessageInfo: ChatMessageWrapper, var referenceState: ReferenceState, var progress: Int = 0)