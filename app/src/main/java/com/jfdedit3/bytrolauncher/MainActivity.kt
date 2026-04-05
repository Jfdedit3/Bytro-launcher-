package com.jfdedit3.bytrolauncher

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.PopupMenu
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.jfdedit3.bytrolauncher.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: GamePagerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        pagerAdapter = GamePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.offscreenPageLimit = GameRepository.games.size

        binding.gamePickerButton.setOnClickListener {
            showGameMenu(it)
        }

        binding.refreshButton.setOnClickListener {
            currentFragment().reloadPage()
        }

        binding.moreButton.setOnClickListener {
            showMoreMenu(it)
        }

        binding.urlInput.setOnEditorActionListener { _, actionId, event ->
            val isSubmit = actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE ||
                actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)

            if (isSubmit) {
                currentFragment().loadCustomUrl(binding.urlInput.text?.toString().orEmpty())
                true
            } else {
                false
            }
        }

        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUrlBox(currentFragment().getCurrentUrl())
            }
        })

        updateUrlBox(currentFragment().getCurrentUrl())
    }

    private fun showGameMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        GameRepository.games.forEachIndexed { index, game ->
            popup.menu.add(Menu.NONE, index, index, game.title)
        }
        popup.setOnMenuItemClickListener { item ->
            binding.viewPager.currentItem = item.itemId
            true
        }
        popup.show()
    }

    private fun showMoreMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(Menu.NONE, 1000, 0, "Open in browser")
        popup.menu.add(Menu.NONE, 1001, 1, "Reload page")
        popup.menu.add(Menu.NONE, 1002, 2, "Open default game URL")
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1000 -> openInBrowser(currentFragment().getCurrentUrl())
                1001 -> currentFragment().reloadPage()
                1002 -> currentFragment().loadCustomUrl(GameRepository.games[binding.viewPager.currentItem].url)
            }
            true
        }
        popup.show()
    }

    private fun openInBrowser(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private fun currentFragment(): GameWebFragment {
        return pagerAdapter.getFragment(binding.viewPager.currentItem)
    }

    fun updateUrlBox(url: String) {
        binding.urlInput.setText(url)
        binding.urlInput.setSelection(binding.urlInput.text?.length ?: 0)
    }
}
