package de.intektor.kentai

import android.content.Intent
import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.DefaultItemAnimator
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.Menu
import de.intektor.kentai.fragment.ContactViewAdapter
import de.intektor.kentai.fragment.FragmentContactsOverview.ListElementClickListener
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.createChat
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai_http_common.chat.ChatType
import java.util.*
import kotlin.collections.ArrayList

class NewChatActivity : AppCompatActivity(), android.support.v7.widget.SearchView.OnQueryTextListener {

    lateinit var list: RecyclerView

    val contactList = mutableListOf<Contact>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_new_chat)

        list = findViewById(R.id.new_chat_list) as RecyclerView

        list.itemAnimator = DefaultItemAnimator()

        list.layoutManager = LinearLayoutManager(this)

        val contactList = mutableListOf<Contact>()
        val cursor = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT username, alias, user_uuid FROM contacts;", null)

        while (cursor.moveToNext()) {
            val username = cursor.getString(0)
            val alias = cursor.getString(1)
            val userUUID = UUID.fromString(cursor.getString(2))
            contactList.add(Contact(username, alias, userUUID))
        }
        cursor.close()
        this.contactList.addAll(contactList)
        list.adapter = ContactViewAdapter(contactList, object : ListElementClickListener {
            override fun click(item: Contact) {
//                val builder = AlertDialog.Builder(applicationContext)
//                builder.setTitle(R.string.new_chat_start_chat_title)
//                builder.setNegativeButton(R.string.new_chat_start_chat_cancel, { _, _ -> })
//                builder.setPositiveButton(R.string.new_chat_start_chat_proceed, { _, _ -> startNewChat(item) })
//                builder.show()
                startNewChat(item)
            }
        })
        list.adapter.notifyDataSetChanged()
    }

    fun startNewChat(contact: Contact) {
        val chatInfo = ChatInfo(UUID.randomUUID(), contact.name, ChatType.TWO_PEOPLE, listOf(KentaiClient.INSTANCE.userUUID.toString(), contact.userUUID.toString()))
        createChat(chatInfo, KentaiClient.INSTANCE.dataBase)
        val intent = Intent(KentaiClient.INSTANCE.currentActivity, ChatActivity::class.java)
        intent.putExtra("chatName", chatInfo.chatName)
        intent.putExtra("chatType", chatInfo.chatType.ordinal)
        intent.putExtra("chatUUID", chatInfo.chatUUID.toString())
        intent.putExtra("participants", ArrayList<String>(chatInfo.participants))
        KentaiClient.INSTANCE.currentActivity!!.startActivity(intent)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_new_chat, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = MenuItemCompat.getActionView(searchItem) as SearchView

        searchView.setOnQueryTextListener(this)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return true
    }

    override fun onQueryTextChange(newText: String): Boolean {
        val currentShownList = if (!newText.isEmpty()) contactList.filter { it.name.contains(newText, true) || it.alias.contains(newText, true) } else contactList
        val adapter = list.adapter
        adapter as ContactViewAdapter
        adapter.mValues.clear()
        adapter.mValues.addAll(currentShownList)
        adapter.notifyDataSetChanged()
        return true
    }
}
