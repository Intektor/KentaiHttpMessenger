package de.intektor.mercury.ui.chat.adapter.chat

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import de.intektor.mercury.R
import de.intektor.mercury.chat.adapter.VoiceReferenceHolder
import de.intektor.mercury.media.MediaType
import de.intektor.mercury.task.ReferenceState
import de.intektor.mercury.util.setGone
import de.intektor.mercury.util.setVisible
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageVoiceMessage

class VoiceMessageViewHolder(itemView: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder<MessageVoiceMessage, VoiceReferenceHolder>(itemView, chatAdapter) {

    private val downloadParentCv: CardView = itemView.findViewById(R.id.item_chat_message_voice_cv_download_parent)
    private val downloadParentCl: ConstraintLayout = itemView.findViewById(R.id.item_chat_message_voice_cl_download_parent)
    private val downloadLabel: TextView = itemView.findViewById(R.id.item_chat_message_voice_tv_download_label)
    private val loadParentCv: CardView = itemView.findViewById(R.id.item_chat_message_voice_cv_load_parent)
    private val loadParentCl: ConstraintLayout = itemView.findViewById(R.id.item_chat_message_voice_cl_load_parent)
    private val playParentCv: CardView = itemView.findViewById(R.id.item_chat_message_voice_cv_play_parent)
    private val playParentCl: ConstraintLayout = itemView.findViewById(R.id.item_chat_message_voice_cl_play_parent)
    private val controlButton: ImageView = itemView.findViewById(R.id.item_chat_message_voice_iv_control_button)
    private val seekBar: SeekBar = itemView.findViewById(R.id.item_chat_message_voice_sb)
    private val time: TextView = itemView.findViewById(R.id.item_chat_message_voice_tv_time)

    override val parentLayout: LinearLayout
        get() = itemView.findViewById(R.id.bubble_layout_parent) as LinearLayout

    override val bubbleLayout: ViewGroup
        get() = itemView.findViewById(R.id.bubble_layout)

    override fun bindMessage(item: VoiceReferenceHolder, core: MessageCore, data: MessageVoiceMessage) {
        val context = itemView.context

        val isClientMessage = item.message.chatMessageInfo.client

        when (item.referenceState) {
            ReferenceState.FINISHED -> {
                downloadParentCv.setGone()
                loadParentCv.setGone()
                playParentCv.setVisible()

                controlButton.setImageResource(if (item.isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24)
            }
            ReferenceState.IN_PROGRESS -> {
                downloadParentCv.setGone()
                loadParentCv.setVisible()
                playParentCv.visibility = if (isClientMessage) View.VISIBLE else View.GONE

                controlButton.setImageResource(if (item.isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24)
            }
            ReferenceState.NOT_STARTED -> {
                downloadParentCv.setVisible()
                loadParentCv.setGone()
                playParentCv.visibility = if (isClientMessage) View.VISIBLE else View.GONE

                downloadLabel.setText(if (isClientMessage) R.string.media_upload else R.string.media_download)
                controlButton.setImageResource(if (item.isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24)
            }
        }

        updateTimeAndSeekBar(context, item, data)

        registerForEditModePress(downloadParentCl) {
            chatAdapter.activity.startReferenceLoad(item, adapterPosition, MediaType.MEDIA_TYPE_VIDEO)
        }

        registerForEditModePress(playParentCl) {
            if (item.isPlaying) {
                chatAdapter.activity.stopAudio(item)
            } else {
                chatAdapter.activity.playAudio(item, item.playProgress)
            }
        }

        registerSeekBarListener(item)
    }

    private fun updateTimeAndSeekBar(context: Context, item: VoiceReferenceHolder, data: MessageVoiceMessage) {
        val (minutes, seconds) = if (item.isPlaying) {
            getTime(item.playProgress / 1000)
        } else {
            getTime(data.durationSeconds)
        }

        time.text = context.getString(R.string.voice_message_duration, minutes, seconds)

        seekBar.max = item.maxPlayProgress
        seekBar.progress = item.playProgress
    }

    private fun registerSeekBarListener(item: VoiceReferenceHolder) {
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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

    private fun getTime(seconds: Int): Pair<Int, Int> {
        var minutes = 0

        var current = seconds

        while (current >= 60) {
            minutes += 1
            current -= 60
        }

        return minutes to current
    }

    override fun getMessage(item: VoiceReferenceHolder): ChatMessage = item.message.chatMessageInfo.message

    override fun isClient(item: VoiceReferenceHolder): Boolean = item.message.chatMessageInfo.client
}