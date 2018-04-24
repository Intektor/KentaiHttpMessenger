package de.intektor.kentai.kentai.nine_gag

import android.content.Context
import android.content.Intent
import de.intektor.kentai.kentai.ACTION_DOWNLOAD_NINE_GAG
import de.intektor.kentai.kentai.KEY_CHAT_UUID
import de.intektor.kentai.kentai.KEY_NINE_GAG_ID
import de.intektor.kentai.kentai.KEY_NINE_GAG_UUID
import de.intektor.kentai.kentai.firebase.SendService
import de.intektor.kentai_http_common.reference.FileType
import java.io.File
import java.util.*

/**
 * @author Intektor
 */
fun getNineGagFile(gagId: String, type: NineGagType, context: Context): File {
    File(context.filesDir, "nine_gag").mkdirs()
    return File(File(context.filesDir, "nine_gag"), "$gagId${type.extension}")
}

enum class NineGagType(val extension: String, val fileType: FileType) {
    VIDEO("_460sv.mp4", FileType.VIDEO),
    IMAGE("_700b.jpg", FileType.IMAGE)
}

fun isNineGagMessage(messageText: String): Boolean = messageText.contains("https://9gag.com/gag/")

fun getGagId(messageText: String): String = messageText.substringAfter("https://9gag.com/gag/").substring(0, 7)

fun getGagUUID(messageText: String): UUID {
    val mostSig = getGagId(messageText).hashCode().toLong()
    return UUID(mostSig, 0)
}

fun downloadNineGag(gagId: String, gagUUID: UUID, chatUUID: UUID, context: Context) {
    val i = Intent(context, SendService::class.java)
    i.action = ACTION_DOWNLOAD_NINE_GAG
    i.putExtra(KEY_NINE_GAG_ID, gagId)
    i.putExtra(KEY_NINE_GAG_UUID, gagUUID)
    i.putExtra(KEY_CHAT_UUID, chatUUID)
    context.startService(i)
}