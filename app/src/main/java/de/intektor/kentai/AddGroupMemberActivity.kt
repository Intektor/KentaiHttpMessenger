package de.intektor.kentai

import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.view.Menu
import de.intektor.kentai.fragment.ContactViewAdapter
import de.intektor.kentai.kentai.Kentai
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.groups.handleGroupModification
import de.intektor.kentai.overview_activity.FragmentContactsOverview
import de.intektor.kentai_http_common.chat.ChatMessageGroupInvite
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.GroupRole
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.chat.group_modification.ChatMessageGroupModification
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationAddUser
import de.intektor.kentai_http_common.util.toAESKey
import de.intektor.kentai_http_common.util.toKey
import de.intektor.kentai_http_common.util.toUUID
import kotlinx.android.synthetic.main.activity_add_group_member.*
import java.util.*
import javax.crypto.SecretKey

class AddGroupMemberActivity : AppCompatActivity(), android.support.v7.widget.SearchView.OnQueryTextListener {

    private val contactList = mutableListOf<Contact>()

    private lateinit var chatInfo: ChatInfo
    private lateinit var roleMap: HashMap<UUID, GroupRole>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_group_member)

        val kentaiClient = applicationContext as KentaiClient

        chatInfo = intent.getParcelableExtra("chatInfo")
        roleMap = intent.getSerializableExtra("roleMap") as HashMap<UUID, GroupRole>

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

        contactList.forEach { contact ->
            if (chatInfo.participants.none { contact.userUUID == it.receiverUUID } || chatInfo.participants.any { contact.userUUID == it.receiverUUID && !it.isActive }) {
                this.contactList.add(contact)
            }
        }

        addGroupMemberContactList.layoutManager = LinearLayoutManager(this@AddGroupMemberActivity)

        addGroupMemberContactList.adapter = ContactViewAdapter(this.contactList,
                object : FragmentContactsOverview.ListElementClickListener {
                    override fun click(item: Contact, view: ContactViewAdapter.ViewHolder) {
                        AlertDialog.Builder(this@AddGroupMemberActivity)
                                .setTitle(R.string.add_group_member_add_alert_title)
                                .setMessage(R.string.add_group_member_add_alert_message)
                                .setNegativeButton(R.string.add_group_member_add_alert_negative, { _, _ -> })
                                .setPositiveButton(R.string.add_group_member_add_alert_positive, { _, _ ->
                                    val modification = GroupModificationAddUser(item.userUUID, chatInfo.chatUUID.toString())
                                    handleGroupModification(modification, kentaiClient.dataBase)
                                    sendMessageToServer(this@AddGroupMemberActivity,
                                            PendingMessage(
                                                    ChatMessageWrapper(
                                                            ChatMessageGroupModification(
                                                                    modification, kentaiClient.userUUID, System.currentTimeMillis()),
                                                            MessageStatus.WAITING, true, System.currentTimeMillis()),
                                                    chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID }), kentaiClient.dataBase)

                                    var groupKey: SecretKey? = null

                                    kentaiClient.dataBase.rawQuery("SELECT group_key FROM group_key_table WHERE chat_uuid = ?", arrayOf(chatInfo.chatUUID.toString())).use { query ->
                                        cursor.moveToNext()
                                        groupKey = cursor.getString(0).toAESKey() as SecretKey
                                    }

                                    kentaiClient.dataBase.rawQuery("SELECT chat_uuid FROM user_to_chat_uuid WHERE user_uuid = ?", arrayOf(item.userUUID.toString())).use { query ->
                                        val chatUUID: UUID
                                        if (query.moveToNext()) {
                                            chatUUID = query.getString(0).toUUID()
                                        } else {
                                            chatUUID = UUID.randomUUID()
                                            createChat(ChatInfo(chatUUID, item.name, ChatType.TWO_PEOPLE, listOf(ChatReceiver(item.userUUID, item.message_key, ChatReceiver.ReceiverType.USER),
                                                    ChatReceiver(kentaiClient.userUUID, null, ChatReceiver.ReceiverType.USER))), kentaiClient.dataBase, kentaiClient.userUUID)
                                        }

                                        roleMap.put(item.userUUID, GroupRole.DEFAULT)

                                        sendMessageToServer(this@AddGroupMemberActivity, PendingMessage(
                                                ChatMessageWrapper(ChatMessageGroupInvite(chatInfo.chatUUID, roleMap, chatInfo.chatName, groupKey!!, kentaiClient.userUUID, System.currentTimeMillis()), MessageStatus.WAITING, true, System.currentTimeMillis())
                                                , chatUUID, listOf(ChatReceiver(item.userUUID, item.message_key, ChatReceiver.ReceiverType.USER))), kentaiClient.dataBase)
                                    }
                                    finish()
                                })
                                .create().show()
                    }
                }, false)
        addGroupMemberContactList.adapter.notifyDataSetChanged()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_add_group_member, menu)
        val searchItem = menu.findItem(R.id.addGroupMemberActionSearch)
        val searchView = MenuItemCompat.getActionView(searchItem) as SearchView
        searchView.setOnQueryTextListener(this)

        return super.onCreateOptionsMenu(menu)
    }

    override fun onQueryTextSubmit(query: String?): Boolean = true

    override fun onQueryTextChange(newText: String): Boolean {
        val currentShownList = if (!newText.isEmpty()) contactList.filter { it.name.contains(newText, true) || it.alias.contains(newText, true) } else contactList
        val adapter = addGroupMemberContactList.adapter
        adapter as ContactViewAdapter
        adapter.mValues.clear()
        adapter.mValues.addAll(currentShownList)
        adapter.notifyDataSetChanged()
        return true
    }
}
