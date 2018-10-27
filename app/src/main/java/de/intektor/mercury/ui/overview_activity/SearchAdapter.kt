package de.intektor.mercury.ui.overview_activity

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import de.intektor.mercury.R
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.chat.ChatMessageInfo
import de.intektor.mercury.chat.MessageUtil
import de.intektor.mercury.ui.overview_activity.fragment.ChatListViewAdapter
import de.intektor.mercury.ui.util.BindableViewHolder
import java.text.SimpleDateFormat
import java.util.*

/**
 * @author Intektor
 */
class SearchAdapter(private val list: List<Any>, private val clickResponse: (Any) -> Unit) : RecyclerView.Adapter<BindableViewHolder<Any>>() {

    companion object {
        private const val CHAT_ID = 0
        private const val CHAT_MESSAGE_ID = 1
        private const val HEADER_ID = 2
    }

    override fun getItemViewType(position: Int): Int {
        val item = list[position]
        return when (item) {
            is ChatListViewAdapter.ChatItem -> CHAT_ID
            is ChatMessageSearch -> CHAT_MESSAGE_ID
            is SearchHeader -> HEADER_ID
            else -> throw IllegalArgumentException()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindableViewHolder<Any> {
        return when (viewType) {
            CHAT_ID -> ChatListViewAdapter.ChatItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_item, parent, false), {})
            CHAT_MESSAGE_ID -> ChatMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_search_item, parent, false))
            HEADER_ID -> SearchLabelViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.search_label, parent, false))
            else -> throw IllegalArgumentException()
        } as BindableViewHolder<Any>
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: BindableViewHolder<Any>, position: Int) {
        val item = list[position]

        holder.bind(item)

        holder.itemView.setOnClickListener {
            clickResponse.invoke(item)
        }
    }

    private class ChatMessageViewHolder(view: View) : BindableViewHolder<ChatMessageSearch>(view) {
        private val chatLabel: TextView = view.findViewById(R.id.chatMessageSearchChatLabel)
        private val messageLabel: TextView = view.findViewById(R.id.chatMessageSearchMessageLabel)
        private val timeLabel: TextView = view.findViewById(R.id.chatMessageSearchTimeLabel)

        companion object {
            private val dateFormat = SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.SHORT, SimpleDateFormat.SHORT)
        }

        override fun bind(item: ChatMessageSearch) {
            chatLabel.text = item.chatInfo.chatName
            messageLabel.text = MessageUtil.getPreviewText(itemView.context, item.message.message)
            timeLabel.text = dateFormat.format(Date(item.message.message.messageCore.timeCreated))
        }
    }

    class SearchLabelViewHolder(view: View) : BindableViewHolder<SearchHeader>(view) {
        private val label: TextView = view.findViewById(R.id.searchLabelLabel)

        override fun bind(item: SearchHeader) {
            label.text = when (item.type) {
                SearchAdapter.SearchHeader.SearchHeaderType.CHATS -> itemView.context.getString(R.string.overview_activity_search_label_chats)
                SearchAdapter.SearchHeader.SearchHeaderType.MESSAGES -> itemView.context.getString(R.string.overview_activity_search_label_messages)
            }
        }
    }

    data class ChatMessageSearch(val message: ChatMessageInfo, val chatInfo: ChatInfo)

    data class SearchHeader(val type: SearchHeaderType) {

        enum class SearchHeaderType {
            CHATS,
            MESSAGES
        }
    }
}