package de.intektor.mercury.ui.register

import android.app.Activity
import android.os.AsyncTask
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.gms.safetynet.SafetyNet
import de.intektor.mercury.R
import de.intektor.mercury.io.HttpManager
import de.intektor.mercury.util.getCompatColor
import de.intektor.mercury_common.client_to_server.CheckUsernameAvailableRequestToServer
import de.intektor.mercury_common.gson.genGson
import de.intektor.mercury_common.server_to_client.CheckUsernameAvailableResponseToClient
import kotlinx.android.synthetic.main.fragment_register_account.*

/**
 * @author Intektor
 */
class FragmentRegisterAccount : Fragment() {

    private var usernameValid: Boolean = false
        set(value) {
            fragment_register_account_cl_verify?.isEnabled = value
        }

    private var captchaToken: String? = null
        set(value) {

        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_register_account, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_register_account_et_username.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (s != null && s.isNotBlank() && s.length >= 5) {
                    doAvailabilityCheck(s.toString())
                } else {
                    fragment_register_account_cv_username_info.visibility = View.GONE
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                usernameValid = false

                val context = requireContext()
                if (context is RegisterActivity) {
                    context.username = null
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }
        })

        fragment_register_account_cl_verify.setOnClickListener {
            doCaptcha()
        }

        fragment_register_account_cl_help.setOnClickListener {
            showUsernameHelp()
        }

        fragment_register_account_cl_back.setOnClickListener {
            val context = context
            if (context is IArrowPressedListener) {
                context.onPressedBackwards()
            }
        }
    }

    class CheckUsernameAvailableTask(private val username: String, private val callback: (CheckUsernameAvailableResponseToClient?) -> Unit) : AsyncTask<Unit, Unit, CheckUsernameAvailableResponseToClient>() {
        override fun doInBackground(vararg params: Unit): CheckUsernameAvailableResponseToClient? {
            return try {
                val gson = genGson()
                val response = HttpManager.post(gson.toJson(CheckUsernameAvailableRequestToServer(username)), CheckUsernameAvailableRequestToServer.TARGET)
                gson.fromJson(response, CheckUsernameAvailableResponseToClient::class.java)
            } catch (t: Throwable) {
                null
            }
        }

        override fun onPostExecute(result: CheckUsernameAvailableResponseToClient?) {
            callback(result)
        }
    }

    private fun doAvailabilityCheck(username: String) {
        CheckUsernameAvailableTask(username) {
            if (it == null) {
                fragment_register_account_cv_username_info.visibility = View.GONE
                Toast.makeText(requireContext(), R.string.register_error_while_connecting, Toast.LENGTH_LONG).show()
            } else {
                fragment_register_account_cv_username_info.visibility = View.VISIBLE
                if (it.available) {
                    fragment_register_account_tv_username_enter.setText(R.string.register_username_available)
                    fragment_register_account_tv_username_enter.setTextColor(resources.getCompatColor(android.R.color.holo_green_dark, requireContext().theme))

                    usernameValid = true

                    val context = requireContext()
                    if (context is RegisterActivity) {
                        context.username = username
                    }
                } else {
                    fragment_register_account_tv_username_enter.setText(R.string.register_username_not_available)
                    fragment_register_account_tv_username_enter.setTextColor(resources.getCompatColor(android.R.color.holo_red_dark, requireContext().theme))
                }
            }
        }.execute()
    }

    private fun doCaptcha() {
        SafetyNet.getClient(context as Activity).verifyWithRecaptcha("6Lf3Rn8UAAAAAKd7Jr4T5A_D8WHXVHxsn6IjKoBp")
                .addOnSuccessListener(context as Activity) { response ->
                    if (response.tokenResult.isNotEmpty()) {
                        fragment_register_account_cv_verified.visibility = View.VISIBLE

                        val context = requireContext()
                        if (context is RegisterActivity) {
                            context.captchaToken = response.tokenResult
                        }
                        if (context is IArrowPressedListener) {
                            context.onPressedForward()
                        }
                    }
                }.addOnFailureListener(context as Activity) { exception ->
                    fragment_register_account_cv_verified.visibility = View.GONE
                }
    }

    private fun showUsernameHelp() {
        AlertDialog.Builder(requireContext())
                .setMessage(R.string.register_error_invalid_username_message)
                .show()
    }
}