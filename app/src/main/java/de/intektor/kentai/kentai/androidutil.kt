package de.intektor.kentai.kentai

import android.Manifest
import android.app.Activity
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import de.intektor.kentai.kentai.contacts.Contact
import pub.devrel.easypermissions.EasyPermissions
import java.security.AccessControlContext
import android.provider.MediaStore
import de.intektor.kentai_http_common.reference.FileType
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