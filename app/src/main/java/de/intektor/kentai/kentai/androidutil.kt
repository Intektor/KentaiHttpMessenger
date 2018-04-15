package de.intektor.kentai.kentai

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.AsyncTask
import android.provider.MediaStore
import android.widget.ImageView
import de.intektor.kentai_http_common.reference.FileType
import pub.devrel.easypermissions.EasyPermissions
import java.io.ByteArrayOutputStream
import java.io.File


fun checkStoragePermission(activity: Activity, actionKey: Int): Boolean {
    if (!EasyPermissions.hasPermissions(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)) {
        EasyPermissions.requestPermissions(activity, "", actionKey, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE)
        return false
    }
    return true
}

fun getRealVideoPath(uri: Uri, context: Context): String {
    val projection = arrayOf(MediaStore.Video.Media.DATA)
    return context.contentResolver.query(uri, projection, null, null, null).use { cursor: Cursor? ->
        if (cursor != null) {
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            cursor.moveToNext()
            cursor.getString(columnIndex)
        } else ""
    }
}

fun getRealImagePath(uri: Uri, context: Context): String {
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    return context.contentResolver.query(uri, projection, null, null, null).use { cursor: Cursor? ->
        if (cursor != null) {
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            cursor.moveToNext()
            cursor.getString(columnIndex)
        } else ""
    }
}

fun createSmallPreviewImage(referenceFile: File, fileType: FileType): ByteArray {
    return when (fileType) {
        FileType.AUDIO -> throw IllegalArgumentException()
        FileType.IMAGE -> {
            val byteOut = ByteArrayOutputStream()
            val options = BitmapFactory.Options()
            options.inMutable = true
            val bitmap = BitmapFactory.decodeFile(referenceFile.path, options)
            bitmap.reconfigure(20, 20, Bitmap.Config.RGB_565)
            byteOut.toByteArray()
        }
        FileType.VIDEO -> {
            TODO()
        }
        FileType.GIF -> {
            TODO()
        }
    }
}

fun isImage(file: File): Boolean = when {
    file.extension.equals("jpeg", true) -> true
    file.extension.equals("jpg", true) -> true
    file.extension.equals("png", true) -> true
    file.extension.equals("bmp", true) -> true
    else -> false
}

fun isGif(file: File): Boolean = file.extension.equals("gif", true)

fun isVideo(file: File): Boolean = when {
    file.extension.equals("3gp", true) -> true
    file.extension.equals("mp4", true) -> true
    file.extension.equals("webm", true) -> true
    file.extension.equals("mkv", true) -> true
    else -> false
}

fun loadThumbnail(file: File, context: Context, imageView: ImageView) {
    LoadThumbnail({ bitmap ->
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
        } else {

        }
    }, file, context.contentResolver)
}

private class LoadThumbnail(private val callback: (Bitmap?) -> (Unit), val file: File, val contentResolver: ContentResolver) : AsyncTask<Unit, Unit, Bitmap?>() {
    override fun doInBackground(vararg args: Unit?): Bitmap? {
        val isImage = isImage(file)

        val isGif = isGif(file)

        val isVideo = isVideo(file)

        if (isImage) {
            MediaStore.Images.Thumbnails.queryMiniThumbnails(contentResolver, Uri.fromFile(file), MediaStore.Images.Thumbnails.MINI_KIND, null).use { cursor ->
                if (cursor != null && cursor.count > 0) {
                    val uri = cursor.getColumnIndex(MediaStore.Images.Thumbnails.DATA)
                    return BitmapFactory.decodeFile(cursor.getString(uri))
                }
                return null
            }
        } else if (isGif || isVideo) {
            return ThumbnailUtils.createVideoThumbnail(file.path, MediaStore.Images.Thumbnails.MINI_KIND)
        } else {
            return null
        }
    }

    override fun onPostExecute(result: Bitmap?) {
        callback.invoke(result)
    }
}
