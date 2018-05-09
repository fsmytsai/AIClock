package com.fsmytsai.aiclock.service.app

import android.content.Context
import android.text.Spanned
import android.text.SpannableString
import android.text.style.ImageSpan
import android.support.v4.content.ContextCompat
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentManager


class ViewPagerAdapter(fm: FragmentManager, private val fragments: List<Fragment>, private val context: Context) : FragmentPagerAdapter(fm) {

    var tabTitles: Array<String>? = null
    var tabIcons: IntArray? = null

    override fun getPageTitle(position: Int): CharSequence? {
        if (tabIcons == null) {
            return tabTitles!![position]
        } else {
            if (tabTitles!![position] != "")
                return tabTitles!![position]
            val spannableString = SpannableString(" ")

            val drawable = ContextCompat.getDrawable(context, tabIcons!![position])

            val height = (SharedService.getActionBarSize(context) * 0.7).toInt()

            drawable!!.setBounds(0, 0, height, height)
            val imageSpan = ImageSpan(drawable)
            //to make our tabs icon only, set the Text as blank string with white space
            spannableString.setSpan(imageSpan, 0, spannableString.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return spannableString
        }
    }

    override fun getCount(): Int {
        return fragments.size
    }

    override fun getItem(position: Int): Fragment {
        return fragments[position] //從上方List<Fragment> fragments取得
    }

}