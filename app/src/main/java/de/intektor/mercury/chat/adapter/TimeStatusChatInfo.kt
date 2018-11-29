package de.intektor.mercury.chat.adapter

import de.intektor.mercury_common.chat.MessageStatus

class TimeStatusChatInfo(val time: Long, var status: MessageStatus, val isClient: Boolean) : ChatAdapterSubItem()