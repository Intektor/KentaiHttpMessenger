package de.intektor.kentai.kentai

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.MediaPlayer
import android.media.ThumbnailUtils
import android.os.Handler
import android.provider.MediaStore
import android.support.v4.content.FileProvider
import android.support.v7.widget.RecyclerView
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import de.intektor.kentai.ChatActivity
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.R
import de.intektor.kentai.kentai.chat.ChatInfo
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.ChatReceiver
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.references.*
import de.intektor.kentai_http_common.chat.*
import de.intektor.kentai_http_common.chat.group_modification.*
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.toUUID
import java.text.SimpleDateFormat
import java.util.*


/**
 * @author Intektor
 */
class ChatAdapter(private val componentList: MutableList<Any>, val chatInfo: ChatInfo, val contactMap: Map<UUID, Contact>, val activity: ChatActivity) : RecyclerView.Adapter<ChatAdapter.AbstractViewHolder>() {

    companion object {
        val TEXT_MESSAGE_ID = 0
        val GROUP_INVITE_ID = 1
        val USERNAME_CHAT_INFO = 2
        val TIME_STATUS_INFO = 3
        val GROUP_MODIFICATION_CHANGE_NAME = 4
        val GROUP_MODIFICATION_CHANGE_ROLE = 5
        val GROUP_MODIFICATION_CHANGE_KICK_USER = 6
        val GROUP_MODIFICATION_CHANGE_ADD_USER = 7
        val VOICE_MESSAGE = 8
        val IMAGE_MESSAGE = 9
        val VIDEO_MESSAGE = 10
    }

    fun add(any: Any) {
        componentList.add(any)
    }

    override fun getItemViewType(position: Int): Int {
        val component = componentList[position]
        return when (component) {
            is ChatMessageWrapper -> when (MessageType.values()[ChatMessageRegistry.getID(component.message.javaClass)]) {
                MessageType.TEXT_MESSAGE -> TEXT_MESSAGE_ID
                MessageType.GROUP_INVITE -> GROUP_INVITE_ID
                MessageType.VOICE_MESSAGE -> VOICE_MESSAGE
                MessageType.IMAGE_MESSAGE -> IMAGE_MESSAGE
                MessageType.VIDEO_MESSAGE -> VIDEO_MESSAGE
                MessageType.GROUP_MODIFICATION -> {
                    val message = component.message
                    message as ChatMessageGroupModification
                    when {
                        message.groupModification is GroupModificationChangeName -> GROUP_MODIFICATION_CHANGE_NAME
                        message.groupModification is GroupModificationChangeRole -> GROUP_MODIFICATION_CHANGE_ROLE
                        message.groupModification is GroupModificationKickUser -> GROUP_MODIFICATION_CHANGE_KICK_USER
                        message.groupModification is GroupModificationAddUser -> GROUP_MODIFICATION_CHANGE_ADD_USER
                        else -> TODO()
                    }
                }
                else -> {
                    Log.e("ERROR", "")
                    TODO()
                }
            }
            is UsernameChatInfo -> USERNAME_CHAT_INFO
            is TimeStatusChatInfo -> TIME_STATUS_INFO
            else -> TODO("$component")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AbstractViewHolder =
            when (viewType) {
                TEXT_MESSAGE_ID -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false))
                GROUP_INVITE_ID -> GroupInviteViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_group_invite, parent, false))
                USERNAME_CHAT_INFO -> UsernameChatInfoViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_sender_info, parent, false))
                TIME_STATUS_INFO -> TimeStatusViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_time_info, parent, false))
                GROUP_MODIFICATION_CHANGE_NAME -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false))
                GROUP_MODIFICATION_CHANGE_ROLE -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false))
                GROUP_MODIFICATION_CHANGE_KICK_USER -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false))
                GROUP_MODIFICATION_CHANGE_ADD_USER -> TextMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chatbubble, parent, false))
                VOICE_MESSAGE -> VoiceMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_voice_message, parent, false))
                IMAGE_MESSAGE -> ImageMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_image, parent, false))
                VIDEO_MESSAGE -> VideoMessageViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.chat_message_video, parent, false))
                else -> throw RuntimeException()
            }

    override fun onBindViewHolder(holder: AbstractViewHolder, position: Int) {
        val wrapper: Any = componentList[position]
        holder.mView.tag = position
        holder.setComponent(wrapper)
    }


    override fun getItemCount(): Int = componentList.size

    inner class TextMessageViewHolder(mView: View) : ChatMessageViewHolder(mView) {
        private val msg: TextView = mView.findViewById<TextView>(R.id.message_text) as TextView

        override fun setComponent(component: Any) {
            component as ChatMessageWrapper
            val message = component.message
            msg.text = component.message.text
            msg.movementMethod = LinkMovementMethod.getInstance()

            val layout = mView.findViewById<LinearLayout>(R.id.bubble_layout) as LinearLayout
            val parentLayout = mView.findViewById<LinearLayout>(R.id.bubble_layout_parent) as LinearLayout

            if (component.client) {
                layout.setBackgroundResource(R.drawable.bubble_right)
                parentLayout.gravity = Gravity.END
            } else {
                layout.setBackgroundResource(R.drawable.bubble_left)
                parentLayout.gravity = Gravity.START
                val paddingStart = msg.paddingStart
                val paddingEnd = msg.paddingEnd
                msg.setPadding(paddingEnd, msg.paddingTop, paddingStart, msg.paddingBottom)
            }

            if (message is ChatMessageGroupModification) {
                layout.setBackgroundResource(R.drawable.bubble_advanced)
                val modification = message.groupModification
                when (modification) {
                    is GroupModificationChangeName -> {
                        msg.text = mView.context.getString(R.string.chat_group_change_name, contactMap[component.message.senderUUID]!!.name, modification.oldName, modification.newName)
                    }
                    is GroupModificationChangeRole -> {
                        //TODO: make it impossible to get anything crashing by sending wrong enums

                        val oldRoleString = when (GroupRole.values()[modification.oldRole.toInt()]) {
                            GroupRole.ADMIN -> mView.context.getString(R.string.group_role_admin)
                            GroupRole.MODERATOR -> mView.context.getString(R.string.group_role_moderator)
                            GroupRole.DEFAULT -> mView.context.getString(R.string.group_role_default)
                        }

                        val newRoleString = when (GroupRole.values()[modification.newRole.toInt()]) {
                            GroupRole.ADMIN -> mView.context.getString(R.string.group_role_admin)
                            GroupRole.MODERATOR -> mView.context.getString(R.string.group_role_moderator)
                            GroupRole.DEFAULT -> mView.context.getString(R.string.group_role_default)
                        }

                        msg.text = mView.context.getString(R.string.chat_group_change_role, contactMap[component.message.senderUUID]!!.name, contactMap[modification.userUUID.toUUID()]!!.name,
                                oldRoleString, newRoleString)
                    }
                    is GroupModificationKickUser -> {
                        if (modification.reason.isBlank()) {
                            msg.text = mView.context.getString(R.string.chat_group_change_kick_user_no_reason, contactMap[modification.toKick.toUUID()]!!.name, contactMap[message.senderUUID]!!.name)
                        } else {
                            msg.text = mView.context.getString(R.string.chat_group_change_kick_user_with_reason,
                                    contactMap[modification.toKick.toUUID()]!!.name, contactMap[message.senderUUID]!!.name, modification.reason)
                        }
                    }
                    is GroupModificationAddUser -> {
                        msg.text = mView.context.getString(R.string.chat_group_change_add_user, contactMap[modification.userUUID.toUUID()]!!.name, contactMap[message.senderUUID]!!.name)
                    }
                }
            }

            activity.registerForContextMenu(mView)
        }
    }

    inner class TimeStatusViewHolder(mView: View) : AbstractViewHolder(mView) {
        private val timeView: TextView = mView.findViewById<TextView>(R.id.chatTimeInfoTimeView) as TextView
        private val statusView: ImageView = mView.findViewById<ImageView>(R.id.chatTimeInfoStatusView) as ImageView

        override fun setComponent(component: Any) {
            component as TimeStatusChatInfo
            val layout = mView.findViewById<LinearLayout>(R.id.chatTimeInfoLayout)
            layout.gravity = if (component.isClient) Gravity.END else Gravity.START
            timeView.text = SimpleDateFormat.getTimeInstance().format(Date(component.time))
            when (component.status) {
                MessageStatus.WAITING -> statusView.setImageResource(R.drawable.waiting)
                MessageStatus.SENT -> statusView.setImageResource(R.drawable.sent)
                MessageStatus.RECEIVED -> statusView.setImageResource(R.drawable.received)
                MessageStatus.SEEN -> statusView.setImageResource(R.drawable.seen)
            }
        }
    }

    inner class GroupInviteViewHolder(mView: View) : ChatMessageViewHolder(mView) {
        private val button: Button = mView.findViewById(R.id.group_invite_button)

        override fun setComponent(component: Any) {
            component as ChatMessageWrapper
            val layout = mView.findViewById<LinearLayout>(R.id.group_invite_bubble_layout) as LinearLayout
            val parentLayout = mView.findViewById<LinearLayout>(R.id.group_invite_parent_layout) as LinearLayout
            // if message is mine then align to right
            if (component.client) {
                layout.setBackgroundResource(R.drawable.bubble_right)
                parentLayout.gravity = Gravity.END
            } else {
                layout.setBackgroundResource(R.drawable.bubble_left)
                parentLayout.gravity = Gravity.START
                val paddingStart = button.paddingStart
                val paddingEnd = button.paddingEnd
                button.setPadding(paddingEnd, button.paddingTop, paddingStart, button.paddingBottom)
            }

            val message = component.message
            message as ChatMessageGroupInvite
            button.text = mView.context.getString(R.string.chat_group_invite_message) + "\n" + message.groupName

            button.setOnClickListener {
                val i = Intent(mView.context, ChatActivity::class.java)
                i.putExtra("chatInfo", ChatInfo(message.chatUUID.toUUID(), message.groupName, ChatType.GROUP, message.roleMap.keys.map { ChatReceiver(it, null, ChatReceiver.ReceiverType.USER) }))
                mView.context.startActivity(i)
            }

            activity.registerForContextMenu(mView)
        }
    }

    inner class VoiceMessageViewHolder(mView: View) : ChatMessageViewHolder(mView) {

        private val playButton: ImageView = mView.findViewById(R.id.chatMessageVoiceMessagePlayButton)
        private val uploadBar: ProgressBar = mView.findViewById(R.id.chatMessageVoiceMessageProgressBar)
        private val timeDisplay: TextView = mView.findViewById(R.id.chatMessageVoiceMessageText)
        private val watchBar: SeekBar = mView.findViewById(R.id.chatMessageVoiceMessageWatchBar)

        var isPlaying = false
        var mediaPlayer: MediaPlayer = MediaPlayer()
        var isInitialized = false
        var hasFinished = false

        var isDownloaded = false
        var isUploaded = false

        lateinit var message: ChatMessageVoiceMessage

        override fun setComponent(component: Any) {
            component as ChatMessageWrapper

            val layout = mView.findViewById<LinearLayout>(R.id.bubble_layout) as LinearLayout
            val parentLayout = mView.findViewById<LinearLayout>(R.id.bubble_layout_parent) as LinearLayout
            // if message is mine then align to right
            if (component.client) {
                layout.setBackgroundResource(R.drawable.bubble_right)
                parentLayout.gravity = Gravity.END
            } else {
                layout.setBackgroundResource(R.drawable.bubble_left)
                parentLayout.gravity = Gravity.START
            }

            message = component.message as ChatMessageVoiceMessage

            isDownloaded = isReferenceDownloaded(message.referenceUUID, FileType.AUDIO)

            isUploaded = isReferenceUploaded(message.referenceUUID)

            uploadBar.max = 100
            watchBar.progress = 0

            watchBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                private var userDragging = false

                override fun onProgressChanged(seekBar: SeekBar, p1: Int, p2: Boolean) {
                    if (!hasFinished && isInitialized && userDragging) {
                        mediaPlayer.seekTo(p1)
                    }
                }

                override fun onStartTrackingTouch(p0: SeekBar?) {
                    userDragging = true
                }

                override fun onStopTrackingTouch(p0: SeekBar?) {
                    userDragging = false
                }
            })

            if (isUploaded) {
                playButton.setImageResource(if (isDownloaded) android.R.drawable.ic_media_play else android.R.drawable.ic_menu_upload)
                playButton.rotation = if (isDownloaded) 0f else 180f

            } else {
                playButton.setImageResource(android.R.drawable.ic_menu_upload)
                playButton.rotation = 0f
            }
            timeDisplay.text = "${convertSeconds(0)}-${convertSeconds(message.durationSeconds.toInt())}"

            playButton.setOnClickListener {
                if (!isDownloaded || !isUploaded) {
                    playButton.visibility = View.GONE
                    uploadBar.visibility = View.VISIBLE
                    uploadBar.progress = 0

                    if (!isDownloaded) {
                        downloadAudio(activity, KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, message.referenceUUID, chatInfo.chatType, message.fileHash)
                    } else if (!isUploaded) {
                        uploadAudio(activity, KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, message.referenceUUID,
                                getReferenceFile(chatInfo.chatUUID, message.referenceUUID, FileType.AUDIO, activity.filesDir))
                    }
                } else {
                    if (!isInitialized) {
                        mediaPlayer = MediaPlayer()
                        mediaPlayer.setDataSource(getReferenceFile(chatInfo.chatUUID, message.referenceUUID, FileType.AUDIO, mView.context.filesDir).absolutePath)
                        mediaPlayer.prepare()
                        mediaPlayer.setOnCompletionListener {
                            isPlaying = false
                            watchBar.progress = 1
                            playButton.setImageResource(android.R.drawable.ic_media_play)
                            mediaPlayer.release()
                            isInitialized = false
                            timeDisplay.text = "${convertSeconds(0)}-${convertSeconds(message.durationSeconds.toInt())}"
                        }
                        watchBar.max = mediaPlayer.duration

                        val handler = Handler()
                        handler.post(object : Runnable {
                            override fun run() {
                                if (isPlaying) {
                                    watchBar.progress = mediaPlayer.currentPosition
                                    val av = watchBar.progress.toDouble() / watchBar.max.toDouble()

                                    timeDisplay.text = "${convertSeconds(Math.floor(message.durationSeconds.toDouble() * av).toInt())}-${convertSeconds(message.durationSeconds.toInt())}"
                                }
                                if (hasFinished) {
                                    watchBar.progress = watchBar.max
                                    hasFinished = false
                                }
                                handler.postDelayed(this, 500)
                            }
                        })

                        isInitialized = true
                    }

                    if (!isPlaying) {
                        mediaPlayer.start()
                        isPlaying = true

                        playButton.setImageResource(android.R.drawable.ic_media_pause)
                    } else {
                        mediaPlayer.pause()
                        isPlaying = false

                        playButton.setImageResource(android.R.drawable.ic_media_play)
                    }
                }
            }
        }

        override fun broadcast(target: String, intent: Intent) {
            when (target) {
                "de.intektor.kentai.uploadReferenceStarted" -> {
                    val referenceUUID = intent.getSerializableExtra("referenceUUID") as UUID
                    if (referenceUUID == message.referenceUUID) {
                        playButton.visibility = View.GONE
                        uploadBar.visibility = View.VISIBLE
                        uploadBar.progress = 0
                    }
                }

                "de.intektor.kentai.uploadReferenceFinished" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val successful = intent.getBooleanExtra("successful", false)
                    if (referenceUUID == message.referenceUUID) {
                        playButton.visibility = View.VISIBLE
                        uploadBar.visibility = View.GONE

                        if (successful) {
                            playButton.setImageResource(android.R.drawable.ic_media_play)
                            isUploaded = true
                            isDownloaded = true
                        } else {
                            playButton.setImageResource(android.R.drawable.ic_menu_upload)
                            playButton.rotation = 180f
                        }
                    }
                }

                "de.intektor.kentai.uploadProgress" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val progress = intent.getDoubleExtra("progress", 0.0)
                    if (referenceUUID == message.referenceUUID) {
                        uploadBar.progress = (progress * 100).toInt()
                    }
                }

                "de.intektor.kentai.downloadReferenceFinished" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val successful = intent.getBooleanExtra("successful", false)
                    if (referenceUUID == message.referenceUUID) {
                        playButton.visibility = View.VISIBLE
                        uploadBar.visibility = View.GONE

                        if (successful) {
                            playButton.setImageResource(android.R.drawable.ic_media_play)
                            isUploaded = true
                            isDownloaded = true
                        } else {
                            playButton.setImageResource(android.R.drawable.ic_menu_upload)
                            playButton.rotation = 180f
                        }
                    }
                }

                "de.intektor.kentai.downloadProgress" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val progress = intent.getDoubleExtra("progress", 0.0)
                    if (referenceUUID == message.referenceUUID) {
                        uploadBar.progress = (progress * 100).toInt()
                    }
                }
            }
        }

        private fun convertSeconds(seconds: Int): String {
            var s = seconds
            var m = 0
            while (s >= 60) {
                s -= 60
                m++
            }
            return String.format("%02d:%02d", m, s)
        }
    }

    inner class ImageMessageViewHolder(view: View) : ChatMessageViewHolder(view) {

        private val imageView = mView.findViewById<ImageView>(R.id.chatMessageImageView)
        private val loadBar = mView.findViewById<ProgressBar>(R.id.chatMessageImageViewLoadBar)
        private val loadButton = mView.findViewById<ImageButton>(R.id.chatMessageImageViewLoadButton)

        private lateinit var message: ChatMessage

        private var isUploaded = false
        private var isDownloaded = false

        override fun setComponent(component: Any) {
            component as ChatMessageWrapper
            message = component.message as ChatMessageImage

            val message = message as ChatMessageImage

            val referenceFile = getReferenceFile(chatInfo.chatUUID, message.referenceUUID, FileType.IMAGE, activity.filesDir)

            isDownloaded = isReferenceDownloaded(message.referenceUUID, FileType.IMAGE)
            isUploaded = isReferenceUploaded(message.referenceUUID)

            loadBar.max = 100

            imageView.scaleType = ImageView.ScaleType.CENTER_CROP

            imageView.setOnClickListener {
                val showImage = Intent(Intent.ACTION_VIEW)
                showImage.setDataAndType(FileProvider.getUriForFile(activity, activity.applicationContext.packageName + ".kentai.android.GenericFileProvider", referenceFile), "image/*")
                showImage.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                activity.startActivity(showImage)
            }

            if (!isDownloaded || !isUploaded) {
                loadButton.visibility = View.VISIBLE
                loadButton.setImageResource(android.R.drawable.ic_menu_upload)

                loadButton.setOnClickListener {
                    loadButton.visibility = View.GONE
                    loadBar.visibility = View.VISIBLE
                    loadBar.progress = 0
                    imageView.visibility = View.GONE
                    if (component.client) {
                        uploadImage(activity, KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, message.referenceUUID,
                                referenceFile)
                    } else {
                        downloadImage(activity, KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, message.referenceUUID, chatInfo.chatType, message.hash)
                    }
                }
            }

            if (isDownloaded) {
                val b = BitmapFactory.decodeFile(referenceFile.path)
                imageView.setImageBitmap(b)
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_upload)
            }

            val layout = mView.findViewById<LinearLayout>(R.id.bubble_layout) as LinearLayout
            val parentLayout = mView.findViewById<LinearLayout>(R.id.bubble_layout_parent) as LinearLayout
            // if message is mine then align to right
            if (component.client) {
                layout.setBackgroundResource(R.drawable.bubble_right)
                parentLayout.gravity = Gravity.END
            } else {
                layout.setBackgroundResource(R.drawable.bubble_left)
                parentLayout.gravity = Gravity.START
            }
        }

        override fun broadcast(target: String, intent: Intent) {
            when (target) {
                "de.intektor.kentai.uploadReferenceStarted" -> {
                    val referenceUUID = intent.getSerializableExtra("referenceUUID") as UUID
                    if (referenceUUID == message.referenceUUID) {
                        loadButton.visibility = View.GONE
                        imageView.visibility = View.GONE
                        loadBar.visibility = View.VISIBLE
                        loadBar.progress = 0
                    }
                }

                "de.intektor.kentai.uploadReferenceFinished" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val successful = intent.getBooleanExtra("successful", false)
                    if (referenceUUID == message.referenceUUID) {
                        if (successful) {
                            imageView.visibility = View.VISIBLE
                            loadBar.visibility = View.GONE
                            isUploaded = true
                            isDownloaded = true
                        } else {
                            loadBar.visibility = View.GONE
                            loadButton.visibility = View.VISIBLE
                            imageView.visibility = View.VISIBLE
                        }
                    }
                }

                "de.intektor.kentai.uploadProgress" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val progress = intent.getDoubleExtra("progress", 0.0)
                    if (referenceUUID == message.referenceUUID) {
                        loadBar.progress = (progress * 100).toInt()
                    }
                }

                "de.intektor.kentai.downloadReferenceFinished" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val successful = intent.getBooleanExtra("successful", false)
                    if (referenceUUID == message.referenceUUID) {
                        if (successful) {
                            isUploaded = true
                            isDownloaded = true
                            loadButton.visibility = View.GONE
                            loadBar.visibility = View.GONE
                            imageView.visibility = View.VISIBLE

                            val b = BitmapFactory.decodeFile(getReferenceFile(chatInfo.chatUUID, message.referenceUUID, FileType.IMAGE, activity.filesDir).path)
                            imageView.setImageBitmap(b)
                        } else {
                            loadBar.visibility = View.GONE
                            loadButton.visibility = View.VISIBLE
                            imageView.visibility = View.VISIBLE
                        }
                    }
                }

                "de.intektor.kentai.downloadProgress" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val progress = intent.getDoubleExtra("progress", 0.0)
                    if (referenceUUID == message.referenceUUID) {
                        loadBar.progress = (progress * 100).toInt()
                    }
                }
            }
        }
    }

    inner class VideoMessageViewHolder(view: View) : ChatMessageViewHolder(view) {

        private val imageView = mView.findViewById<ImageView>(R.id.chatMessageVideoView)
        private val loadBar = mView.findViewById<ProgressBar>(R.id.chatMessageVideoViewLoadBar)
        private val loadButton = mView.findViewById<ImageButton>(R.id.chatMessageVideoViewLoadButton)

        private lateinit var message: ChatMessage

        private var isUploaded = false
        private var isDownloaded = false

        override fun setComponent(component: Any) {
            component as ChatMessageWrapper
            message = component.message as ChatMessageVideo

            val message = message as ChatMessageVideo

            val referenceFile = getReferenceFile(chatInfo.chatUUID, message.referenceUUID, FileType.VIDEO, activity.filesDir)

            isDownloaded = isReferenceDownloaded(message.referenceUUID, FileType.VIDEO)
            isUploaded = isReferenceUploaded(message.referenceUUID)

            loadBar.max = 100

            imageView.scaleType = ImageView.ScaleType.CENTER_CROP

            imageView.setOnClickListener {
                val showVideo = Intent(Intent.ACTION_VIEW)
                showVideo.setDataAndType(FileProvider.getUriForFile(activity, activity.applicationContext.packageName + ".kentai.android.GenericFileProvider", referenceFile), "video/*")
                showVideo.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                activity.startActivity(showVideo)
            }

            if (!isDownloaded || !isUploaded) {
                loadButton.visibility = View.VISIBLE
                loadButton.setImageResource(android.R.drawable.ic_menu_upload)

                loadButton.setOnClickListener {
                    loadButton.visibility = View.GONE
                    loadBar.visibility = View.VISIBLE
                    loadBar.progress = 0
                    imageView.visibility = View.GONE
                    if (component.client) {
                        uploadVideo(activity, KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, message.referenceUUID,
                                referenceFile)
                    } else {
                        downloadVideo(activity, KentaiClient.INSTANCE.dataBase, chatInfo.chatUUID, message.referenceUUID, chatInfo.chatType, message.hash)
                    }
                }
            }

            if (isDownloaded) {
                val b = ThumbnailUtils.createVideoThumbnail(referenceFile.path, MediaStore.Images.Thumbnails.MINI_KIND)
                imageView.setImageBitmap(b)
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_upload)
            }

            val layout = mView.findViewById<LinearLayout>(R.id.bubble_layout) as LinearLayout
            val parentLayout = mView.findViewById<LinearLayout>(R.id.bubble_layout_parent) as LinearLayout
            // if message is mine then align to right
            if (component.client) {
                layout.setBackgroundResource(R.drawable.bubble_right)
                parentLayout.gravity = Gravity.END
            } else {
                layout.setBackgroundResource(R.drawable.bubble_left)
                parentLayout.gravity = Gravity.START
            }
        }

        override fun broadcast(target: String, intent: Intent) {
            when (target) {
                "de.intektor.kentai.uploadReferenceStarted" -> {
                    val referenceUUID = intent.getSerializableExtra("referenceUUID") as UUID
                    if (referenceUUID == message.referenceUUID) {
                        loadButton.visibility = View.GONE
                        imageView.visibility = View.GONE
                        loadBar.visibility = View.VISIBLE
                        loadBar.progress = 0
                    }
                }

                "de.intektor.kentai.uploadReferenceFinished" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val successful = intent.getBooleanExtra("successful", false)
                    if (referenceUUID == message.referenceUUID) {
                        if (successful) {
                            imageView.visibility = View.VISIBLE
                            loadBar.visibility = View.GONE
                            isUploaded = true
                            isDownloaded = true

                            val b = ThumbnailUtils.createVideoThumbnail(getReferenceFile(chatInfo.chatUUID, referenceUUID, FileType.AUDIO, activity.filesDir).path, MediaStore.Images.Thumbnails.MINI_KIND)
                            imageView.setImageBitmap(b)
                        } else {
                            loadBar.visibility = View.GONE
                            loadButton.visibility = View.VISIBLE
                            imageView.visibility = View.VISIBLE
                        }
                    }
                }

                "de.intektor.kentai.uploadProgress" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val progress = intent.getDoubleExtra("progress", 0.0)
                    if (referenceUUID == message.referenceUUID) {
                        loadBar.progress = (progress * 100).toInt()
                    }
                }

                "de.intektor.kentai.downloadReferenceFinished" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val successful = intent.getBooleanExtra("successful", false)
                    if (referenceUUID == message.referenceUUID) {
                        if (successful) {
                            isUploaded = true
                            isDownloaded = true
                            loadButton.visibility = View.GONE
                            loadBar.visibility = View.GONE
                            imageView.visibility = View.VISIBLE

                            val b = ThumbnailUtils.createVideoThumbnail(getReferenceFile(chatInfo.chatUUID, referenceUUID, FileType.AUDIO, activity.filesDir).path, MediaStore.Images.Thumbnails.MINI_KIND)
                            imageView.setImageBitmap(b)
                        } else {
                            loadBar.visibility = View.GONE
                            loadButton.visibility = View.VISIBLE
                            imageView.visibility = View.VISIBLE
                        }
                    }
                }

                "de.intektor.kentai.downloadProgress" -> {
                    val referenceUUID = intent.getStringExtra("referenceUUID").toUUID()
                    val progress = intent.getDoubleExtra("progress", 0.0)
                    if (referenceUUID == message.referenceUUID) {
                        loadBar.progress = (progress * 100).toInt()
                    }
                }
            }
        }
    }

    private fun isReferenceDownloaded(referenceUUID: UUID, fileType: FileType): Boolean =
            getReferenceFile(chatInfo.chatUUID, referenceUUID, fileType, activity.filesDir).exists()

    private fun isReferenceUploaded(referenceUUID: UUID): Boolean {
        return KentaiClient.INSTANCE.dataBase.rawQuery("SELECT state FROM reference_upload_table WHERE reference_uuid = ?", arrayOf(referenceUUID.toString())).use { query ->
            if (query.moveToNext()) {
                val uploadState = UploadState.values()[query.getInt(0)]
                when (uploadState) {
                    UploadState.IN_PROGRESS -> false
                    UploadState.UPLOADED -> true
                }
            } else {
                false
            }
        }
    }

    inner abstract class ChatMessageViewHolder(view: View) : AbstractViewHolder(view)

    inner class UsernameChatInfoViewHolder(view: View) : AbstractViewHolder(view) {

        val text: TextView = view.findViewById(R.id.chatSenderInfoText)

        override fun setComponent(component: Any) {
            component as UsernameChatInfo
            text.text = component.username
            text.setTextColor(Color.parseColor("#${component.color}"))
        }
    }

    abstract inner class AbstractViewHolder(val mView: View) : RecyclerView.ViewHolder(mView) {
        abstract fun setComponent(component: Any)

        open fun broadcast(target: String, intent: Intent) {

        }
    }

    class UsernameChatInfo(val username: String, val color: String)

    class TimeStatusChatInfo(val time: Long, var status: MessageStatus, val isClient: Boolean)
}