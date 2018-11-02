package de.intektor.mercury.action.chat

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.intektor.mercury.android.getChatMessageExtra
import de.intektor.mercury.android.getUUIDExtra
import de.intektor.mercury.android.putExtra
import de.intektor.mercury_common.chat.ChatMessage
import java.util.*

/**
 * @author Intektor
 */
object ActionChatMessageReceived {

    private const val ACTION = "de.intektor.mercury.ACTION_CHAT_MESSAGE_RECEIVED"

    private const val EXTRA_CHAT_MESSAGE: String = "de.intektor.mercury.EXTRA_CHAT_MESSAGE"
    private const val EXTRA_CHAT_UUID: String = "de.intektor.mercury.EXTRA_CHAT_UUID"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context, chatMessage: ChatMessage, chatUuid: UUID) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_CHAT_MESSAGE, chatMessage)
                    .putExtra(EXTRA_CHAT_UUID, chatUuid)

    fun launch(context: Context, chatMessage: ChatMessage, chatUuid: UUID) {
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, chatMessage, chatUuid))
    }

    fun getData(intent: Intent): Holder {
        val chatMessage: ChatMessage = intent.getChatMessageExtra(EXTRA_CHAT_MESSAGE)
        val chatUuid: UUID = intent.getUUIDExtra(EXTRA_CHAT_UUID)
        return Holder(chatMessage, chatUuid)
    }

    data class Holder(val chatMessage: ChatMessage, val chatUuid: UUID)
}