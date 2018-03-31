package de.intektor.kentai.kentai.chat.adapter

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
    private val timeView: TextView = itemView.findViewById<TextView>(R.id.chatTimeInfoTimeView) as TextView
    private val statusView: ImageView = itemView.findViewById<ImageView>(R.id.chatTimeInfoStatusView) as ImageView

    override fun setComponent(component: Any) {
        component as TimeStatusChatInfo
        val layout = itemView.findViewById<LinearLayout>(R.id.chatTimeInfoLayout)
        layout.gravity = if (component.isClient) Gravity.END else Gravity.START
        timeView.text = SimpleDateFormat.getTimeInstance().format(Date(component.time))
        when (component.status) {
            MessageStatus.WAITING -> statusView.setImageResource(R.drawable.waiting)
            MessageStatus.SENT -> statusView.setImageResource(R.drawable.sent)
            MessageStatus.RECEIVED -> statusView.setImageResource(R.drawable.received)
            MessageStatus.SEEN -> statusView.setImageResource(R.drawable.seen)
        }
    }
}