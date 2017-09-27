package de.intektor.kentai.kentai.firebase.additional_information.info

import de.intektor.kentai.kentai.firebase.additional_information.IAdditionalInfo
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * @author Intektor
 */
class AdditionalInfoGroupInviteMessage : IAdditionalInfo {

    lateinit var invitedByUsername: String
    lateinit var groupName: String

    constructor(invitedByUsername: String, groupName: String) {
        this.invitedByUsername = invitedByUsername
        this.groupName = groupName
    }

    constructor()

    override fun writeToStream(output: DataOutputStream) {
        output.writeUTF(invitedByUsername)
        output.writeUTF(groupName)
    }

    override fun readFromStream(input: DataInputStream) {
        invitedByUsername = input.readUTF()
        groupName = input.readUTF()
    }

}