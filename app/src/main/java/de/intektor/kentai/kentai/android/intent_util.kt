package de.intektor.kentai.kentai.android

import android.content.Intent
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.MessageStatus
import java.util.*

/**
 * @author Intektor
 */
/**
 * @param wrapper the wrapper
 * @param id the id, starting from 0, 1, 2, ...
 */
fun Intent.writeMessageWrapper(wrapper: ChatMessageWrapper, id: Int) {
    this.putExtra("wrapper_client$id", wrapper.client)
    this.putExtra("wrapper_status$id", wrapper.status.ordinal)
    this.putExtra("wrapper_status_change_time$id", wrapper.statusChangeTime)
    this.putExtra("wrapper_chat_uuid$id", wrapper.chatUUID)

    this.putExtra("wrapper_message_registry_id$id", ChatMessageRegistry.getID(wrapper.message.javaClass))
    this.putExtra("wrapper_message_aes_key$id", wrapper.message.aesKey)
    this.putExtra("wrapper_message_id$id", wrapper.message.id)
    this.putExtra("wrapper_message_init_vector$id", wrapper.message.initVector)
    this.putExtra("wrapper_message_sender_uuid$id", wrapper.message.senderUUID)
    this.putExtra("wrapper_message_text$id", wrapper.message.text)
    this.putExtra("wrapper_message_time_sent$id", wrapper.message.timeSent)
    this.putExtra("wrapper_message_reference$id", wrapper.message.referenceUUID)
    this.putExtra("wrapper_message_additional_info$id", wrapper.message.getAdditionalInfo())
}

fun Intent.readMessageWrapper(id: Int): ChatMessageWrapper {
    val wrapperClient = getBooleanExtra("wrapper_client$id", false)
    val wrapperStatus = MessageStatus.values()[getIntExtra("wrapper_status$id", 0)]
    val wrapperStatusChangeTime = getLongExtra("wrapper_status_change_time$id", 0)
    val chatUUID = getSerializableExtra("wrapper_chat_uuid$id") as UUID

    val message = ChatMessageRegistry.create(getIntExtra("wrapper_message_registry_id$id", 0))
    message.aesKey = getStringExtra("wrapper_message_aes_key$id")
    message.id = getStringExtra("wrapper_message_id$id")
    message.initVector = getStringExtra("wrapper_message_init_vector$id")
    message.senderUUID = getSerializableExtra("wrapper_message_sender_uuid$id") as UUID
    message.text = getStringExtra("wrapper_message_text$id")
    message.timeSent = getLongExtra("wrapper_message_time_sent$id", 0L)
    message.referenceUUID = getSerializableExtra("wrapper_message_reference$id") as UUID
    message.processAdditionalInfo(getByteArrayExtra("wrapper_message_additional_info$id"))

    return ChatMessageWrapper(message, wrapperStatus, wrapperClient, wrapperStatusChangeTime, chatUUID)
}