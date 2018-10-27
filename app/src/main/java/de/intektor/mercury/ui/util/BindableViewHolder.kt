package de.intektor.mercury.ui.util

import android.support.v7.widget.RecyclerView
import android.view.View

abstract class BindableViewHolder<in T : Any>(view: View) : RecyclerView.ViewHolder(view) {

    abstract fun bind(item: T)
}