package de.intektor.mercury.firebase

import android.content.Context
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.io.HttpManager
import de.intektor.mercury_common.client_to_server.UpdateFBCMTokenRequest
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.server_to_client.UpdateFBCMTokenResponse
import de.intektor.mercury_common.util.sign

/**
 * @author Intektor
 */
object UploadTokenTask {
    fun uploadToken(context: Context, token: String): Boolean {
        val client = ClientPreferences.getClientUUID(context)
        val privateAuthKey = ClientPreferences.getPrivateAuthKey(context)

        val gson = genGson()
        val s = gson.toJson(UpdateFBCMTokenRequest(client, sign(token, privateAuthKey), token))

        val rMessage = HttpManager.post(s, UpdateFBCMTokenRequest.TARGET)
        val response = gson.fromJson(rMessage, UpdateFBCMTokenResponse::class.java)
        return response.successful
    }
}