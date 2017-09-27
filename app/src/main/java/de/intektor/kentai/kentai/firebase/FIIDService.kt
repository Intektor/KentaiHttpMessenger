package de.intektor.kentai.kentai.firebase

import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.FirebaseInstanceIdService
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

//        val userInput = File(filesDir.path + "/username.info")
//        if (userInput.exists()) {
//            val userInfoInput = DataInputStream(userInput.inputStream())
//            userInfoInput.readUTF()
//            val userUUID = UUID.fromString(userInfoInput.readUTF())
//
//            val authKey = readPrivateKey(DataInputStream(File(filesDir.path + "/keys/authKeyPrivate.key").inputStream()))
//
//            val connection: URLConnection = URL("localhost/" + RegisterRequestToServer.TARGET).openConnection()
//            connection.connectTimeout = 15000
//            connection.doOutput = true
//
//            val gson = genGson()
//            gson.toJson(UpdateFBCMTokenRequest(userUUID, refreshedToken!!.encryptRSA(authKey)), BufferedWriter(OutputStreamWriter(connection.getOutputStream())))
//        }
    }
}