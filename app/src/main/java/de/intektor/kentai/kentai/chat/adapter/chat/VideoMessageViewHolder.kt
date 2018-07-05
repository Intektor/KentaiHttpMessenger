package de.intektor.kentai.kentai.chat.adapter.chat

import android.content.Intent
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
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.chat.ReferenceHolder
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai_http_common.chat.ChatMessageVideo
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.toUUID

class VideoMessageViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(view, chatAdapter) {

    private val imageView = itemView.findViewById<ImageView>(R.id.chatMessageVideoView)
    private val loadBar = itemView.findViewById<ProgressBar>(R.id.chatMessageVideoViewLoadBar)
    private val loadButton: ImageView = itemView.findViewById(R.id.chatMessageVideoPlayButton)
    private val text = itemView.findViewById<TextView>(R.id.chatMessageVideoViewText)

    override fun setComponent(component: Any) {
        component as ReferenceHolder
        val wrapper = component.chatMessageWrapper
        val message = wrapper.message as ChatMessageVideo

        val kentaiClient = itemView.context.applicationContext as KentaiClient

        val referenceFile = getReferenceFile(message.referenceUUID, FileType.VIDEO, chatAdapter.activity.filesDir, chatAdapter.activity)

        loadBar.max = 100

        loadBar.visibility = if (component.isInternetInProgress) View.VISIBLE else View.GONE

        if (component.isInternetInProgress) {
            loadBar.progress = component.progress
            imageView.visibility = View.GONE
        } else {
            loadBar.progress = 0
            imageView.visibility = View.VISIBLE
        }

        if (!component.isFinished && !component.isInternetInProgress) {
            loadButton.visibility = View.VISIBLE
        } else {
            loadButton.visibility = View.GONE
        }

        imageView.scaleType = ImageView.ScaleType.CENTER_CROP

        imageView.setOnClickListener {
            val viewImageIntent = Intent(imageView.context, ViewIndividualMediaActivity::class.java)
            viewImageIntent.putExtra(KEY_FILE_URI, Uri.fromFile(referenceFile))
            viewImageIntent.putExtra(KEY_MEDIA_TYPE, FileType.VIDEO)
            viewImageIntent.putExtra(KEY_MESSAGE_UUID, message.id.toUUID())
            imageView.context.startActivity(viewImageIntent)
        }

        if (!component.isFinished) {
            loadButton.setImageResource(if (wrapper.client) R.drawable.ic_file_upload_white_24dp else R.drawable.ic_file_download_white_24dp)
        } else {
            val imageFileUri = if (component.isFinished) Uri.fromFile(referenceFile) else null
            Picasso.with(itemView.context).load(imageFileUri).into(imageView)
        }

        loadButton.setOnClickListener {
            chatAdapter.activity.startReferenceLoad(component, adapterPosition, FileType.VIDEO)
        }

        if (wrapper.client || component.isFinished) {
            videoPicasso(itemView.context).loadVideoThumbnailFull(referenceFile.path).into(imageView)
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