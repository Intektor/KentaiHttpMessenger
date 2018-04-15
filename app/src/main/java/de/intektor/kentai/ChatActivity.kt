package de.intektor.kentai

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.support.v13.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.*
import com.google.common.hash.Hashing
import de.intektor.kentai.group_info_activity.GroupInfoActivity
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.android.saveImageExternalKentai
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.chat.adapter.chat.ChatAdapter
import de.intektor.kentai.kentai.chat.adapter.chat.TimeStatusChatInfo
import de.intektor.kentai.kentai.chat.adapter.chat.UsernameChatInfo
import de.intektor.kentai.kentai.chat.adapter.viewing.UserState
import de.intektor.kentai.kentai.chat.adapter.viewing.ViewingAdapter
import de.intektor.kentai.kentai.chat.adapter.viewing.ViewingUser
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.firebase.DisplayNotificationReceiver
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

    private var lastTimeSentTypingMessage = 0L

    private val userToUserInfo = mutableMapOf<UUID, UsernameChatInfo>()

    private val messageObjects = mutableMapOf<UUID, List<Any>>()

    private val referenceUUIDToMessageUUID = mutableMapOf<UUID, UUID>()

    private val componentList = mutableListOf<Any>()

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

    companion object {
        private val dateFormatTY = DateFormat.getTimeInstance(DateFormat.SHORT)
        private val dateFormatAnytime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

        const val PERMISSION_REQUEST_EXTERNAL_STORAGE = 1000

        private const val ACTION_PICK_MEDIA = 1001
        private const val ACTION_SEND_MEDIA = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        val kentaiClient = applicationContext as KentaiClient

        chatActivitySendMessage.setOnClickListener({
            sendMessage()
            chatActivityTextInput.text.clear()
        })

        val lM = LinearLayoutManager(this)
        chatActivityMessageList.layoutManager = lM
        lM.stackFromEnd = true

        chatInfo = intent.getParcelableExtra(KEY_CHAT_INFO)

        val actionBar = supportActionBar
        actionBar?.title = chatInfo.chatName

        if (chatInfo.chatType == ChatType.GROUP) {
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
                val contact = readContact(kentaiClient.dataBase, receiverUUID)
                contactMap[receiverUUID] = contact
            }
        }

        if (!chatInfo.isUserParticipant(kentaiClient.userUUID) || !chatInfo.userProfile(kentaiClient.userUUID).isActive) {
            chatActivityTextInput.isEnabled = false
            chatActivityTextInput.setText(R.string.chat_group_no_member)
            chatActivitySendMessage.isEnabled = false
        }


        viewingUsersAdapter = ViewingAdapter(viewingUsersList)
        chatActivityViewingUsersList.adapter = viewingUsersAdapter
        chatActivityViewingUsersList.layoutManager = LinearLayoutManager(this)

        val messages = load20Messages(kentaiClient, chatInfo.chatUUID, firstMessageTime)

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
                        .map { componentList[it] }
                        .filter { it is ChatMessageWrapper }
                        .filter { it as ChatMessageWrapper; it.status != MessageStatus.SEEN }
                        .forEach {
                            it as ChatMessageWrapper
                            if (!it.client) {
                                it.status = MessageStatus.SEEN
                                val statement = kentaiClient.dataBase.compileStatement("INSERT INTO message_status_change (message_uuid, status, time) VALUES(?, ?, ?)")
                                statement.bindString(1, it.message.id.toString())
                                statement.bindLong(2, it.status.ordinal.toLong())
                                statement.bindLong(3, System.currentTimeMillis())
                                statement.execute()
                                for (s in chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID }) {
                                    val wrapper = ChatMessageWrapper(ChatMessageStatusChange(chatInfo.chatUUID, it.message.id, MessageStatus.SEEN, System.currentTimeMillis(),
                                            kentaiClient.userUUID, System.currentTimeMillis()), MessageStatus.WAITING, true, System.currentTimeMillis())
                                    wrapper.message.referenceUUID = UUID.randomUUID()
                                    sendMessageToServer(this@ChatActivity, PendingMessage(wrapper, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != kentaiClient.userUUID }), kentaiClient.dataBase)
                                }
                                recyclerView.adapter.notifyDataSetChanged()
                            }
                        }
                if (onTop && !currentlyLoadingMore) {
                    loadMore()
                }
            }
        })

        addMessages(messages, true)

        chatActivityTextInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

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
                val referenceUUID = intent.getStringExtra(KEY_REFERENCE_UUID).toUUID()
                val messageUUID = referenceUUIDToMessageUUID[referenceUUID]

                val holder = messageObjects[messageUUID]?.first { it is ReferenceHolder } as ReferenceHolder
                holder.progress = (intent.getDoubleExtra("progress", 0.0) * 100).toInt()

                chatAdapter.notifyItemChanged(componentList.indexOf(holder))
            }
        }

        uploadReferenceFinishedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val successful = intent.getBooleanExtra("successful", false)
                val messageUUID = referenceUUIDToMessageUUID[referenceUUID]

                val holder = (messageObjects[messageUUID]?.firstOrNull { it is ReferenceHolder }
                        ?: return) as ReferenceHolder
                holder.isInternetInProgress = false
                holder.isFinished = successful

                chatAdapter.notifyItemChanged(componentList.indexOf(holder))
            }
        }

        downloadProgressReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val messageUUID = referenceUUIDToMessageUUID[referenceUUID]

                val holder = messageObjects[messageUUID]?.first { it is ReferenceHolder } as ReferenceHolder
                holder.progress = (intent.getDoubleExtra("progress", 0.0) * 100).toInt()

                chatAdapter.notifyItemChanged(componentList.indexOf(holder))
            }
        }

        downloadReferenceFinishedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                val successful = intent.getBooleanExtra("successful", false)
                val messageUUID = referenceUUIDToMessageUUID[referenceUUID]

                val holder = (messageObjects[messageUUID]?.firstOrNull { it is ReferenceHolder }
                        ?: return) as ReferenceHolder
                holder.isInternetInProgress = false
                holder.isFinished = successful

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
                kentaiClient.directConnectionManager.sendPacket(ViewChatPacketToServer(chatInfo.chatUUID, true))
            }
        }

        setUnreadMessages(kentaiClient.dataBase, chatInfo.chatUUID, 0)

        //Remove notifications related to this chat
        val sharedPreferences = getSharedPreferences(DisplayNotificationReceiver.NOTIFICATION_FILE, Context.MODE_PRIVATE)
        val id = sharedPreferences.getInt(chatInfo.chatUUID.toString(), -1)
        if (id != -1) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)
        }

        kentaiClient.dataBase.execSQL("DELETE FROM notification_messages WHERE chat_uuid = ?", arrayOf(chatInfo.chatUUID.toString()))

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

        kentaiClient.directConnectionManager.sendPacket(ViewChatPacketToServer(chatInfo.chatUUID, true))
    }


    private fun sendMessage() {
        val kentaiClient = applicationContext as KentaiClient
        var current = chatActivityTextInput.text.toString()

        while (current.startsWith('\n')) current = current.substring(1)
        while (current.endsWith('\n')) current = current.substring(0, current.length - 1)

        val written = current

        val chatMessage = ChatMessageText(written, kentaiClient.userUUID, System.currentTimeMillis())
        chatMessage.referenceUUID = UUID.randomUUID()
        val wrapper = ChatMessageWrapper(chatMessage, MessageStatus.WAITING, true, System.currentTimeMillis())
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

    fun addMessages(list: List<ChatMessageWrapper>, bottom: Boolean) {
        val listToAdd = mutableListOf<Any>()
        for (wrapper in list) {
            val associated = createMessageObjects(wrapper)
            messageObjects[wrapper.message.id] = associated

            if (bottom) {
                listToAdd.addAll(associated)
            } else {
                listToAdd.addAll(0, associated)
            }

            referenceUUIDToMessageUUID[wrapper.message.referenceUUID] = wrapper.message.id
        }

        val prevSize = componentList.size

        if (bottom) {
            componentList.addAll(listToAdd)
            chatAdapter.notifyItemRangeInserted(prevSize, listToAdd.size)
        } else {
            componentList.addAll(0, listToAdd)
            chatAdapter.notifyItemRangeInserted(0, listToAdd.size)
        }
    }

    /**
     * Returns the list of the list components that will be added, you have to add the list to the recycler view yourself
     */
    private fun createMessageObjects(wrapper: ChatMessageWrapper): List<Any> {
        val kentaiClient = applicationContext as KentaiClient

        val message = wrapper.message
        if (!message.shouldBeStored()) return emptyList()

        val resultList = mutableListOf<Any>()

        if (message is ChatMessageGroupModification) {
            val groupModification = message.groupModification
            if (groupModification is GroupModificationAddUser) {
                registerGroupStuff(groupModification.userUUID.toUUID())
            }
        }

        if (chatInfo.chatType == ChatType.GROUP && !wrapper.client) {
            resultList += userToUserInfo[message.senderUUID]!!
        }

        resultList += if (wrapper.message.hasReference()) {
            val referenceState = getReferenceState(kentaiClient.dataBase, chatInfo.chatUUID, wrapper.message.referenceUUID)

            if (wrapper.message is ChatMessageVoiceMessage) {
                VoiceReferenceHolder(wrapper, kentaiClient.currentLoadingTable.containsKey(message.referenceUUID), referenceState == UploadState.FINISHED)
            } else {
                ReferenceHolder(wrapper, kentaiClient.currentLoadingTable.containsKey(message.referenceUUID), referenceState == UploadState.FINISHED)
            }
        } else {
            wrapper
        }

        val timeStatusInfo = TimeStatusChatInfo(message.timeSent, wrapper.status, wrapper.client)
        resultList += timeStatusInfo

        return resultList
    }

    fun updateMessageStatus(messageUUID: UUID, status: MessageStatus) {
        val associatedObjects = messageObjects[messageUUID]
                ?: throw IllegalStateException("No chat message found")
        val first = associatedObjects.first { it is ChatMessageWrapper || it is ReferenceHolder }
        val wrapper = (first as? ReferenceHolder)?.chatMessageWrapper ?: first as ChatMessageWrapper
        wrapper.status = status

        val timeStatusInfo = associatedObjects.first { it is TimeStatusChatInfo } as TimeStatusChatInfo
        timeStatusInfo.status = status
        val index = componentList.indexOf(timeStatusInfo)
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

    fun loadMore() {
        val kentaiClient = applicationContext as KentaiClient
        LoadMoreTask({ list ->
            addMessages(list, false)
            if (list.isNotEmpty()) {
                lastMessageTime = list.first().message.timeSent
            }
        }, chatInfo.chatUUID, kentaiClient, lastMessageTime).execute()
    }

    private class LoadMoreTask(val updater: (List<ChatMessageWrapper>) -> (Unit), val chatUUID: UUID, val kentaiClient: KentaiClient, val firstMessageTime: Long) :
            AsyncTask<Unit, Unit, List<ChatMessageWrapper>>() {
        override fun doInBackground(vararg params: Unit?): List<ChatMessageWrapper> =
                load20Messages(kentaiClient, chatUUID, firstMessageTime)

        override fun onPostExecute(result: List<ChatMessageWrapper>) {
            updater.invoke(result)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val kentaiClient = applicationContext as KentaiClient
        when (item.itemId) {
            R.id.chatActivityMenuAccount -> {
                if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
                    val intent = Intent(this@ChatActivity, ContactInfoActivity::class.java)
                    intent.putExtra(KEY_USER_UUID, chatInfo.participants.first { it.receiverUUID != kentaiClient.userUUID }.receiverUUID)
                    this.startActivity(intent)
                } else if (chatInfo.chatType == ChatType.GROUP) {
                    val intent = Intent(this@ChatActivity, GroupInfoActivity::class.java)
                    intent.putExtra(KEY_CHAT_INFO, chatInfo)
                    this.startActivity(intent)
                }
            }
        }
        return super.onOptionsItemSelected(item)
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

            return ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis())
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
                    getReferenceFile(result.message.referenceUUID, FileType.VIDEO, filesDir, this@ChatActivity))
            scrollToBottom()
        }, kentaiClient, uri, text, isGif).execute()
    }

    private class SendVideoTask(val execute: (ChatMessageWrapper) -> Unit, val kentaiClient: KentaiClient, val uri: Uri, val text: String, val isGif: Boolean) : AsyncTask<Unit, Unit, ChatMessageWrapper>() {
        override fun doInBackground(vararg args: Unit): ChatMessageWrapper {
            val referenceUUID = UUID.randomUUID()

            val fileType = if (isGif) FileType.GIF else FileType.VIDEO
            val referenceFile = saveMediaFileInAppStorage(referenceUUID, uri, kentaiClient, fileType)

            val hash = Hashing.sha512().hashBytes(referenceFile.readBytes())
            val message = ChatMessageVideo(hash.toString(), getVideoDuration(referenceFile, kentaiClient), kentaiClient.userUUID, text, System.currentTimeMillis(), isGif)
            message.referenceUUID = referenceUUID
            return ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis())
        }

        override fun onPostExecute(result: ChatMessageWrapper) {
            execute.invoke(result)
        }
    }

    override fun onStop() {
        super.onStop()
        stopRecording(false)
    }

    override fun onResume() {
        super.onResume()

        val kentaiClient = applicationContext as KentaiClient

        if (componentList.isNotEmpty()) {
            val (lastMessageTime: Long, lastMessageUUID: UUID) = if (componentList.any { it is ChatMessageWrapper }) {
                val message = (componentList.last { it is ChatMessageWrapper } as ChatMessageWrapper).message
                message.timeSent to message.id
            } else 0L to UUID.randomUUID()

            val newMessages = readChatMessageWrappers(kentaiClient.dataBase, "chat_uuid = '${chatInfo.chatUUID}' AND time > $firstMessageTime")

            addMessages(newMessages.filter { it.message.id != lastMessageUUID }, false)

            if (onBottom) scrollToBottom()
        }

        registerReceiver(uploadProgressReceiver, IntentFilter(ACTION_UPLOAD_REFERENCE_PROGRESS))
        registerReceiver(uploadReferenceFinishedReceiver, IntentFilter(ACTION_UPLOAD_REFERENCE_FINISHED))
        registerReceiver(downloadProgressReceiver, IntentFilter(ACTION_DOWNLOAD_REFERENCE_PROGRESS))
        registerReceiver(downloadReferenceFinishedReceiver, IntentFilter(ACTION_DOWNLOAD_REFERENCE_FINISHED))
        registerReceiver(uploadReferenceStartedReceiver, IntentFilter(ACTION_UPLOAD_REFERENCE_STARTED))
        registerReceiver(userViewChatReceiver, IntentFilter(ACTION_USER_VIEW_CHAT))
        registerReceiver(directConnectedReceiver, IntentFilter(ACTION_DIRECT_CONNECTION_CONNECTED))

        kentaiClient.directConnectionManager.sendPacket(ViewChatPacketToServer(chatInfo.chatUUID, true))
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

        kentaiClient.directConnectionManager.sendPacket(ViewChatPacketToServer(chatInfo.chatUUID, false))
    }

    private var currentContextSelectedIndex = -1

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        menuInflater.inflate(R.menu.menu_chat_bubble, menu)

        val position = v.tag as Int
        currentContextSelectedIndex = position

        super.onCreateContextMenu(menu, v, menuInfo)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val kentaiClient = applicationContext as KentaiClient

        val index = currentContextSelectedIndex
        when (item.itemId) {
            R.id.menuChatBubbleDelete -> {
                val temp = componentList[index]
                val message = if (temp is ChatMessageWrapper) temp else (temp as? ReferenceHolder)?.chatMessageWrapper
                        ?: throw IllegalStateException()
                deleteMessage(chatInfo.chatUUID, message.message.id, message.message.referenceUUID, kentaiClient.dataBase)

                val messageComponentList = messageObjects[message.message.id]!!
                componentList.removeAll(messageComponentList)
                chatAdapter.notifyDataSetChanged()
                return true
            }
            R.id.menuChatBubbleInfo -> {
                val message = componentList[index] as ChatMessageWrapper

                return true
            }
            R.id.menuChatBubbleCopy -> {
                val message = componentList[index] as ChatMessageWrapper

                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Kentai", message.message.text)
                clipboard.primaryClip = clip
            }
        }

        return super.onContextItemSelected(item)
    }

    private fun startRecording() {
        if (checkRecordingPermission()) {
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

                    val min = secondsRecording % 60
                    val sec = secondsRecording - min * 60

                    chatActivityRecordingText.text = getString(R.string.chat_activity_voice_record, min, sec)

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

                    val message = ChatMessageWrapper(ChatMessageVoiceMessage(secondsRecording, Hashing.sha512().hashBytes(audioFile.readBytes()).toString(), currentAudioUUID!!, kentaiClient.userUUID, "", System.currentTimeMillis()), MessageStatus.WAITING, true, System.currentTimeMillis())
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

    private fun checkRecordingPermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 123)
            return false
        }
        return true
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

    fun setDefaultSubtitle() {
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

        chatAdapter.notifyItemChanged(componentList.indexOf(referenceHolder))

        mediaPlayer.setOnCompletionListener {
            stopAudio(referenceHolder)
        }

        val handler = Handler()
        handler.post(object : Runnable {
            override fun run() {
                if (currentPlaying != referenceHolder) return

                referenceHolder.playProgress = mediaPlayer.currentPosition
                chatAdapter.notifyItemChanged(componentList.indexOf(referenceHolder))

                handler.postDelayed(this, 1000L)
            }
        })
    }

    fun seekAudioChange(referenceHolder: VoiceReferenceHolder, change: Int) {
        if (currentPlaying == referenceHolder) {
            mediaPlayer.seekTo(change)
        }
        referenceHolder.playProgress = change
        chatAdapter.notifyItemChanged(componentList.indexOf(referenceHolder))
    }

    fun stopAudio(referenceHolder: VoiceReferenceHolder) {
        referenceHolder.isPlaying = false
        referenceHolder.playProgress = 0
        chatAdapter.notifyItemChanged(componentList.indexOf(referenceHolder))
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
            uploadReference(this, kentaiClient.dataBase, chatInfo.chatUUID, referenceUUID, referenceFile, fileType)
        } else {
            downloadReference(this, kentaiClient.dataBase, chatInfo.chatUUID, referenceUUID, fileType, chatInfo.chatType, hash, kentaiClient.privateMessageKey!!)
        }

        chatAdapter.notifyItemChanged(adapterPosition)
    }

}