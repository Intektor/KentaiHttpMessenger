package de.intektor.mercury.ui.chat.adapter.chat

import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.intektor.mercury.R
import de.intektor.mercury.android.loadVideoThumbnailFull
import de.intektor.mercury.android.videoPicasso
import de.intektor.mercury.chat.ReferenceHolder
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.task.ReferenceState
import de.intektor.mercury.ui.ChatMediaViewActivity
import de.intektor.mercury.util.setGone
import de.intektor.mercury.util.setVisible
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageVideo

class VideoMessageViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder<MessageVideo, ReferenceHolder>(view, chatAdapter) {

    private val imageView = itemView.findViewById<ImageView>(R.id.chatMessageVideoView)
    private val loadBar = itemView.findViewById<ProgressBar>(R.id.chatMessageVideoViewLoadBar)
    private val loadButton: ImageView = itemView.findViewById(R.id.chatMessageVideoPlayButton)
    private val text = itemView.findViewById<TextView>(R.id.chatMessageVideoViewText)

    override val parentLayout: LinearLayout
        get() = itemView.findViewById(R.id.bubble_layout_parent) as LinearLayout

    override val bubbleLayout: LinearLayout
        get() = itemView.findViewById(R.id.bubble_layout) as LinearLayout

    override fun bindMessage(item: ReferenceHolder, core: MessageCore, data: MessageVideo) {
        val context = itemView.context
        loadBar.max = 100

        val referenceFile = ReferenceUtil.getFileForReference(context, data.reference)

        when (item.referenceState) {
            ReferenceState.FINISHED -> {
                loadBar.setGone()
                loadButton.setGone()
            }
            ReferenceState.IN_PROGRESS -> {
                loadButton.setVisible()
                loadBar.setVisible()
            }
            ReferenceState.NOT_STARTED -> {
                loadBar.setGone()
                loadButton.setVisible()
            }
        }

        if (item.referenceState == ReferenceState.FINISHED) {
            loadButton.setImageResource(if (isClient(item)) R.drawable.ic_file_upload_white_24dp else R.drawable.ic_file_download_white_24dp)
        } else {
            Picasso.get().load(referenceFile).into(imageView)
        }

        if (isClient(item) || item.referenceState == ReferenceState.FINISHED) {
            videoPicasso(itemView.context).loadVideoThumbnailFull(referenceFile.path).into(imageView)
        }

        text.visibility = if (data.text.isBlank()) View.GONE else View.VISIBLE
        text.text = data.text

        imageView.setOnClickListener {
            ChatMediaViewActivity.launch(context, chatAdapter.chatInfo.chatUUID, data.reference)
        }
    }

    override fun getMessage(item: ReferenceHolder): ChatMessage = item.chatMessageInfo.chatMessageInfo.message

    override fun isClient(item: ReferenceHolder): Boolean = item.chatMessageInfo.chatMessageInfo.client
}