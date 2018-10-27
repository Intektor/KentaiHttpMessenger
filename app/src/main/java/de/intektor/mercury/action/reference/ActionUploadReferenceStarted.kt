package de.intektor.mercury.action.reference

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.support.v4.content.LocalBroadcastManager
import de.intektor.mercury.android.getUUIDExtra
import java.util.*

/**
 * @author Intektor
 */
object ActionUploadReferenceStarted {

    private const val ACTION = "de.intektor.mercury.ACTION_UPLOAD_REFERENCE_STARTED"

    private const val EXTRA_REFERENCE_UUID: String = "de.intektor.mercury.EXTRA_REFERENCE_UUID"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter() = IntentFilter(ACTION)

    private fun createIntent(context: Context, referenceUuid: UUID) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_REFERENCE_UUID, referenceUuid)

    fun launch(context: Context, referenceUuid: UUID) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, referenceUuid))
    }

    fun getData(intent: Intent): Holder {
        val referenceUuid: UUID = intent.getUUIDExtra(EXTRA_REFERENCE_UUID)
        return Holder(referenceUuid)
    }

    data class Holder(val referenceUuid: UUID)
}