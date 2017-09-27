package de.intektor.kentai

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Rect
import android.media.MediaRecorder
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.support.v13.app.ActivityCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.Editable
import android.text.TextWatcher
import android.view.*
import android.widget.TextView
import android.widget.Toast
import de.intektor.kentai.kentai.ChatAdapter
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.references.getReferenceFile
import de.intektor.kentai.kentai.references.uploadAudio
import de.intektor.kentai_http_common.chat.*
import de.intektor.kentai_http_common.chat.group_modification.ChatMessageGroupModification
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationAddUser
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.toUUID
import kotlinx.android.synthetic.main.activity_chat.*
import java.io.File
import java.util.*
import kotlin.collections.HashMap

class ChatActivity : AppCompatActivity() {

    private lateinit var chatAdapter: ChatAdapter

    lateinit var chatInfo: ChatInfo

    private val componentList: MutableList<Any> = mutableListOf()

    private val messageMap: HashMap<UUID, ChatMessageWrapper> = HashMap()
    private val colorMap: HashMap<UUID, ChatAdapter.UsernameChatInfo> = HashMap()

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
        lM.stackFromEnd = true
        msgListView.layoutManager = lM

        chatInfo = intent.getParcelableExtra("chatInfo")

        supportActionBar?.title = chatInfo.chatName

        if (chatInfo.chatType == ChatType.GROUP) {
            for ((receiverUUID) in chatInfo.participants) {
                KentaiClient.INSTANCE.dataBase.rawQuery("SELECT contacts.username, user_color_table.color, contacts.user_uuid, contacts.alias FROM user_color_table LEFT JOIN contacts ON contacts.user_uuid = user_color_table.user_uuid WHERE user_color_table.user_uuid = ?", arrayOf(receiverUUID.toString())).use { query ->
                    query.moveToNext()
                    val username = query.getString(0)
                    val color = query.getString(1)
                    val userUUID = query.getString(2).toUUID()
                    val alias = query.getString(3)
                    colorMap.put(receiverUUID, ChatAdapter.UsernameChatInfo(username, color))
                    contactMap.put(userUUID, Contact(username, alias, userUUID, null))
                }
            }
        }

        if (!chatInfo.participants.first { it.receiverUUID == KentaiClient.INSTANCE.userUUID }.isActive) {
            messageBox.isEnabled = false
            messageBox.setText(R.string.chat_group_no_member)
            sendMessageButton.isEnabled = false
        }

        val messages = load20Messages()
        messages.forEach { addMessage(it, false, true) }

        componentList.reverse()

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
            }
        })

        uploadProgressReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                (0 until msgListView.adapter.itemCount).forEach {
                    val holder = msgListView.getChildViewHolder(msgListView.getChildAt(it)) as ChatAdapter.AbstractViewHolder
                    holder.broadcast("de.intektor.kentai.uploadProgress", intent)
                }
            }
        }

        uploadReferenceFinishedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                (0 until msgListView.childCount).forEach {
                    val holder = msgListView.getChildViewHolder(msgListView.getChildAt(it)) as ChatAdapter.AbstractViewHolder
                    holder.broadcast("de.intektor.kentai.uploadReferenceFinished", intent)
                }
            }
        }

        downloadProgressReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                (0 until msgListView.adapter.itemCount).forEach {
                    val holder = msgListView.getChildViewHolder(msgListView.getChildAt(it)) as ChatAdapter.AbstractViewHolder
                    holder.broadcast("de.intektor.kentai.downloadProgress", intent)
                }
            }
        }

        downloadReferenceFinishedReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                (0 until msgListView.childCount).forEach {
                    val holder = msgListView.getChildViewHolder(msgListView.getChildAt(it)) as ChatAdapter.AbstractViewHolder
                    holder.broadcast("de.intektor.kentai.downloadReferenceFinished", intent)
                }
            }
        }

        scrollToBottom()
    }

    private fun sendMessage() {
        val written = messageBox.text.toString()
        val chatMessage = ChatMessageText(written, KentaiClient.INSTANCE.userUUID, System.currentTimeMillis())
        val wrapper = ChatMessageWrapper(chatMessage, MessageStatus.WAITING, true, System.currentTimeMillis())
        val wrapperCopy = wrapper.copy(message = ChatMessageText(written, KentaiClient.INSTANCE.userUUID, System.currentTimeMillis()))
        wrapperCopy.message.id = wrapper.message.id
        wrapperCopy.message.referenceUUID = wrapper.message.referenceUUID
        addMessage(wrapperCopy, back = false)
        chatAdapter.notifyDataSetChanged()

        if (onBottom) scrollToBottom()

        sendMessageToServer(this, PendingMessage(wrapper, chatInfo.chatUUID, chatInfo.participants.filter { it.receiverUUID != KentaiClient.INSTANCE.userUUID }))
    }

    fun addMessage(chatMessageWrapper: ChatMessageWrapper, notifyDataSetChanges: Boolean = true, back: Boolean) {
        val message = chatMessageWrapper.message
        if (!message.shouldBeStored()) return
        if (message is ChatMessageGroupModification) {
            val groupModification = message.groupModification
            if (groupModification is GroupModificationAddUser) {
                KentaiClient.INSTANCE.dataBase.rawQuery("SELECT contacts.username, user_color_table.color, contacts.user_uuid, contacts.alias FROM user_color_table LEFT JOIN contacts ON contacts.user_uuid = user_color_table.user_uuid WHERE user_color_table.user_uuid = ?", arrayOf(groupModification.userUUID)).use { query ->
                    query.moveToNext()
                    val username = query.getString(0)
                    val color = query.getString(1)
                    val userUUID = query.getString(2).toUUID()
                    val alias = query.getString(3)
                    colorMap.put(userUUID, ChatAdapter.UsernameChatInfo(username, color))
                    contactMap.put(userUUID, Contact(username, alias, userUUID, null))
                }
            }
        }

        if (back) {
            componentList.add(ChatAdapter.TimeStatusChatInfo(message.timeSent, chatMessageWrapper.status, chatMessageWrapper.client))
        }
        if (chatInfo.chatType == ChatType.GROUP && !chatMessageWrapper.client && !back) {
            componentList.add(colorMap[message.senderUUID]!!)
        }

        componentList.add(chatMessageWrapper)

        if (chatInfo.chatType == ChatType.GROUP && !chatMessageWrapper.client && back) {
            componentList.add(colorMap[message.senderUUID]!!)
        }
        if (!back) {
            componentList.add(ChatAdapter.TimeStatusChatInfo(message.timeSent, chatMessageWrapper.status, chatMessageWrapper.client))
        }

        messageMap.put(message.id, chatMessageWrapper)
        if (notifyDataSetChanges) chatAdapter.notifyDataSetChanged()
    }

    fun addMessages(messages: List<ChatMessageWrapper>, back: Boolean) {
        var totalInserted = messages.size
        val finalList = mutableListOf<Any>()

        val previousSize = componentList.size

        if (chatInfo.chatType == ChatType.GROUP) {
            messages.forEach {
                if (!it.client) {
                    finalList.add(colorMap[it.message.senderUUID]!!)
                    totalInserted++
                }
            }
        }

        if (back) {
            componentList.addAll(0, finalList)
        } else {
            componentList.addAll(finalList)
        }

        messages.forEach {
            messageMap.put(it.message.id, it)
        }

        if (back) {
            chatAdapter.notifyItemRangeInserted(0, totalInserted)
        } else {
            chatAdapter.notifyItemRangeInserted(previousSize, totalInserted)
        }
    }

    fun updateMessageStatus(messageUUID: UUID, status: MessageStatus) {
        val wrapper = messageMap[messageUUID]
        wrapper?.status = status
        chatAdapter.notifyDataSetChanged()
    }

    fun scrollToBottom() {
        msgListView.smoothScrollToPosition(componentList.size)
    }

    private fun load20Messages(): List<ChatMessageWrapper> {
        val list = mutableListOf<ChatMessageWrapper>()
        val query = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT message_uuid, additional_info, text, time, type, sender_uuid, client, reference FROM chat_table WHERE chat_uuid = ? AND time < $firstMessageTime ORDER BY time DESC LIMIT 20", arrayOf(chatInfo.chatUUID.toString()))

        while (query.moveToNext()) {
            val uuid = UUID.fromString(query.getString(0))
            val blob = query.getBlob(1)
            val text = query.getString(2)
            val time = query.getLong(3)
            val type = query.getInt(4)
            val sender = query.getString(5)
            val client: Boolean = query.getInt(6) != 0
            val reference = query.getString(7).toUUID()

            val cursor = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT status, time FROM message_status_change WHERE message_uuid = '$uuid' ORDER BY time DESC LIMIT 1", null)
            cursor.moveToNext()
            val status = MessageStatus.values()[cursor.getInt(0)]
            val timeChange = cursor.getLong(1)
            cursor.close()

            val message = ChatMessageRegistry.create(type)
            message.id = uuid
            message.senderUUID = sender.toUUID()
            message.text = text
            message.timeSent = time
            message.referenceUUID = reference
            message.processAdditionalInfo(blob)

            list.add(ChatMessageWrapper(message, status, client, timeChange))
        }

        if (list.isNotEmpty()) {
            firstMessageTime = list.last().message.timeSent
        }
        query.close()
        return list
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
                return load20Messages().reversed()
            }

            override fun onPostExecute(result: List<ChatMessageWrapper>) {
                super.onPostExecute(result)
                val prevMessageList = componentList.size
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
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onStop() {
        super.onStop()
        stopRecording(false)
    }

    override fun onResume() {
        super.onResume()

        if (componentList.isNotEmpty()) {
            val lastMessage = componentList.last { it is ChatMessageWrapper } as ChatMessageWrapper

            val query = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT message_uuid, additional_info, text, time, type, sender_uuid, client FROM chat_table WHERE chat_uuid = ? AND time > ${lastMessage.message.timeSent} ORDER BY time", arrayOf(chatInfo.chatUUID.toString()))

            val moreList = mutableListOf<ChatMessageWrapper>()

            while (query.moveToNext()) {
                val uuid = UUID.fromString(query.getString(0))
                val blob = query.getBlob(1)
                val text = query.getString(2)
                val time = query.getLong(3)
                val type = query.getInt(4)
                val sender = query.getString(5)
                val client: Boolean = query.getInt(6) != 0

                val cursor = KentaiClient.INSTANCE.dataBase.rawQuery("SELECT status, time FROM message_status_change WHERE message_uuid = '$uuid' ORDER BY time DESC LIMIT 1", null)
                cursor.moveToNext()
                val status = MessageStatus.values()[cursor.getInt(0)]
                val timeChange = cursor.getLong(1)
                cursor.close()

                val message = ChatMessageRegistry.create(type)
                message.id = uuid
                message.senderUUID = sender.toUUID()
                message.text = text
                message.timeSent = time
                message.processAdditionalInfo(blob)

                moreList.add(ChatMessageWrapper(message, status, client, timeChange))
            }
            query.close()

            moreList.forEach {
                val message = it.message
                if (message is ChatMessageGroupModification) {
                    val groupModification = message.groupModification
                    if (groupModification is GroupModificationAddUser) {
                        KentaiClient.INSTANCE.dataBase.rawQuery("SELECT contacts.username, user_color_table.color, contacts.user_uuid, contacts.alias FROM user_color_table LEFT JOIN contacts ON contacts.user_uuid = user_color_table.user_uuid WHERE user_color_table.user_uuid = ?", arrayOf(groupModification.userUUID)).use { query ->
                            query.moveToNext()
                            val username = query.getString(0)
                            val color = query.getString(1)
                            val userUUID = query.getString(2).toUUID()
                            val alias = query.getString(3)
                            colorMap.put(userUUID, ChatAdapter.UsernameChatInfo(username, color))
                            contactMap.put(userUUID, Contact(username, alias, userUUID, null))
                        }
                    }
                }
            }

            addMessages(moreList, false)
        }

        registerReceiver(uploadProgressReceiver, IntentFilter("de.intektor.kentai.uploadProgress"))
        registerReceiver(uploadReferenceFinishedReceiver, IntentFilter("de.intektor.kentai.uploadReferenceFinished"))
        registerReceiver(downloadProgressReceiver, IntentFilter("de.intektor.kentai.downloadReferenceFinished"))
        registerReceiver(downloadReferenceFinishedReceiver, IntentFilter("de.intektor.kentai.downloadReferenceFinished"))
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(uploadProgressReceiver)
        unregisterReceiver(uploadReferenceFinishedReceiver)
        unregisterReceiver(downloadProgressReceiver)
        unregisterReceiver(downloadReferenceFinishedReceiver)
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
                componentList.removeAt(index)
                componentList.removeAt(index + 1)
                if (message.client || chatInfo.chatType == ChatType.TWO_PEOPLE) {
                    msgListView.adapter.notifyItemRangeRemoved(index, 2)
                } else {
                    componentList.removeAt(index - 1)
                    msgListView.adapter.notifyItemRangeRemoved(index - 1, 3)
                }
                return true
            }
            R.id.menuChatBubbleInfo -> {
                val message = componentList[index] as ChatMessageWrapper

                return true
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
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)

        if (!hasPermissionToRecordAudio) return

        isRecording = true
        secondsRecording = -1

        currentAudioUUID = UUID.randomUUID()

        mediaRecorder = MediaRecorder()
        mediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
        File(filesDir.path + "/resources/${chatInfo.chatUUID}/").mkdirs()
        mediaRecorder!!.setOutputFile(getReferenceFile(chatInfo.chatUUID, currentAudioUUID!!, FileType.AUDIO, filesDir).path)
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

    private fun stopRecording(stillOnButton: Boolean) {
        if (isRecording) {
            isRecording = false

            recordingTimeView.visibility = View.GONE
            messageBox.visibility = View.VISIBLE

            mediaRecorder!!.stop()
            mediaRecorder!!.release()
            mediaRecorder = null

            if (secondsRecording > 0 && stillOnButton) {
                val message = ChatMessageWrapper(ChatMessageVoiceMessage(secondsRecording, currentAudioUUID!!, KentaiClient.INSTANCE.userUUID, "", System.currentTimeMillis()), MessageStatus.WAITING, true, System.currentTimeMillis())
                sendMessageToServer(this, PendingMessage(
                        message,
                        chatInfo.chatUUID, chatInfo.participants.filter { it.isActive && it.receiverUUID != KentaiClient.INSTANCE.userUUID }
                ))
                addMessage(message, true, false)
                uploadAudio(this, KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, currentAudioUUID!!, getReferenceFile(chatInfo.chatUUID, currentAudioUUID!!, FileType.AUDIO, filesDir))
            }
        }
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
}

