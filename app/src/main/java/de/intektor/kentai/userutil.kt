package de.intektor.kentai

import android.content.Context
import de.intektor.kentai.kentai.contacts.Contact

/**
 * @author Intektor
 */
fun getName(contact: Contact, context: Context, personal: Boolean = false): String {
    val kentaiClient = context.applicationContext as KentaiClient
    return if (personal && contact.userUUID == kentaiClient.userUUID) context.getString(R.string.group_role_member_yourself_label) else contact.name
}
