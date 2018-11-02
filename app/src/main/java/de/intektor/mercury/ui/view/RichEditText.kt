package de.intektor.mercury.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.os.BuildCompat
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import com.google.common.hash.Hashing
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.chat.ChatMessageInfo
import de.intektor.mercury.chat.ChatMessageWrapper
import de.intektor.mercury.chat.PendingMessage
import de.intektor.mercury.chat.sendMessageToServer
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.task.ThumbnailUtil
import de.intektor.mercury.task.getVideoDimension
import de.intektor.mercury.task.getVideoDuration
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.MessageStatus
import de.intektor.mercury_common.chat.data.MessageImage
import de.intektor.mercury_common.chat.data.MessageVideo
import de.intektor.mercury_common.reference.FileType
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
            if (BuildCompat.isAtLeastNMR1() && flags and InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION != 0) {
                try {
                    inputContentInfo.requestPermission()

                    val fileType = when (inputContentInfo.description.getMimeType(0)) {
                        "image/gif" -> FileType.GIF
                        "image/png" -> FileType.IMAGE
                        else -> throw IllegalArgumentException("")
                    }

                    if (context is ChatActivity) {
                        val referenceUUID = UUID.randomUUID()
                        val referenceFile = ReferenceUtil.getFileForReference(context, referenceUUID)

                        val client = ClientPreferences.getClientUUID(context)

                        val hash = Hashing.sha512().hashBytes(referenceFile.readBytes())

                        val aes = generateAESKey()
                        val iV = generateInitVector()

                        val core = MessageCore(client, System.currentTimeMillis(), UUID.randomUUID())

                        val data = when (fileType) {
                            FileType.IMAGE -> {
                                val bitmap = BitmapFactory.decodeStream(context.contentResolver.openInputStream(inputContentInfo.contentUri))

                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, referenceFile.outputStream())

                                MessageImage(ThumbnailUtil.createThumbnail(referenceFile, FileType.IMAGE), "", bitmap.width, bitmap.height, aes, iV, referenceUUID, hash.toString())
                            }
                            FileType.GIF -> {
                                context.contentResolver.openInputStream(inputContentInfo.contentUri).copyTo(referenceFile.outputStream(), 1024 * 1024)

                                val videoDuration = getVideoDuration(referenceFile, mercuryClient)
                                val dimension = getVideoDimension(context, referenceFile)

                                MessageVideo(videoDuration,
                                        true,
                                        dimension.width,
                                        dimension.height,
                                        ThumbnailUtil.createThumbnail(referenceFile, FileType.GIF), "", aes, iV, referenceUUID, hash.toString())
                            }
                            else -> throw IllegalArgumentException()
                        }

                        val message = ChatMessage(core, data)
                        val info = ChatMessageInfo(message, true, context.chatInfo.chatUUID)
                        val wrapper = ChatMessageWrapper(info, MessageStatus.WAITING, System.currentTimeMillis())

                        context.addMessage(wrapper, true)
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