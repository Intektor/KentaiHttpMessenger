package de.intektor.mercury.ui.register

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.widget.PopupWindowCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.safetynet.SafetyNet
import de.intektor.mercury.R
import kotlinx.android.synthetic.main.fragment_register_account.*

/**
 * @author Intektor
 */
class FragmentRegisterAccount : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_register_account, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_register_account_et_username.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {

            }
        })

        fragment_register_account_cl_verify.setOnClickListener {
            doCaptcha()
        }

        PopupWindow

        fragment_register_account_cv_help.hint
    }

    private fun doCaptcha() {
        SafetyNet.getClient(context as Activity).verifyWithRecaptcha("6Lf3Rn8UAAAAAKd7Jr4T5A_D8WHXVHxsn6IjKoBp")
                .addOnSuccessListener(context as Activity) { response ->
                    if (response.tokenResult.isNotEmpty()) {

                    }
                }.addOnFailureListener(context as Activity) { exception ->

                }
    }
}