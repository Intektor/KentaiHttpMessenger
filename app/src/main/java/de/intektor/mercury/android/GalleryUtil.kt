package de.intektor.mercury.android

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import de.intektor.mercury.media.MediaType
import de.intektor.mercury_common.util.copyFully
import java.io.File

/**
 * @author Intektor
 */
object GalleryUtil {

    const val FOLDER_TAKEN_IMAGES = "Mercury Taken Images"
    const val FOLDER_RECEIVED_MEDIA = "Mercury Received Media"

    private fun addImageToGallery(context: Context, folder: File, fileToCopy: File) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.DATA, File(folder, fileToCopy.name).path)
            put(MediaStore.Files.FileColumns.MEDIA_TYPE, MediaType.MEDIA_TYPE_IMAGE)
        }

        context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun copyImage(folder: File, fileToCopy: File) {
        File(folder, fileToCopy.name).outputStream().use { output ->
            fileToCopy.inputStream().use { input -> input.copyFully(output) }
        }
    }

    fun copyImageAndAddToGallery(context: Context, folder: String, fileToCopy: File) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            val folderDirectory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folder)
            folderDirectory.mkdirs()
            copyImage(folderDirectory, fileToCopy)

            addImageToGallery(context, folderDirectory, fileToCopy)
        }
    }
}