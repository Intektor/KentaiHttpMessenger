package de.intektor.kentai.kentai

import android.content.Context
import android.graphics.Bitmap
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.util.*

fun getProfilePicture(userUUID: UUID, context: Context): File =
        File(context.filesDir, "profile_pictures/$userUUID")

fun hasProfilePicture(userUUID: UUID, context: Context): Boolean = getProfilePicture(userUUID, context).exists()

fun setProfilePicture(bitmap: Bitmap, userUUID: UUID, context: Context) {
    File(context.filesDir, "profile_pictures").mkdirs()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, File(context.filesDir, "profile_pictures/$userUUID").outputStream())
}

fun requestProfilePicture(userUUID: UUID, context: Context) {
    val requestBody = object : RequestBody() {
        override fun contentType(): MediaType? = MediaType.parse("application/octet-stream")

        override fun writeTo(sink: BufferedSink) {

        }

    }
}