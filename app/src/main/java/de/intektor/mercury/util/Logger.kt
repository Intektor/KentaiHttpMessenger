package de.intektor.mercury.util

import android.util.Log
import com.crashlytics.android.Crashlytics

/**
 * @author Intektor
 */
object Logger {

    fun debug(tag: String, message: String, throwable: Throwable? = null) {
        Log.d(tag, message, throwable)
        Crashlytics.log(Log.DEBUG, tag, message)
        if (throwable != null) Crashlytics.logException(throwable)
    }

    fun info(tag: String, message: String, throwable: Throwable? = null) {
        Log.i(tag, message, throwable)
        Crashlytics.log(Log.INFO, tag, message)
        if (throwable != null) Crashlytics.logException(throwable)
    }

    fun warning(tag: String, message: String, throwable: Throwable? = null) {
        Log.w(tag, message, throwable)
        Crashlytics.log(Log.WARN, tag, message)
        if (throwable != null) Crashlytics.logException(throwable)
    }

    fun error(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        Crashlytics.log(Log.ERROR, tag, message)
        if (throwable != null) Crashlytics.logException(throwable)
    }
}