package de.intektor.kentai

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_user_chat_info.*
import java.util.*

class ContactInfoActivity : AppCompatActivity() {

    lateinit var userUUID: UUID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_chat_info)
        userUUID = intent.getSerializableExtra("userUUID") as UUID

        KentaiClient.INSTANCE.dataBase.rawQuery("SELECT username, alias FROM contacts WHERE user_uuid = ?", arrayOf(userUUID.toString())).use { query ->
            query.moveToNext()
            val username = query.getString(0)
            val alias = query.getString(1)
            contact_info_username_view.text = username
            if (alias.isNotEmpty() && alias != username) {
                contact_info_edit_alias.setText(alias, TextView.BufferType.EDITABLE)
            }
        }
    }
}
