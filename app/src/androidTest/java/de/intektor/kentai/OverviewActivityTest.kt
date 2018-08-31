package de.intektor.kentai

import android.support.test.InstrumentationRegistry
import android.support.test.filters.LargeTest
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import de.intektor.kentai.kentai.DbHelper
import de.intektor.kentai.overview_activity.OverviewActivity
import org.junit.Before
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * @author Intektor
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class OverviewActivityTest {

    @get:Rule
    val activityRule = ActivityTestRule<OverviewActivity>(OverviewActivity::class.java)


    @Before
    fun setup() {
        val context = InstrumentationRegistry.getContext()

        val databaseHelper = DbHelper(context)

    }
}