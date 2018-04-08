package de.intektor.kentai.kentai.contacts

import android.os.Parcel
import android.os.Parcelable
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
data class Contact(val name: String, val alias: String, val userUUID: UUID, val message_key: Key?) : Parcelable {
    constructor(parcel: Parcel) : this(
            parcel.readString(),
            parcel.readString(),
            UUID(parcel.readLong(), parcel.readLong()),
            if (parcel.readByte() == 1.toByte()) parcel.readSerializable() as Key else null)

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(alias)
        parcel.writeLong(userUUID.mostSignificantBits)
        parcel.writeLong(userUUID.leastSignificantBits)
        parcel.writeByte(if (message_key == null) 0 else 1)
        if (message_key != null) {
            parcel.writeSerializable(message_key)
        }
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<Contact> {
        override fun createFromParcel(parcel: Parcel): Contact = Contact(parcel)

        override fun newArray(size: Int): Array<Contact?> = arrayOfNulls(size)
    }
}