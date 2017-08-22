package de.intektor.kentai.fragment

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import de.intektor.kentai.R
import de.intektor.kentai.fragment.FragmentContactsOverview.ListElementClickListener
import de.intektor.kentai.kentai.contacts.Contact

class ContactViewAdapter(val mValues: MutableList<Contact>, private val mListener: ListElementClickListener?) : RecyclerView.Adapter<ContactViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = mValues[position]
        holder.item = contact
        holder.text.text = "${contact.name} (${contact.alias})"

        holder.mView.setOnClickListener {
            mListener?.click(holder.item!!)
        }
    }

    override fun getItemCount(): Int {
        return mValues.size
    }

    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        val text: TextView = mView.findViewById(R.id.text) as TextView
        val seperator: ImageView = mView.findViewById(R.id.seperator) as ImageView
        var item: Contact? = null

    }
}
