package de.intektor.mercury.chat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.AsyncTask
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.adapter.*
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.task.ReferenceState
import de.intektor.mercury.ui.chat.adapter.chat.ChatAdapter
import de.intektor.mercury_common.chat.MessageDataRegistry
import de.intektor.mercury_common.chat.data.MessageReference
import de.intektor.mercury_common.chat.data.MessageStatusUpdate
import de.intektor.mercury_common.chat.data.MessageVoiceMessage
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.ZoneId
import java.util.*

/**
 * @author Intektor
 */
object ChatLoader {

    fun loadMessageItemsForAdapterAsync(context: Context,
                                        database: SQLiteDatabase,
                                        chatInfo: ChatInfo,
                                        epochMilliMin: Long,
                                        epochMilliMax: Long,
                                        loadType: LoadType,
                                        latestChatMessageEpochMilli: Long,
                                        callback: (MessageTransform) -> Unit,
                                        limit: Int = 20) {
        LoadTask(context.mercuryClient(), database, chatInfo, epochMilliMin, epochMilliMax, loadType, latestChatMessageEpochMilli, limit, callback).execute()
    }

    fun loadMessageItemsForAdapter(context: Context,
                                   database: SQLiteDatabase,
                                   chatInfo: ChatInfo,
                                   epochMilliMin: Long,
                                   epochMilliMax: Long,
                                   loadType: LoadType,
                                   latestChatMessageEpochMilli: Long,
                                   limit: Int = 20): MessageTransform {

        val loadedMessages = loadMessages(context, database, chatInfo, epochMilliMin, epochMilliMax, loadType, limit)
        return transformMessageList(context, chatInfo, latestChatMessageEpochMilli, loadedMessages)
    }

    private fun loadMessages(context: Context, database: SQLiteDatabase, chatInfo: ChatInfo, epochMilliMin: Long, epochMilliMax: Long, loadType: LoadType, limit: Int = 20) =
            getChatMessages(context, database, "time_created < ? AND time_created > ? AND chat_uuid = ? AND data_type != ?",
                    arrayOf(epochMilliMax.toString(), epochMilliMin.toString(), chatInfo.chatUUID.toString(), MessageDataRegistry.getID(MessageStatusUpdate::class.java).toString()),
                    "time_created " + when (loadType) {
                        LoadType.ASC -> "ASC"
                        LoadType.DESC -> "DESC"
                    }, limit.toString()).asReversed()

    fun transformMessageList(context: Context, chatInfo: ChatInfo, latestChatMessageEpochMilli: Long, loaded: List<ChatMessageWrapper>): MessageTransform {
        val messageUUIDToItems = mutableMapOf<UUID, List<ChatAdapter.ChatAdapterWrapper<ChatAdapterSubItem>>>()
        val referenceUUIDToReferenceUUID = mutableMapOf<UUID, UUID>()

        val adapterItemListInOrder = mutableListOf<ChatAdapter.ChatAdapterWrapper<ChatAdapterSubItem>>()

        val alreadyAdded = mutableListOf<LocalDate>(LocalDateTime.ofInstant(Instant.ofEpochMilli(latestChatMessageEpochMilli), ZoneId.systemDefault()).toLocalDate())

        for (wrapper in loaded) {
            val info = wrapper.chatMessageInfo
            val (core, data) = info.message

            val client = wrapper.chatMessageInfo.client

            val messageItem = when (data) {
                is MessageVoiceMessage -> VoiceReferenceHolder(wrapper, getReferenceStateOfMessage(context, data, client))
                is MessageReference -> ReferenceHolder(wrapper, getReferenceStateOfMessage(context, data, client))
                else -> ChatAdapterMessage(wrapper)
            }

            val date = LocalDateTime.ofInstant(Instant.ofEpochMilli(core.timeCreated), ZoneId.systemDefault()).toLocalDate()

            if (alreadyAdded.none { it == date }) {
                adapterItemListInOrder.add(ChatAdapter.ChatAdapterWrapper(item = DateInfo(core.timeCreated)))

                alreadyAdded += date
            }

            val statusTime = TimeStatusChatInfo(core.timeCreated, wrapper.latestStatus, client)

            val items = listOf(messageItem, statusTime).map { ChatAdapter.ChatAdapterWrapper(item = it) }

            messageUUIDToItems[core.messageUUID] = items

            if (data is MessageReference) {
                referenceUUIDToReferenceUUID[data.reference] = core.messageUUID
            }

            adapterItemListInOrder += items
        }

        return MessageTransform(adapterItemListInOrder, messageUUIDToItems, referenceUUIDToReferenceUUID)
    }

    fun filterDuplicatedDateItems(adapter: ChatAdapter, currentLoadedItems: MutableList<ChatAdapter.ChatAdapterWrapper<*>>, affectedAreaStart: Int, affectedAreaEnd: Int) {
        //This is the beginning of the list and also the top, so we need to check if there is a date item above the list (below in chat perspective)
        val oldPartHighestItem = (affectedAreaEnd until currentLoadedItems.size)
                .map { currentLoadedItems[it] to it }
                .firstOrNull { (item, _) -> item.item is DateInfo }

        if (oldPartHighestItem != null) {
            val highestDateItem = (affectedAreaStart until affectedAreaEnd)
                    .map { currentLoadedItems[it] to it }
                    .lastOrNull { (item, _) -> item.item is DateInfo }

            if (highestDateItem != null) {
                val higherDate = LocalDateTime.ofInstant(Instant.ofEpochMilli((oldPartHighestItem.first.item as DateInfo).time), ZoneId.systemDefault()).toLocalDate()
                val highest = LocalDateTime.ofInstant(Instant.ofEpochMilli((highestDateItem.first.item as DateInfo).time), ZoneId.systemDefault()).toLocalDate()

                if (higherDate == highest && oldPartHighestItem.second != highestDateItem.second) {
                    currentLoadedItems.removeAt(oldPartHighestItem.second)

                    adapter.notifyItemRemoved(oldPartHighestItem.second)
                }
            }
        }

        val latestPartHighestItem = (0 until affectedAreaStart)
                .map { currentLoadedItems[it] to it }
                .lastOrNull { (item, _) -> item.item is DateInfo }

        if (latestPartHighestItem != null) {
            val latestDateItem = (affectedAreaStart until affectedAreaEnd)
                    .map { currentLoadedItems[it] to it }
                    .firstOrNull { (item, _) -> item.item is DateInfo }

            if (latestDateItem != null) {
                val lowestDate = LocalDateTime.ofInstant(Instant.ofEpochMilli((latestPartHighestItem.first.item as DateInfo).time), ZoneId.systemDefault()).toLocalDate()
                val latest = LocalDateTime.ofInstant(Instant.ofEpochMilli((latestDateItem.first.item as DateInfo).time), ZoneId.systemDefault()).toLocalDate()

                if (lowestDate == latest && latestPartHighestItem.second != latestDateItem.second) {
                    currentLoadedItems.removeAt(latestDateItem.second)

                    adapter.notifyItemRemoved(latestDateItem.second)
                }
            }
        }
    }

//    fun orderDateItems(adapter: ChatAdapter, currentLoadedItems: List<ChatAdapter.ChatAdapterWrapper<ChatAdapterSubItem>>, startIndex: Int, endIndex: Int) {
//        val belowDateItemIndex = (0..startIndex).reversed().firstOrNull { currentLoadedItems[it].item is DateInfo }
//        if (belowDateItemIndex != null) {
//            val belowDataItem = currentLoadedItems[belowDateItemIndex].item as DateInfo
//
//            val day = LocalDateTime.ofInstant(Instant.ofEpochMilli(belowDataItem.time), ZoneId.systemDefault()).toLocalDate()
//
//            (startIndex..endIndex).map { curren }
//        }
//    }

    private fun getReferenceStateOfMessage(context: Context, data: MessageReference, client: Boolean): ReferenceState =
            getReferenceState(context, context.mercuryClient().dataBase, client, data.reference)

    private fun getReferenceState(context: Context, database: SQLiteDatabase, client: Boolean, referenceUUID: UUID): ReferenceState {
        val mercury = context.mercuryClient()

        return when {
            mercury.currentLoadingTable.containsKey(referenceUUID) -> ReferenceState.IN_PROGRESS
            else -> if (when (client) {
                        true -> ReferenceUtil.isReferenceUploaded(database, referenceUUID)
                        false -> ReferenceUtil.getFileForReference(context, referenceUUID).exists()
                    }) ReferenceState.FINISHED else ReferenceState.NOT_STARTED
        }
    }

    data class MessageTransform(val items: List<ChatAdapter.ChatAdapterWrapper<*>>, val messageUUIDToItems: Map<UUID, List<ChatAdapter.ChatAdapterWrapper<ChatAdapterSubItem>>>, val referenceUUIDToMessageUUID: Map<UUID, UUID>)

    private class LoadTask(val context: MercuryClient,
                           val database: SQLiteDatabase,
                           val chatInfo: ChatInfo,
                           val epochMilliMin: Long,
                           val epochMilliMax: Long,
                           val loadType: LoadType,
                           val latestChatMessageEpochMilli: Long,
                           val limit: Int = 20,
                           val callback: (MessageTransform) -> Unit) : AsyncTask<Unit, Unit, MessageTransform>() {
        override fun doInBackground(vararg params: Unit?): MessageTransform = loadMessageItemsForAdapter(context, database, chatInfo, epochMilliMin, epochMilliMax, loadType, latestChatMessageEpochMilli, limit)

        override fun onPostExecute(result: MessageTransform) {
            callback(result)
        }
    }

    enum class LoadType {
        ASC,
        DESC
    }
}