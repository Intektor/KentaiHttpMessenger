package de.intektor.kentai.kentai.chat.adapter.chat

import android.graphics.Color
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.getAttrDrawable
import de.intektor.kentai_http_common.chat.ChatMessageGroupInvite
import de.intektor.kentai_http_common.chat.GroupRole
import de.intektor.kentai_http_common.chat.group_modification.*
import de.intektor.kentai_http_common.util.toUUID


class TextMessageViewHolder(itemView: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(itemView, chatAdapter) {

    private val msg: TextView = itemView.findViewById(R.id.message_text) as TextView
    private val bubbleLayout: LinearLayout = itemView.findViewById(R.id.bubble_layout)
    private val parentLayout: LinearLayout = itemView.findViewById(R.id.bubble_layout_parent)

    override fun setComponent(component: Any) {
        component as ChatMessageWrapper
        val message = component.message
        msg.text = component.message.text

        if (component.client) {
            bubbleLayout.background = getAttrDrawable(itemView.context, R.attr.bubble_right)
            parentLayout.gravity = Gravity.END
        } else {
            bubbleLayout.background = getAttrDrawable(itemView.context, R.attr.bubble_left)
            parentLayout.gravity = Gravity.START
        }

        msg.isClickable = false

        if (message is ChatMessageGroupModification) {
            bubbleLayout.setBackgroundResource(R.drawable.bubble_advanced)
            val modification = message.groupModification
            when (modification) {
                is GroupModificationChangeName -> {
                    msg.text = itemView.context.getString(R.string.chat_group_change_name, chatAdapter.contactMap[component.message.senderUUID]!!.name, modification.oldName, modification.newName)
                }
                is GroupModificationChangeRole -> {
                    //TODO: make it impossible to get anything crashing by sending wrong enums

                    val oldRoleString = when (GroupRole.values()[modification.oldRole.toInt()]) {
                        GroupRole.ADMIN -> itemView.context.getString(R.string.group_role_admin)
                        GroupRole.MODERATOR -> itemView.context.getString(R.string.group_role_moderator)
                        GroupRole.DEFAULT -> itemView.context.getString(R.string.group_role_default)
                    }

                    val newRoleString = when (GroupRole.values()[modification.newRole.toInt()]) {
                        GroupRole.ADMIN -> itemView.context.getString(R.string.group_role_admin)
                        GroupRole.MODERATOR -> itemView.context.getString(R.string.group_role_moderator)
                        GroupRole.DEFAULT -> itemView.context.getString(R.string.group_role_default)
                    }

                    msg.text = itemView.context.getString(R.string.chat_group_change_role, chatAdapter.contactMap[component.message.senderUUID]!!.name, chatAdapter.contactMap[modification.userUUID.toUUID()]!!.name,
                            oldRoleString, newRoleString)
                }
                is GroupModificationKickUser -> {
                    if (modification.reason.isBlank()) {
                        msg.text = itemView.context.getString(R.string.chat_group_change_kick_user_no_reason, chatAdapter.contactMap[modification.toKick.toUUID()]!!.name, chatAdapter.contactMap[message.senderUUID]!!.name)
                    } else {
                        msg.text = itemView.context.getString(R.string.chat_group_change_kick_user_with_reason,
                                chatAdapter.contactMap[modification.toKick.toUUID()]!!.name, chatAdapter.contactMap[message.senderUUID]!!.name, modification.reason)
                    }
                }
                is GroupModificationAddUser -> {
                    msg.text = itemView.context.getString(R.string.chat_group_change_add_user, chatAdapter.contactMap[modification.userUUID.toUUID()]!!.name, chatAdapter.contactMap[message.senderUUID]!!.name)
                }
            }
        } else if (message is ChatMessageGroupInvite) {
            msg.isClickable = true

            val sS = SpannableString(itemView.context.getString(R.string.chat_group_invite_message, message.groupInvite.groupName))

            sS.setSpan(UnderlineSpan(), 0, sS.length, 0)
            sS.setSpan(ForegroundColorSpan(Color.CYAN), 0, sS.length, 0)

            msg.text = sS
            //TODO:
//            msg.setOnClickListener {
//                val i = Intent(itemView.context, ChatActivity::class.java)
//                i.putExtra(KEY_CHAT_INFO, ChatInfo(message.chatUUID.toUUID(), message.groupName, ChatType.GROUP, message.roleMap.keys.map { ChatReceiver(it, null, ChatReceiver.ReceiverType.USER) }))
//                itemView.context.startActivity(i)
//            }
        }
    }
}