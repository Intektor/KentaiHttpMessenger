package de.intektor.mercury.ui.chat.adapter.chat

import android.support.constraint.ConstraintLayout
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import de.intektor.mercury.R
import de.intektor.mercury.android.getAttrDrawable
import de.intektor.mercury.util.setCompatBackground
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.MessageData

abstract class ChatMessageViewHolder<T : MessageData, K>(view: View, chatAdapter: ChatAdapter) : AbstractViewHolder<K>(view, chatAdapter) {

    private val checkBox: CheckBox = view.findViewById(R.id.selectedBox)
    private val messageParentLayout: ConstraintLayout = view.findViewById(R.id.messageParentLayout)

    abstract val parentLayout: LinearLayout
    abstract val bubbleLayout: ViewGroup

    override fun bind(component: ChatAdapter.ChatAdapterWrapper<K>) {
        checkBox.visibility = if (chatAdapter.activity.isInEditMode) View.VISIBLE else View.GONE

        checkBox.setOnCheckedChangeListener { _, isChecked ->
            chatAdapter.activity.select(component, isChecked)
        }

        checkBox.isChecked = component.selected

        registerForEditModeLongPress(messageParentLayout)

        val message = getMessage(component.item)

        val data = message.messageData as T

        if (isClient(component.item)) {
            bubbleLayout.setCompatBackground(getAttrDrawable(itemView.context, R.attr.bubble_right))
            parentLayout.gravity = Gravity.END
        } else {
            bubbleLayout.setCompatBackground(getAttrDrawable(itemView.context, R.attr.bubble_left))
            parentLayout.gravity = Gravity.START
        }

        bindMessage(component.item, message.messageCore, data)
    }

    abstract fun bindMessage(item: K, core: MessageCore, data: T)

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

    protected fun registerForBoth(view: View) {
        registerForEditModeLongPress(view)
        registerForEditModePress(view)
    }

    abstract fun getMessage(item: K): ChatMessage

    abstract fun isClient(item: K): Boolean
}