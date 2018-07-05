package de.intektor.kentai.overview_activity

import android.Manifest
import android.accounts.AccountManager
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter
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
import de.intektor.kentai.*
import de.intektor.kentai.fragment.ChatListViewAdapter
import de.intektor.kentai.kentai.*
import de.intektor.kentai.kentai.android.backup.BackupService
import de.intektor.kentai.kentai.android.backup.BackupService.Companion.PREF_ACCOUNT_NAME
import de.intektor.kentai.kentai.android.backup.installChatBackup
import de.intektor.kentai.kentai.chat.ChatMessageWrapper
import de.intektor.kentai.kentai.chat.adapter.chat.HeaderItemDecoration
import de.intektor.kentai.kentai.chat.readChatMessageWrappers
import de.intektor.kentai.kentai.chat.readChats
import de.intektor.kentai_http_common.chat.ChatMessageStatusChange
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.client_to_server.UpdateFBCMTokenRequest
import de.intektor.kentai_http_common.gson.genGson
import de.intektor.kentai_http_common.util.encryptRSA
import kotlinx.android.synthetic.main.activity_overview.*
import kotlinx.android.synthetic.main.fragment_chat_list.*
import pub.devrel.easypermissions.AfterPermissionGranted
import pub.devrel.easypermissions.EasyPermissions
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

    companion object {
        private const val SIGN_IN_REQUEST_CODE = 0
        private const val REQUEST_ACCOUNT_PICKER = 1000
        private const val REQUEST_AUTHORIZATION = 1001
        private const val REQUEST_GOOGLE_PLAY_SERVICES = 1002
        private const val REQUEST_PERMISSION_GET_ACCOUNTS = 1003
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!File(filesDir.path + "/username.info").exists()) {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }

        currentTheme = getSelectedTheme(this, false)

        setTheme(currentTheme)

        setContentView(R.layout.activity_overview)

        setSupportActionBar(toolbar)

        appbar.context.setTheme(currentTheme)

        toolbar.context.setTheme(currentTheme)

        mSectionsPagerAdapter = SectionsPagerAdapter(supportFragmentManager)

        container.adapter = mSectionsPagerAdapter

        tabs.setupWithViewPager(container)

        searchAdapter = SearchAdapter(searchList, { item ->
            if (item is ChatListViewAdapter.ChatItem) {
                val i = Intent(this@OverviewActivity, ChatActivity::class.java)
                i.putExtra(KEY_CHAT_INFO, item.chatInfo)
                startActivity(i)
            } else if (item is SearchAdapter.ChatMessageSearch) {
                val i = Intent(this@OverviewActivity, ChatActivity::class.java)
                i.putExtra(KEY_CHAT_INFO, item.chatInfo)
                i.putExtra(KEY_MESSAGE_UUID, item.message.message.id)
                startActivity(i)
            }
        })

        overviewActivitySearchList.adapter = searchAdapter
        overviewActivitySearchList.layoutManager = LinearLayoutManager(this)

        overviewActivitySearchList.addItemDecoration(HeaderItemDecoration(overviewActivitySearchList, object : HeaderItemDecoration.StickyHeaderInterface {
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

        overviewActivitySearchList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                val scrollPercent = (overviewActivitySearchList.computeVerticalScrollOffset().toFloat() + overviewActivitySearchList.height) / overviewActivitySearchList.computeVerticalScrollRange().toFloat()

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
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_overview, menu)
        menu.findItem(R.id.new_chat).icon = getAttrDrawable(this, R.attr.ic_message)
        menu.findItem(R.id.new_contact).icon = getAttrDrawable(this, R.attr.ic_person_add)
        val searchItem = menu.findItem(R.id.search_chat)
        searchItem.icon = getAttrDrawable(this, R.attr.ic_search)

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
                    tabs.visibility = View.VISIBLE
                    container.visibility = View.VISIBLE
                    overviewActivitySearchList.visibility = View.GONE
                } else {
                    currentMessagesOffset = 0
                    currentQuery = query

                    tabs.visibility = View.GONE
                    container.visibility = View.GONE
                    overviewActivitySearchList.visibility = View.VISIBLE

                    val kentaiClient = applicationContext as KentaiClient

                    if (chatList.isEmpty()) {
                        chatList += readChats(kentaiClient.dataBase, this@OverviewActivity)
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
        val kentaiClient = applicationContext as KentaiClient
        val messages = readChatMessageWrappers(kentaiClient.dataBase, "text LIKE ?", arrayOf("%$currentQuery%"), "time",
                limit = "$currentMessagesOffset, 20")
                .map { a -> SearchAdapter.ChatMessageSearch(a, chatList.first { it.chatInfo.chatUUID == a.chatUUID }.chatInfo) }

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
                UpdateFCMKeyTask(applicationContext as KentaiClient).execute()
            }
            R.id.menuOverviewSettings -> {
                val openSettingsIntent = Intent(this, SettingsOverviewActivity::class.java)
                startActivity(openSettingsIntent)
            }
        }

        return super.onOptionsItemSelected(item)
    }

    fun addChat(item: ChatListViewAdapter.ChatItem) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        fragment.addChat(item)
        fragment.shownChatList.sortBy { it.lastChatMessage.message.timeSent }
        fragment.list.adapter.notifyDataSetChanged()
    }

    fun getCurrentChats(): List<ChatListViewAdapter.ChatItem> {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        return fragment.shownChatList
    }

    fun updateLatestChatMessage(chatUUID: UUID, lastMessage: ChatMessageWrapper, unreadMessages: Int) {
        if (lastMessage.message is ChatMessageStatusChange) return
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        val chatItem = fragment.chatMap[chatUUID] ?: return
        chatItem.lastChatMessage = lastMessage
        chatItem.unreadMessages = unreadMessages
        fragment.shownChatList.sortByDescending { it.lastChatMessage.message.timeSent }
        fragment.list.adapter.notifyDataSetChanged()
    }

    fun updateLatestChatMessageStatus(chatUUID: UUID, status: MessageStatus, messageUUID: UUID) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        val chatItem = fragment.chatMap[chatUUID]!!
        if (chatItem.lastChatMessage.message.id == messageUUID.toString()) {
            chatItem.lastChatMessage.status = status
            fragment.list.adapter.notifyDataSetChanged()
        }
    }

    fun updateChatName(chatUUID: UUID, name: String) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        val chatItem = fragment.chatMap[chatUUID]!!
        chatItem.chatInfo = chatItem.chatInfo.copy(chatName = name)
        fragment.list.adapter.notifyDataSetChanged()
    }

    fun updateInitChat(chatUUID: UUID, successful: Boolean) {
        val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
        fragment.updateInitChat(chatUUID, successful, false)
    }

    override fun onResume() {
        super.onResume()

        if (currentTheme != getSelectedTheme(this, false)) {
            val i = Intent(this, OverviewActivity::class.java)
            startActivity(i)
            finish()
        }

        registerReceiver(initChatListener, IntentFilter(ACTION_INIT_CHAT_FINISHED))
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(initChatListener)
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

        AlertDialog.Builder(this)
                .setView(editText)
                .setTitle(R.string.overview_activity_chat_backup_alert_title)
                .setMessage(R.string.overview_activity_chat_backup_alert_message)
                .setNegativeButton(R.string.overview_activity_chat_backup_alert_cancel, { _, _ -> })
                .setPositiveButton(R.string.overview_activity_chat_backup_alert_proceed, { _, _ ->
                    val startServiceIntent = Intent(this, BackupService::class.java)
                    startService(startServiceIntent)
                    val i = Intent(Kentai.ACTION_BACKUP)
                    i.putExtra(BackupService.BACKUP_NAME_EXTRA, editText.text.toString())
                    sendBroadcast(i)
                })
                .show()
    }

    private fun useDriveBackup() {
        thread {
            readyDriveService()

            val finalList = mutableListOf<com.google.api.services.drive.model.File>()
            var pageToken: String? = null

            do {
                val result = driveService.files().list()
                        .setQ("mimeType='application/kentai.backup.zip'")
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
        val kentaiClient = applicationContext as KentaiClient
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
                        .setPositiveButton(R.string.overview_activity_chat_backup_use_install_alert_dialog_proceed, { _, _ ->
                            val progressDialog = ProgressDialog.show(this, getString(R.string.overview_activity_chat_backup_use_install_progress_dialog_title),
                                    getString(R.string.overview_activity_chat_backup_use_install_progress_dialog_message), true)
                            thread {
                                installChatBackup(kentaiClient.dataBase, tempFile, this, kentaiClient)
                                runOnUiThread {
                                    progressDialog.dismiss()
                                    val fragment = supportFragmentManager.findFragmentByTag("android:switcher:" + R.id.container + ":0") as FragmentChatsOverview
                                    fragment.updateList()
                                }
                            }


                        })
                        .setNegativeButton(R.string.overview_activity_chat_backup_use_install_alert_dialog_cancel, { _, _ -> })
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

    @AfterPermissionGranted(REQUEST_PERMISSION_GET_ACCOUNTS)
    private fun chooseAccount() {
        if (EasyPermissions.hasPermissions(this, Manifest.permission.GET_ACCOUNTS)) {
            val accountName = applicationContext.getSharedPreferences(BackupService.DRIVE_SHARED_PREFERENCES, Context.MODE_PRIVATE).getString(PREF_ACCOUNT_NAME, null)
            if (accountName != null) {
                credential.selectedAccountName = accountName
                readyUpDrive()
            } else {
                startActivityForResult(credential.newChooseAccountIntent(), REQUEST_ACCOUNT_PICKER)
            }
        } else {
            EasyPermissions.requestPermissions(this,
                    getString(R.string.overview_activity_request_google_accounts_permission_message),
                    REQUEST_PERMISSION_GET_ACCOUNTS,
                    Manifest.permission.GET_ACCOUNTS)
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
                    .setApplicationName("Kentai Messenger")
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
                    .setItems(array, { _, index ->
                        a.selectedBackup(index)
                    })
                    .create()
        }

        override fun setArguments(args: Bundle) {
            super.setArguments(args)
            array = args.getStringArray("fileArray")
        }
    }

    private class UpdateFCMKeyTask(val kentaiClient: KentaiClient) : AsyncTask<Unit, Unit, Unit>() {
        override fun doInBackground(vararg p0: Unit?) {
            val gson = genGson()
            val s = gson.toJson(UpdateFBCMTokenRequest(kentaiClient.userUUID, FirebaseInstanceId.getInstance().token!!.encryptRSA(kentaiClient.privateAuthKey!!)))
            httpPost(s, UpdateFBCMTokenRequest.TARGET)
        }
    }
}