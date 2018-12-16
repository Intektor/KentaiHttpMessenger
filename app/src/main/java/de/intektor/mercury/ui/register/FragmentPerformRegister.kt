package de.intektor.mercury.ui.register

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.intektor.mercury.R
import kotlinx.android.synthetic.main.fragment_perfom_register_account.*

/**
 * @author Intektor
 */
class FragmentPerformRegister : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            layoutInflater.inflate(R.layout.fragment_perfom_register_account, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_perform_register_account_cl_privacy_policy_see.setOnClickListener {
            //            val tabs = CustomTabsIntent.Builder()
//                    .build()
//
//            tabs.launchUrl(requireContext(), Uri.parse("intektor.de/mercury/privacy_policy"))
        }

        fragment_perform_register_account_cl_register.setOnClickListener {
            val context = requireContext()
            if (context is RegisterActivity) {

                val dialog = AlertDialog.Builder(context)
                        .setView(layoutInflater.inflate(R.layout.dialog_registering, null))
                        .show()

                context.attemptRegister { successful ->
                    dialog.dismiss()
                    if (successful) {
                        context.onPressedForward()
                    } else {
                        context.onPressedBackwards()
                    }
                }
            }
        }
    }
}