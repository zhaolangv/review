package com.gongkao.cuotifupan.detector

import android.graphics.Rect
import android.util.Log
import com.gongkao.cuotifupan.ocr.OcrResult
import com.gongkao.cuotifupan.ocr.TextBlock
import com.gongkao.cuotifupan.ocr.TextLine
import kotlin.math.max
import kotlin.math.min

/**
 * 题目区域检测结果
 */
data class QuestionRegion(
    val bounds: Rect,              // 题目区域边界（Bitmap 坐标）
    val questionNumber: String?,   // 题号（如 "16"）
    val confidence: Float          // 检测置信度
)

/**
 * 题目区域检测器
 * 严格按照新的实现逻辑：
 * Step 1: OCR识别（ML Kit已提供坐标）
 * Step 2: 题号识别（严格的正则和筛选条件）
 * Step 3: 题目区域生成（简单的边界计算）
 */
class QuestionRegionDetector {
    
    companion object {
        private const val TAG = "QuestionRegionDetector"
        
        // 题号正则表达式（只认这种格式）
        // ^\d{1,2}[\.、]$
        private val QUESTION_NUMBER_PATTERN = Regex("^(\\d{1,2})[.、]")
        
        // 边距设置
        private const val MARGIN_TOP = 15      // 10~20px
        private const val MARGIN_BOTTOM = 20   // 10~30px
        
        // 题号筛选条件
        private const val LEFT_REGION_RATIO = 0.3f  // 位于页面左侧30%区域
        private const val LINE_HEIGHT_RATIO = 0.6f  // 行高 > 页面平均行高 × 0.6（放宽条件，避免漏掉真实题号）
    }
    
    /**
     * 检测图片中的题目区域
     * @param ocrResult OCR 识别结果
     * @param imageWidth 原始图片宽度
     * @param imageHeight 原始图片高度
     * @return 检测到的题目区域列表
     */
    fun detectQuestionRegions(
        ocrResult: OcrResult,
        imageWidth: Int,
        imageHeight: Int
    ): List<QuestionRegion> {
        if (!ocrResult.success || ocrResult.textBlocks.isEmpty()) {
            Log.w(TAG, "OCR 结果为空或失败")
            return emptyList()
        }
        
        Log.d(TAG, "开始检测题目区域，文本块数量: ${ocrResult.textBlocks.size}")
        
        // Step 1: 收集所有带位置的文本行
        val allLines = collectAllLines(ocrResult.textBlocks)
        Log.d(TAG, "收集到 ${allLines.size} 行文本")
        
        if (allLines.isEmpty()) {
            Log.w(TAG, "没有文本行")
            return emptyList()
        }
        
        // Step 2: 找出题号行（整个系统的灵魂）
        val questionStartLines = findQuestionStartLines(allLines, imageWidth, imageHeight)
        Log.d(TAG, "检测到 ${questionStartLines.size} 个题号")
        
        if (questionStartLines.isEmpty()) {
            Log.w(TAG, "未检测到题号")
            return emptyList()
        }
        
        // Step 3: 根据题号分割区域（利用选项规律性）
        val regions = splitByQuestionNumbers(questionStartLines, allLines, imageWidth, imageHeight)
        Log.d(TAG, "生成 ${regions.size} 个题目区域")
        
        return regions
    }
    
    /**
     * Step 1: 收集所有文本行（保留坐标）
     */
    private fun collectAllLines(textBlocks: List<TextBlock>): List<LineInfo> {
        val lines = mutableListOf<LineInfo>()
        
        textBlocks.forEach { block ->
            block.lines.forEach { line ->
                lines.add(LineInfo(
                    text = line.text.trim(),
                    bounds = line.boundingBox
                ))
            }
        }
        
        // 按 Y 坐标排序
        return lines.sortedBy { it.bounds.top }
    }
    
    /**
     * Step 2: 题号识别（整个系统的灵魂）
     * 严格的正则：^\d{1,2}[\.、]$
     * 筛选条件（全部满足才算题号）：
     * 1. 行高 > 页面平均行高 × 0.8
     * 2. 位于页面左侧30%区域
     * 3. 上下空白明显（与上一行Y差距较大）
     */
    private fun findQuestionStartLines(
        allLines: List<LineInfo>,
        imageWidth: Int,
        imageHeight: Int
    ): List<LineInfo> {
        if (allLines.isEmpty()) {
            return emptyList()
        }
        
        // 计算页面平均行高
        val avgLineHeight = allLines.map { it.bounds.height() }.average().toFloat()
        Log.d(TAG, "页面平均行高: $avgLineHeight")
        
        // 计算左侧30%区域的X坐标
        val leftRegionX = imageWidth * LEFT_REGION_RATIO
        
        val questionLines = mutableListOf<LineInfo>()
        
        for (i in allLines.indices) {
            val line = allLines[i]
            val text = line.text.trim()
            
            // 1. 正则匹配：^\d{1,2}[\.、]$
            val match = QUESTION_NUMBER_PATTERN.find(text)
            if (match == null) {
                continue
            }
            
            val questionNumber = match.groupValues.getOrNull(1) ?: ""
            if (questionNumber.isEmpty()) {
                continue
            }
            
            // 2. 筛选条件1：行高 > 页面平均行高 × 0.6（放宽条件，避免漏掉真实题号）
            val lineHeight = line.bounds.height()
            val minLineHeight = avgLineHeight * LINE_HEIGHT_RATIO
            if (lineHeight < minLineHeight) {
                Log.d(TAG, "题号 $questionNumber 行高不足: $lineHeight < $minLineHeight (平均行高: $avgLineHeight)")
                // 如果行高不足，但满足其他条件（位置、正则），仍然认为是题号
                // 因为OCR识别时，行高可能不准确
                if (lineHeight < avgLineHeight * 0.5) {
                    // 只有行高小于平均行高的50%才过滤
                    Log.d(TAG, "题号 $questionNumber 行高过小，过滤: $lineHeight < ${avgLineHeight * 0.5}")
                    continue
                } else {
                    Log.d(TAG, "题号 $questionNumber 行高略小但仍在可接受范围，保留")
                }
            }
            
            // 3. 筛选条件2：位于页面左侧30%区域
            val lineLeft = line.bounds.left
            if (lineLeft > leftRegionX) {
                Log.d(TAG, "题号 $questionNumber 不在左侧30%区域: $lineLeft > $leftRegionX")
                continue
            }
            
            // 4. 筛选条件3：上下空白明显（与上一行Y差距较大）
            // 注意：OCR识别时，题号行可能和上一行有重叠或间距很小，这是正常的
            // 我们只检查：如果间距为正数且很大（> 平均行高的2倍），可能是题号
            // 如果间距很小或为负数，也可能是题号（OCR识别问题）
            // 所以这个条件不作为硬性过滤条件，只作为参考
            val prevLine = allLines.getOrNull(i - 1)
            if (prevLine != null) {
                val gapToPrev = line.bounds.top - prevLine.bounds.bottom
                if (gapToPrev < 0) {
                    Log.d(TAG, "题号 $questionNumber 与上一行有重叠: $gapToPrev (仍然认为是题号)")
                } else if (gapToPrev < avgLineHeight * 0.3) {
                    Log.d(TAG, "题号 $questionNumber 与上一行间距较小: $gapToPrev (仍然认为是题号)")
                } else {
                    Log.d(TAG, "题号 $questionNumber 与上一行间距足够: $gapToPrev")
                }
            }
            
            // 所有条件都满足，确认为题号
            questionLines.add(LineInfo(
                text = text,
                bounds = line.bounds,
                questionNumber = questionNumber
            ))
            Log.d(TAG, "✅ 检测到题号: $questionNumber, 文本: ${text.take(30)}, 行高: $lineHeight, 位置: (${line.bounds.left}, ${line.bounds.top})")
        }
        
        return questionLines
    }
    
    /**
     * Step 3: 题目区域生成（自动裁剪框）
     * 利用题目结构：题号 + 内容 + 整齐的ABCD选项
     * 改进：基于实际文本行的位置生成精确的裁剪框，避免将文字行切半
     * 裁剪规则：
     *   top    = 当前题号.top - marginTop
     *   bottom = 找到最后一个选项（通常是D）的底部，或下一题号.top - marginBottom
     *   left   = 题目范围内所有文本行的最左边界
     *   right  = 题目范围内所有文本行的最右边界
     */
    private fun splitByQuestionNumbers(
        questionLines: List<LineInfo>,
        allLines: List<LineInfo>,
        imageWidth: Int,
        imageHeight: Int
    ): List<QuestionRegion> {
        // 排序：按 boundingBox.top 从小到大排序
        val sortedQuestions = questionLines.sortedBy { it.bounds.top }
        
        val regions = mutableListOf<QuestionRegion>()
        
        for (i in sortedQuestions.indices) {
            val currentQuestion = sortedQuestions[i]
            val nextQuestion = sortedQuestions.getOrNull(i + 1)
            
            // 计算题目范围
            val questionTop = max(0, currentQuestion.bounds.top - MARGIN_TOP)
            val nextQuestionTop = nextQuestion?.bounds?.top
            
            // 优先使用最后一个选项的底部作为题目底部
            val lastOptionBottom = findLastOptionBottom(allLines, questionTop, nextQuestionTop)
            
            val questionBottom = when {
                // 如果找到了最后一个选项，使用选项底部 + 小边距
                lastOptionBottom != null -> {
                    min(
                        lastOptionBottom + 10,  // 选项底部 + 10px边距
                        nextQuestionTop?.minus(MARGIN_BOTTOM) ?: imageHeight
                    )
                }
                // 如果没有找到选项，使用下一题号位置
                nextQuestion != null -> {
                    max(questionTop + 50, nextQuestion.bounds.top - MARGIN_BOTTOM)
                }
                // 最后一题：使用图片底部
                else -> imageHeight
            }
            
            // 收集题目范围内的所有文本行
            // 改进：使用更宽松的条件，确保包含所有相关文本行
            val questionTextLines = allLines.filter { line ->
                // 文本行的任何部分在题目范围内，或者文本行的中心点在范围内
                val lineTop = line.bounds.top
                val lineBottom = line.bounds.bottom
                val lineCenterY = (lineTop + lineBottom) / 2
                
                // 如果文本行的顶部、底部或中心点在题目范围内，都包含
                (lineTop >= questionTop && lineTop < questionBottom) ||
                (lineBottom >= questionTop && lineBottom < questionBottom) ||
                (lineCenterY >= questionTop && lineCenterY < questionBottom) ||
                // 或者文本行跨越了题目范围
                (lineTop < questionTop && lineBottom > questionBottom)
            }
            
            // 基于实际文本行位置计算精确边界
            val (left, right, top, bottom) = if (questionTextLines.isNotEmpty()) {
                // 使用文本行的实际边界
                val minLeft = questionTextLines.minOfOrNull { it.bounds.left } ?: 0
                val maxRight = questionTextLines.maxOfOrNull { it.bounds.right } ?: imageWidth
                val minTop = questionTextLines.minOfOrNull { it.bounds.top } ?: questionTop
                val maxBottom = questionTextLines.maxOfOrNull { it.bounds.bottom } ?: questionBottom
                
                // 确保包含所有文本行：检查是否有文本行被遗漏
                // 如果计算出的bottom小于最后一个选项的底部，使用选项底部
                val actualBottom = if (lastOptionBottom != null && maxBottom < lastOptionBottom) {
                    lastOptionBottom + 10
                } else {
                    maxBottom + 5
                }
                
                // 添加边距，但不要超出图片边界
                val finalLeft = max(0, minLeft - 15)  // 左边距15px（增大边距，避免切掉文字）
                val finalRight = min(imageWidth, maxRight + 15)  // 右边距15px
                val finalTop = max(0, minTop - 10)  // 上边距10px（增大边距）
                val finalBottom = min(imageHeight, actualBottom)  // 下边距已在actualBottom中计算
                
                Log.d(TAG, "题目 ${currentQuestion.questionNumber} 文本行边界: left=$minLeft, right=$maxRight, top=$minTop, bottom=$maxBottom")
                Log.d(TAG, "  包含 ${questionTextLines.size} 行文本")
                Log.d(TAG, "  最终边界: left=$finalLeft, right=$finalRight, top=$finalTop, bottom=$finalBottom")
                
                Quadruple(finalLeft, finalRight, finalTop, finalBottom)
            } else {
                // 如果没有文本行，使用默认边界（但这种情况不应该发生）
                Log.w(TAG, "题目 ${currentQuestion.questionNumber} 没有找到文本行，使用默认边界")
                Quadruple(0, imageWidth, questionTop, questionBottom)
            }
            
            // 验证并调整裁剪框，确保包含所有文本行
            var finalBounds = Rect(left, top, right, bottom)
            finalBounds = ensureAllTextLinesIncluded(finalBounds, questionTextLines, imageWidth, imageHeight)
            
            // 验证区域有效性
            if (finalBounds.width() > 50 && finalBounds.height() > 30) {
                Log.d(TAG, "题目 ${currentQuestion.questionNumber} 区域: top=${finalBounds.top}, bottom=${finalBounds.bottom}, left=${finalBounds.left}, right=${finalBounds.right}, width=${finalBounds.width()}, height=${finalBounds.height()}")
                if (lastOptionBottom != null) {
                    Log.d(TAG, "  ✓ 使用选项底部定位: $lastOptionBottom")
                }
                if (questionTextLines.isNotEmpty()) {
                    Log.d(TAG, "  ✓ 基于 ${questionTextLines.size} 行文本的实际位置生成裁剪框")
                }
                regions.add(QuestionRegion(
                    bounds = finalBounds,
                    questionNumber = currentQuestion.questionNumber,
                    confidence = 0.95f
                ))
            } else {
                Log.w(TAG, "题目 ${currentQuestion.questionNumber} 区域无效: width=${finalBounds.width()}, height=${finalBounds.height()}")
            }
        }
        
        return regions
    }
    
    /**
     * 四元组数据类（用于返回4个值）
     */
    private data class Quadruple<A, B, C, D>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D
    )
    
    /**
     * 确保裁剪框包含所有文本行
     * 如果文本行的任何部分在裁剪框外，扩大裁剪框
     */
    private fun ensureAllTextLinesIncluded(
        bounds: Rect,
        textLines: List<LineInfo>,
        imageWidth: Int,
        imageHeight: Int
    ): Rect {
        if (textLines.isEmpty()) {
            return bounds
        }
        
        var minLeft = bounds.left
        var maxRight = bounds.right
        var minTop = bounds.top
        var maxBottom = bounds.bottom
        
        var adjusted = false
        
        for (line in textLines) {
            val lineBox = line.bounds
            
            // 检查文本行是否完全在裁剪框内
            // 如果文本行的任何部分在框外，扩大裁剪框
            if (lineBox.left < minLeft) {
                minLeft = max(0, lineBox.left - 20)  // 左边距20px
                adjusted = true
            }
            if (lineBox.right > maxRight) {
                maxRight = min(imageWidth, lineBox.right + 20)  // 右边距20px
                adjusted = true
            }
            if (lineBox.top < minTop) {
                minTop = max(0, lineBox.top - 15)  // 上边距15px
                adjusted = true
            }
            if (lineBox.bottom > maxBottom) {
                maxBottom = min(imageHeight, lineBox.bottom + 15)  // 下边距15px
                adjusted = true
            }
        }
        
        if (adjusted) {
            Log.d(TAG, "  调整裁剪框以包含所有文本行: 原边界=$bounds, 新边界=($minLeft, $minTop, $maxRight, $maxBottom)")
        }
        
        return Rect(minLeft, minTop, maxRight, maxBottom)
    }
    
    /**
     * 查找题目中的最后一个选项（D选项）位置
     * 用于精确定位题目底部边界
     */
    private fun findLastOptionBottom(
        allLines: List<LineInfo>,
        questionTop: Int,
        nextQuestionTop: Int?
    ): Int? {
        // 选项正则：A. B. C. D. 或 A B C D 开头（支持多种格式）
        val optionPattern = Regex("^[A-D][.、)）]|^[A-D]\\s+")
        
        // 在题目范围内查找选项（从题号开始到下一题号之前）
        val questionRange = allLines.filter { line ->
            line.bounds.top >= questionTop && 
            (nextQuestionTop == null || line.bounds.top < nextQuestionTop)
        }
        
        // 查找所有选项行
        val optionLines = questionRange.filter { line ->
            val text = line.text.trim()
            optionPattern.containsMatchIn(text)
        }
        
        if (optionLines.isEmpty()) {
            return null
        }
        
        // 找到最后一个选项（通常是D，按bottom坐标排序）
        val lastOption = optionLines.maxByOrNull { it.bounds.bottom }
        
        Log.d(TAG, "找到 ${optionLines.size} 个选项行，最后一个选项底部: ${lastOption?.bounds?.bottom}")
        
        return lastOption?.bounds?.bottom
    }
    
    /**
     * 文本行信息
     */
    private data class LineInfo(
        val text: String,
        val bounds: Rect,
        val questionNumber: String? = null
    )
}
