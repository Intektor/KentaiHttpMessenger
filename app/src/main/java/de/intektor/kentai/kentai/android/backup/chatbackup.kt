package de.intektor.kentai.kentai.android.backup

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.google.common.base.Splitter
import com.google.common.io.BaseEncoding
import de.intektor.kentai.KentaiClient
import de.intektor.kentai.kentai.chat.*
import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai.kentai.contacts.addContact
import de.intektor.kentai.kentai.dataIn
import de.intektor.kentai.kentai.internalFile
import de.intektor.kentai.kentai.references.UploadState
import de.intektor.kentai_http_common.chat.ChatType
import de.intektor.kentai_http_common.chat.GroupRole
import de.intektor.kentai_http_common.chat.MessageStatus
import de.intektor.kentai_http_common.reference.FileType
import de.intektor.kentai_http_common.util.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * @author Intektor
 */
fun createChatBackup(backupName: String, context: Context, callback: (Int) -> (Unit), kentaiClient: KentaiClient): File {
    val dateFormat = DateFormat.getDateTimeInstance()

    internalFile("backups/", kentaiClient).mkdirs()

    val fileName = "backups/$backupName-${dateFormat.format(Date())}"

    val zipBuilder = ZipBuilder(internalFile(fileName, kentaiClient))

    callback.invoke(5)

    val dataOut = DataOutputStream(zipBuilder.zipOut)

    val chats = readChats(kentaiClient.dataBase, context)
    for (chat in chats) {
        val info = chat.chatInfo
        val baseFolder = "chat-${info.chatUUID}"
        val messageAmount = getAmountChatMessages(kentaiClient.dataBase, info.chatUUID)

        var current = 0
        var lastTime = 0L
        while (current < messageAmount) {
            val list = readChatMessageWrappers(kentaiClient.dataBase, "chat_uuid = '${info.chatUUID}' AND time > $lastTime", null, "time ASC", "10")

            for (messageWrapper in list) {
                zipBuilder.zipOut.putNextEntry(ZipEntry("$baseFolder/${messageWrapper.message.id}.km"))
                writeMessageWrapper(dataOut, messageWrapper)
            }

            lastTime = list.last().message.timeSent
            current += 10
        }

        zipBuilder.zipOut.putNextEntry(ZipEntry("$baseFolder/info.kci"))
        writeChatInfo(info, dataOut)

        if (chat.chatInfo.chatType.isGroup()) {
            zipBuilder.zipOut.putNextEntry(ZipEntry("$baseFolder/roles.kcri"))

            val groupRoles = getGroupMembers(kentaiClient.dataBase, chat.chatInfo.chatUUID)
            dataOut.writeInt(groupRoles.size)
            for (role in groupRoles) {
                writeGroupMember(role, dataOut)
            }

            zipBuilder.zipOut.putNextEntry(ZipEntry("$baseFolder/key.kk"))

            kentaiClient.dataBase.rawQuery("SELECT group_key FROM group_key_table WHERE chat_uuid = ?", arrayOf(info.chatUUID.toString())).use { query ->
                query.moveToNext()
                val key = query.getString(0)
                dataOut.writeUTF(key)
            }
        }
    }

    callback.invoke(40)

    val contacts = readContacts(kentaiClient.dataBase)
    for (contact in contacts) {
        zipBuilder.zipOut.putNextEntry(ZipEntry("contacts/${contact.userUUID}.kc"))
        writeContact(contact, dataOut)
    }

    callback.invoke(60)

    var count = 0
    val max = getAmountMessageStatusChange(kentaiClient.dataBase)
    while (count < max) {
        val list = readMessageStatusChange(kentaiClient.dataBase, count, 10)
        for (change in list) {
            zipBuilder.zipOut.putNextEntry(ZipEntry("message_status_change/${change.messageUUID}.kmsc"))
            writeMessageStatusChange(change, dataOut)
        }
        count += 10
    }

    for (c in File(context.filesDir.path + "/resources/").listFiles()) {
        for (resourceFile in c.listFiles()) {
            zipBuilder.zipOut.putNextEntry(ZipEntry(resourceFile.path.substring(context.filesDir.path.length)))
            resourceFile.inputStream().copyTo(zipBuilder.zipOut)
        }
    }

    zipBuilder.close()
    return internalFile(fileName, kentaiClient)
}

fun installChatBackup(database: SQLiteDatabase, chatBackup: File, context: Context, kentaiClient: KentaiClient) {
    val zipIn = ZipInputStream(chatBackup.inputStream())
    val path = context.cacheDir.path + "/currentBackup"
    File(path).mkdirs()
    while (true) {
        val entry = zipIn.nextEntry ?: break
        val p = path + "/${entry.name}"
        val splitter = Splitter.on('/')
        val list = splitter.splitToList(p)
        val sb = StringBuilder()
        for (i in 0 until list.size - 1) {
            sb.append(list[i])
            sb.append('/')
        }
        File(sb.toString()).mkdirs()
        val fos = FileOutputStream(path + "/${entry.name}")
        zipIn.copyTo(fos)
    }

    File(path, "contacts/").listFiles()
            .map { readContact(it.inputStream().dataIn()) }
            .forEach { addContact(it.userUUID, it.name, database, it.message_key) }

    for (folder in File(path).listFiles().filter { it.name.startsWith("chat") && it.isDirectory }) {
        val chatInfoFile = File(folder.path + "/info.kci")
        val rolesFile = File(folder.path + "/roles.kcri")
        val keyFile = File(folder.path + "/key.kk")

        val chatInfoDataIn = chatInfoFile.inputStream().dataIn()

        val chatInfo = readChatInfo(chatInfoDataIn)
        when (chatInfo.chatType) {
            ChatType.TWO_PEOPLE -> {
                createChat(chatInfo, database, kentaiClient.userUUID)
            }
            ChatType.GROUP_CENTRALIZED, ChatType.GROUP_DECENTRALIZED -> {
                val rolesDataIn = rolesFile.inputStream().dataIn()

                val groupMembers = mutableListOf<GroupMember>()
                val amount = rolesDataIn.readInt()
                for (i in 0 until amount) {
                    val member = readGroupMember(rolesDataIn)
                    groupMembers += member
                }

                val map = mutableMapOf<UUID, GroupRole>()

                for (groupMember in groupMembers) {
                    map.put(groupMember.contact.userUUID, groupMember.role)
                }

                val keyDataIn = keyFile.inputStream().dataIn()
                val key = keyDataIn.readUTF().toAESKey()

                createGroupChat(chatInfo, map, key, database, kentaiClient.userUUID)
            }
            else -> {
                TODO()
            }
        }

        for (file in folder.listFiles().filter { it.name != chatInfoFile.name && it.name != rolesFile.name && it.name != keyFile.name }) {
            val dataIn = file.inputStream().dataIn()
            saveMessage(chatInfo.chatUUID, readMessageWrapper(dataIn), database)
            dataIn.close()
        }

    }

    val statusChangeFolder = File(path + "/message_status_change/")
    if (statusChangeFolder.exists()) {
        for (file in statusChangeFolder.listFiles()) {
            val dataIn = file.inputStream().dataIn()
            saveMessageStatusChange(database, readMessageStatusChange(dataIn))
            dataIn.close()
        }

    }
    for (file in File(path + "/resources/").listFiles()) {
        for (resourceFile in file.listFiles()) {
            val filePath = resourceFile.path.substring(path.length)
            resourceFile.copyTo(File(context.filesDir.path + filePath), true)

            val pathList = filePath.substring("/resources/".length).split('/')
            val chatUUID = pathList[0]
            val list = pathList[1].split('.')
            val referenceUUID = list[0]
            val type = FileType.forExtension(list[1])
            setReferenceState(database, chatUUID.toUUID(), referenceUUID.toUUID(), type!!, UploadState.FINISHED)
        }
    }
}

fun writeContact(contact: Contact, dataOut: DataOutputStream) {
    dataOut.writeUTF(contact.name)
    dataOut.writeUTF(contact.alias)
    dataOut.writeUUID(contact.userUUID)
    dataOut.writeBoolean(contact.message_key != null)
    if (contact.message_key != null) {
        dataOut.writeUTF(BaseEncoding.base64().encode(contact.message_key.encoded))
    }
}

fun readContact(dataIn: DataInputStream): Contact {
    val name = dataIn.readUTF()
    val alias = dataIn.readUTF()
    val userUUID = dataIn.readUUID()
    if (dataIn.readBoolean()) {
        val key = dataIn.readUTF().toKey()
        return Contact(name, alias, userUUID, key)
    }
    return Contact(name, alias, userUUID, null)
}

fun writeChatInfo(chatInfo: ChatInfo, dataOut: DataOutputStream) {
    dataOut.writeUTF(chatInfo.chatName)
    dataOut.writeInt(chatInfo.chatType.ordinal)
    dataOut.writeUUID(chatInfo.chatUUID)

    dataOut.writeInt(chatInfo.participants.size)
    for (participant in chatInfo.participants) {
        dataOut.writeUUID(participant.receiverUUID)
        dataOut.writeBoolean(participant.isActive)
    }
}

fun readChatInfo(dataIn: DataInputStream): ChatInfo {
    val chatName = dataIn.readUTF()
    val chatType = ChatType.values()[dataIn.readInt()]
    val chatUUID = dataIn.readUUID()

    val participantList = mutableListOf<ChatReceiver>()

    val participants = dataIn.readInt()
    for (i in 0 until participants) {
        val pUUID = dataIn.readUUID()
        val isActive = dataIn.readBoolean()
        val receiver = ChatReceiver(pUUID, null, ChatReceiver.ReceiverType.USER, isActive)
        participantList += receiver
    }

    return ChatInfo(chatUUID, chatName, chatType, participantList)
}

fun writeMessageStatusChange(msc: MessageStatusChange, dataOut: DataOutputStream) {
    dataOut.writeUUID(msc.messageUUID)
    dataOut.writeInt(msc.status.ordinal)
    dataOut.writeLong(msc.time)
}

fun readMessageStatusChange(dataIn: DataInputStream): MessageStatusChange {
    val messageUUID = dataIn.readUUID()
    val messageStatus = MessageStatus.values()[dataIn.readInt()]
    val time = dataIn.readLong()
    return MessageStatusChange(messageUUID, messageStatus, time)
}

fun writeGroupMember(role: GroupMember, dataOut: DataOutputStream) {
    dataOut.writeInt(role.role.ordinal)
    dataOut.writeUUID(role.contact.userUUID)
}

/**
 * The returned GroupMember object does not contain a valid contact, just the uuid is correct.
 * Don't use it in any other context but read it from the database
 */
fun readGroupMember(dataIn: DataInputStream): GroupMember {
    val role = GroupRole.values()[dataIn.readInt()]
    val userUUID = dataIn.readUUID()
    return GroupMember(Contact("", "", userUUID, null), role)
}