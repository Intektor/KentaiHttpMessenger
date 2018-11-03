package de.intektor.mercury.ui.support

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import androidx.fragment.app.Fragment
import com.squareup.picasso.Picasso
import de.intektor.mercury.R
import de.intektor.mercury.task.ThumbnailUtil
import de.intektor.mercury.util.setGone
import de.intektor.mercury.util.setVisible
import kotlinx.android.synthetic.main.fragment_view_image.*
import java.lang.IllegalStateException

class FragmentViewImage : Fragment() {

    companion object {

        private const val EXTRA_CONTENT = "de.intektor.mercury.EXTRA_CONTENT"

        fun create(content: ThumbnailUtil.PreviewFile): FragmentViewImage {
            val fragment = FragmentViewImage()

            val arguments = Bundle()
            arguments.putParcelable(EXTRA_CONTENT, content)
            fragment.arguments = arguments

            return fragment
        }

        fun getData(arguments: Bundle): Holder {
            val content = arguments.getParcelable<ThumbnailUtil.PreviewFile>(EXTRA_CONTENT)
                    ?: throw IllegalStateException("$EXTRA_CONTENT must not be null")
            return Holder(content)
        }

        data class Holder(val content: ThumbnailUtil.PreviewFile)
    }

    private lateinit var content: ThumbnailUtil.PreviewFile

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_view_image, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ThumbnailUtil.loadThumbnail(content, fragment_view_image_pv_content, MediaStore.Images.Thumbnails.FULL_SCREEN_KIND)

        if (content.mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) {
            fragment_view_image_pv_content.doubleTapToZoom = false
            fragment_view_image_pv_content.isZoomable = false
            fragment_view_image_pv_content.isTranslatable = false

            fragment_view_image_iv_play.setVisible()
            fragment_view_image_vv_content.setGone()

            fragment_view_image_vv_content.setOnCompletionListener {
                fragment_view_image_iv_play.setVisible()
                fragment_view_image_vv_content.setGone()
                fragment_view_image_pv_content.setVisible()
            }
        }

        fragment_view_image_iv_play.setOnClickListener {
            fragment_view_image_iv_play.setGone()
            fragment_view_image_vv_content.setVisible()
            fragment_view_image_pv_content.setGone()

            val path = requireContext().contentResolver.query(MediaStore.Files.getContentUri("external"),
                    arrayOf(MediaStore.MediaColumns.DATA),
                    "${MediaStore.Files.FileColumns._ID} = ?",
                    arrayOf("${content.id}"),
                    null).use { cursor ->
                if (cursor == null || !cursor.moveToNext()) return@use ""

                cursor.getString(0)
            }

            fragment_view_image_vv_content.setVideoPath(path)
            fragment_view_image_vv_content.start()
            fragment_view_image_vv_content.setMediaController(MediaController(context))
        }
    }

    fun reset() {
        fragment_view_image_pv_content?.reset()
    }

    override fun setArguments(args: Bundle?) {
        super.setArguments(args)

        if (args != null) {
            val (content) = getData(args)

            this.content = content
        }
    }
}