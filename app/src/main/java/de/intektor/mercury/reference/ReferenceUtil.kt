package de.intektor.mercury.reference

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import de.intektor.mercury.database.*
import de.intektor.mercury_common.reference.FileType
import de.intektor.mercury_common.util.toAESKey
import java.io.File
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
object ReferenceUtil {

    fun getFileForReference(context: Context, referenceUUID: UUID): File {
        File(context.filesDir, "references").mkdirs()
        return File(context.filesDir, "references/$referenceUUID")
    }

    fun setFileTypeForReference(referenceUUID: UUID, fileType: FileType, database: SQLiteDatabase) {
        database.compileStatement("INSERT INTO file_type (reference_uuid, file_type) VALUES(?, ?)").use { statement ->
            statement.bindUUID(1, referenceUUID)
            statement.bindEnum(2, fileType)
            statement.execute()
        }
    }

    fun getFileTypeForReference(database: SQLiteDatabase, referenceUUID: UUID): FileType {
        return database.rawQuery("SELECT file_type FROM file_type WHERE reference_uuid = ?", arrayOf(referenceUUID.toString())).use { cursor ->
            cursor.moveToNext()
            cursor.getEnum(1)
        }
    }

    fun dropReference(context: Context, database: SQLiteDatabase, referenceUUID: UUID) {
        database.execSQL("DELETE FROM reference_upload WHERE reference_uuid = ?", arrayOf(referenceUUID.toString()))

        database.execSQL("DELETE FROM file_type WHERE reference_uuid = ?", arrayOf(referenceUUID.toString()))

        getFileForReference(context, referenceUUID).delete()
    }

    fun isReferenceUploaded(database: SQLiteDatabase, referenceUUID: UUID): Boolean {
        return database.rawQuery("SELECT uploaded FROM reference_upload WHERE reference_uuid = ?", arrayOf(referenceUUID.toString())).use { cursor ->
            if (!cursor.moveToNext()) return@use false

            cursor.getBoolean(1)
        }
    }

    fun setReferenceUploaded(database: SQLiteDatabase, referenceUUID: UUID, uploaded: Boolean) {
        database.compileStatement("INSERT INTO reference_upload (reference_uuid, reference_upload) VALUES (?, ?)").use { statement ->
            statement.bindUUID(0, referenceUUID)
            statement.bindBoolean(1, uploaded)

            statement.execute()
        }
    }

    fun setReferenceKey(database: SQLiteDatabase, referenceUUID: UUID, aesKey: Key, initVector: ByteArray) {
        database.compileStatement("INSERT INTO reference_key (reference_uuid, aes, init_vector) VALUES (?, ?, ?)").use { statement ->
            statement.bindUUID(1, referenceUUID)
            statement.bindBlob(2, aesKey.encoded)
            statement.bindBlob(3, initVector)
            statement.execute()
        }
    }

    fun getReferenceKey(database: SQLiteDatabase, referenceUUID: UUID): ReferenceKey {
        return database.rawQuery("SELECT aes, init_vector FROM reference_key WHERE reference_uuid = ?", arrayOf(referenceUUID.toString())).use { cursor ->
            if (!cursor.moveToNext()) throw IllegalStateException("No key found for referenceUUID=$referenceUUID")

            val aes = cursor.getBlob(0).toAESKey()
            val initVector = cursor.getBlob(1)
            ReferenceKey(aes, initVector)
        }
    }

    fun addReferenceToChat(database: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID, messageUUID: UUID) {
        database.compileStatement("INSERT INTO reference (chat_uuid, reference_uuid, message_uuid) VALUES(?, ?, ?)").use { statement ->
            statement.bindUUID(1, chatUUID)
            statement.bindUUID(2, referenceUUID)
            statement.bindUUID(3, messageUUID)
            statement.execute()
        }
    }

    fun getReferencesInChat(database: SQLiteDatabase, chatUUID: UUID): List<ReferenceInfo> {
        return database.rawQuery("SELECT reference_uuid, user_uuid FROM reference WHERE chat_uuid = ?", arrayOf(chatUUID.toString())).use { cursor ->
            val list = mutableListOf<ReferenceInfo>()
            while (cursor.moveToNext()) {
                val referenceUUID = cursor.getUUID(0)
                val senderUUID = cursor.getUUID(1)

                list += ReferenceInfo(referenceUUID, senderUUID)
            }
            list
        }
    }

    class ReferenceKey(val key: Key, val initVector: ByteArray)

    data class ReferenceInfo(val referenceUUID: UUID, val messageUUID: UUID)
}