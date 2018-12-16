@file:Suppress("DEPRECATION")

package de.intektor.mercury.util

import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View

/**
 * @author Intektor
 */
fun View.setCompatBackground(drawable: Drawable) {
    if (Build.VERSION.SDK_INT >= 16) {
        background = drawable
    } else {
        setBackgroundDrawable(drawable)
    }
}

fun Resources.getCompatDrawable(id: Int, theme: Resources.Theme): Drawable {
    return if (Build.VERSION.SDK_INT >= 21) {
        getDrawable(id, theme)
    } else {
        getDrawable(id)
    }
}

fun Resources.getCompatColor(colorId: Int, theme: Resources.Theme): Int {
    return if(Build.VERSION.SDK_INT >= 23) {
        getColor(colorId, theme)
    } else {
        getColor(colorId)
    }
}