package de.intektor.mercury.action.reference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.intektor.mercury.android.getUUIDExtra
import de.intektor.mercury.io.ChatMessageService
import java.util.*

/**
 * @author Intektor
 */
object ActionUploadReferenceFinished {

    private const val ACTION = "de.intektor.mercury.ACTION_UPLOAD_REFERENCE_FINISHED"

    private const val EXTRA_REFERENCE_UUID: String = "de.intektor.mercury.EXTRA_REFERENCE_UUID"
    private const val EXTRA_SUCCESSFUL: String = "de.intektor.mercury.EXTRA_SUCCESSFUL"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter() = IntentFilter(ACTION)

    private fun createIntent(context: Context, referenceUuid: UUID, successful: Boolean) =
            Intent()
                    .setAction(ACTION)
                    .setComponent(ComponentName(context, ChatMessageService::class.java))
                    .putExtra(EXTRA_REFERENCE_UUID, referenceUuid)
                    .putExtra(EXTRA_SUCCESSFUL, successful)

    fun launch(context: Context, referenceUuid: UUID, successful: Boolean) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, referenceUuid, successful))
    }

    fun getData(intent: Intent): Holder {
        val referenceUuid: UUID = intent.getUUIDExtra(EXTRA_REFERENCE_UUID)
        val successful: Boolean = intent.getBooleanExtra(EXTRA_SUCCESSFUL, false)
        return Holder(referenceUuid, successful)
    }

    data class Holder(val referenceUuid: UUID, val successful: Boolean)
}