package de.intektor.kentai.kentai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.intektor.kentai.kentai.contacts.Contact

/**
 * @author Intektor
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context, "KENTAI_DATABASE", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
    }

}