package de.intektor.mercury.ui

import android.os.AsyncTask
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.io.HttpManager
import de.intektor.mercury_common.client_to_server.AddContactRequest
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.server_to_client.AddContactResponse
import kotlinx.android.synthetic.main.activity_add_contact.*
import java.security.interfaces.RSAPublicKey
import java.util.*

class AddContactActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_add_contact)

        add_contact_add_button.setOnClickListener {
            attemptAddContact()
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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

        AddContactTask({ response ->
            if (response != null) {
                attemptAddContactCallback(response.isValid, response.messageKey, response.userUUID)
            }
        }, username.toString()).execute()
    }

    private fun attemptAddContactCallback(isValid: Boolean, messageKey: RSAPublicKey?, userUUID: UUID) {
        val builder = AlertDialog.Builder(this)
        builder.setMessage(if (isValid) R.string.add_contact_existing_user else R.string.add_contact_not_existing_user)
        builder.setTitle(if (isValid) R.string.add_contact_existing_user_title else R.string.add_contact_not_existing_user_title)
        builder.setPositiveButton(if (isValid) getText(R.string.add_contact_proceed) else getText(R.string.add_contact_try_again)) { _, _ ->
            if (isValid) {
                addNewContact(messageKey!!, userUUID)

                finish()
            } else {
                add_contact_username_field.requestFocus()
            }
        }

        builder.create().show()
    }

    private fun addNewContact(key: RSAPublicKey, userUUID: UUID) {
        val mercuryClient = applicationContext as MercuryClient
        ContactUtil.addContact(userUUID, add_contact_username_field.text.toString(), mercuryClient.dataBase, key)
    }

    private class AddContactTask(val callback: (AddContactResponse?) -> (Unit), val username: String) : AsyncTask<Unit, Unit, AddContactResponse?>() {
        override fun doInBackground(vararg p0: Unit?): AddContactResponse? {
            return try {
                val gson = genGson()

                val res = HttpManager.httpPost(gson.toJson(AddContactRequest(username)), AddContactRequest.TARGET)

                gson.fromJson(res, AddContactResponse::class.java)
            } catch (t: Throwable) {
                null
            }
        }

        override fun onPostExecute(result: AddContactResponse?) {
            callback.invoke(result)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
