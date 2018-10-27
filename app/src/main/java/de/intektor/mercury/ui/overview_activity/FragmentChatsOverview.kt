package de.intektor.mercury.ui.overview_activity

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.app.AlertDialog
import android.support.v7.widget.LinearLayoutManager
import android.view.*
import de.intektor.mercury.R
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.deleteChat
import de.intektor.mercury.chat.readChatParticipants
import de.intektor.mercury.chat.readChats
import de.intektor.mercury.io.ChatMessageService
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.overview_activity.fragment.ChatListViewAdapter
import de.intektor.mercury.ui.overview_activity.fragment.ChatListViewAdapter.ChatItem
import kotlinx.android.synthetic.main.fragment_chat_list.*
import java.util.*
import kotlin.collections.HashMap


class FragmentChatsOverview : Fragment() {

    val shownChatList: MutableList<ChatListViewAdapter.ChatItem> = mutableListOf()
    val chatMap: HashMap<UUID, ChatItem> = HashMap()

    private lateinit var chatsAdapter: ChatListViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_chat_list, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val context = requireContext()

        val mercuryClient = context.mercuryClient()

        chatsAdapter = ChatListViewAdapter(shownChatList, { item ->
            val missingUsers = readChatParticipants(mercuryClient.dataBase, item.chatInfo.chatUUID).filter { it.publicKey == null }

            if (missingUsers.isNotEmpty()) {
                ChatMessageService.ActionInitializeChat.launch(context, missingUsers.map { it.receiverUUID }, item.chatInfo.chatUUID)

                updateInitChat(item.chatInfo.chatUUID, true, false)
            } else {
                ChatActivity.launch(context, item.chatInfo)
            }
        }, this@FragmentChatsOverview)

        fragment_chat_list_rv_chats.layoutManager = LinearLayoutManager(context)
        fragment_chat_list_rv_chats.adapter = chatsAdapter

        super.onViewCreated(view, savedInstanceState)
    }


    fun addChat(chatItem: ChatItem) {
        shownChatList += chatItem
        chatMap[chatItem.chatInfo.chatUUID] = chatItem
    }

    fun updateList() {
        shownChatList.clear()

        val mercuryClient = requireContext().mercuryClient()

        readChats(mercuryClient.dataBase, requireContext()).forEach {
            addChat(it)
        }

        shownChatList.sortByDescending { it.lastChatMessage?.chatMessageInfo?.message?.messageCore?.timeCreated ?: 0 }

        chatsAdapter.notifyDataSetChanged()
    }

    fun updateInitChat(chatUUID: UUID, loading: Boolean, successful: Boolean) {
        val index = shownChatList.indexOfFirst { it.chatInfo.chatUUID == chatUUID }
        if (index != -1) {
            val item = shownChatList[index]
            item.loading = loading
            item.finishedInitialisation = successful
            chatsAdapter.notifyItemChanged(index)
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
        val mercuryClient = requireContext().mercuryClient()

        when (item.itemId) {
            R.id.menuChatItemDelete -> {
                AlertDialog.Builder(context!!)
                        .setTitle(R.string.overview_activity_delete_chat_title)
                        .setMessage(R.string.overview_activity_delete_chat_message)
                        .setNegativeButton(R.string.overview_activity_delete_chat_cancel, null)
                        .setPositiveButton(R.string.overview_activity_delete_chat_do_delete) { _, _ ->
                            deleteChat(shownChatList[currentContextSelectedIndex].chatInfo.chatUUID, mercuryClient.dataBase)
                            shownChatList.removeAt(currentContextSelectedIndex)
                            chatsAdapter.notifyItemRemoved(currentContextSelectedIndex)
                        }
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
