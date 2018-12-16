package de.intektor.mercury.action.chat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.intektor.mercury.android.getUUIDExtra
import java.util.*

/**
 * @author Intektor
 */
object ActionChatMessageNotification {

    private const val ACTION = "de.intektor.mercury.ACTION_CHAT_MESSAGE_NOTIFICATION"

    private const val EXTRA_CHAT_UUID: String = "de.intektor.mercury.EXTRA_CHAT_UUID"
    private const val EXTRA_MESSAGE_UUID: String = "de.intektor.mercury.EXTRA_MESSAGE_UUID"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context, chatUuid: UUID, messageUuid: UUID) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_CHAT_UUID, chatUuid)
                    .putExtra(EXTRA_MESSAGE_UUID, messageUuid)

    fun launch(context: Context, chatUuid: UUID, messageUuid: UUID) {
        context.sendOrderedBroadcast(createIntent(context, chatUuid, messageUuid), null)
    }

    fun getData(intent: Intent): Holder {
        val chatUuid: UUID = intent.getUUIDExtra(EXTRA_CHAT_UUID)
        val messageUuid: UUID = intent.getUUIDExtra(EXTRA_MESSAGE_UUID)
        return Holder(chatUuid, messageUuid)
    }

    data class Holder(val chatUuid: UUID, val messageUuid: UUID)
}