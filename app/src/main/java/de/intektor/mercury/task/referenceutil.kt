package de.intektor.mercury.task

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import de.intektor.mercury.MercuryClient
import java.io.FilterInputStream
import java.io.InputStream
import java.util.*

/**
 * @author Intektor
 */

enum class ReferenceState {
    IN_PROGRESS,
    FINISHED,
    NOT_STARTED
}

private class ResponseInputStream(inputStream: InputStream, private val totalToReceive: Long, val referenceUUID: UUID, val context: Context, val mercuryClient: MercuryClient) : FilterInputStream(inputStream) {

    var bytesRead = 0L
    var prefRead = 0.0

    override fun read(): Int {
        bytesRead++
        update()
        return super.read()
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        bytesRead += len
        update()
        return super.read(b, off, len)
    }

    private fun update() {
        val currentPercent = bytesRead.toDouble() / totalToReceive.toDouble()
        if (prefRead + 0.01 < currentPercent) {
            prefRead = currentPercent
            val i = Intent("de.intektor.mercury.downloadProgress")
            i.putExtra("referenceUUID", referenceUUID)
            i.putExtra("progress", currentPercent)
            context.sendBroadcast(i)
            mercuryClient.currentLoadingTable[referenceUUID] = prefRead
        }
    }
}

fun getVideoDuration(context: Context, uri: Uri): Int {
    val time: Long = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

        retriever.release()
        time.toLong()
    } catch (t: Throwable) {
//        val movie = Movie.decodeStream(referenceFile.inputStream())
//        movie.duration().toLong()
        0
    }

    return (time / 1000L).toInt()
}