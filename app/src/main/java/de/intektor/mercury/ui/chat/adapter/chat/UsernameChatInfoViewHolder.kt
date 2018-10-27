package de.intektor.mercury.ui.chat.adapter.chat

import android.graphics.Color
import android.view.View
import android.widget.TextView
import de.intektor.mercury.R

class UsernameChatInfoViewHolder(view: View, chatAdapter: ChatAdapter) : AbstractViewHolder<UsernameChatInfo>(view, chatAdapter) {

    val text: TextView = view.findViewById(R.id.chatSenderInfoText)

    override fun bind(component: ChatAdapter.ChatAdapterWrapper<UsernameChatInfo>) {
        val item = component.item
        text.text = item.username
        text.setTextColor(Color.parseColor("#${item.color}"))
    }
}