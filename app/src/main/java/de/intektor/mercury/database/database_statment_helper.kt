package de.intektor.mercury.database

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteStatement
import de.intektor.mercury_common.util.toUUID
import java.util.*

/**
 * @author Intektor
 */
fun SQLiteStatement.bindUUID(index: Int, item: UUID) {
    bindString(index, item.toString())
}

fun Cursor.getUUID(index: Int): UUID = getString(index).toUUID()

fun SQLiteStatement.bindEnum(index: Int, enum: Enum<*>) {
    bindLong(index, enum.ordinal.toLong())
}

inline fun <reified T : Enum<T>> Cursor.getEnum(index: Int) = enumValues<T>()[getInt(index)]

fun <T> SQLiteDatabase.isValuePresent(table: String, field: String, value: T): Boolean = rawQuery("SELECT COUNT($field) FROM $table WHERE $field = ?", arrayOf(value.toString())).use { cursor ->
    cursor.moveToNext()
    cursor.getInt(0) > 0
}

fun SQLiteStatement.bindBoolean(index: Int, item: Boolean) = bindLong(index, if (item) 1 else 0)

fun Cursor.getBoolean(index: Int) = getInt(index) == 1