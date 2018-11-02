package de.intektor.mercury.ui.chat.adapter.chat

import androidx.recyclerview.widget.RecyclerView
import android.view.View

abstract class AbstractViewHolder<T>(itemView: View, val chatAdapter: ChatAdapter) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {

    abstract fun bind(component: ChatAdapter.ChatAdapterWrapper<T>)
}