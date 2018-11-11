package de.intektor.mercury.ui.support

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentPagerAdapter
import androidx.viewpager.widget.ViewPager

/**
 * @author Intektor
 */
abstract class FragmentViewImageResetAdapter(fragmentManager: FragmentManager) : FragmentPagerAdapter(fragmentManager), ViewPager.OnPageChangeListener {

    internal val fragments = mutableMapOf<Int, Fragment?>()

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val fragment = super.instantiateItem(container, position)

        fragments[position] = if (fragment is Fragment) fragment else null

        return fragment
    }

    override fun onPageScrollStateChanged(state: Int) {

    }

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
    }

    override fun onPageSelected(position: Int) {
        (0..fragments.size).filterNot { it == position }.forEach { it ->
            val item = fragments[it]

            if (item is FragmentViewImage) item.reset()
        }
    }
}