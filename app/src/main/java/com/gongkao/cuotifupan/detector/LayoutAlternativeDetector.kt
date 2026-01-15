package com.gongkao.cuotifupan.detector

import android.graphics.Rect
import com.gongkao.cuotifupan.ocr.TextBlock
import com.gongkao.cuotifupan.ocr.TextLine
import kotlin.math.abs
import kotlin.math.max

/**
 * 布局替代检测器：处理ABCD被遮挡的情况
 */
object LayoutAlternativeDetector {
    
    /**
     * 检测是否有布局替代的选项结构
     * 规则：下半部分存在 ≥3 个短行且 x 坐标接近且 bbox_vertical_alignment_score >= 0.7
     * 增强：对部分遮挡更鲁棒，即使选项标记被遮挡，也能通过布局结构识别
     */
    fun detectAlternativeOptions(textBlocks: List<TextBlock>): AlternativeOptionResult {
        if (textBlocks.isEmpty()) {
            return AlternativeOptionResult(false, 0, 0.0)
        }
        
        // 计算图片总高度
        val maxY = textBlocks.map { it.boundingBox.bottom }.maxOrNull() ?: 0
        val midY = maxY / 2
        
        // 获取下半部分的所有行（放宽短行长度限制，部分遮挡时可能识别不完整）
        val bottomHalfLines = textBlocks
            .filter { it.boundingBox.top >= midY }
            .flatMap { it.lines }
            .filter { 
                val trimmed = it.text.trim()
                // 放宽短行长度限制：从20字符放宽到30字符（部分遮挡时可能识别不完整）
                trimmed.length < 30 && trimmed.length >= 2  // 至少2个字符，避免单字符误识别
            }
        
        // 降低要求：从3个降低到2个（部分遮挡时可能识别不完整）
        if (bottomHalfLines.size < 2) {
            return AlternativeOptionResult(false, bottomHalfLines.size, 0.0)
        }
        
        // 检查X坐标对齐（放宽对齐要求，部分遮挡时可能对齐不完美）
        val xPositions = bottomHalfLines.map { it.boundingBox.left }
        val avgX = xPositions.average()
        val maxDeviation = xPositions.map { abs(it - avgX) }.maxOrNull() ?: 0.0
        
        // 计算对齐分数（放宽对齐要求：从0.3放宽到0.4）
        val alignmentScore = if (avgX > 0) {
            max(0.0, 1.0 - (maxDeviation / avgX) / 0.4)  // 从0.3放宽到0.4
        } else {
            0.0
        }
        
        // 检查垂直间距一致性（新增：选项之间的间距应该相似）
        val sortedLines = bottomHalfLines.sortedBy { it.boundingBox.top }
        val spacings = mutableListOf<Int>()
        for (i in 0 until sortedLines.size - 1) {
            val spacing = sortedLines[i + 1].boundingBox.top - sortedLines[i].boundingBox.bottom
            if (spacing > 0 && spacing < 200) {  // 间距在合理范围内
                spacings.add(spacing)
            }
        }
        
        val spacingConsistency = if (spacings.size >= 2) {
            val avgSpacing = spacings.average()
            val variance = spacings.map { (it - avgSpacing) * (it - avgSpacing) }.average()
            val stdDev = kotlin.math.sqrt(variance)
            if (avgSpacing > 0) {
                max(0.0, 1.0 - (stdDev / avgSpacing) / 0.3)  // 间距一致性
            } else {
                0.0
            }
        } else {
            0.0
        }
        
        // 综合评分：对齐分数(60%) + 间距一致性(40%)
        val combinedScore = alignmentScore * 0.6 + spacingConsistency * 0.4
        
        // 放宽条件：从0.7降低到0.6，从3个降低到2个
        val isValid = bottomHalfLines.size >= 2 && combinedScore >= 0.6
        
        return AlternativeOptionResult(
            isValid = isValid,
            shortLineCount = bottomHalfLines.size,
            alignmentScore = combinedScore  // 使用综合分数
        )
    }
    
    /**
     * 检测替代标记（· - ○ 等）作为选项线索
     */
    fun detectAlternativeMarkers(textBlocks: List<TextBlock>): Int {
        val markers = listOf("·", "-", "○", "●", "■", "□", "→", "→", "①", "②", "③", "④")
        
        var count = 0
        textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val text = line.text
                markers.forEach { marker ->
                    if (text.contains(marker)) {
                        count++
                    }
                }
            }
        }
        
        return count
    }
    
    /**
     * 检测分隔符作为选项分隔线索
     */
    fun detectSeparatorMarkers(textBlocks: List<TextBlock>): Int {
        val separators = listOf("|", "｜", "/", "\\", "——", "——", "|")
        
        var count = 0
        textBlocks.forEach { block ->
            block.lines.forEach { line ->
                val text = line.text
                separators.forEach { sep ->
                    if (text.contains(sep)) {
                        count++
                    }
                }
            }
        }
        
        return count
    }
}

/**
 * 替代选项检测结果
 */
data class AlternativeOptionResult(
    val isValid: Boolean,
    val shortLineCount: Int,
    val alignmentScore: Double
)

