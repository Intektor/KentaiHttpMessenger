package de.intektor.mercury.ui.media

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import de.intektor.mercury.R
import de.intektor.mercury.media.MediaFile
import de.intektor.mercury.media.MediaProvider
import de.intektor.mercury.ui.chat.adapter.chat.HeaderItemDecoration
import de.intektor.mercury.ui.util.MediaAdapter
import kotlinx.android.synthetic.main.activity_pick_gallery_folder.*
import kotlinx.android.synthetic.main.fragment_media_list.*
import org.threeten.bp.*

class FragmentListMedia : Fragment() {

    companion object {

        private const val EXTRA_MEDIA_SOURCE = "de.intektor.mercury.EXTRA_MEDIA_SOURCE"

        fun create(mediaProvider: MediaProvider<*>): FragmentListMedia {
            val fragment = FragmentListMedia()

            val bundle = Bundle()
            bundle.putSerializable(EXTRA_MEDIA_SOURCE, mediaProvider)

            fragment.arguments = bundle

            return fragment
        }

        fun getData(bundle: Bundle): Holder {
            val mediaSource = bundle.getSerializable(EXTRA_MEDIA_SOURCE) as MediaProvider<*>
            return Holder(mediaSource)
        }

        data class Holder(val mediaProvider: MediaProvider<*>)
    }

    private lateinit var adapter: MediaAdapter<GalleryMediaFile>

    private lateinit var mediaProvider: MediaProvider<*>

    private var hasReachedLimit = false

    private var actionMode = false

    private val loadedListItems = mutableListOf<Any>()

    val selectedMediaFiles = mutableListOf<MediaAdapter.MediaFileWrapper>()

    private var currentLoadMinimum = 0L

    private var callback: UserInteractionCallback? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_media_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val context = requireContext()

        val clickCallback: (GalleryMediaFile, MediaAdapter.MediaViewHolder<GalleryMediaFile>) -> Unit = { item, holder ->
            if (!actionMode) {
                callback?.selectSingleItemAndContinue(item.file)
            } else {
                holder.setSelected(!item.selected)

                item.selected = !item.selected

                if (item.selected) {
                    selectedMediaFiles += item
                    callback?.selectedItem(loadedListItems.indexOf(item), item.file, selectedMediaFiles.size)
                } else {
                    callback?.unselectItem(loadedListItems.indexOf(item), item.file, selectedMediaFiles.size)
                    selectedMediaFiles -= item
                }

                if (selectedMediaFiles.isEmpty()) callback?.finishActionMode()
            }
        }

        val longClickCallback: (GalleryMediaFile, MediaAdapter.MediaViewHolder<GalleryMediaFile>) -> Unit = { item, holder ->
            if (!actionMode) {
                callback?.activatedActionMode()
            }

            actionMode = true

            holder.setSelected(!item.selected)

            item.selected = !item.selected

            if (item.selected) {
                selectedMediaFiles += item
                callback?.selectedItem(loadedListItems.indexOf(item), item.file, selectedMediaFiles.size)
            } else {
                callback?.unselectItem(loadedListItems.indexOf(item), item.file, selectedMediaFiles.size)
                selectedMediaFiles -= item
            }
        }

        adapter = MediaAdapter(loadedListItems, clickCallback, longClickCallback)

        fragment_media_list_rv_content.adapter = adapter

        val layoutManager = GridLayoutManager(context, 5)
        layoutManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when (loadedListItems[position]) {
                    is MediaAdapter.MediaFileHeader -> 5
                    is GalleryMediaFile -> 1
                    else -> throw IllegalArgumentException()
                }
            }
        }
        fragment_media_list_rv_content.layoutManager = layoutManager

        fragment_media_list_rv_content.addItemDecoration(HeaderItemDecoration(fragment_media_list_rv_content, object : HeaderItemDecoration.StickyHeaderInterface {
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

        val lastTime = mediaProvider.getEpochSecondTimeOfLast(context)

        fragment_media_list_rv_content.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                val lastVisible = layoutManager.findLastVisibleItemPosition()

                if (lastVisible > loadedListItems.size - 15 && !hasReachedLimit) {
                    val result = loadMore(lastTime)

                    insertItemsIntoList(result)

                    hasReachedLimit = result.reachedLimit
                }
            }
        })

        fragment_media_list_rv_content.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val now = LocalDate.now(Clock.systemDefaultZone())

                //First day of this month
                val minimum = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond

                currentLoadMinimum = minimum

                var totalAdded = 0

                //We load all images from the start of this month till now
                val latest = mediaProvider.loadMediaFiles(context, minimum, Clock.systemDefaultZone().instant().epochSecond)

                insertItemsIntoList(LoadResult(minimum, latest, latest.last().epochSecondAdded == lastTime))

                totalAdded += latest.size

                //We load until the screen is filled so it doesn't look empty, this is just an approximation because we don't calculate the header size
                while (totalAdded / 5 * resources.displayMetrics.density * 82 < fragment_media_list_rv_content.height) {
                    val result = loadMore(lastTime)

                    hasReachedLimit = result.reachedLimit

                    insertItemsIntoList(result)

                    totalAdded += result.loaded.size

                    if (hasReachedLimit) break
                }
                fragment_media_list_rv_content.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    private fun insertItemsIntoList(result: LoadResult) {
        if (result.loaded.isNotEmpty()) {
            val start = loadedListItems.size
            loadedListItems += MediaAdapter.MediaFileHeader(result.minimumEpochSecond)
            loadedListItems.addAll(result.loaded.map { GalleryMediaFile(it) })

            adapter.notifyItemRangeInserted(start, result.loaded.size + 1)
        }
    }

    private fun loadMore(lastTime: Long): LoadResult {
        val maximum = LocalDateTime.ofInstant(Instant.ofEpochSecond(currentLoadMinimum), ZoneId.systemDefault()).toLocalDate()

        val minimum = maximum.minusMonths(1)

        val minimumEpochSecond = minimum.atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond

        currentLoadMinimum = minimumEpochSecond

        val result = mediaProvider.loadMediaFiles(requireContext(), minimumEpochSecond, maximum.atStartOfDay(ZoneId.systemDefault()).toInstant().epochSecond)

        val reachedLimit = result.isNotEmpty() && result.last().epochSecondAdded == lastTime

        return LoadResult(minimumEpochSecond, result, reachedLimit)
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        if (context is UserInteractionCallback) {
            callback = context
        }
    }

    override fun setArguments(args: Bundle?) {
        if (args != null) {
            val (mediaSource) = getData(args)
            this.mediaProvider = mediaSource
        }
    }

    fun cancelActionMode() {
        actionMode = false

        callback?.finishActionMode()

        selectedMediaFiles.forEach {
            it.selected = false

            adapter.notifyItemChanged(loadedListItems.indexOf(it))
        }

        selectedMediaFiles.clear()
    }

    private class GalleryMediaFile(file: MediaFile) : MediaAdapter.MediaFileWrapper(file)

    private data class LoadResult(val minimumEpochSecond: Long, val loaded: List<MediaFile>, val reachedLimit: Boolean)

    interface UserInteractionCallback {
        fun activatedActionMode()

        fun finishActionMode()

        fun selectedItem(index: Int, mediaFile: MediaFile, totalSelected: Int)

        fun unselectItem(index: Int, mediaFile: MediaFile, totalSelected: Int)

        fun selectSingleItemAndContinue(mediaFile: MediaFile)
    }
}