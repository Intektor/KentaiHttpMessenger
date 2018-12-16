package de.intektor.mercury.ui

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import de.intektor.mercury.R
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.getContact
import de.intektor.mercury.chat.getUserChat
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury.util.getCompatDrawable
import de.intektor.mercury_common.users.ProfilePictureType
import java.util.*

/**
 * @author Intektor
 */
class ContactInfoDialog : DialogFragment() {

    private lateinit var userUUID: UUID
    private var onCancel: (() -> Unit)? = null

    private var editMode = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = requireActivity().layoutInflater.inflate(R.layout.fragment_contact_info, null)

        val contact = getContact(requireContext().mercuryClient().dataBase, userUUID)

        val displayName = dialog.findViewById<TextView>(R.id.fragment_contact_info_tv_display_name)
        val username = dialog.findViewById<TextView>(R.id.fragment_contact_info_tv_username)
        val editName = dialog.findViewById<EditText>(R.id.fragment_contact_info_et)
        val editImage = dialog.findViewById<ImageView>(R.id.fragment_contact_info_iv_edit)

        displayName.text = ContactUtil.getDisplayName(requireContext(), requireContext().mercuryClient().dataBase, contact)
        username.text = contact.name

        ProfilePictureUtil.loadProfilePicture(userUUID, ProfilePictureType.SMALL, dialog.findViewById<ImageView>(R.id.fragment_contact_info_iv_pp)
                ?: throw IllegalStateException(),
                requireContext().resources.getCompatDrawable(R.drawable.baseline_account_circle_24, requireContext().theme))

        dialog.findViewById<View>(R.id.fragment_contact_info_cl_send_message).setOnClickListener {
            launchChat(contact)
        }

        dialog.findViewById<View>(R.id.fragment_contact_info_cl_message).setOnClickListener {
            launchChat(contact)
        }

        dialog.findViewById<View>(R.id.fragment_contact_info_cl_edit).setOnClickListener {
            if (!editMode) {
                displayName.visibility = View.GONE
                username.visibility = View.GONE
                editName.visibility = View.VISIBLE
                editName.requestFocus()

                val currentDisplayName = ContactUtil.getDisplayName(requireContext(), requireContext().mercuryClient().dataBase, contact)
                editName.setText(currentDisplayName)

                editName.setSelection(currentDisplayName.length)

                editImage.setImageResource(R.drawable.baseline_check_24)
                editMode = true
            } else {
                finalizeEdit()
            }
        }

        editName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                finalizeEdit()
            }
        }

        return AlertDialog.Builder(requireContext())
                .setView(dialog)
                .create()
    }

    private fun finalizeEdit() {
        editMode = false

        val displayName = dialog.findViewById<TextView>(R.id.fragment_contact_info_tv_display_name)
        val username = dialog.findViewById<TextView>(R.id.fragment_contact_info_tv_username)
        val editName = dialog.findViewById<EditText>(R.id.fragment_contact_info_et)
        val editImage = dialog.findViewById<ImageView>(R.id.fragment_contact_info_iv_edit)

        displayName.visibility = View.VISIBLE
        username.visibility = View.VISIBLE
        editName.visibility = View.GONE

        editImage.setImageResource(R.drawable.baseline_edit_24)

        displayName.text = editName.text.toString()

        ContactUtil.setDisplayName(requireContext().mercuryClient().dataBase, userUUID, editName.text.toString())
    }

    private fun launchChat(contact: Contact) {
        ChatActivity.launch(requireContext(), getUserChat(requireContext(), requireContext().mercuryClient().dataBase, contact))
    }

    fun setUserUUID(userUUID: UUID): ContactInfoDialog {
        this.userUUID = userUUID
        return this
    }

    override fun onCancel(dialog: DialogInterface?) {
        super.onCancel(dialog)

        onCancel?.invoke()
    }

    fun setOnCancelListener(onCancel: () -> Unit): ContactInfoDialog {
        this.onCancel = onCancel
        return this
    }
}