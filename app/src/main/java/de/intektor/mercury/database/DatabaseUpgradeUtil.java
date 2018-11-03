package de.intektor.mercury.database;

import android.database.sqlite.SQLiteDatabase;

/**
 * @author Intektor
 */
public class DatabaseUpgradeUtil {

    public static void upgradeDatabase(SQLiteDatabase database, int oldVersion, int newVersion) {
        switch (oldVersion) {
            case 1:
                upgradeToVersion2(database);
        }
    }

    private static void upgradeToVersion2(SQLiteDatabase database) {

    }

    public static void addPendingGroupModificationsTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS pending_group_modifications (" +
                "chat_uuid VARCHAR (40) REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "modification_id VARCHAR (40) NOT NULL, " +
                "modification_data VARBINARY (2048) NOT NULL," +
                "PRIMARY KEY(modification_id));");
    }

    public static void addGroupMessageUUIDTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS group_message_uuid (" +
                "message_uuid VARCHAR (40) REFERENCES chat_table(message_uuid) ON DELETE CASCADE, " +
                "user_uuid VARCHAR (40) NOT NULL, " +
                "client_message_uuid VARCHAR (40) NOT NULL, " +
                "PRIMARY KEY(message_uuid));");
        database.execSQL("CREATE INDEX group_message_uuid_user_index ON group_message_uuid (user_uuid);");
    }

    public static void addNotificationMessagesTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS notification_messages (" +
                "chat_uuid VARCHAR(40) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "message_uuid VARCHAR(40) NOT NULL REFERENCES chat_message(message_uuid) ON DELETE CASCADE, " +
                "time BIGINT NOT NULL, " +
                "PRIMARY KEY (message_uuid))");
    }

    public static void addReferenceTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS reference (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "chat_uuid VARCHAR(40) NOT NULL REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "reference_uuid VARCHAR(40) NOT NULL," +
                "message_uuid VARCHAR(40) NOT NULL REFERENCES chat_message(message_uuid) ON DELETE CASCADE," +
                "media_type INT NOT NULL, " +
                "time BIGINT NOT NULL)");
    }
}
