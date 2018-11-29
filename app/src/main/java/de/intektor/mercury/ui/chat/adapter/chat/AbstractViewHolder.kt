package de.intektor.mercury.ui.chat.adapter.chat

import android.view.View
import de.intektor.mercury.chat.adapter.ChatAdapterSubItem

abstract class AbstractViewHolder<T : ChatAdapterSubItem>(itemView: View, val chatAdapter: ChatAdapter) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {

    abstract fun bind(component: ChatAdapter.ChatAdapterWrapper<T>)
}