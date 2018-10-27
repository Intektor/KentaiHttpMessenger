package de.intektor.mercury.util

import android.util.Log

/**
 * @author Intektor
 */
object Logger {

    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
    }

    fun info(tag: String, message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
    }

    fun warning(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
    }
}