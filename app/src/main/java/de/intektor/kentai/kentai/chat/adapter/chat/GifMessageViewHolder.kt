package de.intektor.kentai.kentai.chat.adapter.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Movie
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.*
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ReferenceHolder
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai_http_common.chat.ChatMessageVideo
import de.intektor.kentai_http_common.reference.FileType

class GifMessageViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(view, chatAdapter) {

    private val text: TextView = view.findViewById(R.id.messageGifText)
    private val videoView: VideoView = view.findViewById(R.id.messageGifVideo)
    private val thumbnail: ImageView = view.findViewById(R.id.messageGifThumbnail)
    private val playButton: TextView = view.findViewById(R.id.messageGifPlay)
    private val loadButton: ImageView = view.findViewById(R.id.messageGifStartLoad)
    private val loadBar: ProgressBar = view.findViewById(R.id.messageGifLoadBar)
    private val webView: WebView = view.findViewById(R.id.messageGifWebView)

    override fun setComponent(component: Any) {
        component as ReferenceHolder

        val kentaiClient = text.context.applicationContext as KentaiClient

        val message = component.chatMessageWrapper.message as ChatMessageVideo
        val messageText = message.text
        text.visibility = if (messageText.isEmpty()) View.GONE else View.VISIBLE
        text.text = messageText

        videoView.visibility = View.GONE

        if (videoView.isPlaying) {
            videoView.pause()
        }

        val fileType = if (message.isGif.toBoolean()) FileType.GIF else FileType.VIDEO

        val referenceFile = getReferenceFile(message.referenceUUID, fileType, kentaiClient.filesDir, kentaiClient)

        videoView.visibility = View.GONE
        webView.visibility = View.GONE

        if (component.isFinished) {
            loadBar.visibility = View.GONE
            loadButton.visibility = View.GONE
            playButton.visibility = View.VISIBLE
            thumbnail.visibility = View.VISIBLE

            val isWebView = try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(kentaiClient, Uri.fromFile(referenceFile))
                false
            } catch (t: Throwable) {
                true
            }

            if (isWebView) {
                val movie = Movie.decodeFile(referenceFile.path)
                val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.RGB_565)
                val canvas = Canvas(bitmap)
                movie.draw(canvas, 0f, 0f)
                thumbnail.setImageBitmap(bitmap)
            } else {
                thumbnail.setImageBitmap(ThumbnailUtils.createVideoThumbnail(referenceFile.path, MediaStore.Images.Thumbnails.MINI_KIND))
            }

            playButton.setOnClickListener {
                thumbnail.visibility = View.GONE
                playButton.visibility = View.GONE

                if (!isWebView) {
                    videoView.visibility = View.VISIBLE
                    videoView.setVideoURI(Uri.fromFile(referenceFile))
                    videoView.start()
                } else {
                    webView.visibility = View.VISIBLE
                    webView.settings.loadWithOverviewMode = true
                    webView.settings.useWideViewPort = true
                    webView.loadUrl(Uri.fromFile(referenceFile).toString())
                }
            }

            videoView.setOnCompletionListener {
                videoView.seekTo(0)
                videoView.start()
            }

            videoView.setOnClickListener {
                videoView.pause()
                videoView.seekTo(0)
                videoView.visibility = View.GONE

                thumbnail.visibility = View.VISIBLE
                playButton.visibility = View.VISIBLE
            }

            webView.setOnClickListener {
                webView.visibility = View.GONE
                thumbnail.visibility = View.VISIBLE
                playButton.visibility = View.VISIBLE
            }

        } else {
            if (component.isInternetInProgress) {
                loadBar.visibility = View.VISIBLE
                loadBar.progress = component.progress
                playButton.visibility = View.GONE
            } else {
                thumbnail.visibility = View.VISIBLE
                if (component.chatMessageWrapper.client) {
                    thumbnail.setImageBitmap(ThumbnailUtils.createVideoThumbnail(referenceFile.path, MediaStore.Images.Thumbnails.MINI_KIND))
                } else thumbnail.setImageResource(R.mipmap.ic_launcher)

                playButton.visibility = View.VISIBLE
                loadBar.visibility = View.GONE

                playButton.visibility = View.GONE

                loadButton.setImageResource(if (component.chatMessageWrapper.client) R.drawable.ic_file_upload_white_24dp else R.drawable.ic_file_download_white_24dp)
                loadButton.setOnClickListener {
                    chatAdapter.activity.startReferenceLoad(component, adapterPosition, fileType)
                }
            }
        }

        val layout = itemView.findViewById(R.id.bubble_layout) as LinearLayout
        val parentLayout = itemView.findViewById(R.id.bubble_layout_parent) as LinearLayout
        // if message is mine then align to right
        if (component.chatMessageWrapper.client) {
            layout.setBackgroundResource(R.drawable.bubble_right)
            parentLayout.gravity = Gravity.END
        } else {
            layout.setBackgroundResource(R.drawable.bubble_left)
            parentLayout.gravity = Gravity.START
        }
    }

}