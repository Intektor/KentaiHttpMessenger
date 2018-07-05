package de.intektor.kentai.kentai

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

fun getBackgroundChatFile(context: Context, chatUUID: UUID?): File = File(context.filesDir, "backgrounds/$chatUUID")