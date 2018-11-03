package de.intektor.mercury.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
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

    private const val SCHEME_THUMBNAIL_EXTERNAL = "thumbnail_external"

    /**
     * @param kind when kind is either [MediaStore.Images.Thumbnails.MINI_KIND] or [MediaStore.Images.Thumbnails.MICRO_KIND], this will use the default android [MediaStore.Images.Thumbnails.getThumbnail] way, if its [MediaStore.Images.Thumbnails.FULL_SCREEN_KIND]
     * it will use [android.media.ThumbnailUtils.createVideoThumbnail] for video files and load the file via [android.graphics.BitmapFactory.decodeFile] for images
     */
    fun loadThumbnail(mediaFile: MediaFile, target: ImageView, kind: Int) {
        val uri = when (mediaFile) {
            is ExternalStorageFile -> Uri.Builder()
                    .scheme(SCHEME_THUMBNAIL_EXTERNAL)
                    .path("${mediaFile.id}/${mediaFile.mediaType}/$kind")
                    .build()
            else -> throw UnsupportedOperationException("Can't load thumbnail for mediaFile=$mediaFile")
        }

        Picasso.get().load(uri).into(target)
    }

    fun createExternalThumbnailRequestHandler(context: Context) = object : RequestHandler() {
        override fun canHandleRequest(data: Request?): Boolean = data?.uri?.scheme == SCHEME_THUMBNAIL_EXTERNAL

        override fun load(request: Request, networkPolicy: Int): Result? {
            val path = request.uri.path ?: return null

            val parts = path.split("/")

            val id = parts[1].toLong()
            val mediaType = parts[2].toInt()
            val kind = parts[3].toInt()

            val thumbnail = if (kind == MediaStore.Images.Thumbnails.MICRO_KIND || kind == MediaStore.Images.Thumbnails.MINI_KIND) {
                when (mediaType) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> {
                        MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, kind, BitmapFactory.Options())
                    }
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> {
                        MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id, kind, BitmapFactory.Options())
                    }
                    else -> throw UnsupportedOperationException("Can't load thumbnail of mediaType=$mediaType")
                }
            } else if (kind == MediaStore.Images.Thumbnails.FULL_SCREEN_KIND) {
                val filePath = context.contentResolver.query(MediaStore.Files.getContentUri("external"),
                        arrayOf(MediaStore.MediaColumns.DATA),
                        "${MediaStore.MediaColumns._ID} = ?",
                        arrayOf("$id"),
                        null).use { cursor ->
                    if (cursor == null || !cursor.moveToNext()) return@use ""

                    cursor.getString(0)
                }
                when (mediaType) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> {
                        BitmapFactory.decodeFile(filePath)
                    }
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> {
                        ThumbnailUtils.createVideoThumbnail(filePath, kind)
                    }
                    else -> throw UnsupportedOperationException("Can't load thumbnail of mediaType=$mediaType")
                }
            } else throw UnsupportedOperationException("Cant load thumbnail of kind=$kind")

            return Result(thumbnail, Picasso.LoadedFrom.DISK)
        }
    }



    fun createThumbnail(file: File, fileType: FileType): ByteArray {
        return byteArrayOf()
    }
}