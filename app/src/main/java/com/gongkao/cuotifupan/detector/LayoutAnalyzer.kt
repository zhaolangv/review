package com.gongkao.cuotifupan.detector

import android.graphics.Rect
import com.gongkao.cuotifupan.ocr.TextBlock
import com.gongkao.cuotifupan.ocr.TextLine
import kotlin.math.abs

/**
 * 布局分析器：分析文字的空间分布
 */
object LayoutAnalyzer {
    
    /**
     * 检测选项的垂直排列特征
     * 返回：(是否符合题目布局, 选项数量)
     */
    fun analyzeQuestionLayout(textBlocks: List<TextBlock>): Pair<Boolean, Int> {
        if (textBlocks.isEmpty()) {
            return Pair(false, 0)
        }
        
        // 获取所有包含选项标记的行
        val optionLines = mutableListOf<OptionInfo>()
        
        textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val trimmed = line.text.trim()
                
                // 检测是否为选项行
                val optionLabel = when {
                    trimmed.matches(Regex("^([A-D])[.、)].*")) -> {
                        Regex("^([A-D])").find(trimmed)?.groupValues?.get(1)?.firstOrNull()
                    }
                    trimmed.matches(Regex("^([A-D])\\s+[\\u4e00-\\u9fa5]{2,}.*")) -> {
                        trimmed.first()
                    }
                    else -> null
                }
                
                if (optionLabel != null) {
                    optionLines.add(
                        OptionInfo(
                            label = optionLabel,
                            text = trimmed,
                            rect = line.boundingBox
                        )
                    )
                }
            }
        }
        
        // 至少需要2个选项
        if (optionLines.size < 2) {
            return Pair(false, 0)
        }
        
        // 检查选项是否符合典型题目布局
        val hasValidLayout = checkOptionsLayout(optionLines)
        
        return Pair(hasValidLayout, optionLines.size)
    }
    
    /**
     * 检查选项布局是否合理
     */
    private fun checkOptionsLayout(options: List<OptionInfo>): Boolean {
        if (options.size < 2) return false
        
        // 按 Y 坐标排序
        val sortedOptions = options.sortedBy { it.rect.top }
        
        var validCount = 0
        
        // 检查1：选项标签是否按 A、B、C、D 顺序出现
        val expectedOrder = listOf('A', 'B', 'C', 'D')
        var orderCorrect = true
        var lastIndex = -1
        
        for (option in sortedOptions) {
            val currentIndex = expectedOrder.indexOf(option.label)
            if (currentIndex <= lastIndex) {
                orderCorrect = false
                break
            }
            lastIndex = currentIndex
        }
        
        if (orderCorrect) validCount++
        
        // 检查2：选项是否垂直排列（Y坐标递增）
        var verticallyArranged = true
        for (i in 0 until sortedOptions.size - 1) {
            val current = sortedOptions[i]
            val next = sortedOptions[i + 1]
            
            // 下一个选项的 Y 坐标应该大于当前选项
            if (next.rect.top <= current.rect.bottom) {
                verticallyArranged = false
                break
            }
        }
        
        if (verticallyArranged) validCount++
        
        // 检查3：选项 X 坐标是否基本对齐（左对齐，允许10%误差）
        if (sortedOptions.size >= 3) {
            val xPositions = sortedOptions.map { it.rect.left }
            val avgX = xPositions.average()
            val maxDeviation = xPositions.map { abs(it - avgX) }.maxOrNull() ?: 0.0
            
            // 如果最大偏差小于平均值的20%，认为对齐
            if (avgX > 0 && maxDeviation / avgX < 0.2) {
                validCount++
            }
        }
        
        // 检查4：选项间距是否相对均匀
        if (sortedOptions.size >= 3) {
            val gaps = mutableListOf<Int>()
            for (i in 0 until sortedOptions.size - 1) {
                val gap = sortedOptions[i + 1].rect.top - sortedOptions[i].rect.bottom
                gaps.add(gap)
            }
            
            val avgGap = gaps.average()
            val gapVariance = gaps.map { abs(it - avgGap) }.average()
            
            // 如果间距方差小于平均间距的50%，认为均匀
            if (avgGap > 0 && gapVariance / avgGap < 0.5) {
                validCount++
            }
        }
        
        // 至少满足2个布局特征才认为是题目
        return validCount >= 2
    }
    
    /**
     * 选项信息
     */
    private data class OptionInfo(
        val label: Char,
        val text: String,
        val rect: Rect
    )
}

