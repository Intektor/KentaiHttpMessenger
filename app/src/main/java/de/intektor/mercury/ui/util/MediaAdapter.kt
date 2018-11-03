package de.intektor.mercury.ui.util

import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import de.intektor.mercury.R
import de.intektor.mercury.media.ThumbnailUtil
import java.text.SimpleDateFormat
import java.util.*

class MediaAdapter<T : MediaAdapter.MediaFile>(
        private val componentList: List<Any>, private val clickCallback: (T, MediaViewHolder<T>) -> Unit,
        private val longClickCallback: (T, MediaViewHolder<T>) -> Unit) : androidx.recyclerview.widget.RecyclerView.Adapter<BindableViewHolder<Any>>() {

    companion object {
        private const val MEDIA_FILE_ITEM = 0
        private const val MEDIA_HEADER_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (componentList[position]) {
            is MediaFile -> MEDIA_FILE_ITEM
            is MediaFileHeader -> MEDIA_HEADER_ITEM
            else -> throw IllegalArgumentException("Unknown item type at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindableViewHolder<Any> {
        return when (viewType) {
            MEDIA_FILE_ITEM -> MediaViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.image_item, parent, false), clickCallback, longClickCallback)
            MEDIA_HEADER_ITEM -> MediaHeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.media_group_header, parent, false))
            else -> throw IllegalArgumentException()
        } as BindableViewHolder<Any>
    }

    override fun getItemCount(): Int = componentList.size

    override fun onBindViewHolder(holder: BindableViewHolder<Any>, position: Int) {
        val mediaGroup = componentList[position]
        holder.bind(mediaGroup)
    }

    class MediaHeaderViewHolder(itemView: View) : BindableViewHolder<MediaFileHeader>(itemView) {
        private val timeView: TextView = itemView.findViewById(R.id.mediaItemTimeView)

        override fun bind(item: MediaFileHeader) {
            val dataFormat = SimpleDateFormat("MMM yyyy", Locale.getDefault())

            timeView.text = dataFormat.format(item.time * 1000)
        }
    }

    class MediaViewHolder<T : MediaFile>(view: View, private val clickCallback: (T, MediaViewHolder<T>) -> Unit, private val longClickCallback: (T, MediaViewHolder<T>) -> Unit) : BindableViewHolder<T>(view) {
        private val content: ImageView = view.findViewById(R.id.fragment_image_item_iv_content)
        private val videoOverlay: CardView = view.findViewById(R.id.fragment_image_item_cv_video_overlay)
        private val checked: ImageView = view.findViewById(R.id.fragment_image_item_iv_check)
        private val checkedOverlay: ImageView = view.findViewById(R.id.fragment_image_item_iv_dark_overlay)

        fun setSelected(selected: Boolean) {
            checked.visibility = if (selected) View.VISIBLE else View.GONE
            checkedOverlay.visibility = if (selected) View.VISIBLE else View.GONE
        }

        override fun bind(item: T) {
            try {
                ThumbnailUtil.loadThumbnail(item.file, content, MediaStore.Images.Thumbnails.MICRO_KIND)

                videoOverlay.visibility = if (item.file.mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) View.VISIBLE else View.GONE

                setSelected(item.selected)

                content.setOnClickListener {
                    clickCallback.invoke(item, this)
                }

                content.setOnLongClickListener {
                    longClickCallback.invoke(item, this)
                    return@setOnLongClickListener true
                }
            } catch (t: Throwable) {
                Toast.makeText(itemView.context, t.localizedMessage, Toast.LENGTH_SHORT).show()
            }
        }
    }

    open class MediaFile(val time: Long, val file: de.intektor.mercury.media.MediaFile, var selected: Boolean = false)

    class MediaFileHeader(val time: Long)
}
