package de.intektor.kentai.kentai.firebase.additional_information.info

import de.intektor.kentai.kentai.firebase.additional_information.IAdditionalInfo
import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * @author Intektor
 */
class AdditionalInfoEmpty() : IAdditionalInfo {

    override fun writeToStream(output: DataOutputStream) {

    }

    override fun readFromStream(input: DataInputStream) {

    }

}