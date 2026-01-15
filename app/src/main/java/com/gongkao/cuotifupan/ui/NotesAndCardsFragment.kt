package com.gongkao.cuotifupan.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import com.gongkao.cuotifupan.R

/**
 * 笔记和卡片管理 Fragment（合并页面）
 */
class NotesAndCardsFragment : Fragment() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_notes_and_cards, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        (activity as? AppCompatActivity)?.supportActionBar?.title = "笔记与卡片"
        
        viewPager = view.findViewById(R.id.viewPager)
        tabLayout = view.findViewById(R.id.tabLayout)
        
        // 创建适配器
        viewPager.adapter = NotesAndCardsPagerAdapter(this)
        
        // 关联 TabLayout 和 ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "笔记"
                1 -> "卡片"
                else -> ""
            }
        }.attach()
    }
    
    /**
     * 获取当前活动的子 Fragment
     */
    private fun getCurrentChildFragment(): Fragment? {
        val currentItem = viewPager.currentItem
        // 通过 FragmentManager 查找当前 Fragment
        // ViewPager2 使用 "f" + viewId + ":" + id 作为 tag
        val fragmentTag = "f${viewPager.id}:${currentItem}"
        return childFragmentManager.findFragmentByTag(fragmentTag) ?: 
               childFragmentManager.fragments.getOrNull(currentItem)
    }
    
    /**
     * 退出批量模式（委托给当前活动的子 Fragment）
     */
    fun exitBatchMode() {
        val currentFragment = getCurrentChildFragment()
        when (currentFragment) {
            is NotesFragment -> currentFragment.exitBatchMode()
            is FlashcardsFragment -> currentFragment.exitBatchMode()
        }
    }
    
    /**
     * 显示批量标签对话框（委托给当前活动的子 Fragment）
     */
    fun showBatchTagDialog() {
        val currentFragment = getCurrentChildFragment()
        when (currentFragment) {
            is NotesFragment -> currentFragment.showBatchTagDialog()
            is FlashcardsFragment -> currentFragment.showBatchTagDialog()
        }
    }
    
    /**
     * 显示批量删除对话框（委托给当前活动的子 Fragment）
     */
    fun showBatchDeleteDialog() {
        val currentFragment = getCurrentChildFragment()
        when (currentFragment) {
            is NotesFragment -> currentFragment.showBatchDeleteDialog()
            is FlashcardsFragment -> currentFragment.showBatchDeleteDialog()
        }
    }
}

/**
 * ViewPager2 适配器
 */
class NotesAndCardsPagerAdapter(fragment: Fragment) : androidx.viewpager2.adapter.FragmentStateAdapter(fragment) {
    
    override fun getItemCount(): Int = 2
    
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> NotesFragment()
            1 -> FlashcardsFragment()
            else -> NotesFragment()
        }
    }
}

