package de.intektor.mercury.media

import android.net.Uri
import android.os.AsyncTask
import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.chat.ChatMessageInfo
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.task.getVideoDuration
import de.intektor.mercury.util.Logger
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageImage
import de.intektor.mercury_common.chat.data.MessageVideo
import de.intektor.mercury_common.util.generateAESKey
import de.intektor.mercury_common.util.generateInitVector
import org.threeten.bp.Clock
import java.util.*

/**
 * This class handles the creation of media specific messages
 * @author Intektor
 */
object MediaSendUtil {

    private const val TAG = "MediaSendUtil"

    /**
     * Async creation of video messages and all its necessary parts like hashing. This method creates a message for every chat supplied
     * Also saves the reference into the local app storage.
     * @param videoUri the uri of the file
     * @param text the text if the user has supplied any
     * @param onFinishedCallback callback after the operation has finished. Returns the messages on success and null on failure
     * @param relatedChats the list of chatUUIDs for that the message should be created
     */
    fun saveVideoAndCreateMessage(mercuryClient: MercuryClient, relatedChats: List<UUID>, videoUri: Uri, text: String, isGif: Boolean, onFinishedCallback: (List<ChatMessageInfo>?) -> Unit) {
        val referenceUUID = UUID.randomUUID()

        SaveReferenceTask(mercuryClient, mercuryClient.contentResolver.openInputStream(videoUri), referenceUUID) {
            CreateVideoMessageTask(relatedChats, mercuryClient, videoUri, text, isGif, referenceUUID) { result ->
                onFinishedCallback(result)
            }.execute()
        }.execute()
    }

    /**
     * Async creation of image messages and all its necessary parts like hashing. This method creates a message for every chat supplied
     * Also saves the reference into the local app storage.
     * @param imageUri the uri of the file
     * @param text the text if the user has supplied any
     * @param onFinishedCallback callback after the operation has finished. Returns the messages on success and null on failure
     * @param relatedChats the list of chatUUIDs for that the message should be created
     */
    fun saveImageAndCreateMessage(mercuryClient: MercuryClient, relatedChats: List<UUID>, imageUri: Uri, text: String, onFinishedCallback: (List<ChatMessageInfo>?) -> Unit) {
        val referenceUUID = UUID.randomUUID()

        SaveReferenceTask(mercuryClient, mercuryClient.contentResolver.openInputStream(imageUri), referenceUUID) {
            SendImageTask(mercuryClient, referenceUUID, relatedChats, imageUri, text) { result ->
                onFinishedCallback(result)
            }.execute()
        }.execute()
    }

    private class CreateVideoMessageTask(val relatedChats: List<UUID>,
                                         val mercuryClient: MercuryClient,
                                         val uri: Uri,
                                         val text: String,
                                         val isGif: Boolean,
                                         val referenceUUID: UUID,
                                         val callback: (List<ChatMessageInfo>?) -> Unit) : AsyncTask<Unit, Unit, List<ChatMessageInfo>?>() {
        override fun doInBackground(vararg args: Unit): List<ChatMessageInfo>? =
                createVideoMessages(mercuryClient, relatedChats, uri, text, isGif, referenceUUID)

        override fun onPostExecute(result: List<ChatMessageInfo>?) {
            callback.invoke(result)
        }
    }

    /**
     * [saveVideoAndCreateMessage] but does not save the video and is not async
     * @see [saveVideoAndCreateMessage]
     */
    fun createVideoMessages(mercuryClient: MercuryClient,
                            relatedChats: List<UUID>,
                            uri: Uri,
                            text: String,
                            isGif: Boolean,
                            referenceUUID: UUID): List<ChatMessageInfo>? {
        try {
            //What comes next is all fixed for the video and doesn't change for different chats
            val clientUUID = ClientPreferences.getClientUUID(mercuryClient)
            val database = mercuryClient.dataBase

            val aesKey = generateAESKey()
            val initVector = generateInitVector()

            val (width, height) = ThumbnailUtil.getVideoDimension(mercuryClient, uri)
            val videoDuration = getVideoDuration(mercuryClient, uri)
            val hash = HashingInputStream(Hashing.sha512(), mercuryClient.contentResolver.openInputStream(uri)).hash()
            val thumbnail = ThumbnailUtil.createThumbnail(uri.path, MediaType.MEDIA_TYPE_VIDEO)

            ReferenceUtil.setReferenceKey(database, referenceUUID, aesKey, initVector)

            //The current time
            val timeCreated = Clock.systemDefaultZone().instant().toEpochMilli()

            val createdMessages = mutableListOf<ChatMessageInfo>()

            for (chatUUID in relatedChats) {
                //All here changes for each chat
                val messageUUID = UUID.randomUUID()

                ReferenceUtil.addReference(database, chatUUID, referenceUUID, messageUUID, MediaType.MEDIA_TYPE_VIDEO, timeCreated)

                val data = MessageVideo(videoDuration, isGif, width, height, thumbnail, text, aesKey, initVector, referenceUUID, hash.toString())
                val core = MessageCore(clientUUID, System.currentTimeMillis(), messageUUID)

                createdMessages += ChatMessageInfo(ChatMessage(core, data), true, chatUUID)
            }

            return createdMessages
        } catch (t: Throwable) {
            Logger.warning(TAG, "Fail while creating video message. uri=$uri, uriPath=${uri.path}", t)
            return null
        }
    }

    private class SendImageTask(val mercuryClient: MercuryClient,
                                val referenceUUID: UUID,
                                val relatedChats: List<UUID>,
                                val uri: Uri,
                                val text: String,
                                val execute: (List<ChatMessageInfo>?) -> Unit) : AsyncTask<Unit, Unit, List<ChatMessageInfo>?>() {
        override fun doInBackground(vararg args: Unit): List<ChatMessageInfo>? = createImageMessages(mercuryClient, referenceUUID, relatedChats, uri, text)

        override fun onPostExecute(result: List<ChatMessageInfo>?) {
            execute.invoke(result)
        }
    }

    /**
     * [saveImageAndCreateMessage] but does not save the image and is not async
     * @see [saveImageAndCreateMessage]
     */
    fun createImageMessages(mercuryClient: MercuryClient,
                            referenceUUID: UUID,
                            relatedChats: List<UUID>,
                            uri: Uri,
                            text: String): List<ChatMessageInfo>? {
        try {
            //What comes next is all fixed for the image and doesn't change for different chats
            val clientUUID = ClientPreferences.getClientUUID(mercuryClient)
            val database = mercuryClient.dataBase

            val referenceFile = ReferenceUtil.getFileForReference(mercuryClient, referenceUUID)

            val (width, height) = ThumbnailUtil.getBitmapDimensions(referenceFile.path)
            val hash = HashingInputStream(Hashing.sha512(), mercuryClient.contentResolver.openInputStream(uri)).hash()
            val thumbnail = ThumbnailUtil.createThumbnail(referenceFile.path, MediaType.MEDIA_TYPE_IMAGE)

            val aes = generateAESKey()
            val iV = generateInitVector()

            val timeCreated = Clock.systemDefaultZone().instant().toEpochMilli()

            val createdMessages = mutableListOf<ChatMessageInfo>()

            for (chat in relatedChats) {
                //All here changes for each chat
                val messageUUID = UUID.randomUUID()

                ReferenceUtil.setReferenceKey(database, referenceUUID, aes, iV)
                ReferenceUtil.addReference(database, chat, referenceUUID, messageUUID, MediaType.MEDIA_TYPE_IMAGE, timeCreated)

                val core = MessageCore(clientUUID, System.currentTimeMillis(), messageUUID)
                val data = MessageImage(thumbnail, text, width, height, aes, iV, referenceUUID, hash.toString())

                createdMessages += ChatMessageInfo(ChatMessage(core, data), true, chat)
            }
            return createdMessages
        } catch (t: Throwable) {
            Logger.warning(TAG, "Fail while creating image message. uri=$uri, uriPath=${uri.path}", t)
            return null
        }
    }
}