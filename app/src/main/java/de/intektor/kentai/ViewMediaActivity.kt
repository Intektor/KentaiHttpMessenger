package de.intektor.kentai

import android.graphics.LinearGradient
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.widget.LinearLayout
import de.intektor.kentai.kentai.KEY_CHAT_INFO
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.view.media.MediaGroupAdapter
import de.intektor.kentai_http_common.chat.MessageType
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.toUUID
import kotlinx.android.synthetic.main.activity_view_media.*
import java.util.*

class ViewMediaActivity : AppCompatActivity() {

    lateinit var adapter: MediaGroupAdapter

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

                list += ReferenceFile(referenceUUID, when (type) {
                    MessageType.IMAGE_MESSAGE -> {
                        FileType.IMAGE
                    }
                    MessageType.VIDEO_MESSAGE -> {
                        FileType.VIDEO
                    }
                    else -> throw IllegalArgumentException()
                }, time, messageUUID)
            }
            list
        }

        val grouped = list.groupBy {
            calendar.time = Date(it.time)
            calendar.get(Calendar.MONTH) to calendar.get(Calendar.YEAR)
        }.map { CombinedReferences(it.value, Date(it.value.first().time)) }

        adapter = MediaGroupAdapter(grouped, chatInfo.chatUUID)
        activityViewMediaList.adapter = adapter
        activityViewMediaList.layoutManager = LinearLayoutManager(this)
        activityViewMediaList.isNestedScrollingEnabled = false

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    data class ReferenceFile(val referenceUUID: UUID, val fileType: FileType, val time: Long, val messageUUID: UUID)

    data class CombinedReferences(val combined: List<ReferenceFile>, val date: Date)

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
