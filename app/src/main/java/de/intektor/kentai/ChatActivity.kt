package de.intektor.kentai

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.provider.MediaStore
import android.support.v13.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.*
import android.widget.TextView
import android.widget.Toast
import com.google.common.hash.Hashing
import de.intektor.kentai.kentai.android.saveImageExternalKentai
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.chat.adapter.AbstractViewHolder
import de.intektor.kentai.kentai.chat.adapter.ChatAdapter
import de.intektor.kentai.kentai.chat.adapter.TimeStatusChatInfo
import de.intektor.kentai.kentai.chat.adapter.UsernameChatInfo
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.direct.connection.DirectConnectionManager
import de.intektor.kentai.kentai.firebase.DisplayNotificationReceiver
import de.intektor.kentai.kentai.image_text
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai.kentai.references.uploadAudio
import de.intektor.kentai.kentai.references.uploadImage
import de.intektor.kentai.kentai.references.uploadVideo
import de.intektor.kentai.kentai.video_text
import de.intektor.kentai_http_common.chat.*
import de.intektor.kentai_http_common.chat.group_modification.ChatMessageGroupModification
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationAddUser
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.tcp.client_to_server.TypingPacketToServer
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

    private lateinit var chatAdapter: ChatAdapter

    lateinit var chatInfo: ChatInfo

    private val componentList: MutableList<Any> = mutableListOf()

    private val messageMap: HashMap<UUID, ChatMessageWrapper> = HashMap()
    private val messageInfoMap: HashMap<UUID, TimeStatusChatInfo> = HashMap()
    private val colorMap: HashMap<UUID, UsernameChatInfo> = HashMap()

    /**
     * A map containing every element that belongs to a message
     */
    private val messageComponentMap: HashMap<UUID, List<Any>> = HashMap()

    var onTop: Boolean = false
    var onBottom: Boolean = false

    var currentlyLoadingMore = false

    private var firstMessageTime = System.currentTimeMillis()

    private val contactMap: MutableMap<UUID, Contact> = HashMap()

    private var hasPermissionToRecordAudio = false

    private var isRecording = false
    private var currentAudioUUID: UUID? = null
    private var mediaRecorder: MediaRecorder? = null
    private var secondsRecording: Int = 0

    private lateinit var uploadReferenceFinishedReceiver: BroadcastReceiver
    private lateinit var uploadProgressReceiver: BroadcastReceiver

    private lateinit var downloadReferenceFinishedReceiver: BroadcastReceiver
    private lateinit var downloadProgressReceiver: BroadcastReceiver

    private lateinit var uploadReferenceStartedReceiver: BroadcastReceiver

    private val codeTakePhoto = 0
    private val codePickPhoto = 1
    private val codeTakeVideo = 2
    private val codePickVideo = 3

    private var lastTimeSentTypingMessage = 0L
    private var lastUserTyping: UUID = UUID.randomUUID()
    private var lastUserTypingTime = 0L

    private var takePhotoUri: Uri? = null
    private var takeVideoUri: Uri? = null

    companion object {
        private val dateFormatTY = DateFormat.getTimeInstance(DateFormat.SHORT)
        private val dateFormatAnytime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        sendMessageButton.setOnClickListener({
            sendMessage()
        })

        sendMessageButton.setOnTouchListener { _: View, motionEvent: MotionEvent ->
            if (messageBox.text.isBlank()) {
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    startRecording()
                } else if (motionEvent.action == MotionEvent.ACTION_UP) {
                    val outRect = Rect()
                    val location = IntArray(2)
                    sendMessageButton.getDrawingRect(outRect)
                    sendMessageButton.getLocationOnScreen(location)
                    stopRecording(outRect.contains(motionEvent.x.toInt(), motionEvent.y.toInt()))
                }
            } else {
                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                    sendMessage()
                    messageBox.setText("", TextView.BufferType.EDITABLE)
                }
            }
            true
        }

        val lM = LinearLayoutManager(this)
        msgListView.layoutManager = lM
        lM.stackFromEnd = true

        chatInfo = intent.getParcelableExtra("chatInfo")

        supportActionBar?.title = chatInfo.chatName

        for ((receiverUUID) in chatInfo.participants) {
            KentaiClient.INSTANCE.dataBase.rawQuery("SELECT contacts.username, user_color_table.color, contacts.user_uuid, contacts.alias FROM user_color_table LEFT JOIN contacts ON contacts.user_uuid = user_color_table.user_uuid WHERE user_color_table.user_uuid = ?", arrayOf(receiverUUID.toString())).use { query ->
                query.moveToNext()
                val username = query.getString(0)
                val color = query.getString(1)
                val userUUID = query.getString(2).toUUID()
                val alias = query.getString(3)
                colorMap.put(receiverUUID, UsernameChatInfo(username, color))
                contactMap.put(userUUID, Contact(username, alias, userUUID, null))
            }
        }

        if (!chatInfo.isUserParticipant(KentaiClient.INSTANCE.userUUID) || !chatInfo.userProfile(KentaiClient.INSTANCE.userUUID).isActive) {
            messageBox.isEnabled = false
            messageBox.setText(R.string.chat_group_no_member)
            sendMessageButton.isEnabled = false
        }

        val messages = load20Messages()

        addMessages(messages, false)

        chatAdapter = ChatAdapter(componentList, chatInfo, contactMap, this)

        msgListView.adapter = chatAdapter

        msgListView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
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
                                val statement = KentaiClient.INSTANCE.dataBase.compileStatement("INSERT INTO message_status_change (message_uuid, status, time) VALUES(?, ?, ?)")
                                statement.bindString(1, it.message.id.toString())
                                statement.bindLong(2, it.status.ordinal.toLong())
                                statement.bindLong(3, System.currentTimeMillis())
                                statement.execute()
                                for (s in chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }) {
                                    val wrapper = ChatMessageWrapper(ChatMessageStatusChange(chatInfo.chatUUID, it.message.id, MessageStatus.SEEN, System.currentTimeMillis(),
                                            KentaiClient.INSTANCE.userUUID, System.currentTimeMillis()), MessageStatus.WAITING, true, System.currentTimeMillis())
                                    wrapper.message.referenceUUID = UUID.randomUUID()
                                    sendMessageToServer(this@ChatActivity, PendingMessage(wrapper, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }))
                                }
                                recyclerView.adapter.notifyDataSetChanged()
                            }
                        }
                if (onTop && !currentlyLoadingMore) {
                    loadMore()
                }
            }
        })

        chatLoadMoreProgressBar.visibility = View.GONE

        messageBox.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(newText: CharSequence, p1: Int, p2: Int, p3: Int) {
                if (newText.isBlank()) {
                    sendMessageButton.setImageResource(android.R.drawable.ic_btn_speak_now)
                } else {
                    sendMessageButton.setImageResource(android.R.drawable.ic_menu_send)
                }
                if (System.currentTimeMillis() - 5000 >= lastTimeSentTypingMessage) {
                    lastTimeSentTypingMessage = System.currentTimeMillis()
                    if (DirectConnectionManager.isConnected) {
                        DirectConnectionManager.sendPacket(TypingPacketToServer(chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }.map { it.receiverUUID }, chatInfo.chatUUID))
                    }
                }
            }
        })

        uploadProgressReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                broadcast("de.intektor.kentai.uploadProgress", intent)
            }
        }

        uploadReferenceFinishedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                broadcast("de.intektor.kentai.uploadReferenceFinished", intent)
            }
        }

        downloadProgressReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                broadcast("de.intektor.kentai.downloadProgress", intent)
            }
        }

        downloadReferenceFinishedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                broadcast("de.intektor.kentai.downloadReferenceFinished", intent)
            }
        }

        uploadReferenceStartedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                broadcast("de.intektor.kentai.uploadReferenceStarted", intent)
            }
        }

        setUnreadMessages(KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, 0)

        //Remove notifications related to this chat
        val sharedPreferences = getSharedPreferences(DisplayNotificationReceiver.NOTIFICATION_FILE, Context.MODE_PRIVATE)
        val id = sharedPreferences.getInt(chatInfo.chatUUID.toString(), -1)
        if (id != -1) {
            val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(id)
        }

        KentaiClient.INSTANCE.dataBase.execSQL("DELETE FROM notification_messages WHERE chat_uuid = ?", arrayOf(chatInfo.chatUUID.toString()))

        val handler = Handler()
        TypingUpdater(handler).run()

        setDefaultSubtitle()

        scrollToBottom()
    }

    private fun sendMessage() {
        var current = messageBox.text.toString()

        while (current.startsWith('\n')) current = current.substring(1)
        while (current.endsWith('\n')) current = current.substring(0, current.length - 1)

        val written = current

        val chatMessage = ChatMessageText(written, KentaiClient.INSTANCE.userUUID, System.currentTimeMillis())
        chatMessage.referenceUUID = UUID.randomUUID()
        val wrapper = ChatMessageWrapper(chatMessage, MessageStatus.WAITING, true, System.currentTimeMillis())
        val wrapperCopy = wrapper.copy(message = ChatMessageText(written, KentaiClient.INSTANCE.userUUID, System.currentTimeMillis()))
        wrapperCopy.message.id = wrapper.message.id
        wrapperCopy.message.referenceUUID = wrapper.message.referenceUUID
        wrapperCopy.message.timeSent = wrapper.message.timeSent
        addMessages(wrapperCopy, false)
        chatAdapter.notifyDataSetChanged()

        if (onBottom) scrollToBottom()

        sendMessageToServer(this, PendingMessage(wrapper, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }))
    }

    fun addMessages(message: ChatMessageWrapper, front: Boolean) {
        addMessages(listOf(message), front)
    }

    fun addMessages(messages: List<ChatMessageWrapper>, front: Boolean) {
        val totalList = mutableListOf<Any>()
        messages.forEach {
            val elements = addMessage(it)
            totalList.addAll(elements)
            messageComponentMap.put(it.message.id, elements)
        }

        if (!front) {
            val oldSize = componentList.size
            componentList.addAll(totalList)
            msgListView?.adapter?.notifyItemRangeInserted(oldSize, totalList.size)
        } else {
            componentList.addAll(0, totalList)
            msgListView?.adapter?.notifyItemRangeInserted(0, totalList.size)
        }
    }

    /**
     * Returns the list of the list components that will be added, you have to add the list to the recycler view yourself
     */
    private fun addMessage(wrapper: ChatMessageWrapper): List<Any> {
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
            resultList.add(colorMap[message.senderUUID]!!)
        }

        resultList.add(wrapper)
        val timeStatusInfo = TimeStatusChatInfo(message.timeSent, wrapper.status, wrapper.client)
        resultList.add(timeStatusInfo)

        messageInfoMap.put(wrapper.message.id, timeStatusInfo)

        return resultList
    }

    fun updateMessageStatus(messageUUID: UUID, status: MessageStatus) {
        val wrapper = messageMap[messageUUID]
        wrapper?.status = status
        messageInfoMap[messageUUID]?.status = status
        chatAdapter.notifyDataSetChanged()
    }

    /**
     * Puts all information needed for group message display like color or the user himself in the expected arrays
     */
    private fun registerGroupStuff(userUUID: UUID) {
        KentaiClient.INSTANCE.dataBase.rawQuery("SELECT contacts.username, user_color_table.color, contacts.alias FROM user_color_table LEFT JOIN contacts ON contacts.user_uuid = user_color_table.user_uuid WHERE user_color_table.user_uuid = ?", arrayOf(userUUID.toString())).use { query ->
            query.moveToNext()
            val username = query.getString(0)
            val color = query.getString(1)
            val alias = query.getString(2)
            colorMap.put(userUUID, UsernameChatInfo(username, color))
            contactMap.put(userUUID, Contact(username, alias, userUUID, null))
        }
    }

    fun scrollToBottom() {
        msgListView.smoothScrollToPosition(componentList.size)
    }

    private fun load20Messages(): List<ChatMessageWrapper> {
        val list = readChatMessageWrappers(KentaiClient.INSTANCE.dataBase, "chat_uuid = '${chatInfo.chatUUID}' AND time < $firstMessageTime", null, "time DESC", 20)

        if (list.isNotEmpty()) {
            firstMessageTime = list.last().message.timeSent
        }
        return list.reversed()
    }

    fun loadMore() {
        object : AsyncTask<Unit, Unit, List<ChatMessageWrapper>>() {
            override fun onPreExecute() {
                super.onPreExecute()
                chatLoadMoreProgressBar.visibility = View.VISIBLE
                currentlyLoadingMore = true
            }

            override fun doInBackground(vararg p0: Unit?): List<ChatMessageWrapper> {
                Thread.sleep(500)
                return load20Messages()
            }

            override fun onPostExecute(result: List<ChatMessageWrapper>) {
                super.onPostExecute(result)
                addMessages(result, true)
                chatLoadMoreProgressBar.visibility = View.GONE
                currentlyLoadingMore = false
            }
        }.execute()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_chat_info -> {
                if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
                    val intent = Intent(this@ChatActivity, ContactInfoActivity::class.java)
                    intent.putExtra("userUUID", chatInfo.participants.first { it.receiverUUID != KentaiClient.INSTANCE.userUUID }.receiverUUID)
                    this.startActivity(intent)
                    return false
                } else if (chatInfo.chatType == ChatType.GROUP) {
                    val intent = Intent(this@ChatActivity, GroupInfoActivity::class.java)
                    intent.putExtra("chatInfo", chatInfo)
                    this.startActivity(intent)
                    return false
                }
            }
            R.id.actionChatActivityPhotoFromGallery -> {
                if (checkStoragePermission()) {
                    val pickPhoto = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    startActivityForResult(pickPhoto, codePickPhoto)
                }
            }
            R.id.actionChatActivityPhotoTakeNow -> {
                if (checkStoragePermission()) {
                    val values = ContentValues()
                    values.put(MediaStore.Images.Media.TITLE, "An image taken by the Kentai Application!")
                    values.put(MediaStore.Images.Media.DESCRIPTION, "")

                    takePhotoUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                    val takePhoto = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    takePhoto.putExtra(MediaStore.EXTRA_OUTPUT, takePhotoUri)
                    startActivityForResult(takePhoto, codeTakePhoto)
                }
            }
            R.id.actionChatActivityVideoFromGallery -> {
                if (checkStoragePermission()) {
                    val pickVideo = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    pickVideo.type = "video/*"
                    startActivityForResult(pickVideo, codePickVideo)
                }
            }
            R.id.actionChatActivityVideoTakeNow -> {
                if (checkStoragePermission()) {
                    val values = ContentValues()

                    takeVideoUri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)

                    val takeVideo = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                    takeVideo.putExtra(MediaStore.EXTRA_OUTPUT, takeVideoUri)
                    startActivityForResult(takeVideo, codeTakeVideo)
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            codePickPhoto -> {
                if (resultCode == Activity.RESULT_OK) {
                    val image = data?.data
                    if (image != null) {
                        sendPhoto(image)
                    }
                }
            }
            codeTakePhoto -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (takePhotoUri != null) {
                        sendPhoto(takePhotoUri!!)
                    } else {
                        Toast.makeText(this@ChatActivity, "Couldn't fetch photo! This is an error! Please submit a bug report", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            codePickVideo -> {
                if (resultCode == Activity.RESULT_OK) {
                    val video = data?.data
                    if (video != null) {
                        sendVideo(video)
                    }
                }
            }
            codeTakeVideo -> {
                if (resultCode == Activity.RESULT_OK) {
                    if (takeVideoUri != null) {
                        sendVideo(takeVideoUri!!)
                    } else {
                        Toast.makeText(this@ChatActivity, "Couldn't fetch video! This is an error! Please submit a bug report", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun checkStoragePermission(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE), 123)
            return false
        }
        return true
    }

    private fun sendPhoto(uri: Uri) {
        object : AsyncTask<Unit, Unit, ChatMessageWrapper>() {
            override fun doInBackground(vararg p0: Unit?): ChatMessageWrapper {
                val referenceUUID = UUID.randomUUID()

                val bitmap = BitmapFactory.decodeFile(getPath(uri))

                val byteOut = ByteArrayOutputStream()

                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, byteOut)

                val referenceFile = getReferenceFile(chatInfo.chatUUID, referenceUUID, FileType.IMAGE, filesDir, this@ChatActivity)
                byteOut.writeTo(referenceFile.outputStream())

                saveImageExternalKentai(referenceUUID.toString(), bitmap, this@ChatActivity)

                val hash = Hashing.sha512().hashBytes(byteOut.toByteArray()).toString()
                val message = ChatMessageImage(hash, KentaiClient.INSTANCE.userUUID, image_text, System.currentTimeMillis())
                message.referenceUUID = referenceUUID

                return ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis())
            }

            override fun onPostExecute(result: ChatMessageWrapper) {
                super.onPostExecute(result)
                addMessages(result, false)
                sendMessageToServer(this@ChatActivity, PendingMessage(result, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }))
                uploadImage(this@ChatActivity, KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, result.message.referenceUUID,
                        getReferenceFile(chatInfo.chatUUID, result.message.referenceUUID, FileType.IMAGE, filesDir, this@ChatActivity))
                scrollToBottom()
            }
        }.execute()
    }

    private fun sendVideo(uri: Uri) {
        object : AsyncTask<Unit, Unit, ChatMessageWrapper>() {
            override fun doInBackground(vararg p0: Unit?): ChatMessageWrapper {
                val referenceUUID = UUID.randomUUID()

                val referenceFile = getReferenceFile(chatInfo.chatUUID, referenceUUID, FileType.VIDEO, filesDir, this@ChatActivity)

                File(getPath(uri)).inputStream().use { fileIn ->
                    fileIn.copyTo(referenceFile.outputStream())
                }

                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(this@ChatActivity, Uri.fromFile(referenceFile))
                val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                retriever.release()

                val hash = Hashing.sha512().hashBytes(referenceFile.readBytes())
                val message = ChatMessageVideo(hash.toString(), (time.toLong() / 1000).toInt(), KentaiClient.INSTANCE.userUUID, video_text, System.currentTimeMillis())
                message.referenceUUID = referenceUUID
                return ChatMessageWrapper(message, MessageStatus.WAITING, true, System.currentTimeMillis())
            }

            override fun onPostExecute(result: ChatMessageWrapper) {
                super.onPostExecute(result)
                addMessages(result, false)
                sendMessageToServer(this@ChatActivity, PendingMessage(result, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }))
                uploadVideo(this@ChatActivity, KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, result.message.referenceUUID,
                        getReferenceFile(chatInfo.chatUUID, result.message.referenceUUID, FileType.VIDEO, filesDir, this@ChatActivity))
                scrollToBottom()
            }
        }.execute()
    }

    /**
     * Only works for images selected from the gallery
     */
    fun getPath(uri: Uri): String {
        val proj = arrayOf(MediaStore.Images.Media.DATA)
        val cursor = managedQuery(uri, proj, null, null, null)
        val columnIndex = cursor
                .getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        cursor.moveToFirst()
        return cursor.getString(columnIndex)
    }

    override fun onStop() {
        super.onStop()
        stopRecording(false)
    }

    override fun onResume() {
        super.onResume()

        if (componentList.isNotEmpty()) {
            val (lastMessageTime: Long, lastMessageUUID: UUID) = if (componentList.any { it is ChatMessageWrapper }) {
                val message = (componentList.last { it is ChatMessageWrapper } as ChatMessageWrapper).message
                message.timeSent to message.id
            } else 0L to UUID.randomUUID()

            val newMessages = readChatMessageWrappers(KentaiClient.INSTANCE.dataBase, "chat_uuid = '${chatInfo.chatUUID}' AND time > $lastMessageTime")

            addMessages(newMessages.filter { it.message.id != lastMessageUUID }, false)

            if (onBottom) scrollToBottom()
        }

        registerReceiver(uploadProgressReceiver, IntentFilter("de.intektor.kentai.uploadProgress"))
        registerReceiver(uploadReferenceFinishedReceiver, IntentFilter("de.intektor.kentai.uploadReferenceFinished"))
        registerReceiver(downloadProgressReceiver, IntentFilter("de.intektor.kentai.downloadReferenceFinished"))
        registerReceiver(downloadReferenceFinishedReceiver, IntentFilter("de.intektor.kentai.downloadReferenceFinished"))
        registerReceiver(uploadReferenceStartedReceiver, IntentFilter("de.intektor.kentai.uploadReferenceStarted"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(uploadProgressReceiver)
        unregisterReceiver(uploadReferenceFinishedReceiver)
        unregisterReceiver(downloadProgressReceiver)
        unregisterReceiver(downloadReferenceFinishedReceiver)
        unregisterReceiver(uploadReferenceStartedReceiver)
    }

    private var currentContextSelectedIndex = -1

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        menuInflater.inflate(R.menu.menu_chat_bubble, menu)

        val position = v.tag as Int
        currentContextSelectedIndex = position

        super.onCreateContextMenu(menu, v, menuInfo)
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val index = currentContextSelectedIndex
        when (item.itemId) {
            R.id.menuChatBubbleDelete -> {
                val message = componentList[index] as ChatMessageWrapper
                deleteMessage(chatInfo.chatUUID, message.message.id, KentaiClient.INSTANCE.dataBase)

                val messageComponentList = messageComponentMap[message.message.id]!!
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

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            200 -> {
                hasPermissionToRecordAudio = grantResults[0] == PackageManager.PERMISSION_GRANTED
            }
        }
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
            mediaRecorder!!.setOutputFile(getReferenceFile(chatInfo.chatUUID, currentAudioUUID!!, FileType.AUDIO, filesDir, this@ChatActivity).path)
            mediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)

            try {
                mediaRecorder!!.prepare()
            } catch (t: Throwable) {
                Toast.makeText(this@ChatActivity, "Recording failed", Toast.LENGTH_LONG).show()
            }

            recordingTimeView.visibility = View.VISIBLE

            messageBox.visibility = View.GONE

            val handler = Handler()
            val updater = RecordTextViewUpdater(handler, currentAudioUUID!!)
            handler.post(updater)

            mediaRecorder!!.start()
        }
    }

    private fun stopRecording(stillOnButton: Boolean) {
        if (isRecording) {
            isRecording = false

            recordingTimeView.visibility = View.GONE
            messageBox.visibility = View.VISIBLE

            try {
                mediaRecorder!!.stop()
                mediaRecorder!!.release()
                mediaRecorder = null

                if (secondsRecording > 0 && stillOnButton) {
                    val audioFile = getReferenceFile(chatInfo.chatUUID, currentAudioUUID!!, FileType.AUDIO, filesDir, this@ChatActivity)

                    val message = ChatMessageWrapper(ChatMessageVoiceMessage(secondsRecording, Hashing.sha512().hashBytes(audioFile.readBytes()).toString(), currentAudioUUID!!, KentaiClient.INSTANCE.userUUID, "", System.currentTimeMillis()), MessageStatus.WAITING, true, System.currentTimeMillis())
                    sendMessageToServer(this, PendingMessage(
                            message,
                            chatInfo.chatUUID, chatInfo.participants.filter { it.isActive && it.receiverUUID != KentaiClient.INSTANCE.userUUID }
                    ))
                    addMessages(message, false)
                    uploadAudio(this, KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, currentAudioUUID!!, audioFile)

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

    inner class RecordTextViewUpdater(private val handler: Handler, private val audioUUID: UUID) : Runnable {
        override fun run() {
            if (isRecording && currentAudioUUID == audioUUID) {
                secondsRecording++

                var s = secondsRecording
                var m = 0
                while (s >= 60) {
                    s -= 60
                    m++
                }

                recordingTimeView.text = String.format("%02d:%02d", m, s)
                handler.postDelayed(this, 1000)
            }
        }
    }

    private fun broadcast(target: String, intent: Intent) {
        (0 until msgListView.adapter.itemCount).forEach {
            val childAt = msgListView.getChildAt(it)
            if (childAt != null) {
                val childViewHolder = msgListView.getChildViewHolder(childAt)
                if (childViewHolder != null) {
                    if (childViewHolder is AbstractViewHolder) {
                        childViewHolder.broadcast(target, intent)
                    }
                }
            }
        }
    }

    fun userTyping(userUUID: UUID) {
        lastUserTyping = userUUID
        lastUserTypingTime = System.currentTimeMillis()
        if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
            supportActionBar?.subtitle = getString(R.string.chat_user_typing)
        } else if (chatInfo.chatType == ChatType.GROUP) {
            supportActionBar?.subtitle = getString(R.string.chat_user_typing_group, contactMap[userUUID]?.name ?: "???")
        }
    }

    fun userStatusChange(userChange: UserChange) {
        if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
            val partner = chatInfo.participants.first { it.receiverUUID != KentaiClient.INSTANCE.userUUID }
            if (partner.receiverUUID == userChange.userUUID) {
                setDefaultSubtitle()
            }
        }
    }

    fun connectionClosed() {
        supportActionBar!!.subtitle = null
    }

    fun setDefaultSubtitle() {
        if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
            val partner = chatInfo.participants.first { it.receiverUUID != KentaiClient.INSTANCE.userUUID }
            if (KentaiClient.INSTANCE.userStatusMap.containsKey(partner.receiverUUID)) {
                val userChange = KentaiClient.INSTANCE.userStatusMap[partner.receiverUUID]!!
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
            if (System.currentTimeMillis() - 5000 >= lastUserTypingTime) {
                setDefaultSubtitle()
            }
            handler.postDelayed(this, 7000)
        }
    }
}