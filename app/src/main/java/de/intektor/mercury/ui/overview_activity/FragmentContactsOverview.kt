package de.intektor.mercury.ui.overview_activity

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.intektor.mercury.R
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.getUserChat
import de.intektor.mercury.chat.readContacts
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.overview_activity.fragment.ContactAdapter
import kotlinx.android.synthetic.main.fragment_contact_list.*


class FragmentContactsOverview : androidx.fragment.app.Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_contact_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mercuryClient = requireContext().mercuryClient()

        fragment_contact_list_rv_contacts.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)

        val client = ClientPreferences.getClientUUID(mercuryClient)

        fragment_contact_list_rv_contacts.adapter = ContactAdapter(readContacts(mercuryClient.dataBase).map { ContactAdapter.ContactWrapper(it, false) }, { wrapper, _ ->
            val contact = wrapper.contact
            if (contact.userUUID == client) return@ContactAdapter

            ChatActivity.launch(mercuryClient, getUserChat(mercuryClient, mercuryClient.dataBase, contact))
        }, callbackClickCheckBox = { _, _ -> }, registerForContextMenu = { false })
    }
}
