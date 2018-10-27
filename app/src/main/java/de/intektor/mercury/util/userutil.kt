package de.intektor.mercury.util

import android.content.Context
import de.intektor.mercury.R
import de.intektor.mercury.client.ClientPreferences
import de.intektor.mercury.contacts.Contact

/**
 * @author Intektor
 */
fun getName(contact: Contact, context: Context, personal: Boolean = false): String {
    val client = ClientPreferences.getClientUUID(context)

    return if (personal && contact.userUUID == client) context.getString(R.string.group_role_member_yourself_label) else contact.name
}
