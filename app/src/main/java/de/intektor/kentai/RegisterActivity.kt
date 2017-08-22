package de.intektor.kentai

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import com.google.common.io.BaseEncoding
import com.google.firebase.iid.FirebaseInstanceId
import de.intektor.kentai.kentai.internalFile
import de.intektor.kentai_http_common.client_to_server.CheckUsernameAvailableRequestToServer
import de.intektor.kentai_http_common.client_to_server.RegisterRequestToServer
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.CheckUsernameAvailableResponseToClient
import de.intektor.kentai_http_common.server_to_client.RegisterRequestResponseToClient
import de.intektor.kentai_http_common.util.writeKey
import kotlinx.android.synthetic.main.activity_register.*
import java.io.BufferedWriter
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.net.URLConnection
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

        object : AsyncTask<Void, Void, Unit>() {
            override fun doInBackground(vararg params: Void) {
                val connection: URLConnection = URL("localhost/" + CheckUsernameAvailableRequestToServer.TARGET).openConnection()
                connection.readTimeout = 15000
                connection.connectTimeout = 15000
                connection.doInput = true
                connection.doOutput = true

                val gson = genGson()
                gson.toJson(CheckUsernameAvailableRequestToServer(username), BufferedWriter(OutputStreamWriter(connection.getOutputStream())))

                val response = gson.fromJson(InputStreamReader(connection.getInputStream()), CheckUsernameAvailableResponseToClient::class.java)

                val builder = AlertDialog.Builder(applicationContext)
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

        object : AsyncTask<Unit, Unit, Unit>() {
            override fun doInBackground(vararg params: Unit?) {
                val connection: URLConnection = URL("localhost/" + RegisterRequestToServer.TARGET).openConnection()
                connection.readTimeout = 15000
                connection.connectTimeout = 15000
                connection.doInput = true
                connection.doOutput = true

                val gson = genGson()
                gson.toJson(RegisterRequestToServer(register_username_field.text.toString(), authPair.public as RSAPublicKey, messagePair.public as RSAPublicKey, FirebaseInstanceId.getInstance().token), BufferedWriter(OutputStreamWriter(connection.getOutputStream())))

                val response = gson.fromJson(InputStreamReader(connection.getInputStream()), RegisterRequestResponseToClient::class.java)
                if (response.type != RegisterRequestResponseToClient.Type.SUCCESS) {
                    val builder = AlertDialog.Builder(applicationContext)
                    builder.setTitle(R.string.register_register_error_title)
                    builder.setMessage(response.type.name)
                    builder.setPositiveButton("OK", { _, _ ->

                    })
                    builder.create().show()
                } else {
                    val dataOut = DataOutputStream(internalFile("username.info").outputStream())
                    dataOut.writeUTF(response.username)
                    dataOut.writeUTF(response.userUUID.toString())
                    dataOut.close()
                    KentaiClient.INSTANCE.username = response.username
                    KentaiClient.INSTANCE.userUUID = response.userUUID!!

                    startActivity(Intent(applicationContext, OverviewActivity::class.java))
                }
            }
        }.execute()

        val statement = KentaiClient.INSTANCE.dataBase.compileStatement("INSERT INTO contacts (username, alias, message_key) VALUES (?, ?, ?)")
        statement.bindString(1, register_username_field.text.toString())
        statement.bindString(2, "")
        statement.bindString(3, BaseEncoding.base64().encode(messagePair.public.encoded))
        statement.execute()
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

        return keyPair
    }
}
