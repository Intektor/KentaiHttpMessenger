package de.intektor.mercury

import android.app.Activity
import android.content.Intent
import android.support.test.rule.ActivityTestRule

/**
 * @author Intektor
 */
class EditableActivityTestRule<T : Activity>(clazz: Class<T>, private val beforeActivityLaunched: () -> Unit, private val getIntent: (() -> Intent)? = null) : ActivityTestRule<T>(clazz) {
    override fun beforeActivityLaunched() = beforeActivityLaunched.invoke()



    override fun getActivityIntent(): Intent {
        return getIntent?.invoke() ?: super.getActivityIntent()
    }
}