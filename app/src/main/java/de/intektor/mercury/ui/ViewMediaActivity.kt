package de.intektor.mercury.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import android.view.View
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.chat.getChatMessages
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.ui.chat.adapter.chat.HeaderItemDecoration
import de.intektor.mercury.ui.util.MediaAdapter
import de.intektor.mercury.util.KEY_CHAT_INFO
import de.intektor.mercury.util.KEY_FILE_URI
import de.intektor.mercury.util.KEY_MEDIA_TYPE
import de.intektor.mercury.util.KEY_MESSAGE_UUID
import de.intektor.mercury_common.reference.FileType
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

        val mercuryClient = applicationContext as MercuryClient

        val list = ReferenceUtil.getReferencesInChat(mercuryClient.dataBase, chatInfo.chatUUID).map { referenceInfo ->
            val referenceFile = ReferenceUtil.getFileForReference(this, referenceInfo.referenceUUID)
            val fileType = ReferenceUtil.getFileTypeForReference(mercuryClient.dataBase, referenceInfo.referenceUUID)

            val message = (getChatMessages(this, mercuryClient.dataBase, "message_uuid = ?", arrayOf(referenceInfo.messageUUID.toString())).firstOrNull()
                    ?: throw IllegalStateException("References without given message found. referenceUUID=${referenceInfo.referenceUUID}, messageUUID=${referenceInfo.messageUUID}"))
                    .chatMessageInfo.message

            ReferenceFile(referenceFile, fileType, message.messageCore.timeCreated, referenceInfo.messageUUID)
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
//            viewMediaIntent.putExtra(KEY_FILE_URI, Uri.fromFile(item.file))
            viewMediaIntent.putExtra(KEY_MEDIA_TYPE, item.fileType)
            viewMediaIntent.putExtra(KEY_MESSAGE_UUID, item.messageUUID)
            startActivity(viewMediaIntent)
        }, { _, _ -> })
        activityViewMediaList.adapter = adapter

        val layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)
        layoutManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
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

    class ReferenceFile(referenceFile: File, val fileType: FileType, time: Long, val messageUUID: UUID) : MediaAdapter.MediaFile(time, TODO())

    class CombinedReferences(val date: Date, val combined: MutableList<ReferenceFile>)

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
