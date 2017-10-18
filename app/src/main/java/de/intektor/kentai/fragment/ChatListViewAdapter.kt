package de.intektor.kentai.fragment

import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.overview_activity.FragmentChatsOverview.ClickListener
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.util.minString
import java.text.SimpleDateFormat

class ChatListViewAdapter(private val mValues: List<ChatItem>, private val mListener: ClickListener?, val fragment: Fragment? = null) : RecyclerView.Adapter<ChatListViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_chat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val timeInstance = SimpleDateFormat.getTimeInstance()
        val chatItem = mValues[position]
        holder.item = chatItem
        holder.nameView.text = chatItem.chatInfo.chatName
        holder.hintView.text = chatItem.lastChatMessage.message.text.minString(0..20)
        holder.timeView.text = timeInstance.format(chatItem.lastChatMessage.message.timeSent)
        holder.messageStatusView.setImageResource(when (chatItem.lastChatMessage.status) {
            MessageStatus.WAITING -> R.drawable.waiting
            MessageStatus.SENT -> R.drawable.sent
            MessageStatus.RECEIVED -> R.drawable.received
            MessageStatus.SEEN -> R.drawable.seen
        })

        holder.view.setOnClickListener {
            mListener?.onClickItem(holder.item)
        }

        fragment?.registerForContextMenu(holder.view)
    }

    override fun getItemCount(): Int {
        return mValues.size
    }

    class ChatItem(var chatInfo: ChatInfo, var lastChatMessage: ChatMessageWrapper, var unreadMessages: Int)

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.findViewById(R.id.name)
        val hintView: TextView = view.findViewById(R.id.hint)
        val timeView: TextView = view.findViewById(R.id.time)
        val messageStatusView: ImageView = view.findViewById(R.id.messaage_status)

        lateinit var item: ChatItem

    }
}
