package de.intektor.kentai

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.NotificationManagerCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.*
import android.widget.CheckBox
import com.google.common.hash.Hashing
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import de.intektor.kentai.group_info_activity.GroupInfoActivity
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.android.saveImageExternalKentai
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.chat.adapter.chat.*
import de.intektor.kentai.kentai.chat.adapter.viewing.UserState
import de.intektor.kentai.kentai.chat.adapter.viewing.ViewingAdapter
import de.intektor.kentai.kentai.chat.adapter.viewing.ViewingUser
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.nine_gag.getGagUUID
import de.intektor.kentai.kentai.nine_gag.isNineGagMessage
import de.intektor.kentai.kentai.references.*
import de.intektor.kentai_http_common.chat.*
import de.intektor.kentai_http_common.chat.group_modification.ChatMessageGroupModification
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationAddUser
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.tcp.client_to_server.TypingPacketToServer
import de.intektor.kentai_http_common.tcp.client_to_server.ViewChatPacketToServer
import de.intektor.kentai_http_common.tcp.server_to_client.Status
import de.intektor.kentai_http_common.tcp.server_to_client.UserChange
import de.intektor.kentai_http_common.util.toUUID
import kotlinx.android.synthetic.main.activity_chat.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ChatActivity : AppCompatActivity() {

    lateinit var chatInfo: ChatInfo

    private var firstMessageTime = System.currentTimeMillis()
    private var lastMessageTime = 0L

    private val contactMap: MutableMap<UUID, Contact> = HashMap()

    private lateinit var uploadReferenceFinishedReceiver: BroadcastReceiver
    private lateinit var uploadProgressReceiver: BroadcastReceiver
    private lateinit var downloadReferenceFinishedReceiver: BroadcastReceiver
    private lateinit var downloadProgressReceiver: BroadcastReceiver
    private lateinit var uploadReferenceStartedReceiver: BroadcastReceiver
    private lateinit var userViewChatReceiver: BroadcastReceiver
    private lateinit var directConnectedReceiver: BroadcastReceiver
    private lateinit var updateProfilePictureReceiver: BroadcastReceiver

    private var lastTimeSentTypingMessage = 0L

    private val userToUserInfo = mutableMapOf<UUID, UsernameChatInfo>()

    private val messageObjects = mutableMapOf<UUID, List<ChatAdapter.ChatAdapterWrapper>>()

    private val referenceUUIDToMessageUUID = mutableMapOf<UUID, UUID>()

    private val componentList = mutableListOf<ChatAdapter.ChatAdapterWrapper>()

    private lateinit var chatAdapter: ChatAdapter

    private lateinit var viewingUsersAdapter: ViewingAdapter
    private val viewingUsersList = mutableListOf<ViewingUser>()

    private var onTop = false
    var onBottom = false

    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null

    private var currentlyLoadingMore = false

    private var secondsRecording = 0

    private var currentAudioUUID: UUID? = null

    private val mediaPlayer = MediaPlayer()
    private var currentPlaying: VoiceReferenceHolder? = null

    private val userTypingTime = mutableMapOf<UUID, Long>()

    private var upToDate = true

    lateinit var bubbleLeft: Drawable
    lateinit var bubbleRight: Drawable

    private var actionModeEdit: ActionMode? = null
    private var selectedItems: MutableSet<ChatAdapter.ChatAdapterWrapper> = hashSetOf()

    val isInEditMode: Boolean
        get() = actionModeEdit != null


    private var selectedWithMedia = 0

    companion object {
        private val dateFormatTY = DateFormat.getTimeInstance(DateFormat.SHORT)
        private val dateFormatAnytime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

        const val PERMISSION_REQUEST_EXTERNAL_STORAGE = 1000
        const val PERMISSION_REQUEST_CAMERA = 1001
        const val PERMISSION_REQUEST_AUDIO = 1002

        private const val ACTION_PICK_MEDIA = 1101
        private const val ACTION_SEND_MEDIA = 1103
        private const val ACTION_PICK_BACKGROUND_IMAGE = 1104
        private const val ACTION_TAKE_IMAGE = 1105

        private val calendar = Calendar.getInstance()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_chat)

        val kentaiClient = applicationContext as KentaiClient

        chatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)

        if (intent.extras.containsKey(KEY_MESSAGE_UUID)) {
            val messageUUID = intent.getSerializableExtra(KEY_MESSAGE_UUID) as UUID
            val message = readChatMessageWrappers(kentaiClient.dataBase, "message_uuid = ?", arrayOf(messageUUID.toString())).first()
            firstMessageTime = message.message.timeSent + 1

            upToDate = false
        }

        val lM = LinearLayoutManager(this)
        chatActivityMessageList.layoutManager = lM
        lM.stackFromEnd = true

        val actionBar = supportActionBar

        actionBar?.title = chatInfo.chatName

        if (chatInfo.chatType.isGroup()) {
            for ((receiverUUID) in chatInfo.participants) {
                kentaiClient.dataBase.rawQuery("SELECT contacts.username, user_color_table.color, contacts.user_uuid, contacts.alias FROM user_color_table LEFT JOIN contacts ON contacts.user_uuid = user_color_table.user_uuid WHERE user_color_table.user_uuid = ?", arrayOf(receiverUUID.toString())).use { query ->
                    query.moveToNext()
                    val username = query.getString(0)
                    val color = query.getString(1)
                    val userUUID = query.getString(2).toUUID()
                    val alias = query.getString(3)
                    userToUserInfo[receiverUUID] = UsernameChatInfo(username, color)
                    contactMap.put(userUUID, Contact(username, alias, userUUID, null))
                }
            }
        } else {
            for ((receiverUUID) in chatInfo.participants) {
                val contact = getContact(kentaiClient.dataBase, receiverUUID)
                contactMap[receiverUUID] = contact
            }
        }

        val userNoMember = !chatInfo.isUserParticipant(kentaiClient.userUUID) || !chatInfo.userProfile(kentaiClient.userUUID).isActive
        val adminNoMember = if (chatInfo.chatType == ChatType.GROUP_DECENTRALIZED) {
            val adminContact = getGroupMembers(kentaiClient.dataBase, chatInfo.chatUUID).first { it.role == GroupRole.ADMIN }.contact
            !chatInfo.participants.first { it.receiverUUID == adminContact.userUUID }.isActive
        } else false

        if (userNoMember || adminNoMember) {
            chatActivityTextInput.isEnabled = false
            chatActivitySendMessage.isEnabled = false
        }
        if (userNoMember) {
            chatActivityTextInput.setText(R.string.chat_group_no_member)
        }
        if (adminNoMember) {
            chatActivityTextInput.setText(R.string.chat_group_no_admin)
        }

        viewingUsersAdapter = ViewingAdapter(viewingUsersList)
        chatActivityViewingUsersList.adapter = viewingUsersAdapter
        chatActivityViewingUsersList.layoutManager = LinearLayoutManager(this)

        val messages = load20Messages(kentaiClient, chatInfo.chatUUID, firstMessageTime, true)

        lastMessageTime = messages.firstOrNull()?.message?.timeSent ?: 0L

        chatAdapter = ChatAdapter(componentList, chatInfo, contactMap, this)

        chatActivityMessageList.adapter = chatAdapter

        chatActivityMessageList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                onTop = !recyclerView.canScrollVertically(-1)
                onBottom = !recyclerView.canScrollVertically(1)

                val firstVisibleIndex = lM.findFirstVisibleItemPosition()
                val lastVisibleIndex = lM.findLastVisibleItemPosition()

                (Math.max(0, firstVisibleIndex)..Math.min(lastVisibleIndex, componentList.size))
                        .map { componentList[it].item }
                        .filter { it is ChatMessageWrapper || it is ReferenceHolder }
                        .filter {
                            val wrapper = (it as? ReferenceHolder)?.chatMessageWrapper
                                    ?: it as ChatMessageWrapper
                            wrapper.status != MessageStatus.SEEN
                        }
                        .forEach {
                            val wrapper = (it as? ReferenceHolder)?.chatMessageWrapper
                                    ?: it as ChatMessageWrapper
                            if (!wrapper.client) {
                                wrapper.status = MessageStatus.SEEN

                                updateMessageStatus(kentaiClient.dataBase, wrapper.message.id.toUUID(), wrapper.status, System.currentTimeMillis())

                                for (s in chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID }) {
                                    val other = ChatMessageWrapper(ChatMessageStatusChange(chatInfo.chatUUID, wrapper.message.id.toUUID(), MessageStatus.SEEN, System.currentTimeMillis(),
                                            kentaiClient.userUUID, System.currentTimeMillis()), MessageStatus.WAITING, true, System.currentTimeMillis(), chatInfo.chatUUID)
                                    other.message.referenceUUID = UUID.randomUUID()
                                    sendMessageToServer(this@ChatActivity, PendingMessage(other, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID }), kentaiClient.dataBase)
                                }
                                recyclerView.adapter.notifyItemChanged(componentList.indexOf(it))
                            }
                        }

                if (firstVisibleIndex <= 10 && !currentlyLoadingMore) {
                    loadMore(true)
                }

                if (lastVisibleIndex >= componentList.size - 10 && !currentlyLoadingMore && !upToDate) {
                    loadMore(false)
                }

                if (onBottom) {
                    upToDate = true
                }
            }
        })

        addMessages(messages, true)

        chatActivityTextInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable) {
                chatActivitySendMessage.isEnabled = p0.isNotBlank()
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(newText: CharSequence, p1: Int, p2: Int, p3: Int) {
                if (System.currentTimeMillis() - 5000 >= lastTimeSentTypingMessage) {
                    lastTimeSentTypingMessage = System.currentTimeMillis()
                    if (kentaiClient.directConnectionManager.isConnected) {
                        kentaiClient.directConnectionManager.sendPacket(TypingPacketToServer(chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID }.map { it.receiverUUID }, chatInfo.chatUUID))
                    }
                }
            }
        })

        uploadProgressReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val referenceUUID = intent.getSerializableExtra(KEY_REFERENCE_UUID) as UUID
                val messageUUID = referenceUUIDToMessageUUID[referenceUUID]

                val holder = messageObjects[messageUUID]?.first { it.item is ReferenceHolder }
                        ?: return
                val item = holder.item as ReferenceHolder
                item.progress = (intent.getDoubleExtra(KEY_PROGRESS, 0.0) * 100).toInt()

                chatAdapter.notifyItemChanged(componentList.indexOf(holder))
            }
        }

        uploadReferenceFinishedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val referenceUUID = intent.getSerializableExtra(KEY_REFERENCE_UUID) as UUID
                val successful = intent.getBooleanExtra(KEY_SUCCESSFUL, false)
                val messageUUID = referenceUUIDToMessageUUID[referenceUUID]

                val holder = messageObjects[messageUUID]?.first { it.item is ReferenceHolder }
                        ?: return
                val item = holder.item as ReferenceHolder
                item.isInternetInProgress = false
                item.isFinished = successful

                chatAdapter.notifyItemChanged(componentList.indexOf(holder))
            }
        }

        downloadProgressReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val referenceUUID = intent.getSerializableExtra(KEY_REFERENCE_UUID) as UUID
                val messageUUID = referenceUUIDToMessageUUID[referenceUUID]

                val holder = messageObjects[messageUUID]?.first { it.item is ReferenceHolder }
                        ?: return
                val item = holder.item as ReferenceHolder
                item.progress = intent.getIntExtra(KEY_PROGRESS, 0)

                chatAdapter.notifyItemChanged(componentList.indexOf(holder))
            }
        }

        downloadReferenceFinishedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val referenceUUID = intent.getSerializableExtra(KEY_REFERENCE_UUID) as UUID
                val successful = intent.getBooleanExtra(KEY_SUCCESSFUL, false)
                val messageUUID = referenceUUIDToMessageUUID[referenceUUID]

                val holder = messageObjects[messageUUID]?.first { it.item is ReferenceHolder }
                        ?: return
                val item = holder.item as ReferenceHolder
                item.isInternetInProgress = false
                item.isFinished = successful

                chatAdapter.notifyItemChanged(componentList.indexOf(holder))
            }
        }

        uploadReferenceStartedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
            }
        }

        userViewChatReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val chatUUID = intent.getSerializableExtra(KEY_CHAT_UUID)
                val userUUID = intent.getSerializableExtra(KEY_USER_UUID)
                val view = intent.getBooleanExtra(KEY_USER_VIEW, false)

                if (chatUUID == chatInfo.chatUUID) {
                    if (userUUID == kentaiClient.userUUID) return
                    val contact = contactMap[userUUID]!!
                    val viewingUser = ViewingUser(contact, UserState.VIEWING)
                    if (view) {
                        if (viewingUsersList.any { it.contact.userUUID == userUUID }) return
                        viewingUsersList += viewingUser
                        viewingUsersAdapter.notifyItemInserted(viewingUsersList.size - 1)
                    } else {
                        val index = viewingUsersList.indexOfFirst { it.contact.userUUID == userUUID }
                        if (index != -1) {
                            viewingUsersList.removeAt(index)
                            viewingUsersAdapter.notifyItemRemoved(index)
                        }
                    }
                }
            }
        }

        directConnectedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                viewChat(true)
            }
        }

        updateProfilePictureReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val updatedUserUUID = intent.getSerializableExtra(KEY_USER_UUID) as UUID
                val indexOf = viewingUsersList.indexOfFirst { it.contact.userUUID == updatedUserUUID }
                if (indexOf != -1) {
                    viewingUsersAdapter.notifyItemChanged(indexOf)
                }

                if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
                    invalidateOptionsMenu()
                }
            }
        }

        chatActivitySendMessage.setOnClickListener {
            sendMessage()
            chatActivityTextInput.text.clear()
        }

        val handler = Handler()
        TypingUpdater(handler).run()

        setDefaultSubtitle()

        scrollToBottom()

        chatActivityButtonPickMedia.setOnClickListener {
            if (checkStoragePermission(this, PERMISSION_REQUEST_EXTERNAL_STORAGE)) {
                val pickMedia = Intent(this, PickGalleryActivity::class.java)
                pickMedia.putExtra(KEY_CHAT_INFO, chatInfo)

                startActivityForResult(pickMedia, ACTION_SEND_MEDIA)
            }
        }

        chatActivityButtonRecordVoice.setOnTouchListener { _, motionEvent ->
            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                startRecording()
            } else if (motionEvent.action == MotionEvent.ACTION_UP) {
                stopRecording(true)
            }
            true
        }

        chatActivityButtonTakePicture.setOnClickListener {
            if (checkCameraPermission(this, PERMISSION_REQUEST_CAMERA)) {
                val takePicture = Intent(this, CameraActivity::class.java)

                startActivityForResult(takePicture, ACTION_TAKE_IMAGE)
            }
        }

        chatActivityMessageList.addItemDecoration(HeaderItemDecoration(chatActivityMessageList, object : HeaderItemDecoration.StickyHeaderInterface {
            override fun getHeaderPositionForItem(itemPosition: Int): Int {
                var i = itemPosition
                while (true) {
                    if (isHeader(i)) return i
                    i--
                }
            }

            override fun getHeaderLayout(headerPosition: Int): Int = R.layout.chat_date_item

            override fun bindHeaderData(header: View, headerPosition: Int) {
                DateViewHolder(header, chatAdapter).bind(componentList[headerPosition])
            }

            override fun isHeader(itemPosition: Int): Boolean = componentList[itemPosition].item is DateInfo

        }))

        bubbleLeft = getAttrDrawable(this, R.attr.bubble_left)
        bubbleRight = getAttrDrawable(this, R.attr.bubble_right)

        viewChat(true)

        addInterestedChatUsers(true)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        applyBackgroundImage(getBackgroundChatFile(this, chatInfo.chatUUID))

        chatActivityButtonTakePicture.setImageDrawable(getAttrDrawable(this, R.attr.ic_camera))
        chatActivityButtonPickMedia.setImageDrawable(getAttrDrawable(this, R.attr.ic_perm_media))
        chatActivityButtonRecordVoice.setImageDrawable(getAttrDrawable(this, R.attr.ic_mic))
        chatActivitySendMessage.setImageDrawable(getAttrDrawable(this, R.attr.ic_send))
    }


    private fun sendMessage() {
        val kentaiClient = applicationContext as KentaiClient
        var current = chatActivityTextInput.text.toString()

        while (current.startsWith('\n')) current = current.substring(1)
        while (current.endsWith('\n')) current = current.substring(0, current.length - 1)

        val written = current

        val chatMessage = ChatMessageText(written, kentaiClient.userUUID, System.currentTimeMillis())
        chatMessage.referenceUUID = UUID.randomUUID()
        val wrapper = ChatMessageWrapper(chatMessage, MessageStatus.WAITING, true, System.currentTimeMillis(), chatInfo.chatUUID)
        val wrapperCopy = wrapper.copy(message = ChatMessageText(written, kentaiClient.userUUID, System.currentTimeMillis()))
        wrapperCopy.message.id = wrapper.message.id
        wrapperCopy.message.referenceUUID = wrapper.message.referenceUUID
        wrapperCopy.message.timeSent = wrapper.message.timeSent
        addMessage(wrapperCopy, true)
        chatAdapter.notifyDataSetChanged()

        if (onBottom) scrollToBottom()

        sendMessageToServer(this, PendingMessage(wrapper, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID }), kentaiClient.dataBase)
    }

    fun addMessage(wrapper: ChatMessageWrapper, bottom: Boolean) {
        addMessages(listOf(wrapper), bottom)
    }

    private fun addMessages(list: List<ChatMessageWrapper>, bottom: Boolean) {
        val listToAdd = mutableListOf<ChatAdapter.ChatAdapterWrapper>()
        for (wrapper in list) {
            val associated = createMessageObjects(wrapper)
            messageObjects[wrapper.message.id.toUUID()] = associated

            val dateInfo = DateInfo(wrapper.message.timeSent)

            if (!bottom) {
                listToAdd.removeAll {
                    if (it.item is DateInfo) {
                        calendar.timeInMillis = it.item.time
                        val dateThen = calendar.get(Calendar.DATE)

                        calendar.timeInMillis = dateInfo.time
                        val dateNow = calendar.get(Calendar.DATE)
                        return@removeAll dateThen == dateNow
                    }
                    false
                }
            }

            if (bottom) {
                if ((listToAdd + componentList).none {
                            if (it.item is DateInfo) {
                                calendar.timeInMillis = it.item.time
                                val dateThen = calendar.get(Calendar.DATE)

                                calendar.timeInMillis = dateInfo.time
                                val dateNow = calendar.get(Calendar.DATE)
                                dateThen == dateNow
                            } else false
                        }) {
                    listToAdd += ChatAdapter.ChatAdapterWrapper(item = dateInfo)
                }
                listToAdd.addAll(associated)
            } else {
                listToAdd.addAll(0, associated)
                listToAdd.add(0, ChatAdapter.ChatAdapterWrapper(item = dateInfo))
            }

            referenceUUIDToMessageUUID[wrapper.message.referenceUUID] = wrapper.message.id.toUUID()
            if (isNineGagMessage(wrapper.message.text)) {
                val gagUUID = getGagUUID(wrapper.message.text)
                referenceUUIDToMessageUUID[gagUUID] = wrapper.message.id.toUUID()
            }
        }

        val removed = mutableListOf<Int>()

        if (!bottom) {
            listToAdd.filter { it.item is DateInfo }.forEach { c ->
                c.item as DateInfo
                componentList.filter {
                    if (it.item is DateInfo) {
                        calendar.timeInMillis = it.item.time
                        val dateThen = calendar.get(Calendar.DATE)

                        calendar.timeInMillis = c.item.time
                        val dateNow = calendar.get(Calendar.DATE)
                        dateThen == dateNow
                    } else false
                }.forEach {
                    val index = componentList.indexOf(it)
                    componentList.remove(it)
                    removed += index
                }
            }
        }

        val prevSize = componentList.size

        if (bottom) {
            componentList.addAll(listToAdd)
            chatAdapter.notifyItemRangeInserted(prevSize, listToAdd.size)
        } else {
            componentList.addAll(0, listToAdd)
            chatAdapter.notifyItemRangeInserted(0, listToAdd.size)
        }

        for (i in removed) {
            chatAdapter.notifyItemRemoved(i)
        }
    }

    /**
     * Returns the list of the list components that will be added, you have to add the list to the recycler view yourself
     */
    private fun createMessageObjects(wrapper: ChatMessageWrapper): List<ChatAdapter.ChatAdapterWrapper> {
        val kentaiClient = applicationContext as KentaiClient
        val message = wrapper.message
        if (!message.shouldBeStored()) return emptyList()

        val resultList = mutableListOf<ChatAdapter.ChatAdapterWrapper>()

        if (message is ChatMessageGroupModification) {
            val groupModification = message.groupModification
            if (groupModification is GroupModificationAddUser) {
                registerGroupStuff(groupModification.userUUID.toUUID())
            }
        }

        if (chatInfo.chatType.isGroup() && !wrapper.client) {
            resultList += ChatAdapter.ChatAdapterWrapper(item = userToUserInfo[message.senderUUID]!!)
        }

        resultList += if (wrapper.message.hasReference()) {
            val referenceState = getReferenceState(kentaiClient.dataBase, chatInfo.chatUUID, wrapper.message.referenceUUID)

            if (wrapper.message is ChatMessageVoiceMessage) {
                ChatAdapter.ChatAdapterWrapper(item = VoiceReferenceHolder(wrapper, kentaiClient.currentLoadingTable.containsKey(message.referenceUUID), referenceState == UploadState.FINISHED))
            } else {
                ChatAdapter.ChatAdapterWrapper(item = ReferenceHolder(wrapper, kentaiClient.currentLoadingTable.containsKey(message.referenceUUID), referenceState == UploadState.FINISHED))
            }
        } else if (isNineGagMessage(wrapper.message.text)) {
            val gagUUID = getGagUUID(wrapper.message.text)

            val referenceState = getReferenceState(kentaiClient.dataBase, null, gagUUID)

            ChatAdapter.ChatAdapterWrapper(item = ReferenceHolder(wrapper, kentaiClient.currentLoadingTable.containsKey(gagUUID), referenceState == UploadState.FINISHED))
        } else {
            ChatAdapter.ChatAdapterWrapper(item = wrapper)
        }

        val timeStatusInfo = TimeStatusChatInfo(message.timeSent, wrapper.status, wrapper.client)
        resultList += ChatAdapter.ChatAdapterWrapper(item = timeStatusInfo)

        return resultList
    }

    fun updateMessageStatus(messageUUID: UUID, status: MessageStatus) {
        val associatedObjects = messageObjects[messageUUID] ?: return
        val first = associatedObjects.first { it.item is ChatMessageWrapper || it.item is ReferenceHolder }
        val wrapper = (first.item as? ReferenceHolder)?.chatMessageWrapper ?: first.item as ChatMessageWrapper
        wrapper.status = status

        val timeStatusInfo = associatedObjects.first { it.item is TimeStatusChatInfo }.item as TimeStatusChatInfo
        timeStatusInfo.status = status
        val index = componentList.indexOfFirst { it.item == timeStatusInfo }
        if (index == -1) throw IllegalStateException("TimeStatusInfo was not found!")
        chatAdapter.notifyItemChanged(index)
    }

    /**
     * Puts all information needed for group message display like color or the user himself in the expected arrays
     */
    private fun registerGroupStuff(userUUID: UUID) {
        val kentaiClient = applicationContext as KentaiClient
        kentaiClient.dataBase.rawQuery("SELECT contacts.username, user_color_table.color, contacts.alias FROM user_color_table LEFT JOIN contacts ON contacts.user_uuid = user_color_table.user_uuid WHERE user_color_table.user_uuid = ?", arrayOf(userUUID.toString())).use { query ->
            query.moveToNext()
            val username = query.getString(0)
            val color = query.getString(1)
            val alias = query.getString(2)
            userToUserInfo[userUUID] = UsernameChatInfo(username, color)
            contactMap.put(userUUID, Contact(username, alias, userUUID, null))
        }
    }

    fun scrollToBottom() {
        chatActivityMessageList.smoothScrollToPosition(componentList.size)
    }

    fun loadMore(top: Boolean) {
        val kentaiClient = applicationContext as KentaiClient
        LoadMoreTask({ list ->
            addMessages(list.reversed(), !top)
            if (list.isNotEmpty()) {
                if (top) {
                    lastMessageTime = list.first().message.timeSent
                } else {
                    firstMessageTime = list.last().message.timeSent
                }
            }
        }, chatInfo.chatUUID, kentaiClient, lastMessageTime, lastMessageTime, top).execute()
    }

    private class LoadMoreTask(val updater: (List<ChatMessageWrapper>) -> (Unit), val chatUUID: UUID, val kentaiClient: KentaiClient, val firstMessageTime: Long, val lastMessageTime: Long, val top: Boolean) :
            AsyncTask<Unit, Unit, List<ChatMessageWrapper>>() {
        override fun doInBackground(vararg params: Unit?): List<ChatMessageWrapper> =
                load20Messages(kentaiClient, chatUUID, if (top) lastMessageTime else firstMessageTime, top)

        override fun onPostExecute(result: List<ChatMessageWrapper>) {
            updater.invoke(result)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)

        val kentaiClient = applicationContext as KentaiClient

        val accountItem = menu.findItem(R.id.chatActivityMenuAccount)
        if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
            Picasso.with(this)
                    .load(getProfilePicture(chatInfo.participants.first { it.receiverUUID != kentaiClient.userUUID }.receiverUUID, this))
                    .memoryPolicy(MemoryPolicy.NO_CACHE)
                    .resize(80, 80)
                    .into(object : Target {
                        override fun onPrepareLoad(placeHolderDrawable: Drawable?) {

                        }

                        override fun onBitmapFailed(errorDrawable: Drawable?) {

                        }

                        override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
                            accountItem.icon = BitmapDrawable(resources, bitmap)

                        }
                    })
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val kentaiClient = applicationContext as KentaiClient
        when (item.itemId) {
            R.id.chatActivityMenuAccount -> {
                if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
                    val intent = Intent(this@ChatActivity, ContactInfoActivity::class.java)
                    intent.putExtra(KEY_USER_UUID, chatInfo.participants.first { it.receiverUUID != kentaiClient.userUUID }.receiverUUID)
                    startActivity(intent)
                } else if (chatInfo.chatType.isGroup()) {
                    val intent = Intent(this@ChatActivity, GroupInfoActivity::class.java)
                    intent.putExtra(KEY_CHAT_INFO, chatInfo)
                    startActivity(intent)
                }
                return true
            }
            R.id.chatActivityNativeGallery -> {
                val i = Intent(Intent.ACTION_PICK)
                i.type = "*/*"
                i.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                startActivityForResult(i, ACTION_PICK_MEDIA)
                return true
            }
            R.id.chatActivitySetBackgroundColor -> {

            }
            R.id.chatActivitySetBackgroundImage -> {
                val i = Intent(Intent.ACTION_PICK)
                i.type = "image/*"
                startActivityForResult(i, ACTION_PICK_BACKGROUND_IMAGE)
                return true
            }
            R.id.chatActivityEdit -> {
                startActionMode(editActionMode)
            }
        }
        return false
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ACTION_SEND_MEDIA -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val mediaData = data.getParcelableArrayListExtra<SendMediaActivity.MediaData>(KEY_MEDIA_DATA)
                    if (mediaData == null || mediaData.isEmpty()) return
                    for (mediaDatum in mediaData) {
                        if (isImage(mediaDatum.file)) {
                            sendPhoto(mediaDatum.uri, mediaDatum.text)
                        } else if (isVideo(mediaDatum.file) || isGif(mediaDatum.file)) {
                            sendVideo(mediaDatum.uri, mediaDatum.text, mediaDatum.gif)
                        }
                    }
                }
            }
            ACTION_PICK_MEDIA -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val startMedia = Intent(this, SendMediaActivity::class.java)
                    startMedia.putExtra(KEY_CHAT_INFO, chatInfo)
                    startMedia.putParcelableArrayListExtra(KEY_MEDIA_URL, ArrayList(listOf(data.data)))
                    startActivityForResult(startMedia, ACTION_SEND_MEDIA)
                }
            }
            ACTION_PICK_BACKGROUND_IMAGE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    setBackgroundImage(this, data.data.toString(), chatInfo.chatUUID)
                    applyBackgroundImage(getBackgroundChatFile(this, chatInfo.chatUUID))
                }
            }
            ACTION_TAKE_IMAGE -> {
                if (resultCode == Activity.RESULT_OK && data != null) {

                    val i = Intent(this, SendMediaActivity::class.java)
                    i.putParcelableArrayListExtra(KEY_MEDIA_URL, ArrayList(listOf(data.getParcelableExtra<Uri>(KEY_FILE_URI))))
                    i.putExtra(KEY_CHAT_INFO, chatInfo)
                    startActivityForResult(i, ACTION_SEND_MEDIA)
                }
            }
        }
    }

    private fun sendPhoto(uri: Uri, text: String) {
        val kentaiClient = applicationContext as KentaiClient

        SendPhotoTask({ result ->
            kentaiClient.currentLoadingTable[result.message.referenceUUID] = 0.0
            addMessage(result, true)
            sendMessageToServer(this@ChatActivity, PendingMessage(result, chatInfo.chatUUID,
                    chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID }), kentaiClient.dataBase)
            uploadImage(this@ChatActivity, kentaiClient.dataBase, chatInfo.chatUUID, result.message.referenceUUID,
                    getReferenceFile(result.message.referenceUUID, FileType.IMAGE, filesDir, this@ChatActivity))
            scrollToBottom()
        }, kentaiClient, chatInfo.chatUUID, uri, text).execute()
    }

    private class SendPhotoTask(val execute: (ChatMessageWrapper) -> (Unit), val kentaiClient: KentaiClient, val chatUUID: UUID, val uri: Uri, val text: String) : AsyncTask<Unit, Unit, ChatMessageWrapper>() {
        override fun doInBackground(vararg args: Unit): ChatMessageWrapper {
            val referenceUUID = UUID.randomUUID()

            val bitmap = BitmapFactory.decodeStream(kentaiClient.contentResolver.openInputStream(uri))

            val byteOut = ByteArrayOutputStream()

            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteOut)

            val referenceFile = getReferenceFile(referenceUUID, FileType.IMAGE, kentaiClient.filesDir, kentaiClient)
            byteOut.writeTo(referenceFile.outputStream())

            saveImageExternalKentai(referenceUUID.toString(), bitmap, kentaiClient)

            val hash = Hashing.sha512().hashBytes(byteOut.toByteArray()).toString()
            val message = ChatMessageImage(hash, kentaiClient.userUUID, text, System.currentTimeMillis(), createSmallPreviewImage(referenceFile, FileType.IMAGE))
            message.referenceUUID = referenceUUID

            return ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis(), chatUUID)
        }

        override fun onPostExecute(result: ChatMessageWrapper) {
            execute.invoke(result)
        }
    }

    private fun sendVideo(uri: Uri, text: String, isGif: Boolean) {
        val kentaiClient = applicationContext as KentaiClient

        SendVideoTask({ result ->
            kentaiClient.currentLoadingTable[result.message.referenceUUID] = 0.0

            addMessage(result, true)
            sendMessageToServer(this@ChatActivity, PendingMessage(result, chatInfo.chatUUID,
                    chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID }), kentaiClient.dataBase)
            uploadVideo(this@ChatActivity, kentaiClient.dataBase, chatInfo.chatUUID, result.message.referenceUUID,
                    getReferenceFile(result.message.referenceUUID, if (isGif) FileType.GIF else FileType.VIDEO, filesDir, this@ChatActivity))
            scrollToBottom()
        }, kentaiClient, uri, text, isGif, chatInfo.chatUUID).execute()
    }

    private class SendVideoTask(val execute: (ChatMessageWrapper) -> Unit, val kentaiClient: KentaiClient, val uri: Uri, val text: String, val isGif: Boolean, val chatUUID: UUID) : AsyncTask<Unit, Unit, ChatMessageWrapper>() {
        override fun doInBackground(vararg args: Unit): ChatMessageWrapper {
            val referenceUUID = UUID.randomUUID()

            val fileType = if (isGif) FileType.GIF else FileType.VIDEO
            val referenceFile = saveMediaFileInAppStorage(referenceUUID, uri, kentaiClient, fileType)

            val hash = Hashing.sha512().hashBytes(referenceFile.readBytes())
            val message = ChatMessageVideo(hash.toString(), getVideoDuration(referenceFile, kentaiClient), kentaiClient.userUUID, text, System.currentTimeMillis(), isGif)
            message.referenceUUID = referenceUUID
            return ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis(), chatUUID)
        }

        override fun onPostExecute(result: ChatMessageWrapper) {
            execute.invoke(result)
        }
    }

    override fun onStop() {
        super.onStop()
        stopRecording(false)
        addInterestedChatUsers(false)
    }

    override fun onResume() {
        super.onResume()

        val kentaiClient = applicationContext as KentaiClient

        if (componentList.isNotEmpty() && upToDate) {
            val (_: Long, lastMessageUUID: UUID) = if (componentList.any { it.item is ChatMessageWrapper }) {
                val message = (componentList.last { it.item is ChatMessageWrapper }.item as ChatMessageWrapper).message
                message.timeSent to message.id.toUUID()
            } else 0L to UUID.randomUUID()

            val newMessages = readChatMessageWrappers(kentaiClient.dataBase, "chat_uuid = '${chatInfo.chatUUID}' AND time > $firstMessageTime")

            addMessages(newMessages.filter { it.message.id.toUUID() != lastMessageUUID }, true)

            if (onBottom) scrollToBottom()
        }

        registerReceiver(uploadProgressReceiver, IntentFilter(ACTION_UPLOAD_REFERENCE_PROGRESS))
        registerReceiver(uploadReferenceFinishedReceiver, IntentFilter(ACTION_UPLOAD_REFERENCE_FINISHED))
        registerReceiver(downloadProgressReceiver, IntentFilter(ACTION_DOWNLOAD_REFERENCE_PROGRESS))
        registerReceiver(downloadReferenceFinishedReceiver, IntentFilter(ACTION_DOWNLOAD_REFERENCE_FINISHED))
        registerReceiver(uploadReferenceStartedReceiver, IntentFilter(ACTION_UPLOAD_REFERENCE_STARTED))
        registerReceiver(userViewChatReceiver, IntentFilter(ACTION_USER_VIEW_CHAT))
        registerReceiver(directConnectedReceiver, IntentFilter(ACTION_DIRECT_CONNECTION_CONNECTED))

        viewChat(true)

        addInterestedChatUsers(true)

        setUnreadMessages(kentaiClient.dataBase, chatInfo.chatUUID, 0)

        cancelChatNotifications(this, chatInfo.chatUUID)
    }

    override fun onPause() {
        super.onPause()
        val kentaiClient = applicationContext as KentaiClient

        unregisterReceiver(uploadProgressReceiver)
        unregisterReceiver(uploadReferenceFinishedReceiver)
        unregisterReceiver(downloadProgressReceiver)
        unregisterReceiver(downloadReferenceFinishedReceiver)
        unregisterReceiver(uploadReferenceStartedReceiver)
        unregisterReceiver(userViewChatReceiver)
        unregisterReceiver(directConnectedReceiver)

        viewChat(false)

        addInterestedChatUsers(false)

        setUnreadMessages(kentaiClient.dataBase, chatInfo.chatUUID, 0)
    }

    private var currentContextSelectedIndex = -1

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        menuInflater.inflate(R.menu.menu_chat_bubble, menu)

        val position = v.tag as Int
        currentContextSelectedIndex = position

        super.onCreateContextMenu(menu, v, menuInfo)
    }

    private fun startRecording() {
        if (checkRecordingPermission(this, PERMISSION_REQUEST_AUDIO)) {
            isRecording = true
            secondsRecording = -1

            currentAudioUUID = UUID.randomUUID()

            mediaRecorder = MediaRecorder()
            mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            File(filesDir.path + "/resources/${chatInfo.chatUUID}/").mkdirs()
            mediaRecorder!!.setOutputFile(getReferenceFile(currentAudioUUID!!, FileType.AUDIO, filesDir, this@ChatActivity).path)
            mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            try {
                mediaRecorder!!.prepare()
            } catch (t: Throwable) {
            }

            chatActivityRecordingLayout.visibility = View.VISIBLE

            val handler = Handler()
            handler.post(object : Runnable {
                override fun run() {
                    if (!isRecording) return
                    secondsRecording++

                    var min = 0
                    var seconds = secondsRecording
                    while (seconds >= 60) {
                        min++
                        seconds -= 60
                    }

                    chatActivityRecordingText.text = getString(R.string.chat_activity_voice_record, min, seconds)

                    handler.postDelayed(this, 1000L)
                }
            })

            mediaRecorder!!.start()
        }
    }

    private fun stopRecording(stillOnButton: Boolean) {
        val kentaiClient = applicationContext as KentaiClient

        if (isRecording) {
            isRecording = false

            chatActivityRecordingLayout.visibility = View.GONE

            try {
                mediaRecorder!!.stop()
                mediaRecorder!!.release()
                mediaRecorder = null

                if (secondsRecording > 0 && stillOnButton) {
                    val audioFile = getReferenceFile(currentAudioUUID!!, FileType.AUDIO, filesDir, this@ChatActivity)

                    val message = ChatMessageWrapper(ChatMessageVoiceMessage(secondsRecording, Hashing.sha512().hashBytes(audioFile.readBytes()).toString(), currentAudioUUID!!, kentaiClient.userUUID, "", System.currentTimeMillis()), MessageStatus.WAITING, true, System.currentTimeMillis(), chatInfo.chatUUID)
                    sendMessageToServer(this, PendingMessage(
                            message,
                            chatInfo.chatUUID, chatInfo.participants.filter { it.isActive && it.receiverUUID != kentaiClient.userUUID }
                    ), kentaiClient.dataBase)
                    addMessage(message, true)
                    uploadAudio(this, kentaiClient.dataBase, chatInfo.chatUUID, currentAudioUUID!!, audioFile)

                    scrollToBottom()
                }
            } catch (t: Throwable) {

            }
        }
    }

    fun userTyping(userUUID: UUID) {
        val typingUser = viewingUsersList.firstOrNull { it.contact.userUUID == userUUID } ?: return
        typingUser.userState = UserState.TYPING
        userTypingTime[userUUID] = System.currentTimeMillis()

        viewingUsersAdapter.notifyItemChanged(viewingUsersList.indexOf(typingUser))
    }

    fun userStatusChange(userChange: UserChange) {
        val kentaiClient = applicationContext as KentaiClient

        if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
            val partner = chatInfo.participants.first { it.receiverUUID != kentaiClient.userUUID }
            if (partner.receiverUUID == userChange.userUUID) {
                setDefaultSubtitle()
            }
        }
    }

    fun connectionClosed() {
        supportActionBar!!.subtitle = null
    }

    private fun setDefaultSubtitle() {
        val kentaiClient = applicationContext as KentaiClient

        if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
            val partner = chatInfo.participants.first { it.receiverUUID != kentaiClient.userUUID }
            if (kentaiClient.userStatusMap.containsKey(partner.receiverUUID)) {
                val userChange = kentaiClient.userStatusMap[partner.receiverUUID]!!
                supportActionBar!!.subtitle = when (userChange.status) {
                    Status.ONLINE -> getString(R.string.chat_user_online)
                    Status.OFFLINE_CLOSED, Status.OFFLINE_EXCEPTION -> {
                        if (DateUtils.isToday(userChange.time)) {
                            getString(R.string.chat_user_offline_closed_today, dateFormatTY.format(Date(userChange.time)))
                        } else {
                            val statusTime = Calendar.getInstance()
                            statusTime.timeInMillis = userChange.time

                            val now = Calendar.getInstance()
                            now.timeInMillis = System.currentTimeMillis()

                            if (Math.abs(now.get(Calendar.DATE) - statusTime.get(Calendar.DATE)) == 1) {
                                getString(R.string.chat_user_offline_closed_yesterday, dateFormatTY.format(Date(userChange.time)))
                            } else {
                                getString(R.string.chat_user_offline_closed_anytime, dateFormatAnytime.format(Date(userChange.time)))
                            }
                        }
                    }
                }
            } else {
                supportActionBar!!.subtitle = null
            }
        } else {
            supportActionBar!!.subtitle = null
        }
    }

    inner class TypingUpdater(private val handler: Handler) : Runnable {
        override fun run() {
            for (viewingUser in viewingUsersList.filter { it.userState == UserState.TYPING }) {
                val lastTime = userTypingTime[viewingUser.contact.userUUID]!!
                if (System.currentTimeMillis() - 5000 >= lastTime) {
                    viewingUser.userState = UserState.VIEWING
                    viewingUsersAdapter.notifyItemChanged(viewingUsersList.indexOf(viewingUser))
                }
            }
            handler.postDelayed(this, 7000)
        }
    }

    fun playAudio(referenceHolder: VoiceReferenceHolder, progress: Int) {
        val previous = currentPlaying
        if (previous != null) {
            stopAudio(referenceHolder)
        }

        currentPlaying = referenceHolder
        referenceHolder.isPlaying = true
        referenceHolder.progress = progress

        mediaPlayer.setDataSource(this, Uri.fromFile(getReferenceFile(referenceHolder.chatMessageWrapper.message.referenceUUID, FileType.AUDIO, filesDir,
                this)))
        mediaPlayer.prepare()
        mediaPlayer.seekTo(progress)
        mediaPlayer.start()

        referenceHolder.maxPlayProgress = mediaPlayer.duration

        chatAdapter.notifyItemChanged(componentList.indexOfFirst { it.item == referenceHolder })

        mediaPlayer.setOnCompletionListener {
            stopAudio(referenceHolder)
        }

        val handler = Handler()
        handler.post(object : Runnable {
            override fun run() {
                if (currentPlaying != referenceHolder) return

                referenceHolder.playProgress = mediaPlayer.currentPosition
                chatAdapter.notifyItemChanged(componentList.indexOfFirst { it.item == referenceHolder })

                handler.postDelayed(this, 1000L)
            }
        })
    }

    fun seekAudioChange(referenceHolder: VoiceReferenceHolder, change: Int) {
        if (currentPlaying == referenceHolder) {
            mediaPlayer.seekTo(change)
        }
        referenceHolder.playProgress = change
        chatAdapter.notifyItemChanged(componentList.indexOfFirst { it.item == referenceHolder })
    }

    fun stopAudio(referenceHolder: VoiceReferenceHolder) {
        referenceHolder.isPlaying = false
        referenceHolder.playProgress = 0
        chatAdapter.notifyItemChanged(componentList.indexOfFirst { it.item == referenceHolder })
        mediaPlayer.stop()
        mediaPlayer.reset()
        currentPlaying = null
    }

    fun startReferenceLoad(holder: ReferenceHolder, adapterPosition: Int, fileType: FileType) {
        val kentaiClient = applicationContext as KentaiClient

        if (!checkStoragePermission(this, ChatActivity.PERMISSION_REQUEST_EXTERNAL_STORAGE)) {
            return
        }

        holder.isInternetInProgress = true
        holder.progress = 0

        val upload = holder.chatMessageWrapper.client

        val referenceUUID = holder.chatMessageWrapper.message.referenceUUID

        val referenceFile = getReferenceFile(referenceUUID, fileType, filesDir, this)

        val message = holder.chatMessageWrapper.message
        val hash = when (message) {
            is ChatMessageVoiceMessage -> message.fileHash
            is ChatMessageImage -> message.hash
            is ChatMessageVideo -> message.hash
            else -> throw IllegalArgumentException()
        }
        if (upload) {
            uploadReference(this, chatInfo.chatUUID, referenceUUID, referenceFile, fileType)
        } else {
            downloadReference(this, kentaiClient.dataBase, chatInfo.chatUUID, referenceUUID, fileType, chatInfo.chatType, hash, kentaiClient.privateMessageKey!!)
        }

        chatAdapter.notifyItemChanged(adapterPosition)
    }

    private fun addInterestedChatUsers(add: Boolean) {
        val kentaiClient = applicationContext as KentaiClient
        chatInfo.participants.filter { it.type == ChatReceiver.ReceiverType.USER }.forEach {
            if (add) {
                kentaiClient.addInterestedUser(it.receiverUUID)
            } else {
                kentaiClient.removeInterestedUser(it.receiverUUID)
            }
        }
    }

    private fun viewChat(view: Boolean) {
        val kentaiClient = applicationContext as KentaiClient
        kentaiClient.directConnectionManager.sendPacket(ViewChatPacketToServer(chatInfo.chatUUID, view))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun applyBackgroundImage(backgroundFile: File) {
        if (backgroundFile.exists()) {
            val drawable = BitmapDrawable(resources, BitmapFactory.decodeStream(backgroundFile.inputStream()))
            drawable.gravity = Gravity.FILL
            chatActivityBackgroundImage.setImageDrawable(drawable)
        }
    }

    fun updateChatName(chatUUID: UUID, newName: String) {
        if (chatUUID != chatInfo.chatUUID) return
        supportActionBar?.title = newName
        chatInfo = chatInfo.copy(chatName = newName)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        recreate()
    }

    fun select(item: ChatAdapter.ChatAdapterWrapper, selected: Boolean) {
        item.selected = selected

        if (selected) {
            selectedItems.add(item)
        } else {
            selectedItems.remove(item)
        }

        val menu = actionModeEdit?.menu
        if (menu != null) {
            val reply = menu.findItem(R.id.menuChatEditReply)
            val share = menu.findItem(R.id.menuChatEditShare)
            val delete = menu.findItem(R.id.menuChatEditDelete)
            val copy = menu.findItem(R.id.menuChatEditCopy)
            val info = menu.findItem(R.id.menuChatEditInfo)
            when {
                selectedItems.isEmpty() -> {
                    reply.isEnabled = false
                    share.isEnabled = false
                    delete.isEnabled = false
                    copy.isEnabled = false
                    info.isEnabled = false
                }
                selectedItems.size == 1 -> {
                    reply.isEnabled = true
                    share.isEnabled = true
                    delete.isEnabled = true
                    copy.isEnabled = true
                    info.isEnabled = true
                }
                else -> {
                    reply.isEnabled = false
                    share.isEnabled = true
                    delete.isEnabled = true
                    copy.isEnabled = true
                    info.isEnabled = false
                }
            }
        }
    }

    fun activateEditMode() {
        if (!isInEditMode) {
            startActionMode(editActionMode)
        }
    }

    private val editActionMode = object : ActionMode.Callback {
        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
            when (item.itemId) {
                R.id.menuChatEditDelete -> {
                    val dialog = AlertDialog.Builder(this@ChatActivity)
                            .setIcon(R.drawable.baseline_warning_white_24)
                            .setTitle(R.string.chat_activity_edit_chat_menu_delete_alert_title)
                            .setMessage(getString(R.string.chat_activity_edit_chat_menu_delete_alert_message, selectedItems.size))
                            .setNegativeButton(R.string.chat_activity_edit_chat_menu_delete_alert_negative) { _, _ -> }
                            .setPositiveButton(R.string.chat_activity_edit_chat_menu_delete_alert_positive) { _, _ ->
                                DeleteMessagesTask(selectedItems.map { it.item as ChatMessageWrapper }, chatInfo.chatUUID, applicationContext as KentaiClient, NotificationManagerCompat.from(this@ChatActivity)).execute()
                                selectedItems.map { componentList.indexOf(it) }.forEach { chatAdapter.notifyItemRemoved(it) }
                                componentList.removeAll(selectedItems)
                            }
                    if (selectedWithMedia > 0) {
                        val deleteMediaCheckbox = CheckBox(this@ChatActivity)
                        deleteMediaCheckbox.isChecked = false
                        deleteMediaCheckbox.text = getString(R.string.chat_activity_edit_chat_menu_delete_alert_media)

                        dialog.setView(deleteMediaCheckbox)
                    }

                    dialog.create().show()
                }
                R.id.menuChatEditShare -> {

                }
                R.id.menuChatEditReply -> {

                }
                R.id.menuChatEditCopy -> {
                    val text = if (selectedItems.size == 1) {
                        val wrapper = selectedItems.first().item as ChatMessageWrapper
                        wrapper.message.text
                    } else {
                        selectedItems.joinToString(separator = "\n") {
                            val wrapper = it.item as ChatMessageWrapper
                            "[${dateFormatAnytime.format(Date(wrapper.message.timeSent))}]: ${wrapper.message.text}"
                        }
                    }
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText(getString(R.string.app_name), text)
                    clipboard.primaryClip = clip

                    mode.finish()
                }
            }
            return true
        }

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menuInflater.inflate(R.menu.menu_chat_edit, menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            actionModeEdit = mode

            chatAdapter.notifyItemRangeChanged(0, componentList.size)
            return false
        }

        override fun onDestroyActionMode(mode: ActionMode) {
            actionModeEdit = null

            chatAdapter.notifyItemRangeChanged(0, componentList.size)

            selectedItems.forEach { it.selected = false }
            selectedItems.clear()
        }
    }
}