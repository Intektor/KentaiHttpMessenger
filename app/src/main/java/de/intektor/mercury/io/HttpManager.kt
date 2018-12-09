package de.intektor.mercury.io

import android.os.AsyncTask
import okhttp3.*
import java.util.concurrent.TimeUnit

/**
 * @author Intektor
 */
object HttpManager {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()

    fun rawPost(json: String, target: String): ResponseBody? {
        val body = RequestBody.create(MediaType.parse("JSON"), json)
        val request = Request.Builder()
                .url(AddressHolder.HTTP_ADDRESS + target)
                .post(body)
                .build()
        val response = httpClient.newCall(request).execute()
        return response.body()
    }

    fun post(json: String, target: String): String {
        val body = rawPost(json, target)
        val response = body?.string() ?: throw RuntimeException()
        body.close()

        return response
    }


    fun asyncCall(json: String, target: String, callback: (String?) -> Unit) {
        AsyncCall(json, target, callback).execute()
    }

    private class AsyncCall(private val json: String, private val target: String, private val callback: (String?) -> Unit) : AsyncTask<Unit, Unit, String?>() {
        override fun doInBackground(vararg params: Unit?): String? {
            return try {
                post(json, target)
            } catch (t: Throwable) {
                null
            }
        }

        override fun onPostExecute(result: String?) {
            callback(result)
        }
    }
}