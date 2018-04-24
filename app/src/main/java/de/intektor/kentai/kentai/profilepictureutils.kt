package de.intektor.kentai.kentai

import android.content.Context
import android.graphics.Bitmap
import de.intektor.kentai_http_common.users.ProfilePictureType
import java.io.File
import java.util.*

private const val PROFILE_PICTURE_FOLDER = "profile_pictures"

fun getProfilePicture(userUUID: UUID, context: Context, type: ProfilePictureType? = null): File {
    File(context.filesDir, PROFILE_PICTURE_FOLDER).mkdirs()
    return when (type) {
        ProfilePictureType.SMALL -> File(File(context.filesDir, PROFILE_PICTURE_FOLDER), "${userUUID}_small")
        ProfilePictureType.NORMAL -> File(File(context.filesDir, PROFILE_PICTURE_FOLDER), "$userUUID")
        null -> {
            val searchedType = getProfilePictureType(userUUID, context) ?: ProfilePictureType.SMALL
            getProfilePicture(userUUID, context, searchedType)
        }
    }
}

fun getProfilePictureType(userUUID: UUID, context: Context): ProfilePictureType? {
    val normal = getProfilePicture(userUUID, context, ProfilePictureType.NORMAL)
    return if (!normal.exists()) (if (getProfilePicture(userUUID, context, ProfilePictureType.SMALL).exists()) ProfilePictureType.SMALL else null) else ProfilePictureType.NORMAL
}

fun hasProfilePicture(userUUID: UUID, context: Context): Boolean = getProfilePicture(userUUID, context).exists()

fun setProfilePicture(bitmap: Bitmap, userUUID: UUID, context: Context, type: ProfilePictureType) {
    File(context.filesDir, "profile_pictures").mkdirs()
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, getProfilePicture(userUUID, context, type).outputStream())
}