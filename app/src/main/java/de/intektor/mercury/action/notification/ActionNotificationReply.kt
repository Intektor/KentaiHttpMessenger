package de.intektor.mercury.action.notification

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import de.intektor.mercury.android.getUUIDExtra
import java.util.*

/**
 * @author Intektor
 */
object ActionNotificationReply {

    private const val ACTION = "de.intektor.mercury.ACTION_NOTIFICATION_REPLY"

    private const val EXTRA_CHAT_UUID: String = "de.intektor.mercury.EXTRA_CHAT_UUID"
    private const val EXTRA_NOTIFICATION_ID: String = "de.intektor.mercury.EXTRA_NOTIFICATION_ID"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    fun createIntent(context: Context, chatUuid: UUID, notificationId: Int) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_CHAT_UUID, chatUuid)
                    .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun launch(context: Context, chatUuid: UUID, notificationId: Int) {
        context.sendBroadcast(createIntent(context, chatUuid, notificationId))
    }

    fun getData(intent: Intent): Holder {
        val chatUuid: UUID = intent.getUUIDExtra(EXTRA_CHAT_UUID)
        val notificationId: Int = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        return Holder(chatUuid, notificationId)
    }

    data class Holder(val chatUuid: UUID, val notificationId: Int)
}