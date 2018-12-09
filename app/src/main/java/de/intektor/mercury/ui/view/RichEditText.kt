package de.intektor.mercury.ui.view

import android.content.Context
import android.graphics.BitmapFactory
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import com.google.common.hash.Hashing
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.chat.ChatMessageInfo
import de.intektor.mercury.chat.ChatMessageWrapper
import de.intektor.mercury.chat.PendingMessage
import de.intektor.mercury.chat.sendMessageToServer
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.media.MediaType
import de.intektor.mercury.media.ThumbnailUtil
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.task.getVideoDuration
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.MessageStatus
import de.intektor.mercury_common.chat.data.MessageImage
import de.intektor.mercury_common.chat.data.MessageVideo
import de.intektor.mercury_common.util.generateAESKey
import de.intektor.mercury_common.util.generateInitVector
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

                    val mediaType = when (inputContentInfo.description.getMimeType(0)) {
                        "image/gif" -> MediaType.MEDIA_TYPE_VIDEO
                        "image/png" -> MediaType.MEDIA_TYPE_IMAGE
                        else -> throw IllegalArgumentException("")
                    }

                    if (context is ChatActivity) {
                        val referenceUUID = UUID.randomUUID()
                        val referenceFile = ReferenceUtil.getFileForReference(context, referenceUUID)

                        val client = ClientPreferences.getClientUUID(context)

                        val contentBytes = context.contentResolver.openInputStream(inputContentInfo.contentUri).readBytes()

                        val hash = Hashing.sha512().hashBytes(contentBytes)

                        val aes = generateAESKey()
                        val iV = generateInitVector()

                        val core = MessageCore(client, System.currentTimeMillis(), UUID.randomUUID())

                        referenceFile.outputStream().write(contentBytes)

                        val data = when (mediaType) {
                            MediaType.MEDIA_TYPE_IMAGE -> {
                                val bitmap = BitmapFactory.decodeByteArray(contentBytes, 0, contentBytes.size)

                                MessageImage(ThumbnailUtil.createThumbnail(referenceFile, MediaType.MEDIA_TYPE_IMAGE), "", bitmap.width, bitmap.height, aes, iV, referenceUUID, hash.toString())
                            }
                            MediaType.MEDIA_TYPE_VIDEO -> {
                                val videoDuration = getVideoDuration(referenceFile, mercuryClient)

                                val options = BitmapFactory.Options()
                                val dimension = BitmapFactory.decodeByteArray(contentBytes, 0, contentBytes.size, options)

                                MessageVideo(videoDuration,
                                        true,
                                        dimension.width,
                                        dimension.height,
                                        ThumbnailUtil.createThumbnail(referenceFile, MediaType.MEDIA_TYPE_IMAGE), "", aes, iV, referenceUUID, hash.toString())
                            }
                            else -> throw IllegalArgumentException()
                        }

                        val message = ChatMessage(core, data)
                        val info = ChatMessageInfo(message, true, context.chatInfo.chatUUID)
                        val wrapper = ChatMessageWrapper(info, MessageStatus.WAITING, System.currentTimeMillis())

                        context.addMessageToBottom(wrapper)
                        sendMessageToServer(mercuryClient, PendingMessage(message, context.chatInfo.chatUUID, context.chatInfo.getOthers(client)), mercuryClient.dataBase)
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