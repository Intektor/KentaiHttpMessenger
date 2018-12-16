package de.intektor.mercury.ui.register

import android.os.AsyncTask
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import com.google.firebase.iid.FirebaseInstanceId
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.io.HttpManager
import de.intektor.mercury_common.client_to_server.RegisterRequestToServer
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.server_to_client.RegisterRequestResponseToClient
import de.intektor.mercury_common.util.writeUUID
import kotlinx.android.synthetic.main.activity_register.*
import java.io.DataOutputStream
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.interfaces.RSAPublicKey
import java.util.*


/**
 * A login screen that offers login via email/password.
 */
class RegisterActivity : AppCompatActivity(), IArrowPressedListener {

    private lateinit var adapter: RegisterFragmentAdapter

    var username: String? = null
    var captchaToken: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_register)

        adapter = RegisterFragmentAdapter(supportFragmentManager)

        activity_register_vp.adapter = adapter
    }

    private class RegisterFragmentAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager) {
        override fun getItem(position: Int): Fragment {
            return when (position) {
                0 -> FragmentRegisterInfo()
                1 -> FragmentRegisterAccount()
                2 -> FragmentPerformRegister()
                3 -> FragmentRegisterSetupProfile()
                else -> throw IllegalStateException()
            }
        }

        override fun getCount(): Int = 4
    }

    override fun onPressedForward() {
        activity_register_vp.setCurrentItem(activity_register_vp.currentItem + 1, true)
    }

    override fun onPressedBackwards() {
        activity_register_vp.setCurrentItem(activity_register_vp.currentItem - 1, true)
    }

    fun attemptRegister(registerCallback: (Boolean) -> Unit) {
        val authPair = generateAuthKeys()
        val messagePair = generateMessageKeys()

        val username = this.username ?: return
        AttemptRegisterTask({ response ->
            if (response == null) {
                AlertDialog.Builder(this)
                        .setMessage(R.string.register_error_while_connecting)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()

                registerCallback(false)

                return@AttemptRegisterTask
            }
            if (response.type != RegisterRequestResponseToClient.Type.SUCCESS) {
                AlertDialog.Builder(this)
                        .setMessage(response.type.name)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()

                registerCallback(false)
            } else {
                initUser(username, response.userUUID
                        ?: return@AttemptRegisterTask, messagePair.public)

                registerCallback(true)
            }
        }, messagePair, authPair, username, captchaToken ?: return).execute()
    }

    class AttemptRegisterTask(val callback: (RegisterRequestResponseToClient?) -> (Unit),
                              private val messagePair: KeyPair,
                              private val authPair: KeyPair,
                              private val username: String,
                              private val captchaToken: String) :
            AsyncTask<Unit, Unit, RegisterRequestResponseToClient?>() {
        override fun doInBackground(vararg p0: Unit?): RegisterRequestResponseToClient? {
            return try {
                val gson = genGson()
                val response = HttpManager.post(gson.toJson(RegisterRequestToServer(username, authPair.public as RSAPublicKey,
                        messagePair.public as RSAPublicKey, FirebaseInstanceId.getInstance().token, captchaToken)), RegisterRequestToServer.TARGET)
                gson.fromJson<RegisterRequestResponseToClient>(response, RegisterRequestResponseToClient::class.java)
            } catch (t: Throwable) {
                Log.e("ERROR", "Unable to register", t)
                null
            }
        }

        override fun onPostExecute(response: RegisterRequestResponseToClient?) {
            callback.invoke(response)
        }
    }

    private fun initUser(username: String, userUUID: UUID, publicMessageKey: PublicKey) {
        val mercuryClient = applicationContext as MercuryClient

        val dataOut = DataOutputStream(File(filesDir.path + "/username.info").outputStream())
        dataOut.writeUTF(username)
        dataOut.writeUUID(userUUID)
        dataOut.close()

        ClientPreferences.setClientUUID(this, userUUID)
        ClientPreferences.setUsername(this, username)
        ClientPreferences.setRegistered(this, true)

        ContactUtil.addContact(userUUID, username, mercuryClient.dataBase, publicMessageKey)
    }

    private fun generateAuthKeys(): KeyPair {
        val keyGenerator = KeyPairGenerator.getInstance("RSA")
        keyGenerator.initialize(2048)
        val keyPair = keyGenerator.generateKeyPair()

        ClientPreferences.setPublicAuthKey(this, keyPair.public)
        ClientPreferences.setPrivateAuthKey(this, keyPair.private)

        return keyPair
    }

    private fun generateMessageKeys(): KeyPair {
        val keyGenerator = KeyPairGenerator.getInstance("RSA")
        keyGenerator.initialize(2048)
        val keyPair = keyGenerator.generateKeyPair()

        ClientPreferences.setPublicMessageKey(this, keyPair.public)
        ClientPreferences.setPrivateMessageKey(this, keyPair.private)

        return keyPair
    }

    override fun onSupportNavigateUp(): Boolean = true

    override fun onNavigateUp(): Boolean = true

    override fun onBackPressed() {

    }
}

