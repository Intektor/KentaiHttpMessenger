package de.intektor.mercury.media

import android.content.Context
import android.provider.MediaStore

data class ExternalStorageFile(val id: Long, override val mediaType: Int) : MediaFile {

    override fun getPath(context: Context): String {
       return context.contentResolver.query(MediaStore.Files.getContentUri("external"),
                arrayOf(MediaStore.MediaColumns.DATA),
                "${MediaStore.Files.FileColumns._ID} = ?",
                arrayOf("$id"),
                null).use { cursor ->
            if (cursor == null || !cursor.moveToNext()) return@use ""

            cursor.getString(0)
        }
    }
}