package de.intektor.kentai.fragment

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import de.intektor.kentai.R
import de.intektor.kentai.fragment.FragmentChatsOverview.ClickListener
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import java.text.SimpleDateFormat

class ViewAdapter(private val mValues: List<ChatItem>, private val mListener: ClickListener?) : RecyclerView.Adapter<ViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.fragment_chat, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val timeInstance = SimpleDateFormat.getTimeInstance()
        holder.item = mValues[position]
        holder.nameView.text = mValues[position].chatInfo.chatName
        holder.hintView.text = mValues[position].lastChatMessage.message.text
        holder.timeView.text = timeInstance.format(mValues[position].lastChatMessage.message.timeSent)
        holder.messageStatusView.setImageResource(R.drawable.received)

        holder.view.setOnClickListener {
            mListener?.onClickItem(holder.item)
        }
    }

    override fun getItemCount(): Int {
        return mValues.size
    }

    class ChatItem(val chatInfo: ChatInfo, var lastChatMessage: ChatMessageWrapper, var unreadMessages: Int)

    inner class ViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.findViewById(R.id.name) as TextView
        val hintView: TextView = view.findViewById(R.id.hint) as TextView
        val timeView: TextView = view.findViewById(R.id.time) as TextView
        val messageStatusView: ImageView = view.findViewById(R.id.messaage_status) as ImageView

        lateinit var item: ChatItem

    }
}
