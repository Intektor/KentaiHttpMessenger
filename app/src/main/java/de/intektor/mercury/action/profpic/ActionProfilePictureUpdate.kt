package de.intektor.mercury.action.profpic

import android.content.Context
import android.content.Intent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.intektor.mercury.android.getUUIDExtra
import java.util.*

/**
 * @author Intektor
 */
object ActionProfilePictureUpdate {

    private const val ACTION = "de.intektor.mercury.ACTION_PROFILE_PICTURE_UPDATE"

    private const val EXTRA_USER_UUID: String = "de.intektor.mercury.EXTRA_USER_UUID"

    fun isAction(intent: Intent) = intent.action == ACTION

    private fun createIntent(context: Context, userUuid: UUID) =
            Intent()
                .setAction(ACTION)
                .putExtra(EXTRA_USER_UUID,  userUuid)

    fun launch(context: Context, userUuid: UUID) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, userUuid))
    }

    fun getData(intent: Intent): Holder {
        val userUuid: UUID = intent.getUUIDExtra(EXTRA_USER_UUID)
        return Holder(userUuid)
    }

    data class Holder(val userUuid: UUID)
}