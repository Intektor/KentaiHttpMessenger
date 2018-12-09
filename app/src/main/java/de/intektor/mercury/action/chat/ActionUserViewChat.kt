package de.intektor.mercury.action.chat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.intektor.mercury.android.getUUIDExtra
import java.util.*

/**
 * @author Intektor
 */
object ActionUserViewChat {

    private const val ACTION = "de.intektor.mercury.ACTION_USER_VIEW_CHAT"

    private const val EXTRA_CHAT_UUID: String = "de.intektor.mercury.EXTRA_CHAT_UUID"
    private const val EXTRA_USER_UUID: String = "de.intektor.mercury.EXTRA_USER_UUID"
    private const val EXTRA_IS_VIEWING: String = "de.intektor.mercury.EXTRA_IS_VIEWING"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context, chatUuid: UUID, userUuid: UUID, isViewing: Boolean) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_CHAT_UUID, chatUuid)
                    .putExtra(EXTRA_USER_UUID, userUuid)
                    .putExtra(EXTRA_IS_VIEWING, isViewing)

    fun launch(context: Context, chatUuid: UUID, userUuid: UUID, isViewing: Boolean) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, chatUuid, userUuid, isViewing))
    }

    fun getData(intent: Intent): Holder {
        val chatUuid: UUID = intent.getUUIDExtra(EXTRA_CHAT_UUID)
        val userUuid: UUID = intent.getUUIDExtra(EXTRA_USER_UUID)
        val isViewing: Boolean = intent.getBooleanExtra(EXTRA_IS_VIEWING, false)
        return Holder(chatUuid, userUuid, isViewing)
    }

    data class Holder(val chatUuid: UUID, val userUuid: UUID, val isViewing: Boolean)
}