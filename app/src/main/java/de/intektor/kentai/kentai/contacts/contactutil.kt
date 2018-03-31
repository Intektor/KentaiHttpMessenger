package de.intektor.kentai.kentai.contacts

import android.database.sqlite.SQLiteDatabase
import com.google.common.io.BaseEncoding
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
fun addContact(userUUID: UUID, userName: String, dataBase: SQLiteDatabase, messageKey: Key? = null) {
    val contained = dataBase.rawQuery("SELECT COUNT(user_uuid) FROM contacts WHERE user_uuid = '$userUUID'", arrayOf()).use { cursor ->
        cursor.moveToNext()
        cursor.getInt(0) > 0
    }
    if (contained) return
    dataBase.compileStatement("INSERT INTO contacts (user_uuid, username, alias, message_key) VALUES (?, ?, ?, ?)").use { statement ->
        statement.bindString(1, userUUID.toString())
        statement.bindString(2, userName)
        statement.bindString(3, userName)
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

val colors = arrayOf("FFE100", "FF1000", "FF0797", "D000FF", "7200FF", "0050FF", "00C7FF", "00FF00", "AD5E30", "826BAA", "72A8A4")