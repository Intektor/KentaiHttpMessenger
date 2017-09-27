package de.intektor.kentai.kentai

import de.intektor.kentai.KentaiClient
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.util.concurrent.TimeUnit


/**
 * @author Intektor
 */
fun internalFile(name: String) = File(KentaiClient.INSTANCE.filesDir.path + "/" + name)

val address = "http://192.168.178.46:17349/"

val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
        .build()

fun httpPost(json: String, target: String): String {
    val body = RequestBody.create(MediaType.parse("JSON"), json)
    val request = Request.Builder()
            .url(address + target)
            .post(body)
            .build()
    val response = httpClient.newCall(request).execute()
    return response.body()?.string() ?: throw RuntimeException()
}