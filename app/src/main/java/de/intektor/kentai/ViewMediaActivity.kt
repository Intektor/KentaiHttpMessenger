package de.intektor.kentai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import de.intektor.kentai.kentai.KEY_CHAT_INFO
import de.intektor.kentai.kentai.KEY_FILE_URI
import de.intektor.kentai.kentai.KEY_MEDIA_TYPE
import de.intektor.kentai.kentai.KEY_MESSAGE_UUID
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai.kentai.view.media.MediaGroupAdapter
import de.intektor.kentai_http_common.chat.MessageType
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.toUUID
import kotlinx.android.synthetic.main.activity_view_media.*
import java.io.File
import java.util.*

class ViewMediaActivity : AppCompatActivity() {

    lateinit var adapter: MediaGroupAdapter<ReferenceFile, CombinedReferences>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_view_media)

        val chatInfo = intent.getParcelableExtra<ChatInfo>(KEY_CHAT_INFO)

        val calendar = Calendar.getInstance()

        val kentaiClient = applicationContext as KentaiClient
        val list = kentaiClient.dataBase.rawQuery("SELECT reference, type, time, message_uuid FROM chat_table WHERE chat_uuid = ? AND (type = ? OR type = ?)",
                arrayOf(chatInfo.chatUUID.toString(), MessageType.VIDEO_MESSAGE.ordinal.toString(), MessageType.IMAGE_MESSAGE.ordinal.toString())).use { cursor ->
            val list = mutableListOf<ReferenceFile>()
            while (cursor.moveToNext()) {
                val referenceUUID = cursor.getString(0).toUUID()
                val type = MessageType.values()[cursor.getInt(1)]
                val time = cursor.getLong(2)
                val messageUUID = cursor.getString(3).toUUID()

                val fileType = when (type) {
                    MessageType.IMAGE_MESSAGE -> {
                        FileType.IMAGE
                    }
                    MessageType.VIDEO_MESSAGE -> {
                        FileType.VIDEO
                    }
                    else -> throw IllegalArgumentException()
                }
                list += ReferenceFile(getReferenceFile(referenceUUID, fileType, filesDir, this), fileType, time, messageUUID)
            }
            list
        }

        val grouped = list.groupBy {
            calendar.time = Date(it.time)
            calendar.get(Calendar.MONTH) to calendar.get(Calendar.YEAR)
        }.map { CombinedReferences(Date(it.value.first().time), it.value.toMutableList()) }

        adapter = MediaGroupAdapter(grouped, { item, _, _ ->
            val viewMediaIntent = Intent(this, ViewIndividualMediaActivity::class.java)
            viewMediaIntent.putExtra(KEY_FILE_URI, Uri.fromFile(item.file))
            viewMediaIntent.putExtra(KEY_MEDIA_TYPE, item.fileType)
            viewMediaIntent.putExtra(KEY_MESSAGE_UUID, item.messageUUID)
            startActivity(viewMediaIntent)
        }, { _, _, _ -> })
        activityViewMediaList.adapter = adapter
        activityViewMediaList.layoutManager = LinearLayoutManager(this)
        activityViewMediaList.isNestedScrollingEnabled = false

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class ReferenceFile(referenceFile: File, val fileType: FileType, time: Long, val messageUUID: UUID) : MediaGroupAdapter.MediaFile(time, referenceFile)

    class CombinedReferences(date: Date, combined: MutableList<ReferenceFile>) : MediaGroupAdapter.GroupedMediaFile<ReferenceFile>(date, combined)

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
