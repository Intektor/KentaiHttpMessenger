package de.intektor.kentai.overview_activity

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
import de.intektor.kentai.fragment.ContactViewAdapter
import de.intektor.kentai.kentai.chat.readContacts
import de.intektor.kentai.kentai.contacts.Contact


class FragmentContactsOverview : Fragment() {

    private var mListener: ListElementClickListener? = null

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_contact_list, container, false)

        if (view is RecyclerView) {
            val context = view.getContext()
            view.layoutManager = LinearLayoutManager(context)

            view.adapter = ContactViewAdapter(readContacts(KentaiClient.INSTANCE.dataBase).toMutableList(), mListener)
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
        fun click(item: Contact, view: ContactViewAdapter.ViewHolder)
    }
}
