package de.intektor.mercury.action

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.LocalBroadcastManager
import de.intektor.mercury.android.getUUIDExtra
import de.intektor.mercury_common.chat.MessageStatus
import java.util.*

/**
 * @author Intektor
 */
object ActionMessageStatusChange {

    private const val ACTION = "de.intektor.mercury.ACTION_MESSAGE_STATUS_CHANGE"

    private const val EXTRA_CHAT_UUID: String = "de.intektor.mercury.EXTRA_CHAT_UUID"
    private const val EXTRA_MESSAGE_UUID: String = "de.intektor.mercury.EXTRA_MESSAGE_UUID"
    private const val EXTRA_MESSAGE_STATUS: String = "de.intektor.mercury.EXTRA_MESSAGE_STATUS"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context, chatUuid: UUID, messageUuid: UUID, messageStatus: MessageStatus) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_CHAT_UUID, chatUuid)
                    .putExtra(EXTRA_MESSAGE_UUID, messageUuid)
                    .putExtra(EXTRA_MESSAGE_STATUS, messageStatus)

    fun launch(context: Context, chatUuid: UUID, messageUuid: UUID, messageStatus: MessageStatus) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, chatUuid, messageUuid, messageStatus))
    }

    fun getData(intent: Intent): Holder {
        val chatUuid: UUID = intent.getUUIDExtra(EXTRA_CHAT_UUID)
        val messageUuid: UUID = intent.getUUIDExtra(EXTRA_MESSAGE_UUID)
        val messageStatus: MessageStatus = intent.getSerializableExtra(EXTRA_MESSAGE_STATUS) as MessageStatus
        return Holder(chatUuid, messageUuid, messageStatus)
    }

    data class Holder(val chatUuid: UUID, val messageUuid: UUID, val messageStatus: MessageStatus)
}