package de.intektor.mercury.ui.chat.adapter.chat

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.*
import de.intektor.mercury.R
import de.intektor.mercury.chat.adapter.ReferenceHolder
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageVideo

class GifMessageViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder<MessageVideo, ReferenceHolder>(view, chatAdapter) {

    private val text: TextView = view.findViewById(R.id.messageGifText)
    private val videoView: VideoView = view.findViewById(R.id.messageGifVideo)
    private val thumbnail: ImageView = view.findViewById(R.id.messageGifThumbnail)
    private val playButton: TextView = view.findViewById(R.id.messageGifPlay)
    private val loadButton: ImageView = view.findViewById(R.id.messageGifStartLoad)
    private val loadBar: ProgressBar = view.findViewById(R.id.messageGifLoadBar)
    private val webView: WebView = view.findViewById(R.id.messageGifWebView)

    override val parentLayout: LinearLayout
        get() = itemView.findViewById(R.id.bubble_layout_parent)

    override val bubbleLayout: ViewGroup
        get() = itemView.findViewById(R.id.bubble_layout)

    override fun bindMessage(item: ReferenceHolder, core: MessageCore, data: MessageVideo) {
        //TODO:
    }

//    override fun bind(component: ChatAdapter.ChatAdapterWrapper) {
//        super.bind(component)
//        val item = component.item as ReferenceHolder
//
//        val mercuryClient = text.context.applicationContext as MercuryClient
//
//        val message = item.chatMessageInfo.message as ChatMessageVideo
//        val messageText = message.text
//        text.visibility = if (messageText.isEmpty()) View.GONE else View.VISIBLE
//        text.text = messageText
//
//        videoView.visibility = View.GONE
//
//        if (videoView.isPlaying) {
//            videoView.pause()
//        }
//
//        val mediaType = if (message.isGif.toBoolean()) FileType.GIF else FileType.VIDEO
//
//        val referenceFile = getReferenceFile(message.referenceUUID, mediaType, mercuryClient.filesDir, mercuryClient)
//
//        videoView.visibility = View.GONE
//        webView.visibility = View.GONE
//
//        if (item.isFinished) {
//            loadBar.visibility = View.GONE
//            loadButton.visibility = View.GONE
//            playButton.visibility = View.VISIBLE
//            thumbnail.visibility = View.VISIBLE
//
//            val isWebView = try {
//                val retriever = MediaMetadataRetriever()
//                retriever.setDataSource(mercuryClient, Uri.fromFile(referenceFile))
//                false
//            } catch (t: Throwable) {
//                true
//            }
//
//            if (isWebView) {
//                val movie = Movie.decodeFile(referenceFile.path)
//                if (movie != null) {
//                    val bitmap = Bitmap.createBitmap(movie.width(), movie.height(), Bitmap.Config.RGB_565)
//                    val canvas = Canvas(bitmap)
//                    movie.draw(canvas, 0f, 0f)
//                    thumbnail.setImageBitmap(bitmap)
//                }
//            } else {
//                thumbnail.setImageBitmap(ThumbnailUtils.createVideoThumbnail(referenceFile.path, MediaStore.Images.Thumbnails.MINI_KIND))
//            }
//
//            playButton.setOnClickListener {
//                thumbnail.visibility = View.GONE
//                playButton.visibility = View.GONE
//
//                if (!isWebView) {
//                    videoView.visibility = View.VISIBLE
//                    videoView.setVideoURI(Uri.fromFile(referenceFile))
//                    videoView.start()
//                } else {
//                    webView.visibility = View.VISIBLE
//                    webView.settings.loadWithOverviewMode = true
//                    webView.settings.useWideViewPort = true
//                    webView.loadUrl(Uri.fromFile(referenceFile).toString())
//                }
//            }
//
//            thumbnail.setOnClickListener {
//                val i = Intent(itemView.context, ChatMediaViewActivity::class.java)
//                i.putExtra(KEY_FILE_URI, Uri.fromFile(referenceFile))
//                i.putExtra(KEY_MEDIA_TYPE, FileType.GIF)
//                itemView.context.startActivity(i)
//            }
//
//            videoView.setOnCompletionListener {
//                videoView.seekTo(0)
//                videoView.start()
//            }
//
//            videoView.setOnClickListener {
//                videoView.pause()
//                videoView.seekTo(0)
//                videoView.visibility = View.GONE
//
//                thumbnail.visibility = View.VISIBLE
//                playButton.visibility = View.VISIBLE
//            }
//
//            webView.setOnClickListener {
//                webView.visibility = View.GONE
//                thumbnail.visibility = View.VISIBLE
//                playButton.visibility = View.VISIBLE
//            }
//
//        } else {
//            if (item.isInternetInProgress) {
//                loadBar.visibility = View.VISIBLE
//                loadBar.progress = item.progress
//                playButton.visibility = View.GONE
//            } else {
//                thumbnail.visibility = View.VISIBLE
//                if (item.chatMessageInfo.client) {
//                    thumbnail.setImageBitmap(ThumbnailUtils.createVideoThumbnail(referenceFile.path, MediaStore.Images.Thumbnails.MINI_KIND))
//                } else thumbnail.setImageResource(R.mipmap.ic_launcher)
//
//                playButton.visibility = View.VISIBLE
//                loadBar.visibility = View.GONE
//
//                playButton.visibility = View.GONE
//
//                loadButton.setImageResource(if (item.chatMessageInfo.client) R.drawable.ic_file_upload_white_24dp else R.drawable.ic_file_download_white_24dp)
//                loadButton.setOnClickListener {
//                    chatAdapter.activity.startReferenceLoad(item, adapterPosition, mediaType)
//                }
//            }
//        }
//
//        registerForEditModeLongPress(text)
//        registerForEditModeLongPress(thumbnail)
//        registerForEditModeLongPress(playButton)
//        registerForEditModeLongPress(loadButton)
//    }

    override fun getMessage(item: ReferenceHolder): ChatMessage = item.message.chatMessageInfo.message

    override fun isClient(item: ReferenceHolder): Boolean = item.message.chatMessageInfo.client
}