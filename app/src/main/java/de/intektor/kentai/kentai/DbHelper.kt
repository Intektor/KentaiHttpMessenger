package de.intektor.kentai.kentai

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * @author Intektor
 */
class DbHelper(context: Context) : SQLiteOpenHelper(context, "KENTAI_DATABASE", null, 1) {

    override fun onCreate(dB: SQLiteDatabase) {
        dB.execSQL("CREATE TABLE IF NOT EXISTS chats (" +
                "chat_name VARCHAR(20) NOT NULL, " +
                "chat_uuid VARCHAR(45) NOT NULL, " +
                "type INT NOT NULL, " +
                "unread_messages INT NOT NULL, " +
                "PRIMARY KEY (chat_uuid));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS chat_participants (" +
                "id INT," +
                "chat_uuid VARCHAR(45) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "participant_uuid VARCHAR(40) NOT NULL, " +
                "is_active INT NOT NULL, " +
                "PRIMARY KEY (id));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS pending_messages (" +
                "id INT, " +
                "chat_uuid VARCHAR(45) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "message_uuid VARCHAR(45) NOT NULL, " +
                "PRIMARY KEY (id));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS contacts (" +
                "username VARCHAR(20) NOT NULL, " +
                "user_uuid VARCHAR(40) NOT NULL, " +
                "message_key VARCHAR(400), " +
                "alias VARCHAR(30) NOT NULL, " +
                "PRIMARY KEY (username));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS message_status_change (" +
                "id INT, " +
                "message_uuid VARCHAR(45) NOT NULL REFERENCES chat_table(message_uuid) ON DELETE CASCADE, " +
                "status INT NOT NULL, " +
                "time BIGINT NOT NULL, " +
                "PRIMARY KEY(id));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS chat_table (" +
                "message_uuid VARCHAR(40) NOT NULL," +
                "additional_info VARBINARY(2048)," +
                "text VARCHAR(2000) NOT NULL," +
                "time BIGINT NOT NULL," +
                "type INT NOT NULL," +
                "sender_uuid VARCHAR(20) NOT NULL," +
                "client INT NOT NULL," +
                "chat_uuid VARCHAR(40) NOT NULL, " +
                "reference VARCHAR(40) NOT NULL, " +
                "PRIMARY KEY (message_uuid));" +
                "CREATE INDEX chat_uuid_index ON chat_table (chat_uuid);")

        dB.execSQL("CREATE TABLE IF NOT EXISTS fetching_messages (" +
                "chat_uuid VARCHAR(45) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "message_uuid VARCHAR(45) NOT NULL, " +
                "PRIMARY KEY (message_uuid));")

        dB.execSQL("CREATE TABLE IF NOT EXISTS notification_messages (" +
                "chat_uuid VARCHAR(40) NOT NULL, " +
                "sender_uuid VARCHAR(40) NOT NULL, " +
                "message_uuid VARCHAR(40) NOT NULL, " +
                "preview_text VARCHAR(60) NOT NULL, " +
                "additional_info_id INT NOT NULL, " +
                "additional_info_content VARBINARY(1024), " +
                "time BIGINT NOT NULL, " +
                "PRIMARY KEY (message_uuid))")

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

        dB.execSQL("CREATE TABLE IF NOT EXISTS reference_upload_table (" +
                "chat_uuid VARCHAR(40) REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "reference_uuid VARCHAR(40) NOT NULL, " +
                "file_type INT NOT NULL, " +
                "state INT NOT NULL, " +
                "PRIMARY KEY(reference_uuid));")
    }

    override fun onUpgrade(dB: SQLiteDatabase, oldVersion: Int, newVersion: Int) {

    }

}