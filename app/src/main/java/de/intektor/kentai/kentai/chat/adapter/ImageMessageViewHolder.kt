package de.intektor.kentai.kentai.chat.adapter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.FileProvider
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import com.squareup.picasso.Picasso
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.references.downloadImage
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai.kentai.references.uploadImage
import de.intektor.kentai_http_common.chat.ChatMessage
import de.intektor.kentai_http_common.chat.ChatMessageImage
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.toUUID
import java.util.*

class ImageMessageViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(view, chatAdapter) {

    private lateinit var imageView: ImageView
    private lateinit var loadBar: ProgressBar
    private lateinit var loadButton: ImageButton

    private lateinit var message: ChatMessage

    private var isUploaded = false
    private var isDownloaded = false

    private var showImage = true
    private var showProgressbar = false
    private var showUpDownloadButton = false
    private var progress = 0
    private var imageFileUri: Uri? = null

    override fun setComponent(component: Any) {
        component as ChatMessageWrapper
        imageView = itemView.findViewById(R.id.chatMessageImageView)
        loadBar = itemView.findViewById(R.id.chatMessageImageViewLoadBar)
        loadButton = itemView.findViewById(R.id.chatMessageImageViewLoadButton)

        message = component.message as ChatMessageImage

        val message = message as ChatMessageImage

        val referenceFile = getReferenceFile(chatAdapter.chatInfo.chatUUID, message.referenceUUID, FileType.IMAGE, chatAdapter.activity.filesDir, chatAdapter.activity)

        isDownloaded = isReferenceDownloaded(message.referenceUUID, FileType.IMAGE, chatAdapter.chatInfo, chatAdapter.activity)
        isUploaded = isReferenceUploaded(KentaiClient.INSTANCE.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID)

        loadBar.max = 100

        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        imageView.setOnClickListener {
            val showImage = Intent(Intent.ACTION_VIEW)
            showImage.setDataAndType(FileProvider.getUriForFile(chatAdapter.activity, chatAdapter.activity.applicationContext.packageName + ".kentai.android.GenericFileProvider", referenceFile), "image/*")
            showImage.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            chatAdapter.activity.startActivity(showImage)
        }

        if (!isDownloaded || !isUploaded) {
            showUpDownloadButton = true
            loadButton.setImageResource(android.R.drawable.ic_menu_upload)

            loadButton.setOnClickListener {
                val payload = Bundle()
                payload.putInt(ChatAdapter.DATA_PAYLOAD_TYPE, if (component.client) ChatAdapter.UPLOAD_REFERENCE_STARTED else ChatAdapter.DOWNLOAD_REFERENCE_STARTED)
                chatAdapter.notifyItemChanged(adapterPosition, payload)
                if (component.client) {
                    uploadImage(chatAdapter.activity, KentaiClient.INSTANCE.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID,
                            referenceFile)
                } else {
                    downloadImage(chatAdapter.activity, KentaiClient.INSTANCE.dataBase, chatAdapter.chatInfo.chatUUID, message.referenceUUID, chatAdapter.chatInfo.chatType, message.hash)
                }
            }
        }

        imageFileUri = if (isDownloaded) Uri.fromFile(referenceFile) else null

        val layout = itemView.findViewById<LinearLayout>(R.id.bubble_layout) as LinearLayout
        val parentLayout = itemView.findViewById<LinearLayout>(R.id.bubble_layout_parent) as LinearLayout

        if (component.client) {
            layout.setBackgroundResource(R.drawable.bubble_right)
            parentLayout.gravity = Gravity.END
        } else {
            layout.setBackgroundResource(R.drawable.bubble_left)
            parentLayout.gravity = Gravity.START
        }

        applyPayload()
    }

    override fun broadcast(target: String, intent: Intent) {
        when (target) {
            "de.intektor.kentai.uploadReferenceStarted" -> {
                val referenceUUID = intent.getSerializableExtra("referenceUUID") as UUID
                if (referenceUUID == message.referenceUUID) {
                    val payload = Bundle()
                    payload.putInt(ChatAdapter.DATA_PAYLOAD_TYPE, ChatAdapter.UPLOAD_REFERENCE_STARTED)
                    chatAdapter.notifyItemChanged(adapterPosition, payload)
                }
            }

            "de.intektor.kentai.uploadReferenceFinished" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val successful = intent.getBooleanExtra("successful", false)
                if (referenceUUID == message.referenceUUID) {
                    val payload = Bundle()
                    payload.putInt(ChatAdapter.DATA_PAYLOAD_TYPE, ChatAdapter.UPLOAD_REFERENCE_FINISHED)
                    payload.putBoolean(ChatAdapter.DATA_SUCCESSFUL, successful)
                    chatAdapter.notifyItemChanged(adapterPosition, payload)
                }
            }

            "de.intektor.kentai.uploadProgress" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val progress = intent.getDoubleExtra("progress", 0.0)
                if (referenceUUID == message.referenceUUID) {
//                        loadBar.progress = (progress * 100).toInt()
                }
            }

            "de.intektor.kentai.downloadReferenceFinished" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val successful = intent.getBooleanExtra("successful", false)
                if (referenceUUID == message.referenceUUID) {
                    val payload = Bundle()
                    payload.putInt(ChatAdapter.DATA_PAYLOAD_TYPE, ChatAdapter.DOWNLOAD_REFERENCE_FINISHED)
                    payload.putBoolean(ChatAdapter.DATA_SUCCESSFUL, successful)
                    chatAdapter.notifyItemChanged(adapterPosition, payload)
                }
            }

            "de.intektor.kentai.downloadProgress" -> {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val progress = intent.getDoubleExtra("progress", 0.0)
                if (referenceUUID == message.referenceUUID) {
//                        loadBar.progress = (progress * 100).toInt()
                }
            }
        }
    }

    override fun bindPayload(payloads: MutableList<Any>?) {
        super.bindPayload(payloads)
        if (payloads == null || payloads.isEmpty()) return
        val payload = payloads.first() as Bundle

        when (payload.getInt(ChatAdapter.DATA_PAYLOAD_TYPE)) {
            ChatAdapter.UPLOAD_REFERENCE_STARTED -> {
                Log.e("ERROR", "APPLYING PAYLOAD ${message.referenceUUID}")
                showUpDownloadButton = false
                showImage = false
                showProgressbar = true
                progress = 0
            }

            ChatAdapter.UPLOAD_REFERENCE_FINISHED -> {
                if (payload.getBoolean(ChatAdapter.DATA_SUCCESSFUL)) {
                    isUploaded = true
                    isDownloaded = true

                    showUpDownloadButton = false
                    showProgressbar = false
                    showImage = true
                    imageFileUri = Uri.fromFile(getReferenceFile(chatAdapter.chatInfo.chatUUID, message.referenceUUID, FileType.IMAGE, chatAdapter.activity.filesDir, chatAdapter.activity))
                } else {
                    showProgressbar = false
                    showUpDownloadButton = true
                    showImage = true
                }
            }

//                DOWNLOAD_REFERENCE_STARTED -> {
//                    showUpDownloadButton = false
//                    showImage = false
//                    showProgressbar = true
//                    progress = 0
//                }
//
//                DOWNLOAD_REFERENCE_FINISHED -> {
//                    if (payload.getBoolean(DATA_SUCCESSFUL)) {
//                        isUploaded = true
//                        isDownloaded = true
//                        loadButton.visibility = View.GONE
//                        loadBar.visibility = View.GONE
//                        imageView.visibility = View.VISIBLE
//
//                        showProgressbar = false
//                        showUpDownloadButton = false
//                        showImage = true
//                        imageFileUri = Uri.fromFile(getReferenceFile(chatInfo.chatUUID, message.referenceUUID, FileType.IMAGE, activity.filesDir))
//                    } else {
//                        showProgressbar = false
//                        showUpDownloadButton = true
//                        showImage = true
//                    }
//                }
        }
    }

    private fun applyPayload() {
        imageView.visibility = if (showImage) View.VISIBLE else View.GONE
        loadButton.visibility = if (showUpDownloadButton) View.VISIBLE else View.GONE
        loadBar.visibility = if (showProgressbar) View.VISIBLE else View.GONE
        loadBar.progress = progress
        Log.e("ERROR", "$showProgressbar ${message.referenceUUID}")
        if (imageFileUri != null) {
            Picasso.with(chatAdapter.activity).load(imageFileUri).fit().centerInside().into(imageView)
        } else {
            imageView.setImageResource(R.mipmap.ic_launcher)
        }
    }
}