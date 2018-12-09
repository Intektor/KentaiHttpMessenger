package de.intektor.mercury.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.PendingMessage
import de.intektor.mercury.chat.createGroupChat
import de.intektor.mercury.chat.getUserChat
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.chat.model.ChatReceiver
import de.intektor.mercury.chat.sendMessageToServer
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.overview_activity.fragment.ContactAdapter
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.ChatType
import de.intektor.mercury_common.chat.GroupRole
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageGroupInvite
import de.intektor.mercury_common.util.generateAESKey
import de.intektor.mercury_common.util.toKey
import de.intektor.mercury_common.util.toUUID
import kotlinx.android.synthetic.main.activity_new_chat_group.*
import java.util.*
import javax.crypto.SecretKey
import kotlin.collections.HashMap

class NewChatGroupActivity : AppCompatActivity(), androidx.appcompat.widget.SearchView.OnQueryTextListener {

    private val contactList = mutableListOf<ContactAdapter.ContactWrapper>()
    private val shownContactList = mutableListOf<ContactAdapter.ContactWrapper>()
    private val selected = mutableListOf<ContactAdapter.ContactWrapper>()
    private lateinit var createItem: MenuItem
    private lateinit var selectedAdapter: SelectedContactsAdapter
    private lateinit var contactAdapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_new_chat_group)

        val mercuryClient = applicationContext as MercuryClient

        contactListView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        val contactList = mutableListOf<Contact>()
        val cursor = mercuryClient.dataBase.rawQuery("SELECT username, alias, user_uuid, message_key FROM contacts;", null)

        val client = ClientPreferences.getClientUUID(this)

        while (cursor.moveToNext()) {
            val username = cursor.getString(0)
            val alias = cursor.getString(1)
            val userUUID = cursor.getString(2).toUUID()
            val messageKey = cursor.getString(3).toKey()
            if (userUUID != client) {
                contactList.add(Contact(username, alias, userUUID, messageKey))
            }
        }
        cursor.close()
        this.contactList += contactList.map { ContactAdapter.ContactWrapper(it, false) }

        this.shownContactList += this.contactList

        contactAdapter = ContactAdapter(this.shownContactList, { _, _ -> }, true, { contact, view ->
            if (view.checkBox.isChecked) {
                addContact(contact)
            } else {
                removeContact(contact)
            }
            createItem.isEnabled = selected.isNotEmpty() && groupName.text.isNotBlank() && groupName.text.length >= 3
        }, { false })
        contactListView.adapter = contactAdapter

        groupName.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                createItem.isEnabled = selected.isNotEmpty() && groupName.text.isNotBlank() && groupName.text.length >= 3
            }
        })

        selectedAdapter = SelectedContactsAdapter(selected) { contact ->
            removeContact(contact)
        }
        selectedContacts.adapter = selectedAdapter

        selectedContacts.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun addContact(contact: ContactAdapter.ContactWrapper) {
        selected += contact
        selectedAdapter.notifyItemInserted(selected.size - 1)
        contact.checked = true
    }

    private fun removeContact(contact: ContactAdapter.ContactWrapper) {
        val indexSelected = selected.indexOf(contact)

        contact.checked = false

        if (indexSelected != -1) {
            selected -= contact
            selectedAdapter.notifyItemRemoved(indexSelected)
        }
        val indexAll = contactList.indexOf(contact)
        contactAdapter.notifyItemChanged(indexAll)
    }

    private fun startNewChat() {
        val mercuryClient = applicationContext as MercuryClient

        val client = ClientPreferences.getClientUUID(this)

        //TODO:
        val chatInfo = ChatInfo(UUID.randomUUID(), groupName.text.toString(), ChatType.GROUP_DECENTRALIZED, selected.asSequence().map {
            ChatReceiver(it.contact.userUUID, it.contact.message_key, ChatReceiver.ReceiverType.USER)
        }.plus(ChatReceiver(client, null, ChatReceiver.ReceiverType.USER)).toList())

        val roleMap = HashMap<UUID, GroupRole>()
        roleMap[client] = GroupRole.ADMIN
        for (wrapper in selected) {
            roleMap[wrapper.contact.userUUID] = GroupRole.DEFAULT
        }

        val groupKey = generateAESKey() as SecretKey

        createGroupChat(chatInfo, roleMap, groupKey, mercuryClient.dataBase, client)

        //TODO:
        val data = MessageGroupInvite(MessageGroupInvite.GroupInviteDecentralizedChat(roleMap, chatInfo.chatUUID, groupName.text.toString(), groupKey))

        val toSend = mutableListOf<PendingMessage>()

        for (wrapper in selected) {
            val userChat = getUserChat(mercuryClient, mercuryClient.dataBase, wrapper.contact)

            val core = MessageCore(client, System.currentTimeMillis(), UUID.randomUUID())

            toSend.add(PendingMessage(ChatMessage(core, data), userChat.chatUUID, chatInfo.participants.filter { it.receiverUUID == wrapper.contact.userUUID }))
        }

        sendMessageToServer(this, mercuryClient.dataBase, toSend)

        ChatActivity.launch(this, chatInfo)

        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_new_chat_group, menu)
        val searchItem = menu.findItem(R.id.new_chat_group_action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(this)

        createItem = menu.findItem(R.id.new_chat_group_create_action)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.new_chat_group_create_action -> {
                AlertDialog.Builder(this@NewChatGroupActivity)
                        .setTitle(R.string.new_chat_start_chat_title)
                        .setMessage(R.string.new_chat_start_chat_text)
                        .setNegativeButton(R.string.new_chat_start_chat_cancel, { _, _ -> })
                        .setPositiveButton(R.string.new_chat_start_chat_proceed, { _, _ -> startNewChat() })
                        .show()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onQueryTextSubmit(query: String?): Boolean = true

    override fun onQueryTextChange(newText: String): Boolean {
        val currentShownList = if (!newText.isEmpty()) contactList.filter { it.contact.name.contains(newText, true) || it.contact.alias?.contains(newText, true) == true } else contactList
        val adapter = contactListView.adapter
        adapter as ContactAdapter
        shownContactList.clear()
        shownContactList += currentShownList
        adapter.notifyDataSetChanged()
        return true
    }

    private class SelectedContactsAdapter(val selectedList: List<ContactAdapter.ContactWrapper>, val onClick: (ContactAdapter.ContactWrapper) -> Unit) : androidx.recyclerview.widget.RecyclerView.Adapter<SelectedViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_item_small, parent, false)
            return SelectedViewHolder(view)
        }

        override fun getItemCount(): Int = selectedList.size

        override fun onBindViewHolder(holder: SelectedViewHolder, position: Int) {
            val item = selectedList[position]

            val userUUID = item.contact.userUUID
            Picasso.get()
                    .load(ProfilePictureUtil.getProfilePicture(userUUID, holder.itemView.context))
                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                    .placeholder(R.drawable.baseline_account_circle_24)
                    .into(holder.image)


            holder.itemView.setOnClickListener {
                onClick.invoke(item)
            }
        }
    }

    private class SelectedViewHolder(view: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.chatItemSmallProfilePicture)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
