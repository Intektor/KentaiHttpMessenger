package de.intektor.mercury.ui

import android.content.ComponentName
import android.content.Context
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
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getFileTypeExtra
import de.intektor.mercury.android.getRealVideoPath
import de.intektor.mercury.android.getUUIDExtra
import de.intektor.mercury.android.getUriExtra
import de.intektor.mercury.chat.getChatMessages
import de.intektor.mercury.chat.getContact
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.nine_gag.isNineGagMessage
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageData
import de.intektor.mercury_common.chat.data.MessageImage
import de.intektor.mercury_common.chat.data.MessageVideo
import de.intektor.mercury_common.reference.FileType
import kotlinx.android.synthetic.main.activity_view_individual_media.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ViewIndividualMediaActivity : AppCompatActivity() {

    private lateinit var fileType: FileType
    private lateinit var uri: Uri
    private var chatMessage: ChatMessage? = null

    companion object {
        private const val EXTRA_FILE_URI: String = "de.intektor.mercury.EXTRA_FILE_URI"
        private const val EXTRA_FILE_TYPE: String = "de.intektor.mercury.EXTRA_FILE_TYPE"
        private const val EXTRA_MESSAGE_UUID: String = "de.intektor.mercury.EXTRA_MESSAGE_UUID"

        private fun createIntent(context: Context, fileUri: Uri, fileType: FileType, messageUuid: UUID) =
                Intent()
                        .setComponent(ComponentName(context, ViewIndividualMediaActivity::class.java))
                        .putExtra(EXTRA_FILE_URI, fileUri)
                        .putExtra(EXTRA_FILE_TYPE, fileType)
                        .putExtra(EXTRA_MESSAGE_UUID, messageUuid)

        fun launch(context: Context, fileUri: Uri, fileType: FileType, messageUuid: UUID) {
            context.startActivity(createIntent(context, fileUri, fileType, messageUuid))
        }

        fun getData(intent: Intent): Holder {
            val fileUri: Uri = intent.getUriExtra(EXTRA_FILE_URI)
            val fileType: FileType = intent.getFileTypeExtra(EXTRA_FILE_TYPE)
            val messageUuid: UUID = intent.getUUIDExtra(EXTRA_MESSAGE_UUID)
            return Holder(fileUri, fileType, messageUuid)
        }

        data class Holder(val fileUri: Uri, val fileType: FileType, val messageUuid: UUID)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_view_individual_media)

        setSupportActionBar(activityViewIndividualMediaToolbar)

        val mercuryClient = applicationContext as MercuryClient

        val (fileUri, fileType, messageUUID) = getData(intent)

        val chatMessage = (getChatMessages(this, mercuryClient.dataBase, "message_uuid = ?", arrayOf(messageUUID.toString()), limit = "1").firstOrNull()
                ?: throw IllegalStateException("No message found matching messageUUID=$messageUUID")).chatMessageInfo.message
        this.chatMessage = chatMessage

        val dateFormat = SimpleDateFormat.getDateTimeInstance()

        val contact = getContact(mercuryClient.dataBase, chatMessage.messageCore.senderUUID)

        activityViewIndividualMediaWhoWhen.text = getString(
                R.string.view_individual_media_who_when,
                ContactUtil.getDisplayName(this, mercuryClient.dataBase, contact),
                dateFormat.format(chatMessage.messageCore.timeCreated))

        val data = chatMessage.messageData

        val text: String = getText(data)
        activityViewIndividualMediaMessage.text = text

        activityViewIndividualMediaMessage.visibility = if (text.isBlank()) View.GONE else View.VISIBLE

        if (fileType == FileType.IMAGE) {
            activityViewIndividualMediaImage.visibility = View.VISIBLE
            Picasso.get().load(uri).into(activityViewIndividualMediaImage)
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

                    val text = getText(cM?.messageData ?: return false)

                    val isNineGag = isNineGagMessage(text)

                    val shareIntent = Intent(this, ShareReceiveActivity::class.java)
                    shareIntent.action = Intent.ACTION_SEND
                    if (!isNineGag) {
                        shareIntent.type = if (fileType == FileType.VIDEO || fileType == FileType.GIF) "video/*" else if (fileType == FileType.IMAGE) "image/*" else throw IllegalArgumentException()
                        shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
                    } else {
                        shareIntent.type = "text/plain"
                        shareIntent.putExtra(Intent.EXTRA_TEXT, text)
                    }
                    startActivity(shareIntent)
                    return true
                }
            }
        }
        return false
    }

    private fun getText(data: MessageData): String = when (data) {
        is MessageImage -> data.text
        is MessageVideo -> data.text
        else -> "Error: Wrong type"
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
