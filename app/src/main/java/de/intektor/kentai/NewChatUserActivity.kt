package de.intektor.kentai

import android.content.Intent
import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.view.Menu
import de.intektor.kentai.fragment.ContactViewAdapter
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai.kentai.chat.createChat
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.overview_activity.FragmentContactsOverview.ListElementClickListener
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.util.toKey
import kotlinx.android.synthetic.main.activity_new_chat.*
import java.util.*

class NewChatUserActivity : AppCompatActivity(), android.support.v7.widget.SearchView.OnQueryTextListener {

    private val contactList = mutableListOf<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_chat)

        new_chat_list.itemAnimator = DefaultItemAnimator()

        new_chat_list.layoutManager = LinearLayoutManager(this)

        val contactList = mutableListOf<Contact>()
        val cursor = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT username, alias, user_uuid, message_key FROM contacts;", null)

        while (cursor.moveToNext()) {
            val username = cursor.getString(0)
            val alias = cursor.getString(1)
            val userUUID = UUID.fromString(cursor.getString(2))
            val messageKey = cursor.getString(3).toKey()
            contactList.add(Contact(username, alias, userUUID, messageKey))
        }
        cursor.close()
        this.contactList.addAll(contactList)
        new_chat_list.adapter = ContactViewAdapter(contactList, object : ListElementClickListener {
            override fun click(item: Contact, view: ContactViewAdapter.ViewHolder) {
                AlertDialog.Builder(this@NewChatUserActivity)
                        .setTitle(R.string.new_chat_start_chat_title)
                        .setMessage(R.string.new_chat_start_chat_text)
                        .setNegativeButton(R.string.new_chat_start_chat_cancel, { _, _ -> })
                        .setPositiveButton(R.string.new_chat_start_chat_proceed, { _, _ -> startNewChat(item) })
                        .show()
            }
        })
        new_chat_list.adapter.notifyDataSetChanged()
    }

    fun startNewChat(contact: Contact) {
        val chatInfo = ChatInfo(UUID.randomUUID(), contact.name, ChatType.TWO_PEOPLE,
                listOf(ChatReceiver(KentaiClient.INSTANCE.userUUID, KentaiClient.INSTANCE.publicMessageKey, ChatReceiver.ReceiverType.USER),
                        ChatReceiver(contact.userUUID, contact.message_key, ChatReceiver.ReceiverType.USER)))
        createChat(chatInfo, KentaiClient.INSTANCE.dataBase, KentaiClient.INSTANCE.userUUID)
        val intent = Intent(this@NewChatUserActivity, ChatActivity::class.java)
        intent.putExtra("chatInfo", chatInfo)
        startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_new_chat_user, menu)
        val searchItem = menu.findItem(R.id.new_chat_user_action_search)
        val searchView = MenuItemCompat.getActionView(searchItem) as SearchView

        searchView.setOnQueryTextListener(this)

        return super.onCreateOptionsMenu(menu)
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
