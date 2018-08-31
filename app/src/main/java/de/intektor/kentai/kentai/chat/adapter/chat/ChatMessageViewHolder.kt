package de.intektor.kentai.kentai.chat.adapter.chat

import android.support.constraint.ConstraintLayout
import android.view.View
import android.widget.CheckBox
import de.intektor.kentai.R

abstract class ChatMessageViewHolder(view: View, chatAdapter: ChatAdapter) : AbstractViewHolder(view, chatAdapter) {

    private val checkBox: CheckBox = view.findViewById(R.id.selectedBox)
    private val messageParentLayout: ConstraintLayout = view.findViewById(R.id.messageParentLayout)

    override fun bind(component: ChatAdapter.ChatAdapterWrapper) {
        checkBox.visibility = if (chatAdapter.activity.isInEditMode) View.VISIBLE else View.GONE

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            chatAdapter.activity.select(component, isChecked)
        }

        checkBox.isChecked = component.selected

        registerForEditModeLongPress(messageParentLayout)
    }

    protected fun registerForEditModeLongPress(view: View) {
        view.setOnLongClickListener {
            chatAdapter.activity.activateEditMode()
            checkBox.isChecked = true
            true
        }
    }

    private fun selectForEditMode() {
        checkBox.isChecked = !checkBox.isChecked
    }

    protected fun registerForEditModePress(view: View, other: (() -> Unit)? = null) {
        view.setOnClickListener {
            if (chatAdapter.activity.isInEditMode) {
                selectForEditMode()
            } else other?.invoke()
        }
    }
}