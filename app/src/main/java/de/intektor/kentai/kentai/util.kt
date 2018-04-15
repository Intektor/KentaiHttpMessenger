package de.intektor.kentai.kentai

import android.content.Context
import de.intektor.kentai.kentai.contacts.Contact
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.*
import java.util.concurrent.TimeUnit


/**
 * @author Intektor
 */
fun internalFile(name: String, context: Context) = File(context.filesDir.path + "/" + name)

//TODO
val address = "192.168.178.46"
//val address = "intektor.de"

val httpAddress = "http://$address:17349/"

val httpClient = OkHttpClient.Builder()
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .build()

fun httpPost(json: String, target: String): String {
    val body = RequestBody.create(MediaType.parse("JSON"), json)
    val request = Request.Builder()
            .url(httpAddress + target)
            .post(body)
            .build()
    val response = httpClient.newCall(request).execute()
    return response.body()?.string() ?: throw RuntimeException()
}

fun getName(contact: Contact): String = contact.name

fun StringBuilder.newLine() {
    this.append('\n')
}

fun FileOutputStream.dataOut() = DataOutputStream(this)

fun FileInputStream.dataIn() = DataInputStream(this)