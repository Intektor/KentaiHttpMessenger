package de.intektor.mercury.ui.overview_activity

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import androidx.appcompat.app.AlertDialog
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import de.intektor.mercury.R
import de.intektor.mercury.action.ActionMessageStatusChange
import de.intektor.mercury.action.chat.ActionChatMessageReceived
import de.intektor.mercury.action.group.ActionGroupModificationReceived
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.*
import de.intektor.mercury.io.ChatMessageService
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.overview_activity.fragment.ChatListViewAdapter
import de.intektor.mercury.ui.overview_activity.fragment.ChatListViewAdapter.ChatItem
import de.intektor.mercury_common.chat.data.group_modification.GroupModificationChangeName
import kotlinx.android.synthetic.main.fragment_chat_list.*
import java.util.*
import kotlin.collections.HashMap


class FragmentChatsOverview : androidx.fragment.app.Fragment() {

    val shownChatList: MutableList<ChatListViewAdapter.ChatItem> = mutableListOf()
    val chatMap: HashMap<UUID, ChatItem> = HashMap()

    private lateinit var chatsAdapter: ChatListViewAdapter

    private val messageStatusChangeListener = MessageStatusChangeListener()
    private val chatMessageListener = ChatMessageListener()

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

        shownChatList.sortByDescending {
            it.lastChatMessage?.chatMessageInfo?.message?.messageCore?.timeCreated ?: 0
        }

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
                            deleteChat(context!!, shownChatList[currentContextSelectedIndex].chatInfo.chatUUID, mercuryClient.dataBase)
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

        LocalBroadcastManager.getInstance(requireContext()).apply {
            registerReceiver(messageStatusChangeListener, ActionMessageStatusChange.getFilter())
            registerReceiver(chatMessageListener, ActionChatMessageReceived.getFilter())
        }
    }

    override fun onPause() {
        super.onPause()

        LocalBroadcastManager.getInstance(requireContext()).apply {
            unregisterReceiver(chatMessageListener)
        }
    }

    private inner class MessageStatusChangeListener : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (chatUuid, messageUuid, messageStatus) = ActionMessageStatusChange.getData(intent)

            val chatItem = shownChatList.firstOrNull { it.chatInfo.chatUUID == chatUuid } ?: return
            val lastChatMessage = chatItem.lastChatMessage
            if (lastChatMessage?.message?.messageCore?.messageUUID == messageUuid) {
                lastChatMessage.latestStatus = messageStatus

                chatsAdapter.notifyItemChanged(shownChatList.indexOfFirst { it.chatInfo.chatUUID == chatUuid })
            }
        }
    }

    private inner class ChatMessageListener : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val mercuryClient = context.mercuryClient()
            val (_, chatUuid) = ActionChatMessageReceived.getData(intent)

            val chatInfo = getChatInfo(chatUuid, mercuryClient.dataBase) ?: return

            val latestMessage = getChatMessages(context, mercuryClient.dataBase, "chat_uuid = ?", arrayOf(chatUuid.toString()), "time DESC", "1")
            val unreadMessages = ChatUtil.getUnreadMessagesFromChat(mercuryClient.dataBase, chatUuid)

            if (shownChatList.none { it.chatInfo.chatUUID == chatUuid }) {
                addChat(ChatListViewAdapter.ChatItem(chatInfo, latestMessage.firstOrNull(), unreadMessages, ChatUtil.isChatInitialized(mercuryClient.dataBase, chatUuid)))
            } else {
                val presentItemIndex = shownChatList.indexOfFirst { it.chatInfo.chatUUID == chatUuid }
                val presentItem = shownChatList[presentItemIndex]

                presentItem.lastChatMessage = latestMessage.firstOrNull()
                presentItem.unreadMessages = unreadMessages

                chatsAdapter.notifyItemChanged(presentItemIndex)
                shownChatList.removeAt(presentItemIndex)
                shownChatList.add(0, presentItem)

                chatsAdapter.notifyItemMoved(presentItemIndex, 0)
            }
        }
    }

    private inner class GroupModificationListener : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (chatUuid, modification) = ActionGroupModificationReceived.getData(intent)

            val chatItem = shownChatList.firstOrNull { it.chatInfo.chatUUID == chatUuid } ?: return
            if (modification is GroupModificationChangeName) {
                chatItem.chatInfo = chatItem.chatInfo.copy(chatName = modification.newName)

                chatsAdapter.notifyItemChanged(shownChatList.indexOfFirst { it.chatInfo.chatUUID == chatUuid })
            }
        }
    }
}
