package de.intektor.mercury.action.profpic

import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * @author Intektor
 */
object ActionProfilePictureUploaded {

    private const val ACTION = "de.intektor.mercury.ACTION_PROFILE_PICTURE_UPLOADED"


    fun isAction(intent: Intent) = intent.action == ACTION

    fun getFilter(): IntentFilter = IntentFilter(ACTION)

    private fun createIntent(context: Context) =
            Intent()
                    .setAction(ACTION)

    fun launch(context: Context) {
        context.sendBroadcast(createIntent(context))
    }
}