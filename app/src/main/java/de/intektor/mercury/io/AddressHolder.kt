package de.intektor.mercury.io

import de.intektor.mercury.BuildConfig

/**
 * @author Intektor
 */
object AddressHolder {
    val ADDRESS = if (BuildConfig.DEBUG) "192.168.178.27" else "intektor.de"

    val HTTP_ADDRESS = "http://$ADDRESS:17349/"
}