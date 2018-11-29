package de.intektor.mercury.action.group

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.intektor.mercury.android.getChatInfoExtra
import de.intektor.mercury.android.getUUIDExtra
import de.intektor.mercury.chat.model.ChatInfo
import java.util.*

/**
 * @author Intektor
 */
object ActionGroupInvite {

    private const val ACTION = "de.intektor.mercury.ACTION_GROUP_INVITE"

    private const val EXTRA_GROUP_NAME: String = "de.intektor.mercury.EXTRA_GROUP_NAME"
    private const val EXTRA_SENDER_UUID: String = "de.intektor.mercury.EXTRA_SENDER_UUID"
    private const val EXTRA_CHAT_INFO: String = "de.intektor.mercury.EXTRA_CHAT_INFO"
    private const val EXTRA_FROM_CHAT: String = "de.intektor.mercury.EXTRA_FROM_CHAT"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context, groupName: String, senderUuid: UUID, chatInfo: ChatInfo, fromChat: UUID) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_GROUP_NAME, groupName)
                    .putExtra(EXTRA_SENDER_UUID, senderUuid)
                    .putExtra(EXTRA_CHAT_INFO, chatInfo)
                    .putExtra(EXTRA_FROM_CHAT, fromChat)

    fun launch(context: Context, groupName: String, senderUuid: UUID, chatInfo: ChatInfo, fromChat: UUID) {
        context.sendBroadcast(createIntent(context, groupName, senderUuid, chatInfo, fromChat))
    }

    fun getData(intent: Intent): Holder {
        val groupName: String = intent.getStringExtra(EXTRA_GROUP_NAME)
        val senderUuid: UUID = intent.getUUIDExtra(EXTRA_SENDER_UUID)
        val chatInfo: ChatInfo = intent.getChatInfoExtra(EXTRA_CHAT_INFO)
        val fromChat: UUID = intent.getUUIDExtra(EXTRA_FROM_CHAT)
        return Holder(groupName, senderUuid, chatInfo, fromChat)
    }

    data class Holder(val groupName: String, val senderUuid: UUID, val chatInfo: ChatInfo, val fromChat: UUID)
}