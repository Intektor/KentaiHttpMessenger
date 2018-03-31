package de.intektor.kentai.group_info_activity

import android.graphics.Color
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.GroupMember
import de.intektor.kentai_http_common.chat.GroupRole

/**
 * @author Intektor
 */
class GroupMemberAdapter(val list: List<GroupMember>, private val clickListener: ClickListener, val activity: AppCompatActivity) : RecyclerView.Adapter<GroupMemberAdapter.ViewHolder>() {

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val member = list[position]
        holder.username.text = if (member.contact.userUUID == KentaiClient.INSTANCE.userUUID) activity.getString(R.string.group_role_member_yourself_label) else member.contact.name
        holder.status.text = "TODO"
        when (member.role) {
            GroupRole.ADMIN -> {
                holder.subtitle.setText(R.string.group_role_admin)
                holder.subtitle.setTextColor(Color.RED)
            }
            GroupRole.MODERATOR -> {
                holder.subtitle.setText(R.string.group_role_moderator)
                holder.subtitle.setTextColor(Color.BLUE)
            }
            GroupRole.DEFAULT -> {
                holder.subtitle.setText(R.string.group_role_default)
                holder.subtitle.setTextColor(Color.GRAY)
            }
        }

        if (member.contact.userUUID != KentaiClient.INSTANCE.userUUID) {
            activity.registerForContextMenu(holder.view)
        }

        holder.view.setOnClickListener {
            clickListener.onClickedGroupMember(it, holder)
        }

        holder.view.tag = position
    }

    override fun getItemCount(): Int = list.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.group_member_layout, parent, false)
        return ViewHolder(view)
    }

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val username: TextView = view.findViewById(R.id.groupMemberLayoutUsername)
        val status: TextView = view.findViewById(R.id.groupMemberLayoutUserStatus)
        val subtitle: TextView = view.findViewById(R.id.groupMemberLayoutSubtitle)
    }

    interface ClickListener {
        fun onClickedGroupMember(view: View, holder: ViewHolder)
    }
}