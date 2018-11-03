package de.intektor.mercury.ui.chat.adapter.chat

import android.view.View
import android.view.ViewGroup
import android.widget.*
import de.intektor.mercury.R
import de.intektor.mercury.android.getAttrDrawable
import de.intektor.mercury.chat.VoiceReferenceHolder
import de.intektor.mercury.io.download.IOService
import de.intektor.mercury.media.MediaHelper
import de.intektor.mercury.task.ReferenceState
import de.intektor.mercury.util.setGone
import de.intektor.mercury.util.setVisible
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageVoiceMessage
import de.intektor.mercury_common.reference.FileType

class VoiceMessageViewHolder(itemView: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder<MessageVoiceMessage, VoiceReferenceHolder>(itemView, chatAdapter) {

    private val playButton: ImageView = itemView.findViewById(R.id.chatMessageVoiceMessagePlayButton)
    private val uploadBar: ProgressBar = itemView.findViewById(R.id.chatMessageVoiceMessageProgressBar)
    private val timeDisplay: TextView = itemView.findViewById(R.id.chatMessageVoiceMessageText)
    private val watchBar: SeekBar = itemView.findViewById(R.id.chatMessageVoiceMessageWatchBar)

    override val parentLayout: LinearLayout
        get() = itemView.findViewById(R.id.bubble_layout_parent) as LinearLayout

    override val bubbleLayout: ViewGroup
        get() = itemView.findViewById(R.id.bubble_layout)

    override fun bindMessage(item: VoiceReferenceHolder, core: MessageCore, data: MessageVoiceMessage) {
        val context = itemView.context

        playButton.setVisible()

        uploadBar.max = 100
        watchBar.progress = item.playProgress

        watchBar.max = if (item.maxPlayProgress == 0) 100 else item.maxPlayProgress

        when (item.referenceState) {
            ReferenceState.FINISHED -> {
                uploadBar.setGone()

                playButton.setImageResource(R.drawable.baseline_play_arrow_24)
            }
            ReferenceState.IN_PROGRESS -> {
                playButton.setGone()
                uploadBar.setVisible()
                uploadBar.progress = item.progress
            }
            ReferenceState.NOT_STARTED -> {
                playButton.setImageDrawable(getAttrDrawable(context, if (isClient(item)) R.attr.ic_file_upload else R.attr.ic_file_download))
                uploadBar.setGone()
            }
        }

        timeDisplay.text = "${convertSeconds(item.playProgress / 1000)}-${convertSeconds(data.durationSeconds.toInt())}"

        registerForEditModePress(playButton) {
            if (item.referenceState != ReferenceState.FINISHED) {
                item.referenceState = ReferenceState.IN_PROGRESS

                if (isClient(item)) {
                    IOService.ActionUploadReference.launch(context, data.reference, data.aesKey, data.initVector, FileType.AUDIO)
                } else {
                    IOService.ActionDownloadReference.launch(context, data.reference, data.aesKey, data.initVector, MediaHelper.MEDIA_TYPE_AUDIO, chatAdapter.activity.chatInfo.chatUUID, core.messageUUID)
                }
            } else {
                if (item.isPlaying) {
                    chatAdapter.activity.stopAudio(item)
                } else {
                    chatAdapter.activity.playAudio(item, item.playProgress)
                }
            }
        }

        registerSeekBarListener(item)

        registerForEditModeLongPress(playButton)
        registerForEditModeLongPress(watchBar)
    }

    private fun registerSeekBarListener(item: VoiceReferenceHolder) {
        watchBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var userDragging = false

            override fun onProgressChanged(seekBar: SeekBar, p1: Int, fromUser: Boolean) {
                if (fromUser) {
                    chatAdapter.activity.seekAudioChange(item, p1)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                userDragging = true
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                userDragging = false
            }
        })
    }

    private fun convertSeconds(seconds: Int): String {
        var s = seconds
        var m = 0
        while (s >= 60) {
            s -= 60
            m++
        }
        return String.format("%02d:%02d", m, s)
    }

    override fun getMessage(item: VoiceReferenceHolder): ChatMessage = item.chatMessageInfo.chatMessageInfo.message

    override fun isClient(item: VoiceReferenceHolder): Boolean = item.chatMessageInfo.chatMessageInfo.client
}