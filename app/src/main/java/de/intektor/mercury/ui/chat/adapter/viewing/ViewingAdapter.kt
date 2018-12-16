package de.intektor.mercury.ui.chat.adapter.viewing

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView
import de.intektor.mercury.R
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury.util.getCompatDrawable
import de.intektor.mercury_common.users.ProfilePictureType

class ViewingAdapter(private val viewingUsers: List<ViewingUser>) : androidx.recyclerview.widget.RecyclerView.Adapter<ViewingAdapter.ViewingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.viewing_user_item, parent, false)
        return ViewingViewHolder(view)
    }

    override fun getItemCount(): Int = viewingUsers.size

    override fun onBindViewHolder(holder: ViewingViewHolder, position: Int) {
        val item = viewingUsers[position]

        val context = holder.itemView.context

        holder.typingParent.visibility = if (item.userState == UserState.TYPING) View.VISIBLE else View.GONE

        holder.username.text = ContactUtil.getDisplayName(context, context.mercuryClient().dataBase, item.contact)

        ProfilePictureUtil.loadProfilePicture(item.contact.userUUID,
                ProfilePictureType.SMALL,
                holder.profilePicture,
                context.resources.getCompatDrawable(R.drawable.baseline_account_circle_24, context.theme))
    }

    class ViewingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val profilePicture: ImageView = view.findViewById(R.id.item_viewing_user_iv_pp)
        val username: TextView = view.findViewById(R.id.item_viewing_user_tv_username)
        val typingParent: CardView = view.findViewById(R.id.item_viewing_user_cv_typing)
    }
}

data class ViewingUser(val contact: Contact, var userState: UserState)

enum class UserState {
    VIEWING,
    TYPING,
}

