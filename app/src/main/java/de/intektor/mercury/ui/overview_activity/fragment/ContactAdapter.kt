package de.intektor.mercury.ui.overview_activity.fragment

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury.util.getName

class ContactAdapter(val contacts: List<ContactWrapper>, private val callbackClickView: (ContactWrapper, ViewHolder) -> Unit, private val showCheckBox: Boolean = false, val callbackClickCheckBox: (ContactWrapper, ViewHolder) -> Unit, private val registerForContextMenu: (Contact) -> Boolean) : androidx.recyclerview.widget.RecyclerView.Adapter<ContactAdapter.ViewHolder>() {

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

        val profilePicture = ProfilePictureUtil.getProfilePicture(contact.userUUID, holder.itemView.context)
        if (profilePicture.exists()) {
            Picasso.get().load(profilePicture).placeholder(R.drawable.baseline_account_circle_24).into(holder.profilePicture)
        } else {
            holder.profilePicture.setImageResource(R.drawable.baseline_account_circle_24)
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

        val mercuryClient = holder.itemView.context.applicationContext as MercuryClient
        mercuryClient.addInterestedUser(contact.userUUID)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)

        val contact = holder.itemView.tag as Contact
        val mercuryClient = holder.itemView.context.applicationContext as MercuryClient
        mercuryClient.removeInterestedUser(contact.userUUID)
    }

    override fun getItemCount(): Int = contacts.size

    inner class ViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.contactName)
        val checkBox: CheckBox = view.findViewById(R.id.contact_selected)
        val profilePicture: ImageView = view.findViewById(R.id.profilePicture)
        val subtext: TextView = view.findViewById(R.id.contactSubtext)

        var item: Contact? = null
    }

    data class ContactWrapper(val contact: Contact, var checked: Boolean, var subtext: String? = null, var subtextColor: Int? = null)
}