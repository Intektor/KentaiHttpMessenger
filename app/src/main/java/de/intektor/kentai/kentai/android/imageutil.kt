package de.intektor.kentai.kentai.android

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * @author Intektor
 */

fun addImageToGallery(filePath: String, context: Context) {
    val values = ContentValues()
    values.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
    values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
    values.put(MediaStore.MediaColumns.DATA, filePath)

    context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}

private fun saveImage(path: String, bitmap: Bitmap) {
    File(path).outputStream().use { output ->
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output)
    }
}

fun saveImageExternalKentai(name: String, bitmap: Bitmap, context: Context) {
    File(Environment.getExternalStorageDirectory().toString() + "/Pictures/Kentai/").mkdirs()
    val path = Environment.getExternalStorageDirectory().toString() + "/Pictures/Kentai/$name.jpg"
    saveImage(path, bitmap)
    addImageToGallery(path, context)
}

fun saveImageExternal9Gag(name: String, bitmap: Bitmap, context: Context) {
    File(Environment.getExternalStorageDirectory().toString() + "/Pictures/9GAG/").mkdirs()
    val path = Environment.getExternalStorageDirectory().toString() + "/Pictures/9GAG/$name.jpg"
    saveImage(path, bitmap)
    addImageToGallery(path, context)
}