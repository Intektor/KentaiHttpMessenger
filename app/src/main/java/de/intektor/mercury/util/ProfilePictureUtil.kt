package de.intektor.mercury.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import com.squareup.picasso.Picasso
import com.squareup.picasso.Request
import com.squareup.picasso.RequestHandler
import de.intektor.mercury.io.HttpManager
import de.intektor.mercury_common.client_to_server.DownloadProfilePictureRequest
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.users.ProfilePictureType
import de.intektor.mercury_common.util.toUUID
import java.io.File
import java.util.*

object ProfilePictureUtil {

    private const val PROFILE_PICTURE_FOLDER = "profile_pictures"

    private const val SCHEME_PROFILE_PICTURE = "profile_picture"

    fun getProfilePicture(userUUID: UUID, context: Context, type: ProfilePictureType? = null): File {
        File(context.filesDir, PROFILE_PICTURE_FOLDER).mkdirs()
        return when (type) {
            ProfilePictureType.SMALL -> File(File(context.filesDir, PROFILE_PICTURE_FOLDER), "${userUUID}_small")
            ProfilePictureType.NORMAL -> File(File(context.filesDir, PROFILE_PICTURE_FOLDER), "$userUUID")
            null -> {
                val searchedType = ProfilePictureUtil.getProfilePictureType(userUUID, context)
                        ?: ProfilePictureType.SMALL
                ProfilePictureUtil.getProfilePicture(userUUID, context, searchedType)
            }
        }
    }

    fun getProfilePictureType(userUUID: UUID, context: Context): ProfilePictureType? {
        val normal = ProfilePictureUtil.getProfilePicture(userUUID, context, ProfilePictureType.NORMAL)
        return if (!normal.exists()) (if (ProfilePictureUtil.getProfilePicture(userUUID, context, ProfilePictureType.SMALL).exists()) ProfilePictureType.SMALL else null) else ProfilePictureType.NORMAL
    }

    fun hasProfilePicture(userUUID: UUID, context: Context): Boolean = ProfilePictureUtil.getProfilePicture(userUUID, context).exists()

    fun setProfilePicture(bitmap: Bitmap, userUUID: UUID, context: Context, type: ProfilePictureType) {
        File(context.filesDir, "profile_pictures").mkdirs()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, ProfilePictureUtil.getProfilePicture(userUUID, context, type).outputStream())
    }

    fun loadProfilePicture(userUUID: UUID, type: ProfilePictureType, target: ImageView, placeholder: Drawable? = null) {
        val uri = Uri.Builder()
                .scheme(SCHEME_PROFILE_PICTURE)
                .path("${userUUID}/${type.ordinal}")
                .build()
        if (placeholder == null) Picasso.get().load(uri).into(target) else Picasso.get().load(uri).placeholder(placeholder).into(target)
    }

    fun createProfilePictureRequestHandler(context: Context) = object : RequestHandler() {
        override fun canHandleRequest(data: Request?): Boolean = data?.uri?.scheme == SCHEME_PROFILE_PICTURE

        override fun load(request: Request, networkPolicy: Int): Result? {

            val path = request.uri.path ?: return null

            val parts = path.split("/")

            val userUUID = parts[1].toUUID()
            val type = ProfilePictureType.values()[parts[2].toInt()]

            val response = try {
                HttpManager.rawPost(genGson().toJson(DownloadProfilePictureRequest(userUUID, type)), DownloadProfilePictureRequest.TARGET)
            } catch (t: Throwable) {
                null
            } ?: return null

            val bitmap = BitmapFactory.decodeStream(response.byteStream())

            return Result(bitmap, Picasso.LoadedFrom.NETWORK)
        }
    }
}