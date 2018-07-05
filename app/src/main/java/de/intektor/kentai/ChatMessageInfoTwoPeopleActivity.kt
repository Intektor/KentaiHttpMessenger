package de.intektor.kentai

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import de.intektor.kentai.kentai.getSelectedTheme

class ChatMessageInfoTwoPeopleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setTheme(getSelectedTheme(this))

        setContentView(R.layout.activity_chat_message_info_two_people)
    }
}
