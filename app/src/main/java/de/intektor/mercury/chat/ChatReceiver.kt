package de.intektor.mercury.chat

import android.os.Parcel
import android.os.Parcelable
import de.intektor.mercury.contacts.Contact
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
data class ChatReceiver(val receiverUUID: UUID, var publicKey: Key?, val type: ReceiverType, val isActive: Boolean = true) : Parcelable {

    constructor(parcel: Parcel) : this(parcel.readSerializable() as UUID, parcel.readSerializable() as Key?, parcel.readSerializable() as ReceiverType, parcel.readByte() == 1.toByte())

    enum class ReceiverType {
        USER,
        GROUP
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeSerializable(receiverUUID)
        parcel.writeSerializable(publicKey)
        parcel.writeSerializable(type)
        parcel.writeByte(if (isActive) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ChatReceiver> {
        override fun createFromParcel(parcel: Parcel): ChatReceiver = ChatReceiver(parcel)

        override fun newArray(size: Int): Array<ChatReceiver?> = arrayOfNulls(size)

        fun fromContact(contact: Contact, isActive: Boolean = true): ChatReceiver = ChatReceiver(contact.userUUID, contact.message_key, ReceiverType.USER, isActive)

    }
}