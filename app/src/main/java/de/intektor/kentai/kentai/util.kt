package de.intektor.kentai.kentai

import de.intektor.kentai.KentaiClient
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
fun internalFile(name: String) = File(KentaiClient.INSTANCE.filesDir.path + "/" + name)

//TODO
val address = "192.168.178.46"
//val address = "intektor.de"

val httpAddress = "http://$address:17349/"

val httpClient = OkHttpClient.Builder()
        .readTimeout(120, TimeUnit.SECONDS)
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

fun getName(contact: Contact): String {
    return contact.name
}

fun StringBuilder.newLine() {
    this.append('\n')
}

fun FileOutputStream.dataOut() = DataOutputStream(this)

fun FileInputStream.dataIn() = DataInputStream(this)