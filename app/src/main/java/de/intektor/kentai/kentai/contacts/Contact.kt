package de.intektor.kentai.kentai.contacts

import java.security.Key
import java.util.*

/**
 * @author Intektor
 */
data class Contact(val name: String, val alias: String, val userUUID: UUID, val message_key: Key?)