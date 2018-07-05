package de.intektor.kentai.kentai.chat.adapter.chat

import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.intektor.kentai.R
import de.intektor.kentai_http_common.chat.MessageStatus
import java.text.SimpleDateFormat
import java.util.*

class TimeStatusViewHolder(itemView: View, chatAdapter: ChatAdapter) : AbstractViewHolder(itemView, chatAdapter) {

    private val timeView: TextView = itemView.findViewById(R.id.chatTimeInfoTimeView)
    private val statusView: ImageView = itemView.findViewById(R.id.chatTimeInfoStatusView)

    override fun setComponent(component: Any) {
        component as TimeStatusChatInfo
        val layout = itemView.findViewById<LinearLayout>(R.id.chatTimeInfoLayout)
        layout.gravity = if (component.isClient) Gravity.END else Gravity.START
        timeView.text = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT).format(Date(component.time))

        statusView.visibility = if (component.isClient) View.VISIBLE else View.GONE

        when (component.status) {
            MessageStatus.WAITING -> statusView.setImageResource(R.drawable.waiting)
            MessageStatus.SENT -> statusView.setImageResource(R.drawable.sent)
            MessageStatus.RECEIVED -> statusView.setImageResource(R.drawable.received)
            MessageStatus.SEEN -> statusView.setImageResource(R.drawable.seen)
        }
    }

    override fun registerForContextMenu(): Boolean = false
}