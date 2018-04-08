package de.intektor.kentai.kentai.firebase

import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.chat.hasClient
import de.intektor.kentai.kentai.chat.readClientContact
import de.intektor.kentai.kentai.httpClient
import de.intektor.kentai.kentai.httpPost
import de.intektor.kentai_http_common.client_to_server.RegisterRequestToServer
import de.intektor.kentai_http_common.client_to_server.UpdateFBCMTokenRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.util.encryptRSA
import de.intektor.kentai_http_common.util.readPrivateKey
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.File
import java.io.OutputStreamWriter
import java.net.URL
import java.net.URLConnection
import java.util.*

/**
 * @author Intektor
 */
class FIIDService : FirebaseInstanceIdService() {
    override fun onTokenRefresh() {
        val refreshedToken = FirebaseInstanceId.getInstance().token

        if (hasClient(this)) {
            val kentaiClient = applicationContext as KentaiClient

            val gson = genGson()
            val s = gson.toJson(UpdateFBCMTokenRequest(kentaiClient.userUUID, refreshedToken!!.encryptRSA(kentaiClient.privateAuthKey!!)))
            httpPost(s, UpdateFBCMTokenRequest.TARGET)
        }
    }
}