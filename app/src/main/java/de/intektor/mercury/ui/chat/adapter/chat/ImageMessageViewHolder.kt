package de.intektor.mercury.ui.chat.adapter.chat

import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.intektor.mercury.R
import de.intektor.mercury.chat.ReferenceHolder
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.task.ReferenceState
import de.intektor.mercury.ui.ChatMediaViewActivity
import de.intektor.mercury.util.setGone
import de.intektor.mercury.util.setVisible
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageImage
import de.intektor.mercury_common.reference.FileType

class ImageMessageViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder<MessageImage, ReferenceHolder>(view, chatAdapter) {

    override val parentLayout: LinearLayout
        get() = itemView.findViewById(R.id.bubble_layout) as LinearLayout
    override val bubbleLayout: LinearLayout
        get() = itemView.findViewById(R.id.bubble_layout_parent) as LinearLayout

    private var imageView: ImageView = itemView.findViewById(R.id.chatMessageImageView)
    private var loadBar: ProgressBar = itemView.findViewById(R.id.chatMessageImageViewLoadBar)
    private var loadButton: ImageView = itemView.findViewById(R.id.chatMessageImageViewLoadButton)
    private var text: TextView = itemView.findViewById(R.id.chatMessageImageViewText)

    override fun bindMessage(item: ReferenceHolder, core: MessageCore, data: MessageImage) {
        val context = itemView.context
        val referenceFile = ReferenceUtil.getFileForReference(context, data.reference)

        when (item.referenceState) {
            ReferenceState.FINISHED -> {
                loadButton.setGone()
                loadBar.setGone()
            }
            ReferenceState.IN_PROGRESS -> {
                loadButton.setGone()
                loadBar.setGone()
            }
            ReferenceState.NOT_STARTED -> {
                loadButton.setVisible()
                loadBar.setGone()
            }
        }

        if (item.referenceState == ReferenceState.FINISHED || isClient(item)) {
            Picasso.get().load(referenceFile).placeholder(ColorDrawable(Color.BLACK)).resize(800, 0).into(imageView)
        } else {
            val smallPreview = data.thumbnail
            val bitmap = BitmapFactory.decodeByteArray(smallPreview, 0, smallPreview.size)
            imageView.setImageBitmap(bitmap)
        }

        registerForEditModePress(imageView) {
            ChatMediaViewActivity.launch(context, chatAdapter.chatInfo.chatUUID, data.reference)
        }

        registerForEditModePress(loadButton) {
            chatAdapter.activity.startReferenceLoad(item, adapterPosition, FileType.IMAGE)
        }

        text.visibility = if (data.text.isBlank()) View.GONE else View.VISIBLE

        text.text = data.text

        registerForEditModeLongPress(text)
        registerForEditModePress(text)
        registerForEditModeLongPress(imageView)
        registerForEditModeLongPress(loadButton)
    }

    override fun getMessage(item: ReferenceHolder): ChatMessage = item.chatMessageInfo.chatMessageInfo.message

    override fun isClient(item: ReferenceHolder): Boolean = item.chatMessageInfo.chatMessageInfo.client
}