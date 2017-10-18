package de.intektor.kentai.kentai.references

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.os.AsyncTask
import android.widget.Toast
import com.google.common.hash.Hashing
import com.google.common.io.BaseEncoding
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.httpAddress
import de.intektor.kentai.kentai.firebase.SendService
import de.intektor.kentai.kentai.httpClient
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.client_to_server.DownloadReferenceRequest
import de.intektor.kentai_http_common.client_to_server.DownloadReferenceRequest.Response
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.decryptRSA
import de.intektor.kentai_http_common.util.toAESKey
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import java.io.DataInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
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
    uploadReference(context, database, chatUUID, referenceUUID, audioFile, FileType.AUDIO)
}

fun downloadAudio(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, chatType: ChatType, hash: String) {
    downloadReference(context, database, chatUUID, referenceUUID, FileType.AUDIO, chatType, hash)
}

fun uploadImage(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, imageFile: File) {
    uploadReference(context, database, chatUUID, referenceUUID, imageFile, FileType.IMAGE)
}

fun downloadImage(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, chatType: ChatType, hash: String) {
    downloadReference(context, database, chatUUID, referenceUUID, FileType.IMAGE, chatType, hash)
}

fun uploadVideo(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, imageFile: File) {
    uploadReference(context, database, chatUUID, referenceUUID, imageFile, FileType.VIDEO)
}

fun downloadVideo(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, chatType: ChatType, hash: String) {
    downloadReference(context, database, chatUUID, referenceUUID, FileType.VIDEO, chatType, hash)
}

private fun downloadReference(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, fileType: FileType, chatType: ChatType, hash: String) {
    object : AsyncTask<Unit, Unit, DownloadReferenceRequest.Response>() {

        override fun doInBackground(vararg p0: Unit?): DownloadReferenceRequest.Response {
            //This key is used to either decrypt the key sent in the file or to encrypt the full file
            val decryptionKey: Key = when (chatType) {
                ChatType.TWO_PEOPLE -> {
                    KentaiClient.INSTANCE.privateMessageKey!!
                }
                ChatType.GROUP -> {
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
                            Response.NOT_FOUND -> return Response.NOT_FOUND
                            Response.DELETED -> return Response.DELETED
                            Response.IN_PROGRESS -> return Response.IN_PROGRESS
                            Response.SUCCESS -> {
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

                                CipherInputStream(ResponseInputStream(response.body()!!.byteStream(), totalToReceive, referenceUUID, context), cipher).use { cipherIn ->
                                    cipherIn.copyTo(getReferenceFile(chatUUID, referenceUUID, fileType, context.filesDir).outputStream())
                                }
                                database.compileStatement("INSERT INTO reference_upload_table (chat_uuid, reference_uuid, file_type, state) VALUES(?, ?, ?, ?)").use { statement ->
                                    statement.bindString(1, chatUUID.toString())
                                    statement.bindString(2, referenceUUID.toString())
                                    statement.bindLong(3, fileType.ordinal.toLong())
                                    statement.bindLong(4, UploadState.UPLOADED.ordinal.toLong())
                                    statement.execute()
                                }
                                return Response.SUCCESS
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
                    val referenceFile = getReferenceFile(chatUUID, referenceUUID, fileType, context.filesDir)
                    if (Hashing.sha512().hashBytes(referenceFile.readBytes()).toString() != hash) {
                        Toast.makeText(context, "File hashes don't match!", Toast.LENGTH_SHORT).show()
                    } else {
                        successful = true
                    }
                }
            }
            val i = Intent("de.intektor.kentai.downloadReferenceFinished")
            i.putExtra("successful", successful)
            i.putExtra("referenceUUID", referenceUUID.toString())
            context.sendBroadcast(i)
        }
    }.execute()
}

private fun uploadReference(context: Context, database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, referenceFile: File, fileType: FileType) {
    val alreadyContained = database.rawQuery("SELECT COUNT(*) FROM reference_upload_table WHERE reference_uuid = ?", arrayOf(referenceUUID.toString())).use { query ->
        query.moveToNext()
        query.getInt(0) == 1
    }
    if (!alreadyContained) {
        database.compileStatement("INSERT INTO reference_upload_table (chat_uuid, reference_uuid, file_type, state) VALUES(?, ?, ?, ?)").use { statement ->
            statement.bindString(1, chatUUID.toString())
            statement.bindString(2, referenceUUID.toString())
            statement.bindLong(3, fileType.ordinal.toLong())
            statement.bindLong(4, UploadState.IN_PROGRESS.ordinal.toLong())
            statement.execute()
        }
    }

    val startService = Intent(context, SendService::class.java)
    context.startService(startService)

    val i = Intent("de.intektor.kentai.referenceUpload")
    i.putExtra("chatUUID", chatUUID)
    i.putExtra("referenceFile", referenceFile.absolutePath)
    i.putExtra("fileType", fileType.ordinal)
    i.putExtra("referenceUUID", referenceUUID.toString())
    context.sendBroadcast(i)
}

fun getReferenceFile(chatUUID: UUID, referenceUUID: UUID, fileType: FileType, filesDir: File): File {
    File("${filesDir.path}/resources/$chatUUID/").mkdirs()

    return File("${filesDir.path}/resources/$chatUUID/$referenceUUID.${when (fileType) {
        FileType.AUDIO -> "3gp"
        FileType.IMAGE -> "jpeg"
        FileType.VIDEO -> "mp4"
    }}")
}

enum class UploadState {
    IN_PROGRESS,
    UPLOADED
}

private class ResponseInputStream(inputStream: InputStream, private val totalToReceive: Long, val referenceUUID: UUID, val context: Context) : FilterInputStream(inputStream) {

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
        if (prefRead + 10 < currentPercent) {
            prefRead = currentPercent
            val i = Intent("de.intektor.kentai.downloadProgress")
            i.putExtra("referenceUUID", referenceUUID)
            i.putExtra("progress", currentPercent)
            context.sendBroadcast(i)
        }
    }
}