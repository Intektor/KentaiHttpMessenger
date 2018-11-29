package de.intektor.mercury.chat.model

import de.intektor.mercury.contacts.Contact
import de.intektor.mercury_common.chat.GroupRole

/**
 * @author Intektor
 */
data class GroupMember(val contact: Contact, val role: GroupRole)