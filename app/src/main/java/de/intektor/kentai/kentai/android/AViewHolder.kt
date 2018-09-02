package de.intektor.kentai.kentai.android

import android.support.v7.widget.RecyclerView
import android.view.View

abstract class AViewHolder<in T : Any>(view: View) : RecyclerView.ViewHolder(view) {

    abstract fun bind(item: T)
}