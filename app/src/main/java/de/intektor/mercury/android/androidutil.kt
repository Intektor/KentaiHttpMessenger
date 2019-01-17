package de.intektor.mercury.android

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import de.intektor.mercury.R
import de.intektor.mercury.media.MediaType
import de.intektor.mercury.util.SHARED_PREFERENCES_THEME
import de.intektor.mercury.util.SP_IS_LIGHT_THEME_KEY
import de.intektor.mercury.util.getCompatDrawable
import de.intektor.mercury_common.reference.FileType


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

fun isUsingLightTheme(context: Context): Boolean {
    val sp = context.getSharedPreferences(SHARED_PREFERENCES_THEME, Context.MODE_PRIVATE)
    return sp.getBoolean(SP_IS_LIGHT_THEME_KEY, true)
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

@Deprecated("Should not use mediaType anymore and replace with MediaType")
fun FileType.mediaType(): Int {
    return when(this) {
        FileType.AUDIO -> MediaType.MEDIA_TYPE_AUDIO
        FileType.IMAGE -> MediaType.MEDIA_TYPE_IMAGE
        FileType.VIDEO -> MediaType.MEDIA_TYPE_VIDEO
        FileType.GIF -> MediaType.MEDIA_TYPE_VIDEO
    }
}