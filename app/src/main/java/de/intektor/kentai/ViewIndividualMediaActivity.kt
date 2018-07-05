package de.intektor.kentai

import android.content.Intent
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.MediaController
import com.squareup.picasso.Picasso
import de.intektor.kentai.kentai.KEY_FILE_URI
import de.intektor.kentai.kentai.KEY_MEDIA_TYPE
import de.intektor.kentai.kentai.KEY_MESSAGE_UUID
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.getContact
import de.intektor.kentai.kentai.chat.readChatMessageWrappers
import de.intektor.kentai.kentai.getRealVideoPath
import de.intektor.kentai.kentai.nine_gag.isNineGagMessage
import de.intektor.kentai_http_common.reference.FileType
import kotlinx.android.synthetic.main.activity_view_individual_media.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ViewIndividualMediaActivity : AppCompatActivity() {

    private lateinit var fileType: FileType
    private lateinit var uri: Uri
    private var chatMessage: ChatMessageWrapper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_view_individual_media)

        setSupportActionBar(activityViewIndividualMediaToolbar)

        val kentaiClient = applicationContext as KentaiClient

        uri = intent.getParcelableExtra(KEY_FILE_URI)
        fileType = intent.getSerializableExtra(KEY_MEDIA_TYPE) as FileType
        val messageUUID = intent.getSerializableExtra(KEY_MESSAGE_UUID) as UUID

        val chatMessage = readChatMessageWrappers(kentaiClient.dataBase, "message_uuid = ?", arrayOf(messageUUID.toString()), limit = "1").first()

        this.chatMessage = chatMessage

        val dateFormat = SimpleDateFormat.getDateTimeInstance()

        val contact = getContact(kentaiClient.dataBase, chatMessage.message.senderUUID)

        activityViewIndividualMediaWhoWhen.text = getString(R.string.view_individual_media_who_when, getName(contact, this, true), dateFormat.format(chatMessage.message.timeSent))

        activityViewIndividualMediaMessage.text = chatMessage.message.text
        activityViewIndividualMediaMessage.visibility = if (chatMessage.message.text.isBlank()) View.GONE else View.VISIBLE

        if (fileType == FileType.IMAGE) {
            activityViewIndividualMediaImage.visibility = View.VISIBLE
            Picasso.with(this).load(uri).into(activityViewIndividualMediaImage)
        } else if (fileType == FileType.VIDEO || fileType == FileType.GIF) {
            val mediaController = MediaController(this, true)
            mediaController.setAnchorView(activityViewIndividualMediaVideo)
            activityViewIndividualMediaVideo.setMediaController(mediaController)

            activityViewIndividualMediaVideoLayout.visibility = View.VISIBLE

            activityViewIndividualMediaVideo.setOnCompletionListener {
                activityViewIndividualMediaVideoPlay.visibility = View.VISIBLE
                activityViewIndividualMediaVideoPreview.visibility = View.VISIBLE

                activityViewIndividualMediaVideo.visibility = View.GONE
            }

            val realPath = getRealVideoPath(uri, this)
            if (realPath.isNotBlank()) {
                val thumbnail = ThumbnailUtils.createVideoThumbnail(realPath, MediaStore.Images.Thumbnails.MINI_KIND)
                activityViewIndividualMediaVideoPreview.setImageBitmap(thumbnail)
            } else if (File(uri.path).exists()) {
                val thumbnail = ThumbnailUtils.createVideoThumbnail(uri.path, MediaStore.Images.Thumbnails.MINI_KIND)
                activityViewIndividualMediaVideoPreview.setImageBitmap(thumbnail)
            }

            activityViewIndividualMediaVideoPlay.setOnClickListener {
                activityViewIndividualMediaVideoPlay.visibility = View.GONE
                activityViewIndividualMediaVideoPreview.visibility = View.GONE

                activityViewIndividualMediaVideo.visibility = View.VISIBLE

                activityViewIndividualMediaVideo.setVideoURI(uri)
                activityViewIndividualMediaVideo.start()
            }
        }

        activityViewIndividualMediaImage.setOnClickListener {
            if (supportActionBar?.isShowing == true) {
                supportActionBar?.hide()
                activityViewIndividualMediaMessage.visibility = View.GONE
                activityViewIndividualMediaWhoWhen.visibility = View.GONE
            } else {
                supportActionBar?.show()
                activityViewIndividualMediaMessage.visibility = View.VISIBLE
                activityViewIndividualMediaWhoWhen.visibility = View.VISIBLE
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_view_individual_media, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (item != null) {
            when (item.itemId) {
                R.id.menuViewIndividualMediaShare -> {
                    val cM = chatMessage
                    val isNineGag = cM != null && isNineGagMessage(cM.message.text)

                    val shareIntent = Intent(this, ShareReceiveActivity::class.java)
                    shareIntent.action = Intent.ACTION_SEND
                    if (!isNineGag) {
                        shareIntent.type = if (fileType == FileType.VIDEO || fileType == FileType.GIF) "video/*" else if (fileType == FileType.IMAGE) "image/*" else throw IllegalArgumentException()
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                    } else {
                        shareIntent.type = "text/plain"
                        shareIntent.putExtra(Intent.EXTRA_TEXT, cM?.message?.text)
                    }
                    startActivity(shareIntent)
                    return true
                }
            }
        }
        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
