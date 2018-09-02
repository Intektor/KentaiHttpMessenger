package de.intektor.kentai.fragment

import android.app.Activity
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
import de.intektor.kentai.getName
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.getAttrDrawable
import de.intektor.kentai.kentai.getProfilePicture

class ContactAdapter(val contacts: List<ContactWrapper>, private val callbackClickView: (ContactWrapper, ViewHolder) -> Unit, private val showCheckBox: Boolean = false, val callbackClickCheckBox: (ContactWrapper, ViewHolder) -> Unit, private val registerForContextMenu: (Contact) -> Boolean) : RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.contact_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val wrapper = contacts[position]
        val contact = wrapper.contact

        holder.itemView.tag = contact

        holder.item = contact
        holder.text.text = getName(contact, holder.itemView.context, true)

        holder.checkBox.visibility = if (showCheckBox) View.VISIBLE else View.GONE

        holder.checkBox.isChecked = wrapper.checked

        holder.checkBox.setOnClickListener {
            callbackClickCheckBox.invoke(wrapper, holder)
        }

        holder.itemView.setOnClickListener {
            callbackClickView.invoke(wrapper, holder)
        }

        val profilePicture = getProfilePicture(contact.userUUID, holder.itemView.context)
        if (profilePicture.exists()) {
            Picasso.with(holder.itemView.context).load(profilePicture).placeholder(getAttrDrawable(holder.itemView.context, R.attr.ic_account)).into(holder.profilePicture)
        } else {
            holder.profilePicture.setImageDrawable(getAttrDrawable(holder.itemView.context, R.attr.ic_account))
        }

        holder.subtext.visibility = if (wrapper.subtext == null) View.GONE else View.VISIBLE
        if (wrapper.subtext != null) holder.subtext.text = wrapper.subtext
        if (wrapper.subtextColor != null) holder.subtext.setTextColor(wrapper.subtextColor!!)

        if (registerForContextMenu.invoke(contact)) {
            val c = holder.itemView.context
            if (c is Activity) {
                c.registerForContextMenu(holder.itemView)
            }
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

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.contactName)
        val checkBox: CheckBox = view.findViewById(R.id.contact_selected)
        val profilePicture: ImageView = view.findViewById(R.id.profilePicture)
        val subtext: TextView = view.findViewById(R.id.contactSubtext)

        var item: Contact? = null
    }

    data class ContactWrapper(val contact: Contact, var checked: Boolean, var subtext: String? = null, var subtextColor: Int? = null)
}