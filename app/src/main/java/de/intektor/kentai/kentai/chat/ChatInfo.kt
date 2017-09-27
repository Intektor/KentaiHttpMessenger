package de.intektor.kentai.kentai.chat

import android.os.Parcel
import android.os.Parcelable
import de.intektor.kentai_http_common.chat.ChatType
import java.util.*

/**
 * @author Intektor
 */
data class ChatInfo(val chatUUID: UUID, val chatName: String, val chatType: ChatType, val participants: List<ChatReceiver>) : Parcelable {

    constructor(parcel: Parcel) : this(parcel.readSerializable() as UUID, parcel.readString(), ChatType.values()[parcel.readInt()], parcel.createTypedArrayList(ChatReceiver))

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeSerializable(chatUUID)
        parcel.writeString(chatName)
        parcel.writeInt(chatType.ordinal)
        parcel.writeTypedList(participants)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ChatInfo> {
        override fun createFromParcel(parcel: Parcel): ChatInfo = ChatInfo(parcel)

        override fun newArray(size: Int): Array<ChatInfo?> = arrayOfNulls(size)
    }
}