package de.intektor.mercury.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.intektor.mercury.R
import de.intektor.mercury.android.getChatInfoExtra
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.ChatUtil
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.media.MediaFile
import de.intektor.mercury.media.ThumbnailUtil
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.support.FragmentViewImage
import de.intektor.mercury.ui.support.FragmentViewImageResetAdapter
import kotlinx.android.synthetic.main.activity_send_media.*

class SendMediaActivity : AppCompatActivity() {

    private lateinit var smallMediaAdapter: SmallMediaAdapter

    private var mediaMap = mutableMapOf<MediaPreview, MediaData>()

    private var currentlySelected: Int = 0

    companion object {
        private const val EXTRA_CHAT_INFO = "de.intektor.mercury.EXTRA_CHAT_INFO"
        private const val EXTRA_FILES = "de.intektor.mercury.EXTRA_FILES"

        fun createIntent(context: Context, chatInfo: ChatInfo, files: List<MediaFile>): Intent {
            return Intent(context, SendMediaActivity::class.java)
                    .putExtra(EXTRA_CHAT_INFO, chatInfo)
                    .putExtra(EXTRA_FILES, ArrayList(files))
        }


        fun getData(intent: Intent): Holder {
            val chatInfo = intent.getChatInfoExtra(EXTRA_CHAT_INFO)
            val files = intent.getSerializableExtra(EXTRA_FILES) as ArrayList<MediaFile>
            return Holder(chatInfo, files)
        }

        data class Holder(val chatInfo: ChatInfo, val itemIds: List<MediaFile>)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_send_media)

        val (chatInfo, files) = getData(intent)

        supportActionBar?.title = getString(R.string.send_media_activity_title, ChatUtil.getChatName(this, mercuryClient().dataBase, chatInfo.chatUUID))

        val media = files.map { MediaPreview(it, false) }

        media.forEach { mediaMap[it] = MediaData(it.file, "", false) }

        media[0].selected = true

        activity_send_media_iv_send.setOnClickListener {
            val selected = media.first { mediaWrapper -> mediaWrapper.selected }
            mediaMap[selected]?.text = activitySendMediaInput.text.toString()
            mediaMap[selected]?.gif = activitySendMediaVideoGifButton.isSelected

            setResult(Activity.RESULT_OK, ChatActivity.ActionPickMedia.createIntent(
                    mediaMap.map { mutableMediaData ->
                        ChatActivity.ActionPickMedia.MediaToSend(
                                mutableMediaData.value.mediaFile,
                                mutableMediaData.value.text,
                                mutableMediaData.value.gif)
                    })
            )
            finish()
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

        val pagerAdapter = object : FragmentViewImageResetAdapter(supportFragmentManager) {
            override fun getItem(position: Int): Fragment = FragmentViewImage.create(media[position].file)

            override fun getCount(): Int = files.size

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                setCurrentMedia(position, media)

                activitySendMediaOther.smoothScrollToPosition(position)
            }
        }

        activity_send_media_vp_content.adapter = pagerAdapter
        activity_send_media_vp_content.addOnPageChangeListener(pagerAdapter)

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
            val view = LayoutInflater.from(parent.context).inflate(R.layout.image_item, parent, false)
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
        val thumbnail: ImageView = view.findViewById(R.id.fragment_image_item_iv_content)
        val video: CardView = view.findViewById(R.id.fragment_image_item_cv_video_overlay)
        val selected: ImageView = view.findViewById(R.id.fragment_image_item_iv_background_selected)
    }

    private data class MediaPreview(val file: MediaFile, var selected: Boolean)

    class MediaData(val mediaFile: MediaFile, var text: String, var gif: Boolean)
}
