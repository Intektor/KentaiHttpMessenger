package de.intektor.kentai

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.ImageView
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import de.intektor.kentai.fragment.ContactViewAdapter
import de.intektor.kentai.kentai.KEY_CHAT_INFO
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.getProfilePicture
import de.intektor.kentai_http_common.chat.ChatMessageGroupInvite
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.GroupRole
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.util.generateAESKey
import de.intektor.kentai_http_common.util.toKey
import de.intektor.kentai_http_common.util.toUUID
import kotlinx.android.synthetic.main.activity_new_chat_group.*
import java.util.*
import javax.crypto.SecretKey
import kotlin.collections.HashMap

class NewChatGroupActivity : AppCompatActivity(), android.support.v7.widget.SearchView.OnQueryTextListener {

    private val contactList = mutableListOf<ContactViewAdapter.ContactWrapper>()
    private val shownContactList = mutableListOf<ContactViewAdapter.ContactWrapper>()
    private val selected = mutableListOf<ContactViewAdapter.ContactWrapper>()
    private lateinit var createItem: MenuItem
    private lateinit var selectedAdapter: SelectedContactsAdapter
    private lateinit var contactAdapter: ContactViewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_chat_group)

        val kentaiClient = applicationContext as KentaiClient

        contactListView.layoutManager = LinearLayoutManager(this)

        val contactList = mutableListOf<Contact>()
        val cursor = kentaiClient.dataBase.rawQuery("SELECT username, alias, user_uuid, message_key FROM contacts;", null)

        while (cursor.moveToNext()) {
            val username = cursor.getString(0)
            val alias = cursor.getString(1)
            val userUUID = cursor.getString(2).toUUID()
            val messageKey = cursor.getString(3).toKey()
            if (userUUID != kentaiClient.userUUID) {
                contactList.add(Contact(username, alias, userUUID, messageKey))
            }
        }
        cursor.close()
        this.contactList += contactList.map { ContactViewAdapter.ContactWrapper(it, false) }

        this.shownContactList += this.contactList

        contactAdapter = ContactViewAdapter(this.shownContactList, { _, _ -> }, true) { contact, view ->
            if (view.checkBox.isChecked) {
                addContact(contact)
            } else {
                removeContact(contact)
            }
            createItem.isEnabled = selected.isNotEmpty() && groupName.text.isNotBlank() && groupName.text.length >= 3
        }
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

        selectedAdapter = SelectedContactsAdapter(selected, { contact ->
            removeContact(contact)
        })
        selectedContacts.adapter = selectedAdapter

        selectedContacts.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun addContact(contact: ContactViewAdapter.ContactWrapper) {
        selected += contact
        selectedAdapter.notifyItemInserted(selected.size - 1)
        contact.checked = true
    }

    private fun removeContact(contact: ContactViewAdapter.ContactWrapper) {
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
        val kentaiClient = applicationContext as KentaiClient

        val chatInfo = ChatInfo(UUID.randomUUID(), groupName.text.toString(), ChatType.GROUP, selected.map {
            ChatReceiver(it.contact.userUUID, it.contact.message_key, ChatReceiver.ReceiverType.USER)
        }.plus(ChatReceiver(kentaiClient.userUUID, null, ChatReceiver.ReceiverType.USER)))

        val roleMap = HashMap<UUID, GroupRole>()
        roleMap[kentaiClient.userUUID] = GroupRole.ADMIN
        for (wrapper in selected) {
            roleMap[wrapper.contact.userUUID] = GroupRole.DEFAULT
        }

        val groupKey = generateAESKey() as SecretKey

        createGroupChat(chatInfo, roleMap, groupKey, kentaiClient.dataBase, kentaiClient.userUUID)

        val message = ChatMessageWrapper(ChatMessageGroupInvite(chatInfo.chatUUID, roleMap, groupName.text.toString(), groupKey, kentaiClient.userUUID, System.currentTimeMillis()), MessageStatus.WAITING, true, System.currentTimeMillis())

        val toSend = mutableListOf<PendingMessage>()

        for (wrapper in selected) {
            kentaiClient.dataBase.rawQuery("SELECT chat_uuid FROM user_to_chat_uuid WHERE user_uuid = ?", arrayOf(wrapper.contact.userUUID.toString())).use { query ->
                if (query.moveToNext()) {
                    val chatUUID = query.getString(0).toUUID()
                    toSend.add(PendingMessage(message, chatUUID, chatInfo.participants.filter { it.receiverUUID == wrapper.contact.userUUID }))
                } else {
                    val newChatUUID = UUID.randomUUID()
                    createChat(ChatInfo(newChatUUID, wrapper.contact.name, ChatType.TWO_PEOPLE, listOf(ChatReceiver(wrapper.contact.userUUID, wrapper.contact.message_key, ChatReceiver.ReceiverType.USER),
                            ChatReceiver(kentaiClient.userUUID, null, ChatReceiver.ReceiverType.USER))), kentaiClient.dataBase, kentaiClient.userUUID)
                    toSend.add(PendingMessage(message, newChatUUID, chatInfo.participants.filter { it.receiverUUID == wrapper.contact.userUUID }))
                }
            }
        }

        sendMessageToServer(this, toSend, kentaiClient.dataBase)

        val intent = Intent(this@NewChatGroupActivity, ChatActivity::class.java)
        intent.putExtra(KEY_CHAT_INFO, chatInfo)
        startActivity(intent)

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
        val currentShownList = if (!newText.isEmpty()) contactList.filter { it.contact.name.contains(newText, true) || it.contact.alias.contains(newText, true) } else contactList
        val adapter = contactListView.adapter
        adapter as ContactViewAdapter
        shownContactList.clear()
        shownContactList += currentShownList
        adapter.notifyDataSetChanged()
        return true
    }

    private class SelectedContactsAdapter(val selectedList: List<ContactViewAdapter.ContactWrapper>, val onClick: (ContactViewAdapter.ContactWrapper) -> Unit) : RecyclerView.Adapter<SelectedViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SelectedViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.chat_item_small, parent, false)
            return SelectedViewHolder(view)
        }

        override fun getItemCount(): Int = selectedList.size

        override fun onBindViewHolder(holder: SelectedViewHolder, position: Int) {
            val item = selectedList[position]

            val userUUID = item.contact.userUUID
            Picasso.with(holder.itemView.context)
                    .load(getProfilePicture(userUUID, holder.itemView.context))
                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                    .placeholder(R.drawable.ic_account_circle_white_24dp)
                    .into(holder.image)

            holder.itemView.setOnClickListener {
                onClick.invoke(item)
            }
        }
    }

    private class SelectedViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val image: ImageView = view.findViewById(R.id.chatItemSmallProfilePicture)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
