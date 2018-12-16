package de.intektor.mercury.ui.overview_activity.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.ChatMessageWrapper
import de.intektor.mercury.chat.ChatUtil
import de.intektor.mercury.chat.MessageUtil
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.ui.util.BindableViewHolder
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury.util.getCompatDrawable
import de.intektor.mercury_common.chat.ChatType
import de.intektor.mercury_common.chat.MessageStatus
import de.intektor.mercury_common.users.ProfilePictureType
import java.text.SimpleDateFormat

class ChatListAdapter(private val chats: List<ChatItem>, private val clickResponse: (ChatItem) -> (Unit), val fragment: androidx.fragment.app.Fragment? = null) : androidx.recyclerview.widget.RecyclerView.Adapter<ChatListAdapter.ChatItemViewHolder>() {

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
        val client = ClientPreferences.getClientUUID(holder.itemView.context)

        if (chatItem.chatInfo.chatType == ChatType.TWO_PEOPLE) {
            val mercuryClient = holder.itemView.context.applicationContext as MercuryClient
            mercuryClient.removeInterestedUser(chatItem.chatInfo.participants.first { it.receiverUUID != client }.receiverUUID)
        }
    }

    override fun getItemCount(): Int = chats.size

    class ChatItem(var chatInfo: ChatInfo, var lastChatMessage: ChatMessageWrapper?, var unreadMessages: Int, var finishedInitialisation: Boolean, var selected: Boolean = false, var loading: Boolean = false)

    class ChatItemViewHolder(view: View, private val clickResponse: (ChatItem) -> Unit) : BindableViewHolder<ChatItem>(view) {
        private val nameView: TextView = view.findViewById(R.id.chatItemName)
        private val hintView: TextView = view.findViewById(R.id.chatItemLastMessage)
        private val timeView: TextView = view.findViewById(R.id.chatItemTime)
        private val messageStatusView: TextView = view.findViewById(R.id.chatItemMessageStatus)
        private val unreadMessages: TextView = view.findViewById(R.id.chatItemUnreadMessages)
        private val selectedView: ImageView = view.findViewById(R.id.chatItemCheck)
        private val picture: ImageView = view.findViewById(R.id.chatItemChatPicture)
        private val loadBar: ProgressBar = view.findViewById(R.id.chatItemProgressBar)

        private companion object {
            private val timeFormat = SimpleDateFormat.getTimeInstance(SimpleDateFormat.SHORT)
        }

        override fun bind(item: ChatItem) {
            val context = itemView.context

            nameView.text = ChatUtil.getChatName(context, context.mercuryClient().dataBase, item.chatInfo.chatUUID)

            val client = ClientPreferences.getClientUUID(context)

            val lastMessage = item.lastChatMessage?.chatMessageInfo?.message
            val isClient = item.lastChatMessage?.chatMessageInfo?.client ?: false
            val time = lastMessage?.messageCore?.timeCreated ?: 0
            val status = item.lastChatMessage?.latestStatus ?: MessageStatus.WAITING

            val previewText = if (lastMessage != null) MessageUtil.getPreviewText(context, lastMessage) else ""

            hintView.text = previewText
            timeView.text = timeFormat.format(time)

            unreadMessages.text = item.unreadMessages.toString()

            if (item.unreadMessages < 1) {
                unreadMessages.visibility = View.INVISIBLE
            } else {
                unreadMessages.visibility = View.VISIBLE
            }

            messageStatusView.visibility = if (isClient) View.VISIBLE else View.GONE

            messageStatusView.text = context.getString(when (status) {
                MessageStatus.WAITING -> R.string.message_status_waiting
                MessageStatus.SENT -> R.string.message_status_sent
                MessageStatus.RECEIVED -> R.string.message_status_received
                MessageStatus.SEEN -> R.string.message_status_seen
            })

            selectedView.visibility = if (item.selected) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                clickResponse.invoke(item)
            }

            itemView.tag = adapterPosition

            if (item.chatInfo.chatType == ChatType.TWO_PEOPLE) {
                val mercuryClient = context.applicationContext as MercuryClient
                val other = item.chatInfo.participants.firstOrNull { it.receiverUUID != client }
                if (other != null) {
                    mercuryClient.addInterestedUser(other.receiverUUID)

                    ProfilePictureUtil.loadProfilePicture(other.receiverUUID, ProfilePictureType.SMALL, picture, context.resources.getCompatDrawable(R.drawable.baseline_account_circle_24, context.theme))
                }
            }

            if (!item.finishedInitialisation) {
                picture.setImageResource(R.drawable.baseline_account_circle_24)

                loadBar.visibility = if (item.loading) View.VISIBLE else View.GONE
                picture.visibility = if (item.loading) View.GONE else View.VISIBLE
            }

            loadBar.visibility = if (item.loading) View.VISIBLE else View.GONE
        }
    }
}
