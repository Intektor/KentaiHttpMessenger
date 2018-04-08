package de.intektor.kentai.kentai.chat

import android.media.MediaPlayer

class VoiceReferenceHolder(chatMessageWrapper: ChatMessageWrapper, isInternetInProgress: Boolean, isFinished: Boolean, progress: Int = 0, var isPlaying: Boolean = false, var playProgress: Int = 0, var maxPlayProgress: Int = 0) : ReferenceHolder(chatMessageWrapper, isInternetInProgress, isFinished, progress) {

}