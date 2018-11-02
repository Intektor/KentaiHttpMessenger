package de.intektor.mercury.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import de.intektor.mercury.R
import de.intektor.mercury.android.*
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.util.KEY_CHAT_INFO
import de.intektor.mercury.util.KEY_MEDIA_DATA
import de.intektor.mercury.util.KEY_MEDIA_URL
import kotlinx.android.synthetic.main.activity_send_media.*
import java.io.File

class SendMediaActivity : AppCompatActivity() {

    private lateinit var smallMediaAdapter: SmallMediaAdapter

    private var mediaMap = mutableMapOf<MediaPreview, MediaData>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_send_media)

        val chatInfo: ChatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)

        val mediaUri = intent.getParcelableArrayListExtra<Uri>(KEY_MEDIA_URL)

        supportActionBar?.title = getString(R.string.send_media_activity_title, chatInfo.chatName)

        val media: List<MediaPreview> = mediaUri.map {
            val file = if (File(it.path).exists()) File(it.path) else {
                val imagePath = getRealImagePath(it, this)
                if (imagePath.isNotBlank()) File(imagePath)
                val videoPath = getRealVideoPath(it, this)
                File(videoPath)
            }
            MediaPreview(it, file, false, false)
        }

        media[0].selected = true

        activitySendMediaButton.setOnClickListener {
            val selected = media.first { it.selected }
            mediaMap[selected] = MediaData(selected.uri, selected.file, activitySendMediaInput.text.toString(), activitySendMediaVideoGifButton.isChecked)

            val resultData = Intent()
            resultData.putParcelableArrayListExtra(KEY_MEDIA_DATA, ArrayList(mediaMap.values))
            setResult(Activity.RESULT_OK, resultData)
            finish()
        }

        smallMediaAdapter = SmallMediaAdapter(media) { item ->
            val old = media.first { it.selected }
            mediaMap[old] = MediaData(item.uri, item.file, activitySendMediaInput.text.toString(), activitySendMediaVideoGifButton.isChecked)

            old.selected = false
            smallMediaAdapter.notifyItemChanged(media.indexOf(old))

            item.selected = true
            smallMediaAdapter.notifyItemChanged(media.indexOf(item))

            setCurrentMedia(item)
        }

        activitySendMediaOther.adapter = smallMediaAdapter
        activitySendMediaOther.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        if (media.size <= 1) {
            activitySendMediaOther.visibility = View.GONE
        }

        setCurrentMedia(media[0])

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setCurrentMedia(media: MediaPreview) {
        activitySendMediaVideo.visibility = View.GONE
        activitySendMediaVideoGifButton.visibility = View.GONE

        activitySendMediaInput.setText(mediaMap[media]?.text ?: "")

        activitySendMediaVideoGifButton.isChecked = mediaMap[media]?.gif ?: false

        activitySendMediaVideoLayout.visibility = View.GONE

        //TODO
//        if (isImage(media.file)) {
//            activitySendMediaImage.setImageURI(Uri.fromFile(media.file))
//            activitySendMediaImage.visibility = View.VISIBLE
//
//        } else if (isVideo(media.file)) {
//            activitySendMediaImage.visibility = View.GONE
//            val mediaController = MediaController(this, true)
//            mediaController.setAnchorView(activitySendMediaVideo)
//            activitySendMediaVideo.setMediaController(mediaController)
//
//            activitySendMediaVideoLayout.visibility = View.VISIBLE
//
//            activitySendMediaVideoGifButton.visibility = View.VISIBLE
//
//            activitySendMediaVideo.setOnCompletionListener {
//                activitySendMediaVideoPlay.visibility = View.VISIBLE
//                activitySendMediaVideoPreview.visibility = View.VISIBLE
//
//                activitySendMediaVideo.visibility = View.GONE
//            }
//
//            loadThumbnail(media.file, this, activitySendMediaVideoPreview)
//
//            activitySendMediaVideoPlay.setOnClickListener {
//                activitySendMediaVideoPlay.visibility = View.GONE
//                activitySendMediaVideoPreview.visibility = View.GONE
//
//                activitySendMediaVideo.visibility = View.VISIBLE
//
//                activitySendMediaVideo.setVideoURI(Uri.fromFile(media.file))
//                activitySendMediaVideo.start()
//            }
//        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private class SmallMediaAdapter(private val componentList: List<MediaPreview>, private val clickResponse: (MediaPreview) -> Unit) : RecyclerView.Adapter<SmallMediaViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SmallMediaViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.media_item_small, parent, false)
            return SmallMediaViewHolder(view)
        }

        override fun getItemCount(): Int = componentList.size

        override fun onBindViewHolder(holder: SmallMediaViewHolder, position: Int) {
            val item = componentList[position]

//            loadThumbnail(item.file, holder.itemView.context, holder.thumbnail)
//
//            holder.video.visibility = if (isVideo(item.file)) View.VISIBLE else View.GONE
//            holder.selected.visibility = if (item.selected) View.VISIBLE else View.GONE

            val listener = View.OnClickListener { clickResponse.invoke(item) }

            holder.video.setOnClickListener(listener)
            holder.thumbnail.setOnClickListener(listener)
            holder.selected.setOnClickListener(listener)
        }
    }

    private class SmallMediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.mediaItemSmallThumbnail)
        val video: ImageView = view.findViewById(R.id.mediaItemSmallVideo)
        val selected: ImageView = view.findViewById(R.id.mediaItemSmallSelected)
    }

    private data class MediaPreview(val uri: Uri, val file: File, var selected: Boolean, var isGif: Boolean)

    class MediaData(val uri: Uri, val file: File, val text: String, val gif: Boolean) : Parcelable {
        constructor(parcel: Parcel) : this(
                parcel.readParcelable(Uri::class.java.classLoader),
                parcel.readSerializable() as File,
                parcel.readString(),
                parcel.readByte() != 0.toByte())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(uri, flags)
            parcel.writeSerializable(file)
            parcel.writeString(text)
            parcel.writeByte(if (gif) 1 else 0)
        }

        override fun describeContents(): Int = 0

        companion object CREATOR : Parcelable.Creator<MediaData> {
            override fun createFromParcel(parcel: Parcel): MediaData = MediaData(parcel)

            override fun newArray(size: Int): Array<MediaData?> = arrayOfNulls(size)
        }
    }
}
