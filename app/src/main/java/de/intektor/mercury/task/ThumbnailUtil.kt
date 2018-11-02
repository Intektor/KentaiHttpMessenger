package de.intektor.mercury.task

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import de.intektor.mercury_common.reference.FileType
import java.io.File
import java.lang.UnsupportedOperationException

object ThumbnailUtil {

    private const val SCHEME_THUMBNAIL = "thumbnail"

    fun loadThumbnail(previewFile: PreviewFile, target: ImageView, kind: Int) {
        val uri = Uri.Builder()
                .scheme(SCHEME_THUMBNAIL)
                .path("${previewFile.id}/${previewFile.mediaType}/$kind")
                .build()

        Picasso.get().load(uri).into(target)
    }

    fun createThumbnailRequestHandler(context: Context) = object : RequestHandler() {
        override fun canHandleRequest(data: Request?): Boolean = data?.uri?.scheme == SCHEME_THUMBNAIL

        override fun load(request: Request, networkPolicy: Int): Result? {
            val path = request.uri.path ?: return null

            val parts = path.split("/")

            val id = parts[1].toLong()
            val mediaType = parts[2].toInt()
            val kind = parts[3].toInt()

            val thumbnail = when (mediaType) {
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> {
                    MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, kind, BitmapFactory.Options())
                }
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> {
                    MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id, kind, BitmapFactory.Options())
                }
                else -> throw UnsupportedOperationException("Can't load thumbnail of mediaType=$mediaType")
            }

            return Result(thumbnail, Picasso.LoadedFrom.DISK)
        }
    }

    data class PreviewFile(val id: Long, val mediaType: Int)

    fun createThumbnail(file: File, fileType: FileType): ByteArray {
        return byteArrayOf()
    }
}