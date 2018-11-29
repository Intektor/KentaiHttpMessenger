package de.intektor.mercury

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.espresso.Espresso
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers
import android.view.View
import de.intektor.mercury.chat.*
import de.intektor.mercury.chat.model.ChatInfo
import de.intektor.mercury.chat.model.ChatReceiver
import de.intektor.mercury.contacts.Contact
import de.intektor.mercury.contacts.ContactUtil
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.ChatType
import de.intektor.mercury_common.chat.MessageStatus
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
fun onView(viewMatcher: Matcher<View>) = Espresso.onView(viewMatcher)

fun withId(id: Int) = ViewMatchers.withId(id)

fun <T> assertThat(actual: T, matcher: Matcher<T>) = ViewMatchers.assertThat(actual, matcher)

fun <T> `is`(matcher: Matcher<T>) = Matchers.`is`(matcher)

fun <T> `is`(value: T) = Matchers.`is`(value)

fun click() = ViewActions.click()

fun typeText(text: String) = ViewActions.typeText(text)

fun matches(matcher: Matcher<View>) = ViewAssertions.matches(matcher)

fun isDisplayed() = ViewMatchers.isDisplayed()

fun hasComponent(clazz: Class<*>) = IntentMatchers.hasComponent(clazz.canonicalName)

fun hasAction(action: String) = IntentMatchers.hasAction(action)

fun <T> hasExtra(key: String, value: T) = IntentMatchers.hasExtra(key, value)

fun hasNChildren(amount: Int) = ViewAssertion { view, noViewFoundException ->
    if (view !is androidx.recyclerview.widget.RecyclerView) throw noViewFoundException

    val adapter = view.adapter ?: error("no adapter set")
    assertThat(adapter.itemCount, `is`(amount))
}

fun createContact(name: String, dataBase: SQLiteDatabase, key: Key): Contact {
    val userUUID = UUID.nameUUIDFromBytes(name.toByteArray())
    val contact = Contact(name, "", userUUID, key)
    ContactUtil.addContact(userUUID, name, dataBase, key)
    return contact
}

fun createChat(name: String, client: Contact, partner: Contact, dataBase: SQLiteDatabase): ChatInfo {
    val chatUUID = getChatUUIDForUserChat(client.userUUID, partner.userUUID)
    val chatInfo = ChatInfo(chatUUID, name, ChatType.TWO_PEOPLE, listOf(ChatReceiver.fromContact(client), ChatReceiver.fromContact(partner)))

    de.intektor.mercury.chat.createChat(chatInfo, dataBase, client.userUUID)

    return chatInfo
}

fun createGroupChat(name: String, contacts: List<Contact>) =
        ChatInfo(UUID.nameUUIDFromBytes(name.toByteArray()), name, ChatType.GROUP_DECENTRALIZED, contacts.map { ChatReceiver.fromContact(it) })

fun addChatMessage(context: Context, dataBase: SQLiteDatabase, message: ChatMessage, chatUUID: UUID) {
    saveMessage(context, dataBase, message, chatUUID)
    saveMessageStatusChange(dataBase, MessageStatusChange(message.messageCore.messageUUID, MessageStatus.WAITING, System.currentTimeMillis()))
}