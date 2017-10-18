package de.intektor.kentai

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v7.app.AppCompatActivity
import android.view.Menu
import android.view.MenuItem
import de.intektor.kentai.fragment.ContactViewAdapter
import de.intektor.kentai.fragment.ChatListViewAdapter
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.overview_activity.FragmentChatsOverview
import de.intektor.kentai.overview_activity.FragmentContactsOverview
import de.intektor.kentai_http_common.chat.MessageStatus
import kotlinx.android.synthetic.main.activity_overview.*
import kotlinx.android.synthetic.main.fragment_chat_list.*
import java.io.File
import java.util.*

class OverviewActivity : AppCompatActivity(), FragmentContactsOverview.ListElementClickListener {

    private var mSectionsPagerAdapter: SectionsPagerAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!File(filesDir.path + "/username.info").exists()) {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        setContentView(R.layout.activity_overview)

        setSupportActionBar(toolbar)

        mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)

        // Set up the ViewPager with the sections adapter.
        container.adapter = mSectionsPagerAdapter

        tabs.setupWithViewPager(container)
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_overview, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.new_chat_button_user -> {
                val i = Intent(this@OverviewActivity, NewChatUserActivity::class.java)
                startActivity(i)
            }
            R.id.new_chat_button_group -> {
                val i = Intent(this@OverviewActivity, NewChatGroupActivity::class.java)
                startActivity(i)
            }
            R.id.new_contact -> {
                val i = Intent(this@OverviewActivity, AddContactActivity::class.java)
                startActivity(i)
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun addChat(item: ChatListViewAdapter.ChatItem) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        fragment.addChat(item)
        fragment.shownChatList.sortBy { it.lastChatMessage.message.timeSent }
        fragment.list.adapter.notifyDataSetChanged()
    }

    fun getCurrentChats(): List<ChatListViewAdapter.ChatItem> {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        return fragment.shownChatList
    }

    fun updateLatestChatMessage(chatUUID: UUID, lastMessage: ChatMessageWrapper, unreadMessages: Int) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        val chatItem = fragment.chatMap[chatUUID]!!
        chatItem.lastChatMessage = lastMessage
        chatItem.unreadMessages = unreadMessages
        fragment.shownChatList.sortByDescending { it.lastChatMessage.message.timeSent }
        fragment.list.adapter.notifyDataSetChanged()
    }

    fun updateLatestChatMessageStatus(chatUUID: UUID, status: MessageStatus, messageUUID: UUID) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        val chatItem = fragment.chatMap[chatUUID]!!
        if (chatItem.lastChatMessage.message.id == messageUUID) {
            chatItem.lastChatMessage.status = status
            fragment.list.adapter.notifyDataSetChanged()
        }
    }

    fun updateChatName(chatUUID: UUID, name: String) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        val chatItem = fragment.chatMap[chatUUID]!!
        chatItem.chatInfo = chatItem.chatInfo.copy(chatName = name)
        fragment.list.adapter.notifyDataSetChanged()
    }

    inner class SectionsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getItem(position: Int): Fragment? {
            when (position) {
                0 -> return FragmentChatsOverview()
                1 -> return FragmentContactsOverview()
            }
            return null
        }

        override fun getCount(): Int = 2

        override fun getPageTitle(position: Int): CharSequence? {
            when (position) {
                0 -> return "CHATS"
                1 -> return "CONTACTS"
            }
            return null
        }
    }

    override fun click(item: Contact, view: ContactViewAdapter.ViewHolder) {
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
