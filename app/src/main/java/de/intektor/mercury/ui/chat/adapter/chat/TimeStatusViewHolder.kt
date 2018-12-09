package de.intektor.mercury.ui.chat.adapter.chat

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.intektor.mercury.R
import de.intektor.mercury.chat.adapter.TimeStatusChatInfo
import de.intektor.mercury_common.chat.MessageStatus
import java.text.SimpleDateFormat
import java.util.*

class TimeStatusViewHolder(itemView: View, chatAdapter: ChatAdapter) : AbstractViewHolder<TimeStatusChatInfo>(itemView, chatAdapter) {

    private val time: TextView = itemView.findViewById(R.id.item_time_info_tv_time)
    private val status: TextView = itemView.findViewById(R.id.item_time_info_tv_status)
    private val dot: ImageView = itemView.findViewById(R.id.item_time_info_iv_dot)

    override fun bind(component: ChatAdapter.ChatAdapterWrapper<TimeStatusChatInfo>) {
        val item = component.item
        val layout = itemView.findViewById<LinearLayout>(R.id.chatTimeInfoLayout)

        val context = itemView.context

        layout.gravity = if (item.isClient) Gravity.END else Gravity.START
        time.text = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT).format(Date(item.time))

        status.visibility = if(component.item.isClient) View.VISIBLE else View.GONE
        dot.visibility = if(component.item.isClient) View.VISIBLE else View.GONE

        status.text = context.getString(when (item.status) {
            MessageStatus.WAITING -> R.string.message_status_waiting
            MessageStatus.SENT -> R.string.message_status_sent
            MessageStatus.RECEIVED -> R.string.message_status_received
            MessageStatus.SEEN -> R.string.message_status_seen
        })
    }
}