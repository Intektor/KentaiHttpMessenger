package de.intektor.mercury.action.chat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.intektor.mercury.android.getUUIDExtra
import java.util.*

/**
 * @author Intektor
 */
object ActionInitChatFinished {

    private const val ACTION = "de.intektor.mercury.ACTION_INIT_CHAT_FINISHED"

    private const val EXTRA_CHAT_UUID: String = "de.intektor.mercury.EXTRA_CHAT_UUID"
    private const val EXTRA_SUCCESSFUL: String = "de.intektor.mercury.EXTRA_SUCCESSFUL"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context, chatUuid: UUID, successful: Boolean) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_CHAT_UUID, chatUuid)
                    .putExtra(EXTRA_SUCCESSFUL, successful)

    fun launch(context: Context, chatUuid: UUID, successful: Boolean) {
        context.sendBroadcast(createIntent(context, chatUuid, successful))
    }

    fun getData(intent: Intent): Holder {
        val chatUuid: UUID = intent.getUUIDExtra(EXTRA_CHAT_UUID)
        val successful: Boolean = intent.getBooleanExtra(EXTRA_SUCCESSFUL, false)
        return Holder(chatUuid, successful)
    }

    data class Holder(val chatUuid: UUID, val successful: Boolean)
}