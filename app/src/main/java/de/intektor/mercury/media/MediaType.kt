package de.intektor.mercury.media

import android.provider.MediaStore

object MediaType {
    val MEDIA_TYPE_IMAGE = MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE
    val MEDIA_TYPE_VIDEO = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
    val MEDIA_TYPE_AUDIO = MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO
    val MEDIA_TYPE_NONE = MediaStore.Files.FileColumns.MEDIA_TYPE_NONE

    fun getMediaTypeFromMime(mime: String): Int {
        return when {
            mime.contains("image") -> MEDIA_TYPE_IMAGE
            mime.contains("video") -> MEDIA_TYPE_VIDEO
            mime.contains("audio") -> MEDIA_TYPE_AUDIO
            else -> MEDIA_TYPE_NONE
        }
    }
}