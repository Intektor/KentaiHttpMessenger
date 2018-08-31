package de.intektor.kentai.kentai.chat.adapter.chat

import android.graphics.Color
import android.view.View
import android.widget.TextView
import de.intektor.kentai.R

class UsernameChatInfoViewHolder(view: View, chatAdapter: ChatAdapter) : AbstractViewHolder(view, chatAdapter) {

    val text: TextView = view.findViewById(R.id.chatSenderInfoText)

    override fun bind(component: ChatAdapter.ChatAdapterWrapper) {
        val item = component.item as UsernameChatInfo
        text.text = item.username
        text.setTextColor(Color.parseColor("#${item.color}"))
    }
}