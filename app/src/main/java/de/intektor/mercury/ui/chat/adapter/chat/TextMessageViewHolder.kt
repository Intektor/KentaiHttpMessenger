package de.intektor.mercury.ui.chat.adapter.chat

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import de.intektor.mercury.R
import de.intektor.mercury.chat.ChatMessageWrapper
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageText


class TextMessageViewHolder(itemView: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder<MessageText, ChatMessageWrapper>(itemView, chatAdapter) {

    private val msg: TextView = itemView.findViewById(R.id.message_text) as TextView
    override val bubbleLayout: LinearLayout = itemView.findViewById(R.id.bubble_layout)
    override val parentLayout: LinearLayout
        get() = itemView.findViewById(R.id.bubble_layout_parent)

    override fun bindMessage(item: ChatMessageWrapper, core: MessageCore, data: MessageText) {
        msg.text = data.message

        msg.isClickable = false

        registerForEditModeLongPress(msg)
        registerForEditModePress(msg)
        registerForEditModeLongPress(bubbleLayout)
        registerForEditModePress(bubbleLayout)
        registerForEditModeLongPress(parentLayout)
        registerForEditModePress(parentLayout)
    }

//        if (message is ChatMessageGroupModification) {
//            bubbleLayout.setBackgroundResource(R.drawable.bubble_advanced)
//            val modification = message.groupModification
//            when (modification) {
//                is GroupModificationChangeName -> {
//                    msg.text = itemView.context.getString(R.string.chat_group_change_name, chatAdapter.contactMap[item.message.messageUUID]!!.name, modification.oldName, modification.newName)
//                }
//                is GroupModificationChangeRole -> {
//                    //TODO: make it impossible to get anything crashing by sending wrong enums
//
//                    val oldRoleString = when (GroupRole.values()[modification.oldRole.toInt()]) {
//                        GroupRole.ADMIN -> itemView.context.getString(R.string.group_role_admin)
//                        GroupRole.MODERATOR -> itemView.context.getString(R.string.group_role_moderator)
//                        GroupRole.DEFAULT -> itemView.context.getString(R.string.group_role_default)
//                    }
//
//                    val newRoleString = when (GroupRole.values()[modification.newRole.toInt()]) {
//                        GroupRole.ADMIN -> itemView.context.getString(R.string.group_role_admin)
//                        GroupRole.MODERATOR -> itemView.context.getString(R.string.group_role_moderator)
//                        GroupRole.DEFAULT -> itemView.context.getString(R.string.group_role_default)
//                    }
//
//                    msg.text = itemView.context.getString(R.string.chat_group_change_role, chatAdapter.contactMap[item.message.messageUUID]!!.name, chatAdapter.contactMap[modification.userUUID.toUUID()]!!.name,
//                            oldRoleString, newRoleString)
//                }
//                is GroupModificationKickUser -> {
//                    if (modification.reason.isBlank()) {
//                        msg.text = itemView.context.getString(R.string.chat_group_change_kick_user_no_reason, chatAdapter.contactMap[modification.toKick.toUUID()]!!.name, chatAdapter.contactMap[message.messageUUID]!!.name)
//                    } else {
//                        msg.text = itemView.context.getString(R.string.chat_group_change_kick_user_with_reason,
//                                chatAdapter.contactMap[modification.toKick.toUUID()]!!.name, chatAdapter.contactMap[message.messageUUID]!!.name, modification.reason)
//                    }
//                }
//                is GroupModificationAddUser -> {
//                    msg.text = itemView.context.getString(R.string.chat_group_change_add_user, chatAdapter.contactMap[modification.userUUID.toUUID()]!!.name, chatAdapter.contactMap[message.messageUUID]!!.name)
//                }
//            }
//        } else if (message is ChatMessageGroupInvite) {
//            msg.isClickable = true
//
//            val sS = SpannableString(itemView.context.getString(R.string.chat_group_invite_message, message.groupInvite.groupName))
//
//            sS.setSpan(UnderlineSpan(), 0, sS.length, 0)
//            sS.setSpan(ForegroundColorSpan(Color.CYAN), 0, sS.length, 0)
//
//            msg.text = sS
//        }

    override fun getMessage(item: ChatMessageWrapper): ChatMessage = item.chatMessageInfo.message

    override fun isClient(item: ChatMessageWrapper): Boolean = item.chatMessageInfo.client
}