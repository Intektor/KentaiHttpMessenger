package de.intektor.mercury.android

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.AsyncTask
import android.provider.MediaStore
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestCreator
import com.squareup.picasso.RequestHandler
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.util.SHARED_PREFERENCES_THEME
import de.intektor.mercury.util.SP_IS_LIGHT_THEME_KEY
import de.intektor.mercury.util.getCompatDrawable
import de.intektor.mercury_common.reference.FileType
import java.io.ByteArrayOutputStream
import java.io.File


fun checkWriteStoragePermission(activity: Activity, actionKey: Int): Boolean = checkPermission(activity, actionKey, Manifest.permission.WRITE_EXTERNAL_STORAGE)

fun checkCameraPermission(activity: Activity, actionKey: Int): Boolean = checkPermission(activity, actionKey, Manifest.permission.CAMERA)

fun checkRecordingPermission(activity: Activity, actionKey: Int): Boolean = checkPermission(activity, actionKey, Manifest.permission.RECORD_AUDIO)

fun checkPermission(activity: Activity, actionKey: Int, permission: String): Boolean {
    if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(activity, arrayOf(permission), actionKey)
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
    return context.resources.getCompatDrawable(a.getResourceId(0, 0), context.theme)
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