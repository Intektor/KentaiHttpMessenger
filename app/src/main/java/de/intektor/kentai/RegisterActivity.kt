package de.intektor.kentai

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import androidmads.library.qrgenearator.QRGContents
import androidmads.library.qrgenearator.QRGEncoder
import com.google.common.io.BaseEncoding
import com.google.firebase.iid.FirebaseInstanceId
import de.intektor.kentai.kentai.contacts.addContact
import de.intektor.kentai.kentai.httpPost
import de.intektor.kentai.kentai.internalFile
import de.intektor.kentai_http_common.client_to_server.CheckUsernameAvailableRequestToServer
import de.intektor.kentai_http_common.client_to_server.RegisterRequestToServer
import de.intektor.kentai_http_common.client_to_server.UpdateFBCMTokenRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.CheckUsernameAvailableResponseToClient
import de.intektor.kentai_http_common.server_to_client.RegisterRequestResponseToClient
import de.intektor.kentai_http_common.util.*
import kotlinx.android.synthetic.main.activity_register.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.util.*
import kotlin.concurrent.thread


/**
 * A login screen that offers login via email/password.
 */
class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        register_register_button.setOnClickListener({
            doAvailabilityCheck()
        })

    }

    private fun doAvailabilityCheck() {
        val username = register_username_field.text.toString()
        if (username.length !in 5..20 || !username.matches(("\\w+".toRegex()))) {
            val builder = AlertDialog.Builder(this)
            builder.setMessage(R.string.register_error_invalid_username_message)
            builder.setTitle(R.string.register_error_invalid_username_title)
            builder.setPositiveButton("OK") { _, _ ->
                register_username_field.requestFocus()
            }
            builder.create().show()
            return
        }

        CheckUsernameAvailableTask().execute(username to this)
    }

    class CheckUsernameAvailableTask : AsyncTask<Pair<String, RegisterActivity>, Void, Pair<CheckUsernameAvailableResponseToClient?, RegisterActivity>>() {
        override fun doInBackground(vararg params: Pair<String, RegisterActivity>): Pair<CheckUsernameAvailableResponseToClient?, RegisterActivity> {
            return try {
                val gson = genGson()
                val response = httpPost(gson.toJson(CheckUsernameAvailableRequestToServer(params[0].first)), CheckUsernameAvailableRequestToServer.TARGET)
                gson.fromJson(response, CheckUsernameAvailableResponseToClient::class.java) to params[0].second
            } catch (t: Throwable) {
                Log.e("ERROR", "Unable to check the availability of the username ${params[0].first}", t)
                Pair(null, params[0].second)
            }
        }

        override fun onPostExecute(response: Pair<CheckUsernameAvailableResponseToClient?, RegisterActivity>) {
            val r = response.first
            val activity = response.second
            if (r == null) {
                val builder = AlertDialog.Builder(response.second)
                builder.setTitle(R.string.register_register_error_title)
                builder.setMessage(R.string.register_error_while_connecting)
                builder.setPositiveButton("OK", { _, _ ->

                })
                builder.create().show()
                return
            }
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(if (r.available) R.string.register_username_available_title else R.string.register_username_not_available_title)
            builder.setMessage(if (r.available) R.string.register_username_available else R.string.register_username_not_available)
            builder.setPositiveButton(R.string.register_username_available_proceed, { _, _ ->
                if (r.available) {
                    activity.attemptRegister()
                } else {
                    activity.register_username_field.requestFocus()
                }
            })
            builder.create().show()
        }
    }

    private fun attemptRegister() {
        val messagePair = generateMessageKeys()
        val authPair = generateAuthKeys()

        AttemptRegisterTask().execute(Triple(this, messagePair, authPair))
    }

    class AttemptRegisterTask : AsyncTask<Triple<RegisterActivity, KeyPair, KeyPair>, Unit, Triple<RegisterRequestResponseToClient?, RegisterActivity, KeyPair?>>() {
        override fun doInBackground(vararg params: Triple<RegisterActivity, KeyPair, KeyPair>): Triple<RegisterRequestResponseToClient?, RegisterActivity, KeyPair?> {
            val activity = params[0].first
            val messagePair = params[0].second
            val authPair = params[0].third
            return try {
                val gson = genGson()
                val response = httpPost(gson.toJson(RegisterRequestToServer(activity.register_username_field.text.toString(), authPair.public as RSAPublicKey,
                        messagePair.public as RSAPublicKey, FirebaseInstanceId.getInstance().token)), RegisterRequestToServer.TARGET)
                Triple(gson.fromJson(response, RegisterRequestResponseToClient::class.java), activity, messagePair)
            } catch (t: Throwable) {
                Log.e("ERROR", "Unable to register", t)
                Triple(null, activity, null)
            }
        }

        override fun onPostExecute(response: Triple<RegisterRequestResponseToClient?, RegisterActivity, KeyPair?>) {
            if (response.first == null || response.third == null) {
                val builder = AlertDialog.Builder(response.second)
                builder.setTitle(R.string.register_register_error_title)
                builder.setMessage(R.string.register_error_while_connecting)
                builder.setPositiveButton("OK", { _, _ ->

                })
                builder.create().show()
                return
            }
            val r = response.first!!
            val activity = response.second
            val messagePair = response.third!!
            if (r.type != RegisterRequestResponseToClient.Type.SUCCESS) {
                val builder = AlertDialog.Builder(activity)
                builder.setTitle(R.string.register_register_error_title)
                builder.setMessage(r.type.name)
                builder.setPositiveButton("OK", { _, _ ->

                })
                builder.create().show()
            } else {
                activity.initUser(r.username, r.userUUID!!, messagePair.public)

                activity.startActivity(Intent(activity, OverviewActivity::class.java))
            }
        }
    }

    fun initUser(username: String, userUUID: UUID, publicMessageKey: PublicKey) {
        val dataOut = DataOutputStream(File(filesDir.path + "/username.info").outputStream())
        dataOut.writeUTF(username)
        dataOut.writeUTF(userUUID.toString())
        dataOut.close()
        KentaiClient.INSTANCE.username = username
        KentaiClient.INSTANCE.userUUID = userUUID

        addContact(userUUID, register_username_field.text.toString(), KentaiClient.INSTANCE.dataBase, publicMessageKey)
    }

    private fun generateAuthKeys(): KeyPair {
        val keyGenerator = KeyPairGenerator.getInstance("RSA")
        keyGenerator.initialize(2048)
        val keyPair = keyGenerator.generateKeyPair()
        internalFile("keys/").mkdirs()

        var output = DataOutputStream(internalFile("keys/authKeyPublic.key").outputStream())
        (keyPair.public as RSAPublicKey).writeKey(output)

        output = DataOutputStream(internalFile("keys/authKeyPrivate.key").outputStream())
        (keyPair.private as RSAPrivateKey).writeKey(output)

        KentaiClient.INSTANCE.privateAuthKey = keyPair.private
        KentaiClient.INSTANCE.publicAuthKey = keyPair.public

        return keyPair
    }

    private fun generateMessageKeys(): KeyPair {
        val keyGenerator = KeyPairGenerator.getInstance("RSA")
        keyGenerator.initialize(2048)
        val keyPair = keyGenerator.generateKeyPair()
        internalFile("keys/").mkdirs()

        var output = DataOutputStream(internalFile("keys/encryptionKeyPublic.key").outputStream())
        (keyPair.public as RSAPublicKey).writeKey(output)

        output = DataOutputStream(internalFile("keys/encryptionKeyPrivate.key").outputStream())
        (keyPair.private as RSAPrivateKey).writeKey(output)

        KentaiClient.INSTANCE.privateMessageKey = keyPair.private
        KentaiClient.INSTANCE.publicMessageKey = keyPair.public

        return keyPair
    }

    fun onClickTransportText(view: View) {
        register_qr_image.visibility = View.VISIBLE

        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipAddress = wm.connectionInfo.ipAddress
        val ip = String.format("%d.%d.%d.%d", ipAddress and 0xff, ipAddress shr 8 and 0xff, ipAddress shr 16 and 0xff, ipAddress shr 24 and 0xff)

        val gen = KeyPairGenerator.getInstance("RSA")
        gen.initialize(2048)
        val pair = gen.generateKeyPair()
        val publicKey = BaseEncoding.base64().encode(pair.public.encoded)
        val pK = pair.private

        val finalString = "$ip\u0000$publicKey"

        val encoder = QRGEncoder(finalString, null, QRGContents.Type.TEXT, 4)
        val bitmap = encoder.encodeAsBitmap()
        register_qr_image.setImageBitmap(bitmap)

        thread {
            val context = this@RegisterActivity
            val serverSocket = ServerSocket(37455)
            serverSocket.soTimeout = 0
            val socket = serverSocket.accept()
            val dataIn = DataInputStream(socket.getInputStream())

            val keyEncrypted = dataIn.readUTF()
            val key = keyEncrypted.decryptRSA(pair.private)

            if (key != "Transport") {
                serverSocket.close()
                socket.close()
                return@thread
            }

            val aesKey = dataIn.readUTF().decryptRSA(pK).toAESKey()
            val initVector = dataIn.readUTF().decryptRSA(pK).toByteArray(Charsets.UTF_8)

            val username = dataIn.readUTF().decryptAES(aesKey, initVector)
            val userUUID = dataIn.readUTF().decryptAES(aesKey, initVector).toUUID()

            val privateAuthKey = dataIn.readUTF().decryptAES(aesKey, initVector).toPrivateKey()
            val publicAuthKey = dataIn.readUTF().decryptAES(aesKey, initVector).toKey()

            val privateMessageKey = dataIn.readUTF().decryptAES(aesKey, initVector).toPrivateKey()
            val publicMessageKey = dataIn.readUTF().decryptAES(aesKey, initVector).toKey()

            var output = DataOutputStream(internalFile("keys/authKeyPublic.key").outputStream())
            (publicAuthKey as RSAPublicKey).writeKey(output)

            output = DataOutputStream(internalFile("keys/authKeyPrivate.key").outputStream())
            (privateAuthKey as RSAPrivateKey).writeKey(output)

            output = DataOutputStream(internalFile("keys/encryptionKeyPublic.key").outputStream())
            (publicMessageKey as RSAPublicKey).writeKey(output)

            output = DataOutputStream(internalFile("keys/encryptionKeyPrivate.key").outputStream())
            (privateMessageKey as RSAPrivateKey).writeKey(output)

            initUser(username, userUUID, publicMessageKey)

            val encryptedToken = FirebaseInstanceId.getInstance().token!!.encryptRSA(privateAuthKey)

            val gson = genGson()
            httpPost(gson.toJson(UpdateFBCMTokenRequest(userUUID, encryptedToken)), UpdateFBCMTokenRequest.TARGET)

            context.startActivity(Intent(context, OverviewActivity::class.java))
        }
    }
}

