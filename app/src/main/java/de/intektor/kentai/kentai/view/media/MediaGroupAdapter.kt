package de.intektor.kentai.kentai.view.media

import android.content.Intent
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.intektor.kentai.R
import de.intektor.kentai.ViewIndividualMediaActivity
import de.intektor.kentai.ViewMediaActivity
import de.intektor.kentai.kentai.KEY_FILE_URI
import de.intektor.kentai.kentai.KEY_MEDIA_TYPE
import de.intektor.kentai.kentai.KEY_MESSAGE_UUID
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai_http_common.reference.FileType
import java.text.SimpleDateFormat
import java.util.*

class MediaGroupAdapter(private val componentList: List<ViewMediaActivity.CombinedReferences>, private val chatUUID: UUID) : RecyclerView.Adapter<MediaGroupAdapter.MediaGroupViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaGroupViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.media_item, parent, false)
        return MediaGroupViewHolder(view)
    }

    override fun getItemCount(): Int = componentList.size

    override fun onBindViewHolder(holder: MediaGroupViewHolder, position: Int) {
        val mediaGroup = componentList[position]

        val dataFormat = SimpleDateFormat("MMMM yyyy", Locale.US)

        holder.timeView.text = dataFormat.format(mediaGroup.date)

        val subAdapter = MediaAdapter(mediaGroup.combined.sortedByDescending { it.time }, chatUUID)
        holder.gridView.layoutManager = GridLayoutManager(holder.gridView.context, 3)
        holder.gridView.adapter = subAdapter
    }

    class MediaGroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeView: TextView = itemView.findViewById(R.id.mediaItemTimeView)
        val gridView: RecyclerView = itemView.findViewById(R.id.mediaItemGridView)
    }

    class MediaAdapter(val list: List<ViewMediaActivity.ReferenceFile>, private val chatUUID: UUID) : RecyclerView.Adapter<MediaViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.image_item, parent, false)
            return MediaViewHolder(view)
        }

        override fun getItemCount(): Int = list.size

        override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
            val item = list[position]
            val referenceFile = getReferenceFile(item.referenceUUID, item.fileType, holder.image.context.filesDir, holder.image.context)
            if (item.fileType == FileType.IMAGE) {
                Picasso.with(holder.image.context)
                        .load(referenceFile)
                        .resize(400, 0)
                        .into(holder.image)
            } else if (item.fileType == FileType.VIDEO) {
                holder.videoOverlay.visibility = View.VISIBLE
                val thumbnail = ThumbnailUtils.createVideoThumbnail(referenceFile.absolutePath, MediaStore.Images.Thumbnails.MINI_KIND)
                holder.image.setImageBitmap(thumbnail)
            }
            holder.image.setOnClickListener {
                val viewMediaIntent = Intent(holder.image.context, ViewIndividualMediaActivity::class.java)
                viewMediaIntent.putExtra(KEY_FILE_URI, Uri.fromFile(referenceFile))
                viewMediaIntent.putExtra(KEY_MEDIA_TYPE, item.fileType)
                viewMediaIntent.putExtra(KEY_MESSAGE_UUID, item.messageUUID)
                holder.image.context.startActivity(viewMediaIntent)
            }
        }
    }

    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.imageItemView)
        val videoOverlay: ImageView = view.findViewById(R.id.imageItemVideoOverlay)
    }
}
