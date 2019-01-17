package de.intektor.mercury.media

import android.content.Context
import android.net.Uri
import java.io.Serializable

interface MediaFile : Serializable {
    val mediaType: Int

    val epochSecondAdded: Long

    fun getUri(context: Context): Uri

    fun getPath(context: Context): String
}