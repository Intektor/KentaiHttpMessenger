package de.intektor.kentai.kentai.chat.adapter

import android.content.Intent
import android.media.ThumbnailUtils
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.references.downloadVideo
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai.kentai.references.uploadVideo
import de.intektor.kentai_http_common.chat.ChatMessage
import de.intektor.kentai_http_common.chat.ChatMessageVideo
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.toUUID
import java.util.*

class VideoMessageViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(view, chatAdapter) {

    private val imageView = itemView.findViewById<ImageView>(R.id.chatMessageVideoView)
    private val loadBar = itemView.findViewById<ProgressBar>(R.id.chatMessageVideoViewLoadBar)
    private val loadButton = itemView.findViewById<ImageButton>(R.id.chatMessageVideoViewLoadButton)

    private lateinit var message: ChatMessage

    private var isUploaded = false
    private var isDownloaded = false

    override fun setComponent(component: Any) {
        component as ChatMessageWrapper
        message = component.message as ChatMessageVideo

        val message = message as ChatMessageVideo

        val referenceFile = getReferenceFile(chatAdapter.chatInfo.chatUUID, message.referenceUUID, FileType.VIDEO, chatAdapter.activity.filesDir, chatAdapter.activity)

        isDownloaded = isReferenceDownloaded(message.referenceUUID, FileType.VIDEO, chatAdapter.chatInfo, chatAdapter.activity)
        isUploaded = isReferenceUploaded(KentaiClient.INSTANCE.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID)

        loadBar.max = 100

        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        imageView.setOnClickListener {
            val showVideo = Intent(Intent.ACTION_VIEW)
            showVideo.setDataAndType(FileProvider.getUriForFile(chatAdapter.activity, chatAdapter.activity.applicationContext.packageName + ".kentai.android.GenericFileProvider", referenceFile), "video/*")
            showVideo.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            chatAdapter.activity.startActivity(showVideo)
        }

        if (!isDownloaded || !isUploaded) {
            loadButton.visibility = View.VISIBLE
            loadButton.setImageResource(android.R.drawable.ic_menu_upload)

            loadButton.setOnClickListener {
                loadButton.visibility = View.GONE
                loadBar.visibility = View.VISIBLE
                loadBar.progress = 0
                imageView.visibility = View.GONE
                if (component.client) {
                    uploadVideo(chatAdapter.activity, KentaiClient.INSTANCE.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID,
                            referenceFile)
                } else {
                    downloadVideo(chatAdapter.activity, KentaiClient.INSTANCE.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID, chatAdapter.chatInfo.chatType, message.hash)
                }
            }
        }

        if (isDownloaded) {
            val b = ThumbnailUtils.createVideoThumbnail(referenceFile.path, MediaStore.Images.Thumbnails.MINI_KIND)
            imageView.setImageBitmap(b)
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_upload)
        }

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
    }

    override fun broadcast(target: String, intent: Intent) {
        when (target) {
            "de.intektor.kentai.uploadReferenceStarted" -> {
                val referenceUUID = intent.getSerializableExtra("referenceUUID") as UUID
                if (referenceUUID == message.referenceUUID) {
                    loadButton.visibility = View.GONE
                    imageView.visibility = View.GONE
                    loadBar.visibility = View.VISIBLE
                    loadBar.progress = 0
                }
            }

            "de.intektor.kentai.uploadReferenceFinished" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val successful = intent.getBooleanExtra("successful", false)
                if (referenceUUID == message.referenceUUID) {
                    if (successful) {
                        imageView.visibility = View.VISIBLE
                        loadBar.visibility = View.GONE
                        isUploaded = true
                        isDownloaded = true

                        val b = ThumbnailUtils.createVideoThumbnail(getReferenceFile(chatAdapter.chatInfo.chatUUID, referenceUUID, FileType.AUDIO, chatAdapter.activity.filesDir, chatAdapter.activity).path, MediaStore.Images.Thumbnails.MINI_KIND)
                        imageView.setImageBitmap(b)
                    } else {
                        loadBar.visibility = View.GONE
                        loadButton.visibility = View.VISIBLE
                        imageView.visibility = View.VISIBLE
                    }
                }
            }

            "de.intektor.kentai.uploadProgress" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val progress = intent.getDoubleExtra("progress", 0.0)
                if (referenceUUID == message.referenceUUID) {
                    loadBar.progress = (progress * 100).toInt()
                }
            }

            "de.intektor.kentai.downloadReferenceFinished" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val successful = intent.getBooleanExtra("successful", false)
                if (referenceUUID == message.referenceUUID) {
                    if (successful) {
                        isUploaded = true
                        isDownloaded = true
                        loadButton.visibility = View.GONE
                        loadBar.visibility = View.GONE
                        imageView.visibility = View.VISIBLE

                        val b = ThumbnailUtils.createVideoThumbnail(getReferenceFile(chatAdapter.chatInfo.chatUUID, referenceUUID, FileType.AUDIO, chatAdapter.activity.filesDir, chatAdapter.activity).path, MediaStore.Images.Thumbnails.MINI_KIND)
                        imageView.setImageBitmap(b)
                    } else {
                        loadBar.visibility = View.GONE
                        loadButton.visibility = View.VISIBLE
                        imageView.visibility = View.VISIBLE
                    }
                }
            }

            "de.intektor.kentai.downloadProgress" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val progress = intent.getDoubleExtra("progress", 0.0)
                if (referenceUUID == message.referenceUUID) {
                    loadBar.progress = (progress * 100).toInt()
                }
            }
        }
    }


}