package de.intektor.kentai.kentai.chat.adapter.chat

import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai_http_common.chat.GroupRole
import de.intektor.kentai_http_common.chat.group_modification.*
import de.intektor.kentai_http_common.util.toUUID

class TextMessageViewHolder(itemView: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(itemView, chatAdapter) {

    private val msg: TextView = itemView.findViewById(R.id.message_text) as TextView

    override fun setComponent(component: Any) {
        component as ChatMessageWrapper
        val message = component.message
        msg.text = component.message.text
        msg.movementMethod = LinkMovementMethod.getInstance()

        val layout = itemView.findViewById(R.id.bubble_layout) as LinearLayout
        val parentLayout = itemView.findViewById(R.id.bubble_layout_parent) as LinearLayout

        if (component.client) {
            layout.setBackgroundResource(R.drawable.bubble_right)
            parentLayout.gravity = Gravity.END
        } else {
            layout.setBackgroundResource(R.drawable.bubble_left)
            parentLayout.gravity = Gravity.START
            val paddingStart = msg.paddingStart
            val paddingEnd = msg.paddingEnd
            msg.setPadding(paddingEnd, msg.paddingTop, paddingStart, msg.paddingBottom)
        }

        if (message is ChatMessageGroupModification) {
            layout.setBackgroundResource(R.drawable.bubble_advanced)
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
        }
    }
}