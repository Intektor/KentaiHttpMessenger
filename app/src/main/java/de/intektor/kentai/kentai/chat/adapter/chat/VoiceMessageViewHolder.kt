package de.intektor.kentai.kentai.chat.adapter.chat

import android.support.constraint.ConstraintLayout
import android.view.Gravity
import android.view.View
import android.widget.*
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.VoiceReferenceHolder
import de.intektor.kentai.kentai.getAttrDrawable
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

    override fun bind(component: ChatAdapter.ChatAdapterWrapper) {
        super.bind(component)

        val item = component.item as VoiceReferenceHolder
        val wrapper = item.chatMessageWrapper
        val message = wrapper.message as ChatMessageVoiceMessage

        val context = itemView.context
        val kentaiClient = context.applicationContext as KentaiClient

        val layout = itemView.findViewById(R.id.bubble_layout) as ConstraintLayout
        val parentLayout = itemView.findViewById(R.id.bubble_layout_parent) as LinearLayout

        if (item.chatMessageWrapper.client) {
            layout.background = getAttrDrawable(itemView.context, R.attr.bubble_right)
            parentLayout.gravity = Gravity.END
        } else {
            layout.background = getAttrDrawable(itemView.context, R.attr.bubble_left)
            parentLayout.gravity = Gravity.START
        }

        playButton.visibility = View.VISIBLE

        uploadBar.max = 100
        watchBar.progress = item.playProgress
        if (item.maxPlayProgress == 0) {
            watchBar.max = 100
        } else {
            watchBar.max = item.maxPlayProgress
        }

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

        if (item.isFinished) {
            playButton.setImageDrawable(getAttrDrawable(context, R.attr.ic_play_arrow))
            uploadBar.visibility = View.GONE
        } else {
            if (item.isInternetInProgress) {
                playButton.visibility = View.GONE
                uploadBar.visibility = View.VISIBLE
                uploadBar.progress = item.progress
            } else {
                playButton.setImageDrawable(getAttrDrawable(context, if (item.chatMessageWrapper.client) R.attr.ic_file_upload else R.attr.ic_file_download))
                uploadBar.visibility = View.GONE
            }
        }

        if (item.isFinished) {
            playButton.setImageDrawable(getAttrDrawable(context, if (item.isPlaying) R.attr.ic_pause else R.attr.ic_play_arrow))
        }

        timeDisplay.text = "${convertSeconds(item.playProgress / 1000)}-${convertSeconds(message.durationSeconds.toInt())}"

        registerForEditModePress(playButton) {
            if (!item.isInternetInProgress && !item.isFinished) {
                item.isInternetInProgress = true

                if (!wrapper.client) {
                    downloadAudio(chatAdapter.activity, kentaiClient.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID, chatAdapter.chatInfo.chatType, message.fileHash, kentaiClient.privateMessageKey!!)
                } else {
                    uploadAudio(chatAdapter.activity, kentaiClient.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID,
                            getReferenceFile(message.referenceUUID, FileType.AUDIO, chatAdapter.activity.filesDir, chatAdapter.activity))
                }
            } else {
                if (item.isPlaying) {
                    chatAdapter.activity.stopAudio(item)
                } else {
                    chatAdapter.activity.playAudio(item, item.playProgress)
                }
            }
        }

        registerForEditModeLongPress(playButton)
        registerForEditModeLongPress(watchBar)
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