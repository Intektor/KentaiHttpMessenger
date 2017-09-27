package de.intektor.kentai

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import de.intektor.kentai.fragment.ContactViewAdapter
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.overview_activity.FragmentContactsOverview
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

    private val contactList = mutableListOf<Contact>()
    private val selected = mutableListOf<Contact>()
    private lateinit var createItem: MenuItem

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_chat_group)

        new_chat_list.layoutManager = LinearLayoutManager(this)

        val contactList = mutableListOf<Contact>()
        val cursor = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT username, alias, user_uuid, message_key FROM contacts;", null)

        while (cursor.moveToNext()) {
            val username = cursor.getString(0)
            val alias = cursor.getString(1)
            val userUUID = cursor.getString(2).toUUID()
            val messageKey = cursor.getString(3).toKey()
            if (userUUID != KentaiClient.INSTANCE.userUUID) {
                contactList.add(Contact(username, alias, userUUID, messageKey))
            }
        }
        cursor.close()
        this.contactList.addAll(contactList)
        new_chat_list.adapter = ContactViewAdapter(contactList, object : FragmentContactsOverview.ListElementClickListener {
            override fun click(item: Contact, view: ContactViewAdapter.ViewHolder) {
                view.checkBox.isChecked = !view.checkBox.isChecked
                if (view.checkBox.isChecked) {
                    selected.add(item)
                    view.mView.setBackgroundColor(Color.GREEN)
                } else {
                    view.mView.setBackgroundColor(Color.WHITE)
                    selected.remove(item)
                }
                createItem.isEnabled = selected.isNotEmpty() && new_chat_group_group_name.text.isNotBlank() && new_chat_group_group_name.text.length >= 3
            }
        }, true)
        new_chat_list.adapter.notifyDataSetChanged()

        new_chat_group_group_name.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                createItem.isEnabled = selected.isNotEmpty() && new_chat_group_group_name.text.isNotBlank() && new_chat_group_group_name.text.length >= 3
            }
        })
    }

    private fun startNewChat() {
        val chatInfo = ChatInfo(UUID.randomUUID(), new_chat_group_group_name.text.toString(), ChatType.GROUP, selected.map {
            ChatReceiver(it.userUUID, it.message_key, ChatReceiver.ReceiverType.USER)
        }.plus(ChatReceiver(KentaiClient.INSTANCE.userUUID, null, ChatReceiver.ReceiverType.USER)))

        val roleMap = HashMap<UUID, GroupRole>()
        roleMap.put(KentaiClient.INSTANCE.userUUID, GroupRole.ADMIN)
        for (contact in selected) {
            roleMap.put(contact.userUUID, GroupRole.DEFAULT)
        }

        val groupKey = generateAESKey() as SecretKey

        createGroupChat(chatInfo, roleMap, groupKey, KentaiClient.INSTANCE.dataBase, KentaiClient.INSTANCE.userUUID)

        val message = ChatMessageWrapper(ChatMessageGroupInvite(chatInfo.chatUUID, roleMap, new_chat_group_group_name.text.toString(), groupKey, KentaiClient.INSTANCE.userUUID, System.currentTimeMillis()), MessageStatus.WAITING, true, System.currentTimeMillis())

        val toSend = mutableListOf<PendingMessage>()

        for (contact in selected) {
            KentaiClient.INSTANCE.dataBase.rawQuery("SELECT chat_uuid FROM user_to_chat_uuid WHERE user_uuid = ?", arrayOf(contact.userUUID.toString())).use { query ->
                if (query.moveToNext()) {
                    val chatUUID = query.getString(0).toUUID()
                    toSend.add(PendingMessage(message, chatUUID, chatInfo.participants.filter { it.receiverUUID == contact.userUUID }))
                } else {
                    val newChatUUID = UUID.randomUUID()
                    createChat(ChatInfo(newChatUUID, contact.name, ChatType.TWO_PEOPLE, listOf(ChatReceiver(contact.userUUID, contact.message_key, ChatReceiver.ReceiverType.USER),
                            ChatReceiver(KentaiClient.INSTANCE.userUUID, null, ChatReceiver.ReceiverType.USER))), KentaiClient.INSTANCE.dataBase, KentaiClient.INSTANCE.userUUID)
                    toSend.add(PendingMessage(message, newChatUUID, chatInfo.participants.filter { it.receiverUUID == contact.userUUID }))
                }
            }
        }

        sendMessageToServer(this, toSend)

        val intent = Intent(this@NewChatGroupActivity, ChatActivity::class.java)
        intent.putExtra("chatInfo", chatInfo)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_new_chat_group, menu)
        val searchItem = menu.findItem(R.id.new_chat_group_action_search)
        val searchView = MenuItemCompat.getActionView(searchItem) as SearchView
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
                val i = Intent(this@NewChatGroupActivity, ChatActivity::class.java)
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onQueryTextSubmit(query: String?): Boolean = true

    override fun onQueryTextChange(newText: String): Boolean {
        val currentShownList = if (!newText.isEmpty()) contactList.filter { it.name.contains(newText, true) || it.alias.contains(newText, true) } else contactList
        val adapter = new_chat_list.adapter
        adapter as ContactViewAdapter
        adapter.mValues.clear()
        adapter.mValues.addAll(currentShownList)
        adapter.notifyDataSetChanged()
        return true
    }
}
