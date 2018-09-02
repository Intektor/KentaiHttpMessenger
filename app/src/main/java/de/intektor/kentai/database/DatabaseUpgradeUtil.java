package de.intektor.kentai.database;

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
        addPendingGroupModificationsTable(database);
        addGroupMessageUUIDTable(database);
        //Delete all group invites and group modifications because they were drastically changed and don't matter too much
        database.execSQL("DELETE FROM chat_table WHERE type = 2 OR type = 3");

        database.execSQL("DROP TABLE notification_messages;");
        addNotificationMessagesTable(database);
    }

    public static void addPendingGroupModificationsTable(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS pending_group_modifications (" +
                "chat_uuid VARCHAR (40) REFERENCES chats(chat_uuid) ON DELETE CASCADE, " +
                "modification_id VARCHAR (40) NOT NULL, " +
                "modification_type INT NOT NULL, " +
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
                "sender_uuid VARCHAR(40) NOT NULL, " +
                "message_uuid VARCHAR(40) NOT NULL, " +
                "preview_text VARCHAR(60) NOT NULL, " +
                "additional_info_id INT NOT NULL, " +
                "additional_info_content VARBINARY(1024), " +
                "time BIGINT NOT NULL, " +
                "PRIMARY KEY (message_uuid))");
    }
}
