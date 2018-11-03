package de.intektor.mercury.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import de.intektor.mercury.R
import de.intektor.mercury.android.getChatInfoExtra
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.media.MediaFile
import de.intektor.mercury.media.MediaProviderExternalContent
import de.intektor.mercury.ui.media.FragmentListMedia
import kotlinx.android.synthetic.main.activity_pick_gallery_folder.*

class PickGalleryFolderActivity : AppCompatActivity(), FragmentListMedia.UserInteractionCallback {

    companion object {
        private const val ACTION_SEND_MEDIA = 0

        private const val EXTRA_FOLDER_ID = "de.intektor.mercury.EXTRA_FOLDER_ID"
        private const val EXTRA_CHAT_INFO = "de.intektor.mercury.EXTRA_CHAT_INFO"
        private const val EXTRA_FOLDER_NAME = "de.intektor.mercury.EXTRA_FOLDER_NAME"

        fun createIntent(context: Context, folderId: Long, chatInfo: ChatInfo, folderName: String): Intent {
            return Intent(context, PickGalleryFolderActivity::class.java)
                    .putExtra(EXTRA_FOLDER_ID, folderId)
                    .putExtra(EXTRA_CHAT_INFO, chatInfo)
                    .putExtra(EXTRA_FOLDER_NAME, folderName)
        }

        fun launch(context: Context, folderId: Long, chatInfo: ChatInfo, folderName: String) {
            context.startActivity(createIntent(context, folderId, chatInfo, folderName))
        }

        fun getData(intent: Intent): Holder {
            val folderId = intent.getLongExtra(EXTRA_FOLDER_ID, 0)
            val chatInfo = intent.getChatInfoExtra(EXTRA_CHAT_INFO)
            val folderName = intent.getStringExtra(EXTRA_FOLDER_NAME)

            return Holder(folderId, chatInfo, folderName)
        }

        data class Holder(val folderId: Long, val chatInfo: ChatInfo, val folderName: String)
    }


    private lateinit var actionCallback: ActionMode.Callback

    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this, false))

        setContentView(R.layout.activity_pick_gallery_folder)

        val (folderId, chatInfo, folderName) = getData(intent)

        val mediaProvider = MediaProviderExternalContent(folderId)

        val fragment = FragmentListMedia.create(mediaProvider)

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.activity_pick_gallery_folder_content, fragment)
                .commit()

        actionCallback = object : ActionMode.Callback {
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.menuPickGalleryActionModeSend -> {
                        startActivityForResult(SendMediaActivity.createIntent(this@PickGalleryFolderActivity, chatInfo, folderId, fragment.selectedMediaFiles.map { it.file }), ACTION_SEND_MEDIA)
                        return true
                    }
                }
                return false
            }

            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menuInflater.inflate(R.menu.menu_pick_gallery_action_mode, menu)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null

                fragment.cancelActionMode()
            }
        }

        setSupportActionBar(activity_pick_gallery_folder_tb)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        supportActionBar?.title = folderName
    }

    override fun activatedActionMode() {
        actionMode = startSupportActionMode(actionCallback)
    }

    override fun finishActionMode() {
        actionMode?.finish()
        actionMode = null
    }

    override fun selectedItem(index: Int, mediaFile: MediaFile, totalSelected: Int) {
        updateActionModeLabel(totalSelected)
    }

    override fun unselectItem(index: Int, mediaFile: MediaFile, totalSelected: Int) {
        updateActionModeLabel(totalSelected)
    }

    override fun selectSingleItemAndContinue(mediaFile: MediaFile) {
        val (folderId, chatInfo, _) = getData(intent)

        startActivityForResult(SendMediaActivity.createIntent(this, chatInfo, folderId, listOf(mediaFile)), PickGalleryFolderActivity.ACTION_SEND_MEDIA)
    }

    private fun updateActionModeLabel(selectedAmount: Int) {
        actionMode?.title = getString(R.string.pick_gallery_folder_action_mode_items_selected, selectedAmount)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ACTION_SEND_MEDIA -> {
                if (data != null && resultCode == Activity.RESULT_OK) {
                    setResult(Activity.RESULT_OK, data)
                    finish()
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

}
