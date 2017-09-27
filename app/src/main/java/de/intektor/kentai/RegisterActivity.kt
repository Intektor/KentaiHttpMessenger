package de.intektor.kentai

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.google.firebase.iid.FirebaseInstanceId
import de.intektor.kentai.kentai.contacts.addContact
import de.intektor.kentai.kentai.httpPost
import de.intektor.kentai.kentai.internalFile
import de.intektor.kentai_http_common.client_to_server.CheckUsernameAvailableRequestToServer
import de.intektor.kentai_http_common.client_to_server.RegisterRequestToServer
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.CheckUsernameAvailableResponseToClient
import de.intektor.kentai_http_common.server_to_client.RegisterRequestResponseToClient
import de.intektor.kentai_http_common.util.writeKey
import kotlinx.android.synthetic.main.activity_register.*
import java.io.DataOutputStream
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey


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

        object : AsyncTask<Void, Void, CheckUsernameAvailableResponseToClient>() {
            override fun doInBackground(vararg params: Void): CheckUsernameAvailableResponseToClient {
                val gson = genGson()
                val response = httpPost(gson.toJson(CheckUsernameAvailableRequestToServer(username)), CheckUsernameAvailableRequestToServer.TARGET)
                return gson.fromJson(response, CheckUsernameAvailableResponseToClient::class.java)
            }

            override fun onPostExecute(response: CheckUsernameAvailableResponseToClient) {
                val builder = AlertDialog.Builder(this@RegisterActivity)
                builder.setTitle(if (response.available) R.string.register_username_available_title else R.string.register_username_not_available_title)
                builder.setMessage(if (response.available) R.string.register_username_available else R.string.register_username_not_available)
                builder.setPositiveButton(R.string.register_username_available_proceed, { _, _ ->
                    if (response.available) {
                        attemptRegister()
                    } else {
                        register_username_field.requestFocus()
                    }
                })
                builder.create().show()
            }
        }.execute()
    }

    private fun attemptRegister() {
        val messagePair = generateMessageKeys()
        val authPair = generateAuthKeys()

        object : AsyncTask<Unit, Unit, RegisterRequestResponseToClient>() {
            override fun doInBackground(vararg params: Unit?): RegisterRequestResponseToClient {
                val gson = genGson()
                val response = httpPost(gson.toJson(RegisterRequestToServer(register_username_field.text.toString(), authPair.public as RSAPublicKey,
                        messagePair.public as RSAPublicKey, FirebaseInstanceId.getInstance().token)), RegisterRequestToServer.TARGET)

                Log.v("VERBOSE", response)
                return gson.fromJson(response, RegisterRequestResponseToClient::class.java)
            }

            override fun onPostExecute(response: RegisterRequestResponseToClient) {
                if (response.type != RegisterRequestResponseToClient.Type.SUCCESS) {
                    val builder = AlertDialog.Builder(this@RegisterActivity)
                    builder.setTitle(R.string.register_register_error_title)
                    builder.setMessage(response.type.name)
                    builder.setPositiveButton("OK", { _, _ ->

                    })
                    builder.create().show()
                } else {
                    val dataOut = DataOutputStream(File(filesDir.path + "/username.info").outputStream())
                    dataOut.writeUTF(response.username)
                    dataOut.writeUTF(response.userUUID.toString())
                    dataOut.close()
                    KentaiClient.INSTANCE.username = response.username
                    KentaiClient.INSTANCE.userUUID = response.userUUID!!

                    addContact(response.userUUID!!, register_username_field.text.toString(), KentaiClient.INSTANCE.dataBase, messagePair.public)

                    startActivity(Intent(applicationContext, OverviewActivity::class.java))
                }
            }
        }.execute()
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
}

