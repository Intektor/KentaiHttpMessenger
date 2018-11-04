package de.intektor.mercury.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.squareup.picasso.MemoryPolicy
import com.squareup.picasso.Picasso
import de.intektor.mercury.MercuryClient
import de.intektor.mercury.R
import de.intektor.mercury.android.getSelectedTheme
import de.intektor.mercury.chat.getContact
import de.intektor.mercury.chat.getUserChat
import de.intektor.mercury.io.ChatMessageService
import de.intektor.mercury.util.*
import de.intektor.mercury_common.users.ProfilePictureType
import kotlinx.android.synthetic.main.activity_user_chat_info.*
import java.util.*

class ContactInfoActivity : AppCompatActivity() {

    private lateinit var updateProfilePictureReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_user_chat_info)

        val mercuryClient = applicationContext as MercuryClient

        val userUUID = intent.getSerializableExtra(KEY_USER_UUID) as UUID

        val contact = getContact(mercuryClient.dataBase, userUUID)

        activityUserChatInfoProfileName.text = contact.name
        activityUserChatInfoProfileAlias.text = contact.alias

        val chatInfo = getUserChat(this, mercuryClient.dataBase, contact)

        activityUserChatInfoSentMedia.setOnClickListener {
            ViewMediaActivity.launch(this, chatInfo)
        }

        Picasso.get().load(getProfilePicture(userUUID, this)).memoryPolicy(MemoryPolicy.NO_CACHE).into(activityUserChatInfoProfilePicture)

        if (getProfilePictureType(userUUID, this) != ProfilePictureType.NORMAL) {
            activityUserChatInfoProfilePicture.setOnClickListener {
                val i = Intent(this@ContactInfoActivity, ChatMessageService::class.java)
                i.action = ACTION_DOWNLOAD_PROFILE_PICTURE
                i.putExtra(KEY_USER_UUID, userUUID)
                i.putExtra(KEY_PROFILE_PICTURE_TYPE, ProfilePictureType.NORMAL)
                startService(i)
            }
        }

        updateProfilePictureReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val updatedUserUUID = intent.getSerializableExtra(KEY_USER_UUID) as UUID
                if (updatedUserUUID == userUUID) {
                    Picasso.get().load(getProfilePicture(updatedUserUUID, context)).memoryPolicy(MemoryPolicy.NO_CACHE).into(activityUserChatInfoProfilePicture)
                }
            }
        }

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onResume() {
        super.onResume()

        registerReceiver(updateProfilePictureReceiver, IntentFilter(ACTION_PROFILE_PICTURE_UPDATED))
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(updateProfilePictureReceiver)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
