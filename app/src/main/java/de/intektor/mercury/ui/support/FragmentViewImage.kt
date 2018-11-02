package de.intektor.mercury.ui.support

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.squareup.picasso.Picasso
import de.intektor.mercury.R
import de.intektor.mercury.task.ThumbnailUtil
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
    }

    override fun setArguments(args: Bundle?) {
        super.setArguments(args)

        if (args != null) {
            val (content) = getData(args)

            this.content = content
        }
    }
}