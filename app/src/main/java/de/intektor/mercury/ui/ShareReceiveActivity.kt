package de.intektor.mercury.ui

import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.*
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.io.download.IOService
import de.intektor.mercury.media.MediaSendUtil
import de.intektor.mercury.media.MediaType
import de.intektor.mercury.media.SaveReferenceTask
import de.intektor.mercury.ui.overview_activity.fragment.ChatListAdapter
import de.intektor.mercury.util.Logger
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury.util.getCompatDrawable
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.ChatType
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageImage
import de.intektor.mercury_common.chat.data.MessageReference
import de.intektor.mercury_common.chat.data.MessageText
import de.intektor.mercury_common.chat.data.MessageVideo
import de.intektor.mercury_common.users.ProfilePictureType
import kotlinx.android.synthetic.main.activity_share_receive.*
import java.util.*

class ShareReceiveActivity : AppCompatActivity(), SearchView.OnQueryTextListener {

    companion object {
        private const val TAG = "ShareReceiveActivity"
    }

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
                selectedList += item
                selectedAdapter.notifyItemInserted(selectedList.size - 1)
            } else {
                val index = selectedList.indexOf(item)
                selectedAdapter.notifyItemRemoved(index)

                selectedList.removeAt(index)
            }

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

        sendItem?.isEnabled = false

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

        if (action == null || type == null) {
            Logger.warning(TAG, "Tried sending messages but null check failed: action=$action, type=$type")

            Toast.makeText(this, "Tried sending messages but something failed. This error has been reported.", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, R.string.activity_share_receive_sending_info, Toast.LENGTH_SHORT).show()

        val clientUUID = ClientPreferences.getClientUUID(this)

        CreateMessagesAndSend(mercuryClient(), clientUUID, selectedList.map { it.chatInfo }, intent, action, type) {
            finish()
        }.execute()
    }

    /**
     * Creates messages depending on the given action and type and then sends them to the server
     * @param selectedChats the chats that the created messages should be sent to.
     * @param intent the intent that contains information about the data
     * @param action of the given intent
     * @param type of the given intent
     */
    private class CreateMessagesAndSend(val mercuryClient: MercuryClient,
                                        val clientUUID: UUID,
                                        val selectedChats: List<ChatInfo>,
                                        val intent: Intent,
                                        val action: String,
                                        val type: String,
                                        val onFinishedCallback: () -> Unit) : AsyncTask<Unit, Unit, Unit>() {
        override fun doInBackground(vararg params: Unit?) {
            //Create the messages we want to send
            val messages = when {
                type == "text/plain" -> createPlainMessages()
                type.startsWith("image/") -> createImageMessages()
                type.startsWith("video/") -> createVideoMessages()
                else -> throw IllegalArgumentException("Tried sending a message with unsupported type=$type")
            }

            //Map these messages to pending messages
            val pendingMessages = messages.map { messageInfo ->
                val chatInfo = selectedChats.first { it.chatUUID == messageInfo.chatUUID }
                PendingMessage(messageInfo.message, messageInfo.chatUUID, chatInfo.getOthers(clientUUID))
            }

            //Send them to the server
            sendMessageToServer(mercuryClient, mercuryClient.dataBase, pendingMessages)

            //Finally start uploading video and image messages to the server
            for (messageInfo in messages) {
                val data = messageInfo.message.messageData

                if (data is MessageReference) {
                    val mediaType: Int = when (data) {
                        is MessageVideo -> MediaType.MEDIA_TYPE_VIDEO
                        is MessageImage -> MediaType.MEDIA_TYPE_IMAGE
                        else -> MediaType.MEDIA_TYPE_NONE
                    }

                    if (mediaType != MediaType.MEDIA_TYPE_NONE) {
                        IOService.ActionUploadReference.launch(mercuryClient, data.reference, data.aesKey, data.initVector, mediaType)
                    }
                }
            }
        }

        private fun createPlainMessages(): List<ChatMessageInfo> {
            return when (action) {
                Intent.ACTION_SEND -> {
                    val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                    createPlainMessages(text)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val texts = intent.getStringArrayExtra(Intent.EXTRA_TEXT)

                    val text = texts.joinToString(separator = "\n") { it }
                    createPlainMessages(text)
                }
                else -> throw IllegalArgumentException("Can't have intent action=$action")
            }
        }

        private fun createPlainMessages(text: String): List<ChatMessageInfo> {
            val createdMessages = mutableListOf<ChatMessageInfo>()

            for (chat in selectedChats) {
                val core = MessageCore(clientUUID, System.currentTimeMillis(), UUID.randomUUID())
                val data = MessageText(text)

                createdMessages += ChatMessageInfo(ChatMessage(core, data), true, chat.chatUUID)
            }

            return createdMessages
        }

        private fun createImageMessages(): List<ChatMessageInfo> {
            return when (action) {
                Intent.ACTION_SEND -> {
                    val uri: Uri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    createImageMessages(uri)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val uris: Array<Uri> = intent.getParcelableArrayExtra(Intent.EXTRA_STREAM) as Array<Uri>

                    uris.map { createImageMessages(it) }.flatten()
                }
                else -> throw IllegalArgumentException("Can't have intent action=$action")
            }
        }

        private fun createImageMessages(uri: Uri): List<ChatMessageInfo> {
            val referenceUUID = UUID.randomUUID()

            SaveReferenceTask.saveReference(mercuryClient, mercuryClient.contentResolver.openInputStream(uri), referenceUUID)

            return MediaSendUtil.createImageMessages(mercuryClient, referenceUUID, selectedChats.map { it.chatUUID }, uri, "")
                    ?: emptyList()
        }

        private fun createVideoMessages(): List<ChatMessageInfo> {
            return when (action) {
                Intent.ACTION_SEND -> {
                    val uri: Uri = intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    createVideoMessages(uri)
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    val uris: Array<Uri> = intent.getParcelableArrayExtra(Intent.EXTRA_STREAM) as Array<Uri>

                    uris.map { createVideoMessages(it) }.flatten()
                }
                else -> throw IllegalArgumentException("Can't have intent action=$action")
            }
        }

        private fun createVideoMessages(uri: Uri): List<ChatMessageInfo> {
            val referenceUUID = UUID.randomUUID()

            SaveReferenceTask.saveReference(mercuryClient, mercuryClient.contentResolver.openInputStream(uri), referenceUUID)

            return MediaSendUtil.createVideoMessages(mercuryClient, selectedChats.map { it.chatUUID }, uri, "", false, referenceUUID)
                    ?: emptyList()
        }

        override fun onPostExecute(result: Unit?) {
            onFinishedCallback()
        }
    }

    private class SelectedChatsAdapter(val selectedList: MutableList<ChatListAdapter.ChatItem>, val onClick: (ChatListAdapter.ChatItem) -> (Unit)) : androidx.recyclerview.widget.RecyclerView.Adapter<SelectedViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_item_small, parent, false)
            return SelectedViewHolder(view)
        }

        override fun getItemCount(): Int = selectedList.size

        override fun onBindViewHolder(holder: SelectedViewHolder, position: Int) {
            val item = selectedList[position]

            val context = holder.itemView.context
            val mercuryClient = context.applicationContext as MercuryClient

            val client = ClientPreferences.getClientUUID(mercuryClient)

            if (item.chatInfo.chatType == ChatType.TWO_PEOPLE) {
                val userUUID = item.chatInfo.participants.first { it.receiverUUID != client }.receiverUUID

                ProfilePictureUtil.loadProfilePicture(userUUID, ProfilePictureType.SMALL, holder.image, context.resources.getCompatDrawable(R.drawable.baseline_account_circle_24, context.theme))
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
