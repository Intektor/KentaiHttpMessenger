package de.intektor.mercury.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.chat.ChatMessageWrapper
import de.intektor.mercury.chat.PendingMessage
import de.intektor.mercury.chat.sendMessageToServer
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.media.MediaSendUtil
import de.intektor.mercury.media.MediaType
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury_common.chat.MessageStatus
import java.util.*


class RichEditText(context: Context, attrs: AttributeSet) : EditText(context, attrs) {

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {
        val inputConnection = super.onCreateInputConnection(editorInfo)
        EditorInfoCompat.setContentMimeTypes(editorInfo,
                arrayOf("image/png", "image/gif"))

        val mercuryClient = context.applicationContext as MercuryClient

        val context = context

        val callback = InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, opts ->
            if (flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
                try {
                    inputContentInfo.requestPermission()

                    val clientUUID = ClientPreferences.getClientUUID(context)

                    val mediaType = when (inputContentInfo.description.getMimeType(0)) {
                        "image/gif" -> MediaType.MEDIA_TYPE_VIDEO
                        "image/png" -> MediaType.MEDIA_TYPE_IMAGE
                        else -> throw IllegalArgumentException("")
                    }

                    if (context is ChatActivity) {
                        val referenceUUID = UUID.randomUUID()

                        val createdMessages = when (mediaType) {
                            MediaType.MEDIA_TYPE_IMAGE -> {
                                MediaSendUtil.createImageMessages(mercuryClient, referenceUUID, listOf(context.chatInfo.chatUUID), inputContentInfo.contentUri, "")
                            }
                            MediaType.MEDIA_TYPE_VIDEO -> {
                                MediaSendUtil.createVideoMessages(mercuryClient, listOf(context.chatInfo.chatUUID), inputContentInfo.contentUri, "", true, referenceUUID)
                            }
                            else -> throw IllegalArgumentException()
                        } ?: return@OnCommitContentListener false

                        val messageInfo = createdMessages[0]

                        context.addMessageToBottom(ChatMessageWrapper(messageInfo, MessageStatus.WAITING, System.currentTimeMillis()))

                        sendMessageToServer(mercuryClient, PendingMessage(messageInfo.message, context.chatInfo.chatUUID, context.chatInfo.getOthers(clientUUID)), mercuryClient.dataBase)
                    }
                } catch (e: Exception) {
                    return@OnCommitContentListener false
                }
            }
            return@OnCommitContentListener true
        }
        return InputConnectionCompat.createWrapper(inputConnection, editorInfo, callback)
    }
}