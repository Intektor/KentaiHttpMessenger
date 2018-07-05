package de.intektor.kentai

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.view.Menu
import de.intektor.kentai.fragment.ContactAdapter
import de.intektor.kentai.kentai.KEY_CHAT_INFO
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai.kentai.chat.createChat
import de.intektor.kentai.kentai.chat.getChatUUIDForUserChat
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.getSelectedTheme
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.util.toKey
import kotlinx.android.synthetic.main.activity_new_chat.*
import java.util.*

class NewChatUserActivity : AppCompatActivity(), android.support.v7.widget.SearchView.OnQueryTextListener {

    private val contactList = mutableListOf<ContactAdapter.ContactWrapper>()
    private val shownContactsList = mutableListOf<ContactAdapter.ContactWrapper>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_new_chat)

        val kentaiClient = applicationContext as KentaiClient

        new_chat_list.layoutManager = LinearLayoutManager(this)

        val contactList = mutableListOf<ContactAdapter.ContactWrapper>()
        val cursor = kentaiClient.dataBase.rawQuery("SELECT username, alias, user_uuid, message_key FROM contacts;", null)

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

        new_chat_list.adapter = ContactAdapter(this.shownContactsList, { item: ContactAdapter.ContactWrapper, _: ContactAdapter.ViewHolder ->
            AlertDialog.Builder(this@NewChatUserActivity)
                    .setTitle(R.string.new_chat_start_chat_title)
                    .setMessage(R.string.new_chat_start_chat_text)
                    .setNegativeButton(R.string.new_chat_start_chat_cancel, { _, _ -> })
                    .setPositiveButton(R.string.new_chat_start_chat_proceed, { _, _ -> startNewChat(item.contact) })
                    .show()
        }, callbackClickCheckBox = { _, _ -> }, registerForContextMenu = { false })
        new_chat_list.adapter.notifyDataSetChanged()

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun startNewChat(contact: Contact) {
        val kentaiClient = applicationContext as KentaiClient

        val chatInfo = ChatInfo(getChatUUIDForUserChat(kentaiClient.userUUID, contact.userUUID), contact.name, ChatType.TWO_PEOPLE,
                listOf(ChatReceiver(kentaiClient.userUUID, kentaiClient.publicMessageKey, ChatReceiver.ReceiverType.USER),
                        ChatReceiver(contact.userUUID, contact.message_key, ChatReceiver.ReceiverType.USER)))
        createChat(chatInfo, kentaiClient.dataBase, kentaiClient.userUUID)
        val intent = Intent(this@NewChatUserActivity, ChatActivity::class.java)
        intent.putExtra(KEY_CHAT_INFO, chatInfo)
        startActivity(intent)
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
        val currentShownList = if (!newText.isEmpty()) contactList.filter { it.contact.name.contains(newText, true) || it.contact.alias.contains(newText, true) } else contactList
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
