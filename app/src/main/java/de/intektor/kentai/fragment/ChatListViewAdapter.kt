package de.intektor.kentai.fragment

import android.support.v4.app.Fragment
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import com.squareup.picasso.Picasso
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.android.AViewHolder
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.getAttrDrawable
import de.intektor.kentai.kentai.getProfilePicture
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import java.text.SimpleDateFormat

class ChatListViewAdapter(private val chats: List<ChatItem>, private val clickResponse: (ChatItem) -> (Unit), val fragment: Fragment? = null) : RecyclerView.Adapter<ChatListViewAdapter.ChatItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatItemViewHolder {
        val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.chat_item, parent, false)
        return ChatItemViewHolder(view, clickResponse)
    }

    override fun onBindViewHolder(holder: ChatItemViewHolder, position: Int) {
        holder.bind(chats[position])
        fragment?.registerForContextMenu(holder.itemView)
    }

    override fun onViewRecycled(holder: ChatItemViewHolder) {
        val position = holder.itemView.tag as Int
        if (chats.size <= position) return
        val chatItem = chats[position]
        if (chatItem.chatInfo.chatType == ChatType.TWO_PEOPLE) {
            val kentaiClient = holder.itemView.context.applicationContext as KentaiClient
            kentaiClient.removeInterestedUser(chatItem.chatInfo.participants.first { it.receiverUUID != kentaiClient.userUUID }.receiverUUID)
        }
    }

    override fun getItemCount(): Int = chats.size

    class ChatItem(var chatInfo: ChatInfo, var lastChatMessage: ChatMessageWrapper, var unreadMessages: Int, var finished: Boolean, var selected: Boolean = false, var loading: Boolean = false)

    class ChatItemViewHolder(view: View, private val clickResponse: (ChatItem) -> Unit) : AViewHolder<ChatItem>(view) {
        private val nameView: TextView = view.findViewById(R.id.chatItemName)
        private val hintView: TextView = view.findViewById(R.id.chatItemLastMessage)
        private val timeView: TextView = view.findViewById(R.id.chatItemTime)
        private val messageStatusView: ImageView = view.findViewById(R.id.chatItemMessageStatus)
        private val unreadMessages: TextView = view.findViewById(R.id.chatItemUnreadMessages)
        private val selectedView: ImageView = view.findViewById(R.id.chatItemCheck)
        private val picture: ImageView = view.findViewById(R.id.chatItemChatPicture)
        private val loadBar: ProgressBar = view.findViewById(R.id.chatItemProgressBar)

        private companion object {
            private val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
        }

        override fun bind(item: ChatItem) {
            nameView.text = item.chatInfo.chatName
            hintView.text = item.lastChatMessage.message.text
            timeView.text = timeFormat.format(item.lastChatMessage.message.timeSent)
            unreadMessages.text = item.unreadMessages.toString()

            if (item.unreadMessages < 1) {
                unreadMessages.visibility = View.INVISIBLE
            } else {
                unreadMessages.visibility = View.VISIBLE
            }

            messageStatusView.visibility = if (item.lastChatMessage.client) View.VISIBLE else View.GONE

            messageStatusView.setImageResource(when (item.lastChatMessage.status) {
                MessageStatus.WAITING -> R.drawable.waiting
                MessageStatus.SENT -> R.drawable.sent
                MessageStatus.RECEIVED -> R.drawable.received
                MessageStatus.SEEN -> R.drawable.seen
            })

            selectedView.visibility = if (item.selected) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                clickResponse.invoke(item)
            }

            itemView.tag = adapterPosition

            if (item.chatInfo.chatType == ChatType.TWO_PEOPLE) {
                val kentaiClient = itemView.context.applicationContext as KentaiClient
                val other = item.chatInfo.participants.firstOrNull { it.receiverUUID != kentaiClient.userUUID }
                if (other != null) {
                    kentaiClient.addInterestedUser(other.receiverUUID)

                    Picasso.with(itemView.context)
                            .load(getProfilePicture(other.receiverUUID, itemView.context))
                            .placeholder(getAttrDrawable(itemView.context, R.attr.ic_account))
                            .into(picture)
                }
            }

            if (!item.finished) {
                val attr = if (!item.loading) R.attr.ic_file_download else R.attr.ic_account
                picture.setImageDrawable(getAttrDrawable(itemView.context, attr))

                loadBar.visibility = if (item.loading) View.VISIBLE else View.GONE
                picture.visibility = if (item.loading) View.GONE else View.VISIBLE
            }

            loadBar.visibility = if (item.loading) View.VISIBLE else View.GONE
        }
    }
}
