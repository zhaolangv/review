package com.gongkao.cuotifupan.detector

import android.graphics.Rect
import com.gongkao.cuotifupan.ocr.OcrResult
import com.gongkao.cuotifupan.ocr.TextBlock
import com.gongkao.cuotifupan.ocr.TextLine
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 特征提取器
 */
object FeatureExtractor {
    
    /**
     * 提取所有特征
     */
    fun extractFeatures(ocrResult: OcrResult): QuestionFeatures {
        val text = ocrResult.rawText
        val lines = ocrResult.lines
        val textBlocks = ocrResult.textBlocks
        
        return QuestionFeatures(
            // 基础统计
            numTextBlocks = textBlocks.size,
            numLines = lines.size,
            avgLineLength = if (lines.isNotEmpty()) lines.map { it.length }.average() else 0.0,
            shortLineRatio = calculateShortLineRatio(lines),
            
            // 选项相关
            optionMarkerCount = countOptionMarkers(text, textBlocks),
            bboxVerticalAlignmentScore = calculateVerticalAlignmentScore(textBlocks),
            
            // OCR质量
            ocrConfidenceMean = 0.85, // ML Kit不提供置信度，使用默认值
            
            // 内容特征
            emojiOrPunctRatio = calculateEmojiPunctRatio(text),
            hasQuestionKeywords = hasQuestionKeywords(text) || hasQuestionTypeMarker(text) || hasQuestionNumber(text),
            hasMathSymbols = hasMathSymbols(text),
            
            // 布局特征
            topHalfLongLineRatio = calculateTopHalfLongLineRatio(textBlocks),
            bottomHalfShortLines = countBottomHalfShortLines(textBlocks),
            
            // 新增布局/视觉特征
            optionLeftAlignmentScore = calculateOptionLeftAlignmentScore(textBlocks, lines),
            optionSpacingConsistency = calculateOptionSpacingConsistency(textBlocks),
            blockSizeConsistency = calculateBlockSizeConsistency(textBlocks),
            questionOptionSeparation = calculateQuestionOptionSeparation(textBlocks),
            
            // 核心改进：选项结构识别（短行簇检测）
            shortLineClusterCount = detectShortLineClusters(textBlocks),
            shortLineClusterAlignmentScore = calculateShortLineClusterAlignment(textBlocks),
            shortLineClusterSpacingConsistency = calculateShortLineClusterSpacing(textBlocks),
            
            // 特殊场景
            hasAlternativeMarkers = hasAlternativeMarkers(text),
            hasSeparatorMarkers = hasSeparatorMarkers(text)
        )
    }
    
    /**
     * 计算短行比例（长度<10的行）
     */
    private fun calculateShortLineRatio(lines: List<String>): Double {
        if (lines.isEmpty()) return 0.0
        val shortCount = lines.count { it.trim().length < 10 }
        return shortCount.toDouble() / lines.size
    }
    
    /**
     * 统计选项标记数量（A. B. C. D. 等）
     * 改进：支持更多格式，包括行首和行中的选项标记
     * 增强：对部分遮挡更鲁棒，即使选项标记不完整也能识别
     */
    private fun countOptionMarkers(text: String, textBlocks: List<TextBlock> = emptyList()): Int {
        val seenOptions = mutableSetOf<Char>()
        val lines = text.split("\n")
        
        android.util.Log.d("FeatureExtractor", "开始检测选项标记，总行数: ${lines.size}")
        
        for ((lineIndex, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            // 检测行首的选项标记（多种格式，更宽松，对部分遮挡更鲁棒）
            val optionMatch = when {
                // 单独一行只有A-D字母（OCR可能将选项字母和内容分离，或部分遮挡）
                trimmed.matches(Regex("^[A-D]$")) -> {
                    android.util.Log.d("FeatureExtractor", "  行$lineIndex: 匹配格式0 (单独字母): $trimmed")
                    trimmed.first()
                }
                // A. B. C. D. 格式（包括中文句号、逗号）
                trimmed.matches(Regex("^[A-D][.。、,)].*")) -> {
                    android.util.Log.d("FeatureExtractor", "  行$lineIndex: 匹配格式1 (A.或A,): $trimmed")
                    trimmed.first()
                }
                // A: 格式（A后面跟冒号，但后面不能是数字，避免误识别时间如"8:34"）
                trimmed.matches(Regex("^[A-D]:[^0-9].*")) -> {
                    android.util.Log.d("FeatureExtractor", "  行$lineIndex: 匹配格式1.5 (A:): $trimmed")
                    trimmed.first()
                }
                // A 开头后跟空格和中文字符（更严格，避免误识别"一等"、"商务"等）
                trimmed.matches(Regex("^[A-D]\\s+[\\u4e00-\\u9fa5].*")) -> {
                    android.util.Log.d("FeatureExtractor", "  行$lineIndex: 匹配格式2 (A 中文): $trimmed")
                    trimmed.first()
                }
                // A直接跟中文字符（没有空格或标点，如"A推脫"、"B推脫"等）
                trimmed.matches(Regex("^[A-D][\\u4e00-\\u9fa5].*")) -> {
                    android.util.Log.d("FeatureExtractor", "  行$lineIndex: 匹配格式2.5 (A中文，无空格): $trimmed")
                    trimmed.first()
                }
                // A) 或 A）格式
                trimmed.matches(Regex("^[A-D][)）].*")) -> {
                    android.util.Log.d("FeatureExtractor", "  行$lineIndex: 匹配格式3 (A)): $trimmed")
                    trimmed.first()
                }
                // (A) 或 （A）格式
                trimmed.matches(Regex("^[（(][A-D][）)].*")) -> {
                    android.util.Log.d("FeatureExtractor", "  行$lineIndex: 匹配格式4 ((A)): $trimmed")
                    trimmed.getOrNull(1) ?: trimmed.getOrNull(2)
                }
                // 增强：部分遮挡的情况 - 即使选项标记被遮挡，选项内容仍然可见
                // 检测行首是常见选项内容开头（如"行之有效"、"环环相扣"等）
                // 如果连续多行都是短行且左对齐，可能是选项（即使标记被遮挡）
                else -> {
                    // 在行首前15个字符内查找，放宽范围
                    val prefix = trimmed.take(15)
                    // 更宽松的匹配：A/B/C/D后面跟点、括号或空格
                    // 也匹配 "选项A"、"答案A" 等格式
                    // 注意：不匹配冒号，避免误识别时间格式（如"8:34"）
                    val match = Regex("(?:^|\\s)([A-D])[.。、)\\s]").find(prefix) ?:
                                Regex("^([A-D])\\s").find(prefix) ?:  // 也匹配 "A " 开头
                                Regex("(?:选项|答案|选择)[：:]*\\s*([A-D])[.。、)\\s]").find(prefix)  // 选项A、答案B等
                    if (match != null) {
                        android.util.Log.d("FeatureExtractor", "  行$lineIndex: 匹配格式5 (行首附近): $trimmed")
                    }
                    match?.groupValues?.get(1)?.firstOrNull()
                }
            }
            
            if (optionMatch != null && optionMatch in 'A'..'D') {
                if (optionMatch !in seenOptions) {
                    seenOptions.add(optionMatch)
                    android.util.Log.d("FeatureExtractor", "  ✓ 找到选项: $optionMatch")
                }
            }
        }
        
        // 特殊处理1：如果检测到1个选项标记（A），且后面有多个"?"行，推断这些"?"可能是其他选项标记（B、C、D）
        // 这种情况通常发生在OCR识别质量差时，选项标记被识别为"?"
        if (seenOptions.size == 1 && lines.size >= 3) {
            val firstOptionIndex = lines.indexOfFirst { line ->
                val trimmed = line.trim()
                trimmed.matches(Regex("^[A-D]$")) || 
                trimmed.matches(Regex("^[A-D][.。、,)].*")) ||
                trimmed.matches(Regex("^[A-D]\\s+.*"))
            }
            
            if (firstOptionIndex >= 0 && firstOptionIndex < lines.size - 1) {
                // 检查后面的行是否都是"?"（包括单独的"?"或连续的"???"）
                val remainingLines = lines.subList(firstOptionIndex + 1, lines.size)
                val questionMarkLines = remainingLines
                    .map { it.trim() }
                    .filter { it == "?" || it.matches(Regex("^\\?+$")) || it.matches(Regex("^\\?\\s*$")) }
                
                // 如果后面有2-3个"?"行，且这些行是连续的，推断这些可能是B、C、D选项
                // 检查"?"行是否连续（中间没有其他内容）
                val isConsecutive = questionMarkLines.size >= 2 && 
                    remainingLines.take(questionMarkLines.size).all { line ->
                        val trimmed = line.trim()
                        trimmed == "?" || trimmed.matches(Regex("^\\?+$")) || trimmed.matches(Regex("^\\?\\s*$"))
                    }
                
                if (isConsecutive && questionMarkLines.size >= 2 && questionMarkLines.size <= 3) {
                    val inferredOptions = listOf('B', 'C', 'D').take(questionMarkLines.size)
                    inferredOptions.forEach { option ->
                        if (option !in seenOptions) {
                            seenOptions.add(option)
                            android.util.Log.d("FeatureExtractor", "  ✓ 推断选项（从?行）: $option (共${questionMarkLines.size}个连续的?行)")
                        }
                    }
                }
            }
        }
        
        // 特殊处理2：如果检测到1个选项标记（如B），且后面有多个短行（可能是选项内容），推断可能有多个选项
        // 这种情况通常发生在选项标记和选项内容分离，或选项标记（A、C、D）被OCR遗漏时
        if (seenOptions.size == 1 && lines.size >= 4) {
            android.util.Log.d("FeatureExtractor", "  开始特殊处理2：检测到1个选项标记，尝试推断其他选项")
            val firstOptionIndex = lines.indexOfFirst { line ->
                val trimmed = line.trim()
                trimmed.matches(Regex("^[A-D]$")) || 
                trimmed.matches(Regex("^[A-D][.。、,)].*")) ||
                trimmed.matches(Regex("^[A-D]\\s+.*"))
            }
            
            android.util.Log.d("FeatureExtractor", "  第一个选项标记位置: $firstOptionIndex")
            
            if (firstOptionIndex >= 0 && firstOptionIndex < lines.size - 2) {
                val remainingLines = lines.subList(firstOptionIndex + 1, lines.size)
                android.util.Log.d("FeatureExtractor", "  检查后续 ${remainingLines.size} 行，寻找候选选项内容")
                
                // 查找可能的选项内容行：短行（5-40字符），主要是中文，不是空行
                // 放宽字符数限制，因为有些选项内容可能较长
                val candidateOptionLines = remainingLines
                    .mapIndexed { index, line -> index to line.trim() }
                    .filter { (lineIndex, trimmed) ->
                        val isEmpty = trimmed.isEmpty()
                        val lengthOk = trimmed.length in 5..40
                        val chineseRatio = trimmed.count { it in '\u4e00'..'\u9fa5' } * 2.5 > trimmed.length
                        // UI元素关键词过滤：只过滤整行都是UI元素的情况，不过滤包含UI关键词的选项内容
                        val isPureUI = trimmed.matches(Regex("^(关注|点赞|收藏|分享|评论|回复|删除|编辑|转发|商城|推荐|直播|团购|首页|消息|我|正确率|答案|解析|展开|周搜|华图|公考|行测|申论|国考|朋友|祝各位|三验|精选).*$"))
                        val notUI = !isPureUI
                        val notTime = !trimmed.matches(Regex("^\\d{1,2}:\\d{2}$"))
                        val notSymbols = !trimmed.matches(Regex("^[\\d\\s\\W]+$"))
                        
                        val isCandidate = !isEmpty && lengthOk && chineseRatio && notUI && notTime && notSymbols
                        if (!isCandidate && trimmed.isNotEmpty() && trimmed.length in 3..50) {
                            android.util.Log.d("FeatureExtractor", "    行${firstOptionIndex + 1 + lineIndex}被过滤: '$trimmed' (空=$isEmpty, 长度=$lengthOk, 中文=$chineseRatio, 非UI=$notUI, 非时间=$notTime, 非符号=$notSymbols)")
                        }
                        isCandidate
                    }
                    .take(6) // 最多检查6行
                
                android.util.Log.d("FeatureExtractor", "  找到 ${candidateOptionLines.size} 个候选选项行: ${candidateOptionLines.map { "${it.first}:${it.second.take(15)}" }.joinToString(", ")}")
                
                // 如果找到2-4个候选选项行，且它们相对连续（中间最多间隔2行，允许空行），推断可能有多个选项
                if (candidateOptionLines.size >= 2 && candidateOptionLines.size <= 4) {
                    val indices = candidateOptionLines.map { it.first }
                    val isConsecutive = indices.zipWithNext().all { (a, b) -> b - a <= 3 } // 允许间隔2行（包括空行）
                    
                    android.util.Log.d("FeatureExtractor", "  候选行索引: $indices, 是否连续: $isConsecutive")
                    
                    if (isConsecutive) {
                        // 推断选项数量：如果检测到B，且后面有2-3个候选选项行，推断可能有A、C、D
                        val detectedOption = seenOptions.first()
                        val inferredCount = candidateOptionLines.size
                        
                        // 根据检测到的选项和候选行数量，推断其他选项
                        val allOptions = listOf('A', 'B', 'C', 'D')
                        val inferredOptions = when (detectedOption) {
                            'A' -> allOptions.filter { it != 'A' }.take(inferredCount)
                            'B' -> listOf('A') + allOptions.filter { it !in listOf('A', 'B') }.take(inferredCount - 1)
                            'C' -> allOptions.filter { it != 'C' }.take(inferredCount)
                            'D' -> allOptions.filter { it != 'D' }.take(inferredCount)
                            else -> emptyList()
                        }
                        
                        android.util.Log.d("FeatureExtractor", "  推断选项: $inferredOptions (检测到${detectedOption}，候选行${inferredCount}个)")
                        
                        inferredOptions.forEach { option ->
                            if (option !in seenOptions) {
                                seenOptions.add(option)
                                android.util.Log.d("FeatureExtractor", "  ✓ 推断选项（从候选内容行）: $option (检测到${detectedOption}，后面有${inferredCount}个候选选项行: ${candidateOptionLines.map { it.second.take(20) }.joinToString(", ")})")
                            }
                        }
                    } else {
                        android.util.Log.d("FeatureExtractor", "  候选行不连续，跳过推断")
                    }
                } else {
                    android.util.Log.d("FeatureExtractor", "  候选选项行数量不符合要求 (${candidateOptionLines.size}，需要2-4个)")
                }
            } else {
                android.util.Log.d("FeatureExtractor", "  第一个选项标记位置无效或太靠后")
            }
        }
        
        // 也统计数字选项标记（①②③④⑤⑥等）
        // 改进：统计唯一的数字选项，而不是所有出现次数
        val numberOptionSet = mutableSetOf<String>()
        val numberPatterns = listOf(
            Regex("[①②③④⑤⑥⑦⑧⑨⑩]"),  // 扩展到⑩
            Regex("\\([①②③④⑤⑥⑦⑧⑨⑩]\\)"),
            Regex("[（][①②③④⑤⑥⑦⑧⑨⑩][）]")
        )
        
        // 方法1：在行首查找数字选项（避免误识别UI元素）
        // 注意：不再直接查找所有数字选项，而是只在行首查找，避免误识别UI元素
        // 这部分逻辑已经在下面的"方法2"中处理了
        
        // 方法2：在行首查找数字选项（更准确，避免误识别）
        for ((lineIndex, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            // 检查行首是否有数字选项（①②③等）
            // 只使用正面匹配：选项标记后面可以跟选项内容，也可以只有标点符号（如"⑥)"）
            // 但需要排除UI元素（如"不喜欢"、"点赞"、"收藏"、"分享"、"关注"、"评论"等）
            val numberMatch = Regex("^[①②③④⑤⑥⑦⑧⑨⑩]").find(trimmed)
            if (numberMatch != null) {
                val afterNumber = trimmed.substring(numberMatch.value.length).trim()
                
                // 排除UI元素关键词（这些不是选项内容）
                val uiKeywords = listOf("不喜欢", "点赞", "收藏", "分享", "关注", "评论", "回复", "删除", "编辑", "转发")
                val isUIElement = uiKeywords.any { afterNumber.startsWith(it) }
                
                if (isUIElement) {
                    android.util.Log.d("FeatureExtractor", "  行$lineIndex 行首找到数字但后面是UI元素，跳过: $trimmed")
                } else {
                    // 正面匹配：后面可以跟选项内容（中文、英文、数字），也可以只有标点符号（如")"、"."等）
                    // 如果后面只有标点符号，也认为是选项标记（OCR可能只识别了标记本身）
                    val isValidOption = when {
                        afterNumber.isEmpty() -> true  // 只有标记本身，也认为是选项
                        afterNumber.matches(Regex("^[)）\\.。、：；\\s]*$")) -> true  // 只有标点符号
                        afterNumber.matches(Regex("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s，。、：；].*")) -> true  // 有选项内容
                        else -> false
                    }
                    
                    if (isValidOption) {
                        numberOptionSet.add(numberMatch.value)
                        android.util.Log.d("FeatureExtractor", "  行$lineIndex 行首找到数字选项: ${numberMatch.value} in $trimmed")
                    } else {
                        android.util.Log.d("FeatureExtractor", "  行$lineIndex 行首找到数字但后面不是选项内容，跳过: $trimmed")
                    }
                }
            }
            
            // 检查行首是否有带括号的数字选项
            val parenNumberMatch = Regex("^[（(][①②③④⑤⑥⑦⑧⑨⑩][）)]").find(trimmed)
            if (parenNumberMatch != null) {
                val number = parenNumberMatch.value.replace(Regex("[（()）]"), "")
                if (number.isNotEmpty()) {
                    numberOptionSet.add(number)
                    android.util.Log.d("FeatureExtractor", "  行$lineIndex 行首找到带括号数字选项: $number in $trimmed")
                }
            }
            
            // 检查行中是否有数字选项（在"选项"、"答案"等词后面）
            val optionNumberMatch = Regex("(?:选项|答案|选择)[：:]*[（(]*([①②③④⑤⑥⑦⑧⑨⑩])").find(trimmed)
            if (optionNumberMatch != null) {
                val number = optionNumberMatch.groupValues[1]
                numberOptionSet.add(number)
                android.util.Log.d("FeatureExtractor", "  行$lineIndex 行中找到选项数字: $number in $trimmed")
            }
        }
        
        val numberCount = numberOptionSet.size
        var totalCount = seenOptions.size + numberCount
        
        // 如果没检测到选项标记，尝试检测无标记的选项（基于布局特征）
        if (totalCount == 0) {
            val unmarkedOptions = detectUnmarkedOptions(lines, textBlocks)
            if (unmarkedOptions > 0) {
                android.util.Log.d("FeatureExtractor", "检测到无标记选项: ${unmarkedOptions}个")
                totalCount = unmarkedOptions
            }
        }
        
        val unmarkedCount = if (totalCount > seenOptions.size + numberCount) totalCount - seenOptions.size - numberCount else 0
        android.util.Log.d("FeatureExtractor", "选项标记检测完成: 字母选项${seenOptions.size}个(${seenOptions.sorted().joinToString()}), 数字选项${numberCount}个(${numberOptionSet.sorted().joinToString()}), 无标记选项${unmarkedCount}个, 总计${totalCount}个")
        
        // 返回唯一选项数量（A-D）加上唯一数字选项数量加上无标记选项数量
        return totalCount
    }
    
    /**
     * 检测无标记的选项（选项标记被遮挡或OCR未识别）
     * 规则：基于布局特征检测（对齐、间距、位置），而不是内容关键词
     * 要求：
     * 1. 有非常明确的题目关键词（如"填入"、"最恰当的一项是"等）
     * 2. 关键词之后有3-4行短行（长度相似，主要是中文）
     * 3. 这些短行必须满足布局特征（对齐、间距一致性）
     */
    private fun detectUnmarkedOptions(lines: List<String>, textBlocks: List<TextBlock>): Int {
        // 1. 查找非常明确的题目关键词的位置
        var keywordIndex = -1
        val veryStrongKeywords = listOf(
            "填入", "填入划横线", "填入横线", "填入画横线",
            // OCR错误变体
            "镇入", "镇入画横线", "镇入画橫线", "镇入横线",  // "填入"可能识别为"镇入"
            "画橫线", "画横线", "橫线",  // "横线"可能识别为"橫线"
            "最恰当的一项是", "最恰当的是", "最合适的一项是", "最合适的是",
            // OCR错误变体
            "最怡当的一项是", "最怡当的是", "最怡当",  // "恰当"可能识别为"怡当"
            "一顶是", "一顶",  // "一项"可能识别为"一顶"
            "可以推出", "推出", "可以得出", "得出",
            "正确的一项是", "正确的是", "错误的是", "不正确的是",
            "分类", "分为", "分成",
            // 组合关键词（支持OCR错误）
            "依次填入", "依次镇入",  // "依次填入"可能识别为"依次镇入"
            "填入画横线部分", "镇入画橫线部分",  // 完整短语
            "最恰当的一项是", "最怡当的一顶是"  // 完整短语（OCR错误）
        )
        
        for ((index, line) in lines.withIndex()) {
            if (veryStrongKeywords.any { line.contains(it) }) {
                keywordIndex = index
                break
            }
        }
        
        // 如果没找到非常明确的关键词，返回0（避免误识别）
        if (keywordIndex < 0 || keywordIndex >= lines.size - 2) {
            return 0
        }
        
        // 2. 在关键词之后查找连续短行（可能是选项）
        val candidateOptions = mutableListOf<Pair<String, Int>>()  // (文本, 行索引)
        var consecutiveShortLines = 0
        
        for (i in (keywordIndex + 1) until lines.size) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty()) continue
            
            // 选项行：长度在4-50个字符之间，且主要是中文
            // 只做基本的格式检查，不排除内容关键词
            val isOptionLine = trimmed.length in 4..50 && 
                             trimmed.matches(Regex("^[\\u4e00-\\u9fa5\\s，。、：；0-9%]+$")) &&
                             !trimmed.matches(Regex("^\\d+/\\d+$")) &&  // 排除页码格式
                             !trimmed.matches(Regex("^\\d+年.*考试.*题.*$")) &&  // 排除标题格式
                             !trimmed.contains("B/s") &&  // 排除网络速度格式
                             !trimmed.matches(Regex("^\\d+:\\d+.*$")) &&  // 排除时间格式
                             !trimmed.matches(Regex("^[A-Z]$"))  // 排除单个字母
            
            if (isOptionLine) {
                candidateOptions.add(Pair(trimmed, i))
                consecutiveShortLines++
            } else {
                // 如果遇到非选项行，且已经有3个以上候选选项，停止
                if (consecutiveShortLines >= 3) {
                    break
                }
                // 否则重置计数
                consecutiveShortLines = 0
                candidateOptions.clear()
            }
        }
        
        // 3. 检查候选选项的长度是否相似
        if (candidateOptions.size < 3) {
            return 0
        }
        
        val lengths = candidateOptions.map { it.first.length }
        val avgLength = lengths.average()
        val variance = lengths.map { (it - avgLength) * (it - avgLength) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        // 长度相似性检查：标准差小于平均值的30%
        if (avgLength <= 0 || stdDev / avgLength >= 0.3) {
            return 0
        }
        
        // 4. 基于布局特征验证（如果有位置信息）
        if (textBlocks.isNotEmpty()) {
            // 找到候选选项对应的文本块
            val candidateBlocks = candidateOptions.mapNotNull { (text, lineIndex) ->
                // 尝试在textBlocks中找到对应的行
                textBlocks.flatMap { it.lines }.find { it.text.trim() == text }
            }
            
            if (candidateBlocks.size >= 3) {
                // 检查X坐标对齐
                val xPositions = candidateBlocks.map { it.boundingBox.left }
                val avgX = xPositions.average()
                val maxDeviation = xPositions.map { kotlin.math.abs(it - avgX) }.maxOrNull() ?: 0.0
                val alignmentScore = if (avgX > 0) {
                    kotlin.math.max(0.0, 1.0 - (maxDeviation / avgX) / 0.4)
                } else {
                    0.0
                }
                
                // 检查垂直间距一致性
                val sortedBlocks = candidateBlocks.sortedBy { it.boundingBox.top }
                val spacings = mutableListOf<Int>()
                for (i in 0 until sortedBlocks.size - 1) {
                    val gap = sortedBlocks[i + 1].boundingBox.top - sortedBlocks[i].boundingBox.bottom
                    spacings.add(gap)
                }
                
                val avgSpacing = spacings.average()
                val spacingVariance = spacings.map { (it - avgSpacing) * (it - avgSpacing) }.average()
                val spacingStdDev = kotlin.math.sqrt(spacingVariance)
                val spacingConsistency = if (avgSpacing > 0) {
                    kotlin.math.max(0.0, 1.0 - (spacingStdDev / avgSpacing) / 0.3)
                } else {
                    0.0
                }
                
                // 布局特征验证：对齐分数和间距一致性都需要达到一定阈值
                val layoutScore = (alignmentScore * 0.6 + spacingConsistency * 0.4)
                if (layoutScore >= 0.5) {
                    android.util.Log.d("FeatureExtractor", "检测到无标记选项: ${candidateOptions.size}个, 平均长度=${String.format("%.1f", avgLength)}, 对齐分数=${String.format("%.2f", alignmentScore)}, 间距一致性=${String.format("%.2f", spacingConsistency)}, 布局分数=${String.format("%.2f", layoutScore)}")
                    return candidateOptions.size.coerceAtMost(4)  // 最多4个选项
                } else {
                    android.util.Log.d("FeatureExtractor", "候选选项布局验证失败: 对齐分数=${String.format("%.2f", alignmentScore)}, 间距一致性=${String.format("%.2f", spacingConsistency)}, 布局分数=${String.format("%.2f", layoutScore)}")
                }
            }
        }
        
        // 如果没有位置信息，只基于长度相似性（降低要求，需要更严格的关键词）
        // 这种情况下，要求关键词必须是"填入"、"最恰当的一项是"等非常明确的
        val hasVeryStrongKeyword = veryStrongKeywords.any { keyword ->
            lines.any { it.contains(keyword) }
        }
        if (hasVeryStrongKeyword) {
            android.util.Log.d("FeatureExtractor", "检测到无标记选项（无位置信息，仅基于长度相似性）: ${candidateOptions.size}个, 平均长度=${String.format("%.1f", avgLength)}")
            return candidateOptions.size.coerceAtMost(4)
        }
        
        return 0
    }
    
    /**
     * 计算垂直对齐分数（0-1）
     */
    private fun calculateVerticalAlignmentScore(textBlocks: List<TextBlock>): Double {
        if (textBlocks.size < 2) return 0.0
        
        // 获取所有行的X坐标
        val xPositions = textBlocks.flatMap { it.lines.map { line -> line.boundingBox.left } }
        
        if (xPositions.isEmpty()) return 0.0
        
        val avgX = xPositions.average()
        val maxDeviation = xPositions.map { abs(it - avgX) }.maxOrNull() ?: 0.0
        
        // 如果最大偏差小于平均值的30%，认为对齐良好
        return if (avgX > 0) {
            max(0.0, 1.0 - (maxDeviation / avgX) / 0.3)
        } else {
            0.0
        }
    }
    
    /**
     * 计算表情符号和标点符号比例
     */
    private fun calculateEmojiPunctRatio(text: String): Double {
        if (text.isEmpty()) return 0.0
        val punctCount = text.count { it in "，。！？、；：\"\"''（）【】《》…—" }
        return punctCount.toDouble() / text.length
    }
    
    /**
     * 是否包含题目关键词（包括题型标记）
     * 注意：移除过于宽泛的关键词，避免误识别
     */
    private fun hasQuestionKeywords(text: String): Boolean {
        // 只保留非常明确的题目关键词，移除所有可能出现在非题目场景的词
        // 这些关键词必须是非常明确的题目特征，不会在普通文本中出现
        val keywords = listOf(
            // 非常明确的题目格式
            "正确的一项是", "正确的是", "错误的是", "不正确的是",
            "下列", "以下", 
            // 分类相关（非常明确）
            "分类", "分为", "分成", "正确的一项是",
            // 选择相关（但需要配合其他特征）
            "选出", "选出的是",
            // 填入相关（填空题、选词填空题等）
            "依次填入", "填入", "填入划横线", "填入横线", "划横线",
            "最恰当的一项是", "最恰当的是", "最合适的一项是", "最合适的是",
            "填入画横线", "填入画线", "填入下划线",
            // 支持OCR识别错误的情况（"镇入"→"填入"，"怡当"→"恰当"）
            "画横线", "画線", "画线", "横线", "橫线", "下划线",
            "最恰当", "最合适", "的一项是", "的是",
            // 支持OCR识别错误："镇入"可能识别为"填入"
            "镇入", "填入画", "填入横",
            // 注意：移除"依次"、"的是"等过于宽泛的词，避免误识别
            // "依次"可能在聊天记录中出现（如"依次发送"）
            // "的是"可能在各种文本中出现
            // 推理题相关
            "可以推出", "推出", "可以得出", "得出", "可以推断", "推断",
            "可能的结果", "可能的结果是", "结果是", "结果是:"
        )
        
        // 移除过于宽泛的词，这些词在非题目场景也常见：
        // "依据", "属于", "应该", "不属于", "符合", "不符合", "表述", "判断",
        // "关于", "描述", "定义", "特点", "作用", "影响", "原因"
        // "哪个", "什么", "如何", "怎样", "为什么" - 这些问句在社交媒体、聊天等场景也常见
        // 完全移除这些宽泛的问句关键词，只保留非常明确的题目格式关键词
        
        return keywords.any { text.contains(it) }
    }
    
    /**
     * 是否有明确的题型标记（"单选题"等）
     */
    fun hasQuestionTypeMarker(text: String): Boolean {
        val questionTypeKeywords = listOf("单选题", "多选题", "判断题", "选择题", "填空题", "问答题")
        return questionTypeKeywords.any { text.contains(it) }
    }
    
    /**
     * 检测题目序号/编号（如"第1题"、"题目1"、"1."等）
     * 这是题目的强信号
     * 只使用正面特征匹配，不依赖排除关键词
     */
    fun hasQuestionNumber(text: String): Boolean {
        val lines = text.split("\n")
        
        // 行首匹配模式（需要^锚点，更严格）
        val lineStartPatterns = listOf(
            // 1. 明确的题目序号格式（行首）
            Regex("^\\s*\\d+[.。、]\\s*[\\u4e00-\\u9fa5]"),  // 1. 后面跟中文（题目内容）
            Regex("^\\s*\\d+[、]\\s*[\\u4e00-\\u9fa5]"),     // 1、后面跟中文
            Regex("^\\s*\\d+\\s*题"),                      // 1 题 (行首，数字+空格+题)
            Regex("^\\s*题\\s*\\d+"),                      // 题 1 (行首，题+空格+数字)
            Regex("^\\s*第\\s*\\d+"),                      // 第 1 (行首)
            Regex("^\\s*题目\\s*\\d+"),                    // 题目 1 (行首)
            Regex("^\\s*[（(]\\s*\\d+\\s*[）)]\\s*[\\u4e00-\\u9fa5]"), // （1） 后面跟中文
            Regex("^\\s*[（(]\\s*\\d+\\s*题\\s*[）)]")     // （1题） (行首)
        )
        
        // 全文匹配模式（必须包含"题"字或明确的题目序号关键词）
        val fullTextPatterns = listOf(
            Regex("第\\s*\\d+\\s*题"),           // 第1题, 第 2 题
            Regex("题目\\s*\\d+"),               // 题目1, 题目 2
            Regex("题\\s*\\d+"),                // 题 1, 题 2
            Regex("第\\d+题"),                   // 第1题 (紧凑格式)
            Regex("题目\\d+"),                   // 题目1 (紧凑格式)
            Regex("题\\d+题"),                   // 题1题 (题+数字+题)
            Regex("题\\s*\\d+\\s*[：:]"),        // 题 1： (题+数字+冒号)
            Regex("[（(]\\s*\\d+\\s*题\\s*[）)]") // （1题） (带括号和题字)
        )
        
        android.util.Log.d("FeatureExtractor", "开始检测题目序号，总行数: ${lines.size}")
        
        // 检查前15行（题目序号通常在顶部）
        for (i in 0 until minOf(15, lines.size)) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty()) continue
            
            // 检查行首模式
            for (pattern in lineStartPatterns) {
                if (pattern.find(trimmed) != null) {
                    android.util.Log.d("FeatureExtractor", "检测到题目序号（行首模式）: 行$i = '$trimmed', 模式: ${pattern.pattern}")
                    return true
                }
            }
            
            // 检查全文模式（在行中查找）
            for (pattern in fullTextPatterns) {
                if (pattern.containsMatchIn(trimmed)) {
                    android.util.Log.d("FeatureExtractor", "检测到题目序号（全文模式）: 行$i = '$trimmed', 模式: ${pattern.pattern}")
                    return true
                }
            }
        }
        
        // 也检查整个文本（有些序号可能在中间，使用全文模式）
        for (pattern in fullTextPatterns) {
            if (pattern.containsMatchIn(text)) {
                android.util.Log.d("FeatureExtractor", "检测到题目序号（在文本中，全文模式）: ${pattern.pattern}")
                return true
            }
        }
        
        android.util.Log.d("FeatureExtractor", "未检测到题目序号")
        return false
    }
    
    /**
     * 是否包含数学符号
     */
    private fun hasMathSymbols(text: String): Boolean {
        val mathSymbols = "=≠<>≤≥±×÷∑∏∫√∞∠°%"
        return text.any { it in mathSymbols }
    }
    
    /**
     * 计算上半部分长行比例
     */
    private fun calculateTopHalfLongLineRatio(textBlocks: List<TextBlock>): Double {
        if (textBlocks.isEmpty()) return 0.0
        
        // 计算图片总高度
        val maxY = textBlocks.map { it.boundingBox.bottom }.maxOrNull() ?: 0
        val midY = maxY / 2
        
        // 上半部分的长行（长度>20）
        val topHalfLines = textBlocks
            .filter { it.boundingBox.top < midY }
            .flatMap { it.lines }
            .filter { it.text.length > 20 }
        
        val allTopHalfLines = textBlocks
            .filter { it.boundingBox.top < midY }
            .flatMap { it.lines }
        
        return if (allTopHalfLines.isNotEmpty()) {
            topHalfLines.size.toDouble() / allTopHalfLines.size
        } else {
            0.0
        }
    }
    
    /**
     * 统计下半部分短行数量
     */
    private fun countBottomHalfShortLines(textBlocks: List<TextBlock>): Int {
        if (textBlocks.isEmpty()) return 0
        
        val maxY = textBlocks.maxOfOrNull { it.boundingBox.bottom } ?: 0
        val midY = maxY / 2
        
        return textBlocks
            .filter { it.boundingBox.top >= midY }
            .flatMap { it.lines }
            .count { it.text.trim().length < 15 }
    }
    
    /**
     * 是否有替代标记（扩展识别：圈数字、①②、实心/空心圆、短横线、方块符号等）
     */
    private fun hasAlternativeMarkers(text: String): Boolean {
        // 基础标记
        val basicMarkers = listOf("·", "-", "○", "●", "■", "□", "→", "→", "—", "—")
        
        // 带圈数字（①②③④⑤⑥⑦⑧⑨⑩）
        val circledNumbers = Regex("[①②③④⑤⑥⑦⑧⑨⑩]")
        
        // 带圈字母（ⓐⓑⓒⓓ等，但OCR可能识别不准确，先不加入）
        
        // 短横线或下划线（可能表示选项）
        val dashPattern = Regex("^[\\s]*[-—–][\\s]*")
        
        return basicMarkers.any { text.contains(it) } ||
               circledNumbers.containsMatchIn(text) ||
               text.lines().any { dashPattern.containsMatchIn(it) }
    }
    
    /**
     * 是否有分隔符标记
     */
    private fun hasSeparatorMarkers(text: String): Boolean {
        val separators = listOf("|", "｜", "/", "\\", "——", "——")
        return separators.any { text.contains(it) }
    }
    
    /**
     * 计算选项左对齐度（基于包含选项标记的行）
     * 选项通常左对齐，即使有选项标记（A. B. C. D.）也通常对齐
     * 增强：同时检测水平排列（Y坐标接近）和底部位置
     */
    private fun calculateOptionLeftAlignmentScore(textBlocks: List<TextBlock>, lines: List<String>): Double {
        if (textBlocks.isEmpty() || lines.isEmpty()) return 0.0
        
        // 找到包含选项标记的行（A. B. C. D. 等，包括数字选项①②③④）
        val optionLineIndices = lines.mapIndexedNotNull { index, line ->
            val trimmed = line.trim()
            if (trimmed.matches(Regex("^[A-D][.。、:)]\\s*.+")) ||
                trimmed.matches(Regex("^[A-D]\\s+.*")) ||
                trimmed.matches(Regex("^[A-D][)）].*")) ||
                trimmed.matches(Regex("^[①②③④⑤⑥⑦⑧⑨⑩].*"))) {
                index
            } else null
        }
        
        if (optionLineIndices.size < 2) return 0.0
        
        // 获取这些行的位置信息（左边界和Y坐标）
        val optionPositions = optionLineIndices.mapNotNull { lineIndex ->
            // 找到对应的文本块和行
            var currentLineIndex = 0
            for (block in textBlocks) {
                for (line in block.lines) {
                    if (currentLineIndex == lineIndex) {
                        return@mapNotNull Pair(line.boundingBox.left.toDouble(), line.boundingBox.top.toDouble())
                    }
                    currentLineIndex++
                }
            }
            null
        }
        
        if (optionPositions.size < 2) return 0.0
        
        // 计算左对齐度（标准差越小，对齐越好）
        val leftPositions = optionPositions.map { it.first }
        val avgLeft = leftPositions.average()
        val leftVariance = leftPositions.map { (it - avgLeft) * (it - avgLeft) }.average()
        val leftStdDev = kotlin.math.sqrt(leftVariance)
        val leftAlignmentScore = if (avgLeft > 0) {
            max(0.0, 1.0 - (leftStdDev / avgLeft) / 0.1)
        } else {
            0.0
        }
        
        // 计算水平排列度（Y坐标接近度，标准差越小，排列越水平）
        val yPositions = optionPositions.map { it.second }
        val avgY = yPositions.average()
        val yVariance = yPositions.map { (it - avgY) * (it - avgY) }.average()
        val yStdDev = kotlin.math.sqrt(yVariance)
        val horizontalAlignmentScore = if (avgY > 0) {
            // 如果Y坐标标准差小于平均值的5%，认为水平排列良好
            max(0.0, 1.0 - (yStdDev / avgY) / 0.05)
        } else {
            0.0
        }
        
        // 计算是否在底部区域（选项通常在图片下半部分）
        val maxY = textBlocks.maxOfOrNull { it.boundingBox.bottom } ?: 0
        val bottomHalfY = maxY / 2
        val bottomPositionScore = if (maxY > 0) {
            // 如果所有选项都在下半部分，给予加分
            val bottomCount = optionPositions.count { it.second >= bottomHalfY }
            bottomCount.toDouble() / optionPositions.size
        } else {
            0.0
        }
        
        // 综合评分：左对齐(40%) + 水平排列(40%) + 底部位置(20%)
        val combinedScore = leftAlignmentScore * 0.4 + horizontalAlignmentScore * 0.4 + bottomPositionScore * 0.2
        
        android.util.Log.d("FeatureExtractor", "选项对齐检测: 左对齐${String.format("%.2f", leftAlignmentScore)}, 水平排列${String.format("%.2f", horizontalAlignmentScore)}, 底部位置${String.format("%.2f", bottomPositionScore)}, 综合${String.format("%.2f", combinedScore)}")
        
        return combinedScore
    }
    
    /**
     * 计算选项间距一致性
     * 选项之间的垂直间距应该相似
     * 增强：优先检测包含选项标记的文本块之间的间距
     */
    private fun calculateOptionSpacingConsistency(textBlocks: List<TextBlock>): Double {
        if (textBlocks.size < 3) return 0.0
        
        // 找到包含选项标记的文本块（A. B. C. D. 或 ①②③④等）
        val optionBlocks = textBlocks.filter { block ->
            block.lines.any { line ->
                val trimmed = line.text.trim()
                trimmed.matches(Regex("^[A-D][.。、:)]\\s*.+")) ||
                trimmed.matches(Regex("^[A-D]\\s+.*")) ||
                trimmed.matches(Regex("^[A-D][)）].*")) ||
                trimmed.matches(Regex("^[①②③④⑤⑥⑦⑧⑨⑩].*"))
            }
        }
        
        // 如果有选项标记的文本块，优先使用它们
        val blocksToCheck = if (optionBlocks.size >= 2) {
            optionBlocks.sortedBy { it.boundingBox.top }
        } else {
            // 否则使用所有文本块
            textBlocks.sortedBy { it.boundingBox.top }
        }
        
        if (blocksToCheck.size < 2) return 0.0
        
        // 计算相邻文本块之间的间距
        val spacings = mutableListOf<Int>()
        for (i in 0 until blocksToCheck.size - 1) {
            val spacing = blocksToCheck[i + 1].boundingBox.top - blocksToCheck[i].boundingBox.bottom
            if (spacing > 0) {
                spacings.add(spacing)
            }
        }
        
        if (spacings.size < 2) return 0.0
        
        // 计算间距的一致性（标准差越小，一致性越好）
        val avgSpacing = spacings.average()
        val variance = spacings.map { (it - avgSpacing) * (it - avgSpacing) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        // 如果标准差小于平均值的25%，认为一致性良好（更严格）
        val consistencyScore = if (avgSpacing > 0) {
            max(0.0, 1.0 - (stdDev / avgSpacing) / 0.25)
        } else {
            0.0
        }
        
        // 如果使用的是选项标记文本块，给予额外加分
        val bonus = if (optionBlocks.size >= 2) 0.1 else 0.0
        
        android.util.Log.d("FeatureExtractor", "选项间距一致性: ${String.format("%.2f", consistencyScore)}, 使用选项标记块: ${optionBlocks.size >= 2}")
        
        return min(1.0, consistencyScore + bonus)
    }
    
    /**
     * 计算文本块大小一致性
     * 选项文本块的高度应该相似
     */
    private fun calculateBlockSizeConsistency(textBlocks: List<TextBlock>): Double {
        if (textBlocks.size < 2) return 0.0
        
        val heights = textBlocks.map { it.boundingBox.height().toDouble() }
        val avgHeight = heights.average()
        
        if (avgHeight <= 0) return 0.0
        
        // 计算高度的变异系数（标准差/平均值）
        val variance = heights.map { (it - avgHeight) * (it - avgHeight) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        val coefficientOfVariation = stdDev / avgHeight
        
        // 变异系数越小，一致性越好（<0.3认为一致）
        return max(0.0, 1.0 - coefficientOfVariation / 0.3)
    }
    
    /**
     * 计算题目与选项的位置分离度
     * 题目应该在上方，选项在下方，有明显的分离
     * 增强：优先检测包含选项标记的文本块，更准确地识别选项区域
     */
    private fun calculateQuestionOptionSeparation(textBlocks: List<TextBlock>): Double {
        if (textBlocks.size < 3) return 0.0
        
        val maxY = textBlocks.maxOfOrNull { it.boundingBox.bottom } ?: 0
        val midY = maxY / 2
        
        // 找到包含选项标记的文本块（A. B. C. D. 或 ①②③④等）
        val optionBlocks = textBlocks.filter { block ->
            block.lines.any { line ->
                val trimmed = line.text.trim()
                trimmed.matches(Regex("^[A-D][.。、:)]\\s*.+")) ||
                trimmed.matches(Regex("^[A-D]\\s+.*")) ||
                trimmed.matches(Regex("^[A-D][)）].*")) ||
                trimmed.matches(Regex("^[①②③④⑤⑥⑦⑧⑨⑩].*"))
            }
        }
        
        // 如果有选项标记的文本块，使用它们来更准确地识别选项区域
        val bottomBlocks = if (optionBlocks.isNotEmpty()) {
            optionBlocks
        } else {
            // 否则使用下半部分的文本块
            textBlocks.filter { it.boundingBox.top >= midY }
        }
        
        // 上半部分的文本块（题目）- 排除选项块
        val topBlocks = textBlocks.filter { 
            it.boundingBox.top < midY && !bottomBlocks.contains(it)
        }
        
        if (topBlocks.isEmpty() || bottomBlocks.isEmpty()) return 0.0
        
        // 计算上半部分最下方的位置
        val topBottom = topBlocks.maxOfOrNull { it.boundingBox.bottom } ?: 0
        // 计算下半部分（选项）最上方的位置
        val bottomTop = bottomBlocks.minOfOrNull { it.boundingBox.top } ?: maxY
        
        // 计算分离距离
        val separation = bottomTop - topBottom
        
        // 如果分离距离大于图片高度的10%，认为分离良好
        val separationRatio = if (maxY > 0) separation.toDouble() / maxY else 0.0
        
        // 基础分离分数
        val baseScore = when {
            separationRatio > 0.15 -> 1.0  // 分离明显
            separationRatio > 0.10 -> 0.7  // 分离较好
            separationRatio > 0.05 -> 0.4  // 分离一般
            else -> 0.0  // 无明显分离
        }
        
        // 如果使用了选项标记块，给予额外加分（更准确）
        val bonus = if (optionBlocks.isNotEmpty()) 0.15 else 0.0
        
        // 检查选项是否都在底部区域（底部1/3）
        val bottomThirdY = maxY * 2 / 3
        val bottomPositionScore = if (maxY > 0 && bottomBlocks.isNotEmpty()) {
            val bottomCount = bottomBlocks.count { it.boundingBox.top >= bottomThirdY }
            bottomCount.toDouble() / bottomBlocks.size
        } else {
            0.0
        }
        
        // 综合评分：基础分离(60%) + 选项标记加分(15%) + 底部位置(25%)
        val combinedScore = baseScore * 0.6 + bonus + bottomPositionScore * 0.25
        
        android.util.Log.d("FeatureExtractor", "题目-选项分离度: 基础${String.format("%.2f", baseScore)}, 选项标记块:${optionBlocks.isNotEmpty()}, 底部位置${String.format("%.2f", bottomPositionScore)}, 综合${String.format("%.2f", combinedScore)}")
        
        return min(1.0, combinedScore)
    }
    
    /**
     * 检测短行簇（垂直对齐的短行，严格条件）
     * 注意：不能单独作为核心信号，必须配合选项标记使用
     */
    private fun detectShortLineClusters(textBlocks: List<TextBlock>): Int {
        if (textBlocks.isEmpty()) return 0
        
        val maxY = textBlocks.maxOfOrNull { it.boundingBox.bottom } ?: 0
        val midY = maxY / 2
        
        // 只检测下半部分的短行（选项通常在底部）
        val bottomHalfLines = textBlocks
            .filter { it.boundingBox.top >= midY }
            .flatMap { block ->
                block.lines.map { line ->
                    Pair(line, line.boundingBox)
                }
            }
            .filter { (line, _) ->
                val text = line.text.trim()
                // 更严格的短行定义：长度<15字符，且不是纯数字或特殊符号
                text.length < 15 && text.isNotEmpty() && 
                text.count { it.isDigit() } < text.length * 0.5  // 数字比例<50%
            }
        
        if (bottomHalfLines.size < 4) return 0  // 至少需要4个短行（更严格）
        
        // 按Y坐标排序
        val sortedLines = bottomHalfLines.sortedBy { it.second.top }
        
        // 使用X坐标聚类，找到垂直对齐的短行簇
        // 更严格的对齐要求：偏差<平均X的8%或30像素
        val clusters = mutableListOf<MutableList<Pair<TextLine, Rect>>>()
        
        for ((line, bbox) in sortedLines) {
            var foundCluster = false
            for (cluster in clusters) {
                val clusterAvgX = cluster.map { it.second.left }.average()
                val xDeviation = abs(bbox.left - clusterAvgX)
                val threshold = max(clusterAvgX * 0.08, 30.0)  // 更严格：8%或30像素
                
                if (xDeviation < threshold) {
                    cluster.add(Pair(line, bbox))
                    foundCluster = true
                    break
                }
            }
            
            if (!foundCluster) {
                clusters.add(mutableListOf(Pair(line, bbox)))
            }
        }
        
        // 检查簇的间距一致性（选项间距应该相似）
        val validClusters = clusters.filter { cluster ->
            if (cluster.size < 4) return@filter false  // 至少4个
            
            // 检查间距一致性
            val sortedCluster = cluster.sortedBy { it.second.top }
            val spacings = mutableListOf<Int>()
            for (i in 0 until sortedCluster.size - 1) {
                val spacing = sortedCluster[i + 1].second.top - sortedCluster[i].second.bottom
                if (spacing > 0 && spacing < 200) {
                    spacings.add(spacing)
                }
            }
            
            if (spacings.size < 2) return@filter false
            
            val avgSpacing = spacings.average()
            val variance = spacings.map { (it - avgSpacing) * (it - avgSpacing) }.average()
            val stdDev = kotlin.math.sqrt(variance)
            
            // 间距一致性要求：标准差<平均值的25%
            stdDev / avgSpacing < 0.25
        }
        
        // 返回最大的有效簇的大小
        val maxClusterSize = validClusters.maxOfOrNull { it.size } ?: 0
        return if (maxClusterSize >= 4) maxClusterSize else 0  // 至少4个
    }
    
    /**
     * 计算短行簇的对齐分数
     */
    private fun calculateShortLineClusterAlignment(textBlocks: List<TextBlock>): Double {
        if (textBlocks.isEmpty()) return 0.0
        
        val allLines = textBlocks.flatMap { block ->
            block.lines.map { line ->
                Pair(line, line.boundingBox)
            }
        }.filter { (line, _) ->
            line.text.trim().length < 20 && line.text.trim().isNotEmpty()
        }
        
        if (allLines.size < 3) return 0.0
        
        val sortedLines = allLines.sortedBy { it.second.top }
        val xPositions = sortedLines.map { it.second.left.toDouble() }
        
        if (xPositions.isEmpty()) return 0.0
        
        val avgX = xPositions.average()
        val variance = xPositions.map { (it - avgX) * (it - avgX) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        // 如果标准差小于平均值的15%，认为对齐良好
        return if (avgX > 0) {
            max(0.0, 1.0 - (stdDev / avgX) / 0.15)
        } else {
            0.0
        }
    }
    
    /**
     * 计算短行簇的间距一致性
     * 选项之间的垂直间距应该相似
     */
    private fun calculateShortLineClusterSpacing(textBlocks: List<TextBlock>): Double {
        if (textBlocks.isEmpty()) return 0.0
        
        val allLines = textBlocks.flatMap { block ->
            block.lines.map { line ->
                Pair(line, line.boundingBox)
            }
        }.filter { (line, _) ->
            line.text.trim().length < 20 && line.text.trim().isNotEmpty()
        }
        
        if (allLines.size < 3) return 0.0
        
        val sortedLines = allLines.sortedBy { it.second.top }
        
        // 计算相邻短行之间的垂直间距
        val spacings = mutableListOf<Int>()
        for (i in 0 until sortedLines.size - 1) {
            val spacing = sortedLines[i + 1].second.top - sortedLines[i].second.bottom
            if (spacing > 0 && spacing < 200) { // 间距在合理范围内
                spacings.add(spacing)
            }
        }
        
        if (spacings.size < 2) return 0.0
        
        // 计算间距的一致性（标准差越小，一致性越好）
        val avgSpacing = spacings.average()
        val variance = spacings.map { (it - avgSpacing) * (it - avgSpacing) }.average()
        val stdDev = kotlin.math.sqrt(variance)
        
        // 如果标准差小于平均值的30%，认为一致性良好
        return if (avgSpacing > 0) {
            max(0.0, 1.0 - (stdDev / avgSpacing) / 0.3)
        } else {
            0.0
        }
    }
}

/**
 * 题目特征
 */
data class QuestionFeatures(
    // 基础统计
    val numTextBlocks: Int,
    val numLines: Int,
    val avgLineLength: Double,
    val shortLineRatio: Double,
    
    // 选项相关
    val optionMarkerCount: Int,
    val bboxVerticalAlignmentScore: Double,
    
    // OCR质量
    val ocrConfidenceMean: Double,
    
    // 内容特征
    val emojiOrPunctRatio: Double,
    val hasQuestionKeywords: Boolean,
    val hasMathSymbols: Boolean,
    
    // 布局特征
    val topHalfLongLineRatio: Double,
    val bottomHalfShortLines: Int,
    
    // 新增布局/视觉特征
    val optionLeftAlignmentScore: Double,      // 选项左对齐度 (0-1)
    val optionSpacingConsistency: Double,       // 选项间距一致性 (0-1)
    val blockSizeConsistency: Double,           // 文本块大小一致性 (0-1)
    val questionOptionSeparation: Double,       // 题目与选项位置分离度 (0-1)
    
    // 核心改进：短行簇检测（选项结构识别）
    val shortLineClusterCount: Int,              // 短行簇数量（≥3表示有选项结构）
    val shortLineClusterAlignmentScore: Double,  // 短行簇对齐分数 (0-1)
    val shortLineClusterSpacingConsistency: Double, // 短行簇间距一致性 (0-1)
    
    // 特殊场景
    val hasAlternativeMarkers: Boolean,
    val hasSeparatorMarkers: Boolean
)

