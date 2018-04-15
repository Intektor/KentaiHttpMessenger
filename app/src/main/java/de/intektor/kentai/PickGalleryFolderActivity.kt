package de.intektor.kentai

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.LinearLayoutManager
import android.view.Menu
import android.view.MenuItem
import de.intektor.kentai.kentai.KEY_CHAT_INFO
import de.intektor.kentai.kentai.KEY_FOLDER
import de.intektor.kentai.kentai.KEY_MEDIA_URL
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.view.media.MediaGroupAdapter
import kotlinx.android.synthetic.main.activity_pick_gallery_folder.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class PickGalleryFolderActivity : AppCompatActivity() {

    private lateinit var adapter: MediaGroupAdapter<GalleryMediaFile, MediaGroupAdapter.GroupedMediaFile<GalleryMediaFile>>

    private var selectingMore = false

    companion object {
        private const val ACTION_SEND_MEDIA = 0
    }

    private val selectedFiles = mutableListOf<GalleryMediaFile>()

    private lateinit var actionCallback: ActionMode.Callback

    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_gallery_folder)

        val chatInfo: ChatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)

        val folder: File = intent.getSerializableExtra(KEY_FOLDER) as File

        val calendar = Calendar.getInstance()

        val grouped = folder.listFiles().asSequence().map { GalleryMediaFile(it.lastModified(), it) }.groupBy {
            calendar.timeInMillis = it.time
            calendar.get(Calendar.MONTH) to calendar.get(Calendar.YEAR)
        }.map { GalleryMediaFileGroup(Date(it.value.first().time), it.value) }

        val clickCallback: (GalleryMediaFile, MediaGroupAdapter.GroupedMediaFile<GalleryMediaFile>, MediaGroupAdapter.MediaViewHolder) -> Unit = { item, parent, holder ->
            if (!selectingMore) {
                val startMedia = Intent(this, SendMediaActivity::class.java)
                startMedia.putExtra(KEY_CHAT_INFO, chatInfo)
                startMedia.putParcelableArrayListExtra(KEY_MEDIA_URL, ArrayList(listOf(Uri.fromFile(item.file))))
                startActivityForResult(startMedia, ACTION_SEND_MEDIA)
            } else {
                holder.setSelected(!item.selected)

                item.selected = !item.selected

                if (item.selected) selectedFiles += item else selectedFiles -= item

                if(selectedFiles.isEmpty()) actionMode?.finish()

                actionMode?.title = getString(R.string.pick_gallery_folder_action_mode_items_selected, selectedFiles.size)
            }
        }

        val longClickCallback: (GalleryMediaFile, MediaGroupAdapter.GroupedMediaFile<GalleryMediaFile>, MediaGroupAdapter.MediaViewHolder) -> Unit = { item, parent, holder ->
            selectingMore = true

            if (actionMode == null) {
                actionMode = startSupportActionMode(actionCallback)
            }

            holder.setSelected(!item.selected)

            item.selected = !item.selected

            if (item.selected) selectedFiles += item else selectedFiles -= item

            actionMode?.title = getString(R.string.pick_gallery_folder_action_mode_items_selected, selectedFiles.size)
        }

        adapter = MediaGroupAdapter(grouped, clickCallback, longClickCallback)

        pickGalleryFolderList.adapter = adapter
        pickGalleryFolderList.layoutManager = LinearLayoutManager(this)

        actionCallback = object : ActionMode.Callback {
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.menuPickGalleryActionModeSend -> {
                        val startMedia = Intent(this@PickGalleryFolderActivity, SendMediaActivity::class.java)
                        startMedia.putExtra(KEY_CHAT_INFO, chatInfo)
                        startMedia.putParcelableArrayListExtra(KEY_MEDIA_URL, ArrayList(selectedFiles.map { Uri.fromFile(it.file) }))
                        startActivityForResult(startMedia, ACTION_SEND_MEDIA)
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
                selectingMore = false

                selectedFiles.forEach {
                    it.selected = false

                }
                selectedFiles.clear()
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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

    private class GalleryMediaFile(time: Long, file: File) : MediaGroupAdapter.MediaFile(time, file)

    private class GalleryMediaFileGroup(date: Date, combined: List<GalleryMediaFile>) : MediaGroupAdapter.GroupedMediaFile<GalleryMediaFile>(date, combined)
}
