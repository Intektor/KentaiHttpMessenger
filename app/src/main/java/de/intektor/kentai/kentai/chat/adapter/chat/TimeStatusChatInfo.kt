package de.intektor.kentai.kentai.chat.adapter.chat

import de.intektor.kentai_http_common.chat.MessageStatus

class TimeStatusChatInfo(val time: Long, var status: MessageStatus, val isClient: Boolean)