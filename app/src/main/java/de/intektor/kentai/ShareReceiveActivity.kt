package de.intektor.kentai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.common.hash.Hashing
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import de.intektor.kentai.fragment.ChatListViewAdapter
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.PendingMessage
import de.intektor.kentai.kentai.chat.readChats
import de.intektor.kentai.kentai.chat.sendMessageToServer
import de.intektor.kentai.kentai.references.*
import de.intektor.kentai_http_common.chat.*
import de.intektor.kentai_http_common.reference.FileType
import kotlinx.android.synthetic.main.activity_share_receive.*
import java.io.File
import java.util.*

class ShareReceiveActivity : AppCompatActivity(), SearchView.OnQueryTextListener {

    private val totalList = mutableListOf<ChatListViewAdapter.ChatItem>()
    private val currentList = mutableListOf<ChatListViewAdapter.ChatItem>()

    private val selectedList = mutableListOf<ChatListViewAdapter.ChatItem>()

    private lateinit var allListAdapter: ChatListViewAdapter
    private lateinit var selectedAdapter: SelectedChatsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_share_receive)

        val kentaiClient = applicationContext as KentaiClient

        shareReceiveList.layoutManager = LinearLayoutManager(this)

        totalList += readChats(kentaiClient.dataBase, this).sortedByDescending { it.lastChatMessage.message.timeSent }
        currentList += totalList

        allListAdapter = ChatListViewAdapter(currentList, { item ->
            item.selected = !item.selected
            if (item.selected) {
                selectedAdapter.notifyItemInserted(selectedList.size - 1)
            } else {
                selectedAdapter.notifyItemRemoved(selectedList.indexOf(item))
            }
            if (item.selected) selectedList += item else selectedList -= item

            allListAdapter.notifyItemChanged(currentList.indexOf(item))
        })

        shareReceiveList.adapter = allListAdapter

        selectedAdapter = SelectedChatsAdapter(selectedList, { item ->
            val selectedIndex = selectedList.indexOf(item)
            selectedList -= item
            selectedAdapter.notifyItemRemoved(selectedIndex)
            item.selected = false

            val currentIndex = currentList.indexOf(item)
            if (currentIndex != -1) {
                allListAdapter.notifyItemChanged(currentIndex)
            }
        })

        shareReceiveSelected.adapter = selectedAdapter
        shareReceiveSelected.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        shareReceiveSendButton.setOnClickListener {
            send(kentaiClient)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_share_receive_activity, menu)
        val searchItem = menu.findItem(R.id.menuShareReceiveActivitySearch)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(this)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        currentList.clear()
        currentList += totalList.filter { query in it.chatInfo.chatName }
        allListAdapter.notifyDataSetChanged()
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        currentList.clear()
        currentList += totalList.filter { it.chatInfo.chatName.contains(newText, true) }
        allListAdapter.notifyDataSetChanged()
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun send(kentaiClient: KentaiClient) {
        val action = intent.action
        val type = intent.type

        if (type != null) {
            val messageList: List<ChatMessageWrapper> = when {
                type == "text/plain" -> {
                    when (action) {
                        Intent.ACTION_SEND -> {
                            val message = ChatMessageText(intent.getStringExtra(Intent.EXTRA_TEXT), kentaiClient.userUUID, System.currentTimeMillis())
                            val wrapper = ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis(), UUID.randomUUID())
                            listOf(wrapper)
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            val tempList = mutableListOf<ChatMessageWrapper>()
                            for (string in intent.getStringArrayExtra(Intent.EXTRA_TEXT)) {
                                val message = ChatMessageText(intent.getStringExtra(Intent.EXTRA_TEXT), kentaiClient.userUUID, System.currentTimeMillis())
                                val wrapper = ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis(), UUID.randomUUID())
                                tempList += wrapper
                            }
                            tempList
                        }
                        else -> throw IllegalArgumentException()
                    }
                }
                type.startsWith("image/") -> {
                    when (action) {
                        Intent.ACTION_SEND -> {
                            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            val path = if (File(uri.path).exists()) {
                                uri.path
                            } else {
                                getRealImagePath(uri, this)
                            }
                            val hash = Hashing.sha512().hashBytes(File(path).readBytes())

                            val message = ChatMessageImage(hash.toString(), kentaiClient.userUUID, "", System.currentTimeMillis(), createSmallPreviewImage(File(path), FileType.IMAGE))
                            val wrapper = ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis(), UUID.randomUUID())
                            listOf(wrapper)
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            val tempList = mutableListOf<ChatMessageWrapper>()
                            for (uri in intent.getParcelableArrayExtra(Intent.EXTRA_STREAM)) {
                                uri as Uri
                                val path = if (File(uri.path).exists()) {
                                    uri.path
                                } else {
                                    getRealImagePath(uri, this)
                                }
                                val hash = Hashing.sha512().hashBytes(File(path).readBytes())

                                val smallPreview = createSmallPreviewImage(File(path), FileType.IMAGE)

                                val message = ChatMessageImage(hash.toString(), kentaiClient.userUUID, "", System.currentTimeMillis(), smallPreview)
                                val wrapper = ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis(), UUID.randomUUID())
                                tempList += wrapper

                            }
                            tempList
                        }
                        else -> throw IllegalArgumentException()
                    }
                }
                type.startsWith("video/") -> {
                    when (action) {
                        Intent.ACTION_SEND -> {
                            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            val path = if (File(uri.path).exists()) {
                                uri.path
                            } else {
                                getRealVideoPath(uri, this)
                            }
                            val hash = Hashing.sha512().hashBytes(File(path).readBytes())

                            val referenceUUID = UUID.randomUUID()

                            val referenceFile = saveMediaFileInAppStorage(referenceUUID, uri, kentaiClient, FileType.IMAGE)

                            val videoDuration = getVideoDuration(referenceFile, kentaiClient)

                            val message = ChatMessageVideo(hash.toString(), videoDuration, kentaiClient.userUUID, "", System.currentTimeMillis(), false)
                            message.referenceUUID = referenceUUID

                            val wrapper = ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis(), UUID.randomUUID())
                            listOf(wrapper)
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            val tempList = mutableListOf<ChatMessageWrapper>()
                            for (uri in intent.getParcelableArrayExtra(Intent.EXTRA_STREAM)) {
                                uri as Uri
                                val path = if (File(uri.path).exists()) {
                                    uri.path
                                } else {
                                    getRealVideoPath(uri, this)
                                }
                                val hash = Hashing.sha512().hashBytes(File(path).readBytes())

                                val referenceUUID = UUID.randomUUID()

                                val referenceFile = saveMediaFileInAppStorage(referenceUUID, uri, kentaiClient, FileType.IMAGE)

                                val videoDuration = getVideoDuration(referenceFile, kentaiClient)

                                val message = ChatMessageVideo(hash.toString(), videoDuration, kentaiClient.userUUID, "", System.currentTimeMillis(), false)
                                message.referenceUUID = referenceUUID

                                val wrapper = ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis(), UUID.randomUUID())
                                tempList += wrapper
                            }
                            tempList
                        }
                        else -> throw IllegalArgumentException()
                    }
                }
                else -> throw IllegalArgumentException()
            }

            val pendingMessages = messageList.map { wrapper ->
                selectedList.map {
                    val message = wrapper.message.copy()
                    message.id = UUID.randomUUID().toString()

                    val act = wrapper.copy(message = message, chatUUID = it.chatInfo.chatUUID)
                    PendingMessage(act, it.chatInfo.chatUUID, it.chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID })
                }
            }.flatMap { it }
            sendMessageToServer(this, pendingMessages, kentaiClient.dataBase)

            for (pendingMessage in pendingMessages) {
                val wrapper = pendingMessage.message
                if (wrapper.message.hasReference()) {
                    when (wrapper.message) {
                        is ChatMessageVideo -> {
                            val referenceFile = getReferenceFile(wrapper.message.referenceUUID, FileType.VIDEO, filesDir, kentaiClient)
                            uploadVideo(kentaiClient, kentaiClient.dataBase, pendingMessage.chatUUID, wrapper.message.referenceUUID, referenceFile)
                        }
                        is ChatMessageImage -> {
                            val referenceFile = getReferenceFile(wrapper.message.referenceUUID, FileType.IMAGE, filesDir, kentaiClient)
                            uploadImage(kentaiClient, kentaiClient.dataBase, pendingMessage.chatUUID, wrapper.message.referenceUUID, referenceFile)
                        }
                    }
                }
            }

            if (selectedList.size == 1) {
                val i = Intent(this@ShareReceiveActivity, ChatActivity::class.java)
                i.putExtra(KEY_CHAT_INFO, selectedList.first().chatInfo)
                startActivity(i)
            }
        }
        finish()
    }

    private class SelectedChatsAdapter(val selectedList: MutableList<ChatListViewAdapter.ChatItem>, val onClick: (ChatListViewAdapter.ChatItem) -> (Unit)) : RecyclerView.Adapter<SelectedViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_item_small, parent, false)
            return SelectedViewHolder(view)
        }

        override fun getItemCount(): Int = selectedList.size

        override fun onBindViewHolder(holder: SelectedViewHolder, position: Int) {
            val item = selectedList[position]

            val kentaiClient = holder.itemView.context.applicationContext as KentaiClient

            if (item.chatInfo.chatType == ChatType.TWO_PEOPLE) {
                val userUUID = item.chatInfo.participants.first { it.receiverUUID != kentaiClient.userUUID }.receiverUUID
                Picasso.with(holder.itemView.context)
                        .load(getProfilePicture(userUUID, holder.itemView.context))
                        .memoryPolicy(MemoryPolicy.NO_CACHE)
                        .placeholder(R.drawable.ic_account_circle_white_24dp)
                        .into(holder.image)
            }

            holder.itemView.setOnClickListener {
                onClick.invoke(item)
            }
        }
    }

    private class SelectedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.chatItemSmallProfilePicture)
    }
}
