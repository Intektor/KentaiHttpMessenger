package de.intektor.mercury.ui

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.common.hash.Hashing
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getRealImagePath
import de.intektor.mercury.android.getRealVideoPath
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.ChatUtil
import de.intektor.mercury.chat.PendingMessage
import de.intektor.mercury.chat.readChats
import de.intektor.mercury.chat.sendMessageToServer
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.io.download.IOService
import de.intektor.mercury.media.MediaType
import de.intektor.mercury.media.ThumbnailUtil
import de.intektor.mercury.task.getVideoDimension
import de.intektor.mercury.task.getVideoDuration
import de.intektor.mercury.task.saveMediaFileInAppStorage
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.overview_activity.fragment.ChatListAdapter
import de.intektor.mercury.util.ProfilePictureUtil
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

    private val totalList = mutableListOf<ChatListAdapter.ChatItem>()
    private val currentList = mutableListOf<ChatListAdapter.ChatItem>()

    private val selectedList = mutableListOf<ChatListAdapter.ChatItem>()

    private lateinit var allListAdapter: ChatListAdapter
    private lateinit var selectedAdapter: SelectedChatsAdapter

    private var sendItem: MenuItem? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_share_receive)

        val mercuryClient = applicationContext as MercuryClient

        activity_share_receive_rv_selectable.layoutManager = LinearLayoutManager(this)

        totalList += readChats(mercuryClient.dataBase, this).sortedByDescending {
            it.lastChatMessage?.chatMessageInfo?.message?.messageCore?.timeCreated ?: 0L
        }
        currentList += totalList

        allListAdapter = ChatListAdapter(currentList, { item ->
            item.selected = !item.selected
            if (item.selected) {
                selectedAdapter.notifyItemInserted(selectedList.size - 1)
            } else {
                selectedAdapter.notifyItemRemoved(selectedList.indexOf(item))
            }
            if (item.selected) selectedList += item else selectedList -= item

            sendItem?.isEnabled = selectedList.isNotEmpty()

            allListAdapter.notifyItemChanged(currentList.indexOf(item))
        })

        activity_share_receive_rv_selectable.adapter = allListAdapter

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

        activity_share_receive_rv_selected.adapter = selectedAdapter
        activity_share_receive_rv_selected.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_share_receive_activity, menu)
        val searchItem = menu.findItem(R.id.menuShareReceiveActivitySearch)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(this)

        sendItem = menu.findItem(R.id.menu_share_receive_send)

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_share_receive_send -> {
                send()
                return true
            }
        }

        return false
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        currentList.clear()
        currentList += totalList.filter { query in ChatUtil.getChatName(this, mercuryClient().dataBase, it.chatInfo.chatUUID) }
        allListAdapter.notifyDataSetChanged()
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        currentList.clear()
        currentList += totalList.filter { ChatUtil.getChatName(this, mercuryClient().dataBase, it.chatInfo.chatUUID).contains(newText, true) }
        allListAdapter.notifyDataSetChanged()
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun send() {
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
                        val data = MessageImage(ThumbnailUtil.createThumbnail(File(path), MediaType.MEDIA_TYPE_IMAGE),
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

                        val referenceFile = saveMediaFileInAppStorage(referenceUUID, uri, this, FileType.IMAGE)

                        val videoDuration = getVideoDuration(referenceFile, this)
                        val dimension = getVideoDimension(this, referenceFile)

                        val message = MessageVideo(videoDuration,
                                false,
                                dimension.width,
                                dimension.height,
                                ThumbnailUtil.createThumbnail(referenceFile, MediaType.MEDIA_TYPE_VIDEO),
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
            }.flatten()

            sendMessageToServer(this, mercuryClient().dataBase, pendingMessages)

            for (pendingMessage in pendingMessages) {
                val wrapper = pendingMessage.message
                val data = wrapper.messageData
                if (data is MessageReference) {
                    when (data) {
                        is MessageVideo -> {
                            IOService.ActionUploadReference.launch(this, data.reference, data.aesKey, data.initVector, MediaType.MEDIA_TYPE_VIDEO, pendingMessage.chatUUID, wrapper.messageCore.messageUUID)
                        }
                        is MessageImage -> {
                            IOService.ActionUploadReference.launch(this, data.reference, data.aesKey, data.initVector, MediaType.MEDIA_TYPE_IMAGE, pendingMessage.chatUUID, wrapper.messageCore.messageUUID)
                        }
                    }
                }
            }

            if (selectedList.size == 1) {
                ChatActivity.launch(this, selectedList.first().chatInfo)
            }
        }
        finish()
    }

    private class SelectedChatsAdapter(val selectedList: MutableList<ChatListAdapter.ChatItem>, val onClick: (ChatListAdapter.ChatItem) -> (Unit)) : androidx.recyclerview.widget.RecyclerView.Adapter<SelectedViewHolder>() {

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
                        .load(ProfilePictureUtil.getProfilePicture(userUUID, holder.itemView.context))
                        .memoryPolicy(MemoryPolicy.NO_CACHE)
                        .placeholder(R.drawable.baseline_account_circle_24)
                        .into(holder.image)
            }

            holder.label.text = ChatUtil.getChatName(mercuryClient, mercuryClient.dataBase, item.chatInfo.chatUUID)

            holder.itemView.setOnClickListener {
                onClick.invoke(item)
            }
        }
    }

    private class SelectedViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.item_chat_small_iv_pp)
        val label: TextView = view.findViewById(R.id.item_chat_small_tv_label)
    }
}
