package de.intektor.mercury

import android.database.sqlite.SQLiteDatabase
import androidx.test.InstrumentationRegistry
import androidx.test.filters.LargeTest
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.database.DbHelper
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageText
import org.junit.Rule
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.*

/**
 * @author Intektor
 */
@LargeTest
class ChatActivityTest {

    private val dB = SQLiteDatabase.createInMemory(SQLiteDatabase.OpenParams.Builder().build())

    private val chat: ChatInfo

    private val yourself: Contact
    private val other: Contact

    init {
        DbHelper(InstrumentationRegistry.getTargetContext()).onCreate(dB)

        val keyGenerator = KeyPairGenerator.getInstance("RSA")
        keyGenerator.initialize(512)

        val pair = keyGenerator.genKeyPair()

        val public = pair.public

        yourself = createContact("Mercury Client", dB, public)
        other = createContact("User 0", dB, public)

        chat = createChat("Test Chat", yourself, other, dB)
    }

    @get:Rule
    val activityRule = EditableActivityTestRule(ChatActivity::class.java, {
        setup()
    }, { ChatActivity.createIntent(InstrumentationRegistry.getTargetContext(), chat) })


    private fun setup() {
        InstrumentationRegistry.getTargetContext().mercuryClient().dataBase = dB


        (0..100).forEach {
            val message = ChatMessage(MessageCore(yourself.userUUID, System.currentTimeMillis() - 100 + it, UUID.nameUUIDFromBytes("$it".toByteArray())), MessageText("Nachricht $it"))
            addChatMessage(InstrumentationRegistry.getContext(), dB, message, chat.chatUUID)
        }
    }

    @Test
    fun check20Messages() {
        onView(withId(R.id.chatActivityMessageList)).check(hasNChildren(20))
    }
}