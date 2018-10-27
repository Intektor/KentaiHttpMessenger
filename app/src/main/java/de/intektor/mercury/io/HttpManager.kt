package de.intektor.mercury.io

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.util.concurrent.TimeUnit

/**
 * @author Intektor
 */
object HttpManager {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

    fun httpPost(json: String, target: String): String {
        val body = RequestBody.create(MediaType.parse("JSON"), json)
        val request = Request.Builder()
                .url(AddressHolder.HTTP_ADDRESS + target)
                .post(body)
                .build()
        val response = httpClient.newCall(request).execute()
        return response.body()?.string() ?: throw RuntimeException()
    }
}