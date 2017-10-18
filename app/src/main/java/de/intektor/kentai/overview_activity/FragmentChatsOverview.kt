package de.intektor.kentai.overview_activity

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.fragment.ChatListViewAdapter
import de.intektor.kentai.fragment.ChatListViewAdapter.ChatItem
import de.intektor.kentai.kentai.chat.readChats
import java.util.*
import kotlin.collections.HashMap


class FragmentChatsOverview : Fragment() {

    val shownChatList: MutableList<ChatListViewAdapter.ChatItem> = mutableListOf()
    val chatMap: HashMap<UUID, ChatItem> = HashMap()

    lateinit var chatList: RecyclerView

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        chatList = inflater!!.inflate(R.layout.fragment_chat_list, container, false) as RecyclerView

        val context = chatList.context
        chatList.layoutManager = LinearLayoutManager(context)

        chatList.adapter = ChatListViewAdapter(shownChatList, object : ClickListener {
            override fun onClickItem(item: ChatItem) {
                val intent = Intent(KentaiClient.INSTANCE.currentActivity, ChatActivity::class.java)
                intent.putExtra("chatInfo", item.chatInfo)
                KentaiClient.INSTANCE.currentActivity!!.startActivity(intent)
            }
        }, this@FragmentChatsOverview)

        return chatList
    }


    fun addChat(chatItem: ChatItem) {
        shownChatList.add(chatItem)
        chatMap.put(chatItem.chatInfo.chatUUID, chatItem)
    }

    interface ClickListener {
        fun onClickItem(item: ChatItem)
    }

    private fun updateList() {
        shownChatList.clear()

        readChats(KentaiClient.INSTANCE.dataBase, this@FragmentChatsOverview.context).forEach {
            addChat(it)
        }

        shownChatList.sortByDescending { it.lastChatMessage.message.timeSent }

        chatList.adapter.notifyDataSetChanged()
    }

    override fun onCreateContextMenu(menu: ContextMenu?, v: View?, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
    }

    override fun onContextItemSelected(item: MenuItem?): Boolean {
        return super.onContextItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        updateList()
    }
}
