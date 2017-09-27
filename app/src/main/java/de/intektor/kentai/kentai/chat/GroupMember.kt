package de.intektor.kentai.kentai.chat

import de.intektor.kentai.kentai.contacts.Contact
import de.intektor.kentai_http_common.chat.GroupRole

/**
 * @author Intektor
 */
data class GroupMember(val contact: Contact, val role: GroupRole)