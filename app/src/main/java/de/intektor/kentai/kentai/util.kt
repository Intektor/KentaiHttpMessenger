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
import okhttp3.OkHttpClient
import java.io.File
import android.os.AsyncTask.execute
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody


/**
 * @author Intektor
 */
fun internalFile(name: String) = File(KentaiClient.INSTANCE.filesDir.path + "/" + name)

val address = "http://192.168.178.31:17349/"

private val httpClient = OkHttpClient()

fun httpPost(json: String, target: String): String {
    val body = RequestBody.create(MediaType.parse("JSON"), json)
    val request = Request.Builder()
            .url(address + target)
            .post(body)
            .build()
    val response = httpClient.newCall(request).execute()
    return response.body()?.string() ?: throw RuntimeException()
}