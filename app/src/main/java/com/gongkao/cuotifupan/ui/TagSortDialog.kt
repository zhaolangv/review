package com.gongkao.cuotifupan.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.widget.NestedScrollView
import com.gongkao.cuotifupan.R

/**
 * 标签排序选择对话框
 */
object TagSortDialog {
    
    fun show(context: Context, onTagSelected: (String) -> Unit) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("选择标签进行排序")
        
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_tag_sort, null)
        
        val tagsContainer = view.findViewById<LinearLayout>(R.id.tagsContainer)
        
        // 清空容器
        tagsContainer.removeAllViews()
        
        // 获取实际容器宽度（更准确）
        val displayMetrics = context.resources.displayMetrics
        var availableWidth = tagsContainer.width
        if (availableWidth <= 0) {
            // 如果容器还没有布局，使用屏幕宽度估算
            val screenWidth = displayMetrics.widthPixels
            val dialogPadding = (12 * displayMetrics.density).toInt() * 2 // 左右padding
            availableWidth = (screenWidth - dialogPadding - 80).coerceAtLeast(200)
        }
        
        // 创建标签容器（水平布局，支持换行）
        fun createNewRow(): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    // 添加行间距（除了第一行），减小间距
                    if (tagsContainer.childCount > 0) {
                        topMargin = (2 * displayMetrics.density).toInt() // 2dp 行间距（与编辑标签一致）
                    }
                }
            }
        }
        
        var currentRow = createNewRow()
        tagsContainer.addView(currentRow)
        
        var currentRowWidth = 0
        
        // 创建对话框（需要在添加标签之前创建，但不立即显示）
        val dialog = builder.setView(view)
            .setPositiveButton("确定", null) // 先设置为null，稍后自定义处理
            .setNegativeButton("取消", null)
            .create()
        
        // 添加标签到行的辅助函数（与编辑标签对话框一致）
        fun addChipToRow(chip: View) {
            // 减小标签间距
            val chipMargin = (4 * displayMetrics.density).toInt() // 4dp 标签间距（与编辑标签一致）
            
            // 确保标签内容横向显示，但不截断
            val chipText = chip.findViewById<TextView>(R.id.tagText)
            if (chipText != null) {
                chipText.maxLines = 1
                chipText.ellipsize = null // 不截断，让标签完整显示
            }
            
            // 先设置 margin，再测量
            val chipLayoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(chipMargin, 0, chipMargin, 0)
            }
            chip.layoutParams = chipLayoutParams
            
            // 测量标签宽度（使用 UNSPECIFIED 来获取实际需要的宽度，不受限制）
            // 注意：由于 layoutParams 已经设置了 margin，measuredWidth 已经包含了 margin
            chip.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            // measuredWidth 已经包含了 margin（因为 layoutParams 设置了 margin）
            val totalChipWidth = chip.measuredWidth
            
            // 如果当前行放不下整个标签（包括 margin），创建新行（确保标签完整显示）
            // 使用更保守的判断：如果加上这个标签会超过可用宽度，就换行
            // 增大容差（20px），确保标签不会被截断
            val needsNewRow = currentRow.childCount > 0 && (currentRowWidth + totalChipWidth > availableWidth - 20)
            
            if (needsNewRow) {
                currentRow = createNewRow()
                tagsContainer.addView(currentRow)
                currentRowWidth = 0
            }
            
            // 添加标签到当前行
            currentRow.addView(chip)
            currentRowWidth += totalChipWidth
        }
        
        // 当前选中的标签和对应的View
        var selectedTag: String? = null
        var selectedChip: View? = null
        
        // 创建标签芯片的辅助函数
        fun createTagChip(tag: String, displayText: String): View {
            val chip = LayoutInflater.from(context).inflate(R.layout.item_tag_chip, tagsContainer, false)
            val chipText = chip.findViewById<TextView>(R.id.tagText)
            chipText.text = displayText
            
            // 默认未选中状态（参照编辑标签：白色文字，半透明）
            fun setUnselectedState() {
                chip.setBackgroundResource(R.drawable.bg_question_type)
                chipText.setTextColor(android.graphics.Color.WHITE)
                chip.alpha = 0.5f // 与编辑标签一致
            }
            
            // 选中状态（白色文字，不透明）
            fun setSelectedState() {
                chip.setBackgroundResource(R.drawable.bg_question_type)
                chipText.setTextColor(android.graphics.Color.WHITE)
                chip.alpha = 1.0f
            }
            
            // 初始状态：未选中
            setUnselectedState()
            
            // 点击标签：只切换选中状态，不立即筛选
            chip.setOnClickListener {
                if (selectedTag == tag) {
                    // 如果点击的是已选中的标签，取消选中
                    selectedTag = null
                    selectedChip = null
                    setUnselectedState()
                } else {
                    // 选中新标签
                    // 先取消之前选中的标签
                    selectedChip?.let { prevChip ->
                        val prevChipText = prevChip.findViewById<TextView>(R.id.tagText)
                        prevChip.setBackgroundResource(R.drawable.bg_question_type)
                        prevChipText.setTextColor(android.graphics.Color.WHITE)
                        prevChip.alpha = 0.5f
                    }
                    
                    selectedTag = tag
                    selectedChip = chip
                    setSelectedState()
                }
            }
            
            return chip
        }
        
        // 按照与编辑标签对话框相同的顺序添加标签
        // 1. 添加题目类型标签
        val typeTags = listOf("文字题", "图推题")
        typeTags.forEach { tag ->
            val chip = createTagChip(tag, tag)
            addChipToRow(chip)
        }
        
        // 2. 添加用户自定义标签
        val userTags = TagManager.getUserTags(context)
        userTags.forEach { tag ->
            val chip = createTagChip(tag, tag)
            addChipToRow(chip)
                }
                
        // 3. 添加系统预设标签（直接显示标签名）
        TagManager.getAllSystemTags().forEach { tag ->
            val chip = createTagChip(tag, tag)
            addChipToRow(chip)
        }
        
        // 自定义确定按钮的处理
        dialog.setOnShowListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positiveButton.setOnClickListener {
                // 点击确定才开始筛选
                if (selectedTag != null) {
                    onTagSelected(selectedTag!!)
                } else {
                    // 如果没有选中标签，清空筛选
                    onTagSelected("")
                }
                dialog.dismiss()
            }
        }
        
        dialog.show()
    }
}
