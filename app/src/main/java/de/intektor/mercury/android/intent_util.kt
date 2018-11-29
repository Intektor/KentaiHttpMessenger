package de.intektor.mercury.android

import android.content.Intent
import android.net.Uri
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.media.MediaFile
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.data.group_modification.GroupModification
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.reference.FileType
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
fun Intent.getUUIDExtra(key: String) = getSerializableExtra(key) as UUID

fun Intent.getFileTypeExtra(key: String) = getSerializableExtra(key) as FileType

fun Intent.getChatInfoExtra(key: String): ChatInfo = getParcelableExtra(key)

fun Intent.putExtra(key: String, groupModification: GroupModification): Intent = putExtra(key, genGson().toJson(groupModification))

fun Intent.getGroupModificationExtra(key: String) = genGson().fromJson(getStringExtra(key), GroupModification::class.java)

fun Intent.getKeyExtra(key: String) = getSerializableExtra(key) as Key

fun Intent.putExtra(key: String, chatMessage: ChatMessage): Intent = putExtra(key, genGson().toJson(chatMessage))

fun Intent.getChatMessageExtra(key: String): ChatMessage = genGson().fromJson(getStringExtra(key), ChatMessage::class.java)

fun Intent.putEnumExtra(key: String, enum: Enum<*>): Intent = putExtra(key, enum.ordinal)

fun Intent.getUriExtra(key: String): Uri = getParcelableExtra(key)

fun Intent.getMediaFileExtra(key: String): MediaFile = getSerializableExtra(key) as MediaFile

inline fun <reified T : Enum<T>> Intent.getEnumExtra(key: String): T = enumValues<T>()[getIntExtra(key, 0)]