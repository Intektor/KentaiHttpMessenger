package de.intektor.mercury.chat

import android.os.Parcel
import android.os.Parcelable
import de.intektor.mercury_common.chat.ChatMessage
import de.intektor.mercury_common.gson.genGson
import java.util.*

data class PendingMessage(val message: ChatMessage, val chatUUID: UUID, val sendTo: List<ChatReceiver>) : Parcelable {

    constructor(parcel: Parcel) : this(
            gson.fromJson(parcel.readString(), ChatMessage::class.java),
            UUID.fromString(parcel.readString()),
            parcel.createTypedArrayList(ChatReceiver))

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(gson.toJson(message))
        parcel.writeString(chatUUID.toString())
        parcel.writeTypedList(sendTo)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<PendingMessage> {

        private val gson = genGson()

        override fun createFromParcel(parcel: Parcel): PendingMessage {
            return PendingMessage(parcel)
        }

        override fun newArray(size: Int): Array<PendingMessage?> {
            return arrayOfNulls(size)
        }
    }
}