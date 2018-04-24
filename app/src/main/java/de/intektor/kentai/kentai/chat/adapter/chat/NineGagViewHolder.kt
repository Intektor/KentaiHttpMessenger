package de.intektor.kentai.kentai.chat.adapter.chat

import android.content.Intent
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import com.squareup.picasso.Picasso
import de.intektor.kentai.R
import de.intektor.kentai.ViewIndividualMediaActivity
import de.intektor.kentai.kentai.KEY_FILE_URI
import de.intektor.kentai.kentai.KEY_MEDIA_TYPE
import de.intektor.kentai.kentai.KEY_MESSAGE_UUID
import de.intektor.kentai.kentai.chat.ReferenceHolder
import de.intektor.kentai.kentai.nine_gag.*
import de.intektor.kentai.kentai.references.cancelReferenceDownload
import de.intektor.kentai_http_common.reference.FileType

/**
 * @author Intektor
 */
class NineGagViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(view, chatAdapter) {

    val title: TextView = view.findViewById(R.id.nineGagTitle)
    val video: VideoView = view.findViewById(R.id.nineGagVideoView)
    val thumbnail: ImageView = view.findViewById(R.id.nineGagThumbnail)
    val playButton: ImageView = view.findViewById(R.id.nineGagPlayButton)
    val gifButton: TextView = view.findViewById(R.id.nineGagGifPlay)
    val load: ProgressBar = view.findViewById(R.id.nineGagDownloadBar)

    override fun setComponent(component: Any) {
        component as ReferenceHolder

        val gagTitle = component.chatMessageWrapper.message.text.substringBefore("https://9gag.com/gag/")
        val gagId = getGagId(component.chatMessageWrapper.message.text)

        gifButton.visibility = View.GONE
        video.visibility = View.GONE

        if (video.isPlaying) {
            video.pause()
        }

        title.text = gagTitle

        load.visibility = View.GONE

        thumbnail.visibility = View.VISIBLE

        if (!component.isFinished) {
            if (!component.isInternetInProgress) {
                playButton.setImageResource(R.drawable.ic_file_download_white_24dp)
                load.visibility = View.GONE
                thumbnail.setImageResource(R.mipmap.ic_launcher)
                playButton.visibility = View.VISIBLE

                playButton.setOnClickListener {
                    downloadNineGag(gagId, getGagUUID(component.chatMessageWrapper.message.text), chatAdapter.chatInfo.chatUUID, itemView.context)
                    component.isInternetInProgress = true
                    component.progress = 0
                    chatAdapter.notifyItemChanged(adapterPosition)
                }
            } else {
                playButton.setImageResource(R.drawable.ic_cancel_white_24dp)
                load.visibility = View.VISIBLE
                load.progress = component.progress
                load.max = 100
                thumbnail.setImageResource(R.mipmap.ic_launcher)
                playButton.setOnClickListener {
                    cancelReferenceDownload(getGagUUID(component.chatMessageWrapper.message.text), itemView.context)
                    component.progress = 0
                    component.isInternetInProgress = false
                    chatAdapter.notifyItemChanged(adapterPosition)
                }
            }
        } else {
            val imageFile = getNineGagFile(gagId, NineGagType.IMAGE, itemView.context)
            val videoFile = getNineGagFile(gagId, NineGagType.VIDEO, itemView.context)

            if (imageFile.exists()) {
                Picasso.with(itemView.context).load(imageFile).placeholder(R.drawable.ic_account_circle_white_24dp).into(thumbnail)

                video.visibility = View.GONE
                playButton.visibility = View.GONE

                thumbnail.setOnClickListener {
                    val i = Intent(itemView.context, ViewIndividualMediaActivity::class.java)
                    i.putExtra(KEY_FILE_URI, Uri.fromFile(imageFile))
                    i.putExtra(KEY_MEDIA_TYPE, FileType.IMAGE)
                    i.putExtra(KEY_MESSAGE_UUID, component.chatMessageWrapper.message.id)
                    itemView.context.startActivity(i)
                }
            } else if (videoFile.exists()) {
                thumbnail.setImageBitmap(ThumbnailUtils.createVideoThumbnail(videoFile.path, MediaStore.Images.Thumbnails.FULL_SCREEN_KIND))

                video.visibility = View.GONE
                playButton.visibility = View.VISIBLE
                playButton.setImageResource(R.drawable.ic_play_arrow_white_24dp)
                playButton.setOnClickListener {
                    playButton.visibility = View.GONE
                    video.visibility = View.VISIBLE
                    thumbnail.visibility = View.GONE

                    video.setVideoURI(Uri.fromFile(videoFile))

                    video.start()
                }

                video.setOnCompletionListener {
                    video.seekTo(0)
                    video.start()
                }

                video.setOnClickListener {
                    video.pause()
                    video.seekTo(0)
                    video.visibility = View.GONE

                    thumbnail.visibility = View.VISIBLE
                    playButton.visibility = View.VISIBLE
                }
            }
        }
    }
}