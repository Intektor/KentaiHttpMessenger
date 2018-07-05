package de.intektor.kentai.kentai

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.AsyncTask
import android.provider.MediaStore
import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestCreator
import com.squareup.picasso.RequestHandler
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
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

fun checkCameraPermission(activity: Activity, actionKey: Int): Boolean {
    if (!EasyPermissions.hasPermissions(activity, Manifest.permission.CAMERA)) {
        EasyPermissions.requestPermissions(activity, "", actionKey, Manifest.permission.CAMERA)
        return false
    }
    return true
}

fun checkRecordingPermission(activity: Activity, actionKey: Int): Boolean {
    if (!EasyPermissions.hasPermissions(activity, Manifest.permission.RECORD_AUDIO)) {
        EasyPermissions.requestPermissions(activity, "", actionKey, Manifest.permission.RECORD_AUDIO)
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
    val isImage = isImage(file)
    val isGif = isGif(file)
    val isVideo = isVideo(file)
    if (isImage) {
        Picasso.with(context).load(file).resize(200, 0).placeholder(null).into(imageView)
    } else if (isGif || isVideo) {
        videoPicasso(context).loadVideoThumbnailMini(file.path).placeholder(null).into(imageView)
    }
}

private class LoadThumbnail(private val callback: (Bitmap?) -> (Unit), val file: File, val contentResolver: ContentResolver, val kentaiClient: KentaiClient) : AsyncTask<Unit, Unit, Bitmap?>() {
    override fun doInBackground(vararg args: Unit?): Bitmap? {
        val isImage = isImage(file)

        val isGif = isGif(file)

        val isVideo = isVideo(file)

        return if (isImage) {
            val decoded = BitmapFactory.decodeFile(file.path) ?: return null
            resize(decoded, 200, 200)
        } else if (isGif || isVideo) {
            val image = ThumbnailUtils.createVideoThumbnail(file.path, MediaStore.Images.Thumbnails.MINI_KIND)
                    ?: return null
            resize(image, 200, 200)
        } else {
            null
        }
    }

    override fun onPostExecute(result: Bitmap?) {
        callback.invoke(result)
    }
}

private fun resize(image: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    if (maxHeight > 0 && maxWidth > 0) {
        val width = image.width
        val height = image.height
        val ratioBitmap = width.toFloat() / height.toFloat()
        val ratioMax = maxWidth.toFloat() / maxHeight.toFloat()

        var finalWidth = maxWidth
        var finalHeight = maxHeight
        if (ratioMax > ratioBitmap) {
            finalWidth = (maxHeight.toFloat() * ratioBitmap).toInt()
        } else {
            finalHeight = (maxWidth.toFloat() / ratioBitmap).toInt()
        }
        return Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true)
    } else {
        return image
    }
}

fun isUsingLightTheme(context: Context): Boolean {
    val sp = context.getSharedPreferences(SHARED_PREFERENCES_THEME, Context.MODE_PRIVATE)
    return sp.getBoolean(SP_IS_LIGHT_THEME_KEY, false)
}

fun getSelectedTheme(context: Context, actionBar: Boolean = true): Int {
    return if (!isUsingLightTheme(context)) {
        if (actionBar) R.style.AppTheme else R.style.AppTheme_NoActionBar
    } else {
        if (actionBar) R.style.AppThemeLight else R.style.AppThemeLight_NoActionBar
    }
}

fun setSelectedTheme(context: Context, lightTheme: Boolean) {
    val sp = context.getSharedPreferences(SHARED_PREFERENCES_THEME, Context.MODE_PRIVATE)
    val editor = sp.edit()
    editor.putBoolean(SP_IS_LIGHT_THEME_KEY, lightTheme)
    editor.apply()
}

fun getAttrDrawable(context: Context, attr: Int): Drawable {
    val a = context.theme.obtainStyledAttributes(getSelectedTheme(context), intArrayOf(attr))
    return context.resources.getDrawable(a.getResourceId(0, 0), context.theme)
}

private const val VIDEO_KIND_MINI = "videoThumbnailMini"
private const val VIDEO_KIND_FULL = "videoThumbnailFull"

fun videoPicasso(context: Context): Picasso =
        Picasso.Builder(context)
                .addRequestHandler(object : RequestHandler() {
                    override fun canHandleRequest(data: Request): Boolean = data.uri.scheme == VIDEO_KIND_MINI

                    override fun load(request: Request, networkPolicy: Int): Result {
                        val bm = ThumbnailUtils.createVideoThumbnail(request.uri.path, MediaStore.Images.Thumbnails.MINI_KIND)
                        return Result(bm, Picasso.LoadedFrom.DISK)
                    }
                })
                .addRequestHandler(object : RequestHandler() {
                    override fun canHandleRequest(data: Request): Boolean = data.uri.scheme == VIDEO_KIND_FULL

                    override fun load(request: Request, networkPolicy: Int): Result {
                        val bm = ThumbnailUtils.createVideoThumbnail(request.uri.path, MediaStore.Images.Thumbnails.FULL_SCREEN_KIND)
                        return Result(bm, Picasso.LoadedFrom.DISK)
                    }
                }).build()

fun Picasso.loadVideoThumbnailMini(path: String): RequestCreator = load("$VIDEO_KIND_MINI:$path")

fun Picasso.loadVideoThumbnailFull(path: String): RequestCreator = load("$VIDEO_KIND_FULL:$path")