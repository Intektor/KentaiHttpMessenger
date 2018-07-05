package de.intektor.kentai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.view.View
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.adapter.chat.HeaderItemDecoration
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai.kentai.view.media.MediaAdapter
import de.intektor.kentai_http_common.chat.MessageType
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.toUUID
import kotlinx.android.synthetic.main.activity_view_media.*
import java.io.File
import java.util.*

class ViewMediaActivity : AppCompatActivity() {

    lateinit var adapter: MediaAdapter<ReferenceFile>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

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

        val actList = mutableListOf<Any>()
        grouped.reversed().forEach {
            actList += MediaAdapter.MediaFileHeader(it.date.time)
            actList.addAll(it.combined.reversed())
        }

        adapter = MediaAdapter(actList, { item, _ ->
            val viewMediaIntent = Intent(this, ViewIndividualMediaActivity::class.java)
            viewMediaIntent.putExtra(KEY_FILE_URI, Uri.fromFile(item.file))
            viewMediaIntent.putExtra(KEY_MEDIA_TYPE, item.fileType)
            viewMediaIntent.putExtra(KEY_MESSAGE_UUID, item.messageUUID)
            startActivity(viewMediaIntent)
        }, { _, _ -> })
        activityViewMediaList.adapter = adapter

        val layoutManager = GridLayoutManager(this, 3)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (actList[position]) {
                    is MediaAdapter.MediaFileHeader -> 3
                    is ReferenceFile -> 1
                    else -> throw IllegalArgumentException()
                }
            }
        }

        activityViewMediaList.layoutManager = layoutManager

        activityViewMediaList.addItemDecoration(HeaderItemDecoration(activityViewMediaList, object : HeaderItemDecoration.StickyHeaderInterface {
            override fun getHeaderPositionForItem(itemPosition: Int): Int {
                var i = itemPosition
                while (true) {
                    if (isHeader(i)) return i
                    i--
                }
            }

            override fun getHeaderLayout(headerPosition: Int): Int = R.layout.media_group_header

            override fun bindHeaderData(header: View, headerPosition: Int) {
                MediaAdapter.MediaHeaderViewHolder(header).bind(actList[headerPosition] as MediaAdapter.MediaFileHeader)
            }

            override fun isHeader(itemPosition: Int): Boolean = actList[itemPosition] is MediaAdapter.MediaFileHeader
        }))


        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    class ReferenceFile(referenceFile: File, val fileType: FileType, time: Long, val messageUUID: UUID) : MediaAdapter.MediaFile(time, referenceFile)

    class CombinedReferences(val date: Date, val combined: MutableList<ReferenceFile>)

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
