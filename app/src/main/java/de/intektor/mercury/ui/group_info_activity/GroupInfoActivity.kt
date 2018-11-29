package de.intektor.mercury.ui.group_info_activity

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.action.group.ActionGroupModificationReceived
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.*
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.chat.model.ChatReceiver
import de.intektor.mercury.chat.model.GroupMember
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.task.*
import de.intektor.mercury.ui.AddGroupMemberActivity
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.overview_activity.fragment.ContactAdapter
import de.intektor.mercury.util.ACTION_GROUP_MODIFICATION_RECEIVED
import de.intektor.mercury.util.KEY_CHAT_INFO
import de.intektor.mercury.util.KEY_USER_UUID
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.ChatType
import de.intektor.mercury_common.chat.GroupRole
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageGroupInvite
import de.intektor.mercury_common.chat.data.group_modification.*
import kotlinx.android.synthetic.main.activity_group_info.*
import java.util.*
import kotlin.collections.HashMap


class GroupInfoActivity : AppCompatActivity() {

    private lateinit var chatInfo: ChatInfo
    private val roleMap: HashMap<UUID, GroupRole> = HashMap()

    private var clientGroupRole: GroupRole? = null

    private val memberList = mutableListOf<GroupMember>()
    private val memberContactList = mutableListOf<ContactAdapter.ContactWrapper>()

    private lateinit var groupMemberAdapter: ContactAdapter

    private lateinit var groupModificationListener: BroadcastReceiver

    private companion object {
        private const val ACTION_SELECT_CONTACT = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_group_info)

        val mercuryClient = applicationContext as MercuryClient

        chatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)

        val pending = getPendingModifications(chatInfo.chatUUID, mercuryClient.dataBase)

        val changeName: GroupModificationChangeName? = pending.firstOrNull { it is GroupModificationChangeName } as? GroupModificationChangeName

        setGroupNameToUI(changeName?.newName ?: chatInfo.chatName, changeName != null)

        loadRoles()

        val clientRole = ClientPreferences.getClientUUID(this)

        clientGroupRole = roleMap[clientRole]
        groupInfoEditGroup.isEnabled = clientGroupRole != null && clientGroupRole != GroupRole.DEFAULT

        val groupedRoles = memberList.groupBy { it.role }

        memberList.clear()

        if (groupedRoles.containsKey(GroupRole.ADMIN)) {
            memberList += groupedRoles[GroupRole.ADMIN] ?: emptyList()
        }

        if (groupedRoles.containsKey(GroupRole.MODERATOR)) {
            memberList += groupedRoles[GroupRole.MODERATOR] ?: emptyList()
        }

        if (groupedRoles.containsKey(GroupRole.DEFAULT)) {
            memberList += groupedRoles[GroupRole.DEFAULT] ?: emptyList()
        }

        memberContactList.addAll(memberList.map {
            ContactAdapter.ContactWrapper(it.contact, false, getGroupRoleName(this, it.role), getGroupRoleColor(it.role))
        })

        groupMemberAdapter = ContactAdapter(memberContactList,
                { _, _ -> },
                false,
                { _, _ -> },
                { contact -> contact.userUUID != clientRole })

        groupInfoMemberList.adapter = groupMemberAdapter
        groupInfoMemberList.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)

        groupInfoEditGroup.setOnClickListener {
            val dialogBuilder = AlertDialog.Builder(this@GroupInfoActivity)
            dialogBuilder.setTitle(R.string.group_info_edit_group_alert_title)

            val editText = EditText(this@GroupInfoActivity)
            editText.hint = getString(R.string.group_info_edit_group_alert_group_name_hint)
            editText.setText(chatInfo.chatName, TextView.BufferType.EDITABLE)

            val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            editText.layoutParams = layoutParams

            dialogBuilder.setView(editText)

            dialogBuilder.setNegativeButton(R.string.group_info_edit_group_alert_cancel, null)
            dialogBuilder.setPositiveButton(R.string.group_info_edit_group_alert_edit) { _, _ ->
                val newName = editText.text.toString()
                if (newName != chatInfo.chatName) {
                    val groupModification = GroupModificationChangeName(chatInfo.chatName, newName, chatInfo.chatUUID, UUID.randomUUID())
                    sendGroupModification(groupModification)
                    chatInfo = chatInfo.copy(chatName = newName)
                    localHandleGroupModification(groupModification, mercuryClient.dataBase)
                    setGroupNameToUI(newName, clientGroupRole != GroupRole.ADMIN)
                }
            }
            dialogBuilder.create().show()
        }

        groupInfoAddMemberButton.isEnabled = clientGroupRole != GroupRole.DEFAULT
        groupInfoAddMemberButton.setOnClickListener {
            val i = Intent(this@GroupInfoActivity, AddGroupMemberActivity::class.java)
            i.putExtra(KEY_CHAT_INFO, chatInfo)
            startActivityForResult(i, ACTION_SELECT_CONTACT)
        }

        groupModificationListener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val (chatUuid, modification) = ActionGroupModificationReceived.getData(intent)

                when (modification) {
                    is GroupModificationChangeName -> {
                        chatInfo = chatInfo.copy(chatName = modification.newName)
                        setGroupNameToUI(modification.newName, false)
                    }
                    is GroupModificationAddUser -> {
                        val contact = getContact(mercuryClient.dataBase, modification.addedUser)
                        memberList += GroupMember(contact, GroupRole.DEFAULT)
                        memberContactList += ContactAdapter.ContactWrapper(contact, false, getGroupRoleName(context, GroupRole.DEFAULT), getGroupRoleColor(GroupRole.DEFAULT))

                        groupMemberAdapter.notifyItemInserted(memberContactList.size - 1)
                    }
                    is GroupModificationKickUser -> {
                        memberList.removeAll { it.contact.userUUID == modification.kickedUser }
                        val index = memberContactList.indexOfFirst { it.contact.userUUID == modification.kickedUser }
                        memberContactList.removeAt(index)

                        groupMemberAdapter.notifyItemRemoved(index)
                    }
                    is GroupModificationChangeRole -> {
                        val newRole = modification.newRole

                        val iMemberList = memberList.indexOfFirst { it.contact.userUUID == modification.affectedUser }
                        memberList[iMemberList] = memberList[iMemberList].copy(role = newRole)

                        val iContactList = memberContactList.indexOfFirst { it.contact.userUUID == modification.affectedUser }
                        memberContactList[iContactList].subtext = getGroupRoleName(context, newRole)
                        memberContactList[iContactList].subtextColor = getGroupRoleColor(newRole)

                        groupMemberAdapter.notifyItemChanged(iContactList)
                    }
                }
            }
        }

        groupInfoLeaveButton.setOnClickListener {
            if (chatInfo.chatType == ChatType.GROUP_DECENTRALIZED || clientGroupRole != GroupRole.ADMIN) {
                AlertDialog.Builder(this)
                        .setTitle(R.string.group_info_leave_group_alert_default_title)
                        .setMessage(R.string.group_info_leave_group_alert_default_message)
                        .setNegativeButton(R.string.group_info_leave_group_alert_cancel) { _, _ -> }
                        .setPositiveButton(R.string.group_info_leave_group_alert_ok) { _, _ ->
                            leaveGroup()
                        }
                        .create()
                        .show()
            } else {
                AlertDialog.Builder(this)
                        .setTitle(R.string.group_info_leave_group_alert_admin_title)
                        .setMessage(R.string.group_info_leave_group_alert_admin_message)
                        .setNegativeButton(R.string.group_info_leave_group_alert_cancel) { _, _ -> }
                        .setPositiveButton(R.string.group_info_leave_group_alert_ok) { _, _ ->
                            leaveGroup()
                        }
                        .create()
                        .show()
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun setGroupNameToUI(name: String, pending: Boolean = false) {
        val text = if (pending) getString(R.string.group_info_pending, name) else name
        supportActionBar?.title = text
        groupInfoGroupName.text = text

        groupInfoEditGroup.isEnabled = !pending
    }

    private var currentlyClicked: Int = -1

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)

        val contact = v.tag as Contact

        val position = memberContactList.indexOfFirst { it.contact.userUUID == contact.userUUID }

        val inflater = menuInflater
        inflater.inflate(R.menu.menu_group_member_selected, menu)

        val userRole = memberList[position].role

        val kickItem = menu.findItem(R.id.menuGroupMemberKick)
        if (clientGroupRole != null) {
            kickItem.isVisible = userRole == GroupRole.DEFAULT && GroupRole.MODERATOR.isLessOrEqual(clientGroupRole!!) || userRole == GroupRole.MODERATOR && clientGroupRole == GroupRole.ADMIN
        }

        val changeRoleItem = menu.findItem(R.id.menuGroupMemberChangeRole)
        changeRoleItem.isVisible = clientGroupRole == GroupRole.ADMIN

        val changeRoleModeratorItem = menu.findItem(R.id.menuGroupMemberChangeRoleModerator)
        changeRoleModeratorItem.isVisible = userRole != GroupRole.MODERATOR

        val changeRoleDefaultItem = menu.findItem(R.id.menuGroupMemberChangeRoleDefault)
        changeRoleDefaultItem.isVisible = userRole != GroupRole.DEFAULT

        currentlyClicked = position
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val mercuryClient = applicationContext as MercuryClient

        val actIndex = memberList.indexOfFirst { it.contact.userUUID == memberContactList[currentlyClicked].contact.userUUID }
        val groupMember = memberList[actIndex]
        return when (item.itemId) {
            R.id.menuGroupMemberSendMessage -> {
                ChatActivity.launch(this, getUserChat(this, mercuryClient.dataBase, groupMember.contact))
                true
            }

            R.id.menuGroupMemberChangeRoleModerator -> {
                changeGroupRole(groupMember, GroupRole.MODERATOR, mercuryClient, actIndex, currentlyClicked)
                true
            }

            R.id.menuGroupMemberChangeRoleDefault -> {
                changeGroupRole(groupMember, GroupRole.DEFAULT, mercuryClient, actIndex, currentlyClicked)
                true
            }

            R.id.menuGroupMemberKick -> {
                val editText = EditText(this@GroupInfoActivity)
                editText.hint = getString(R.string.group_info_kick_alert_reason_hint)

                val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
                editText.layoutParams = layoutParams

                val builder = AlertDialog.Builder(this@GroupInfoActivity)
                        .setTitle(R.string.group_info_kick_alert_title)
                        .setMessage(R.string.group_info_kick_alert_message)
                        .setNegativeButton(R.string.group_info_kick_alert_cancel, null)
                        .setPositiveButton(R.string.group_info_kick_alert_kick) { _, _ ->
                            val groupModification = GroupModificationKickUser(groupMember.contact.userUUID, editText.text.toString(), chatInfo.chatUUID, UUID.randomUUID())
                            localHandleGroupModification(groupModification, mercuryClient.dataBase)
                            sendGroupModification(groupModification)
                            val contact = memberList[actIndex]
                            memberList.removeAt(actIndex)
                            memberContactList.removeAt(memberContactList.indexOfLast { it.contact.userUUID == contact.contact.userUUID })
                            groupInfoMemberList.adapter!!.notifyItemRemoved(actIndex)
                        }

                builder.setView(editText)

                builder.create().show()

                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun changeGroupRole(groupMember: GroupMember, newRole: GroupRole, mercuryClient: MercuryClient, index: Int, contactIndex: Int) {
        val modificationUUID = UUID.randomUUID()
        val groupModification = GroupModificationChangeRole(groupMember.contact.userUUID, groupMember.role, newRole, chatInfo.chatUUID, modificationUUID)
        sendGroupModification(groupModification)

        val oldRole = groupMember.role

        memberList[index] = GroupMember(groupMember.contact, newRole)

        val newText: String = getGroupRoleName(this, newRole)

        val contactWrapper = memberContactList[contactIndex]
        contactWrapper.subtext = newText
        if (clientGroupRole == GroupRole.ADMIN) contactWrapper.subtextColor = getGroupRoleColor(newRole)

        localHandleGroupModification(groupModification, mercuryClient.dataBase)

        groupMemberAdapter.notifyItemChanged(currentlyClicked)
    }

    private fun localHandleGroupModification(groupModification: GroupModification, dataBase: SQLiteDatabase) {
        val client = ClientPreferences.getClientUUID(this)

        if (clientGroupRole == GroupRole.ADMIN) {
            handleGroupModification(groupModification, client, dataBase, client)
        } else {
            handleGroupModificationPending(groupModification, dataBase)
        }
    }

    private fun sendGroupModification(groupModification: GroupModification) {
        val mercuryClient = applicationContext as MercuryClient

        val client = ClientPreferences.getClientUUID(this)

        val isAdmin = clientGroupRole == GroupRole.ADMIN
        val admin = memberList.first { it.role == GroupRole.ADMIN }.contact

        //If admin we can directly message everybody about the change, if not we have to send our change to the admin
        val sendToChatUUID = if (isAdmin) chatInfo.chatUUID else {
            getUserChat(mercuryClient, mercuryClient.dataBase, admin).chatUUID
        }

        val core = MessageCore(client, System.currentTimeMillis(), UUID.randomUUID())
        val data = MessageGroupModification(groupModification)

        val sendTo = if (isAdmin) chatInfo.getOthers(client) else listOf(ChatReceiver.fromContact(admin))
        sendMessageToServer(this@GroupInfoActivity, PendingMessage(ChatMessage(core, data), sendToChatUUID, sendTo), mercuryClient.dataBase)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val mercuryClient = applicationContext as MercuryClient

        val client = ClientPreferences.getClientUUID(this)

        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                ACTION_SELECT_CONTACT -> {
                    val userUUID = data.getSerializableExtra(KEY_USER_UUID) as UUID

                    val modification = GroupModificationAddUser(userUUID, chatInfo.chatUUID, UUID.randomUUID())
                    localHandleGroupModification(modification, mercuryClient.dataBase)

                    sendGroupModification(modification)

                    if (clientGroupRole == GroupRole.ADMIN) {
                        val roleMap = HashMap(roleMap + Pair(userUUID, GroupRole.DEFAULT))
                        val groupKey = getGroupKey(chatInfo.chatUUID, mercuryClient.dataBase)
                                ?: return

                        val groupInvite = if (chatInfo.chatType == ChatType.GROUP_DECENTRALIZED) {
                            MessageGroupInvite.GroupInviteDecentralizedChat(roleMap, chatInfo.chatUUID, chatInfo.chatName, groupKey)
                        } else MessageGroupInvite.GroupInviteCentralizedChat(chatInfo.chatUUID, chatInfo.chatName, groupKey)

                        inviteUserToGroupChat(this, mercuryClient.dataBase, client, userUUID, groupInvite)
                    }
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onResume() {
        super.onResume()
        roleMap.clear()
        memberList.clear()
        loadRoles()
        groupInfoMemberList.adapter!!.notifyDataSetChanged()

        registerReceiver(groupModificationListener, IntentFilter(ACTION_GROUP_MODIFICATION_RECEIVED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(groupModificationListener)
    }

    private fun loadRoles() {
        val mercuryClient = applicationContext as MercuryClient
        val groupRoles = getGroupMembers(mercuryClient.dataBase, chatInfo.chatUUID)

        memberList += groupRoles

        for (groupRole in groupRoles) {
            roleMap[groupRole.contact.userUUID] = groupRole.role
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun getGroupRoleColor(role: GroupRole): Int = when (role) {
        GroupRole.ADMIN -> Color.RED
        GroupRole.MODERATOR -> Color.BLUE
        GroupRole.DEFAULT -> Color.GREEN
    }

    private fun leaveGroup() {
        val client = ClientPreferences.getClientUUID(this)

        val mercuryClient = applicationContext as MercuryClient
        val modification = GroupModificationKickUser(client, "", chatInfo.chatUUID, UUID.randomUUID())

        localHandleGroupModification(modification, mercuryClient.dataBase)
        sendGroupModification(modification)


    }
}