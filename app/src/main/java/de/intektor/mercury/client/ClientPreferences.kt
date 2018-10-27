package de.intektor.mercury.client

import android.content.Context
import com.google.common.io.BaseEncoding
import de.intektor.mercury_common.util.toKey
import de.intektor.mercury_common.util.toPrivateKey
import de.intektor.mercury_common.util.toUUID
import java.security.Key
import java.security.PrivateKey
import java.security.PublicKey
import java.util.*

/**
 * @author Intektor
 */
object ClientPreferences {

    private const val NAME_SHARED_PREFERENCE = "de.intektor.mercury.CLIENT_PREFERENCES"

    private const val KEY_CLIENT_UUID = "de.intektor.mercury.CLIENT_UUID"
    private const val KEY_USERNAME = "de.intektor.mercury.USERNAME"

    private const val KEY_PRIVATE_MESSAGE_KEY = "de.intektor.mercury.PRIVATE_MESSAGE_KEY"
    private const val KEY_PUBLIC_MESSAGE_KEY = "de.intektor.mercury.PUBLIC_MESSAGE_KEY"

    private const val KEY_PRIVATE_AUTH_KEY = "de.intektor.mercury.PRIVATE_AUTH_KEY"
    private const val KEY_PUBLIC_AUTH_KEY = "de.intektor.mercury.PUBLIC_AUTH_KEY"

    private const val KEY_IS_REGISTERED = "de.intektor.mercury.REGISTERED"

    private fun getPreferences(context: Context) = context.getSharedPreferences(NAME_SHARED_PREFERENCE, Context.MODE_PRIVATE)

    fun setClientUUID(context: Context, uuid: UUID) {
        getPreferences(context)
                .edit()
                .putString(KEY_CLIENT_UUID, uuid.toString())
                .apply()
    }

    fun getClientUUID(context: Context): UUID {
        return getPreferences(context).getString(KEY_CLIENT_UUID, null)?.toUUID()
                ?: throw IllegalStateException("No user uuid set")
    }

    fun setUsername(context: Context, username: String) {
        getPreferences(context)
                .edit()
                .putString(KEY_USERNAME, username)
                .apply()
    }

    fun getUsername(context: Context): String {
        return getPreferences(context).getString(KEY_USERNAME, null)
                ?: throw IllegalStateException("No username set")
    }

    private fun setKey(context: Context, keyWord: String, value: Key) {
        getPreferences(context)
                .edit()
                .putString(keyWord, BaseEncoding.base64().encode(value.encoded))
                .apply()
    }

    private fun getPrivateKey(context: Context, keyWord: String): PrivateKey {
        return getPreferences(context).getString(keyWord, null)?.toPrivateKey()
                ?: throw IllegalStateException("No key set for $keyWord")
    }

    private fun getPublicKey(context: Context, keyWord: String): PublicKey {
        return getPreferences(context).getString(keyWord, null)?.toKey()
                ?: throw IllegalStateException("No key set for $keyWord")
    }

    fun setPrivateMessageKey(context: Context, privateKey: PrivateKey) = setKey(context, KEY_PRIVATE_MESSAGE_KEY, privateKey)

    fun getPrivateMessageKey(context: Context) = getPrivateKey(context, KEY_PRIVATE_MESSAGE_KEY)

    fun setPublicMessageKey(context: Context, publicKey: PublicKey) = setKey(context, KEY_PUBLIC_MESSAGE_KEY, publicKey)

    fun getPublicMessageKey(context: Context) = getPublicKey(context, KEY_PUBLIC_MESSAGE_KEY)

    fun setPrivateAuthKey(context: Context, privateKey: PrivateKey) = setKey(context, KEY_PRIVATE_AUTH_KEY, privateKey)

    fun getPrivateAuthKey(context: Context) = getPrivateKey(context, KEY_PRIVATE_AUTH_KEY)

    fun setPublicAuthKey(context: Context, publicKey: PublicKey) = setKey(context, KEY_PUBLIC_AUTH_KEY, publicKey)

    fun getPublicAuthKey(context: Context) = getPublicKey(context, KEY_PUBLIC_AUTH_KEY)

    fun setRegistered(context: Context, registered: Boolean) {
        getPreferences(context)
                .edit()
                .putBoolean(KEY_IS_REGISTERED, registered)
                .apply()
    }

    fun getIsRegistered(context: Context): Boolean = getPreferences(context).getBoolean(KEY_IS_REGISTERED, false)
}