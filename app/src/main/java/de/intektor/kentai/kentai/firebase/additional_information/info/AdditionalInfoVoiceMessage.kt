package de.intektor.kentai.kentai.firebase.additional_information.info

import de.intektor.kentai.kentai.firebase.additional_information.IAdditionalInfo
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * @author Intektor
 */
class AdditionalInfoVoiceMessage : IAdditionalInfo {

    var lengthSeconds: Int = 0

    constructor(lengthSeconds: Int) {
        this.lengthSeconds = lengthSeconds
    }

    constructor()

    override fun writeToStream(output: DataOutputStream) {
        output.writeInt(lengthSeconds)
    }

    override fun readFromStream(input: DataInputStream) {
        lengthSeconds = input.readInt()
    }

}