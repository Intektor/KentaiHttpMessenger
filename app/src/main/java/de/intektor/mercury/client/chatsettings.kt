package de.intektor.mercury.client

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.*

/**
 * @author Intektor
 */
fun setBackgroundImage(context: Context, path: String, chatUUID: UUID?) {
    File(context.filesDir, "backgrounds/").mkdirs()
    context.contentResolver.openInputStream(Uri.parse(path)).use { input ->
        getBackgroundChatFile(context, chatUUID).outputStream().use { output ->
            input.copyTo(output)
        }
    }
}

fun resetBackgroundImage(context: Context, chatUUID: UUID?) {
    getBackgroundChatFile(context, chatUUID).delete()
}

fun getBackgroundChatFile(context: Context, chatUUID: UUID?): File = File(context.filesDir, "backgrounds/$chatUUID")