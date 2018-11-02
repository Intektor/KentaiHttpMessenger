package de.intektor.mercury.action.reference

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import de.intektor.mercury.android.getUUIDExtra
import java.util.*

/**
 * @author Intektor
 */
object ActionDownloadReferenceFinished {

    private const val ACTION = "de.intektor.mercury.ACTION_DOWNLOAD_REFERENCE_FINISHED"

    private const val EXTRA_REFERENCE_UUID: String = "de.intektor.mercury.EXTRA_REFERENCE_UUID"
    private const val EXTRA_SUCCESSFUL: String = "de.intektor.mercury.EXTRA_SUCCESSFUL"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context, referenceUuid: UUID, successful: Boolean) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_REFERENCE_UUID, referenceUuid)
                    .putExtra(EXTRA_SUCCESSFUL, successful)

    fun launch(context: Context, referenceUuid: UUID, successful: Boolean) {
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, referenceUuid, successful))
    }

    fun getData(intent: Intent): Holder {
        val referenceUuid: UUID = intent.getUUIDExtra(EXTRA_REFERENCE_UUID)
        val successful: Boolean = intent.getBooleanExtra(EXTRA_SUCCESSFUL, false)
        return Holder(referenceUuid, successful)
    }

    data class Holder(val referenceUuid: UUID, val successful: Boolean)
}