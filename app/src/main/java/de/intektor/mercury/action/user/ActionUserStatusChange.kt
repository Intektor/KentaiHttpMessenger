package de.intektor.mercury.action.user

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.LocalBroadcastManager
import de.intektor.mercury.android.getEnumExtra
import de.intektor.mercury.android.getUUIDExtra
import de.intektor.mercury.android.putEnumExtra
import de.intektor.mercury_common.tcp.server_to_client.Status
import java.util.*

/**
 * @author Intektor
 */
object ActionUserStatusChange {

    private const val ACTION = "de.intektor.mercury.ACTION_USER_STATUS_CHANGE"

    private const val EXTRA_USER_UUID: String = "de.intektor.mercury.EXTRA_USER_UUID"
    private const val EXTRA_STATUS: String = "de.intektor.mercury.EXTRA_STATUS"
    private const val EXTRA_TIME: String = "de.intektor.mercury.EXTRA_TIME"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context, userUuid: UUID, status: Status, time: Long) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_USER_UUID, userUuid)
                    .putEnumExtra(EXTRA_STATUS, status)
                    .putExtra(EXTRA_TIME, time)

    fun launch(context: Context, userUuid: UUID, status: Status, time: Long) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, userUuid, status, time))
    }

    fun getData(intent: Intent): Holder {
        val userUuid: UUID = intent.getUUIDExtra(EXTRA_USER_UUID)
        val status: Status = intent.getEnumExtra(EXTRA_STATUS)
        val time: Long = intent.getLongExtra(EXTRA_TIME, 0L)
        return Holder(userUuid, status, time)
    }

    data class Holder(val userUuid: UUID, val status: Status, val time: Long)
}