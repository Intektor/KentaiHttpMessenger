package de.intektor.mercury.ui

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import de.intektor.mercury.R
import de.intektor.mercury.android.getChatInfoExtra
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.android.loadThumbnail
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.util.KEY_CHAT_INFO
import de.intektor.mercury.util.KEY_FOLDER
import kotlinx.android.synthetic.main.activity_pick_gallery.*
import java.io.File

class PickGalleryActivity : AppCompatActivity(), SearchView.OnQueryTextListener {

    private val totalList = mutableListOf<GalleryFolder>()
    private val currentList = mutableListOf<GalleryFolder>()

    private lateinit var adapter: GalleryFolderAdapter

    companion object {
        private const val ACTION_PICK_FROM_GALLERY_FOLDER = 0

        private const val EXTRA_CHAT_INFO = "de.intektor.mercury.EXTRA_CHAT_INFO"

        fun createIntent(context: Context, chatInfo: ChatInfo): Intent {
            return Intent(context, PickGalleryActivity::class.java)
                    .putExtra(EXTRA_CHAT_INFO, chatInfo)
        }

        private fun getData(intent: Intent): Holder {
            val chatInfo = intent.getChatInfoExtra(EXTRA_CHAT_INFO)
            return Holder(chatInfo)
        }

        private data class Holder(val chatInfo: ChatInfo)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_pick_gallery)

        val (chatInfo) = getData(intent)

        adapter = GalleryFolderAdapter(currentList, this, chatInfo)

        activityPickGalleryFolderList.adapter = adapter
        activityPickGalleryFolderList.layoutManager = GridLayoutManager(this, 2)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()

        currentList.clear()
        totalList.clear()

        val fileUri = MediaStore.Files.getContentUri("external")
        val parentDirectories = contentResolver.query(fileUri,
                arrayOf("DISTINCT ${MediaStore.Files.FileColumns.PARENT}"),
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
                null,
                null).use { query ->
            if (query == null) {
                return@use emptyList<Long>()
            }

            val parentDirectories = mutableListOf<Long>()

            while (query.moveToNext()) {
                val x = query.getLong(0)

                if (x != 0L) {
                    parentDirectories += x
                }
            }

            return@use parentDirectories
        }

        for (galleryFolder in parentDirectories) {
            contentResolver.query(ContentUris.withAppendedId(fileUri, galleryFolder), arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.TITLE), null, null, null).use { cursor ->
                if (cursor == null) return@use

                cursor.moveToNext()

                val parentDirectory = cursor.getString(0)

                val previewPath = contentResolver.query(fileUri, arrayOf(MediaStore.MediaColumns.DATA), "${MediaStore.MediaColumns.DATA} LIKE ?", arrayOf("$parentDirectory%"), "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT 1").use firstItem@ { firstItemCursor ->
                    if (firstItemCursor == null || !firstItemCursor.moveToNext()) return@firstItem ""

                    firstItemCursor.getString(0)
                }

                totalList += GalleryFolder(previewPath, parentDirectory.substringAfterLast('/'), File(parentDirectory))
            }
        }

        currentList += totalList

        adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_pick_gallery, menu)
        val searchItem = menu.findItem(R.id.menuPickGallerySearch)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(this)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        when (requestCode) {
            ACTION_PICK_FROM_GALLERY_FOLDER -> {
                if (data != null) {
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

    override fun onQueryTextSubmit(query: String): Boolean {
        currentList.clear()
        currentList += totalList.filter { it.label.contains(query, true) }
        adapter.notifyDataSetChanged()
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        currentList.clear()
        currentList += totalList.filter { it.label.contains(newText, true) }
        adapter.notifyDataSetChanged()
        return true
    }

    private class GalleryFolderAdapter(private val folders: List<GalleryFolder>, private val activity: Activity, private val chatInfo: ChatInfo) : RecyclerView.Adapter<GalleryFolderViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryFolderViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.gallery_folder_item, parent, false)
            return GalleryFolderViewHolder(view)
        }

        override fun getItemCount(): Int = folders.size

        override fun onBindViewHolder(holder: GalleryFolderViewHolder, position: Int) {
            val item = folders[position]

            holder.label.text = item.label
            if (item.previewPath.isNotEmpty()) {
                loadThumbnail(File(item.previewPath), activity, holder.image)
            }
            val clickListener = View.OnClickListener {
                val viewMediaFolderActivity = Intent(activity, PickGalleryFolderActivity::class.java)
                viewMediaFolderActivity.putExtra(KEY_FOLDER, item.folder)
                viewMediaFolderActivity.putExtra(KEY_CHAT_INFO, chatInfo)
                activity.startActivityForResult(viewMediaFolderActivity, ACTION_PICK_FROM_GALLERY_FOLDER)
            }

            holder.itemView.setOnClickListener(clickListener)
            holder.image.setOnClickListener(clickListener)
            holder.label.setOnClickListener(clickListener)
        }
    }

    private class GalleryFolderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.galleryFolderPreview)
        val label: TextView = view.findViewById(R.id.galleryFolderLabel)
    }

    private data class GalleryFolder(val previewPath: String, val label: String, val folder: File)
}
