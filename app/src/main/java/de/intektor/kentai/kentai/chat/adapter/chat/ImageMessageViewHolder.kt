package de.intektor.kentai.kentai.chat.adapter.chat

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.ViewIndividualMediaActivity
import de.intektor.kentai.kentai.KEY_FILE_URI
import de.intektor.kentai.kentai.KEY_MEDIA_TYPE
import de.intektor.kentai.kentai.KEY_MESSAGE_UUID
import de.intektor.kentai.kentai.chat.ReferenceHolder
import de.intektor.kentai.kentai.getAttrDrawable
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai_http_common.chat.ChatMessage
import de.intektor.kentai_http_common.chat.ChatMessageImage
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.toUUID

class ImageMessageViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(view, chatAdapter) {

    private var imageView: ImageView = itemView.findViewById(R.id.chatMessageImageView)
    private var loadBar: ProgressBar = itemView.findViewById(R.id.chatMessageImageViewLoadBar)
    private var loadButton: ImageView = itemView.findViewById(R.id.chatMessageImageViewLoadButton)
    private var text: TextView = itemView.findViewById(R.id.chatMessageImageViewText)

    private lateinit var message: ChatMessage

    override fun setComponent(component: Any) {
        component as ReferenceHolder

        val wrapper = component.chatMessageWrapper

        val kentaiClient = itemView.context.applicationContext as KentaiClient

        message = wrapper.message as ChatMessageImage

        val message = message as ChatMessageImage

        val referenceFile = getReferenceFile(message.referenceUUID, FileType.IMAGE, chatAdapter.activity.filesDir, chatAdapter.activity)

        loadBar.max = 100

        if (component.isFinished) {
            loadButton.visibility = View.GONE
            loadBar.visibility = View.GONE
        } else {
            if (component.isInternetInProgress) {
                loadButton.visibility = View.GONE
                loadBar.progress = component.progress
            } else {
                loadButton.visibility = View.VISIBLE
                loadBar.visibility = View.GONE

                loadButton.setImageResource(if (wrapper.client) R.drawable.ic_file_upload_white_24dp else R.drawable.ic_file_download_white_24dp)
            }
        }

        if (component.isFinished || wrapper.client) {
            Picasso.with(itemView.context).load(referenceFile).resize(800, 0).into(imageView)
        } else {
            val smallPreview = message.smallPreview
            val bitmap = BitmapFactory.decodeByteArray(smallPreview, 0, smallPreview.size)
            imageView.setImageBitmap(bitmap)
        }

        imageView.setOnClickListener {
            val viewImageIntent = Intent(imageView.context, ViewIndividualMediaActivity::class.java)
            viewImageIntent.putExtra(KEY_FILE_URI, Uri.fromFile(referenceFile))
            viewImageIntent.putExtra(KEY_MEDIA_TYPE, FileType.IMAGE)
            viewImageIntent.putExtra(KEY_MESSAGE_UUID, message.id.toUUID())
            imageView.context.startActivity(viewImageIntent)
        }

        loadButton.setOnClickListener {
            chatAdapter.activity.startReferenceLoad(component, adapterPosition, FileType.IMAGE)
        }

        text.visibility = if (message.text.isBlank()) View.GONE else View.VISIBLE

        text.text = message.text

        val layout = itemView.findViewById(R.id.bubble_layout) as LinearLayout
        val parentLayout = itemView.findViewById(R.id.bubble_layout_parent) as LinearLayout

        if (component.chatMessageWrapper.client) {
            layout.background = getAttrDrawable(itemView.context, R.attr.bubble_right)
            parentLayout.gravity = Gravity.END
        } else {
            layout.background = getAttrDrawable(itemView.context, R.attr.bubble_left)
            parentLayout.gravity = Gravity.START
        }
    }
}