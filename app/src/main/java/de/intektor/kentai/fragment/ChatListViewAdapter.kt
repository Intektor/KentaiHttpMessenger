package de.intektor.kentai.fragment

import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import java.text.SimpleDateFormat

class ChatListViewAdapter(private val chats: List<ChatItem>, private val clickResponse: (ChatItem) -> (Unit), val fragment: Fragment? = null) : RecyclerView.Adapter<ChatListViewAdapter.ChatItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatItemViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.chat_item, parent, false)
        return ChatItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatItemViewHolder, position: Int) {
        val timeInstance = SimpleDateFormat.getTimeInstance()
        val chatItem = chats[position]
        holder.item = chatItem
        holder.nameView.text = chatItem.chatInfo.chatName
        holder.hintView.text = chatItem.lastChatMessage.message.text
        holder.timeView.text = timeInstance.format(chatItem.lastChatMessage.message.timeSent)
        holder.unreadMessages.text = chatItem.unreadMessages.toString()

        if (chatItem.unreadMessages < 1) {
            holder.unreadMessages.visibility = View.INVISIBLE
        } else {
            holder.unreadMessages.visibility = View.VISIBLE
        }

        holder.messageStatusView.visibility = if (chatItem.lastChatMessage.client) View.VISIBLE else View.GONE

        holder.messageStatusView.setImageResource(when (chatItem.lastChatMessage.status) {
            MessageStatus.WAITING -> R.drawable.waiting
            MessageStatus.SENT -> R.drawable.sent
            MessageStatus.RECEIVED -> R.drawable.received
            MessageStatus.SEEN -> R.drawable.seen
        })

        holder.selectedView.visibility = if (chatItem.selected) View.VISIBLE else View.GONE

        holder.view.setOnClickListener {
            clickResponse.invoke(holder.item)
        }

        holder.view.tag = position

        fragment?.registerForContextMenu(holder.view)

        if (chatItem.chatInfo.chatType == ChatType.TWO_PEOPLE) {
            val kentaiClient = holder.itemView.context.applicationContext as KentaiClient
            kentaiClient.addInterestedUser(chatItem.chatInfo.participants.first { it.receiverUUID != kentaiClient.userUUID }.receiverUUID)
        }
    }

    override fun onViewRecycled(holder: ChatItemViewHolder) {
        val position = holder.view.tag as Int
        val chatItem = chats[position]
        if (chatItem.chatInfo.chatType == ChatType.TWO_PEOPLE) {
            val kentaiClient = holder.itemView.context.applicationContext as KentaiClient
            kentaiClient.removeInterestedUser(chatItem.chatInfo.participants.first { it.receiverUUID != kentaiClient.userUUID }.receiverUUID)
        }
    }

    override fun getItemCount(): Int = chats.size

    class ChatItem(var chatInfo: ChatInfo, var lastChatMessage: ChatMessageWrapper, var unreadMessages: Int, val chatPicturePath: String = "", var selected: Boolean = false)

    inner class ChatItemViewHolder(val view: View) : RecyclerView.ViewHolder(view) {
        val nameView: TextView = view.findViewById(R.id.chatItemName)
        val hintView: TextView = view.findViewById(R.id.chatItemLastMessage)
        val timeView: TextView = view.findViewById(R.id.chatItemTime)
        val messageStatusView: ImageView = view.findViewById(R.id.chatItemMessageStatus)
        val unreadMessages: TextView = view.findViewById(R.id.chatItemUnreadMessages)
        val selectedView: ImageView = view.findViewById(R.id.chatItemCheck)

        lateinit var item: ChatItem

    }
}
