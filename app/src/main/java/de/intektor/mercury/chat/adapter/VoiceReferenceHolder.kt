package de.intektor.mercury.chat.adapter

import de.intektor.mercury.chat.ChatMessageWrapper
import de.intektor.mercury.task.ReferenceState

class VoiceReferenceHolder(chatMessageInfo: ChatMessageWrapper,
                           referenceState: ReferenceState,
                           progress: Int = 0,
                           var isPlaying: Boolean = false,
                           var playProgress: Int = 0,
                           var maxPlayProgress: Int = 0) : ReferenceHolder(chatMessageInfo, referenceState, progress) {
    fun copy() = VoiceReferenceHolder(message, referenceState, progress, isPlaying, playProgress, maxPlayProgress)
}