package de.intektor.kentai.kentai.view.media

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import de.intektor.kentai.R
import de.intektor.kentai.kentai.android.AViewHolder
import de.intektor.kentai.kentai.isVideo
import de.intektor.kentai.kentai.loadThumbnail
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MediaAdapter<T : MediaAdapter.MediaFile>(
        private val componentList: List<Any>, private val clickCallback: (T, MediaViewHolder<T>) -> Unit,
        private val longClickCallback: (T, MediaViewHolder<T>) -> Unit) : RecyclerView.Adapter<AViewHolder<Any>>() {

    companion object {
        private const val MEDIA_FILE_ITEM = 0
        private const val MEDIA_HEADER_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (componentList[position]) {
            is MediaAdapter.MediaFile -> MEDIA_FILE_ITEM
            is MediaFileHeader -> MEDIA_HEADER_ITEM
            else -> throw IllegalArgumentException("Unknown item type at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AViewHolder<Any> {
        return when (viewType) {
            MEDIA_FILE_ITEM -> MediaViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.image_item, parent, false), clickCallback, longClickCallback)
            MEDIA_HEADER_ITEM -> MediaHeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.media_group_header, parent, false))
            else -> throw IllegalArgumentException()
        } as AViewHolder<Any>
    }

    override fun getItemCount(): Int = componentList.size

    override fun onBindViewHolder(holder: AViewHolder<Any>, position: Int) {
        val mediaGroup = componentList[position]
        holder.bind(mediaGroup)
    }

    class MediaHeaderViewHolder(itemView: View) : AViewHolder<MediaFileHeader>(itemView) {
        private val timeView: TextView = itemView.findViewById(R.id.mediaItemTimeView)

        override fun bind(item: MediaFileHeader) {
            val dataFormat = SimpleDateFormat("MMM yyyy", Locale.US)

            timeView.text = dataFormat.format(item.time)
        }
    }

    class MediaViewHolder<T : MediaFile>(view: View, private val clickCallback: (T, MediaViewHolder<T>) -> Unit, private val longClickCallback: (T, MediaViewHolder<T>) -> Unit) : AViewHolder<T>(view) {
        private val image: ImageView = view.findViewById(R.id.imageItemView)
        private val videoOverlay: ImageView = view.findViewById(R.id.imageItemVideoOverlay)
        private val checked: ImageView = view.findViewById(R.id.imageItemChecked)
        private val checkedOverlay: ImageView = view.findViewById(R.id.imageItemDarker)

        fun setSelected(selected: Boolean) {
            checked.visibility = if (selected) View.VISIBLE else View.GONE
            checkedOverlay.visibility = if (selected) View.VISIBLE else View.GONE
        }

        override fun bind(item: T) {
            try {
                image.setImageDrawable(null)

                loadThumbnail(item.file, itemView.context, image)

                videoOverlay.visibility = if (isVideo(item.file)) View.VISIBLE else View.GONE

                setSelected(item.selected)

                image.setOnClickListener {
                    clickCallback.invoke(item, this)
                }

                image.setOnLongClickListener {
                    longClickCallback.invoke(item, this)
                    return@setOnLongClickListener true
                }
            } catch (t: Throwable) {
                Toast.makeText(itemView.context, t.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    open class MediaFile(val time: Long, val file: File, var selected: Boolean = false)

    class MediaFileHeader(val time: Long)
}
