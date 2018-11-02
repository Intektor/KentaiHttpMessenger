package de.intektor.mercury.ui

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.support.v7.view.ActionMode
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewTreeObserver
import de.intektor.mercury.R
import de.intektor.mercury.android.getChatInfoExtra
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.task.ThumbnailUtil
import de.intektor.mercury.ui.chat.adapter.chat.HeaderItemDecoration
import de.intektor.mercury.ui.util.MediaAdapter
import de.intektor.mercury.util.KEY_CHAT_INFO
import kotlinx.android.synthetic.main.activity_pick_gallery_folder.*
import org.threeten.bp.*

class PickGalleryFolderActivity : AppCompatActivity() {

    private lateinit var adapter: MediaAdapter<GalleryMediaFile>

    private var selectingMore = false

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

    private val loadedListItems = mutableListOf<Any>()

    private val selectedFiles = mutableListOf<GalleryMediaFile>()

    private lateinit var actionCallback: ActionMode.Callback

    private var actionMode: ActionMode? = null

    private var currentLoadTime = System.currentTimeMillis()

    private var hasReachedLimit = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this, false))

        setContentView(R.layout.activity_pick_gallery_folder)

        val (folderId, chatInfo, folderName) = getData(intent)

        val clickCallback: (GalleryMediaFile, MediaAdapter.MediaViewHolder<GalleryMediaFile>) -> Unit = { item, holder ->
            if (!selectingMore) {
                val startMedia = Intent(this, SendMediaActivity::class.java)
                startMedia.putExtra(KEY_CHAT_INFO, chatInfo)
//                startMedia.putParcelableArrayListExtra(KEY_MEDIA_URL, ArrayList(listOf(Uri.fromFile(item.file))))
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

        adapter = MediaAdapter(loadedListItems, clickCallback, longClickCallback)


        pickGalleryFolderList.adapter = adapter

        val layoutManager = GridLayoutManager(this, 5)
        layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (loadedListItems[position]) {
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
                MediaAdapter.MediaHeaderViewHolder(header).bind(loadedListItems[headerPosition] as MediaAdapter.MediaFileHeader)
            }

            override fun isHeader(itemPosition: Int): Boolean = loadedListItems[itemPosition] is MediaAdapter.MediaFileHeader
        }))

        actionCallback = object : ActionMode.Callback {
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    R.id.menuPickGalleryActionModeSend -> {
                        val startMedia = Intent(this@PickGalleryFolderActivity, SendMediaActivity::class.java)
                        startMedia.putExtra(KEY_CHAT_INFO, chatInfo)
//                        startMedia.putParcelableArrayListExtra(KEY_MEDIA_URL, ArrayList(selectedFiles.map { Uri.fromFile(it.file) }))
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

        setSupportActionBar(activity_pick_gallery_folder_tb)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val lastTime = contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns.DATE_ADDED), "${MediaStore.Files.FileColumns.PARENT} = ?",
                arrayOf("$folderId"),
                "${MediaStore.MediaColumns.DATE_ADDED} ASC LIMIT 1").use { cursor ->
            if (cursor == null || !cursor.moveToNext()) return@use System.currentTimeMillis()

            cursor.getLong(0)
        }

        pickGalleryFolderList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (lastVisible > loadedListItems.size - 15 && !hasReachedLimit) {
                    hasReachedLimit = !loadAndInsertItems(folderId, lastTime) {}
                }
            }
        })

        pickGalleryFolderList.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val now = LocalDate.now(Clock.systemDefaultZone())

                //First day of this month
                val minimum = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond

                currentLoadTime = minimum

                var totalAdded = 0

                //We load all images from the start of this month till now
                val latest = loadNextMediaFileGroup(minimum, Clock.systemDefaultZone().instant().epochSecond, folderId)

                if (latest.isNotEmpty()) {
                    loadedListItems += MediaAdapter.MediaFileHeader(minimum)
                    loadedListItems.addAll(latest)
                }
                totalAdded += latest.size

                adapter.notifyItemRangeInserted(0, 1 + latest.size)

                //We load until the screen is filled so it doesn't look empty, this is just an approximation because we don't calculate the header size
                while (totalAdded / 5 * resources.displayMetrics.density * 82 < pickGalleryFolderList.height) {
                    val moreReady = loadAndInsertItems(folderId, lastTime) { moreAdded ->
                        totalAdded += moreAdded
                    }

                    hasReachedLimit = !moreReady

                    if (!moreReady) break
                }
                pickGalleryFolderList.viewTreeObserver.removeGlobalOnLayoutListener(this)
            }
        })
    }

    private fun loadNextMediaFileGroup(minimum: Long, maximum: Long, folderId: Long): List<GalleryMediaFile> {
        return contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.DATE_ADDED),
                "${MediaStore.Files.FileColumns.PARENT} = ? AND ${MediaStore.Files.FileColumns.DATE_ADDED} > ? AND ${MediaStore.Files.FileColumns.DATE_ADDED} < ?",
                arrayOf("$folderId", "$minimum", "$maximum"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC").use { cursor ->

            if (cursor == null) return@use emptyList<GalleryMediaFile>()

            val linkedToGroup = mutableListOf<GalleryMediaFile>()

            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val mediaType = cursor.getString(1).toInt()
                val dateAdded = cursor.getLong(2)

                linkedToGroup += GalleryMediaFile(dateAdded, ThumbnailUtil.PreviewFile(id, mediaType))
            }

            return@use linkedToGroup
        }
    }

    private fun loadMore(folderId: Long, lastTime: Long, amountAdded: (Int) -> Unit): Boolean {
        val maximum = LocalDateTime.ofInstant(Instant.ofEpochSecond(currentLoadTime), ZoneId.systemDefault()).toLocalDate()

        val minimum = maximum.minusMonths(1)

        val minimumEpochSecond = minimum.atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond

        currentLoadTime = minimumEpochSecond

        val result = loadNextMediaFileGroup(minimumEpochSecond, maximum.atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond, folderId)

        if (result.isNotEmpty()) {
            loadedListItems += MediaAdapter.MediaFileHeader(minimumEpochSecond)
            loadedListItems.addAll(result)
        }
        amountAdded(result.size)

        if (result.isNotEmpty() && result.last().time == lastTime) return false

        return true
    }

    private fun loadAndInsertItems(folderId: Long, lastTime: Long, moreAdded: (Int) -> Unit): Boolean {
        return loadMore(folderId, lastTime) { added ->
            moreAdded(added)
            if (added != 0) {
                adapter.notifyItemRangeInserted(loadedListItems.size, 1 + added)
            }
        }
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

    private class GalleryMediaFile(time: Long, file: ThumbnailUtil.PreviewFile) : MediaAdapter.MediaFile(time, file)
}
