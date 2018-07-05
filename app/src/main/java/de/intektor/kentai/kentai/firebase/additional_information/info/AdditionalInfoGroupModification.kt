package de.intektor.kentai.kentai.firebase.additional_information.info

import de.intektor.kentai.kentai.firebase.additional_information.IAdditionalInfo
import de.intektor.kentai_http_common.chat.group_modification.GroupModification
import de.intektor.kentai_http_common.chat.group_modification.GroupModificationRegistry
import de.intektor.kentai_http_common.util.readUUID
import de.intektor.kentai_http_common.util.toUUID
import de.intektor.kentai_http_common.util.writeUUID
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * @author Intektor
 */
class AdditionalInfoGroupModification : IAdditionalInfo {

    lateinit var groupModification: GroupModification

    constructor(groupModification: GroupModification) {
        this.groupModification = groupModification
    }

    constructor()

    override fun writeToStream(output: DataOutputStream) {
        output.writeInt(GroupModificationRegistry.getID(groupModification.javaClass))
        output.writeUUID(groupModification.chatUUID.toUUID())
        output.writeUUID(groupModification.modificationUUID.toUUID())
        groupModification.write(output)
    }

    override fun readFromStream(input: DataInputStream) {
        groupModification = GroupModificationRegistry.create(input.readInt(), input.readUUID(), input.readUUID())
        groupModification.read(input)
    }
}