package de.intektor.mercury.ui.chat.adapter.chat

/**
 * @author Intektor
 */
//class NineGagViewHolder(view: View, chatAdapter: ChatAdapter) : ChatMessageViewHolder<MessageText, >(view, chatAdapter) {
//
//    private val title: TextView = view.findViewById(R.id.nineGagTitle)
//    private val video: VideoView = view.findViewById(R.id.nineGagVideoView)
//    private val thumbnail: ImageView = view.findViewById(R.id.nineGagThumbnail)
//    private val playButton: ImageView = view.findViewById(R.id.nineGagPlayButton)
//    private val gifButton: TextView = view.findViewById(R.id.nineGagGifPlay)
//    private val load: ProgressBar = view.findViewById(R.id.nineGagDownloadBar)
//
//    override fun bind(component: ChatAdapter.ChatAdapterWrapper) {
//        super.bind(component)
//
//        val item = component.item as ReferenceHolder
//
//        val gagTitle = item.chatMessageInfo.message.text.substringBefore("https://9gag.com/gag/")
//        val gagId = getGagId(item.chatMessageInfo.message.text)
//
//        gifButton.visibility = View.GONE
//        video.visibility = View.GONE
//
//        if (video.isPlaying) {
//            video.pause()
//        }
//
//        title.text = gagTitle
//
//        load.visibility = View.GONE
//
//        thumbnail.visibility = View.VISIBLE
//
//        if (!item.isFinished) {
//            if (!item.isInternetInProgress) {
//                playButton.setImageResource(R.drawable.ic_file_download_white_24dp)
//                load.visibility = View.GONE
//                thumbnail.setImageResource(R.mipmap.ic_launcher)
//                playButton.visibility = View.VISIBLE
//
//                registerForEditModePress(playButton) {
//                    downloadNineGag(gagId, getGagUUID(item.chatMessageInfo.message.text), chatAdapter.chatInfo.chatUUID, itemView.context)
//                    item.isInternetInProgress = true
//                    item.progress = 0
//                    chatAdapter.notifyItemChanged(adapterPosition)
//                }
//            } else {
//                playButton.setImageResource(R.drawable.ic_cancel_white_24dp)
//                load.visibility = View.VISIBLE
//                load.progress = item.progress
//                load.max = 100
//                thumbnail.setImageResource(R.mipmap.ic_launcher)
//                registerForEditModePress(playButton) {
//                    cancelReferenceDownload(getGagUUID(item.chatMessageInfo.message.text), itemView.context)
//                    item.progress = 0
//                    item.isInternetInProgress = false
//                    chatAdapter.notifyItemChanged(adapterPosition)
//                }
//            }
//        } else {
//            val imageFile = getNineGagFile(gagId, NineGagType.IMAGE, itemView.context)
//            val videoFile = getNineGagFile(gagId, NineGagType.VIDEO, itemView.context)
//
//            if (imageFile.exists()) {
//                Picasso.get().load(imageFile).placeholder(R.drawable.ic_account_circle_white_24dp).into(thumbnail)
//
//                video.visibility = View.GONE
//                playButton.visibility = View.GONE
//
//                registerForEditModePress(thumbnail) {
//                    val i = Intent(itemView.context, ChatMediaViewActivity::class.java)
//                    i.putExtra(KEY_FILE_URI, Uri.fromFile(imageFile))
//                    i.putExtra(KEY_MEDIA_TYPE, FileType.IMAGE)
//                    i.putExtra(KEY_MESSAGE_UUID, item.chatMessageInfo.message.id.toUUID())
//                    itemView.context.startActivity(i)
//                }
//            } else if (videoFile.exists()) {
//                videoPicasso(itemView.context).loadVideoThumbnailFull(videoFile.path).into(thumbnail)
//
//                video.visibility = View.GONE
//                playButton.visibility = View.VISIBLE
//                playButton.setImageResource(R.drawable.ic_play_arrow_white_24dp)
//                registerForEditModePress(playButton) {
//                    playButton.visibility = View.GONE
//                    thumbnail.visibility = View.GONE
//
//                    video.setVideoURI(Uri.fromFile(videoFile))
//
//                    video.start()
//
//                    video.visibility = View.VISIBLE
//                }
//
//                thumbnail.setOnClickListener {
//                    val i = Intent(itemView.context, ChatMediaViewActivity::class.java)
//                    i.putExtra(KEY_FILE_URI, Uri.fromFile(videoFile))
//                    i.putExtra(KEY_MEDIA_TYPE, FileType.VIDEO)
//                    i.putExtra(KEY_MESSAGE_UUID, item.chatMessageInfo.message.id.toUUID())
//                    itemView.context.startActivity(i)
//                }
//
//                video.setOnCompletionListener {
//                    video.seekTo(0)
//                    video.start()
//                }
//
//                video.setOnClickListener {
//                    video.pause()
//                    video.seekTo(0)
//                    video.visibility = View.GONE
//
//                    thumbnail.visibility = View.VISIBLE
//                    playButton.visibility = View.VISIBLE
//                }
//            }
//        }
//
//        val layout = itemView.findViewById(R.id.bubble_layout) as LinearLayout
//        if (item.chatMessageInfo.client) {
//            layout.background = getAttrDrawable(itemView.context, R.attr.bubble_right)
//        } else {
//            layout.background = getAttrDrawable(itemView.context, R.attr.bubble_left)
//        }
//
//        registerForEditModeLongPress(title)
//        registerForEditModePress(title)
//        registerForEditModeLongPress(playButton)
//        registerForEditModeLongPress(thumbnail)
//        registerForEditModeLongPress(gifButton)
//    }
//}