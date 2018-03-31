package de.intektor.kentai.kentai.chat.adapter

import android.content.Intent
import android.support.v7.widget.RecyclerView
import android.view.View

abstract class AbstractViewHolder(itemView: View, val chatAdapter: ChatAdapter) : RecyclerView.ViewHolder(itemView) {

    fun bind(component: Any) {
        setComponent(component)
        chatAdapter.activity.registerForContextMenu(itemView)
    }

    protected abstract fun setComponent(component: Any)

    open fun broadcast(target: String, intent: Intent) {

    }

    open fun bindPayload(payloads: MutableList<Any>?) {

    }
}