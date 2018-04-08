package de.intektor.kentai.kentai.chat.adapter.chat

import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai_http_common.chat.ChatMessageGroupInvite
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.util.toUUID

class GroupInviteViewHolder(itemView: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder(itemView, chatAdapter) {
    private val button: Button = itemView.findViewById(R.id.group_invite_button)

    override fun setComponent(component: Any) {
        component as ChatMessageWrapper
        val layout = itemView.findViewById<LinearLayout>(R.id.group_invite_bubble_layout) as LinearLayout
        val parentLayout = itemView.findViewById<LinearLayout>(R.id.group_invite_parent_layout) as LinearLayout
        // if message is mine then align to right
        if (component.client) {
            layout.setBackgroundResource(R.drawable.bubble_right)
            parentLayout.gravity = Gravity.END
        } else {
            layout.setBackgroundResource(R.drawable.bubble_left)
            parentLayout.gravity = Gravity.START
            val paddingStart = button.paddingStart
            val paddingEnd = button.paddingEnd
            button.setPadding(paddingEnd, button.paddingTop, paddingStart, button.paddingBottom)
        }

        val message = component.message
        message as ChatMessageGroupInvite
        button.text = itemView.context.getString(R.string.chat_group_invite_message) + "\n" + message.groupName

        button.setOnClickListener {
            val i = Intent(itemView.context, ChatActivity::class.java)
            i.putExtra("chatInfo", ChatInfo(message.chatUUID.toUUID(), message.groupName, ChatType.GROUP, message.roleMap.keys.map { ChatReceiver(it, null, ChatReceiver.ReceiverType.USER) }))
            itemView.context.startActivity(i)
        }

        chatAdapter.activity.registerForContextMenu(itemView)
    }
}