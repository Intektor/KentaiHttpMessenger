package de.intektor.mercury.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * @author Intektor
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, 2) {

    companion object {
        const val DB_NAME = "MercuryDatabase.db"
    }

    override fun onCreate(dB: SQLiteDatabase) {
        dB.execSQL("CREATE TABLE IF NOT EXISTS chats (" +
                "chat_name VARCHAR(20) NOT NULL, " +
                "chat_uuid VARCHAR(45) NOT NULL, " +
                "type INT NOT NULL, " +
                "PRIMARY KEY (chat_uuid));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS chat_unread_messages(" +
                "chat_uuid VARCHAR(45) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "amount INT NOT NULL, " +
                "PRIMARY KEY (chat_uuid));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS chat_participants (" +
                "id INT," +
                "chat_uuid VARCHAR(45) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "participant_uuid VARCHAR(40) NOT NULL, " +
                "is_active INT NOT NULL, " +
                "PRIMARY KEY (id));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS contacts (" +
                "username VARCHAR(20) NOT NULL, " +
                "user_uuid VARCHAR(40) NOT NULL, " +
                "message_key VARCHAR(400), " +
                "alias VARCHAR(30), " +
                "PRIMARY KEY (username));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS message_status (" +
                "id INT, " +
                "message_uuid VARCHAR(45) NOT NULL REFERENCES chat_message(message_uuid) ON DELETE CASCADE, " +
                "status INT NOT NULL, " +
                "time BIGINT NOT NULL, " +
                "PRIMARY KEY(id));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS chat_message (" +
                "message_uuid VARCHAR(40) NOT NULL," +
                "time_created BIGINT NOT NULL," +
                "sender_uuid VARCHAR(20) NOT NULL," +
                "chat_uuid VARCHAR(40) NOT NULL, " +
                "PRIMARY KEY (message_uuid));" +
                "CREATE INDEX chat_uuid_index ON chat_message (chat_uuid);")

        dB.execSQL("CREATE TABLE IF NOT EXISTS message_data (message_uuid VARCHAR(40) NOT NULL REFERENCES chat_message(message_uuid) ON DELETE CASCADE, data TEXT NOT NULL, data_type INT NOT NULL, PRIMARY KEY(message_uuid))")

        dB.execSQL("CREATE TABLE IF NOT EXISTS reference_upload (reference_uuid VARCHAR(40) NOT NULL, uploaded INT NOT NULL, PRIMARY KEY(reference_uuid))")

        dB.execSQL("CREATE TABLE IF NOT EXISTS reference_key (reference_uuid VARCHAR (40) NOT NULL, aes BLOB NOT NULL, init_vector BLOB NOT NULL, PRIMARY KEY(reference_uuid))")

        dB.execSQL("CREATE TABLE IF NOT EXISTS lookup (message_uuid PRIMARY KEY NOT NULL REFERENCES chat_message(message_uuid) ON DELETE CASCADE, query_lookup TEXT NOT NULL)")

        dB.execSQL("CREATE TABLE IF NOT EXISTS fetching_messages (" +
                "chat_uuid VARCHAR(45) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "message_uuid VARCHAR(45) NOT NULL, " +
                "PRIMARY KEY (message_uuid));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS group_role_table (" +
                "id INT, " +
                "chat_uuid VARCHAR(40) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "user_uuid VARCHAR(40) NOT NULL, " +
                "role INT NOT NULL, " +
                "PRIMARY KEY(id));" +
                "CREATE INDEX group_role_table_chat_uuid_index ON group_role_table (chat_uuid);")

        dB.execSQL("CREATE TABLE IF NOT EXISTS user_to_chat_uuid (" +
                "user_uuid VARCHAR(40) REFERENCES contacts(user_uuid) ON DELETE CASCADE, " +
                "chat_uuid VARCHAR(40) REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "PRIMARY KEY(user_uuid));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS user_color_table (" +
                "user_uuid VARCHAR(40) NOT NULL, " +
                "color VARCHAR(6) NOT NULL, " +
                "PRIMARY KEY(user_uuid));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS group_key_table (" +
                "chat_uuid VARCHAR(40) REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "group_key VARCHAR(400) NOT NULL, " +
                "PRIMARY KEY(chat_uuid));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS file_type (" +
                "reference_uuid VARCHAR(40) NOT NULL, " +
                "file_type INT NOT NULL, " +
                "PRIMARY KEY(reference_uuid));")

        DatabaseUpgradeUtil.addPendingGroupModificationsTable(dB)
        DatabaseUpgradeUtil.addGroupMessageUUIDTable(dB)
        DatabaseUpgradeUtil.addNotificationMessagesTable(dB)
        DatabaseUpgradeUtil.addReferenceTable(dB)
        DatabaseUpgradeUtil.addPendingMessageTable(dB)
    }

    override fun onUpgrade(dB: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        DatabaseUpgradeUtil.upgradeDatabase(dB, oldVersion, newVersion)
    }
}