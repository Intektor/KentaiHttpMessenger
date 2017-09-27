package de.intektor.kentai.fragment

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import de.intektor.kentai.R
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.overview_activity.FragmentContactsOverview.ListElementClickListener

class ContactViewAdapter(val mValues: MutableList<Contact>, private val mListener: ListElementClickListener?, private val showCheckBox: Boolean = false) : RecyclerView.Adapter<ContactViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.fragment_contact, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val contact = mValues[position]
        holder.item = contact
        holder.text.text = "${contact.name} (${contact.alias})"
        if (showCheckBox) {
            holder.checkBox.visibility = View.VISIBLE
        }

        holder.mView.setOnClickListener {
            mListener?.click(holder.item!!, holder)
        }
    }

    override fun getItemCount(): Int = mValues.size

    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        val text: TextView = mView.findViewById<TextView>(R.id.text) as TextView
        val seperator: ImageView = mView.findViewById<ImageView>(R.id.seperator) as ImageView
        val checkBox: CheckBox = mView.findViewById<CheckBox>(R.id.contact_selected) as CheckBox
        var item: Contact? = null
    }
}
