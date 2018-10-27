package de.intektor.mercury.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.GridLayoutManager
import android.view.Menu
import android.view.MenuItem
import android.view.View
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.ui.chat.adapter.chat.HeaderItemDecoration
import de.intektor.mercury.ui.util.MediaAdapter
import de.intektor.mercury.util.KEY_CHAT_INFO
import de.intektor.mercury.util.KEY_FOLDER
import de.intektor.mercury.util.KEY_MEDIA_URL
import kotlinx.android.synthetic.main.activity_pick_gallery_folder.*
import java.io.File
import java.util.*
import kotlin.collections.ArrayList

class PickGalleryFolderActivity : AppCompatActivity() {

    private lateinit var adapter: MediaAdapter<GalleryMediaFile>

    private var selectingMore = false

    companion object {
        private const val ACTION_SEND_MEDIA = 0
    }

    private val selectedFiles = mutableListOf<GalleryMediaFile>()

    private lateinit var actionCallback: ActionMode.Callback

    private var actionMode: ActionMode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_pick_gallery_folder)

        val chatInfo: ChatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)

        val folder: File = intent.getSerializableExtra(KEY_FOLDER) as File

        val calendar = Calendar.getInstance()

        val grouped = folder.listFiles().asSequence().map { GalleryMediaFile(it.lastModified(), it) }.groupBy {
            calendar.timeInMillis = it.time
            calendar.get(Calendar.MONTH) to calendar.get(Calendar.YEAR)
        }.map { GalleryMediaFileGroup(Date(it.value.first().time), it.value) }.reversed()

        val clickCallback: (GalleryMediaFile, MediaAdapter.MediaViewHolder<GalleryMediaFile>) -> Unit = { item, holder ->
            if (!selectingMore) {
                val startMedia = Intent(this, SendMediaActivity::class.java)
                startMedia.putExtra(KEY_CHAT_INFO, chatInfo)
                startMedia.putParcelableArrayListExtra(KEY_MEDIA_URL, ArrayList(listOf(Uri.fromFile(item.file))))
                startActivityForResult(startMedia, ACTION_SEND_MEDIA)
            } else {
                holder.setSelected(!item.selected)

                item.selected = !item.selected

                if (item.selected) selectedFiles += item else selectedFiles -= item

                if (selectedFiles.isEmpty()) actionMode?.finish()

                actionMode?.title = getString(R.string.pick_gallery_folder_action_mode_items_selected, selectedFiles.size)
            }
        }

        val longClickCallback: (GalleryMediaFile, MediaAdapter.MediaViewHolder<GalleryMediaFile>) -> Unit = { item, holder ->
            selectingMore = true

            if (actionMode == null) {
                actionMode = startSupportActionMode(actionCallback)
            }

            holder.setSelected(!item.selected)

            item.selected = !item.selected

            if (item.selected) selectedFiles += item else selectedFiles -= item

            actionMode?.title = getString(R.string.pick_gallery_folder_action_mode_items_selected, selectedFiles.size)
        }

        val actList = mutableListOf<Any>()
        grouped.forEach {
            actList += MediaAdapter.MediaFileHeader(it.date.time)
            actList.addAll(it.combined.reversed())
        }

        adapter = MediaAdapter(actList, clickCallback, longClickCallback)

        pickGalleryFolderList.adapter = adapter

        val layoutManager = GridLayoutManager(this, 5)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (actList[position]) {
                    is MediaAdapter.MediaFileHeader -> 5
                    is GalleryMediaFile -> 1
                    else -> throw IllegalArgumentException()
                }
            }
        }

        pickGalleryFolderList.layoutManager = layoutManager

        pickGalleryFolderList.addItemDecoration(HeaderItemDecoration(pickGalleryFolderList, object : HeaderItemDecoration.StickyHeaderInterface {
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

        supportActionBar?.title = folder.name
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

    private class GalleryMediaFile(time: Long, file: File) : MediaAdapter.MediaFile(time, file)

    private class GalleryMediaFileGroup(val date: Date, val combined: List<GalleryMediaFile>)
}
