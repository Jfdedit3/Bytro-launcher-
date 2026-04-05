package com.jfdedit3.bytrolauncher

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class GamePagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    private val fragments = GameRepository.games.map { GameWebFragment.newInstance(it) }

    override fun getItemCount(): Int = fragments.size

    override fun createFragment(position: Int): Fragment = fragments[position]

    fun getFragment(position: Int): GameWebFragment = fragments[position]
}
