package de.intektor.mercury.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import android.view.Menu
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.chat.model.ChatReceiver
import de.intektor.mercury.chat.createChat
import de.intektor.mercury.chat.getChatUUIDForUserChat
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.overview_activity.fragment.ContactAdapter
import de.intektor.mercury_common.chat.ChatType
import de.intektor.mercury_common.util.toKey
import kotlinx.android.synthetic.main.activity_new_chat.*
import java.util.*

class NewChatUserActivity : AppCompatActivity(), androidx.appcompat.widget.SearchView.OnQueryTextListener {

    private val contactList = mutableListOf<ContactAdapter.ContactWrapper>()
    private val shownContactsList = mutableListOf<ContactAdapter.ContactWrapper>()

    private lateinit var contactsAdapter: ContactAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_new_chat)

        val mercuryClient = applicationContext as MercuryClient

        new_chat_list.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        val contactList = mutableListOf<ContactAdapter.ContactWrapper>()
        val cursor = mercuryClient.dataBase.rawQuery("SELECT username, alias, user_uuid, message_key FROM contacts;", null)

        while (cursor.moveToNext()) {
            val username = cursor.getString(0)
            val alias = cursor.getString(1)
            val userUUID = UUID.fromString(cursor.getString(2))
            val messageKey = cursor.getString(3).toKey()
            contactList += ContactAdapter.ContactWrapper(Contact(username, alias, userUUID, messageKey), false)
        }
        cursor.close()
        this.contactList += contactList
        this.shownContactsList += contactList

        contactsAdapter = ContactAdapter(this.shownContactsList, { item: ContactAdapter.ContactWrapper, _: ContactAdapter.ViewHolder ->
            AlertDialog.Builder(this@NewChatUserActivity)
                    .setTitle(R.string.new_chat_start_chat_title)
                    .setMessage(R.string.new_chat_start_chat_text)
                    .setNegativeButton(R.string.new_chat_start_chat_cancel) { _, _ -> }
                    .setPositiveButton(R.string.new_chat_start_chat_proceed) { _, _ -> startNewChat(item.contact) }
                    .show()
        }, callbackClickCheckBox = { _, _ -> }, registerForContextMenu = { false })
        new_chat_list.adapter = contactsAdapter

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun startNewChat(contact: Contact) {
        val mercuryClient = applicationContext as MercuryClient

        val client = ClientPreferences.getClientUUID(this)

        val chatInfo = ChatInfo(getChatUUIDForUserChat(client, contact.userUUID), contact.name, ChatType.TWO_PEOPLE,
                listOf(ChatReceiver(client, ClientPreferences.getPublicMessageKey(this), ChatReceiver.ReceiverType.USER),
                        ChatReceiver(contact.userUUID, contact.message_key, ChatReceiver.ReceiverType.USER)))
        createChat(chatInfo, mercuryClient.dataBase, client)

        ChatActivity.launch(this, chatInfo)

        finish()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_new_chat_user, menu)
        val searchItem = menu.findItem(R.id.new_chat_user_action_search)
        val searchView = searchItem.actionView as SearchView

        searchView.setOnQueryTextListener(this)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onQueryTextSubmit(query: String?): Boolean = true

    override fun onQueryTextChange(newText: String): Boolean {
        val currentShownList = if (!newText.isEmpty()) contactList.filter { it.contact.name.contains(newText, true) || it.contact.alias?.contains(newText, true) == true} else contactList
        val adapter = new_chat_list.adapter
        adapter as ContactAdapter
        shownContactsList.clear()
        shownContactsList += currentShownList
        adapter.notifyDataSetChanged()
        return true
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
