package de.intektor.kentai

import android.app.Activity
import android.content.Intent
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.widget.MediaController
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai_http_common.reference.FileType
import kotlinx.android.synthetic.main.activity_send_media.*

class SendMediaActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_send_media)

        val chatInfo: ChatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)
        val mediaType = FileType.values()[intent.getIntExtra(KEY_MEDIA_TYPE, 0)]
        val mediaUri: Uri = intent.getParcelableExtra(KEY_MEDIA_URL)

        supportActionBar?.title = getString(R.string.send_media_activity_title, chatInfo.chatName)

        if (mediaType == FileType.IMAGE) {
            activitySendMediaImage.setImageURI(mediaUri)
            activitySendMediaImage.visibility = View.VISIBLE
        } else if (mediaType == FileType.VIDEO) {
            val mediaController = MediaController(this, true)
            mediaController.setAnchorView(activitySendMediaVideo)
            activitySendMediaVideo.setMediaController(mediaController)

            activitySendMediaVideoLayout.visibility = View.VISIBLE

            activitySendMediaVideoGifButton.visibility = View.VISIBLE

            activitySendMediaVideo.setOnCompletionListener {
                activitySendMediaVideoPlay.visibility = View.VISIBLE
                activitySendMediaVideoPreview.visibility = View.VISIBLE

                activitySendMediaVideo.visibility = View.GONE
            }

            val realPath = getRealVideoPath(mediaUri, this)
            if (realPath.isNotBlank()) {
                val thumbnail = ThumbnailUtils.createVideoThumbnail(realPath, MediaStore.Images.Thumbnails.MINI_KIND)
                activitySendMediaVideoPreview.setImageBitmap(thumbnail)
            }

            activitySendMediaVideoPlay.setOnClickListener {
                activitySendMediaVideoPlay.visibility = View.GONE
                activitySendMediaVideoPreview.visibility = View.GONE

                activitySendMediaVideo.visibility = View.VISIBLE

                activitySendMediaVideo.setVideoURI(mediaUri)
                activitySendMediaVideo.start()
            }
        }

        activitySendMediaButton.setOnClickListener {
            val resultData = Intent()
            resultData.putExtra(KEY_MESSAGE_TEXT, activitySendMediaInput.text.toString())
            resultData.putExtra(KEY_MEDIA_TYPE, mediaType.ordinal)
            resultData.putExtra(KEY_IS_GIF, activitySendMediaVideoGifButton.isChecked)
            resultData.data = mediaUri
            setResult(Activity.RESULT_OK, resultData)
            finish()
        }
    }
}
