package de.intektor.kentai

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import de.intektor.kentai.fragment.ChatListViewAdapter
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.PendingMessage
import de.intektor.kentai.kentai.chat.readChats
import de.intektor.kentai.kentai.chat.sendMessageToServer
import de.intektor.kentai.overview_activity.FragmentChatsOverview
import de.intektor.kentai_http_common.chat.ChatMessageText
import de.intektor.kentai_http_common.chat.MessageStatus
import kotlinx.android.synthetic.main.activity_share_receive.*

class ShareReceiveActivity : AppCompatActivity(), FragmentChatsOverview.ClickListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_share_receive)

        shareReceiveList.layoutManager = LinearLayoutManager(this)

        val list = readChats(KentaiClient.INSTANCE.dataBase, this).sortedByDescending { it.lastChatMessage.message.timeSent }

        shareReceiveList.adapter = ChatListViewAdapter(list, this)

    }

    override fun onClickItem(item: ChatListViewAdapter.ChatItem) {
        val action = intent.action
        val type = intent.type

        if (Intent.ACTION_SEND == action && type != null) {
            if (type == "text/plain") {
                val message = ChatMessageText(intent.getStringExtra(Intent.EXTRA_TEXT), KentaiClient.INSTANCE.userUUID, System.currentTimeMillis())
                val wrapper = ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis())
                sendMessageToServer(this, PendingMessage(wrapper, item.chatInfo.chatUUID, item.chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }))

                val i = Intent(this@ShareReceiveActivity, ChatActivity::class.java)
                i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                i.putExtra("chatInfo", item.chatInfo)
                startActivity(i)
            }
        }
    }
}
