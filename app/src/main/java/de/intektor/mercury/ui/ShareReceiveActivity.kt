package de.intektor.mercury.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.appcompat.widget.SearchView
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.google.common.hash.Hashing
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getRealImagePath
import de.intektor.mercury.android.getRealVideoPath
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.PendingMessage
import de.intektor.mercury.chat.readChats
import de.intektor.mercury.chat.sendMessageToServer
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.io.download.IOService
import de.intektor.mercury.task.ThumbnailUtil
import de.intektor.mercury.task.getVideoDimension
import de.intektor.mercury.task.getVideoDuration
import de.intektor.mercury.task.saveMediaFileInAppStorage
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.overview_activity.fragment.ChatListViewAdapter
import de.intektor.mercury.util.KEY_CHAT_INFO
import de.intektor.mercury.util.getProfilePicture
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.ChatType
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageImage
import de.intektor.mercury_common.chat.data.MessageReference
import de.intektor.mercury_common.chat.data.MessageText
import de.intektor.mercury_common.chat.data.MessageVideo
import de.intektor.mercury_common.reference.FileType
import de.intektor.mercury_common.util.generateAESKey
import de.intektor.mercury_common.util.generateInitVector
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

        val mercuryClient = applicationContext as MercuryClient

        shareReceiveList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        totalList += readChats(mercuryClient.dataBase, this).sortedByDescending {
            it.lastChatMessage?.chatMessageInfo?.message?.messageCore?.timeCreated ?: 0L
        }
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

        selectedAdapter = SelectedChatsAdapter(selectedList) { item ->
            val selectedIndex = selectedList.indexOf(item)
            selectedList -= item
            selectedAdapter.notifyItemRemoved(selectedIndex)
            item.selected = false

            val currentIndex = currentList.indexOf(item)
            if (currentIndex != -1) {
                allListAdapter.notifyItemChanged(currentIndex)
            }
        }

        shareReceiveSelected.adapter = selectedAdapter
        shareReceiveSelected.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        shareReceiveSendButton.setOnClickListener {
            send(mercuryClient)
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

    private fun send(mercuryClient: MercuryClient) {
        val action = intent.action
        val type = intent.type

        val client = ClientPreferences.getClientUUID(this)

        if (type != null) {
            val messageList: List<ChatMessage> = when {
                type == "text/plain" -> {
                    when (action) {
                        Intent.ACTION_SEND -> {
                            val core = MessageCore(client, System.currentTimeMillis(), UUID.randomUUID())

                            val data = MessageText(intent.getStringExtra(Intent.EXTRA_TEXT))
                            listOf(ChatMessage(core, data))
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            val tempList = mutableListOf<ChatMessage>()
                            for (string in intent.getStringArrayExtra(Intent.EXTRA_TEXT)) {
                                val core = MessageCore(client, System.currentTimeMillis(), UUID.randomUUID())

                                val data = MessageText(intent.getStringExtra(Intent.EXTRA_TEXT))
                                tempList += ChatMessage(core, data)
                            }
                            tempList
                        }
                        else -> throw IllegalArgumentException()
                    }
                }
                type.startsWith("image/") -> {

                    val generateMessage = { uri: Uri ->
                        val path = if (File(uri.path).exists()) {
                            uri.path
                        } else {
                            getRealImagePath(uri, this)
                        }
                        val hash = Hashing.sha512().hashBytes(File(path).readBytes())

                        val bitmap = BitmapFactory.decodeStream(contentResolver.openInputStream(uri))

                        val core = MessageCore(client, System.currentTimeMillis(), UUID.randomUUID())
                        val data = MessageImage(ThumbnailUtil.createThumbnail(File(path), FileType.IMAGE),
                                "",
                                bitmap.width,
                                bitmap.height,
                                generateAESKey(),
                                generateInitVector(),
                                UUID.randomUUID(),
                                hash.toString())

                        ChatMessage(core, data)
                    }

                    when (action) {
                        Intent.ACTION_SEND -> {
                            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            listOf(generateMessage(uri))
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            val tempList = mutableListOf<ChatMessage>()
                            for (uri in intent.getParcelableArrayExtra(Intent.EXTRA_STREAM) as Array<Uri>) {
                                tempList += generateMessage(uri)
                            }
                            tempList
                        }
                        else -> throw IllegalArgumentException()
                    }
                }
                type.startsWith("video/") -> {
                    val generateMessage = { uri: Uri ->
                        val path = if (File(uri.path).exists()) {
                            uri.path
                        } else {
                            getRealVideoPath(uri, this)
                        }
                        val hash = Hashing.sha512().hashBytes(File(path).readBytes())

                        val referenceUUID = UUID.randomUUID()

                        val referenceFile = saveMediaFileInAppStorage(referenceUUID, uri, mercuryClient, FileType.IMAGE)

                        val videoDuration = getVideoDuration(referenceFile, mercuryClient)
                        val dimension = getVideoDimension(mercuryClient, referenceFile)

                        val message = MessageVideo(videoDuration,
                                false,
                                dimension.width,
                                dimension.height,
                                ThumbnailUtil.createThumbnail(referenceFile, FileType.VIDEO),
//                                createSmallPreviewImage(referenceFile, FileType.VIDEO),
                                "",
                                generateAESKey(),
                                generateInitVector(),
                                referenceUUID,
                                hash.toString())

                        val core = MessageCore(client, System.currentTimeMillis(), UUID.randomUUID())

                        ChatMessage(core, message)
                    }
                    when (action) {
                        Intent.ACTION_SEND -> {
                            val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                            listOf(generateMessage(uri))
                        }
                        Intent.ACTION_SEND_MULTIPLE -> {
                            (intent.getParcelableArrayExtra(Intent.EXTRA_STREAM) as Array<Uri>).map { generateMessage(it) }
                        }
                        else -> throw IllegalArgumentException()
                    }
                }
                else -> throw IllegalArgumentException()
            }

            val pendingMessages = messageList.map { message ->
                selectedList.map {
                    PendingMessage(message, it.chatInfo.chatUUID, it.chatInfo.getOthers(client))
                }
            }.flatMap { it }
            sendMessageToServer(this, mercuryClient.dataBase, pendingMessages)

            for (pendingMessage in pendingMessages) {
                val wrapper = pendingMessage.message
                val data = wrapper.messageData
                if (data is MessageReference) {
                    when (data) {
                        is MessageVideo -> {
                            IOService.ActionUploadReference.launch(this, data.reference, data.aesKey, data.initVector, FileType.VIDEO)
                        }
                        is MessageImage -> {
                            IOService.ActionUploadReference.launch(this, data.reference, data.aesKey, data.initVector, FileType.IMAGE)
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

    private class SelectedChatsAdapter(val selectedList: MutableList<ChatListViewAdapter.ChatItem>, val onClick: (ChatListViewAdapter.ChatItem) -> (Unit)) : androidx.recyclerview.widget.RecyclerView.Adapter<SelectedViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_item_small, parent, false)
            return SelectedViewHolder(view)
        }

        override fun getItemCount(): Int = selectedList.size

        override fun onBindViewHolder(holder: SelectedViewHolder, position: Int) {
            val item = selectedList[position]

            val mercuryClient = holder.itemView.context.applicationContext as MercuryClient

            val client = ClientPreferences.getClientUUID(mercuryClient)

            if (item.chatInfo.chatType == ChatType.TWO_PEOPLE) {
                val userUUID = item.chatInfo.participants.first { it.receiverUUID != client }.receiverUUID
                Picasso.get()
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

    private class SelectedViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.chatItemSmallProfilePicture)
    }
}
