package de.intektor.mercury.action.tcp

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

/**
 * @author Intektor
 */
object ActionDirectConnectionClosed {

    private const val ACTION = "de.intektor.mercury.ACTION_DIRECT_CONNECTION_CLOSED"

    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context) =
            Intent()
                .setAction(ACTION)

    fun launch(context: Context) {
        LocalBroadcastManager.getInstance(context).sendBroadcast(createIntent(context))
    }
}