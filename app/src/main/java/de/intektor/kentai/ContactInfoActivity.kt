package de.intektor.kentai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.squareup.picasso.Picasso
import de.intektor.kentai.kentai.ACTION_PROFILE_PICTURE_UPDATED
import de.intektor.kentai.kentai.KEY_CHAT_INFO
import de.intektor.kentai.kentai.KEY_USER_UUID
import de.intektor.kentai.kentai.chat.getUserChat
import de.intektor.kentai.kentai.chat.readContact
import de.intektor.kentai.kentai.getProfilePicture
import kotlinx.android.synthetic.main.activity_user_chat_info.*
import java.util.*

class ContactInfoActivity : AppCompatActivity() {

    private lateinit var updateProfilePictureReceiver: BroadcastReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_chat_info)

        val kentaiClient = applicationContext as KentaiClient

        val userUUID = intent.getSerializableExtra(KEY_USER_UUID) as UUID

        val contact = readContact(kentaiClient.dataBase, userUUID)

        activityUserChatInfoProfileName.text = contact.name
        activityUserChatInfoProfileAlias.text = contact.alias

        val chatInfo = getUserChat(kentaiClient.dataBase, contact, kentaiClient)

        activityUserChatInfoSentMedia.setOnClickListener {
            val i = Intent(this@ContactInfoActivity, ViewMediaActivity::class.java)
            i.putExtra(KEY_CHAT_INFO, chatInfo)
            startActivity(i)
        }

        Picasso.with(this).load(getProfilePicture(userUUID, this)).into(activityUserChatInfoProfilePicture)

        updateProfilePictureReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val updatedUserUUID = intent.getSerializableExtra(KEY_USER_UUID) as UUID
                if (updatedUserUUID == userUUID) {
                    Picasso.with(context).load(getProfilePicture(updatedUserUUID, context)).into(activityUserChatInfoProfilePicture)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        registerReceiver(updateProfilePictureReceiver, IntentFilter(ACTION_PROFILE_PICTURE_UPDATED))
    }

    override fun onPause() {
        super.onPause()

        unregisterReceiver(updateProfilePictureReceiver)
    }
}
