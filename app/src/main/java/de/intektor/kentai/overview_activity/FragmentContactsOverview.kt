package de.intektor.kentai.overview_activity

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.intektor.kentai.ContactInfoActivity
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.fragment.ContactViewAdapter
import de.intektor.kentai.kentai.KEY_USER_UUID
import de.intektor.kentai.kentai.chat.readContacts


class FragmentContactsOverview : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_contact_list, container, false)

        val kentaiClient = context!!.applicationContext as KentaiClient

        if (view is RecyclerView) {
            val context = view.getContext()
            view.layoutManager = LinearLayoutManager(context)

            view.adapter = ContactViewAdapter(readContacts(kentaiClient.dataBase).map { ContactViewAdapter.ContactWrapper(it, false) }, { wrapper, _ ->
                val contact = wrapper.contact
                if (contact.userUUID == kentaiClient.userUUID) return@ContactViewAdapter
                val i = Intent(this.context, ContactInfoActivity::class.java)
                i.putExtra(KEY_USER_UUID, contact.userUUID)
                context.startActivity(i)
            }, callbackClickCheckBox = { _, _ -> })
        }
        return view
    }
}
