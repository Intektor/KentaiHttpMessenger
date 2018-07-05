package de.intektor.kentai.kentai.chat.adapter.chat

import android.graphics.Color
import android.view.View
import android.widget.TextView
import de.intektor.kentai.R

class UsernameChatInfoViewHolder(view: View, chatAdapter: ChatAdapter) : AbstractViewHolder(view, chatAdapter) {

    val text: TextView = view.findViewById(R.id.chatSenderInfoText)

    override fun setComponent(component: Any) {
        component as UsernameChatInfo
        text.text = component.username
        text.setTextColor(Color.parseColor("#${component.color}"))
    }

    override fun registerForContextMenu(): Boolean = false
}