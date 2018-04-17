package de.intektor.kentai.kentai.chat.adapter.viewing

import android.content.Intent
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.intektor.kentai.ContactInfoActivity
import de.intektor.kentai.R
import de.intektor.kentai.kentai.KEY_USER_UUID
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.getProfilePicture

class ViewingAdapter(private val viewingUsers: List<ViewingUser>) : RecyclerView.Adapter<ViewingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.viewing_user_item, parent, false)
        return ViewingViewHolder(view)
    }

    override fun getItemCount(): Int = viewingUsers.size

    override fun onBindViewHolder(holder: ViewingViewHolder, position: Int) {
        val item = viewingUsers[position]

        holder.username.text = when (item.userState) {
            UserState.VIEWING -> ""
            UserState.TYPING -> holder.username.context.getString(R.string.chat_user_typing)
        }

        holder.username.visibility = View.VISIBLE

        Picasso.with(holder.itemView.context)
                .load(getProfilePicture(item.contact.userUUID, holder.itemView.context))
                .placeholder(R.drawable.ic_account_circle_white_24dp)
                .into(holder.profilePicture)

        holder.profilePicture.setOnClickListener {
            val openUser = Intent(holder.username.context, ContactInfoActivity::class.java)
            openUser.putExtra(KEY_USER_UUID, item.contact.userUUID)
            holder.username.context.startActivity(openUser)
        }
    }
}

data class ViewingUser(val contact: Contact, var userState: UserState)

enum class UserState {
    VIEWING,
    TYPING,
}

class ViewingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
    val profilePicture: ImageView = view.findViewById(R.id.viewingUserAccount)
    val username: TextView = view.findViewById(R.id.viewingUserState)
}