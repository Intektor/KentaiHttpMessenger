package de.intektor.kentai.group_info_activity

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color
import android.os.Bundle
import android.support.v7.app.AlertDialog
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.view.ContextMenu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import de.intektor.kentai.AddGroupMemberActivity
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.fragment.ContactAdapter
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.groups.*
import de.intektor.kentai_http_common.chat.ChatMessageGroupInvite
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.GroupRole
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.chat.group_modification.*
import de.intektor.kentai_http_common.util.toKey
import de.intektor.kentai_http_common.util.toUUID
import kotlinx.android.synthetic.main.activity_group_info.*
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.security.PublicKey
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

        val kentaiClient = applicationContext as KentaiClient

        chatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)

        val pending = getPendingModifications(chatInfo.chatUUID, kentaiClient.dataBase)

        val changeName: GroupModificationChangeName? = pending.firstOrNull { it is GroupModificationChangeName } as? GroupModificationChangeName

        setGroupNameToUI(changeName?.newName ?: chatInfo.chatName, changeName != null)

        loadRoles()

        clientGroupRole = roleMap[kentaiClient.userUUID]
        groupInfoEditGroup.isEnabled = clientGroupRole != null && clientGroupRole != GroupRole.DEFAULT

        val groupedRoles = memberList.groupBy { it.role }

        memberList.clear()

        if (groupedRoles.containsKey(GroupRole.ADMIN)) {
            memberList += groupedRoles[GroupRole.ADMIN]!!
        }

        if (groupedRoles.containsKey(GroupRole.MODERATOR)) {
            memberList += groupedRoles[GroupRole.MODERATOR]!!
        }

        if (groupedRoles.containsKey(GroupRole.DEFAULT)) {
            memberList += groupedRoles[GroupRole.DEFAULT]!!
        }

        memberContactList.addAll(memberList.map {
            ContactAdapter.ContactWrapper(it.contact, false, getGroupRoleName(this, it.role), getGroupRoleColor(it.role))
        })

        groupMemberAdapter = ContactAdapter(memberContactList, { _, _ -> }, false, { _, _ -> }, { contact -> contact.userUUID != kentaiClient.userUUID })

        groupInfoMemberList.adapter = groupMemberAdapter
        groupInfoMemberList.layoutManager = LinearLayoutManager(this)

        groupInfoEditGroup.setOnClickListener {
            val dialogBuilder = AlertDialog.Builder(this@GroupInfoActivity)
            dialogBuilder.setTitle(R.string.group_info_edit_group_alert_title)

            val editText = EditText(this@GroupInfoActivity)
            editText.hint = getString(R.string.group_info_edit_group_alert_group_name_hint)
            editText.setText(chatInfo.chatName, TextView.BufferType.EDITABLE)

            val layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
            editText.layoutParams = layoutParams

            dialogBuilder.setView(editText)

            dialogBuilder.setNegativeButton(R.string.group_info_edit_group_alert_cancel, { _, _ -> })
            dialogBuilder.setPositiveButton(R.string.group_info_edit_group_alert_edit, { _, _ ->
                val newName = editText.text.toString()
                if (newName != chatInfo.chatName) {
                    val groupModification = GroupModificationChangeName(chatInfo.chatName, newName, chatInfo.chatUUID.toString(), UUID.randomUUID().toString())
                    sendGroupModification(groupModification)
                    chatInfo = chatInfo.copy(chatName = newName)
                    localHandleGroupModification(groupModification, kentaiClient.dataBase, kentaiClient)
                    setGroupNameToUI(newName, clientGroupRole != GroupRole.ADMIN)
                }
            })
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
                val chatUUID = intent.getSerializableExtra(KEY_CHAT_UUID) as UUID
                if (chatUUID != chatInfo.chatUUID) return

                val modificationUUID = intent.getSerializableExtra(KEY_GROUP_MODIFICATION_UUID) as UUID
                val modification = GroupModificationRegistry.create(intent.getIntExtra(KEY_GROUP_MODIFICATION_TYPE_ID, 0), chatUUID, modificationUUID)
                val input = intent.getByteArrayExtra(KEY_GROUP_MODIFICATION)
                modification.read(DataInputStream(ByteArrayInputStream(input)))

                when (modification) {
                    is GroupModificationChangeName -> {
                        chatInfo = chatInfo.copy(chatName = modification.newName)
                        setGroupNameToUI(modification.newName, false)
                    }
                    is GroupModificationAddUser -> {
                        val contact = getContact(kentaiClient.dataBase, modification.userUUID.toUUID())
                        memberList += GroupMember(contact, GroupRole.DEFAULT)
                        memberContactList += ContactAdapter.ContactWrapper(contact, false, getGroupRoleName(context, GroupRole.DEFAULT), getGroupRoleColor(GroupRole.DEFAULT))

                        groupMemberAdapter.notifyItemInserted(memberContactList.size - 1)
                    }
                    is GroupModificationKickUser -> {
                        memberList.removeAll { it.contact.userUUID == modification.toKick.toUUID() }
                        val index = memberContactList.indexOfFirst { it.contact.userUUID == modification.toKick.toUUID() }
                        memberContactList.removeAt(index)

                        groupMemberAdapter.notifyItemRemoved(index)
                    }
                    is GroupModificationChangeRole -> {
                        val newRole = GroupRole.values()[modification.newRole.toInt()]

                        val iMemberList = memberList.indexOfFirst { it.contact.userUUID == modification.userUUID.toUUID() }
                        memberList[iMemberList] = memberList[iMemberList].copy(role = newRole)

                        val iContactList = memberContactList.indexOfFirst { it.contact.userUUID == modification.userUUID.toUUID() }
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
                        .setNegativeButton(R.string.group_info_leave_group_alert_cancel, { _, _ -> })
                        .setPositiveButton(R.string.group_info_leave_group_alert_ok, { _, _ ->
                            leaveGroup()
                        })
                        .create()
                        .show()
            } else {
                AlertDialog.Builder(this)
                        .setTitle(R.string.group_info_leave_group_alert_admin_title)
                        .setMessage(R.string.group_info_leave_group_alert_admin_message)
                        .setNegativeButton(R.string.group_info_leave_group_alert_cancel, { _, _ -> })
                        .setPositiveButton(R.string.group_info_leave_group_alert_ok, { _, _ ->
                            leaveGroup()
                        })
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
        val kentaiClient = applicationContext as KentaiClient

        val actIndex = memberList.indexOfFirst { it.contact.userUUID == memberContactList[currentlyClicked].contact.userUUID }
        val groupMember = memberList[actIndex]
        return when (item.itemId) {
            R.id.menuGroupMemberSendMessage -> {
                var key: PublicKey? = null

                val contact = groupMember.contact
                kentaiClient.dataBase.rawQuery("SELECT message_key FROM contacts WHERE user_uuid = ?", arrayOf(contact.userUUID.toString())).use { query ->
                    query.moveToNext()
                    key = if (query.isNull(0)) {
                        requestPublicKey(listOf(contact.userUUID), kentaiClient.dataBase)[contact.userUUID]!!
                    } else {
                        val keyString = query.getString(0)
                        if (keyString.isEmpty()) {
                            requestPublicKey(listOf(contact.userUUID), kentaiClient.dataBase)[contact.userUUID]!!
                        } else {
                            keyString.toKey() as PublicKey
                        }
                    }
                }

                val i = Intent(this@GroupInfoActivity, ChatActivity::class.java)

                val chatInfo = getUserChat(kentaiClient.dataBase, contact, kentaiClient)
                i.putExtra(KEY_CHAT_INFO, chatInfo)

                startActivity(i)

                true
            }

            R.id.menuGroupMemberChangeRoleModerator -> {
                changeGroupRole(groupMember, GroupRole.MODERATOR, kentaiClient, actIndex, currentlyClicked)
                true
            }

            R.id.menuGroupMemberChangeRoleDefault -> {
                changeGroupRole(groupMember, GroupRole.DEFAULT, kentaiClient, actIndex, currentlyClicked)
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
                        .setNegativeButton(R.string.group_info_kick_alert_cancel, { _, _ -> })
                        .setPositiveButton(R.string.group_info_kick_alert_kick, { _, _ ->
                            val groupModification = GroupModificationKickUser(groupMember.contact.userUUID, editText.text.toString(), chatInfo.chatUUID.toString(), UUID.randomUUID().toString())
                            localHandleGroupModification(groupModification, kentaiClient.dataBase, kentaiClient)
                            sendGroupModification(groupModification)
                            val contact = memberList[actIndex]
                            memberList.removeAt(actIndex)
                            memberContactList.removeAt(memberContactList.indexOfLast { it.contact.userUUID == contact.contact.userUUID })
                            groupInfoMemberList.adapter.notifyItemRemoved(actIndex)
                        })

                builder.setView(editText)

                builder.create().show()

                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun changeGroupRole(groupMember: GroupMember, newRole: GroupRole, kentaiClient: KentaiClient, index: Int, contactIndex: Int) {
        val modificationUUID = UUID.randomUUID()
        val groupModification = GroupModificationChangeRole(groupMember.contact.userUUID, groupMember.role, newRole, chatInfo.chatUUID.toString(), modificationUUID.toString())
        sendGroupModification(groupModification)

        val oldRole = groupMember.role

        memberList[index] = GroupMember(groupMember.contact, newRole)

        val newText: String = getGroupRoleName(this, newRole)

        val contactWrapper = memberContactList[contactIndex]
        contactWrapper.subtext = newText
        if (clientGroupRole == GroupRole.ADMIN) contactWrapper.subtextColor = getGroupRoleColor(newRole)

        localHandleGroupModification(groupModification, kentaiClient.dataBase, kentaiClient)

        groupMemberAdapter.notifyItemChanged(currentlyClicked)
    }

    private fun localHandleGroupModification(groupModification: GroupModification, dataBase: SQLiteDatabase, kentaiClient: KentaiClient) {
        if (clientGroupRole == GroupRole.ADMIN) {
            handleGroupModification(groupModification, kentaiClient.userUUID, dataBase, kentaiClient.userUUID)
        } else {
            handleGroupModificationPending(groupModification, dataBase)
        }
    }

    private fun sendGroupModification(groupModification: GroupModification) {
        val kentaiClient = applicationContext as KentaiClient

        val isAdmin = clientGroupRole == GroupRole.ADMIN
        val admin = memberList.first { it.role == GroupRole.ADMIN }.contact

        //If admin we can directly message everybody about the change, if not we have to send our change to the admin
        val sendToChatUUID = if (isAdmin) chatInfo.chatUUID else {
            getUserChat(kentaiClient.dataBase, admin, kentaiClient).chatUUID
        }

        val wrapper = ChatMessageWrapper(ChatMessageGroupModification(groupModification, kentaiClient.userUUID, System.currentTimeMillis()),
                MessageStatus.WAITING, true, System.currentTimeMillis(), sendToChatUUID)

        val sendTo = if (isAdmin) chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID } else listOf(ChatReceiver.fromContact(admin))
        sendMessageToServer(this@GroupInfoActivity, PendingMessage(wrapper, sendToChatUUID, sendTo), kentaiClient.dataBase)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        val kentaiClient = applicationContext as KentaiClient
        if (resultCode == Activity.RESULT_OK && data != null) {
            when (requestCode) {
                ACTION_SELECT_CONTACT -> {
                    val userUUID = data.getSerializableExtra(KEY_USER_UUID) as UUID

                    val modification = GroupModificationAddUser(userUUID, chatInfo.chatUUID.toString(), UUID.randomUUID().toString())
                    localHandleGroupModification(modification, kentaiClient.dataBase, kentaiClient)

                    sendGroupModification(modification)

                    if (clientGroupRole == GroupRole.ADMIN) {
                        val roleMap = HashMap(roleMap + Pair(userUUID, GroupRole.DEFAULT))
                        val groupKey = getGroupKey(chatInfo.chatUUID, kentaiClient.dataBase)
                                ?: return

                        val groupInvite = if (chatInfo.chatType == ChatType.GROUP_DECENTRALIZED) {
                            ChatMessageGroupInvite.GroupInviteDecentralizedChat(roleMap, chatInfo.chatUUID, chatInfo.chatName, groupKey)
                        } else ChatMessageGroupInvite.GroupInviteCentralizedChat(chatInfo.chatUUID, chatInfo.chatName, groupKey)

                        inviteUserToGroupChat(userUUID, chatInfo, groupInvite, kentaiClient.userUUID, this, kentaiClient.dataBase)
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
        groupInfoMemberList.adapter.notifyDataSetChanged()

        registerReceiver(groupModificationListener, IntentFilter(ACTION_GROUP_MODIFICATION_RECEIVED))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(groupModificationListener)
    }

    private fun loadRoles() {
        val kentaiClient = applicationContext as KentaiClient
        val groupRoles = getGroupMembers(kentaiClient.dataBase, chatInfo.chatUUID)

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
        val kentaiClient = applicationContext as KentaiClient
        val modification = GroupModificationKickUser(kentaiClient.userUUID, "", chatInfo.chatUUID.toString(), UUID.randomUUID().toString())

        localHandleGroupModification(modification, kentaiClient.dataBase, kentaiClient)
        sendGroupModification(modification)


    }
}