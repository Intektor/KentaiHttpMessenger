package de.intektor.kentai.kentai.firebase.additional_information.info

import de.intektor.kentai.kentai.firebase.additional_information.IAdditionalInfo
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * @author Intektor
 */
class AdditionalInfoVideoMessage : IAdditionalInfo {

    var durationSeconds: Int = 0

    constructor(durationSeconds: Int) {
        this.durationSeconds = durationSeconds
    }

    constructor()

    override fun writeToStream(output: DataOutputStream) {
        output.writeInt(durationSeconds)
    }

    override fun readFromStream(input: DataInputStream) {
        durationSeconds = input.readInt()
    }

}