package de.intektor.kentai.fragment

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.getName
import de.intektor.kentai.kentai.getProfilePicture

class ContactViewAdapter(val contacts: List<ContactWrapper>, private val callbackClickView: (ContactWrapper, ViewHolder) -> Unit, private val showCheckBox: Boolean = false, val callbackClickCheckBox: (ContactWrapper, ViewHolder) -> Unit) : RecyclerView.Adapter<ContactViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.contact_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wrapper = contacts[position]
        val contact = wrapper.contact

        holder.itemView.tag = contact

        holder.item = contact
        holder.text.text = getName(contact)

        holder.checkBox.visibility = if (showCheckBox) View.VISIBLE else View.GONE

        holder.checkBox.isChecked = wrapper.checked

        holder.checkBox.setOnClickListener {
            callbackClickCheckBox.invoke(wrapper, holder)
        }

        holder.mView.setOnClickListener {
            callbackClickView.invoke(wrapper, holder)
        }

        val profilePicture = getProfilePicture(contact.userUUID, holder.itemView.context)
        if (profilePicture.exists()) {
            Picasso.with(holder.itemView.context).load(profilePicture).placeholder(R.drawable.ic_account_circle_white_24dp).into(holder.profilePicture)
        } else {
            holder.profilePicture.setImageResource(R.drawable.ic_account_circle_white_24dp)
        }

        val kentaiClient = holder.itemView.context.applicationContext as KentaiClient
        kentaiClient.addInterestedUser(contact.userUUID)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)

        val contact = holder.itemView.tag as Contact
        val kentaiClient = holder.itemView.context.applicationContext as KentaiClient
        kentaiClient.removeInterestedUser(contact.userUUID)
    }

    override fun getItemCount(): Int = contacts.size

    inner class ViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        val text: TextView = mView.findViewById(R.id.text)
        val checkBox: CheckBox = mView.findViewById(R.id.contact_selected)
        val profilePicture: ImageView = mView.findViewById(R.id.profilePicture)

        var item: Contact? = null
    }

    data class ContactWrapper(val contact: Contact, var checked: Boolean)
}
