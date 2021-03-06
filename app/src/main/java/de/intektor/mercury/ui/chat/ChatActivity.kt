package de.intektor.mercury.ui.chat

import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.*
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import com.google.common.hash.Hashing
import com.squareup.picasso.Picasso
import com.squareup.picasso.Target
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.action.ActionMessageStatusChange
import de.intektor.mercury.action.chat.ActionChatMessageNotification
import de.intektor.mercury.action.chat.ActionChatMessageReceived
import de.intektor.mercury.action.chat.ActionUserViewChat
import de.intektor.mercury.action.group.ActionGroupModificationReceived
import de.intektor.mercury.action.reference.*
import de.intektor.mercury.action.user.ActionTyping
import de.intektor.mercury.action.user.ActionUserStatusChange
import de.intektor.mercury.android.*
import de.intektor.mercury.chat.*
import de.intektor.mercury.chat.adapter.*
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.chat.model.ChatReceiver
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.client.getBackgroundChatFile
import de.intektor.mercury.client.resetBackgroundImage
import de.intektor.mercury.client.setBackgroundImage
import de.intektor.mercury.connection.DirectConnectionService
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.io.download.IOService
import de.intektor.mercury.media.*
import de.intektor.mercury.reference.ReferenceUtil
import de.intektor.mercury.task.DeleteMessagesTask
import de.intektor.mercury.task.PushNotificationUtil
import de.intektor.mercury.task.ReferenceState
import de.intektor.mercury.ui.ContactInfoActivity
import de.intektor.mercury.ui.PickGalleryActivity
import de.intektor.mercury.ui.SendMediaActivity
import de.intektor.mercury.ui.chat.adapter.chat.ChatAdapter
import de.intektor.mercury.ui.chat.adapter.chat.DateViewHolder
import de.intektor.mercury.ui.chat.adapter.chat.HeaderItemDecoration
import de.intektor.mercury.ui.chat.adapter.viewing.UserState
import de.intektor.mercury.ui.chat.adapter.viewing.ViewingAdapter
import de.intektor.mercury.ui.chat.adapter.viewing.ViewingUser
import de.intektor.mercury.ui.group_info_activity.GroupInfoActivity
import de.intektor.mercury.util.ACTION_DIRECT_CONNECTION_CONNECTED
import de.intektor.mercury.util.KEY_CHAT_INFO
import de.intektor.mercury.util.KEY_USER_UUID
import de.intektor.mercury.util.ProfilePictureUtil
import de.intektor.mercury_common.chat.*
import de.intektor.mercury_common.chat.data.MessageReference
import de.intektor.mercury_common.chat.data.MessageStatusUpdate
import de.intektor.mercury_common.chat.data.MessageText
import de.intektor.mercury_common.chat.data.MessageVoiceMessage
import de.intektor.mercury_common.chat.data.group_modification.GroupModificationChangeName
import de.intektor.mercury_common.tcp.client_to_server.TypingPacketToServer
import de.intektor.mercury_common.tcp.client_to_server.ViewChatPacketToServer
import de.intektor.mercury_common.tcp.server_to_client.Status
import de.intektor.mercury_common.tcp.server_to_client.UserChange
import de.intektor.mercury_common.users.ProfilePictureType
import de.intektor.mercury_common.util.generateAESKey
import de.intektor.mercury_common.util.generateInitVector
import kotlinx.android.synthetic.main.activity_chat.*
import java.io.File
import java.text.DateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class ChatActivity : AppCompatActivity() {

    lateinit var chatInfo: ChatInfo
        private set

    private val contactMap: MutableMap<UUID, Contact> = HashMap()

    private val uploadProgressReceiver = UploadProgressReceiver()
    private val uploadReferenceFinishedReceiver = UploadReferenceFinishedReceiver()
    private val downloadProgressReceiver = DownloadProgressReceiver()
    private val downloadReferenceFinishedReceiver = DownloadReferenceFinishedReceiver()
    private val uploadReferenceStartedReceiver = UploadReferenceStartedReceiver()
    private val userViewChatReceiver = UserViewChatReceiver()
    private val directConnectedReceiver = DirectConnectedReceiver()
    private val updateProfilePictureReceiver = UploadProfilePictureReceiver()
    private val chatMessageReceiver = ChatMessageReceiver()
    private val messageStatusChangeReceiver = MessageStatusChangeReceiver()
    private val chatNotificationReceiver = ChatNotificationReceiver()

    private val typingReceiver: BroadcastReceiver = TypingReceiver()
    private val userStatusChangeReceiver: BroadcastReceiver = UserStatusChangeReceiver()

    private val groupModificationReceiver: BroadcastReceiver = GroupModificationReceiver()

    private var lastTimeSentTypingMessage = 0L

    private val userToUserInfo = mutableMapOf<UUID, UsernameChatInfo>()

    private val messageObjects = mutableMapOf<UUID, List<ChatAdapter.ChatAdapterWrapper<*>>>()

    private val referenceUUIDToMessageUUID = mutableMapOf<UUID, UUID>()

    private val componentList = mutableListOf<ChatAdapter.ChatAdapterWrapper<*>>()

    private lateinit var chatAdapter: ChatAdapter

    private lateinit var viewingUsersAdapter: ViewingAdapter
    private val viewingUsersList = mutableListOf<ViewingUser>()

    private var onTop = false
    private var onBottom = false

    private var isRecording = false
    private var mediaRecorder: MediaRecorder? = null

    private var currentlyLoadingMore = false

    private var secondsRecording = 0

    private var currentAudioUUID: UUID? = null
    private var currentPhotoUUID: UUID? = null

    private val mediaPlayer = MediaPlayer()
    private var currentPlaying: VoiceReferenceHolder? = null

    private val userTypingTime = mutableMapOf<UUID, Long>()

    private var upToDate = true

    private var actionModeEdit: ActionMode? = null
    private var selectedItems: MutableSet<ChatAdapter.ChatAdapterWrapper<*>> = hashSetOf()

    val isInEditMode: Boolean
        get() = actionModeEdit != null


    private var selectedWithMedia = 0

    companion object {
        private val dateFormatTY = DateFormat.getTimeInstance(DateFormat.SHORT)
        private val dateFormatAnytime = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

        private const val PERMISSION_REQUEST_EXTERNAL_STORAGE = 1000
        private const val PERMISSION_REQUEST_CAMERA = 1001
        private const val PERMISSION_REQUEST_AUDIO = 1002

        private const val ACTION_PICK_MEDIA = 1101
        private const val ACTION_SEND_MEDIA = 1103
        private const val ACTION_PICK_BACKGROUND_IMAGE = 1104
        private const val ACTION_TAKE_IMAGE = 1105

        private const val EXTRA_CHAT_INFO = "de.intektor.mercury.extra.EXTRA_CHAT_INFO"
        private const val EXTRA_MESSAGE_UUID = "de.intektor.mercury.extra.MESSAGE_UUID"

        fun createIntent(context: Context, chatInfo: ChatInfo, messageUUID: UUID? = null): Intent =
                Intent()
                        .setComponent(ComponentName(context, ChatActivity::class.java))
                        .putExtra(EXTRA_CHAT_INFO, chatInfo)
                        .putExtra(EXTRA_MESSAGE_UUID, messageUUID)

        private fun getData(intent: Intent): Holder {
            val chatInfo = intent.getChatInfoExtra(EXTRA_CHAT_INFO)
            val messageUUID = intent.getSerializableExtra(EXTRA_MESSAGE_UUID) as? UUID

            return Holder(chatInfo, messageUUID)
        }

        fun launch(context: Context, chatInfo: ChatInfo, messageUUID: UUID? = null) {
            context.startActivity(createIntent(context, chatInfo, messageUUID))
        }

        private data class Holder(val chatInfo: ChatInfo, val messageUUID: UUID?)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_chat)

        val (chatInfo, _) = getData(intent)
        this.chatInfo = chatInfo

//        if (messageUUID != null) {
//            val message = getChatMessages(this, mercuryClient.dataBase, "message_uuid = ?", arrayOf(messageUUID.toString())).first()
//            firstMessageTime = message.chatMessageInfo.message.messageCore.timeCreated
//
//            upToDate = false
//        }
//
        val chatAdapterLayoutManager = LinearLayoutManager(this)
        chatActivityMessageList.layoutManager = chatAdapterLayoutManager
        chatAdapterLayoutManager.stackFromEnd = true

        val actionBar = supportActionBar

        actionBar?.title = ChatUtil.getChatName(this, mercuryClient().dataBase, chatInfo.chatUUID)

//        if (chatInfo.chatType.isGroup()) {
//            for ((receiverUUID) in chatInfo.participants) {
//                mercuryClient.dataBase.rawQuery("SELECT contacts.username, user_color_table.color, contacts.user_uuid, contacts.alias FROM user_color_table LEFT JOIN contacts ON contacts.user_uuid = user_color_table.user_uuid WHERE user_color_table.user_uuid = ?", arrayOf(receiverUUID.toString())).use { query ->
//                    query.moveToNext()
//                    val username = query.getString(0)
//                    val color = query.getString(1)
//                    val userUUID = query.getString(2).toUUID()
//                    val alias = query.getString(3)
//                    userToUserInfo[receiverUUID] = UsernameChatInfo(username, color)
//                    contactMap.put(userUUID, Contact(username, alias, userUUID, null))
//                }
//            }
//        } else {
        for ((receiverUUID) in chatInfo.participants) {
            val contact = getContact(mercuryClient().dataBase, receiverUUID)
            contactMap[receiverUUID] = contact
        }
//        }

        val clientUUID = ClientPreferences.getClientUUID(this)

        viewingUsersAdapter = ViewingAdapter(viewingUsersList)
        chatActivityViewingUsersList.adapter = viewingUsersAdapter
        chatActivityViewingUsersList.layoutManager = LinearLayoutManager(this)

        chatAdapter = ChatAdapter(componentList, chatInfo, contactMap, this)

        chatActivityMessageList.adapter = chatAdapter

        chatActivityMessageList.addOnScrollListener(ChatListScrollListener())

        loadMoreMessagesTop(false, LoadType.TOP)

        activity_chat_et_input.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(newText: Editable) {
                applySendMessageLogic(newText)
            }

            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
            }

            override fun onTextChanged(newText: CharSequence, p1: Int, p2: Int, p3: Int) {
                if (System.currentTimeMillis() - 5000 >= lastTimeSentTypingMessage) {
                    lastTimeSentTypingMessage = System.currentTimeMillis()
                    DirectConnectionService.ActionSendPacketToServer.launch(this@ChatActivity, TypingPacketToServer(chatInfo.getOthers(clientUUID).map { it.receiverUUID }, chatInfo.chatUUID))
                }
            }
        })

        applySendMessageLogic("")

        val handler = Handler()
        TypingUpdater(handler).run()

        setDefaultSubtitle()

        scrollToBottom()

        activity_chat_cv_pick_media.setOnClickListener {
            if (checkWriteStoragePermission(this, PERMISSION_REQUEST_EXTERNAL_STORAGE)) {
                if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.preference_use_native_media_picker), false)) {
                    if (Build.VERSION.SDK_INT >= 19) {
                        val i = Intent(Intent.ACTION_PICK)
                        i.type = "*/*"
                        i.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                        startActivityForResult(i, ACTION_PICK_MEDIA)
                    } else {
                        Toast.makeText(this, R.string.chat_activity_native_gallery_not_supported, Toast.LENGTH_LONG).show()
                    }
                } else {
                    startActivityForResult(PickGalleryActivity.createIntent(this, chatInfo), ACTION_SEND_MEDIA)
                }
            }
        }

        activity_chat_cl_take_picture.setOnClickListener {
            takePhoto()
        }

        activity_chat_cl_pick_media.setOnClickListener {
            pickMedia()
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
                DateViewHolder(header, chatAdapter).bind(componentList[headerPosition] as ChatAdapter.ChatAdapterWrapper<DateInfo>)
            }

            override fun isHeader(itemPosition: Int): Boolean = componentList[itemPosition].item is DateInfo
        }))

        activity_chat_cl_new_messages_parent.setOnClickListener {
            activity_chat_cv_new_messages_parent.visibility = View.GONE
            scrollToBottom()
        }

        (chatActivityMessageList.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        viewChat(true)

        addInterestedChatUsers(true)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        applyBackgroundImage(getBackgroundChatFile(this, chatInfo.chatUUID))
    }

    private fun applySendMessageLogic(currentText: CharSequence) {
        activity_chat_iv_send_message.setImageResource(if (currentText.isBlank()) R.drawable.baseline_mic_24 else R.drawable.baseline_send_24)

        if (currentText.isBlank()) {
            activity_chat_cl_send_message.setOnTouchListener { _, motionEvent ->
                if (activity_chat_et_input.text.isEmpty()) {
                    if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                        startRecording()
                    } else if (motionEvent.action == MotionEvent.ACTION_UP) {
                        val x = motionEvent.x
                        val y = motionEvent.y

                        val button = activity_chat_cl_send_message

                        val length = (button.right - button.left)

                        val middleX = button.left + length / 2
                        val middleY = button.bottom + (button.top - button.bottom) / 2

                        val dX = x - middleX
                        val dY = y - middleY

                        val stillOnButton = Math.sqrt((dX * dX + dY * dY).toDouble()) <= length
                        stopRecording(stillOnButton)
                    }
                }
                true
            }
        } else {
            activity_chat_cl_send_message.setOnTouchListener(null)
            activity_chat_cl_send_message.setOnClickListener {
                if (activity_chat_et_input.text.isNotEmpty()) {
                    sendMessage()
                    activity_chat_et_input.text.clear()
                }
            }
        }
    }

    private fun sendMessage() {
        val mercuryClient = applicationContext as MercuryClient

        val clientUUID = ClientPreferences.getClientUUID(this)

        var current = activity_chat_et_input.text.toString()

        while (current.startsWith('\n')) current = current.substring(1)
        while (current.endsWith('\n')) current = current.substring(0, current.length - 1)

        val written = current

        val textData = MessageText(written)
        val messageCore = MessageCore(clientUUID, System.currentTimeMillis(), UUID.randomUUID())

        val chatMessage = ChatMessage(messageCore, textData)

        val chatMessageInfo = ChatMessageInfo(chatMessage, true, chatInfo.chatUUID)

        addMessageToBottom(ChatMessageWrapper(chatMessageInfo, MessageStatus.WAITING, System.currentTimeMillis()))
        chatAdapter.notifyDataSetChanged()

        if (onBottom) scrollToBottom()

        sendMessageToServer(this, PendingMessage(chatMessage, chatInfo.chatUUID, chatInfo.getOthers(clientUUID)), mercuryClient.dataBase)
    }

    fun updateMessageStatus(messageUUID: UUID, status: MessageStatus) {
        val associatedObjects = messageObjects[messageUUID] ?: return

        val message = associatedObjects.first { it.item is ChatAdapterMessage }.item as ChatAdapterMessage
        message.message.latestStatus = status

        val infoAdapterItem = associatedObjects.first { it.item is TimeStatusChatInfo }
        val info = infoAdapterItem.item as TimeStatusChatInfo
        info.status = status
        chatAdapter.notifyItemChanged(componentList.indexOf(infoAdapterItem))
    }

    /**
     * Puts all information needed for group message display like color or the user himself in the expected arrays
     */
    private fun registerGroupStuff(userUUID: UUID) {
        val mercuryClient = applicationContext as MercuryClient
        mercuryClient.dataBase.rawQuery("SELECT contacts.username, user_color_table.color, contacts.alias FROM user_color_table LEFT JOIN contacts ON contacts.user_uuid = user_color_table.user_uuid WHERE user_color_table.user_uuid = ?", arrayOf(userUUID.toString())).use { query ->
            query.moveToNext()
            val username = query.getString(0)
            val color = query.getString(1)
            val alias = query.getString(2)
            userToUserInfo[userUUID] = UsernameChatInfo(username, color)
            contactMap.put(userUUID, Contact(username, alias, userUUID, null))
        }
    }

    private fun scrollToBottom() {
        chatActivityMessageList.smoothScrollToPosition(componentList.size)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)

        val clientUUID = ClientPreferences.getClientUUID(this)

        val accountItem = menu.findItem(R.id.chatActivityMenuAccount)
        if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
            ProfilePictureUtil.loadProfilePicture(chatInfo.getOthers(clientUUID).first().receiverUUID, ProfilePictureType.SMALL, object : Target {
                override fun onBitmapFailed(e: Exception?, errorDrawable: Drawable?) {

                }

                override fun onPrepareLoad(placeHolderDrawable: Drawable?) {

                }

                override fun onBitmapLoaded(bitmap: Bitmap, from: Picasso.LoadedFrom?) {
                    accountItem.icon = BitmapDrawable(resources, bitmap)

                }
            })
        }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val clientUUID = ClientPreferences.getClientUUID(this)

        when (item.itemId) {
            R.id.chatActivityMenuAccount -> {
                if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
                    ContactInfoActivity.launch(this, chatInfo.participants.first { it.receiverUUID != clientUUID }.receiverUUID)
                } else if (chatInfo.chatType.isGroup()) {
                    val intent = Intent(this@ChatActivity, GroupInfoActivity::class.java)
                    intent.putExtra(KEY_CHAT_INFO, chatInfo)
                    startActivity(intent)
                }
                return true
            }
            R.id.chatActivityNativeGallery -> {

            }
            R.id.menu_chat_set_background_image -> {
                val i = Intent(Intent.ACTION_PICK)
                i.type = "image/*"
                startActivityForResult(i, ACTION_PICK_BACKGROUND_IMAGE)
                return true
            }
            R.id.menu_chat_reset_background -> {
                resetBackgroundImage(this, chatInfo.chatUUID)

                recreate()
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
                    val (selectedMedia) = ActionPickMedia.getData(data)

                    selectedMedia.forEach { media ->
                        val uri = media.mediaFile.getUri(this)

                        when (media.mediaFile.mediaType) {
                            MediaType.MEDIA_TYPE_IMAGE -> sendImage(uri, media.text)
                            MediaType.MEDIA_TYPE_VIDEO -> sendVideo(uri, media.text, media.isGif)
                        }
                    }
                }
            }
            ACTION_PICK_MEDIA -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val mime = contentResolver.getType(data.data) ?: "unknown"
                    val mediaType = MediaType.getMediaTypeFromMime(mime)

                    if (mediaType != MediaType.MEDIA_TYPE_NONE) {
                        startActivityForResult(SendMediaActivity.createIntent(this,
                                chatInfo,
                                listOf(ExternalStorageFile(data.data, mediaType, System.currentTimeMillis()))),
                                ACTION_SEND_MEDIA)
                        return
                    }
                    Toast.makeText(this, "Looks like something went wrong", Toast.LENGTH_LONG).show()
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
                    val photoUUID = currentPhotoUUID ?: return

                    startActivityForResult(SendMediaActivity.createIntent(this,
                            chatInfo,
                            listOf(ReferenceFile(photoUUID, chatInfo.chatUUID, MediaType.MEDIA_TYPE_IMAGE, System.currentTimeMillis() / 1000))),
                            ACTION_SEND_MEDIA)
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty()) when (requestCode) {
            PERMISSION_REQUEST_EXTERNAL_STORAGE -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    pickMedia()
                }
            }
            PERMISSION_REQUEST_CAMERA -> {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                }
            }
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun takePhoto() {
        if (checkCameraPermission(this, PERMISSION_REQUEST_CAMERA)) {
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                takePictureIntent.resolveActivity(packageManager)?.also {
                    val photoUUID = UUID.randomUUID()

                    currentPhotoUUID = photoUUID

                    val referenceFile = ReferenceUtil.getFileForReference(this, photoUUID)

                    referenceFile.createNewFile()

                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT,
                            FileProvider.getUriForFile(this, "de.intektor.mercury.android.GenericFileProvider", referenceFile))

                    startActivityForResult(takePictureIntent, ACTION_TAKE_IMAGE)
                }
            }
        }
    }

    private fun pickMedia() {
        if (checkWriteStoragePermission(this, PERMISSION_REQUEST_EXTERNAL_STORAGE)) {
            if (PreferenceManager.getDefaultSharedPreferences(this).getBoolean(getString(R.string.preference_use_native_media_picker), false)) {
                if (Build.VERSION.SDK_INT >= 19) {
                    val i = Intent(Intent.ACTION_PICK)
                    i.type = "*/*"
                    i.putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "video/*"))
                    startActivityForResult(i, ACTION_PICK_MEDIA)
                } else {
                    Toast.makeText(this, R.string.chat_activity_native_gallery_not_supported, Toast.LENGTH_LONG).show()
                }
            } else {
                startActivityForResult(PickGalleryActivity.createIntent(this, chatInfo), ACTION_SEND_MEDIA)
            }
        }
    }

    private fun sendImage(uri: Uri, text: String) {
        val mercuryClient = applicationContext as MercuryClient

        MediaSendUtil.saveImageAndCreateMessage(mercuryClient, listOf(chatInfo.chatUUID), uri, text) { result ->
            if (result == null) {
                Toast.makeText(this, "Looks like something went wrong trying to create an image message. This error has been reported to the developer", Toast.LENGTH_LONG).show()
                return@saveImageAndCreateMessage
            }

            //We only send to this chat so only one message is present
            val messageInfo = result[0]

            val data = messageInfo.message.messageData as MessageReference
            mercuryClient.currentLoadingTable[data.reference] = 0.0

            addMessageToBottom(ChatMessageWrapper(messageInfo, MessageStatus.WAITING, System.currentTimeMillis()))

            sendMessageToServer(this@ChatActivity, PendingMessage(messageInfo.message, chatInfo.chatUUID,
                    chatInfo.getOthers(ClientPreferences.getClientUUID(this))), mercuryClient.dataBase)

            IOService.ActionUploadReference.launch(this, data.reference, data.aesKey, data.initVector, MediaType.MEDIA_TYPE_IMAGE)

            scrollToBottom()
        }
    }

    private fun sendVideo(uri: Uri, text: String, isGif: Boolean) {
        MediaSendUtil.saveVideoAndCreateMessage(mercuryClient(), listOf(chatInfo.chatUUID), uri, text, isGif) { result ->
            if (result == null) {
                Toast.makeText(this, "Looks like something went wrong trying to create a video message. This error has been reported to the developer", Toast.LENGTH_LONG).show()
                return@saveVideoAndCreateMessage
            }

            //We only send to this chat so only one message is present
            val messageInfo = result[0]

            val data = messageInfo.message.messageData as MessageReference
            mercuryClient().currentLoadingTable[data.reference] = 0.0

            addMessageToBottom(ChatMessageWrapper(messageInfo, MessageStatus.WAITING, System.currentTimeMillis()))

            sendMessageToServer(this@ChatActivity, PendingMessage(messageInfo.message, chatInfo.chatUUID,
                    chatInfo.getOthers(ClientPreferences.getClientUUID(this))), mercuryClient().dataBase)

            IOService.ActionUploadReference.launch(this, data.reference, data.aesKey, data.initVector, MediaType.MEDIA_TYPE_VIDEO)

            scrollToBottom()
        }
    }

    override fun onStop() {
        super.onStop()
        stopRecording(false)
        addInterestedChatUsers(false)
    }

    override fun onResume() {
        super.onResume()

        val mercuryClient = applicationContext as MercuryClient
        if (componentList.isNotEmpty() && upToDate) {
            val mostBottomMessage = componentList.lastOrNull { it.item is ChatAdapterMessage }

            if (mostBottomMessage != null) {
                loadMoreMessagesTop(true, LoadType.BOTTOM, Int.MAX_VALUE)
            }
        }

        LocalBroadcastManager.getInstance(this).apply {
            registerReceiver(uploadProgressReceiver, ActionUploadReferenceProgress.getFilter())
            registerReceiver(uploadReferenceFinishedReceiver, ActionUploadReferenceFinished.getFilter())
            registerReceiver(downloadProgressReceiver, ActionDownloadReferenceProgress.getFilter())
            registerReceiver(downloadReferenceFinishedReceiver, ActionDownloadReferenceFinished.getFilter())
            registerReceiver(uploadReferenceStartedReceiver, ActionUploadReferenceStarted.getFilter())
            registerReceiver(userViewChatReceiver, ActionUserViewChat.getFilter())
            registerReceiver(directConnectedReceiver, IntentFilter(ACTION_DIRECT_CONNECTION_CONNECTED))
            registerReceiver(chatMessageReceiver, ActionChatMessageReceived.getFilter())
            registerReceiver(messageStatusChangeReceiver, ActionMessageStatusChange.getFilter())
            registerReceiver(groupModificationReceiver, ActionGroupModificationReceived.getFilter())
            registerReceiver(typingReceiver, ActionTyping.getFilter())
            registerReceiver(userStatusChangeReceiver, ActionUserStatusChange.getFilter())
//            registerReceiver(updateProfilePictureReceiver, )
        }

        val chatNotificationIntentFilter = ActionChatMessageNotification.getFilter()
        chatNotificationIntentFilter.priority = 1

        registerReceiver(chatNotificationReceiver, chatNotificationIntentFilter)

        viewChat(true)

        addInterestedChatUsers(true)

        setUnreadMessages(mercuryClient.dataBase, chatInfo.chatUUID, 0)

        PushNotificationUtil.cancelChatNotifications(this, chatInfo.chatUUID)

        val clientUUID = ClientPreferences.getClientUUID(this)

        val userNoMember = !chatInfo.isUserParticipant(clientUUID) || !chatInfo.userProfile(clientUUID).isActive
        val adminNoMember = if (chatInfo.chatType == ChatType.GROUP_DECENTRALIZED) {
            val adminContact = getGroupMembers(mercuryClient.dataBase, chatInfo.chatUUID).first { it.role == GroupRole.ADMIN }.contact
            !chatInfo.participants.first { it.receiverUUID == adminContact.userUUID }.isActive
        } else false

        if (userNoMember || adminNoMember) {
            activity_chat_et_input.isEnabled = false
            activity_chat_iv_send_message.isEnabled = false
        }
        if (userNoMember) {
            activity_chat_et_input.setText(R.string.chat_group_no_member)
        }
        if (adminNoMember) {
            activity_chat_et_input.setText(R.string.chat_group_no_admin)
        }
    }

    override fun onPause() {
        super.onPause()
        val mercuryClient = applicationContext as MercuryClient

        LocalBroadcastManager.getInstance(this).apply {
            unregisterReceiver(uploadProgressReceiver)
            unregisterReceiver(uploadReferenceFinishedReceiver)
            unregisterReceiver(downloadProgressReceiver)
            unregisterReceiver(downloadReferenceFinishedReceiver)
            unregisterReceiver(uploadReferenceStartedReceiver)
            unregisterReceiver(userViewChatReceiver)
            unregisterReceiver(directConnectedReceiver)
            unregisterReceiver(chatMessageReceiver)
            unregisterReceiver(messageStatusChangeReceiver)
            unregisterReceiver(groupModificationReceiver)
            unregisterReceiver(typingReceiver)
            unregisterReceiver(userStatusChangeReceiver)
        }

        unregisterReceiver(chatNotificationReceiver)

        viewChat(false)

        addInterestedChatUsers(false)

        setUnreadMessages(mercuryClient.dataBase, chatInfo.chatUUID, 0)
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

            val reference = UUID.randomUUID()
            currentAudioUUID = reference

            val mediaRecorder = MediaRecorder()
            this.mediaRecorder = mediaRecorder

            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            File(filesDir.path + "/resources/${chatInfo.chatUUID}/").mkdirs()
            mediaRecorder.setOutputFile(ReferenceUtil.getFileForReference(this, reference).path)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)


            try {
                mediaRecorder.prepare()
            } catch (t: Throwable) {
            }

            activity_chat_cv_recording.visibility = View.VISIBLE

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

                    activity_chat_tv_record_audio_timer.text = getString(R.string.chat_activity_voice_record, min, seconds)

                    handler.postDelayed(this, 1000L)
                }
            })

            try {
                mediaRecorder.start()
            } catch (t: Throwable) {
                isRecording = false
            }
        }
    }

    private fun stopRecording(stillOnButton: Boolean) {
        val mercuryClient = applicationContext as MercuryClient

        if (isRecording) {
            isRecording = false

            activity_chat_cv_recording.visibility = View.GONE

            try {
                val recorder = mediaRecorder ?: return

                recorder.stop()
                recorder.release()
                mediaRecorder = null

                if (secondsRecording > 1 && stillOnButton) {
                    val reference = currentAudioUUID ?: return

                    val audioFile = ReferenceUtil.getFileForReference(this, reference)
                    val clientUUID = ClientPreferences.getClientUUID(this)

                    val hash = Hashing.sha512().hashBytes(audioFile.readBytes())

                    val aes = generateAESKey()
                    val iV = generateInitVector()

                    val core = MessageCore(clientUUID, System.currentTimeMillis(), UUID.randomUUID())
                    val data = MessageVoiceMessage(secondsRecording, aes, iV, reference, hash.toString())

                    val message = ChatMessageInfo(ChatMessage(core, data), true, chatInfo.chatUUID)

                    sendMessageToServer(this, PendingMessage(message.message, chatInfo.chatUUID, chatInfo.getOthers(clientUUID)), mercuryClient.dataBase)

                    addMessageToBottom(ChatMessageWrapper(message, MessageStatus.WAITING, System.currentTimeMillis()))

                    IOService.ActionUploadReference.launch(this, reference, aes, iV, MediaType.MEDIA_TYPE_AUDIO)

                    scrollToBottom()
                }
            } catch (t: Throwable) {

            }
        }
    }

    fun userTyping(userUUID: UUID) {
        val typingUser = viewingUsersList.firstOrNull { it.contact.userUUID == userUUID }
                ?: return
        typingUser.userState = UserState.TYPING
        userTypingTime[userUUID] = System.currentTimeMillis()

        viewingUsersAdapter.notifyItemChanged(viewingUsersList.indexOf(typingUser))
    }

    fun userStatusChange(userChange: UserChange) {
        val mercuryClient = applicationContext as MercuryClient

        val client = ClientPreferences.getClientUUID(this)

        if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
            val partner = chatInfo.participants.first { it.receiverUUID != client }
            if (partner.receiverUUID == userChange.userUUID) {
                setDefaultSubtitle()
            }
        }
    }

    fun connectionClosed() {
        supportActionBar!!.subtitle = null
    }

    private fun setDefaultSubtitle() {
        val mercuryClient = applicationContext as MercuryClient

        val client = ClientPreferences.getClientUUID(this)

        if (chatInfo.chatType == ChatType.TWO_PEOPLE) {
            val partner = chatInfo.participants.first { it.receiverUUID != client }
            if (mercuryClient.userStatusMap.containsKey(partner.receiverUUID)) {
                val userChange = mercuryClient.userStatusMap[partner.receiverUUID]!!
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

        val reference = (referenceHolder.message.chatMessageInfo.message.messageData as MessageReference).reference

        mediaPlayer.setDataSource(this, Uri.fromFile(ReferenceUtil.getFileForReference(this, reference)))
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

    fun startReferenceLoad(holder: ReferenceHolder, adapterPosition: Int, mediaType: Int) {
        if (!checkWriteStoragePermission(this, PERMISSION_REQUEST_EXTERNAL_STORAGE)) {
            return
        }

        holder.referenceState = ReferenceState.IN_PROGRESS
        holder.progress = 0

        val upload = holder.message.chatMessageInfo.client

        val message = holder.message.chatMessageInfo.message
        val data = message.messageData as MessageReference

        val referenceUUID = data.reference

        if (upload) {
            IOService.ActionUploadReference.launch(this, referenceUUID, data.aesKey, data.initVector, mediaType)
        } else {
            IOService.ActionDownloadReference.launch(this, referenceUUID, data.aesKey, data.initVector, mediaType)
        }

        chatAdapter.notifyItemChanged(adapterPosition)
    }

    private fun addInterestedChatUsers(add: Boolean) {
        val mercuryClient = applicationContext as MercuryClient
        chatInfo.participants.filter { it.type == ChatReceiver.ReceiverType.USER }.forEach {
            if (add) {
                mercuryClient.addInterestedUser(it.receiverUUID)
            } else {
                mercuryClient.removeInterestedUser(it.receiverUUID)
            }
        }
    }

    private fun viewChat(view: Boolean) {
        val mercuryClient = applicationContext as MercuryClient

        DirectConnectionService.ActionSendPacketToServer.launch(this, ViewChatPacketToServer(chatInfo.chatUUID, view))
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun applyBackgroundImage(backgroundFile: File) {
        if (backgroundFile.exists()) {
            window.setBackgroundDrawable(BitmapDrawable(resources, BitmapFactory.decodeStream(backgroundFile.inputStream())))
        }
    }

    fun updateChatName(chatUUID: UUID, newName: String) {
        if (chatUUID != chatInfo.chatUUID) return
        supportActionBar?.title = newName
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        finish()
        startActivity(intent)
    }

    fun select(item: ChatAdapter.ChatAdapterWrapper<*>, selected: Boolean) {
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
                                DeleteMessagesTask(selectedItems.map { it.item as ChatMessageInfo }, chatInfo.chatUUID, applicationContext as MercuryClient, NotificationManagerCompat.from(this@ChatActivity)).execute()
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
//                    val text = if (selectedItems.size == 1) {
//                        val wrapper = selectedItems.first().item as ChatMessageWrapper
//                        wrapper.message.text
//                    } else {
//                        selectedItems.joinToString(separator = "\n") {
//                            val wrapper = it.item as ChatMessageInfo
//                            "[${dateFormatAnytime.format(Date(wrapper.message.timeSent))}]: ${wrapper.message.text}"
//                        }
//                    }
//                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
//                    val clip = ClipData.newPlainText(getString(R.string.app_name), text)
//                    clipboard.primaryClip = clip
//
//                    mode.finish()
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

    private enum class LoadType {
        TOP,
        BOTTOM
    }

    private fun loadMoreMessagesTop(loadAsync: Boolean, type: LoadType, limit: Int = 20) {
        if (currentlyLoadingMore) return

        currentlyLoadingMore = true

        val currentEdgeMessage = when (type) {
            LoadType.TOP -> componentList.firstOrNull { it.item is ChatAdapterMessage }?.item
            LoadType.BOTTOM -> componentList.lastOrNull { it.item is ChatAdapterMessage }?.item
        }

        val currentEdgeTimeEpochMilli = if (currentEdgeMessage is ChatAdapterMessage) {
            currentEdgeMessage.message.message.messageCore.timeCreated
        } else System.currentTimeMillis()

        val insertItems =
                { (loadedItems,
                          messageUUIDToItems,
                          referenceUUIDToMessageUUID): ChatLoader.MessageTransform ->
                    when (type) {
                        LoadType.TOP -> {
                            //Add at the very start of list
                            componentList.addAll(0, loadedItems)

                            chatAdapter.notifyItemRangeInserted(0, loadedItems.size)

                            ChatLoader.filterDuplicatedDateItems(chatAdapter, componentList, 0, loadedItems.size)
                        }
                        LoadType.BOTTOM -> {
                            val prevSize = componentList.size
                            //Just append to the list
                            componentList += loadedItems

                            chatAdapter.notifyItemRangeInserted(prevSize, loadedItems.size)

                            ChatLoader.filterDuplicatedDateItems(chatAdapter, componentList, prevSize, prevSize + loadedItems.size)
                        }
                    }

                    messageObjects += messageUUIDToItems

                    this.referenceUUIDToMessageUUID += referenceUUIDToMessageUUID

                    currentlyLoadingMore = false
                }

        val min = when (type) {
            LoadType.TOP -> 0L
            LoadType.BOTTOM -> currentEdgeTimeEpochMilli
        }

        val max = when (type) {
            LoadType.TOP -> currentEdgeTimeEpochMilli
            LoadType.BOTTOM -> System.currentTimeMillis()
        }

        if (loadAsync) {
            ChatLoader.loadMessageItemsForAdapterAsync(this, mercuryClient().dataBase,
                    chatInfo,
                    min,
                    max,
                    ChatLoader.LoadType.DESC,
                    0L,
                    { result ->
                        insertItems(result)
                    }, limit)
        } else {
            insertItems(ChatLoader.loadMessageItemsForAdapter(this, mercuryClient().dataBase,
                    chatInfo,
                    min,
                    max,
                    ChatLoader.LoadType.DESC,
                    0L,
                    limit
            ))
        }
    }

    fun addMessageToBottom(chatMessageWrapper: ChatMessageWrapper) {
        val lastMessage = componentList.lastOrNull { it.item is ChatAdapterMessage }?.item as? ChatAdapterMessage

        val latestTime = lastMessage?.message?.message?.messageCore?.timeCreated ?: 0L

        val (adapterItems, messageUUIDToObjects, referenceUUIDToMessageUUID) =
                ChatLoader.transformMessageList(this, chatInfo, latestTime, listOf(chatMessageWrapper))

        val previousSize = componentList.size

        componentList += adapterItems
        messageObjects += messageUUIDToObjects
        this.referenceUUIDToMessageUUID += referenceUUIDToMessageUUID

        chatAdapter.notifyItemRangeInserted(previousSize, adapterItems.size)
    }

    private inner class ChatListScrollListener : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
            onTop = !recyclerView.canScrollVertically(-1)
            onBottom = !recyclerView.canScrollVertically(1)

            val layoutManager = recyclerView.layoutManager as androidx.recyclerview.widget.LinearLayoutManager

            val firstVisibleIndex = layoutManager.findFirstVisibleItemPosition()
            val lastVisibleIndex = layoutManager.findLastVisibleItemPosition()

            val client = ClientPreferences.getClientUUID(this@ChatActivity)


            (Math.max(0, firstVisibleIndex)..Math.min(lastVisibleIndex, componentList.size))
                    .asSequence()
                    .map { componentList[it].item }
                    .filter { chatAdapterSubItem -> chatAdapterSubItem is ChatAdapterMessage }
                    .filter {
                        it as ChatAdapterMessage
                        it.message.latestStatus != MessageStatus.SEEN
                    }
                    .forEach { chatAdapterSubItem ->
                        chatAdapterSubItem as ChatAdapterMessage
                        val wrapper = chatAdapterSubItem.message
                        if (!wrapper.chatMessageInfo.client) {
                            wrapper.latestStatus = MessageStatus.SEEN

                            val mercuryClient = mercuryClient()

                            val core = wrapper.chatMessageInfo.message.messageCore

                            val messageUUID = core.messageUUID

                            updateMessageStatus(mercuryClient.dataBase, messageUUID, MessageStatus.SEEN, System.currentTimeMillis())

                            for (s in chatInfo.getOthers(client)) {
                                val changeCore = MessageCore(client, System.currentTimeMillis(), UUID.randomUUID())
                                val changeData = MessageStatusUpdate(messageUUID, MessageStatus.SEEN, System.currentTimeMillis())

                                sendMessageToServer(this@ChatActivity, PendingMessage(ChatMessage(changeCore, changeData), chatInfo.chatUUID, chatInfo.getOthers(client)), mercuryClient.dataBase)
                            }
                            chatAdapter.notifyItemChanged(componentList.indexOfFirst { it.item == chatAdapterSubItem })
                        }
                    }

            if (firstVisibleIndex < 10 && !currentlyLoadingMore) {
                loadMoreMessagesTop(true, LoadType.TOP)
            }

            if (onBottom) {
                upToDate = true

                activity_chat_cv_new_messages_parent.visibility = View.GONE
            }
        }
    }

    private inner class GroupModificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (chatUuid, modification) = ActionGroupModificationReceived.getData(intent)

            if (chatInfo.chatUUID == chatUuid && modification is GroupModificationChangeName) {
                updateChatName(chatUuid, modification.newName)
            }
        }
    }

    private inner class TypingReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (chatUuid, userUuid) = ActionTyping.getData(intent)

            if (chatUuid == this@ChatActivity.chatInfo.chatUUID) {
                userTyping(userUuid)
            }
        }
    }

    private inner class UserStatusChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (userUuid, status, time) = ActionUserStatusChange.getData(intent)

            userStatusChange(UserChange(userUuid, status, time))
        }
    }

    inner class MessageStatusChangeReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (chatUuid, messageUuid, messageStatus) = ActionMessageStatusChange.getData(intent)

            if (chatInfo.chatUUID != chatUuid) return

            updateMessageStatus(messageUuid, messageStatus)
        }
    }

    inner class ChatMessageReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (chatMessage, chatUuid) = ActionChatMessageReceived.getData(intent)

            val mercuryClient = context.mercuryClient()

            if (chatUuid == chatInfo.chatUUID) {
                val client = ClientPreferences.getClientUUID(context)

                val isClient = client == chatMessage.messageCore.senderUUID
                val info = ChatMessageInfo(chatMessage, isClient, chatUuid)

                val status = getLatestMessageStatus(mercuryClient.dataBase, chatMessage.messageCore.messageUUID)
                val wrapper = ChatMessageWrapper(info, status.status, status.time)

                addMessageToBottom(wrapper)

                if (onBottom) {
                    scrollToBottom()
                } else {
                    activity_chat_cv_new_messages_parent.visibility = View.VISIBLE
                }
            }
        }
    }

    inner class UploadProfilePictureReceiver : BroadcastReceiver() {
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

    inner class DirectConnectedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            viewChat(true)
        }
    }

    inner class UserViewChatReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (chatUuid, userUuid, isViewing) = ActionUserViewChat.getData(intent)

            if (chatUuid == chatInfo.chatUUID) {
                if (userUuid == ClientPreferences.getClientUUID(context)) return

                val contact = contactMap[userUuid] ?: return
                val viewingUser = ViewingUser(contact, UserState.VIEWING)

                if (isViewing) {
                    if (viewingUsersList.any { it.contact.userUUID == userUuid }) return
                    viewingUsersList += viewingUser
                    viewingUsersAdapter.notifyItemInserted(viewingUsersList.size - 1)
                } else {
                    val index = viewingUsersList.indexOfFirst { it.contact.userUUID == userUuid }
                    if (index != -1) {
                        viewingUsersList.removeAt(index)
                        viewingUsersAdapter.notifyItemRemoved(index)
                    }
                }
            }
        }
    }

    inner class UploadReferenceStartedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
        }
    }

    inner class DownloadReferenceFinishedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (referenceUuid, successful) = ActionDownloadReferenceFinished.getData(intent)

            val messageUUID = referenceUUIDToMessageUUID[referenceUuid]

            val holder = messageObjects[messageUUID]?.first { it.item is ReferenceHolder }
                    ?: return

            val item = holder.item as ReferenceHolder
            item.referenceState = if (successful) ReferenceState.FINISHED else ReferenceState.NOT_STARTED

            chatAdapter.notifyItemChanged(componentList.indexOf(holder))
        }
    }

    inner class DownloadProgressReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
//            val referenceUUID = intent.getSerializableExtra(KEY_REFERENCE_UUID) as UUID
//            val messageUUID = referenceUUIDToMessageUUID[referenceUUID]
//
//            val holder = messageObjects[messageUUID]?.first { it.item is ReferenceHolder }
//                    ?: return
//            val item = holder.item as ReferenceHolder
//            item.progress = intent.getIntExtra(KEY_PROGRESS, 0)
//
//            chatAdapter.notifyItemChanged(componentList.indexOf(holder))
        }
    }

    inner class UploadReferenceFinishedReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (referenceUuid, successful) = ActionUploadReferenceFinished.getData(intent)

            val messageUUID = referenceUUIDToMessageUUID[referenceUuid]

            val holder = messageObjects[messageUUID]?.first { it.item is ReferenceHolder }
                    ?: return

            val item = holder.item as ReferenceHolder
            item.referenceState = if (successful) ReferenceState.FINISHED else ReferenceState.NOT_STARTED

            chatAdapter.notifyItemChanged(componentList.indexOf(holder))
        }
    }

    inner class UploadProgressReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {

        }
    }

    inner class ChatNotificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (chatUuid, _) = ActionChatMessageNotification.getData(intent)
            if (chatUuid == chatInfo.chatUUID) {
                abortBroadcast()
            }
        }
    }

    object ActionPickMedia {

        private const val EXTRA_SELECTED_MEDIA = "de.intektor.mercury.EXTRA_SELECTED_MEDIA"

        fun createIntent(selectedMedia: List<MediaToSend>): Intent {
            return Intent()
                    .putExtra(EXTRA_SELECTED_MEDIA, ArrayList(selectedMedia))
        }

        fun getData(intent: Intent): Holder {
            val selectedMedia = intent.getSerializableExtra(EXTRA_SELECTED_MEDIA) as ArrayList<MediaToSend>
            return Holder(selectedMedia)
        }

        data class Holder(val selectedMedia: List<MediaToSend>)

        data class MediaToSend(val mediaFile: MediaFile, val text: String, val isGif: Boolean) : Parcelable {
            constructor(parcel: Parcel) : this(
                    parcel.readSerializable() as MediaFile,
                    parcel.readString() ?: throw java.lang.IllegalStateException(),
                    parcel.readByte() != 0.toByte())

            override fun writeToParcel(parcel: Parcel, flags: Int) {
                parcel.writeSerializable(mediaFile)
                parcel.writeString(text)
                parcel.writeByte(if (isGif) 1 else 0)
            }

            override fun describeContents(): Int = 0

            companion object CREATOR : Parcelable.Creator<MediaToSend> {
                override fun createFromParcel(parcel: Parcel): MediaToSend = MediaToSend(parcel)

                override fun newArray(size: Int): Array<MediaToSend?> = arrayOfNulls(size)
            }
        }
    }
}