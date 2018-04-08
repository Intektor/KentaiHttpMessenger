package de.intektor.kentai.kentai.chat

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.AsyncTask
import android.support.v13.view.inputmethod.EditorInfoCompat
import android.support.v13.view.inputmethod.InputConnectionCompat
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.EditText
import android.support.v4.os.BuildCompat
import com.google.common.hash.Hashing
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.createSmallPreviewImage
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai.kentai.references.getVideoDuration
import de.intektor.kentai_http_common.chat.ChatMessageImage
import de.intektor.kentai_http_common.chat.ChatMessageVideo
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.reference.FileType
import java.util.*


class RichEditText(context: Context, attrs: AttributeSet) : EditText(context, attrs) {

    override fun onCreateInputConnection(editorInfo: EditorInfo): InputConnection {
        val inputConnection = super.onCreateInputConnection(editorInfo)
        EditorInfoCompat.setContentMimeTypes(editorInfo,
                arrayOf("image/png", "image/gif"))

        val kentaiClient = context.applicationContext as KentaiClient

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
                        val referenceFile = getReferenceFile(referenceUUID, fileType, kentaiClient.filesDir, kentaiClient)
                        context.contentResolver.openInputStream(inputContentInfo.contentUri).copyTo(referenceFile.outputStream(), 1024 * 1024)
                        val hash = Hashing.sha512().hashBytes(referenceFile.readBytes())
                        val message = when (fileType) {
                            FileType.IMAGE -> ChatMessageImage(hash.toString(), kentaiClient.userUUID, "", System.currentTimeMillis(), createSmallPreviewImage(referenceFile, fileType))
                            FileType.GIF -> {
                                val videoDuration = getVideoDuration(referenceFile, kentaiClient)
                                ChatMessageVideo(hash.toString(), videoDuration, kentaiClient.userUUID, "", System.currentTimeMillis(), true)
                            }
                            else -> throw IllegalArgumentException()
                        }
                        message.referenceUUID = referenceUUID
                        val wrapper = ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis())

                        context.addMessage(wrapper, true)
                        sendMessageToServer(kentaiClient, PendingMessage(wrapper, context.chatInfo.chatUUID, context.chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID }), kentaiClient.dataBase)
                    }
                } catch (e: Exception) {
                    return@OnCommitContentListener false
                }
            }
            return@OnCommitContentListener true
        }
        return InputConnectionCompat.createWrapper(inputConnection, editorInfo, callback)
    }

    class DownloadMediaTask(private val callback: (ChatMessageWrapper) -> (Unit), val kentaiClient: KentaiClient, val uri: Uri, private val fileType: FileType, private val contentResolver: ContentResolver) : AsyncTask<Unit, Unit, ChatMessageWrapper>() {
        override fun doInBackground(vararg p0: Unit?): ChatMessageWrapper? {
            return null
        }

        override fun onPostExecute(result: ChatMessageWrapper) {
            callback.invoke(result)
        }
    }
}