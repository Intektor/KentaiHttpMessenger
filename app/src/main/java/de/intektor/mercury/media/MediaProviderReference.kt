package de.intektor.mercury.media

import android.content.Context
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.database.getUUID
import java.util.*
import java.util.concurrent.TimeUnit

class MediaProviderReference(private val chatUUID: UUID) : MediaProvider<ReferenceFile> {

    override fun getEpochSecondTimeOfLast(context: Context): Long {
        val mercuryClient = context.mercuryClient()

        return mercuryClient.dataBase.rawQuery("SELECT time FROM reference WHERE chat_uuid = ? ORDER BY time ASC LIMIT 1", arrayOf(chatUUID.toString())).use { cursor ->
            if (!cursor.moveToNext()) return@use 0L

            cursor.getLong(0)
        }
    }

    override fun loadMediaFiles(context: Context, minimumEpochSecond: Long, maximumEpochSecond: Long): List<ReferenceFile> {
        val mercuryClient = context.mercuryClient()

        return mercuryClient.dataBase.rawQuery("SELECT reference_uuid, media_type, time FROM reference WHERE chat_uuid = ? AND time > ? AND time < ?",
                arrayOf(chatUUID.toString(), (TimeUnit.SECONDS.toMillis(minimumEpochSecond)).toString(), (TimeUnit.SECONDS.toMillis(maximumEpochSecond)).toString())).use { cursor ->

            val list = mutableListOf<ReferenceFile>()

            while (cursor.moveToNext()) {
                val referenceUUID = cursor.getUUID(0)
                val mediaType = cursor.getInt(1)
                val time = cursor.getLong(2)

                list += ReferenceFile(referenceUUID, chatUUID, mediaType, time)
            }

            list
        }
    }

    override fun hasAnyElements(context: Context): Boolean =
            context.mercuryClient().dataBase.rawQuery("SELECT COUNT(reference_uuid) FROM reference WHERE chat_uuid = ? LIMIT 1", arrayOf("$chatUUID")).use { cursor ->
                cursor.moveToNext()
                cursor.getInt(0) > 0
            }
}