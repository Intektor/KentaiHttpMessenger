package de.intektor.kentai.kentai.references

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.graphics.Movie
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.AsyncTask
import android.os.Environment
import android.widget.Toast
import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.chat.setReferenceState
import de.intektor.kentai.kentai.firebase.SendService
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.client_to_server.DownloadReferenceRequest
import de.intektor.kentai_http_common.client_to_server.DownloadReferenceRequest.Response
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.copyFully
import de.intektor.kentai_http_common.util.decryptRSA
import de.intektor.kentai_http_common.util.toAESKey
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import java.io.*
import java.security.Key
import java.util.*
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * @author Intektor
 */

fun uploadAudio(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, audioFile: File) {
    uploadReference(context, chatUUID, referenceUUID, audioFile, FileType.AUDIO)
}

fun downloadAudio(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, chatType: ChatType, hash: String, privateMessageKey: Key) {
    downloadReference(context, database, chatUUID, referenceUUID, FileType.AUDIO, chatType, hash, privateMessageKey)
}

fun uploadImage(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, imageFile: File) {
    uploadReference(context, chatUUID, referenceUUID, imageFile, FileType.IMAGE)
}

fun downloadImage(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, chatType: ChatType, hash: String, privateMessageKey: Key) {
    downloadReference(context, database, chatUUID, referenceUUID, FileType.IMAGE, chatType, hash, privateMessageKey)
}

fun uploadVideo(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, imageFile: File) {
    uploadReference(context, chatUUID, referenceUUID, imageFile, FileType.VIDEO)
}

fun downloadVideo(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, chatType: ChatType, hash: String, privateMessageKey: Key) {
    downloadReference(context, database, chatUUID, referenceUUID, FileType.VIDEO, chatType, hash, privateMessageKey)
}

fun downloadReference(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, fileType: FileType, chatType: ChatType, hash: String, privateMessageKey: Key) {
    val kentaiClient = context.applicationContext as KentaiClient

    object : AsyncTask<Unit, Unit, DownloadReferenceRequest.Response>() {

        override fun doInBackground(vararg p0: Unit?): DownloadReferenceRequest.Response {
            //This key is used to either decrypt the key sent in the file or to encrypt the full file
            val decryptionKey: Key = when (chatType) {
                ChatType.TWO_PEOPLE -> {
                    privateMessageKey
                }
                ChatType.GROUP_CENTRALIZED, ChatType.GROUP_DECENTRALIZED -> {
                    database.rawQuery("SELECT group_key FROM group_key_table WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { query ->
                        query.moveToNext()
                        query.getString(0).toAESKey()
                    }
                }
                else -> throw NotImplementedError()
            }

            val gson = genGson()

            val body = RequestBody.create(MediaType.parse("JSON"), gson.toJson(DownloadReferenceRequest(referenceUUID)))
            val request = Request.Builder()
                    .url(httpAddress + DownloadReferenceRequest.TARGET)
                    .post(body)
                    .build()
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    DataInputStream(response.body()!!.byteStream()).use { dataIn ->
                        val responseCode = Response.values()[dataIn.readInt()]
                        val totalToReceive = dataIn.readLong()
                        when (responseCode) {
                            Response.NOT_FOUND -> {
                                kentaiClient.currentLoadingTable -= referenceUUID
                                setReferenceState(database, chatUUID, referenceUUID, fileType, UploadState.NOT_STARTED)
                                return Response.NOT_FOUND
                            }
                            Response.DELETED -> {
                                kentaiClient.currentLoadingTable -= referenceUUID

                                setReferenceState(database, chatUUID, referenceUUID, fileType, UploadState.NOT_STARTED)
                                return Response.DELETED
                            }
                            Response.IN_PROGRESS -> {
                                kentaiClient.currentLoadingTable -= referenceUUID

                                setReferenceState(database, chatUUID, referenceUUID, fileType, UploadState.NOT_STARTED)
                                return Response.IN_PROGRESS
                            }
                            Response.SUCCESS -> {
                                try {
                                    val actKey: Key = if (chatType == ChatType.TWO_PEOPLE) {
                                        val readUTF = dataIn.readUTF()
                                        SecretKeySpec(BaseEncoding.base64().decode(readUTF.decryptRSA(decryptionKey)), "AES")
                                    } else {
                                        decryptionKey
                                    }

                                    val iV = ByteArray(dataIn.readInt())
                                    response.body()!!.byteStream().read(iV)

                                    val cipher: Cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
                                    cipher.init(Cipher.DECRYPT_MODE, actKey, IvParameterSpec(iV))

                                    val referenceFile = getReferenceFile(referenceUUID, fileType, context.filesDir, context)

                                    BufferedInputStream(CipherInputStream(ResponseInputStream(response.body()!!.byteStream(), totalToReceive, referenceUUID, context, kentaiClient), cipher)).use { cipherIn ->
                                        cipherIn.copyFully(referenceFile.outputStream(), 1024 * 1024)
                                    }

                                    if (fileType == FileType.IMAGE) {
                                        File(Environment.getExternalStorageDirectory().toString() + "/Pictures/Kentai/").mkdirs()
                                        referenceFile.inputStream().use { inputStream ->
                                            inputStream.copyTo(File(Environment.getExternalStorageDirectory().toString() + "/Pictures/Kentai/$referenceUUID.${fileType.extension}").outputStream())
                                        }
                                    }

                                    setReferenceState(database, chatUUID, referenceUUID, fileType, UploadState.FINISHED)
                                    kentaiClient.currentLoadingTable -= referenceUUID
                                    return Response.SUCCESS
                                } catch (t: Throwable) {
                                    setReferenceState(database, chatUUID, referenceUUID, fileType, UploadState.NOT_STARTED)
                                    kentaiClient.currentLoadingTable -= referenceUUID
                                    return Response.NOT_FOUND
                                }
                            }
                        }
                    }
                } else {
                    return Response.NOT_FOUND
                }
            }
        }

        override fun onPostExecute(result: DownloadReferenceRequest.Response) {
            super.onPostExecute(result)
            var successful = false
            when (result) {
                Response.NOT_FOUND -> Toast.makeText(context, "Not found!", Toast.LENGTH_SHORT).show()
                Response.DELETED -> Toast.makeText(context, "Deleted!", Toast.LENGTH_SHORT).show()
                Response.IN_PROGRESS -> Toast.makeText(context, "In progress!", Toast.LENGTH_SHORT).show()
                Response.SUCCESS -> {
                    val referenceFile = getReferenceFile(referenceUUID, fileType, context.filesDir, context)
                    if (Hashing.sha512().hashBytes(referenceFile.readBytes()).toString() != hash) {
                        Toast.makeText(context, "File hashes don't match!", Toast.LENGTH_SHORT).show()
                    } else {
                        successful = true
                    }
                }
            }
            val i = Intent(ACTION_DOWNLOAD_REFERENCE_FINISHED)
            i.putExtra(KEY_SUCCESSFUL, successful)
            i.putExtra(KEY_REFERENCE_UUID, referenceUUID)
            context.sendBroadcast(i)
        }
    }.execute()
}

fun uploadReference(context: Context, chatUUID: UUID, referenceUUID: UUID, referenceFile: File, fileType: FileType) {
    val startService = Intent(context, SendService::class.java)
    context.startService(startService)

    val i = Intent(context, SendService::class.java)
    i.putExtra(KEY_CHAT_UUID, chatUUID)
    i.putExtra(KEY_MEDIA_URL, referenceFile.absolutePath)
    i.putExtra(KEY_MEDIA_TYPE, fileType)
    i.putExtra(KEY_REFERENCE_UUID, referenceUUID)
    i.action = ACTION_UPLOAD_REFERENCE
    context.startService(i)
}

fun getReferenceFile(referenceUUID: UUID, fileType: FileType, filesDir: File, context: Context): File {
    File("${filesDir.path}/resources/").mkdirs()

    return File("${filesDir.path}/resources/$referenceUUID.${fileType.extension}")
}

enum class UploadState {
    IN_PROGRESS,
    FINISHED,
    NOT_STARTED
}

private class ResponseInputStream(inputStream: InputStream, private val totalToReceive: Long, val referenceUUID: UUID, val context: Context, val kentaiClient: KentaiClient) : FilterInputStream(inputStream) {

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
            val i = Intent("de.intektor.kentai.downloadProgress")
            i.putExtra("referenceUUID", referenceUUID)
            i.putExtra("progress", currentPercent)
            context.sendBroadcast(i)
            kentaiClient.currentLoadingTable[referenceUUID] = prefRead
        }
    }
}

fun getVideoDuration(referenceFile: File, kentaiClient: KentaiClient): Int {
    val time: Long = try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(kentaiClient, Uri.fromFile(referenceFile))
        val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

        retriever.release()
        time.toLong()
    } catch (t: Throwable) {
        val movie = Movie.decodeStream(referenceFile.inputStream())
        movie.duration().toLong()
    }
    return (time / 1000L).toInt()
}

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

    val referenceFile = getReferenceFile(referenceUUID, fileType, context.filesDir, context)

    File(realPath).inputStream().use { fileIn ->
        fileIn.copyTo(referenceFile.outputStream())
    }

    return referenceFile
}

fun cancelReferenceDownload(referenceUUID: UUID, context: Context) {

}