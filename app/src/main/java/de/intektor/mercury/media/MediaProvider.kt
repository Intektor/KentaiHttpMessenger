package de.intektor.mercury.media

import android.content.Context
import java.io.Serializable

interface MediaProvider<T : MediaFile> : Serializable {

    /**
     * @return the last elements time that can be retrieved
     */
    fun getEpochSecondTimeOfLast(context: Context): Long

    fun loadMediaFiles(context: Context, minimumEpochSecond: Long, maximumEpochSecond: Long): List<T>

    fun hasAnyElements(context: Context): Boolean
}