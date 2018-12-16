package de.intektor.mercury.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury_common.util.toUUID
import java.io.ByteArrayOutputStream
import java.io.File

object ThumbnailUtil {

    private const val SCHEME_THUMBNAIL_EXTERNAL = "thumbnail_external"
    private const val SCHEME_THUMBNAIL_REFERENCE = "thumbnail_reference"

    /**
     * @param kind when kind is either [MediaStore.Images.Thumbnails.MINI_KIND] or [MediaStore.Images.Thumbnails.MICRO_KIND], this will use the default android [MediaStore.Images.Thumbnails.getThumbnail] way, if its [MediaStore.Images.Thumbnails.FULL_SCREEN_KIND]
     * it will use [android.media.ThumbnailUtils.createVideoThumbnail] for video files and load the file via [android.graphics.BitmapFactory.decodeFile] for images
     */
    fun loadThumbnail(mediaFile: MediaFile, target: ImageView, kind: Int, placeholder: Drawable? = null) {
        val uri = when (mediaFile) {
            is ExternalStorageFile -> Uri.Builder()
                    .scheme(SCHEME_THUMBNAIL_EXTERNAL)
                    .path("${mediaFile.id}/${mediaFile.mediaType}/$kind")
                    .build()
            is ReferenceFile -> Uri.Builder()
                    .scheme(SCHEME_THUMBNAIL_REFERENCE)
                    .path("${mediaFile.referenceUUID}/${mediaFile.mediaType}/$kind")
                    .build()
            else -> throw UnsupportedOperationException("Can't load thumbnail for mediaFile=$mediaFile")
        }

        if (placeholder == null) Picasso.get().load(uri).into(target) else Picasso.get().load(uri).placeholder(placeholder).into(target)
    }

    fun createExternalThumbnailRequestHandler(context: Context) = object : RequestHandler() {
        override fun canHandleRequest(data: Request?): Boolean = data?.uri?.scheme == SCHEME_THUMBNAIL_EXTERNAL

        override fun load(request: Request, networkPolicy: Int): Result? {
            val path = request.uri.path ?: return null

            val parts = path.split("/")

            val id = parts[1].toLong()
            val mediaTypeFromInput = parts[2].toInt()
            val kind = parts[3].toInt()

            val mediaType = if (mediaTypeFromInput != MediaType.MEDIA_TYPE_NONE) mediaTypeFromInput else {
                val mime = context.contentResolver.query(MediaStore.Files.getContentUri("external"),
                        arrayOf(MediaStore.MediaColumns.MIME_TYPE),
                        "${MediaStore.MediaColumns._ID} = ?",
                        arrayOf("$id"),
                        null).use { cursor ->
                    if (cursor == null || !cursor.moveToNext()) ""
                    cursor.getString(0)
                }

                MediaType.getMediaTypeFromMime(mime)
            }

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

    fun createReferenceThumbnailRequestHandler(context: Context) = object : RequestHandler() {
        override fun canHandleRequest(data: Request?): Boolean = data?.uri?.scheme == SCHEME_THUMBNAIL_REFERENCE

        override fun load(request: Request, networkPolicy: Int): Result? {
            val path = request.uri.path ?: return null

            val parts = path.split("/")

            val referenceUUID = parts[1].toUUID()
            val mediaType = parts[2].toInt()
            val kind = parts[3].toInt()

            val cacheFile = File(context.cacheDir, "$referenceUUID-$kind.png")

            val writeBitmapToCache = { bitmap: Bitmap ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, cacheFile.outputStream())
            }

            val createScaledBitmap = cSB@{ bitmap: Bitmap, kind: Int ->
                if (kind == MediaStore.Images.Thumbnails.FULL_SCREEN_KIND) return@cSB bitmap

                val (width, height) = when (kind) {
                    MediaStore.Images.Thumbnails.MINI_KIND -> 512 to 384
                    MediaStore.Images.Thumbnails.MICRO_KIND -> 96 to 96
                    else -> throw UnsupportedOperationException()
                }

                ThumbnailUtils.extractThumbnail(bitmap, width, height)
            }

            val thumbnail = if (cacheFile.exists()) {
                BitmapFactory.decodeFile(cacheFile.path)
            } else {
                val referenceFile = ReferenceUtil.getFileForReference(context, referenceUUID)

                when (mediaType) {
                    MediaType.MEDIA_TYPE_IMAGE -> {
                        val originalBitmap = BitmapFactory.decodeFile(referenceFile.path)
                        val scaled = createScaledBitmap(originalBitmap, kind)

                        writeBitmapToCache(scaled)

                        scaled
                    }
                    MediaType.MEDIA_TYPE_VIDEO -> {
                        val original = ThumbnailUtils.createVideoThumbnail(referenceFile.path, kind)

                        writeBitmapToCache(original)

                        original
                    }
                    else -> throw UnsupportedOperationException("Can't load thumbnail with mediaType=$mediaType")
                }
            }
            return Result(thumbnail, Picasso.LoadedFrom.DISK)
        }
    }

    fun createThumbnail(file: File, mediaType: Int): ByteArray {
        val byteOut = ByteArrayOutputStream()
        when (mediaType) {
            MediaType.MEDIA_TYPE_IMAGE -> {
                Bitmap.createScaledBitmap(BitmapFactory.decodeStream(file.inputStream()), 20, 20, false).compress(Bitmap.CompressFormat.JPEG, 50, byteOut)
            }
            MediaType.MEDIA_TYPE_VIDEO -> {
                Bitmap.createScaledBitmap(ThumbnailUtils.createVideoThumbnail(file.path, MediaStore.Video.Thumbnails.FULL_SCREEN_KIND), 20, 20, false).compress(Bitmap.CompressFormat.JPEG, 50, byteOut)
            }
            else -> throw UnsupportedOperationException("Can't load thumbnail with mediaType=$mediaType")
        }
        return byteOut.toByteArray()
    }
}