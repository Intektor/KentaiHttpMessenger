package de.intektor.kentai

import android.content.Intent
import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import com.google.common.io.BaseEncoding
import de.intektor.kentai.kentai.httpPost
import de.intektor.kentai_http_common.client_to_server.AddContactRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.server_to_client.AddContactResponse
import kotlinx.android.synthetic.main.activity_add_contact.*
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.URL
import java.net.URLConnection
import java.security.interfaces.RSAPublicKey
import java.util.*

class AddContactActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_contact)

        add_contact_add_button.setOnClickListener({
            attemptAddContact()
        })
    }

    private fun attemptAddContact() {
        val username = add_contact_username_field.text
        if (username.length !in 5..15 || !username.matches(("\\w+".toRegex()))) {
            val builder = AlertDialog.Builder(this)
            builder.setMessage(R.string.register_error_invalid_username_message)
            builder.setTitle(R.string.register_error_invalid_username_title)
            builder.setPositiveButton("OK") { _, _ ->
                add_contact_username_field.requestFocus()
            }
            builder.create().show()
            return
        }

        object : AsyncTask<Unit, Unit, AddContactResponse>() {
            override fun doInBackground(vararg params: Unit): AddContactResponse {
                val gson = genGson()

                val res = httpPost(gson.toJson(AddContactRequest(username.toString())), AddContactRequest.TARGET)

                return gson.fromJson(res, AddContactResponse::class.java)
            }

            override fun onPostExecute(result: AddContactResponse) {
                attemptAddContactCallback(result.isValid, result.messageKey, result.userUUID)
            }
        }.execute()
    }

    fun attemptAddContactCallback(isValid: Boolean, messageKey: RSAPublicKey?, userUUID: UUID) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(if (isValid) R.string.add_contact_existing_user else R.string.add_contact_not_existing_user)
        builder.setTitle(if (isValid) R.string.add_contact_existing_user_title else R.string.add_contact_not_existing_user_title)
        builder.setPositiveButton(if (isValid) getText(R.string.add_contact_proceed) else getText(R.string.add_contact_try_again)) { _, _ ->
            if (isValid) {
                addNewContact(messageKey!!, userUUID)
                startActivity(Intent(this, OverviewActivity::class.java))
            } else {
                add_contact_username_field.requestFocus()
            }
        }

        builder.create().show()
    }

    private fun addNewContact(key: RSAPublicKey, userUUID: UUID) {
        val statement = KentaiClient.INSTANCE.dataBase.compileStatement("INSERT INTO contacts (username, message_key, alias, user_uuid) VALUES (?, ?, ?, ?)")
        statement.bindString(1, add_contact_username_field.text.toString())
        statement.bindString(2, BaseEncoding.base64().encode(key.encoded))
        statement.bindString(3, add_contact_username_field.text.toString())
        statement.bindString(4, userUUID.toString())
        statement.execute()
    }
}
