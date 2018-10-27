package de.intektor.mercury.action.group

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.LocalBroadcastManager
import de.intektor.mercury.android.getGroupModificationExtra
import de.intektor.mercury.android.getUUIDExtra
import de.intektor.mercury.android.putExtra
import de.intektor.mercury_common.chat.data.group_modification.GroupModification
import java.util.*

/**
 * @author Intektor
 */
object ActionGroupModificationReceived {

    private const val ACTION = "de.intektor.mercury.ACTION_GROUP_MODIFICATION_RECEIVED"

    private const val EXTRA_CHAT_UUID: String = "de.intektor.mercury.EXTRA_CHAT_UUID"
    private const val EXTRA_MODIFICATION: String = "de.intektor.mercury.EXTRA_MODIFICATION"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context, chatUuid: UUID, modification: GroupModification) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_CHAT_UUID, chatUuid)
                    .putExtra(EXTRA_MODIFICATION, modification)

    fun launch(context: Context, chatUuid: UUID, modification: GroupModification) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, chatUuid, modification))
    }

    fun getData(intent: Intent): Holder {
        val chatUuid: UUID = intent.getUUIDExtra(EXTRA_CHAT_UUID)
        val modification: GroupModification = intent.getGroupModificationExtra(EXTRA_MODIFICATION)
        return Holder(chatUuid, modification)
    }

    data class Holder(val chatUuid: UUID, val modification: GroupModification)
}