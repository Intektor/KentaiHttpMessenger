package de.intektor.mercury.chat

import de.intektor.mercury.task.ReferenceState

class VoiceReferenceHolder(chatMessageInfo: ChatMessageWrapper,
                           referenceState: ReferenceState,
                           progress: Int = 0,
                           var isPlaying: Boolean = false,
                           var playProgress: Int = 0,
                           var maxPlayProgress: Int = 0) : ReferenceHolder(chatMessageInfo, referenceState, progress)