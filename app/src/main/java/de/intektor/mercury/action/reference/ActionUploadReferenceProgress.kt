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
object ActionUploadReferenceProgress {

    private const val ACTION = "de.intektor.mercury.ACTION_UPLOAD_REFERENCE_PROGRESS"

    private const val EXTRA_REFERENCE_UUID: String = "de.intektor.mercury.EXTRA_REFERENCE_UUID"
    private const val EXTRA_PROGRESS: String = "de.intektor.mercury.EXTRA_PROGRESS"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter() = IntentFilter(ACTION)

    private fun createIntent(context: Context, referenceUuid: UUID, progress: Double) =
            Intent()
                    .setAction(ACTION)
                    .putExtra(EXTRA_REFERENCE_UUID, referenceUuid)
                    .putExtra(EXTRA_PROGRESS, progress)

    fun launch(context: Context, referenceUuid: UUID, progress: Double) {
        androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context, referenceUuid, progress))
    }

    fun getData(intent: Intent): Holder {
        val referenceUuid: UUID = intent.getUUIDExtra(EXTRA_REFERENCE_UUID)
        val progress: Double = intent.getDoubleExtra(EXTRA_PROGRESS, 0.0)
        return Holder(referenceUuid, progress)
    }

    data class Holder(val referenceUuid: UUID, val progress: Double)
}