package de.intektor.kentai.kentai.chat.adapter.chat

import android.support.v7.widget.RecyclerView
import android.view.View

abstract class AbstractViewHolder(itemView: View, val chatAdapter: ChatAdapter) : RecyclerView.ViewHolder(itemView) {

    abstract fun bind(component: ChatAdapter.ChatAdapterWrapper)
}