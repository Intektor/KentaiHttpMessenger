package de.intektor.mercury

import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.support.test.InstrumentationRegistry
import android.support.test.espresso.action.ViewActions
import android.support.test.espresso.contrib.RecyclerViewActions
import android.support.test.espresso.intent.Intents
import android.support.test.filters.LargeTest
import android.support.test.runner.AndroidJUnit4
import android.support.v7.widget.RecyclerView
import de.intektor.mercury.android.mercuryClient
import de.intektor.mercury.chat.ChatInfo
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.database.DbHelper
import de.intektor.mercury.ui.chat.ChatActivity
import de.intektor.mercury.ui.overview_activity.OverviewActivity
import de.intektor.mercury.util.KEY_CHAT_INFO
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageText
import de.intektor.mercury_common.util.writeUUID
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataOutputStream
import java.io.File
import java.security.KeyPairGenerator
import java.util.*

/**
 * @author Intektor
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class OverviewActivityTest {

    @get:Rule
    val activityRule = EditableActivityTestRule(OverviewActivity::class.java, {
        setup()
    }, { Intent(InstrumentationRegistry.getTargetContext(), OverviewActivity::class.java) })

    companion object {

    }

    private val chats: MutableList<ChatInfo> = mutableListOf()

    private fun setup() {
        val mercuryClient = InstrumentationRegistry.getTargetContext().mercuryClient()

        val context = InstrumentationRegistry.getTargetContext().mercuryClient()

        ClientPreferences.setRegistered(context, true)

        val dB = SQLiteDatabase.createInMemory(SQLiteDatabase.OpenParams.Builder().build())

        DbHelper(mercuryClient).onCreate(dB)

        val keyGenerator = KeyPairGenerator.getInstance("RSA")
        keyGenerator.initialize(512)

        val pair = keyGenerator.genKeyPair()

        val public = pair.public

        val yourself = createContact("Mercury Client", dB, public)
        val others = (0..9).map { createContact("User $it", dB, public) }

        ClientPreferences.setUsername(context, yourself.name)
        ClientPreferences.setClientUUID(context, yourself.userUUID)

        others.forEach {
            val chatInfo = createChat(it.name, yourself, it, dB)

            val core = MessageCore(yourself.userUUID, 19092300, UUID.randomUUID())
            val data = MessageText("Hello ${it.name}")

            addChatMessage(context, dB, ChatMessage(core, data), chatInfo.chatUUID)

            chats += chatInfo
        }

        if (!File(context.filesDir.path + "/username.info").exists()) {
            val dataOut = DataOutputStream(File(context.filesDir.path + "/username.info").outputStream())
            dataOut.writeUTF(yourself.name)
            dataOut.writeUUID(yourself.userUUID)
            dataOut.close()
        }

        context.dataBase = dB

        Intents.init()
    }

    @Test
    fun check10Chats() {
        onView(withId(R.id.fragment_chat_list_rv_chats)).check(hasNChildren(10))
    }

    @Test
    fun check11Contacts() {
        onView(withId(R.id.activity_overview_viewpager))
                .perform(ViewActions.swipeLeft())

        onView(withId(R.id.fragment_contact_list_rv_contacts)).check(hasNChildren(11))
    }

    @Test
    fun openSearchBox() {
        onView(withId(R.id.menu_activity_overview_item_search)).perform(click())
    }

    @Test
    fun performSearchForUser0Chat() {
        openSearchBox()

        onView(withId(android.support.design.R.id.search_src_text)).perform(typeText("User 0"))

        onView(withId(R.id.activity_overview_rv_search_layout)).check(matches(isDisplayed()))
    }

    @Test
    fun performSearchForMessagesContainingHello() {
        openSearchBox()

        onView(withId(android.support.design.R.id.search_src_text)).perform(typeText("Hello"))

        onView(withId(R.id.activity_overview_rv_search_layout)).check(matches(isDisplayed()))

        onView(withId(R.id.activity_overview_rv_search_layout)).check(hasNChildren(12))
    }

    @Test
    fun clickThirdMessageContainingHello() {
        performSearchForMessagesContainingHello()

//        onView(withId(R.id.activity_overview_rv_search_layout))
//                .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(2))
//                .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(2, click()))
//
//        Intents.intending(Matchers.allOf(hasComponent(ChatActivity::class.java), hasExtra(KEY_CHAT_INFO, chats[2])))
    }

    @Test
    fun clickOnThirdChat() {
        onView(withId(R.id.fragment_chat_list_rv_chats))
                .perform(RecyclerViewActions.scrollToPosition<RecyclerView.ViewHolder>(2))
                .perform(RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(2, click()))

        Intents.intending(Matchers.allOf(hasComponent(ChatActivity::class.java), hasExtra(KEY_CHAT_INFO, chats[2])))
    }

    @After
    fun cleanup() {
        Intents.release()
    }
}