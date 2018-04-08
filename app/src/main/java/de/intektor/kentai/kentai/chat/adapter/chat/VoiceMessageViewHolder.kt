package de.intektor.kentai.kentai.chat.adapter.chat

import android.view.Gravity
import android.view.View
import android.widget.*
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.VoiceReferenceHolder
import de.intektor.kentai.kentai.references.downloadAudio
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai.kentai.references.uploadAudio
import de.intektor.kentai_http_common.chat.ChatMessageVoiceMessage
import de.intektor.kentai_http_common.reference.FileType

class VoiceMessageViewHolder(itemView: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(itemView, chatAdapter) {

    private val playButton: ImageView = itemView.findViewById(R.id.chatMessageVoiceMessagePlayButton)
    private val uploadBar: ProgressBar = itemView.findViewById(R.id.chatMessageVoiceMessageProgressBar)
    private val timeDisplay: TextView = itemView.findViewById(R.id.chatMessageVoiceMessageText)
    private val watchBar: SeekBar = itemView.findViewById(R.id.chatMessageVoiceMessageWatchBar)

    override fun setComponent(component: Any) {
        component as VoiceReferenceHolder
        val wrapper = component.chatMessageWrapper
        val message = wrapper.message as ChatMessageVoiceMessage

        val kentaiClient = itemView.context.applicationContext as KentaiClient

        val layout = itemView.findViewById(R.id.bubble_layout) as LinearLayout
        val parentLayout = itemView.findViewById(R.id.bubble_layout_parent) as LinearLayout
        // if message is mine then align to right
        if (wrapper.client) {
            layout.setBackgroundResource(R.drawable.bubble_right)
            parentLayout.gravity = Gravity.END
        } else {
            layout.setBackgroundResource(R.drawable.bubble_left)
            parentLayout.gravity = Gravity.START
        }

        playButton.visibility = View.VISIBLE

        uploadBar.max = 100
        watchBar.progress = component.playProgress
        if (component.maxPlayProgress == 0) {
            watchBar.max = 100
        } else {
            watchBar.max = component.maxPlayProgress
        }

        watchBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var userDragging = false

            override fun onProgressChanged(seekBar: SeekBar, p1: Int, fromUser: Boolean) {
                if (fromUser) {
                    chatAdapter.activity.seekAudioChange(component, p1)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                userDragging = true
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                userDragging = false
            }
        })

        if (component.isFinished) {
            playButton.setImageResource(android.R.drawable.ic_media_play)
            uploadBar.visibility = View.GONE
        } else {
            if (component.isInternetInProgress) {
                playButton.visibility = View.GONE
                uploadBar.visibility = View.VISIBLE
                uploadBar.progress = component.progress
            } else {
                playButton.setImageResource(if (component.chatMessageWrapper.client) R.drawable.file_upload else R.drawable.file_download)
                uploadBar.visibility = View.GONE
            }
        }

        if (component.isFinished) {
            if (component.isPlaying) {
                playButton.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                playButton.setImageResource(android.R.drawable.ic_media_play)
            }
        }

        timeDisplay.text = "${convertSeconds(0)}-${convertSeconds(message.durationSeconds.toInt())}"

        playButton.setOnClickListener {
            if (!component.isInternetInProgress && !component.isFinished) {
                component.isInternetInProgress = true

                if (!wrapper.client) {
                    downloadAudio(chatAdapter.activity, kentaiClient.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID, chatAdapter.chatInfo.chatType, message.fileHash, kentaiClient.privateMessageKey!!)
                } else {
                    uploadAudio(chatAdapter.activity, kentaiClient.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID,
                            getReferenceFile(message.referenceUUID, FileType.AUDIO, chatAdapter.activity.filesDir, chatAdapter.activity))
                }
            } else {
                if (component.isPlaying) {
                    chatAdapter.activity.stopAudio(component)
                } else {
                    chatAdapter.activity.playAudio(component, component.playProgress)
                }
            }
        }
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
}