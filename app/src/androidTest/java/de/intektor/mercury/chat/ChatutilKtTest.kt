package de.intektor.mercury.chat

import android.database.sqlite.SQLiteDatabase
import android.support.test.InstrumentationRegistry
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.chat.ChatType
import de.intektor.mercury_common.chat.MessageCore
import de.intektor.mercury_common.chat.data.MessageText
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.*

/**
 * @author Intektor
 */
class ChatutilKtTest {

    lateinit var database: SQLiteDatabase

    private val chatUUID = UUID.nameUUIDFromBytes("chatUUID".toByteArray())
    private val clientUUID = UUID.nameUUIDFromBytes("clientUUID".toByteArray())
    private val messageUUID = UUID.nameUUIDFromBytes("messageUUID".toByteArray())

    private val messageText = "test message ääää"

    private val chatMessage = ChatMessage(MessageCore(clientUUID, 0, messageUUID), MessageText(messageText))

    @Before
    fun setUp() {
        val openParams = SQLiteDatabase.OpenParams.Builder()
                .build()
        database = SQLiteDatabase.createInMemory(openParams)
    }

    @Test
    fun createChat() {
        val chatInfo = ChatInfo(chatUUID, "Sample Chat", ChatType.TWO_PEOPLE, listOf())
        de.intektor.mercury.chat.createChat(chatInfo, database, clientUUID)
    }

    @Test
    fun saveMessage() {
        createChat()
        de.intektor.mercury.chat.saveMessage(InstrumentationRegistry.getContext(), database, chatMessage, chatUUID)
    }

    @Test
    fun readMessage() {
        saveMessage()
        val readMessage = getChatMessages(InstrumentationRegistry.getContext(), database, "message_uuid = ?", arrayOf(messageUUID.toString()))[0]
        assert(readMessage.chatMessageInfo.message == chatMessage)
    }

    @Test
    fun deleteMessage() {
        saveMessage()

        de.intektor.mercury.chat.deleteMessage(database, messageUUID, chatUUID)

        val readMessage = getChatMessages(InstrumentationRegistry.getContext(), database, "message_uuid = ?", arrayOf(messageUUID.toString()))
        assert(readMessage.isEmpty())
    }

    @After
    fun tearDown() {
        database.close()
    }
}