package de.intektor.kentai.kentai

import android.content.Context
import android.net.Uri
import okhttp3.MediaType
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

fun getProfilePicture(userUUID: UUID, context: Context): File =
        File(context.filesDir, "profile_pictures/$userUUID")

fun hasProfilePicture(userUUID: UUID, context: Context): Boolean = getProfilePicture(userUUID, context).exists()

fun requestProfilePicture(userUUID: UUID, context: Context) {
    val requestBody = object : RequestBody() {
        override fun contentType(): MediaType? = MediaType.parse("application/octet-stream")

        override fun writeTo(sink: BufferedSink) {

        }

    }
}