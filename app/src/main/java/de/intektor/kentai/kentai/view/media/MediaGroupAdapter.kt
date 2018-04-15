package de.intektor.kentai.kentai.view.media

import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import de.intektor.kentai.R
import de.intektor.kentai.kentai.isVideo
import de.intektor.kentai.kentai.loadThumbnail
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MediaGroupAdapter<T : MediaGroupAdapter.MediaFile, K : MediaGroupAdapter.GroupedMediaFile<T>>(
        private val componentList: List<K>, private val clickCallback: (T, K, MediaViewHolder) -> Unit,
        private val longClickCallback: (T, K, MediaViewHolder) -> Unit) : RecyclerView.Adapter<MediaGroupAdapter.MediaGroupViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaGroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.media_item, parent, false)
        return MediaGroupViewHolder(view)
    }

    override fun getItemCount(): Int = componentList.size

    override fun onBindViewHolder(holder: MediaGroupViewHolder, position: Int) {
        val mediaGroup = componentList[position]

        val dataFormat = SimpleDateFormat("MMM yyyy", Locale.US)

        holder.timeView.text = dataFormat.format(mediaGroup.date)

        val subAdapter = MediaAdapter(mediaGroup.combined.sortedByDescending { it.time }, mediaGroup, clickCallback, longClickCallback)
        holder.gridView.layoutManager = GridLayoutManager(holder.gridView.context, 3)
        holder.gridView.adapter = subAdapter
    }

    class MediaGroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeView: TextView = itemView.findViewById(R.id.mediaItemTimeView)
        val gridView: RecyclerView = itemView.findViewById(R.id.mediaItemGridView)
    }

    class MediaAdapter<out T : MediaFile, K : GroupedMediaFile<T>>(private val list: List<T>, private val parentItem: K,
                                                                   private val clickCallback: (T, K, MediaViewHolder) -> Unit,
                                                                   private val longClickCallback: (T, K, MediaViewHolder) -> Unit) : RecyclerView.Adapter<MediaViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.image_item, parent, false)
            return MediaViewHolder(view)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
            val item = list[position]
            loadThumbnail(item.file, holder.itemView.context, holder.image)

            holder.videoOverlay.visibility = if (isVideo(item.file)) View.VISIBLE else View.GONE

            holder.setSelected(item.selected)

            holder.image.setOnClickListener {
                clickCallback.invoke(item, parentItem, holder)
            }

            holder.image.setOnLongClickListener {
                longClickCallback.invoke(item, parentItem, holder)
                return@setOnLongClickListener true
            }
        }
    }

    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imageItemView)
        val videoOverlay: ImageView = view.findViewById(R.id.imageItemVideoOverlay)
        val checked: ImageView = view.findViewById(R.id.imageItemChecked)
        val checkedOverlay: ImageView = view.findViewById(R.id.imageItemDarker)

        fun setSelected(selected: Boolean) {
            checked.visibility = if (selected) View.VISIBLE else View.GONE
            checkedOverlay.visibility = if (selected) View.VISIBLE else View.GONE
        }
    }

    open class MediaFile(val time: Long, val file: File, var selected: Boolean = false)

    open class GroupedMediaFile<out T : MediaFile>(val date: Date, val combined: List<T>)
}
