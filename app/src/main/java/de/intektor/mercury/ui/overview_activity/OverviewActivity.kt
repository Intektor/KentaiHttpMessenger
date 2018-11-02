package de.intektor.mercury.ui.overview_activity

import android.Manifest
import android.accounts.AccountManager
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.SearchView
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.ExponentialBackOff
import com.google.api.services.drive.Drive
import com.google.firebase.iid.FirebaseInstanceId
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.action.ActionMessageStatusChange
import de.intektor.mercury.action.chat.ActionChatMessageReceived
import de.intektor.mercury.action.chat.ActionInitChatFinished
import de.intektor.mercury.action.group.ActionGroupModificationReceived
import de.intektor.mercury.android.getAttrDrawable
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.backup.BackupService
import de.intektor.mercury.backup.BackupService.Companion.PREF_ACCOUNT_NAME
import de.intektor.mercury.chat.*
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.firebase.UploadTokenTask
import de.intektor.mercury.task.handleNotification
import de.intektor.mercury.ui.*
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.chat.adapter.chat.HeaderItemDecoration
import de.intektor.mercury.ui.overview_activity.fragment.ChatListViewAdapter
import de.intektor.mercury.util.KEY_CHAT_UUID
import de.intektor.mercury.util.KEY_SUCCESSFUL
import de.intektor.mercury_common.chat.MessageStatus
import de.intektor.mercury_common.chat.data.MessageStatusUpdate
import de.intektor.mercury_common.chat.data.group_modification.GroupModificationChangeName
import kotlinx.android.synthetic.main.activity_overview.*
import kotlinx.android.synthetic.main.fragment_chat_list.*
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.concurrent.thread

class OverviewActivity : AppCompatActivity() {

    private var mSectionsPagerAdapter: SectionsPagerAdapter? = null

    private lateinit var credential: GoogleAccountCredential

    /**
     * true = create a backup
     * false = use a backup
     */
    private var createChatBackup = false

    private var selectedFilesArray: List<com.google.api.services.drive.model.File>? = null

    @Volatile
    private var driveServiceInitialized = false

    @Volatile
    private lateinit var driveService: Drive

    private var currentTheme: Int = -1

    private lateinit var searchAdapter: SearchAdapter

    private val searchList = mutableListOf<Any>()

    private var currentMessagesOffset = 0

    private val chatList = mutableListOf<ChatListViewAdapter.ChatItem>()
    private var currentQuery = ""

    private lateinit var initChatListener: BroadcastReceiver
    private lateinit var chatMessageListener: BroadcastReceiver
    private lateinit var messageStatusChangeReceiver: BroadcastReceiver
    private val groupModificationReceiver: BroadcastReceiver = GroupModificationReceiver()

    companion object {
        private const val SIGN_IN_REQUEST_CODE = 0
        private const val REQUEST_ACCOUNT_PICKER = 1000
        private const val REQUEST_AUTHORIZATION = 1001
        private const val REQUEST_GOOGLE_PLAY_SERVICES = 1002
        private const val REQUEST_PERMISSION_GET_ACCOUNTS = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!ClientPreferences.getIsRegistered(this)) {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)

            finish()

            return
        }

        currentTheme = getSelectedTheme(this, false)

        setTheme(currentTheme)

        setContentView(R.layout.activity_overview)

        setSupportActionBar(activity_overview_toolbar)

        activity_overview_appbar_layout.context.setTheme(currentTheme)

        activity_overview_toolbar.context.setTheme(currentTheme)

        mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)

        activity_overview_viewpager.adapter = mSectionsPagerAdapter

        activity_overview_tablayout.setupWithViewPager(activity_overview_viewpager)

        searchAdapter = SearchAdapter(searchList) { item ->
            if (item is ChatListViewAdapter.ChatItem) {
                ChatActivity.launch(this, item.chatInfo)
            } else if (item is SearchAdapter.ChatMessageSearch) {
                ChatActivity.launch(this, item.chatInfo, item.message.message.messageCore.messageUUID)
            }
        }

        activity_overview_rv_search_layout.adapter = searchAdapter
        activity_overview_rv_search_layout.layoutManager = LinearLayoutManager(this)

        activity_overview_rv_search_layout.addItemDecoration(HeaderItemDecoration(activity_overview_rv_search_layout, object : HeaderItemDecoration.StickyHeaderInterface {
            override fun getHeaderPositionForItem(itemPosition: Int): Int {
                var i = itemPosition
                while (true) {
                    if (isHeader(i)) return i
                    i--
                }
            }

            override fun getHeaderLayout(headerPosition: Int): Int = R.layout.search_label

            override fun bindHeaderData(header: View, headerPosition: Int) {
                SearchAdapter.SearchLabelViewHolder(header).bind(searchList[headerPosition] as SearchAdapter.SearchHeader)
            }

            override fun isHeader(itemPosition: Int): Boolean = searchList[itemPosition] is SearchAdapter.SearchHeader
        }))

        activity_overview_rv_search_layout.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                val scrollPercent = (activity_overview_rv_search_layout.computeVerticalScrollOffset().toFloat() + activity_overview_rv_search_layout.height) / activity_overview_rv_search_layout.computeVerticalScrollRange().toFloat()

                if (scrollPercent >= 0.9f) {
                    loadMoreSearchMessages()
                }
            }
        })

        initChatListener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val chatUUID = intent.getSerializableExtra(KEY_CHAT_UUID) as UUID
                val successful = intent.getBooleanExtra(KEY_SUCCESSFUL, false)
                updateInitChat(chatUUID, successful)
            }
        }

        chatMessageListener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val (chatMessage, chatUuid) = ActionChatMessageReceived.getData(intent)

                val mercuryClient = context.mercuryClient()

                val chatType = getChatType(mercuryClient.dataBase, chatUuid) ?: return

                val senderContact = getContact(mercuryClient.dataBase, chatMessage.messageCore.senderUUID)

                handleNotification(context,
                        chatUuid,
                        chatMessage)

                val chatInfo = getChatInfo(chatUuid, mercuryClient.dataBase) ?: return

                val latestMessage = getChatMessages(context, mercuryClient.dataBase, "chat_uuid = ?", arrayOf(chatUuid.toString()), "time DESC", "1")
                val unreadMessages = ChatUtil.getUnreadMessagesFromChat(mercuryClient.dataBase, chatUuid)

                if (getCurrentChats().none { it.chatInfo.chatUUID == chatUuid }) {
                    addChat(ChatListViewAdapter.ChatItem(chatInfo, latestMessage.firstOrNull(), unreadMessages, ChatUtil.isChatInitialized(mercuryClient.dataBase, chatUuid)))
                } else {
                    updateLatestChatMessage(chatUuid, ChatMessageInfo(chatMessage, chatMessage.messageCore.senderUUID == ClientPreferences.getClientUUID(context), chatUuid), unreadMessages)
                }
            }
        }

        messageStatusChangeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val (chatUuid, messageUuid, messageStatus) = ActionMessageStatusChange.getData(intent)

                updateLatestChatMessageStatus(chatUuid, messageStatus, messageUuid)
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_overview, menu)
        menu.findItem(R.id.new_chat).icon = getAttrDrawable(this, R.attr.ic_message)
        menu.findItem(R.id.new_contact).icon = getAttrDrawable(this, R.attr.ic_person_add)
        val searchItem = menu.findItem(R.id.menu_activity_overview_item_search)

        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                doSearch(query)
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                doSearch(newText)
                return true
            }

            private fun doSearch(query: String?) {
                if (query == null || query.isBlank()) {
                    activity_overview_tablayout.visibility = View.VISIBLE
                    activity_overview_viewpager.visibility = View.VISIBLE
                    activity_overview_rv_search_layout.visibility = View.GONE
                } else {
                    currentMessagesOffset = 0
                    currentQuery = query

                    activity_overview_tablayout.visibility = View.GONE
                    activity_overview_viewpager.visibility = View.GONE
                    activity_overview_rv_search_layout.visibility = View.VISIBLE

                    val mercuryClient = applicationContext as MercuryClient

                    if (chatList.isEmpty()) {
                        chatList += readChats(mercuryClient.dataBase, this@OverviewActivity)
                    }

                    val foundChats = chatList.filter { it.chatInfo.chatName.contains(query, true) }

                    searchList.clear()
                    searchList += SearchAdapter.SearchHeader(SearchAdapter.SearchHeader.SearchHeaderType.CHATS)
                    searchList.addAll(foundChats)
                    searchList += SearchAdapter.SearchHeader(SearchAdapter.SearchHeader.SearchHeaderType.MESSAGES)
                    searchAdapter.notifyDataSetChanged()

                    loadMoreSearchMessages()
                }
            }
        })
        return true
    }

    private fun loadMoreSearchMessages() {
        val mercuryClient = applicationContext as MercuryClient
        val messages = MessageUtil.lookupMessages(this, mercuryClient.dataBase, currentQuery, 20, currentMessagesOffset).map { message ->
            SearchAdapter.ChatMessageSearch(message.chatMessageInfo, chatList.first { it.chatInfo.chatUUID == message.chatMessageInfo.chatUUID }.chatInfo)
        }

        currentMessagesOffset += messages.size

        val oldSize = searchList.size

        searchList.addAll(messages)
        searchAdapter.notifyItemRangeInserted(oldSize, messages.size)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.new_chat_button_user -> {
                val i = Intent(this@OverviewActivity, NewChatUserActivity::class.java)
                startActivity(i)
            }
            R.id.new_chat_button_group -> {
                val i = Intent(this@OverviewActivity, NewChatGroupActivity::class.java)
                startActivity(i)
            }
            R.id.new_contact -> {
                val i = Intent(this@OverviewActivity, AddContactActivity::class.java)
                startActivity(i)
            }
            R.id.phone_transfer -> {
                val i = Intent(this@OverviewActivity, TransferScannerActivity::class.java)
                startActivity(i)
            }
            R.id.create_chat_backup -> {
                doDriveStuff()
                createChatBackup = true
            }
            R.id.use_chat_backup -> {
                doDriveStuff()
                createChatBackup = false
            }
            R.id.menuOverviewUpdateFCMKey -> {
                UpdateFCMKeyTask(applicationContext as MercuryClient).execute()
            }
            R.id.menuOverviewSettings -> {
                val openSettingsIntent = Intent(this, SettingsOverviewActivity::class.java)
                startActivity(openSettingsIntent)
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun addChat(item: ChatListViewAdapter.ChatItem) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.activity_overview_viewpager + ":0") as FragmentChatsOverview
        fragment.addChat(item)
        fragment.shownChatList.sortBy {
            it.lastChatMessage?.chatMessageInfo?.message?.messageCore?.timeCreated ?: 0
        }
        fragment.fragment_chat_list_rv_chats.adapter?.notifyDataSetChanged()
    }

    fun getCurrentChats(): List<ChatListViewAdapter.ChatItem> {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.activity_overview_viewpager + ":0") as FragmentChatsOverview
        return fragment.shownChatList
    }

    fun updateLatestChatMessage(chatUUID: UUID, lastMessage: ChatMessageInfo, unreadMessages: Int) {
        if (lastMessage.message.messageData is MessageStatusUpdate) return
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.activity_overview_viewpager + ":0") as FragmentChatsOverview
        val chatItem = fragment.chatMap[chatUUID] ?: return

        chatItem.lastChatMessage = ChatMessageWrapper(lastMessage, MessageStatus.WAITING, System.currentTimeMillis())

        chatItem.unreadMessages = unreadMessages
        fragment.shownChatList.sortByDescending {
            it.lastChatMessage?.chatMessageInfo?.message?.messageCore?.timeCreated ?: 0
        }
        fragment.fragment_chat_list_rv_chats.adapter?.notifyDataSetChanged()
    }

    fun updateLatestChatMessageStatus(chatUUID: UUID, status: MessageStatus, messageUUID: UUID) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.activity_overview_viewpager + ":0") as FragmentChatsOverview
        val chatItem = fragment.chatMap[chatUUID]!!
        if (chatItem.lastChatMessage?.chatMessageInfo?.message?.messageCore?.messageUUID == messageUUID) {
            chatItem.lastChatMessage?.latestStatus = status
            chatItem.lastChatMessage?.latestUpdateTime = System.currentTimeMillis()
            fragment.fragment_chat_list_rv_chats.adapter?.notifyDataSetChanged()
        }
    }

    fun updateChatName(chatUUID: UUID, name: String) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.activity_overview_viewpager + ":0") as FragmentChatsOverview
        val chatItem = fragment.chatMap[chatUUID]!!
        chatItem.chatInfo = chatItem.chatInfo.copy(chatName = name)
        fragment.fragment_chat_list_rv_chats.adapter?.notifyDataSetChanged()
    }

    fun updateInitChat(chatUUID: UUID, successful: Boolean) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.activity_overview_viewpager + ":0") as FragmentChatsOverview
        fragment.updateInitChat(chatUUID, successful, false)
    }

    override fun onResume() {
        super.onResume()

        if (currentTheme != getSelectedTheme(this, false)) {
            val i = Intent(this, OverviewActivity::class.java)
            startActivity(i)
            finish()
        }

        registerReceiver(initChatListener, ActionInitChatFinished.getFilter())
        registerReceiver(messageStatusChangeReceiver, ActionMessageStatusChange.getFilter())
        registerReceiver(groupModificationReceiver, ActionGroupModificationReceived.getFilter())
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(initChatListener)
        unregisterReceiver(messageStatusChangeReceiver)
        unregisterReceiver(groupModificationReceiver)
    }

    inner class SectionsPagerAdapter(fm: FragmentManager) : FragmentPagerAdapter(fm) {

        override fun getItem(position: Int): Fragment? {
            when (position) {
                0 -> return FragmentChatsOverview()
                1 -> return FragmentContactsOverview()
            }
            return null
        }

        override fun getCount(): Int = 2

        override fun getPageTitle(position: Int): CharSequence? {
            when (position) {
                0 -> return "CHATS"
                1 -> return "CONTACTS"
            }
            return null
        }
    }

    private fun createAndUploadBackup() {
        val editText = EditText(this)

        editText.setText(R.string.overview_activity_chat_backup_alert_default_name, TextView.BufferType.EDITABLE)

//        AlertDialog.Builder(this)
//                .setView(editText)
//                .setTitle(R.string.overview_activity_chat_backup_alert_title)
//                .setMessage(R.string.overview_activity_chat_backup_alert_message)
//                .setNegativeButton(R.string.overview_activity_chat_backup_alert_cancel, null)
//                .setPositiveButton(R.string.overview_activity_chat_backup_alert_proceed) { _, _ ->
//                    val startServiceIntent = Intent(this, BackupService::class.java)
//                    startService(startServiceIntent)
//                    val i = Intent(Mercury.ACTION_BACKUP)
//                    i.putExtra(BackupService.BACKUP_NAME_EXTRA, editText.text.toString())
//                    sendBroadcast(i)
//                }
//                .show()
    }

    private fun useDriveBackup() {
        thread {
            readyDriveService()

            val finalList = mutableListOf<com.google.api.services.drive.model.File>()
            var pageToken: String? = null

            do {
                val result = driveService.files().list()
                        .setQ("mediaType='application/mercury.backup.zip'")
                        .setSpaces("drive")
                        .setFields("nextPageToken, files(id, name)")
                        .setPageToken(pageToken)
                        .execute()
                pageToken = result.nextPageToken
                finalList += result.files
            } while (pageToken != null)

            runOnUiThread {
                val bundle = Bundle()
                bundle.putStringArray("fileArray", finalList.map { it.name }.toTypedArray())

                selectedFilesArray = finalList

                val fragment = SelectBackupDialogFragment()
                fragment.arguments = bundle
                fragment.show(fragmentManager, "pickBackupFile")
            }
        }
    }

    private fun selectedBackup(index: Int) {
        val mercuryClient = applicationContext as MercuryClient
        val downloadDialog = ProgressDialog.show(this, getString(R.string.overview_activity_chat_backup_use_download_progress_dialog_title),
                getString(R.string.overview_activity_chat_backup_use_download_progress_dialog_message), true)
        val array = selectedFilesArray ?: return

        val file = array[index]

        val tempFile = File(cacheDir.path + "/backup.zip")
        val out = FileOutputStream(tempFile)

        readyDriveService()

        thread {
            driveService.files().get(file.id)
                    .executeMediaAndDownloadTo(out)

            downloadDialog.dismiss()
            runOnUiThread {
                AlertDialog.Builder(this)
                        .setTitle(R.string.overview_activity_chat_backup_use_install_alert_dialog_title)
                        .setMessage(R.string.overview_activity_chat_backup_use_install_alert_dialog_message)
                        .setPositiveButton(R.string.overview_activity_chat_backup_use_install_alert_dialog_proceed) { _, _ ->
                            val progressDialog = ProgressDialog.show(this, getString(R.string.overview_activity_chat_backup_use_install_progress_dialog_title),
                                    getString(R.string.overview_activity_chat_backup_use_install_progress_dialog_message), true)
                            thread {
                                //                                installChatBackup(mercuryClient.dataBase, tempFile, this, mercuryClient)
                                runOnUiThread {
                                    progressDialog.dismiss()
                                    val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.activity_overview_viewpager + ":0") as FragmentChatsOverview
                                    fragment.updateList()
                                }
                            }


                        }
                        .setNegativeButton(R.string.overview_activity_chat_backup_use_install_alert_dialog_cancel) { _, _ -> }
                        .show()
            }
        }
    }

    private fun doDriveStuff() {
        credential = GoogleAccountCredential
                .usingOAuth2(applicationContext, BackupService.SCOPES.asList())
                .setBackOff(ExponentialBackOff())
        readyUpDrive()
    }

    private fun readyUpDrive() {
        if (!isGooglePlayServicesAvailable()) {
            acquireGooglePlayServices()
        } else if (credential.selectedAccountName == null) {
            chooseAccount()
        } else {
            thread {
                try {
                    credential.token
                    runOnUiThread {
                        driveReady()
                    }
                } catch (e: UserRecoverableAuthException) {
                    startActivityForResult(e.intent, REQUEST_AUTHORIZATION)
                }
            }
        }
    }

    private fun driveReady() {
        if (createChatBackup) {
            createAndUploadBackup()
        } else {
            useDriveBackup()
        }
    }

    private fun chooseAccount() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.GET_ACCOUNTS) == PackageManager.PERMISSION_GRANTED) {
            val accountName = applicationContext.getSharedPreferences(BackupService.DRIVE_SHARED_PREFERENCES, Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null)
            if (accountName != null) {
                credential.selectedAccountName = accountName
                readyUpDrive()
            } else {
                startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER)
            }
        } else {
            AlertDialog.Builder(this)
                    .setMessage(R.string.overview_activity_request_google_accounts_permission_message)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.GET_ACCOUNTS), REQUEST_PERMISSION_GET_ACCOUNTS)
                    }
                    .show()
        }
    }

    private fun isGooglePlayServicesAvailable() =
            GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS

    private fun acquireGooglePlayServices() {
        val apiAvailability = GoogleApiAvailability.getInstance()
        val connectionStatusCode = apiAvailability.isGooglePlayServicesAvailable(this)
        if (apiAvailability.isUserResolvableError(connectionStatusCode)) {
            showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode)
        }
    }

    private fun showGooglePlayServicesAvailabilityErrorDialog(connectionStatusCode: Int) {
        GoogleApiAvailability
                .getInstance()
                .getErrorDialog(this, connectionStatusCode, REQUEST_GOOGLE_PLAY_SERVICES)
                .show()
    }

    private fun isDeviceOnline(): Boolean {
        val connectionManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectionManager.activeNetworkInfo
        return networkInfo != null && networkInfo.isConnected
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_GOOGLE_PLAY_SERVICES -> {
                if (resultCode != Activity.RESULT_OK) {
                    AlertDialog.Builder(this)
                            .setTitle(R.string.overview_activity_missing_google_play_service_alert_title)
                            .setMessage(R.string.overview_activity_missing_google_play_service_alert_message)
                            .show()
                } else {
                    readyUpDrive()
                }
            }
            REQUEST_ACCOUNT_PICKER -> {
                if (resultCode == Activity.RESULT_OK && data != null && data.extras != null) {
                    val accountName = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
                    if (accountName != null) {
                        val settings = applicationContext.getSharedPreferences(BackupService.DRIVE_SHARED_PREFERENCES, Context.MODE_PRIVATE)
                        val editor = settings.edit()
                        editor.putString(PREF_ACCOUNT_NAME, accountName)
                        editor.apply()
                        credential.selectedAccountName = accountName
                        readyUpDrive()
                    }
                }
            }
            REQUEST_AUTHORIZATION -> {
                if (resultCode == Activity.RESULT_OK) {
                    readyUpDrive()
                }
            }
        }
    }

    private fun readyDriveService() {
        if (!driveServiceInitialized) {
            val transport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            driveService = Drive.Builder(transport, jsonFactory, credential)
                    .setApplicationName("Mercury Messenger")
                    .build()
            driveServiceInitialized = true
        }
    }

    class SelectBackupDialogFragment : DialogFragment() {

        lateinit var array: Array<String>

        override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
            val a = activity
            a as OverviewActivity
            return AlertDialog.Builder(activity)
                    .setTitle(R.string.overview_activity_chat_backup_pick_alert_title)
                    .setItems(array) { _, index ->
                        a.selectedBackup(index)
                    }
                    .create()
        }

        override fun setArguments(args: Bundle) {
            super.setArguments(args)
            array = args.getStringArray("fileArray") ?: emptyArray()
        }
    }

    private class UpdateFCMKeyTask(val mercuryClient: MercuryClient) : AsyncTask<Unit, Unit, Unit>() {
        override fun doInBackground(vararg p0: Unit?) {
            UploadTokenTask.uploadToken(mercuryClient, FirebaseInstanceId.getInstance().token ?: "")
        }
    }

    private inner class GroupModificationReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val (chatUuid, modification) = ActionGroupModificationReceived.getData(intent)

            if (modification is GroupModificationChangeName) {
                updateChatName(chatUuid, modification.newName)
            }
        }
    }
}
