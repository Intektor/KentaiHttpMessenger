package de.intektor.mercury.ui.chat.adapter.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import de.intektor.mercury.R
import de.intektor.mercury.chat.adapter.*
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury_common.chat.data.*
import de.intektor.mercury_common.chat.data.group_modification.*
import java.util.*

/**
 * @author Intektor
 */
class ChatAdapter(private val componentList: MutableList<ChatAdapterWrapper<out ChatAdapterSubItem>>,
                  val chatInfo: ChatInfo,
                  val contactMap: Map<UUID, Contact>,
                  val activity: ChatActivity) : androidx.recyclerview.widget.RecyclerView.Adapter<AbstractViewHolder<out ChatAdapterSubItem>>() {

    companion object {
        const val TEXT_MESSAGE_ID = 0
        const val GROUP_INVITE_ID = 1
        const val USERNAME_CHAT_INFO = 2
        const val TIME_STATUS_INFO = 3
        const val GROUP_MODIFICATION_CHANGE_NAME = 4
        const val GROUP_MODIFICATION_CHANGE_ROLE = 5
        const val GROUP_MODIFICATION_CHANGE_KICK_USER = 6
        const val GROUP_MODIFICATION_CHANGE_ADD_USER = 7
        const val VOICE_MESSAGE = 8
        const val IMAGE_MESSAGE = 9
        const val VIDEO_MESSAGE = 10
        const val NINE_GAG_VIEW_ID = 11
        const val GIF_MESSAGE = 12
        const val DATE_INFO = 13
    }

    fun add(any: ChatAdapterWrapper<out ChatAdapterSubItem>) {
        componentList.add(any)
    }

    override fun getItemViewType(position: Int): Int {
        val component = componentList[position].item
        return when (component) {
            is ReferenceHolder -> {
                val data = component.message.chatMessageInfo.message.messageData
                when (data) {
                    is MessageVoiceMessage -> VOICE_MESSAGE
                    is MessageImage -> IMAGE_MESSAGE
                    is MessageVideo -> {
                        if (data.isGif) {
                            GIF_MESSAGE
                        } else {
                            VIDEO_MESSAGE
                        }
                    }
//                    MessageType.TEXT_MESSAGE -> {
//                        if (isNineGagMessage(component.chatMessageInfo.message.text)) {
//                            return NINE_GAG_VIEW_ID
//                        } else TODO()
//                    }
                    else -> TODO()
                }
            }
            is ChatAdapterMessage -> {
                val messageData = component.message.message.messageData
                when (messageData) {
                    is MessageText -> TEXT_MESSAGE_ID
                    is MessageGroupInvite -> GROUP_INVITE_ID
                    is MessageGroupModification -> {
                        val groupModification = messageData.groupModification
                        when (groupModification) {
                            is GroupModificationChangeName -> GROUP_MODIFICATION_CHANGE_NAME
                            is GroupModificationChangeRole -> GROUP_MODIFICATION_CHANGE_ROLE
                            is GroupModificationKickUser -> GROUP_MODIFICATION_CHANGE_KICK_USER
                            is GroupModificationAddUser -> GROUP_MODIFICATION_CHANGE_ADD_USER
                            else -> TODO()
                        }
                    }
                    else -> {
                        TODO("$component")
                    }
                }
            }
//            is UsernameChatInfo -> USERNAME_CHAT_INFO
            is TimeStatusChatInfo -> TIME_STATUS_INFO

            is DateInfo -> DATE_INFO
            else -> TODO("$component")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder<out ChatAdapterSubItem> {
        return when (viewType) {
            TEXT_MESSAGE_ID -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_text, parent, false), this)
            GROUP_INVITE_ID -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_text, parent, false), this)
//            USERNAME_CHAT_INFO -> UsernameChatInfoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_sender_info, parent, false), this)
            TIME_STATUS_INFO -> TimeStatusViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_time_info, parent, false), this)
            GROUP_MODIFICATION_CHANGE_NAME -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_text, parent, false), this)
            GROUP_MODIFICATION_CHANGE_ROLE -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_text, parent, false), this)
            GROUP_MODIFICATION_CHANGE_KICK_USER -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_text, parent, false), this)
            GROUP_MODIFICATION_CHANGE_ADD_USER -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_text, parent, false), this)
            VOICE_MESSAGE -> VoiceMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_voice_message, parent, false), this)
            IMAGE_MESSAGE -> ImageMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_image, parent, false), this)
            VIDEO_MESSAGE -> VideoMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_video, parent, false), this)
            GIF_MESSAGE -> GifMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_gif, parent, false), this)
            DATE_INFO -> DateViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_date_item, parent, false), this)
            else -> throw RuntimeException()
        }
    }

    override fun onBindViewHolder(holder: AbstractViewHolder<out ChatAdapterSubItem>, position: Int) {
        try {
            holder as AbstractViewHolder<ChatAdapterSubItem>

            val wrapper = componentList[position]
            holder.itemView.tag = position
            holder.bind(wrapper as ChatAdapterWrapper<ChatAdapterSubItem>)
        } catch (t: Throwable) {
            Toast.makeText(activity, t.localizedMessage, Toast.LENGTH_SHORT).show()
        }
    }

    override fun getItemCount(): Int = componentList.size

    class ChatAdapterWrapper<T : ChatAdapterSubItem>(var selected: Boolean = false, val item: T)
}