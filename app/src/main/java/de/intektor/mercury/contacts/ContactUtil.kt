package de.intektor.mercury.contacts

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.common.io.BaseEncoding
import de.intektor.mercury.database.bindUUID
import de.intektor.mercury.database.isValuePresent
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
object ContactUtil {

    fun addContact(userUUID: UUID, userName: String, dataBase: SQLiteDatabase, messageKey: Key? = null) {
        val colors = getColors()

        //Check for duplicate
        val contained = dataBase.rawQuery("SELECT COUNT(user_uuid) FROM contacts WHERE user_uuid = '$userUUID'", arrayOf()).use { cursor ->
            cursor.moveToNext()
            cursor.getInt(0) > 0
        }
        if (contained) return

        dataBase.compileStatement("INSERT INTO contacts (user_uuid, username, alias, message_key) VALUES (?, ?, ?, ?)").use { statement ->
            statement.bindUUID(1, userUUID)
            statement.bindString(2, userName)
            statement.bindNull(3)
            if (messageKey != null) {
                statement.bindString(4, BaseEncoding.base64().encode(messageKey.encoded))
            } else {
                statement.bindNull(4)
            }
            statement.execute()
        }

        dataBase.compileStatement("INSERT INTO user_color_table (user_uuid, color) VALUES (?, ?)").use { statement ->
            val r = Random()
            statement.bindString(1, userUUID.toString())
            statement.bindString(2, colors[r.nextInt(colors.size)])
            statement.execute()
        }
    }

    fun hasContact(dataBase: SQLiteDatabase, userUUID: UUID): Boolean =
            dataBase.isValuePresent("contacts", "user_uuid", userUUID)

    fun getDisplayName(context: Context, dataBase: SQLiteDatabase, contact: Contact) = contact.name

    fun getColors() = arrayOf("FFE100", "FF1000", "FF0797", "D000FF", "7200FF", "0050FF", "00C7FF", "00FF00", "AD5E30", "826BAA", "72A8A4")
}


