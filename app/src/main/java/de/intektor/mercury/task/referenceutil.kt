package de.intektor.mercury.task

import android.content.Context
import android.content.Intent
import android.graphics.Movie
import android.media.MediaMetadataRetriever
import android.net.Uri
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.android.getRealImagePath
import de.intektor.mercury.android.getRealVideoPath
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury_common.reference.FileType
import java.io.File
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

fun getVideoDuration(referenceFile: File, mercuryClient: MercuryClient): Int {
    val time: Long = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(mercuryClient, Uri.fromFile(referenceFile))
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

        retriever.release()
        time.toLong()
    } catch (t: Throwable) {
        val movie = Movie.decodeStream(referenceFile.inputStream())
        movie.duration().toLong()
    }

    return (time / 1000L).toInt()
}

fun getVideoDimension(context: Context, referenceFile: File): Dimension {
    val retriever = MediaMetadataRetriever()
    retriever.setDataSource(context, Uri.fromFile(referenceFile))

    val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
    val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()

    retriever.release()

    return Dimension(width, height)
}

data class Dimension(val width: Int, val height: Int)

/**
 * Saves the media and also gives back the reference file
 */
fun saveMediaFileInAppStorage(referenceUUID: UUID, uri: Uri, context: Context, fileType: FileType): File {
    val realPath = if (File(uri.path).exists()) uri.path else when (fileType) {
        FileType.VIDEO -> getRealVideoPath(uri, context)
        FileType.GIF -> getRealVideoPath(uri, context)
        FileType.IMAGE -> getRealImagePath(uri, context)
        else -> throw IllegalArgumentException()
    }

    val referenceFile = ReferenceUtil.getFileForReference(context, referenceUUID)

    File(realPath).inputStream().use { fileIn ->
        fileIn.copyTo(referenceFile.outputStream())
    }

    return referenceFile
}