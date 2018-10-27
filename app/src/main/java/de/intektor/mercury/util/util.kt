package de.intektor.mercury.util

import android.content.Context
import java.io.File


/**
 * @author Intektor
 */
fun internalFile(name: String, context: Context) = File(context.filesDir.path + "/" + name)

fun StringBuilder.newLine() {
    this.append('\n')
}