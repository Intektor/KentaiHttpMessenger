package de.intektor.kentai.kentai

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import de.intektor.kentai.KentaiClient
import java.io.File

/**
 * @author Intektor
 */
fun internalFile(name: String) = File(KentaiClient.INSTANCE.filesDir.path + "/" + name)