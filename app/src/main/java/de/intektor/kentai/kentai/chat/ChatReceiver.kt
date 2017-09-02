package de.intektor.kentai.kentai.chat

import android.os.Parcel
import android.os.Parcelable
import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
data class ChatReceiver(val receiverUUID: UUID, var publicKey: Key?, val type: ReceiverType) : Parcelable {

    constructor(parcel: Parcel) : this(parcel.readSerializable() as UUID, parcel.readSerializable() as Key?, parcel.readSerializable() as ReceiverType) {
    }

    enum class ReceiverType {
        USER,
        GROUP
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeSerializable(receiverUUID)
        parcel.writeSerializable(publicKey)
        parcel.writeSerializable(type)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ChatReceiver> {
        override fun createFromParcel(parcel: Parcel): ChatReceiver = ChatReceiver(parcel)

        override fun newArray(size: Int): Array<ChatReceiver?> = arrayOfNulls(size)
    }
}