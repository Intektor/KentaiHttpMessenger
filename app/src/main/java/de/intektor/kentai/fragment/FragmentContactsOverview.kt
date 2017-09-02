package de.intektor.kentai.fragment

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai_http_common.util.toKey
import java.util.*


class FragmentContactsOverview : Fragment() {
    private var mListener: ListElementClickListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_contact_list, container, false)

        if (view is RecyclerView) {
            val context = view.getContext()
            val recyclerView = view
            recyclerView.layoutManager = LinearLayoutManager(context)

            val contactList = mutableListOf<Contact>()
            val cursor = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT username, alias, user_uuid, message_key FROM contacts;", null)

            while (cursor.moveToNext()) {
                val username = cursor.getString(0)
                val alias = cursor.getString(1)
                val userUUID = UUID.fromString(cursor.getString(2))
                val messageKey = cursor.getString(3).toKey()
                contactList.add(Contact(username, alias, userUUID, messageKey))
            }
            cursor.close()
            recyclerView.adapter = ContactViewAdapter(contactList, mListener)
        }
        return view
    }


    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is ListElementClickListener) {
            mListener = context
        } else {
            throw RuntimeException(context!!.toString() + " must implement ListElementClickListener")
        }
    }

    override fun onDetach() {
        super.onDetach()
        mListener = null
    }


    interface ListElementClickListener {
        fun click(item: Contact)
    }
}
