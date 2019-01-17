package de.intektor.mercury.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.provider.MediaStore
import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury_common.util.toUUID
import okio.Okio
import java.io.ByteArrayOutputStream
import java.io.File

object ThumbnailUtil {

    private const val SCHEME_THUMBNAIL_EXTERNAL = "thumbnail_external_mercury"
    private const val SCHEME_THUMBNAIL_REFERENCE = "thumbnail_reference"

    /**
     * @param kind when kind is either [MediaStore.Images.Thumbnails.MINI_KIND] or [MediaStore.Images.Thumbnails.MICRO_KIND], this will use the default android [MediaStore.Images.Thumbnails.getThumbnail] way, if its [MediaStore.Images.Thumbnails.FULL_SCREEN_KIND]
     * it will use [android.media.ThumbnailUtils.createVideoThumbnail] for video files and load the file via [android.graphics.BitmapFactory.decodeFile] for images
     */
    fun loadThumbnail(mediaFile: MediaFile, target: ImageView, kind: Int, placeholder: Drawable? = null) {
        val uri = when (mediaFile) {
            is ExternalStorageFile -> {
                Uri.Builder()
                        .appendPath(mediaFile.uriString)
                        .appendPath("$kind")
                        .appendPath("${mediaFile.mediaType}")
                        .appendPath("${mediaFile.storeId}")
                        .scheme(SCHEME_THUMBNAIL_EXTERNAL)
                        .build()
            }
            is ReferenceFile -> {
                Uri.Builder()
                        .scheme(SCHEME_THUMBNAIL_REFERENCE)
                        .path("${mediaFile.referenceUUID}/${mediaFile.mediaType}/$kind")
                        .build()
            }
            else -> throw UnsupportedOperationException("Can't load thumbnail for mediaFile=$mediaFile")
        }

        if (placeholder == null) Picasso.get().load(uri).into(target) else Picasso.get().load(uri).placeholder(placeholder).into(target)
    }

    fun createExternalThumbnailRequestHandler(context: Context) = object : RequestHandler() {
        override fun canHandleRequest(data: Request?): Boolean = data?.uri?.scheme == SCHEME_THUMBNAIL_EXTERNAL

        override fun load(request: Request, networkPolicy: Int): Result? {
            val uri = Uri.parse(request.uri.pathSegments[0])
            val kind = request.uri.pathSegments[1].toInt()
            val mediaType = request.uri.pathSegments[2].toInt()

            //get the id of the media file. if the id is 0 we have to load and cache the file ourselves
            val id = request.uri.pathSegments[3].toLongOrNull()

            if (id != null) {
                return if (kind == MediaStore.Images.Thumbnails.MICRO_KIND || kind == MediaStore.Images.Thumbnails.MINI_KIND) {
                    val thumbnail = when (mediaType) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> {
                            MediaStore.Images.Thumbnails.getThumbnail(context.contentResolver, id, kind, BitmapFactory.Options())
                        }
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> {
                            MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, id, kind, BitmapFactory.Options())
                        }
                        else -> throw UnsupportedOperationException("Can't load thumbnail of mediaType=$mediaType")
                    }
                    Result(thumbnail, Picasso.LoadedFrom.DISK)
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
                            Result(Okio.source(File(filePath)), Picasso.LoadedFrom.DISK)
                        }
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> {
                            Result(ThumbnailUtils.createVideoThumbnail(filePath, kind), Picasso.LoadedFrom.DISK)
                        }
                        else -> throw UnsupportedOperationException("Can't load thumbnail of mediaType=$mediaType")
                    }
                } else throw UnsupportedOperationException("Cant load thumbnail of kind=$kind")
            } else {
                val thumbnail = loadThumbnailFromCacheOrCreate(context, uri, uri.pathSegments.filter { it.matches("^[A-Za-z0-9]+\$".toRegex()) }.joinToString(separator = "-") { it }, mediaType, kind)
                return Result(thumbnail, Picasso.LoadedFrom.DISK)
            }
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

            val referenceFile = ReferenceUtil.getFileForReference(context, referenceUUID)

            return Result(loadThumbnailFromCacheOrCreate(context, Uri.fromFile(referenceFile), "$referenceUUID", mediaType, kind), Picasso.LoadedFrom.DISK)
        }
    }

    private fun loadThumbnailFromCacheOrCreate(context: Context, fileUri: Uri, id: String, mediaType: Int, kind: Int): Bitmap {
        val cacheFile = File(context.cacheDir, "$id-$kind.png")

        val writeBitmapToCache = { bitmap: Bitmap ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, cacheFile.outputStream())
        }

        val createScaledBitmap = cSB@{ bitmap: Bitmap ->
            if (kind == MediaStore.Images.Thumbnails.FULL_SCREEN_KIND) return@cSB bitmap

            val (width, height) = when (kind) {
                MediaStore.Images.Thumbnails.MINI_KIND -> 512 to 384
                MediaStore.Images.Thumbnails.MICRO_KIND -> 96 to 96
                else -> throw UnsupportedOperationException()
            }

            ThumbnailUtils.extractThumbnail(bitmap, width, height)
        }

        return if (cacheFile.exists()) {
            BitmapFactory.decodeFile(cacheFile.path)
        } else {
            when (mediaType) {
                MediaType.MEDIA_TYPE_IMAGE -> {
                    val originalBitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(fileUri))
                    val scaled = createScaledBitmap(originalBitmap)

                    writeBitmapToCache(scaled)

                    scaled
                }
                MediaType.MEDIA_TYPE_VIDEO -> {
                    val original = ThumbnailUtils.createVideoThumbnail(fileUri.path, kind)

                    writeBitmapToCache(original)

                    original
                }
                else -> throw UnsupportedOperationException("Can't load thumbnail with mediaType=$mediaType")
            }
        }
    }

    fun createThumbnail(path: String, mediaType: Int): ByteArray {
        val byteOut = ByteArrayOutputStream()
        val bitmap = when (mediaType) {
            MediaType.MEDIA_TYPE_IMAGE -> {
                val (width, height) = getBitmapDimensions(path)

                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = false
                options.inSampleSize = calculateInSampleSize(width, height, 40, 40)

                BitmapFactory.decodeFile(path, options)
            }
            MediaType.MEDIA_TYPE_VIDEO -> {
                Bitmap.createScaledBitmap(ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.FULL_SCREEN_KIND), 40, 40, false)
            }
            else -> throw UnsupportedOperationException("Can't load thumbnail with mediaType=$mediaType")
        }

        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteOut)

        bitmap.recycle()

        return byteOut.toByteArray()
    }

    fun getBitmapDimensions(path: String): Pair<Int, Int> {
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true

        BitmapFactory.decodeFile(path, options)

        return options.outWidth to options.outHeight
    }

    fun getVideoDimension(context: Context, uri: Uri): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)

        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()

        retriever.release()

        return width to height
    }

    /**
     * From https://developer.android.com/topic/performance/graphics/load-bitmap?hl=es#java
     */
    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}