package de.intektor.kentai.kentai

import android.graphics.Color
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai_http_common.chat.MessageStatus
import java.text.SimpleDateFormat
import java.util.*


/**
 * @author Intektor
 */
class ChatAdapter(private val chatMessageList: MutableList<ChatMessageWrapper>) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    fun add(`object`: ChatMessageWrapper) {
        chatMessageList.add(`object`)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val wrapper = chatMessageList[position]
        holder.msg.text = wrapper.message.text

        val layout = holder.mView.findViewById<LinearLayout>(R.id.bubble_layout) as LinearLayout
        val parent_layout = holder.mView.findViewById<LinearLayout>(R.id.bubble_layout_parent) as LinearLayout
        // if message is mine then align to right
        if (wrapper.client) {
            layout.setBackgroundResource(R.drawable.bubble_right)
            parent_layout.gravity = Gravity.END
        } else {
            layout.setBackgroundResource(R.drawable.bubble_left)
            parent_layout.gravity = Gravity.START
        }
        holder.timeView.text = SimpleDateFormat.getTimeInstance().format(Date(wrapper.message.timeSent))
        Log.i("INFO", wrapper.status.toString())
        when (wrapper.status) {
            MessageStatus.WAITING -> holder.statusView.setImageResource(R.drawable.waiting)
            MessageStatus.SENT -> holder.statusView.setImageResource(R.drawable.sent)
            MessageStatus.RECEIVED -> holder.statusView.setImageResource(R.drawable.received)
            MessageStatus.SEEN -> holder.statusView.setImageResource(R.drawable.seen)
        }
        holder.msg.setTextColor(Color.BLACK)
    }

    override fun getItemCount(): Int {
        return chatMessageList.size
    }

    inner class ChatViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        val msg: TextView = mView.findViewById<TextView>(R.id.message_text) as TextView
        val timeView: TextView = mView.findViewById<TextView>(R.id.chatbubble_time_view) as TextView
        val statusView: ImageView = mView.findViewById<ImageView>(R.id.chatbubble_status_view) as ImageView
    }
}