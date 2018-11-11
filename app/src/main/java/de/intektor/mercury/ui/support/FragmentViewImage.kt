package de.intektor.mercury.ui.support

import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.fragment.app.Fragment
import de.intektor.mercury.R
import de.intektor.mercury.media.MediaFile
import de.intektor.mercury.media.ThumbnailUtil
import de.intektor.mercury.util.setGone
import de.intektor.mercury.util.setVisible
import kotlinx.android.synthetic.main.fragment_view_image.*

class FragmentViewImage : Fragment() {

    companion object {

        private const val EXTRA_CONTENT = "de.intektor.mercury.EXTRA_CONTENT"
        private const val EXTRA_HEADLINE = "de.intektor.mercury.EXTRA_HEADLINE"
        private const val EXTRA_SUBTEXT = "de.intektor.mercury.EXTRA_SUBTEXT"

        fun create(content: MediaFile, headline: String? = null, subtext: String? = null): FragmentViewImage {
            val fragment = FragmentViewImage()

            val arguments = Bundle()
            arguments.putSerializable(EXTRA_CONTENT, content)
            arguments.putString(EXTRA_HEADLINE, headline)
            arguments.putString(EXTRA_SUBTEXT, subtext)
            fragment.arguments = arguments

            return fragment
        }

        fun getData(arguments: Bundle): Holder {
            val content = arguments.getSerializable(EXTRA_CONTENT) as? MediaFile
                    ?: throw IllegalStateException("$EXTRA_CONTENT must not be null")

            val headline = arguments.getString(EXTRA_HEADLINE)
            val subtext = arguments.getString(EXTRA_SUBTEXT)
            return Holder(content, headline, subtext)
        }

        data class Holder(val content: MediaFile, val headline: String?, val subtext: String?)
    }

    private lateinit var content: MediaFile
    private var headline: String? = null
    private var subtext: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_view_image, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ThumbnailUtil.loadThumbnail(content, fragment_view_image_pv_content, MediaStore.Images.Thumbnails.FULL_SCREEN_KIND)

        fragment_view_image_cl_headline_parent.visibility = if (headline != null) View.VISIBLE else View.GONE
        fragment_view_image_cl_subtext_parent.visibility = if (subtext != null) View.VISIBLE else View.GONE

        fragment_view_image_tv_headline.text = headline
        fragment_view_image_tv_subtext.text = subtext

        if (content.mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            fragment_view_image_pv_content.doubleTapToZoom = false
            fragment_view_image_pv_content.isZoomable = false
            fragment_view_image_pv_content.isTranslatable = false

            fragment_view_image_cv_play_parent.setVisible()
            fragment_view_image_vv_content.setGone()

            fragment_view_image_vv_content.setOnCompletionListener {
                finishVideoPlayback()
            }
        } else {
            fragment_view_image_cv_play_parent.setGone()
        }

        fragment_view_image_iv_play.setOnClickListener {
            fragment_view_image_cv_play_parent.setGone()
            fragment_view_image_vv_content.setVisible()
            fragment_view_image_pv_content.setGone()

            fragment_view_image_vv_content.setVideoPath(content.getPath(requireContext()))
            fragment_view_image_vv_content.start()
            fragment_view_image_vv_content.setMediaController(MediaController(context))
        }

        val context = requireContext()
        if (context is BindCallback) {
            context.bind(this)
        }
    }

    fun reset() {
        fragment_view_image_pv_content?.reset()

        if (content.mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            finishVideoPlayback()
        }
    }

    private fun finishVideoPlayback() {
        fragment_view_image_cv_play_parent?.setVisible()
        fragment_view_image_vv_content?.setGone()
        fragment_view_image_pv_content?.setVisible()

        fragment_view_image_vv_content?.stopPlayback()
    }

    override fun setArguments(args: Bundle?) {
        super.setArguments(args)

        if (args != null) {
            val (content, headline, subtext) = getData(args)

            this.content = content
            this.headline = headline
            this.subtext = subtext
        }
    }

    interface BindCallback {
        fun bind(item: FragmentViewImage)
    }
}