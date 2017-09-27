package de.intektor.kentai.overview_activity

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.fragment.ViewAdapter
import de.intektor.kentai.fragment.ViewAdapter.ChatItem
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.readChatParticipants
import de.intektor.kentai_http_common.chat.*
import java.util.*
import kotlin.collections.HashMap


class FragmentChatsOverview : Fragment() {

    val shownChatList: MutableList<ViewAdapter.ChatItem> = mutableListOf()
    val chatMap: HashMap<UUID, ChatItem> = HashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater!!.inflate(R.layout.fragment_chat_list, container, false)

        // Set the adapter
        if (view is RecyclerView) {
            val context = view.getContext()
            view.layoutManager = LinearLayoutManager(context)

            val cursor = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT chat_name, chat_uuid, type FROM chats", null)

            while (cursor.moveToNext()) {
                val chatName = cursor.getString(0)
                val chatUUID = UUID.fromString(cursor.getString(1))
                val chatType = ChatType.values()[cursor.getInt(2)]

                val chatInfo = ChatInfo(chatUUID, chatName, chatType, readChatParticipants(KentaiClient.INSTANCE.dataBase, chatUUID))

                val cursor2 = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT message_uuid, additional_info, text, time, type, sender_uuid, reference FROM chat_table ORDER BY time LIMIT 1", null)

                var message: ChatMessage = ChatMessageText("---", UUID.randomUUID(), 0L)
                var senderUUID = UUID.randomUUID()

                if (cursor2.moveToNext()) {
                    val uuid = UUID.fromString(cursor2.getString(0))
                    val blob = cursor2.getBlob(1)
                    val text = cursor2.getString(2)
                    val time = cursor2.getLong(3)
                    val type = cursor2.getInt(4)
                    senderUUID = UUID.fromString(cursor2.getString(5))
                    val reference = UUID.fromString(cursor2.getString(6))

                    message = ChatMessageRegistry.create(type)
                    message.id = uuid
                    message.senderUUID = senderUUID
                    message.text = text
                    message.timeSent = time
                    message.referenceUUID = reference
                    message.processAdditionalInfo(blob)
                }
                cursor2.close()

                val cursor5 = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT status, time FROM message_status_change WHERE message_uuid = '${message.id}' ORDER BY time DESC LIMIT 1", null)

                cursor5.moveToNext()

                val status = MessageStatus.values()[cursor5.getInt(0)]
                val timeChanged = cursor5.getLong(1)

                cursor5.close()

                addChat(ChatItem(chatInfo, ChatMessageWrapper(message, status, KentaiClient.INSTANCE.userUUID == senderUUID, timeChanged), 0))
            }

            cursor.close()

            view.adapter = ViewAdapter(shownChatList, object : ClickListener {
                override fun onClickItem(item: ChatItem) {
                    val intent = Intent(KentaiClient.INSTANCE.currentActivity, ChatActivity::class.java)
                    intent.putExtra("chatInfo", item.chatInfo)
                    KentaiClient.INSTANCE.currentActivity!!.startActivity(intent)
                }
            })
        }
        return view
    }

    fun addChat(chatItem: ChatItem) {
        shownChatList.add(chatItem)
        chatMap.put(chatItem.chatInfo.chatUUID, chatItem)
    }

    interface ClickListener {
        fun onClickItem(item: ChatItem)
    }
}
