package de.intektor.mercury.ui.overview_activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import de.intektor.mercury.R
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.readContacts
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.ui.ContactInfoDialog
import de.intektor.mercury.ui.overview_activity.fragment.ContactAdapter
import kotlinx.android.synthetic.main.fragment_contact_list.*


class FragmentContactsOverview : androidx.fragment.app.Fragment() {

    private val currentContacts = mutableListOf<ContactAdapter.ContactWrapper>()

    private lateinit var adapter: ContactAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_contact_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mercuryClient = requireContext().mercuryClient()

        fragment_contact_list_rv_contacts.layoutManager = LinearLayoutManager(context)

        val client = ClientPreferences.getClientUUID(mercuryClient)

        currentContacts += readContacts(mercuryClient.dataBase).map { ContactAdapter.ContactWrapper(it, false) }

        adapter = ContactAdapter(currentContacts, { wrapper, _ ->
            val contact = wrapper.contact
            if (contact.userUUID == client) return@ContactAdapter

            ContactInfoDialog().setUserUUID(contact.userUUID).setOnCancelListener {
                updateContacts()
            }.show(fragmentManager, "")
        }, callbackClickCheckBox = { _, _ -> }, registerForContextMenu = { false })

        fragment_contact_list_rv_contacts.adapter = adapter
    }

    override fun onResume() {
        super.onResume()

        updateContacts()
    }

    private fun updateContacts() {
        currentContacts.clear()

        currentContacts += readContacts(requireContext().mercuryClient().dataBase).map { ContactAdapter.ContactWrapper(it, false) }

        adapter.notifyDataSetChanged()
    }
}
