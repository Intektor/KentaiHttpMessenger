package de.intektor.kentai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
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
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.chat.ChatInfo
import kotlinx.android.synthetic.main.activity_pick_gallery.*
import java.io.File

class PickGalleryActivity : AppCompatActivity(), SearchView.OnQueryTextListener {

    private val totalList = mutableListOf<GalleryFolder>()
    private val currentList = mutableListOf<GalleryFolder>()
    private lateinit var adapter: GalleryFolderAdapter

    private companion object {
        private const val ACTION_PICK_FROM_GALLERY_FOLDER = 0
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pick_gallery)

        val chatInfo: ChatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)

        val folders = listOf(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES))

        totalList += folders.flatMap { it.listFiles().toList() }.filter { it.isDirectory }.filter { it.listFiles().any { isImage(it) || isVideo(it) || isGif(it) } && it.name != ".thumbnails" }.map { folder ->
            val file = folder.listFiles().lastOrNull { it.isFile }
            GalleryFolder(file?.path ?: "", folder.name, folder)
        }

        currentList += totalList

        adapter = GalleryFolderAdapter(currentList, this, chatInfo)

        activityPickGalleryFolderList.adapter = adapter
        activityPickGalleryFolderList.layoutManager = GridLayoutManager(this, 2)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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
