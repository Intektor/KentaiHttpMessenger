package de.intektor.mercury.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.core.view.MenuItemCompat
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import android.view.Menu
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.chat.readContacts
import de.intektor.mercury.ui.overview_activity.fragment.ContactAdapter
import de.intektor.mercury.util.KEY_CHAT_INFO
import de.intektor.mercury.util.KEY_USER_UUID
import kotlinx.android.synthetic.main.activity_add_group_member.*

class AddGroupMemberActivity : AppCompatActivity(), androidx.appcompat.widget.SearchView.OnQueryTextListener {

    private val contactList = mutableListOf<ContactAdapter.ContactWrapper>()
    private val shownContactsList = mutableListOf<ContactAdapter.ContactWrapper>()

    private lateinit var chatInfo: ChatInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_add_group_member)

        val mercuryClient = applicationContext as MercuryClient

        chatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)

        contactList += readContacts(mercuryClient.dataBase).filter { a -> chatInfo.participants.filter { it.isActive }.none { it.receiverUUID == a.userUUID } }.map { ContactAdapter.ContactWrapper(it, false) }

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

        addGroupMemberContacts.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

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
        val currentShownList = if (!newText.isEmpty()) contactList.filter { it.contact.name.contains(newText, true) || it.contact.alias?.contains(newText, true) == true } else contactList
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
