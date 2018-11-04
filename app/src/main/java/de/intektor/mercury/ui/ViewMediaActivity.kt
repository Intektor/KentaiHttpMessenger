package de.intektor.mercury.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import de.intektor.mercury.R
import de.intektor.mercury.android.getChatInfoExtra
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.media.MediaFile
import de.intektor.mercury.media.MediaProviderReference
import de.intektor.mercury.ui.media.FragmentListMedia
import kotlinx.android.synthetic.main.activity_view_media.*

class ViewMediaActivity : AppCompatActivity(), FragmentListMedia.UserInteractionCallback {

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

    private var actionMode: ActionMode? = null

    private var mediaFragment: FragmentListMedia? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this, false))

        setContentView(R.layout.activity_view_media)

        val (chatInfo) = getData(intent)

        setSupportActionBar(activity_view_media_tb)

        activity_view_media_tab_layout.setupWithViewPager(activity_view_media_vp_content)

        val mediaProvider = MediaProviderReference(chatInfo.chatUUID)

        activity_view_media_vp_content.adapter = object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return when (position) {
                    0 -> {
                        FragmentListMedia.create(mediaProvider)
                    }
                    else -> throw IllegalStateException()
                }
            }

            override fun getCount(): Int = 1

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val fragment = super.instantiateItem(container, position)

                if (position == 0 && fragment is FragmentListMedia) {
                    mediaFragment = fragment
                }

                return fragment
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun activatedActionMode() {
        actionMode = startSupportActionMode(ViewMediaActionModeCallback())
    }

    override fun finishActionMode() {
        actionMode?.finish()
    }

    override fun selectedItem(index: Int, mediaFile: MediaFile, totalSelected: Int) {
        actionMode?.title = getString(R.string.activity_view_chat_media_selected, totalSelected)
    }

    override fun unselectItem(index: Int, mediaFile: MediaFile, totalSelected: Int) {
        actionMode?.title = getString(R.string.activity_view_chat_media_selected, totalSelected)
    }

    override fun selectSingleItemAndContinue(mediaFile: MediaFile) {

    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private inner class ViewMediaActionModeCallback : ActionMode.Callback {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.menu_activity_view_chat_media_share -> {

                }
            }
            return false
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.menu_activity_view_chat_media, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

        override fun onDestroyActionMode(mode: ActionMode) {
            actionMode = null

            mediaFragment?.cancelActionMode()
        }
    }
}
