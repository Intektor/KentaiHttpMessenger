package de.intektor.kentai

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v4.view.MenuItemCompat
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.SearchView
import android.view.Menu
import de.intektor.kentai.fragment.ContactAdapter
import de.intektor.kentai.kentai.KEY_CHAT_INFO
import de.intektor.kentai.kentai.KEY_USER_UUID
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.readContacts
import de.intektor.kentai.kentai.getSelectedTheme
import kotlinx.android.synthetic.main.activity_add_group_member.*

class AddGroupMemberActivity : AppCompatActivity(), android.support.v7.widget.SearchView.OnQueryTextListener {

    private val contactList = mutableListOf<ContactAdapter.ContactWrapper>()
    private val shownContactsList = mutableListOf<ContactAdapter.ContactWrapper>()

    private lateinit var chatInfo: ChatInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_add_group_member)

        val kentaiClient = applicationContext as KentaiClient

        chatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)

        contactList += readContacts(kentaiClient.dataBase).filter { a -> chatInfo.participants.filter { it.isActive }.none { it.receiverUUID == a.userUUID } }.map { ContactAdapter.ContactWrapper(it, false) }

        shownContactsList += contactList

        addGroupMemberContacts.adapter = ContactAdapter(shownContactsList, { contactWrapper, _ ->
            AlertDialog.Builder(this@AddGroupMemberActivity)
                    .setTitle(R.string.add_group_member_add_alert_title)
                    .setMessage(R.string.add_group_member_add_alert_message)
                    .setNegativeButton(R.string.add_group_member_add_alert_negative, { _, _ -> })
                    .setPositiveButton(R.string.add_group_member_add_alert_positive, { _, _ ->
                        val resultData = Intent()
                        resultData.putExtra(KEY_USER_UUID, contactWrapper.contact.userUUID)
                        setResult(Activity.RESULT_OK, resultData)
                        finish()
                    })
                    .create().show()
        }, false, { _, _ -> }, { _ -> false })

        addGroupMemberContacts.layoutManager = LinearLayoutManager(this)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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
        val currentShownList = if (!newText.isEmpty()) contactList.filter { it.contact.name.contains(newText, true) || it.contact.alias.contains(newText, true) } else contactList
        val adapter = addGroupMemberContacts.adapter
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
