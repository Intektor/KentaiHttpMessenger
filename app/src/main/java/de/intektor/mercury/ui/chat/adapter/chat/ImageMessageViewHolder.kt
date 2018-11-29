package de.intektor.mercury.ui.chat.adapter.chat

import android.graphics.drawable.BitmapDrawable
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import de.intektor.mercury.R
import de.intektor.mercury.chat.adapter.ReferenceHolder
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.media.MediaType
import de.intektor.mercury.media.ReferenceFile
import de.intektor.mercury.media.ThumbnailUtil
import de.intektor.mercury.task.ReferenceState
import de.intektor.mercury.ui.ChatMediaViewActivity
import de.intektor.mercury.util.setGone
import de.intektor.mercury.util.setVisible
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageImage
import java.io.ByteArrayInputStream

class ImageMessageViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder<MessageImage, ReferenceHolder>(view, chatAdapter) {

    override val parentLayout: LinearLayout
        get() = itemView.findViewById(R.id.bubble_layout_parent) as LinearLayout
    override val bubbleLayout: ViewGroup
        get() = itemView.findViewById(R.id.bubble_layout) as ViewGroup

    private val content: ImageView = view.findViewById(R.id.item_chat_message_image_iv_content)
    private val loadProgressParent: CardView = view.findViewById(R.id.item_chat_message_image_cv_load_progress_parent)
    private val loadTextParent: CardView = view.findViewById(R.id.item_chat_message_image_cv_load_parent)
    private val loadText: TextView = view.findViewById(R.id.item_chat_message_image_tv_load_label)
    private val subtext: TextView = view.findViewById(R.id.item_chat_message_image_tv_subtext)

    override fun bindMessage(item: ReferenceHolder, core: MessageCore, data: MessageImage) {
        val context = itemView.context

        subtext.visibility = if (data.text.isNotBlank()) View.VISIBLE else View.GONE
        subtext.text = data.text

        ThumbnailUtil.loadThumbnail(
                ReferenceFile(data.reference, chatAdapter.chatInfo.chatUUID, MediaType.MEDIA_TYPE_IMAGE, core.timeCreated),
                content,
                MediaStore.Images.Thumbnails.FULL_SCREEN_KIND,
                BitmapDrawable(context.resources, ByteArrayInputStream(data.thumbnail)))

        when (item.referenceState) {
            ReferenceState.FINISHED -> {
                loadProgressParent.setGone()
                loadTextParent.setGone()
            }
            ReferenceState.IN_PROGRESS -> {
                loadProgressParent.setVisible()
                loadTextParent.setGone()
            }
            ReferenceState.NOT_STARTED -> {
                loadProgressParent.setGone()
                loadTextParent.setVisible()

                if (ClientPreferences.getClientUUID(context) == core.senderUUID) {
                    loadText.setText(R.string.media_upload)
                } else {
                    loadText.setText(R.string.media_download)
                }
            }
        }

        registerForEditModePress(content) {
            if (item.referenceState == ReferenceState.FINISHED) {
                ChatMediaViewActivity.launch(context, chatAdapter.chatInfo.chatUUID, data.reference)
            }
        }

        registerForEditModePress(loadText) {
            chatAdapter.activity.startReferenceLoad(item, adapterPosition, MediaType.MEDIA_TYPE_IMAGE)
        }

        registerForEditModeLongPress(parentLayout)
        registerForEditModeLongPress(content)
        registerForEditModeLongPress(loadTextParent)
    }

    override fun getMessage(item: ReferenceHolder): ChatMessage = item.message.chatMessageInfo.message

    override fun isClient(item: ReferenceHolder): Boolean = item.message.chatMessageInfo.client
}