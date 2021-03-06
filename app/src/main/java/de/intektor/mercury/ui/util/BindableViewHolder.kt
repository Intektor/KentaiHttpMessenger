package de.intektor.mercury.ui.util

import androidx.recyclerview.widget.RecyclerView
import android.view.View

abstract class BindableViewHolder<in T : Any>(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {

    abstract fun bind(item: T)
}