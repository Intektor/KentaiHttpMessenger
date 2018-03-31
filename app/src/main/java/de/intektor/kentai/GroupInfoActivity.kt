package de.intektor.kentai

import android.content.Intent
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
import de.intektor.kentai.group_info_activity.GroupMemberAdapter
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.groups.handleGroupModification
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.GroupRole
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.chat.group_modification.*
import de.intektor.kentai_http_common.util.toKey
import de.intektor.kentai_http_common.util.toUUID
import kotlinx.android.synthetic.main.activity_group_info.*
import java.security.PublicKey
import java.util.*
import kotlin.collections.HashMap


class GroupInfoActivity : AppCompatActivity(), GroupMemberAdapter.ClickListener {

    private lateinit var chatInfo: ChatInfo
    private val roleMap: HashMap<UUID, GroupRole> = HashMap()

    private var myRole: GroupRole? = null

    private val memberList: MutableList<GroupMember> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_group_info)

        chatInfo = intent.getParcelableExtra("chatInfo")

        setGroupNameToUI()

        loadRoles()

        myRole = roleMap[KentaiClient.INSTANCE.userUUID]
        groupInfoEditGroup.isEnabled = myRole != null && myRole != GroupRole.DEFAULT

        val groupedRoles = memberList.groupBy { it.role }

        memberList.clear()

        if (groupedRoles.containsKey(GroupRole.ADMIN)) {
            memberList.addAll(groupedRoles[GroupRole.ADMIN]!!)
        }

        if (groupedRoles.containsKey(GroupRole.MODERATOR)) {
            memberList.addAll(groupedRoles[GroupRole.MODERATOR]!!)
        }

        if (groupedRoles.containsKey(GroupRole.DEFAULT)) {
            memberList.addAll(groupedRoles[GroupRole.DEFAULT]!!)
        }

        val adapter = GroupMemberAdapter(memberList, this, this)
        groupInfoMemberList.adapter = adapter
        groupInfoMemberList.layoutManager = LinearLayoutManager(this)
        groupInfoMemberList.adapter.notifyDataSetChanged()

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
                    val groupModification = GroupModificationChangeName(chatInfo.chatName, newName, chatInfo.chatUUID.toString())
                    sendGroupModification(groupModification)
                    chatInfo = chatInfo.copy(chatName = newName)
                    handleGroupModification(groupModification, KentaiClient.INSTANCE.dataBase)
                    setGroupNameToUI()
                }
            })
            dialogBuilder.create().show()
        }

        groupInfoAddMemberButton.isEnabled = myRole != GroupRole.DEFAULT
        groupInfoAddMemberButton.setOnClickListener {
            val i = Intent(this@GroupInfoActivity, AddGroupMemberActivity::class.java)
            i.putExtra("chatInfo", chatInfo)
            i.putExtra("roleMap", roleMap)
            startActivity(i)
        }
    }

    private fun setGroupNameToUI() {
        supportActionBar?.title = getString(R.string.group_info_action_bar_title, chatInfo.chatName)
        groupInfoGroupName.text = chatInfo.chatName
    }

    override fun onClickedGroupMember(view: View, holder: GroupMemberAdapter.ViewHolder) {

    }

    private var currentlyClicked: Int = -1

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        super.onCreateContextMenu(menu, v, menuInfo)
        val position = v.tag as Int
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_group_member_selected, menu)

        val kickItem = menu.findItem(R.id.menuGroupMemberKick)
        if (myRole != null) {
            if (myRole == GroupRole.ADMIN) kickItem.isVisible = true
            else kickItem.isVisible = myRole == GroupRole.MODERATOR && memberList[position].role == GroupRole.DEFAULT
        }

        val changeRoleItem = menu.findItem(R.id.menuGroupMemberChangeRole)
        changeRoleItem.isVisible = true

        val changeRoleModeratorItem = menu.findItem(R.id.menuGroupMemberChangeRoleModerator)
        changeRoleModeratorItem.isVisible = myRole == GroupRole.ADMIN

        val changeRoleDefaultItem = menu.findItem(R.id.menuGroupMemberChangeRoleDefault)
        changeRoleDefaultItem.isVisible = myRole != GroupRole.DEFAULT

        currentlyClicked = position
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val index = currentlyClicked
        return when (item.itemId) {
            R.id.menuGroupMemberSendMessage -> {

                var key: PublicKey? = null

                val contact = memberList[index].contact
                KentaiClient.INSTANCE.dataBase.rawQuery("SELECT message_key FROM contacts WHERE user_uuid = ?", arrayOf(contact.userUUID.toString())).use { query ->
                    query.moveToNext()
                    key = if (query.isNull(0)) {
                        requestPublicKey(listOf(contact.userUUID), KentaiClient.INSTANCE.dataBase)[contact.userUUID]!!
                    } else {
                        val keyString = query.getString(0)
                        if (keyString.isEmpty()) {
                            requestPublicKey(listOf(contact.userUUID), KentaiClient.INSTANCE.dataBase)[contact.userUUID]!!
                        } else {
                            keyString.toKey() as PublicKey
                        }
                    }
                }

                val i = Intent(this@GroupInfoActivity, ChatActivity::class.java)

                KentaiClient.INSTANCE.dataBase.rawQuery("SELECT chat_uuid FROM user_to_chat_uuid WHERE user_uuid = ?", arrayOf(contact.userUUID.toString())).use { query ->
                    val chatUUID: UUID
                    val wasRandom: Boolean
                    if (query.moveToNext()) {
                        chatUUID = query.getString(0).toUUID()
                        wasRandom = false
                    } else {
                        chatUUID = UUID.randomUUID()
                        wasRandom = true
                    }
                    val chatInfo = ChatInfo(chatUUID, contact.name, ChatType.TWO_PEOPLE,
                            listOf(ChatReceiver(contact.userUUID, key, ChatReceiver.ReceiverType.USER), ChatReceiver(KentaiClient.INSTANCE.userUUID, null, ChatReceiver.ReceiverType.USER, true)))
                    if (wasRandom) createChat(chatInfo, KentaiClient.INSTANCE.dataBase, KentaiClient.INSTANCE.userUUID)
                    i.putExtra("chatInfo", chatInfo)
                }

                startActivity(i)

                true
            }

            R.id.menuGroupMemberChangeRoleModerator -> {
                val groupModification = GroupModificationChangeRole(memberList[index].contact.userUUID, memberList[index].role, GroupRole.MODERATOR, chatInfo.chatUUID.toString())
                handleGroupModification(groupModification, KentaiClient.INSTANCE.dataBase)
                sendGroupModification(groupModification)
                memberList[index] = GroupMember(memberList[index].contact, GroupRole.MODERATOR)
                groupInfoMemberList.adapter.notifyDataSetChanged()
                true
            }

            R.id.menuGroupMemberChangeRoleDefault -> {
                val groupModification = GroupModificationChangeRole(memberList[index].contact.userUUID, memberList[index].role, GroupRole.DEFAULT, chatInfo.chatUUID.toString())
                handleGroupModification(groupModification, KentaiClient.INSTANCE.dataBase)
                sendGroupModification(groupModification)
                memberList[index] = GroupMember(memberList[index].contact, GroupRole.DEFAULT)
                groupInfoMemberList.adapter.notifyDataSetChanged()
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
                            val groupModification = GroupModificationKickUser(memberList[index].contact.userUUID, editText.text.toString(), chatInfo.chatUUID.toString())
                            handleGroupModification(groupModification, KentaiClient.INSTANCE.dataBase)
                            sendGroupModification(groupModification)
                            memberList.removeAt(index)
                            groupInfoMemberList.adapter.notifyDataSetChanged()
                        })

                builder.setView(editText)

                builder.create().show()

                true
            }
            else -> super.onContextItemSelected(item)
        }
    }

    private fun sendGroupModification(groupModification: GroupModification) {
        val wrapper = ChatMessageWrapper(ChatMessageGroupModification(groupModification, KentaiClient.INSTANCE.userUUID, System.currentTimeMillis()),
                MessageStatus.WAITING, true, System.currentTimeMillis())
        wrapper.message.referenceUUID = UUID.randomUUID()
        sendMessageToServer(this@GroupInfoActivity,
                PendingMessage(
                        wrapper,
                        chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }))
    }

    override fun onResume() {
        super.onResume()
        roleMap.clear()
        memberList.clear()
        loadRoles()
        groupInfoMemberList.adapter.notifyDataSetChanged()
    }

    private fun loadRoles() {
        val groupRoles = readGroupRoles(KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID)
        for (groupRole in groupRoles) {
            roleMap.put(groupRole.contact.userUUID, groupRole.role)
        }
    }
}