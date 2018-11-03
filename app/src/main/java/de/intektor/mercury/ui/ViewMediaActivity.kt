package de.intektor.mercury.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getChatInfoExtra
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.chat.getChatMessages
import de.intektor.mercury.media.MediaProviderReference
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.ui.media.FragmentListMedia
import de.intektor.mercury.ui.util.MediaAdapter
import de.intektor.mercury.util.KEY_CHAT_INFO
import de.intektor.mercury_common.reference.FileType
import kotlinx.android.synthetic.main.activity_view_media.*
import java.lang.IllegalStateException
import java.util.*

class ViewMediaActivity : AppCompatActivity() {

    companion object {

        private const val EXTRA_CHAT_INFO = "de.intektor.mercury.EXTRA_CHAT_INFO"

        fun launch(context: Context, chatInfo: ChatInfo) {
            context.startActivity(Intent(context, ViewMediaActivity::class.java)
                    .putExtra(EXTRA_CHAT_INFO, chatInfo))
        }

        fun getData(intent: Intent): Holder {
            val chatInfo = intent.getChatInfoExtra(EXTRA_CHAT_INFO)
            return Holder(chatInfo)
        }

        data class Holder(val chatInfo: ChatInfo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this, false))

        setContentView(R.layout.activity_view_media)

        val (chatInfo) = getData(intent)

        activity_view_media_vp_content.adapter = object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return when (position) {
                    0 -> FragmentListMedia.create(MediaProviderReference(chatInfo.chatUUID))
                    else -> throw IllegalStateException()
                }
            }

            override fun getCount(): Int = 1
        }

        setSupportActionBar(activity_view_media_tb)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
