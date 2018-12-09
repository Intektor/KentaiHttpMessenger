package de.intektor.mercury.ui.chat.adapter.chat

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import de.intektor.mercury.R
import de.intektor.mercury.chat.adapter.ReferenceHolder
import de.intektor.mercury.media.MediaType
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.task.ReferenceState
import de.intektor.mercury.ui.view.GifView
import de.intektor.mercury.util.setGone
import de.intektor.mercury.util.setVisible
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageVideo

class GifMessageViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder<MessageVideo, ReferenceHolder>(view, chatAdapter) {

    private val content: GifView = view.findViewById(R.id.item_chat_message_gif_wv)
    private val downloadParentCv: CardView = view.findViewById(R.id.item_chat_message_gif_cv_download_parent)
    private val downloadParentCl: ConstraintLayout = view.findViewById(R.id.item_chat_message_gif_cl_download_parent)
    private val downloadLabel: TextView = view.findViewById(R.id.item_chat_message_gif_tv_download)
    private val loadParentCv: CardView = view.findViewById(R.id.item_chat_message_gif_cv_load_parent)
    private val playParentCv: CardView = view.findViewById(R.id.item_chat_message_gif_cv_play_parent)
    private val playParentCl: ConstraintLayout = view.findViewById(R.id.item_chat_message_gif_cl_play_parent)

    override val parentLayout: LinearLayout
        get() = itemView.findViewById(R.id.bubble_layout_parent)

    override val bubbleLayout: ViewGroup
        get() = itemView.findViewById(R.id.bubble_layout)

    override fun bindMessage(item: ReferenceHolder, core: MessageCore, data: MessageVideo) {
        val context = itemView.context
        val isClient = item.message.chatMessageInfo.client

        val referenceFile = ReferenceUtil.getFileForReference(context, data.reference)

        when (item.referenceState) {
            ReferenceState.FINISHED -> {
                downloadParentCv.setGone()
                loadParentCv.setGone()
                playParentCv.setGone()

                content.setGif(referenceFile)
            }
            ReferenceState.IN_PROGRESS -> {
                downloadParentCv.setGone()
                loadParentCv.setVisible()
                playParentCv.setGone()

                if (isClient) {
                    content.setGif(referenceFile)
                }
            }
            ReferenceState.NOT_STARTED -> {
                downloadParentCv.setVisible()
                loadParentCv.setGone()
                playParentCv.setGone()

                downloadLabel.text = context.getString(if (isClient) R.string.media_upload else R.string.media_download)

                if (isClient) {
                    content.setGif(referenceFile)
                }
            }
        }

        downloadParentCl.setOnClickListener {
            chatAdapter.activity.startReferenceLoad(item, adapterPosition, MediaType.MEDIA_TYPE_VIDEO)
        }

        content.layoutParams.width = (data.width)
        content.layoutParams.height = (data.height)

        registerForBoth(content)
    }

    override fun getMessage(item: ReferenceHolder): ChatMessage = item.message.chatMessageInfo.message

    override fun isClient(item: ReferenceHolder): Boolean = item.message.chatMessageInfo.client
}