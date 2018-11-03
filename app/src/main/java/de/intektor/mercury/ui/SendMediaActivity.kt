package de.intektor.mercury.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import de.intektor.mercury.R
import de.intektor.mercury.android.*
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.task.ThumbnailUtil
import de.intektor.mercury.ui.support.FragmentViewImage
import kotlinx.android.synthetic.main.activity_send_media.*
import java.io.File
import java.lang.IllegalStateException

class SendMediaActivity : AppCompatActivity() {

    private lateinit var smallMediaAdapter: SmallMediaAdapter

    private var mediaMap = mutableMapOf<MediaPreview, MediaData>()

    private var currentlySelected: Int = 0

    companion object {
        private const val EXTRA_CHAT_INFO = "de.intektor.mercury.EXTRA_CHAT_INFO"
        private const val EXTRA_FOLDER_ID = "de.intektor.mercury.EXTRA_FOLDER_ID"
        private const val EXTRA_FILES = "de.intektor.mercury.EXTRA_FILES"

        fun createIntent(context: Context, chatInfo: ChatInfo, folderId: Long, files: List<ThumbnailUtil.PreviewFile>): Intent {
            return Intent(context, SendMediaActivity::class.java)
                    .putExtra(EXTRA_CHAT_INFO, chatInfo)
                    .putExtra(EXTRA_FOLDER_ID, folderId)
                    .putExtra(EXTRA_FILES, ArrayList(files))
        }


        fun getData(intent: Intent): Holder {
            val chatInfo = intent.getChatInfoExtra(EXTRA_CHAT_INFO)
            val folderId = intent.getLongExtra(EXTRA_FOLDER_ID, 0)
            val files = intent.getParcelableArrayListExtra<ThumbnailUtil.PreviewFile>(EXTRA_FILES)
            return Holder(chatInfo, folderId, files)
        }

        data class Holder(val chatInfo: ChatInfo, val folderId: Long, val itemIds: List<ThumbnailUtil.PreviewFile>)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_send_media)

        val (chatInfo, _, files) = getData(intent)

        supportActionBar?.title = getString(R.string.send_media_activity_title, chatInfo.chatName)

        val media = files.map { MediaPreview(it, false) }.sortedByDescending { it.file.id }

        media.forEach { mediaMap[it] = MediaData(it.file, "", false) }

        media[0].selected = true

        activity_send_media_iv_send.setOnClickListener {
            val selected = media.first { it.selected }
            mediaMap[selected]?.text = activitySendMediaInput.text.toString()
            mediaMap[selected]?.gif = activitySendMediaVideoGifButton.isSelected

            //TODO
//            val resultData = Intent()
//            resultData.putParcelableArrayListExtra(KEY_MEDIA_DATA, ArrayList(mediaMap.values))
//            setResult(Activity.RESULT_OK, resultData)
//            finish()
        }

        smallMediaAdapter = SmallMediaAdapter(media) { item ->
            val index = media.indexOf(item)
            setCurrentMedia(index, media)

            activity_send_media_vp_content.setCurrentItem(index, true)
        }

        activitySendMediaOther.adapter = smallMediaAdapter
        activitySendMediaOther.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        if (media.size <= 1) {
            activitySendMediaOther.visibility = View.GONE
        }

        val fragments = mutableMapOf<Int, FragmentViewImage>()

        val pagerAdapter = object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getItem(position: Int): Fragment = FragmentViewImage.create(media[position].file)

            override fun getCount(): Int = files.size

            override fun instantiateItem(container: ViewGroup, position: Int): Any {
                val instance = super.instantiateItem(container, position)

                fragments[position] = instance as? FragmentViewImage ?: return instance

                return instance
            }

        }
        activity_send_media_vp_content.adapter = pagerAdapter

        activity_send_media_vp_content.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) = Unit

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

            override fun onPageSelected(position: Int) {
                setCurrentMedia(position, media)

                activitySendMediaOther.smoothScrollToPosition(position)

                (0 until files.size).filterNot { it == position }.forEach { fragments[it]?.reset() }
            }
        })

        setCurrentMedia(0, media)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setCurrentMedia(index: Int, media: List<MediaPreview>) {
        val previousSelection = media[currentlySelected]
        previousSelection.selected = false
        mediaMap[previousSelection]?.text = activitySendMediaInput.text.toString()
        mediaMap[previousSelection]?.gif = activitySendMediaVideoGifButton.isSelected
        smallMediaAdapter.notifyItemChanged(currentlySelected)

        activitySendMediaVideoGifButton.visibility = View.GONE

        activitySendMediaInput.setText(mediaMap[media[index]]?.text ?: "")

        activitySendMediaVideoGifButton.isChecked = mediaMap[media[index]]?.gif ?: false

        media[index].selected = true
        smallMediaAdapter.notifyItemChanged(index)

        currentlySelected = index

        activitySendMediaInput.clearFocus()
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

            ThumbnailUtil.loadThumbnail(item.file, holder.thumbnail, MediaStore.Images.Thumbnails.MICRO_KIND)

            holder.video.visibility = if (item.file.mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) View.VISIBLE else View.GONE

            holder.selected.visibility = if (item.selected) View.VISIBLE else View.GONE

            val listener = View.OnClickListener { clickResponse.invoke(item) }

            holder.video.setOnClickListener(listener)
            holder.thumbnail.setOnClickListener(listener)
            holder.selected.setOnClickListener(listener)
        }
    }

    private class SmallMediaViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.mediaItemSmallThumbnail)
        val video: ImageView = view.findViewById(R.id.mediaItemSmallVideo)
        val selected: ImageView = view.findViewById(R.id.mediaItemSmallSelected)
    }

    private data class MediaPreview(val file: ThumbnailUtil.PreviewFile, var selected: Boolean)

    class MediaData(val previewFile: ThumbnailUtil.PreviewFile, var text: String, var gif: Boolean) : Parcelable {
        constructor(parcel: Parcel) : this(
                parcel.readParcelable(ThumbnailUtil.PreviewFile::class.java.classLoader)
                        ?: throw IllegalStateException(),
                parcel.readString() ?: throw IllegalStateException(),
                parcel.readByte() != 0.toByte())

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeParcelable(previewFile, flags)
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
