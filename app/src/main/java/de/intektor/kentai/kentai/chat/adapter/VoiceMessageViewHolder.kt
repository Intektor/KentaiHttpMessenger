package de.intektor.kentai.kentai.chat.adapter

import android.content.Intent
import android.media.MediaPlayer
import android.os.Handler
import android.view.Gravity
import android.view.View
import android.widget.*
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.references.downloadAudio
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai.kentai.references.uploadAudio
import de.intektor.kentai_http_common.chat.ChatMessageVoiceMessage
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.toUUID
import java.util.*

class VoiceMessageViewHolder(itemView: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(itemView, chatAdapter) {

    private val playButton: ImageView = itemView.findViewById(R.id.chatMessageVoiceMessagePlayButton)
    private val uploadBar: ProgressBar = itemView.findViewById(R.id.chatMessageVoiceMessageProgressBar)
    private val timeDisplay: TextView = itemView.findViewById(R.id.chatMessageVoiceMessageText)
    private val watchBar: SeekBar = itemView.findViewById(R.id.chatMessageVoiceMessageWatchBar)

    var isPlaying = false
    var mediaPlayer: MediaPlayer = MediaPlayer()
    var isInitialized = false
    var hasFinished = false

    var isDownloaded = false
    var isUploaded = false

    lateinit var message: ChatMessageVoiceMessage

    override fun setComponent(component: Any) {
        component as ChatMessageWrapper

        val layout = itemView.findViewById<LinearLayout>(R.id.bubble_layout) as LinearLayout
        val parentLayout = itemView.findViewById<LinearLayout>(R.id.bubble_layout_parent) as LinearLayout
        // if message is mine then align to right
        if (component.client) {
            layout.setBackgroundResource(R.drawable.bubble_right)
            parentLayout.gravity = Gravity.END
        } else {
            layout.setBackgroundResource(R.drawable.bubble_left)
            parentLayout.gravity = Gravity.START
        }

        message = component.message as ChatMessageVoiceMessage

        isDownloaded = isReferenceDownloaded(message.referenceUUID, FileType.AUDIO, chatAdapter.chatInfo, chatAdapter.activity)

        isUploaded = isReferenceUploaded(KentaiClient.INSTANCE.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID)

        uploadBar.max = 100
        watchBar.progress = 0

        watchBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            private var userDragging = false

            override fun onProgressChanged(seekBar: SeekBar, p1: Int, p2: Boolean) {
                if (!hasFinished && isInitialized && userDragging) {
                    mediaPlayer.seekTo(p1)
                }
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {
                userDragging = true
            }

            override fun onStopTrackingTouch(p0: SeekBar?) {
                userDragging = false
            }
        })

        if (isUploaded) {
            playButton.setImageResource(if (isDownloaded) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_upload)
            playButton.rotation = if (isDownloaded) 0f else 180f

        } else {
            playButton.setImageResource(android.R.drawable.ic_menu_upload)
            playButton.rotation = 0f
        }
        timeDisplay.text = "${convertSeconds(0)}-${convertSeconds(message.durationSeconds.toInt())}"

        playButton.setOnClickListener {
            if (!isDownloaded || !isUploaded) {
                playButton.visibility = View.GONE
                uploadBar.visibility = View.VISIBLE
                uploadBar.progress = 0

                if (!isDownloaded) {
                    downloadAudio(chatAdapter.activity, KentaiClient.INSTANCE.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID, chatAdapter.chatInfo.chatType, message.fileHash)
                } else if (!isUploaded) {
                    uploadAudio(chatAdapter.activity, KentaiClient.INSTANCE.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID,
                            getReferenceFile(chatAdapter.chatInfo.chatUUID, message.referenceUUID, FileType.AUDIO, chatAdapter.activity.filesDir, chatAdapter.activity))
                }
            } else {
                if (!isInitialized) {
                    mediaPlayer = MediaPlayer()
                    mediaPlayer.setDataSource(getReferenceFile(chatAdapter.chatInfo.chatUUID, message.referenceUUID, FileType.AUDIO, itemView.context.filesDir, chatAdapter.activity).absolutePath)
                    mediaPlayer.prepare()
                    mediaPlayer.setOnCompletionListener {
                        isPlaying = false
                        watchBar.progress = 1
                        playButton.setImageResource(android.R.drawable.ic_media_play)
                        mediaPlayer.release()
                        isInitialized = false
                        timeDisplay.text = "${convertSeconds(0)}-${convertSeconds(message.durationSeconds.toInt())}"
                    }
                    watchBar.max = mediaPlayer.duration

                    val handler = Handler()
                    handler.post(object : Runnable {
                        override fun run() {
                            if (isPlaying) {
                                watchBar.progress = mediaPlayer.currentPosition
                                val av = watchBar.progress.toDouble() / watchBar.max.toDouble()

                                timeDisplay.text = "${convertSeconds(Math.floor(message.durationSeconds.toDouble() * av).toInt())}-${convertSeconds(message.durationSeconds.toInt())}"
                            }
                            if (hasFinished) {
                                watchBar.progress = watchBar.max
                                hasFinished = false
                            }
                            handler.postDelayed(this, 500)
                        }
                    })

                    isInitialized = true
                }

                if (!isPlaying) {
                    mediaPlayer.start()
                    isPlaying = true

                    playButton.setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    mediaPlayer.pause()
                    isPlaying = false

                    playButton.setImageResource(android.R.drawable.ic_media_play)
                }
            }
        }
    }

    override fun broadcast(target: String, intent: Intent) {
        when (target) {
            "de.intektor.kentai.uploadReferenceStarted" -> {
                val referenceUUID = intent.getSerializableExtra("referenceUUID") as UUID
                if (referenceUUID == message.referenceUUID) {
                    playButton.visibility = View.GONE
                    uploadBar.visibility = View.VISIBLE
                    uploadBar.progress = 0
                }
            }

            "de.intektor.kentai.uploadReferenceFinished" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val successful = intent.getBooleanExtra("successful", false)
                if (referenceUUID == message.referenceUUID) {
                    playButton.visibility = View.VISIBLE
                    uploadBar.visibility = View.GONE

                    if (successful) {
                        playButton.setImageResource(android.R.drawable.ic_media_play)
                        isUploaded = true
                        isDownloaded = true
                    } else {
                        playButton.setImageResource(android.R.drawable.ic_menu_upload)
                        playButton.rotation = 180f
                    }
                }
            }

            "de.intektor.kentai.uploadProgress" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val progress = intent.getDoubleExtra("progress", 0.0)
                if (referenceUUID == message.referenceUUID) {
                    uploadBar.progress = (progress * 100).toInt()
                }
            }

            "de.intektor.kentai.downloadReferenceFinished" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val successful = intent.getBooleanExtra("successful", false)
                if (referenceUUID == message.referenceUUID) {
                    playButton.visibility = View.VISIBLE
                    uploadBar.visibility = View.GONE

                    if (successful) {
                        playButton.setImageResource(android.R.drawable.ic_media_play)
                        isUploaded = true
                        isDownloaded = true
                    } else {
                        playButton.setImageResource(android.R.drawable.ic_menu_upload)
                        playButton.rotation = 180f
                    }
                }
            }

            "de.intektor.kentai.downloadProgress" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val progress = intent.getDoubleExtra("progress", 0.0)
                if (referenceUUID == message.referenceUUID) {
                    uploadBar.progress = (progress * 100).toInt()
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