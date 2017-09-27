package de.intektor.kentai.kentai.firebase.additional_information

import java.io.DataInputStream
import java.io.DataOutputStream

/**
 * @author Intektor
 */
interface IAdditionalInfo {
    abstract fun writeToStream(output: DataOutputStream)

    abstract fun readFromStream(input: DataInputStream)
}