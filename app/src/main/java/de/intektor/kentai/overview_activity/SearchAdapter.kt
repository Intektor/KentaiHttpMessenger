package de.intektor.kentai.overview_activity

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.intektor.kentai.R
import de.intektor.kentai.fragment.ChatListViewAdapter
import de.intektor.kentai.kentai.android.AViewHolder
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author Intektor
 */
class SearchAdapter(private val list: List<Any>, private val clickResponse: (Any) -> Unit) : RecyclerView.Adapter<AViewHolder<Any>>() {

    companion object {
        private const val CHAT_ID = 0
        private const val CHAT_MESSAGE_ID = 1
        private const val HEADER_ID = 2
    }

    override fun getItemViewType(position: Int): Int {
        return when (list[position]) {
            is ChatListViewAdapter.ChatItem -> CHAT_ID
            is ChatMessageSearch -> CHAT_MESSAGE_ID
            is SearchHeader -> HEADER_ID
            else -> throw IllegalArgumentException()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AViewHolder<Any> {
        return when (viewType) {
            CHAT_ID -> ChatListViewAdapter.ChatItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_item, parent, false), {})
            CHAT_MESSAGE_ID -> ChatMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_search_item, parent, false))
            HEADER_ID -> SearchLabelViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.search_label, parent, false))
            else -> throw IllegalArgumentException()
        } as AViewHolder<Any>
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: AViewHolder<Any>, position: Int) {
        val item = list[position]

        holder.bind(item)

        holder.itemView.setOnClickListener {
            clickResponse.invoke(item)
        }
    }

    private class ChatMessageViewHolder(view: View) : AViewHolder<ChatMessageSearch>(view) {
        private val chatLabel: TextView = view.findViewById(R.id.chatMessageSearchChatLabel)
        private val messageLabel: TextView = view.findViewById(R.id.chatMessageSearchMessageLabel)
        private val timeLabel: TextView = view.findViewById(R.id.chatMessageSearchTimeLabel)

        companion object {
            private val dateFormat = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT)
        }

        override fun bind(item: ChatMessageSearch) {
            chatLabel.text = item.chatInfo.chatName
            messageLabel.text = item.message.message.text
            timeLabel.text = dateFormat.format(Date(item.message.message.timeSent))
        }
    }

    class SearchLabelViewHolder(view: View) : AViewHolder<SearchHeader>(view) {
        private val label: TextView = view.findViewById(R.id.searchLabelLabel)

        override fun bind(item: SearchHeader) {
            label.text = when (item.type) {
                SearchAdapter.SearchHeader.SearchHeaderType.CHATS -> itemView.context.getString(R.string.overview_activity_search_label_chats)
                SearchAdapter.SearchHeader.SearchHeaderType.MESSAGES -> itemView.context.getString(R.string.overview_activity_search_label_messages)
            }
        }
    }

    data class ChatMessageSearch(val message: ChatMessageWrapper, val chatInfo: ChatInfo)

    data class SearchHeader(val type: SearchHeaderType) {

        enum class SearchHeaderType {
            CHATS,
            MESSAGES
        }
    }
}