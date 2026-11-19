package com.jfdedit3.bytrolauncher

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class GamePagerAdapter(private val activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = GameRepository.games.size

    override fun createFragment(position: Int): Fragment =
        GameWebFragment.newInstance(GameRepository.games[position])

    fun getFragment(position: Int): GameWebFragment? =
        activity.supportFragmentManager.findFragmentByTag("f${getItemId(position)}") as? GameWebFragment
}
