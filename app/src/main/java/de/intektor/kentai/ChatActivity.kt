package de.intektor.kentai

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.widget.EditText
import android.widget.ImageButton
import de.intektor.kentai.kentai.ChatAdapter
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai.kentai.chat.saveMessage
import de.intektor.kentai_http_common.chat.ChatMessageRegistry
import de.intektor.kentai_http_common.chat.ChatMessageText
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.MessageStatus
import kotlinx.android.synthetic.main.activity_chat.*
import java.util.*
import kotlin.collections.HashMap

class ChatActivity : AppCompatActivity() {

    private lateinit var chatAdapter: ChatAdapter

    lateinit var chatInfo: ChatInfo

    private val messageList: MutableList<ChatMessageWrapper> = mutableListOf()

    private val messageMap: HashMap<UUID, ChatMessageWrapper> = HashMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        sendMessageButton.setOnClickListener({
            sendMessage()
        })


        val lM = LinearLayoutManager(this)
        lM.stackFromEnd = true
        msgListView.layoutManager = lM

        val chatUUID = UUID.fromString(intent.getStringExtra("chatUUID"))
        val chatName = intent.getStringExtra("chatName")
        val chatType = ChatType.values()[intent.getIntExtra("chatType", 0)]
        val participants = intent.getParcelableArrayListExtra<ChatReceiver>("participants")
        chatInfo = ChatInfo(chatUUID, chatName, chatType, participants)

//        val query = KentaiClient.INSTANCE.dataBase.query("chat_table", arrayOf("message_uuid", "additional_info", "text", "time", "type", "sender_uuid", "client"), null, null, null, null, "time DESC", "20")

        val query = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT message_uuid, additional_info, text, time, type, sender_uuid, client FROM chat_table WHERE chat_uuid = ? ORDER BY time DESC LIMIT 20", arrayOf(chatUUID.toString()))

        while (query.moveToNext()) {
            val uuid = UUID.fromString(query.getString(0))
            val blob = query.getBlob(1)
            val text = query.getString(2)
            val time = query.getLong(3)
            val type = query.getInt(4)
            val sender = query.getString(5)
            val client: Boolean = query.getInt(6) != 0

            val cursor = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT status, time FROM message_status_change WHERE message_uuid = '$uuid' ORDER BY time DESC LIMIT 1", null)
            cursor.moveToNext()
            val status = MessageStatus.values()[cursor.getInt(0)]
            val timeChange = cursor.getLong(1)
            cursor.close()

            val message = ChatMessageRegistry.create(type)
            message.id = uuid
            message.senderUUID = KentaiClient.INSTANCE.userUUID
            message.text = text
            message.timeSent = time
            message.processAdditionalInfo(blob)

            addMessage(ChatMessageWrapper(message, status, client, timeChange), false)
        }
        query.close()

        messageList.reverse()

        chatAdapter = ChatAdapter(messageList)

        msgListView.adapter = chatAdapter

        msgListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val firstVisibleIndex = lM.findFirstVisibleItemPosition()

                val lastVisibleIndex = lM.findLastVisibleItemPosition()

                (firstVisibleIndex..lastVisibleIndex)
                        .map { messageList[it] }
                        .filter { it.status != MessageStatus.SEEN }
                        .forEach {
                            if (!it.client) {
                                it.status = MessageStatus.SEEN
                                val statement = KentaiClient.INSTANCE.dataBase.compileStatement("INSERT INTO message_status_change (message_uuid, status, time) VALUES(?, ?, ?)")
                                statement.bindString(1, it.message.id.toString())
                                statement.bindLong(2, it.status.ordinal.toLong())
                                statement.bindLong(3, System.currentTimeMillis())
                                statement.execute()
                                for (s in chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }) {
//                                    sendPacket(ChangeChatMessageStatusPacketToServer(it.message.id, MessageStatus.SEEN, System.currentTimeMillis(),
//                                            KentaiClient.INSTANCE.username, s, chatInfo.chatUUID))
                                }
//                                recyclerView.adapter.notifyDataSetChanged()
                            }
                        }
                if (firstVisibleIndex == messageList.size) {

                }
            }

            override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
                super.onScrollStateChanged(recyclerView, newState)
            }
        })

    }

    private fun sendMessage() {
        val written = messageBox.text.toString()
        val chatMessage = ChatMessageText(written, KentaiClient.INSTANCE.userUUID, System.currentTimeMillis())
        val wrapper = ChatMessageWrapper(chatMessage, MessageStatus.WAITING, true, System.currentTimeMillis())
        saveMessage(chatInfo, wrapper, KentaiClient.INSTANCE.dataBase)

        addMessage(wrapper.copy(message = ChatMessageText(written, KentaiClient.INSTANCE.userUUID, System.currentTimeMillis())))
        chatAdapter.notifyDataSetChanged()

        scrollToBottom()

        val statement = KentaiClient.INSTANCE.dataBase.compileStatement("INSERT INTO pending_messages (chat_uuid, message_uuid) VALUES (?, ?)")
        statement.bindString(1, chatInfo.chatUUID.toString())
        statement.bindString(2, wrapper.message.id.toString())

        statement.execute()

        KentaiClient.INSTANCE.pendingMessages.add(KentaiClient.PendingMessage(wrapper, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }))

        KentaiClient.INSTANCE.sendPendingMessages(KentaiClient.INSTANCE.pendingMessages)
    }

    fun addMessage(chatMessageWrapper: ChatMessageWrapper, notifyDataSetChanges: Boolean = true) {
        messageList.add(chatMessageWrapper)
        messageMap.put(chatMessageWrapper.message.id, chatMessageWrapper)
        if (notifyDataSetChanges) chatAdapter.notifyDataSetChanged()
    }

    fun updateMessageStatus(messageUUID: UUID, status: MessageStatus) {
        val wrapper = messageMap[messageUUID]
        wrapper?.status = status
        chatAdapter.notifyDataSetChanged()
    }

    fun scrollToBottom() {
        msgListView.smoothScrollToPosition(messageList.size)
    }
}
