package de.intektor.mercury.media

import android.content.Context
import android.os.Parcel
import android.os.Parcelable
import java.io.Serializable

interface MediaFile : Serializable {
    val mediaType: Int

    fun getPath(context: Context): String
}