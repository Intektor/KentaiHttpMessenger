package de.intektor.mercury.ui.register

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import de.intektor.mercury.R
import kotlinx.android.synthetic.main.fragment_register_info.*

/**
 * @author Intektor
 */
class FragmentRegisterInfo : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_register_info, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fragment_register_info_cl_forward.setOnClickListener {
            val context = context
            if (context is IArrowPressedListener) {
                context.onPressedForward()
            }
        }
    }
}