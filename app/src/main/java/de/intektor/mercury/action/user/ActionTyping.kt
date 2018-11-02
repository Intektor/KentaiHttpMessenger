package de.intektor.mercury.action.user

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.intektor.mercury.android.getUUIDExtra
import java.util.*

/**
 * @author Intektor
 */
object ActionTyping {

    private const val ACTION = "de.intektor.mercury.ACTION_TYPING"

    private const val EXTRA_CHAT_UUID: String = "de.intektor.mercury.EXTRA_CHAT_UUID"
    private const val EXTRA_USER_UUID: String = "de.intektor.mercury.EXTRA_USER_UUID"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context, chatUuid: UUID, userUuid: UUID) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_CHAT_UUID, chatUuid)
                    .putExtra(EXTRA_USER_UUID, userUuid)

    fun launch(context: Context, chatUuid: UUID, userUuid: UUID) {
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, chatUuid, userUuid))
    }

    fun getData(intent: Intent): Holder {
        val chatUuid: UUID = intent.getUUIDExtra(EXTRA_CHAT_UUID)
        val userUuid: UUID = intent.getUUIDExtra(EXTRA_USER_UUID)
        return Holder(chatUuid, userUuid)
    }

    data class Holder(val chatUuid: UUID, val userUuid: UUID)
}