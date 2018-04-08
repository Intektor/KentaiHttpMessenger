package de.intektor.kentai

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import de.intektor.kentai.kentai.KEY_CHAT_INFO
import de.intektor.kentai.kentai.KEY_USER_UUID
import de.intektor.kentai.kentai.chat.getUserChat
import de.intektor.kentai.kentai.chat.readContact
import kotlinx.android.synthetic.main.activity_user_chat_info.*
import java.util.*

class ContactInfoActivity : AppCompatActivity() {

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
    }
}
