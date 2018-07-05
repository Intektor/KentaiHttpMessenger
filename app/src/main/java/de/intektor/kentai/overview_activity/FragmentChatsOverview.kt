package de.intektor.kentai.overview_activity

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.fragment.ChatListViewAdapter
import de.intektor.kentai.fragment.ChatListViewAdapter.ChatItem
import de.intektor.kentai.kentai.ACTION_INITIALIZE_CHAT
import de.intektor.kentai.kentai.KEY_CHAT_INFO
import de.intektor.kentai.kentai.KEY_CHAT_UUID
import de.intektor.kentai.kentai.KEY_USER_UUID
import de.intektor.kentai.kentai.chat.deleteChat
import de.intektor.kentai.kentai.chat.readChatParticipants
import de.intektor.kentai.kentai.chat.readChats
import de.intektor.kentai.kentai.firebase.SendService
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


class FragmentChatsOverview : Fragment() {

    val shownChatList: MutableList<ChatListViewAdapter.ChatItem> = mutableListOf()
    val chatMap: HashMap<UUID, ChatItem> = HashMap()

    lateinit var chatList: RecyclerView

    lateinit var kentaiClient: KentaiClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        chatList = inflater.inflate(R.layout.fragment_chat_list, container, false) as RecyclerView

        val context = chatList.context
        chatList.layoutManager = LinearLayoutManager(context)

        kentaiClient = context.applicationContext as KentaiClient

        chatList.adapter = ChatListViewAdapter(shownChatList, { item ->
            val missingUsers = readChatParticipants(kentaiClient.dataBase, item.chatInfo.chatUUID).filter { it.publicKey == null }

            if (missingUsers.isNotEmpty()) {
                val intent = Intent(context, SendService::class.java)
                intent.action = ACTION_INITIALIZE_CHAT
                intent.putStringArrayListExtra(KEY_USER_UUID, ArrayList(missingUsers.map { it.receiverUUID.toString() }))
                intent.putExtra(KEY_CHAT_UUID, item.chatInfo.chatUUID)
                context.startService(intent)

                updateInitChat(item.chatInfo.chatUUID, true, false)
            } else {
                val intent = Intent(context, ChatActivity::class.java)
                intent.putExtra(KEY_CHAT_INFO, item.chatInfo)
                context.startActivity(intent)
            }
        }, this@FragmentChatsOverview)

        return chatList
    }


    fun addChat(chatItem: ChatItem) {
        shownChatList.add(chatItem)
        chatMap[chatItem.chatInfo.chatUUID] = chatItem
    }

    fun updateList() {
        shownChatList.clear()

        readChats(kentaiClient.dataBase, this@FragmentChatsOverview.context!!).forEach {
            addChat(it)
        }

        shownChatList.sortByDescending { it.lastChatMessage.message.timeSent }

        chatList.adapter.notifyDataSetChanged()
    }

    fun updateInitChat(chatUUID: UUID, loading: Boolean, successful: Boolean) {
        val index = shownChatList.indexOfFirst { it.chatInfo.chatUUID == chatUUID }
        if (index != -1) {
            val item = shownChatList[index]
            item.loading = loading
            item.finished = successful
            chatList.adapter.notifyItemChanged(index)
        }
    }

    private var currentContextSelectedIndex = -1

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        activity?.menuInflater?.inflate(R.menu.menu_chat_item, menu)

        val position = v.tag as Int
        currentContextSelectedIndex = position

        super.onCreateContextMenu(menu, v, menuInfo)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuChatItemDelete -> {
                AlertDialog.Builder(context!!)
                        .setTitle(R.string.overview_activity_delete_chat_title)
                        .setMessage(R.string.overview_activity_delete_chat_message)
                        .setNegativeButton(R.string.overview_activity_delete_chat_cancel, { _, _ ->

                        })
                        .setPositiveButton(R.string.overview_activity_delete_chat_do_delete, { _, _ ->
                            deleteChat(shownChatList[currentContextSelectedIndex].chatInfo.chatUUID, kentaiClient.dataBase)
                            shownChatList.removeAt(currentContextSelectedIndex)
                            chatList.adapter.notifyItemRemoved(currentContextSelectedIndex)
                        })
                        .show()
            }
        }
        return super.onContextItemSelected(item)
    }

    override fun onResume() {
        super.onResume()
        updateList()
    }
}
