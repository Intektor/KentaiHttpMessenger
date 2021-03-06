package de.intektor.mercury.media

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.File

class MediaProviderExternalContent(private val folderId: Long) : MediaProvider<ExternalStorageFile> {

    override fun getEpochSecondTimeOfLast(context: Context): Long = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(MediaStore.MediaColumns.DATE_ADDED), "${MediaStore.Files.FileColumns.PARENT} = ?",
            arrayOf("$folderId"),
            "${MediaStore.MediaColumns.DATE_ADDED} ASC LIMIT 1").use { cursor ->
        if (cursor == null || !cursor.moveToNext()) return@use System.currentTimeMillis()

        cursor.getLong(0)
    }

    override fun loadMediaFiles(context: Context, minimumEpochSecond: Long, maximumEpochSecond: Long, limit: Int?): List<ExternalStorageFile> {
        return context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns.DATA, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.DATE_ADDED, MediaStore.Files.FileColumns._ID),
                "${MediaStore.Files.FileColumns.PARENT} = ? AND ${MediaStore.Files.FileColumns.DATE_ADDED} > ? AND ${MediaStore.Files.FileColumns.DATE_ADDED} < ? " +
                        "AND ${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE}, ${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO})",
                arrayOf("$folderId", "$minimumEpochSecond", "$maximumEpochSecond"),
                "${MediaStore.MediaColumns.DATE_ADDED} DESC LIMIT ${limit
                        ?: Int.MAX_VALUE}").use { cursor ->

            if (cursor == null) return@use emptyList<ExternalStorageFile>()

            val linkedToGroup = mutableListOf<ExternalStorageFile>()

            while (cursor.moveToNext()) {
                val data = cursor.getString(0)
                val mediaType = cursor.getString(1).toInt()
                val dateAdded = cursor.getLong(2)
                val id = cursor.getLong(3)

                linkedToGroup += ExternalStorageFile(Uri.fromFile(File(data)), mediaType, dateAdded, id)
            }

            return@use linkedToGroup
        }
    }

    override fun hasAnyElements(context: Context): Boolean = context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            arrayOf(),
            "${MediaStore.Files.FileColumns.PARENT} = ?",
            arrayOf("$folderId"),
            "${MediaStore.Files.FileColumns.PARENT} LIMIT 1").use { query ->
        query != null && query.moveToNext()
    }
}