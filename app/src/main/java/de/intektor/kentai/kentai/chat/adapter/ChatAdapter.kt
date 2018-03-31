package de.intektor.kentai.kentai.chat.adapter

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.getReferenceState
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.references.UploadState
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.MessageType
import de.intektor.kentai_http_common.chat.group_modification.*
import de.intektor.kentai_http_common.reference.FileType
import java.util.*

/**
 * @author Intektor
 */
class ChatAdapter(private val componentList: MutableList<Any>, val chatInfo: ChatInfo, val contactMap: Map<UUID, Contact>, val activity: ChatActivity) : RecyclerView.Adapter<AbstractViewHolder>() {

    companion object {
        val TEXT_MESSAGE_ID = 0
        val GROUP_INVITE_ID = 1
        val USERNAME_CHAT_INFO = 2
        val TIME_STATUS_INFO = 3
        val GROUP_MODIFICATION_CHANGE_NAME = 4
        val GROUP_MODIFICATION_CHANGE_ROLE = 5
        val GROUP_MODIFICATION_CHANGE_KICK_USER = 6
        val GROUP_MODIFICATION_CHANGE_ADD_USER = 7
        val VOICE_MESSAGE = 8
        val IMAGE_MESSAGE = 9
        val VIDEO_MESSAGE = 10

        val NINE_GAG_VIEW_ID = 11

        val DATA_PAYLOAD_TYPE = "DATA_PAYLOAD_TYPE"

        val UPLOAD_REFERENCE_STARTED = 0
        val UPLOAD_REFERENCE_FINISHED = 1
        val DOWNLOAD_REFERENCE_STARTED = 2
        val DOWNLOAD_REFERENCE_FINISHED = 3

        val DATA_SUCCESSFUL = "DATA_SUCCESSFUL"
    }

    fun add(any: Any) {
        componentList.add(any)
    }

    override fun getItemViewType(position: Int): Int {
        val component = componentList[position]
        return when (component) {
            is ChatMessageWrapper -> when (MessageType.values()[ChatMessageRegistry.getID(component.message.javaClass)]) {
                MessageType.TEXT_MESSAGE -> {
                    if (component.message.text.contains("https://9gag.com/gag/")) {
                        return NINE_GAG_VIEW_ID
                    }
                    TEXT_MESSAGE_ID
                }
                MessageType.GROUP_INVITE -> GROUP_INVITE_ID
                MessageType.VOICE_MESSAGE -> VOICE_MESSAGE
                MessageType.IMAGE_MESSAGE -> IMAGE_MESSAGE
                MessageType.VIDEO_MESSAGE -> VIDEO_MESSAGE
                MessageType.GROUP_MODIFICATION -> {
                    val message = component.message
                    message as ChatMessageGroupModification
                    when {
                        message.groupModification is GroupModificationChangeName -> GROUP_MODIFICATION_CHANGE_NAME
                        message.groupModification is GroupModificationChangeRole -> GROUP_MODIFICATION_CHANGE_ROLE
                        message.groupModification is GroupModificationKickUser -> GROUP_MODIFICATION_CHANGE_KICK_USER
                        message.groupModification is GroupModificationAddUser -> GROUP_MODIFICATION_CHANGE_ADD_USER
                        else -> TODO()
                    }
                }
                else -> {
                    Log.e("ERROR", "")
                    TODO()
                }
            }
            is UsernameChatInfo -> USERNAME_CHAT_INFO
            is TimeStatusChatInfo -> TIME_STATUS_INFO
            else -> TODO("$component")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder =
            when (viewType) {
                TEXT_MESSAGE_ID -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false), this)
                GROUP_INVITE_ID -> GroupInviteViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_group_invite, parent, false), this)
                USERNAME_CHAT_INFO -> UsernameChatInfoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_sender_info, parent, false), this)
                TIME_STATUS_INFO -> TimeStatusViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_time_info, parent, false), this)
                GROUP_MODIFICATION_CHANGE_NAME -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false), this)
                GROUP_MODIFICATION_CHANGE_ROLE -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false), this)
                GROUP_MODIFICATION_CHANGE_KICK_USER -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false), this)
                GROUP_MODIFICATION_CHANGE_ADD_USER -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false), this)
                VOICE_MESSAGE -> VoiceMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_voice_message, parent, false), this)
                IMAGE_MESSAGE -> ImageMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_image, parent, false), this)
                VIDEO_MESSAGE -> VideoMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_video, parent, false), this)
                NINE_GAG_VIEW_ID -> NineGagViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.nine_gag_bubble, parent, false), this)
                else -> throw RuntimeException()
            }

    override fun onBindViewHolder(holder: AbstractViewHolder, position: Int) {
        val wrapper: Any = componentList[position]
        holder.itemView.tag = position
        holder.bind(wrapper)
    }

    override fun onBindViewHolder(holder: AbstractViewHolder?, position: Int, payloads: MutableList<Any>?) {
        holder?.bindPayload(payloads)
        super.onBindViewHolder(holder, position, payloads)
    }

    override fun getItemCount(): Int = componentList.size

}

fun isReferenceDownloaded(referenceUUID: UUID, fileType: FileType, chatInfo: ChatInfo, context: Context): Boolean =
        getReferenceFile(chatInfo.chatUUID, referenceUUID, fileType, context.filesDir, context).exists()

fun isReferenceUploaded(dataBase: SQLiteDatabase, chatUUID: UUID, referenceUUID: UUID): Boolean =
        getReferenceState(dataBase, chatUUID, referenceUUID) == UploadState.UPLOADED