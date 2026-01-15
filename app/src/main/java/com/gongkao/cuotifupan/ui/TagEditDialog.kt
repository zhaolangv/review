package com.gongkao.cuotifupan.ui

import android.app.AlertDialog
import android.content.Context
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.gongkao.cuotifupan.R

/**
 * 标签编辑对话框
 */
object TagEditDialog {
    
    fun show(context: Context, currentTags: List<String>, onTagsChanged: (List<String>) -> Unit) {
        val selectedTags = currentTags.toMutableList()
        Log.d("TagEditDialog", "初始化 TagEditDialog，当前标签: $currentTags，selectedTags: $selectedTags")
        
        val builder = AlertDialog.Builder(context)
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_edit_tags, null)
        
        val tagInput = view.findViewById<EditText>(R.id.tagInput)
        val presetTagsContainer = view.findViewById<LinearLayout>(R.id.presetTagsContainer)
        
        fun createTagChip(tag: String, displayText: String, isSelected: Boolean): View {
            val chip = LayoutInflater.from(context).inflate(R.layout.item_tag_chip, presetTagsContainer, false)
            val chipText = chip.findViewById<TextView>(R.id.tagText)
            chipText.text = displayText
            
            // 设置选中状态
            if (isSelected) {
                chip.setBackgroundResource(R.drawable.bg_question_type)
                chipText.setTextColor(android.graphics.Color.WHITE)
                chip.alpha = 1.0f
            } else {
                chip.setBackgroundResource(R.drawable.bg_question_type)
                chipText.setTextColor(android.graphics.Color.WHITE)
                chip.alpha = 0.5f // 未选中时半透明
            }
            
            chip.setOnClickListener {
                if (selectedTags.contains(tag)) {
                    selectedTags.remove(tag)
                    chip.alpha = 0.5f
                } else {
                    selectedTags.add(tag)
                    chip.alpha = 1.0f
                }
            }
            
            return chip
        }
        
        fun displayPresetTags() {
            presetTagsContainer.removeAllViews()
            
            // 获取实际容器宽度（更准确）
            val displayMetrics = context.resources.displayMetrics
            var availableWidth = presetTagsContainer.width
            if (availableWidth <= 0) {
                // 如果容器还没有布局，使用屏幕宽度估算
                val screenWidth = displayMetrics.widthPixels
                val dialogPadding = (12 * displayMetrics.density).toInt() * 2 // 左右padding
                availableWidth = (screenWidth - dialogPadding - 80).coerceAtLeast(200)
            }
            
            Log.d("TagEditDialog", "可用宽度: $availableWidth")
                    
                    // 创建标签容器（水平布局，支持换行）
            fun createNewRow(): LinearLayout {
                return LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        // 添加行间距（除了第一行），减小间距
                        if (presetTagsContainer.childCount > 0) {
                            topMargin = (2 * displayMetrics.density).toInt() // 2dp 行间距
                        }
                    }
                }
            }
            
            var currentRow = createNewRow()
            presetTagsContainer.addView(currentRow)
            
            var currentRowWidth = 0
            
            // 添加标签到行的辅助函数
            fun addChipToRow(chip: View) {
                // 减小标签间距
                val chipMargin = (4 * displayMetrics.density).toInt() // 4dp 标签间距（减小）
                
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
                    presetTagsContainer.addView(currentRow)
                    currentRowWidth = 0
                }
                
                // 添加标签到当前行
                currentRow.addView(chip)
                currentRowWidth += totalChipWidth
                
                Log.d("TagEditDialog", "添加标签: 文本='${chipText?.text}', 总宽度=$totalChipWidth, 当前行宽度=$currentRowWidth, 可用宽度=$availableWidth, 需要换行=$needsNewRow")
            }
            
            // 添加题目类型标签
            val typeTags = listOf("文字题", "图推题")
            typeTags.forEach { tag ->
                val chip = createTagChip(tag, tag, selectedTags.contains(tag))
                addChipToRow(chip)
            }
            
            // 添加用户自定义标签
            val userTags = TagManager.getUserTags(context)
            userTags.forEach { tag ->
                val chip = createTagChip(tag, tag, selectedTags.contains(tag))
                addChipToRow(chip)
            }
            
            // 添加系统预设标签（直接显示标签名）
            TagManager.getAllSystemTags().forEach { tag ->
                val chip = createTagChip(tag, tag, selectedTags.contains(tag))
                addChipToRow(chip)
            }
            
            // 布局完成后，重新计算可用宽度并检查被截断的标签
            presetTagsContainer.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    presetTagsContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    
                    // 获取实际容器宽度
                    val actualAvailableWidth = presetTagsContainer.width
                    if (actualAvailableWidth > 0) {
                        Log.d("TagEditDialog", "实际容器宽度: $actualAvailableWidth")
                    }
                    
                    // 检查并修复被截断的标签
                    presetTagsContainer.post {
                        // 检查每一行，确保标签完整显示
                        var needsRelayout = false
                        
                        for (i in 0 until presetTagsContainer.childCount) {
                            val row = presetTagsContainer.getChildAt(i) as? LinearLayout ?: continue
                            
                            // 检查这一行的每个标签
                            for (j in 0 until row.childCount) {
                                val chip = row.getChildAt(j)
                                val chipText = chip.findViewById<TextView>(R.id.tagText)
                                
                                // 检查标签是否被截断
                                if (chipText != null && chip.width > 0) {
                                    // 检查文本是否被截断（通过检查 TextView 的 ellipsize 或比较文本宽度）
                                    val textWidth = chipText.paint.measureText(chipText.text.toString())
                                    val availableTextWidth = chipText.width - chipText.paddingLeft - chipText.paddingRight
                                    
                                    // 如果文本宽度超过可用宽度，说明被截断了
                                    if (textWidth > availableTextWidth + 5) { // 5px 容差
                                        Log.d("TagEditDialog", "发现被截断的标签: '${chipText.text}', 文本宽度=$textWidth, 可用宽度=$availableTextWidth")
                                        
                                        // 将这个标签移到下一行
                                        row.removeViewAt(j)
                                        
                                        // 创建新行或使用下一行
                                        val nextRowIndex = i + 1
                                        val nextRow = if (nextRowIndex < presetTagsContainer.childCount) {
                                            presetTagsContainer.getChildAt(nextRowIndex) as? LinearLayout
                                        } else {
                                            createNewRow().also { presetTagsContainer.addView(it) }
                                        }
                                        
                                        nextRow?.addView(chip)
                                        needsRelayout = true
                                        break // 一次只移动一个，然后重新布局
                                    }
                                }
                            }
                            
                            if (needsRelayout) break
                        }
                        
                        // 如果需要重新布局，递归检查
                        if (needsRelayout) {
                            presetTagsContainer.post {
                                // 再次检查（最多检查3次，避免无限循环）
                                var checkCount = 0
                                fun checkAndFix() {
                                    if (checkCount++ >= 3) return
                                    
                                    var fixed = false
                                    for (i in 0 until presetTagsContainer.childCount) {
                                        val row = presetTagsContainer.getChildAt(i) as? LinearLayout ?: continue
                                        
                                        for (j in 0 until row.childCount) {
                                            val chip = row.getChildAt(j)
                                            val chipText = chip.findViewById<TextView>(R.id.tagText)
                                            
                                            if (chipText != null && chip.width > 0) {
                                                val textWidth = chipText.paint.measureText(chipText.text.toString())
                                                val availableTextWidth = chipText.width - chipText.paddingLeft - chipText.paddingRight
                                                
                                                if (textWidth > availableTextWidth + 5) {
                                                    row.removeViewAt(j)
                                                    val nextRowIndex = i + 1
                                                    val nextRow = if (nextRowIndex < presetTagsContainer.childCount) {
                                                        presetTagsContainer.getChildAt(nextRowIndex) as? LinearLayout
                                                    } else {
                                                        createNewRow().also { presetTagsContainer.addView(it) }
                                                    }
                                                    nextRow?.addView(chip)
                                                    fixed = true
                                                    break
                                                }
                                            }
                                        }
                                        if (fixed) break
                                    }
                                    
                                    if (fixed) {
                                        presetTagsContainer.post { checkAndFix() }
                                    }
                                }
                                checkAndFix()
                            }
                        }
                    }
                }
            })
        }
        
        // 提取添加标签的逻辑为独立函数
        fun addCustomTag(customTag: String) {
            Log.d("TagEditDialog", "addCustomTag 被调用，标签: '$customTag'")
            Log.d("TagEditDialog", "addCustomTag 调用前 selectedTags: $selectedTags")
            
            // 如果标签不存在，添加到选中列表
            if (!selectedTags.contains(customTag)) {
                selectedTags.add(customTag)
                Log.d("TagEditDialog", "标签已添加到 selectedTags，当前列表: $selectedTags")
            } else {
                Log.d("TagEditDialog", "标签已存在，跳过添加")
            }
            
            // 保存到用户标签体系（如果还没有保存过）
            TagManager.addUserTag(context, customTag)
            
            // 刷新标签显示（新标签会出现在预设标签列表中，并自动选中）
            displayPresetTags()
            tagInput.text.clear()
            
            Log.d("TagEditDialog", "addCustomTag 调用后 selectedTags: $selectedTags")
        }
        
        // 初始化显示
        displayPresetTags()
        
        tagInput.setOnEditorActionListener { v, actionId, event ->
            val customTag = tagInput.text.toString().trim()
            Log.d("TagEditDialog", "[回车] 用户输入标签: '$customTag', actionId=$actionId")
            if (customTag.isNotBlank()) {
                addCustomTag(customTag)
            }
            true
        }
        
        builder.setView(view)
            .setTitle("编辑标签")
            .setPositiveButton("确定") { _, _ ->
                // 确保 selectedTags 是最新的（包含所有选中的标签）
                Log.d("TagEditDialog", "确定按钮点击，保存标签: $selectedTags")
                Log.d("TagEditDialog", "selectedTags 大小: ${selectedTags.size}")
                Log.d("TagEditDialog", "selectedTags 内容: ${selectedTags.joinToString(", ")}")
                onTagsChanged(selectedTags.toList()) // 转换为不可变列表
            }
            .setNegativeButton("取消", null)
            .show()
        
        // 添加额外的调试：监听输入框内容变化
        tagInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                Log.d("TagEditDialog", "输入框内容变化: '${s?.toString()}'")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }
}
