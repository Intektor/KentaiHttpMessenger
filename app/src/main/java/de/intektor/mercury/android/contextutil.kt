package de.intektor.mercury.android

import android.content.Context
import de.intektor.mercury.MercuryClient

/**
 * @author Intektor
 */
fun Context.mercuryClient() = this.applicationContext as MercuryClient