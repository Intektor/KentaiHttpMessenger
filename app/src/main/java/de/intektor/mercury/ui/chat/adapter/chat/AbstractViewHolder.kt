package de.intektor.mercury.ui.chat.adapter.chat

import android.support.v7.widget.RecyclerView
import android.view.View

abstract class AbstractViewHolder<T>(itemView: View, val chatAdapter: ChatAdapter) : RecyclerView.ViewHolder(itemView) {

    abstract fun bind(component: ChatAdapter.ChatAdapterWrapper<T>)
}