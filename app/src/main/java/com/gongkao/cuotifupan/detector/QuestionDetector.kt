package com.gongkao.cuotifupan.detector

import android.util.Log
import com.gongkao.cuotifupan.ocr.OcrResult
import kotlin.math.max

/**
 * 题目检测结果
 */
data class DetectionResult(
    val isQuestion: Boolean,
    val confidence: Float,
    val questionText: String = "",
    val options: List<String> = emptyList()
)

/**
 * 题目检测器（基于特征提取和加权打分）
 */
class QuestionDetector(
    private val weights: ScoringWeights = ScoringWeights.default()
) {
    
    companion object {
        // 题干关键词
        private val QUESTION_KEYWORDS = listOf(
            "下列", "不正确的是", "正确的是", "依据", "属于", "应该", "不属于",
            "以下", "错误的是", "说法", "符合", "不符合", "表述", "判断",
            "选择", "关于", "描述", "定义", "特点", "作用", "影响", "原因",
            "根据", "按照", "哪个", "什么", "如何", "怎样", "为什么"
        )
        
        // 排除关键词（这些关键词出现说明不是题目）
        // 注意：不再依赖排除关键词，而是专注于识别正确图片的特征
        private val EXCLUDE_KEYWORDS: List<String> = emptyList()
        
        // 选项标记（常见格式）
        private val OPTION_PATTERNS = listOf(
            Regex("^[A-D][.、)]"),           // A. A、 A)
            Regex("^[A-D]\\s+[\\u4e00-\\u9fa5]"),  // A 开头后跟中文
            Regex("[①②③④⑤⑥]"),
            Regex("\\([A-D]\\)"),
            Regex("[（][A-D][）]")
        )
    }
    
    /**
     * 检测文本是否为题目
     */
    fun detect(ocrResult: OcrResult): DetectionResult {
        if (!ocrResult.success || ocrResult.rawText.isBlank()) {
            return DetectionResult(false, 0f)
        }
        
        val text = ocrResult.rawText
        
        // ========== 第一步：快速排除明显不是题目的 ==========
        // 注意：不使用排除关键词，只使用基本的统计特征进行快速排除
        
        // 如果有题型标记（"单选题"等），跳过快速排除，直接进入特征提取
        val hasQuestionTypeMarker = text.contains("单选题") || text.contains("多选题") || 
                                   text.contains("判断题") || text.contains("选择题") ||
                                   text.contains("填空题") || text.contains("问答题")
        
        if (!hasQuestionTypeMarker) {
            // 1. 数字比例检查（超过70%才排除，因为题目中可能有较多数字）
            val digitCount = text.count { it.isDigit() }
            val digitRatio = if (text.length > 0) digitCount.toFloat() / text.length else 0f
            if (digitRatio > 0.7f) {  // 提高到70%，更宽松
                Log.d("QuestionDetector", "快速排除：数字比例过高(${String.format("%.1f", digitRatio * 100)}%)")
                return DetectionResult(false, 0f)
            }
            
            // 2. 特殊符号检查（超过15个才排除，更宽松）
            val specialCount = text.count { it in "￥$@#%&*+=<>《》【】[]{}「」" }
            if (specialCount > 15) {  // 提高到15个，更宽松
                Log.d("QuestionDetector", "快速排除：特殊符号过多(${specialCount}个)")
                return DetectionResult(false, 0f)
            }
        } else {
            Log.d("QuestionDetector", "检测到题型标记，跳过快速排除")
        }
        
        // ========== 第二步：特征提取 ==========
        
        // 调试：输出OCR文本的前500字符
        Log.d("QuestionDetector", "OCR文本预览: ${text.take(500)}")
        Log.d("QuestionDetector", "OCR行数: ${ocrResult.lines.size}")
        ocrResult.lines.take(10).forEachIndexed { index, line ->
            Log.d("QuestionDetector", "  行$index: $line")
        }
        
        val features = FeatureExtractor.extractFeatures(ocrResult)
        
        // 调试：输出关键特征
        Log.d("QuestionDetector", "特征提取结果:")
        Log.d("QuestionDetector", "  选项标记数量: ${features.optionMarkerCount}")
        Log.d("QuestionDetector", "  是否包含题目关键词: ${features.hasQuestionKeywords}")
        Log.d("QuestionDetector", "  是否有题型标记: ${FeatureExtractor.hasQuestionTypeMarker(text)}")
        Log.d("QuestionDetector", "  是否有题目序号: ${FeatureExtractor.hasQuestionNumber(text)}")
        Log.d("QuestionDetector", "  行数: ${features.numLines}")
        Log.d("QuestionDetector", "  文本块数: ${features.numTextBlocks}")
        Log.d("QuestionDetector", "  平均行长度: ${String.format("%.1f", features.avgLineLength)}")
        Log.d("QuestionDetector", "  短行比例: ${String.format("%.1f", features.shortLineRatio * 100)}%")
        
        // ========== 第三步：特殊场景处理（布局替代） ==========
        
        val alternativeResult = LayoutAlternativeDetector.detectAlternativeOptions(ocrResult.textBlocks)
        
        // 如果检测到替代选项结构，补充特征
        val enhancedFeatures = if (alternativeResult.isValid) {
            features.copy(
                bottomHalfShortLines = alternativeResult.shortLineCount,
                bboxVerticalAlignmentScore = max(
                    features.bboxVerticalAlignmentScore,
                    alternativeResult.alignmentScore
                )
            )
        } else {
            features
        }
        
        // ========== 第四步：加权线性打分 ==========
        
        // 检查是否有明确题型标记
        val hasExplicitTypeMarker = FeatureExtractor.hasQuestionTypeMarker(text)
        // 检查是否有题目序号
        val hasQuestionNumber = FeatureExtractor.hasQuestionNumber(text)
        // 检查是否有明显的题目关键词（如"分类"、"正确的一项是"等）
        val hasStrongQuestionKeywords = enhancedFeatures.hasQuestionKeywords
        
        Log.d("QuestionDetector", "========== 特殊标记检测 ==========")
        Log.d("QuestionDetector", "是否有明确题型标记: $hasExplicitTypeMarker")
        Log.d("QuestionDetector", "是否有题目序号: $hasQuestionNumber")
        Log.d("QuestionDetector", "是否有明显题目关键词: $hasStrongQuestionKeywords")
        Log.d("QuestionDetector", "选项标记数: ${enhancedFeatures.optionMarkerCount}")
        
        // 如果有明确题型标记、题目序号或明显题目关键词，需要特殊处理（即使选项标记为0也要处理）
        val needsSpecialHandling = (hasExplicitTypeMarker || hasQuestionNumber || hasStrongQuestionKeywords) && enhancedFeatures.optionMarkerCount < 2
        Log.d("QuestionDetector", "是否需要特殊处理: $needsSpecialHandling")
        
        // 如果有明确题型标记或题目序号，创建一个临时特征，将选项标记数设为2（让基础特征能给分）
        val featuresForScoring = if (needsSpecialHandling && enhancedFeatures.optionMarkerCount == 0) {
            // 临时将选项标记数设为2，让基础特征能给分
            enhancedFeatures.copy(optionMarkerCount = 2)
        } else {
            enhancedFeatures
        }
        
        val calculator = ScoreCalculator(weights)
        var scoringResult = calculator.calculateScore(featuresForScoring)
        
        // 如果有明确题型标记或题目序号但选项标记少于2个，需要调整分数
        if (needsSpecialHandling) {
            // 如果之前临时设置了optionMarkerCount=2，需要减去选项标记的分数（因为实际上没有选项标记）
            val actualOptionScore = when {
                enhancedFeatures.optionMarkerCount >= 1 -> 30.0 * weights.optionMarkerCount  // 1个选项标记的分数
                else -> 0.0  // 没有选项标记
            }
            
            // 计算临时设置optionMarkerCount=2时的选项分数
            val tempOptionScore = if (enhancedFeatures.optionMarkerCount == 0) {
                60.0 * weights.optionMarkerCount  // 2个选项标记的分数
            } else {
                0.0
            }
            
            // 调整分数：减去临时添加的选项分数，加上实际的选项分数
            val scoreAdjustment = actualOptionScore - tempOptionScore
            
            // 额外补偿分数（大幅增加，确保能被识别）
            // 如果有题型标记、题目序号或明显题目关键词，补偿更高
            val compensationScore = when {
                hasExplicitTypeMarker && hasQuestionNumber && enhancedFeatures.optionMarkerCount == 0 -> 60.0 // 有题型标记和题目序号，0选项，给60分补偿（提高）
                hasExplicitTypeMarker && hasQuestionNumber && enhancedFeatures.optionMarkerCount >= 1 -> 40.0 // 有题型标记和题目序号，1选项，给40分补偿（提高）
                hasExplicitTypeMarker && enhancedFeatures.optionMarkerCount == 0 -> 45.0  // 仅有题型标记，0选项，给45分补偿（提高）
                hasExplicitTypeMarker && enhancedFeatures.optionMarkerCount >= 1 -> 25.0  // 仅有题型标记，1选项，给25分补偿（提高）
                hasQuestionNumber && enhancedFeatures.optionMarkerCount == 0 -> 50.0 // 仅有题目序号，0选项，给50分补偿（提高，确保能通过2.0阈值）
                hasQuestionNumber && enhancedFeatures.optionMarkerCount >= 1 -> 30.0 // 仅有题目序号，1选项，给30分补偿（提高）
                hasStrongQuestionKeywords && enhancedFeatures.optionMarkerCount == 0 -> 20.0 // 仅有明显题目关键词，0选项，给20分补偿（降低，避免误识别）
                hasStrongQuestionKeywords && enhancedFeatures.optionMarkerCount >= 1 -> 15.0 // 仅有明显题目关键词，1选项，给15分补偿（降低）
                else -> 0.0
            }
            
            val compensationReason = when {
                hasExplicitTypeMarker && hasQuestionNumber -> "有明确题型标记和题目序号但选项标记较少(${enhancedFeatures.optionMarkerCount}个)，给予补偿分数${compensationScore}分"
                hasExplicitTypeMarker -> "有明确题型标记但选项标记较少(${enhancedFeatures.optionMarkerCount}个)，给予补偿分数${compensationScore}分"
                hasQuestionNumber -> "有题目序号但选项标记较少(${enhancedFeatures.optionMarkerCount}个)，给予补偿分数${compensationScore}分"
                hasStrongQuestionKeywords -> "有明显题目关键词但选项标记较少(${enhancedFeatures.optionMarkerCount}个)，给予补偿分数${compensationScore}分"
                else -> ""
            }
            
            val originalScore = scoringResult.score
            val finalScore = scoringResult.score + scoreAdjustment + compensationScore
            
            Log.d("QuestionDetector", "========== 分数调整 ==========")
            Log.d("QuestionDetector", "原始分数: ${String.format("%.2f", originalScore)}")
            Log.d("QuestionDetector", "选项分数调整: ${String.format("%.2f", scoreAdjustment)}")
            Log.d("QuestionDetector", "补偿分数: ${String.format("%.2f", compensationScore)}")
            Log.d("QuestionDetector", "最终分数: ${String.format("%.2f", finalScore)}")
            Log.d("QuestionDetector", "补偿原因: $compensationReason")
            
            scoringResult = scoringResult.copy(
                score = finalScore,
                reasons = scoringResult.reasons + compensationReason
            )
        }
        
        // ========== 第五步：决策 ==========
        
        // 核心原则：选项标记是题目的核心特征
        // 但有明确题型标记（"单选题"等）或题目序号时，即使只有1个选项标记也放宽要求
        val finalDecision = when {
            // 有2个以上选项标记：使用原始决策
            enhancedFeatures.optionMarkerCount >= 2 -> scoringResult.decision
            
            // 有明确题型标记、题目序号或明显题目关键词：大幅放宽要求（即使选项标记为0也放宽）
            hasExplicitTypeMarker || hasQuestionNumber || hasStrongQuestionKeywords -> {
                // 有题型标记或题目序号时，大幅降低阈值要求
                Log.d("QuestionDetector", "检测到明确题型标记(${hasExplicitTypeMarker})或题目序号(${hasQuestionNumber})，放宽要求。当前分数: ${scoringResult.score}, 选项标记数: ${enhancedFeatures.optionMarkerCount}")
                
                // 优先检查最强的信号组合
                when {
                    // 如果有题型标记和题目序号，这是极强信号，直接确认（无论分数多少）
                    hasExplicitTypeMarker && hasQuestionNumber -> {
                        Log.d("QuestionDetector", "检测到题型标记和题目序号（极强信号），直接确认为题目，分数: ${scoringResult.score}")
                        "confirm"
                    }
                    // 如果有题型标记（但没有题目序号），这也是强信号，直接确认
                    hasExplicitTypeMarker -> {
                        Log.d("QuestionDetector", "检测到题型标记（强信号），直接确认为题目，分数: ${scoringResult.score}")
                        "confirm"
                    }
                    // 如果有题目序号（但没有题型标记），这也是强信号，直接确认
                    hasQuestionNumber -> {
                        Log.d("QuestionDetector", "检测到题目序号（强信号），直接确认为题目，分数: ${scoringResult.score}")
                        "confirm"
                    }
                    // 如果有明显题目关键词（如"分类"、"正确的一项是"、"下列"），需要更严格的条件
                    // 对于非常明确的题目关键词（如"分类"、"分为"、"正确的一项是"），即使只有1个选项标记也放宽要求
                    hasStrongQuestionKeywords -> {
                        // 检查是否是"分类"、"分为"、"填入"等非常明确的题目关键词（包括OCR错误变体）
                        val hasVeryStrongKeywords = listOf(
                            "分类", "分为", "分成",
                            "正确的一项是", "正确的是", "错误的是", "不正确的是",
                            "可以推出", "推出", "可以得出", "得出",
                            "填入", "填入画横线", "填入横线", "填入划横线",
                            // OCR错误变体
                            "镇入", "镇入画横线", "镇入画橫线",  // "填入"可能识别为"镇入"
                            "最恰当的一项是", "最恰当的是", "最怡当的一项是", "最怡当的是", "最怡当",  // "恰当"可能识别为"怡当"
                            "一顶是", "一顶"  // "一项"可能识别为"一顶"
                        ).any { text.contains(it) }
                        
                        val hasEnoughOptions = if (hasVeryStrongKeywords) {
                            // 对于非常明确的题目关键词，1个选项标记也足够
                            enhancedFeatures.optionMarkerCount >= 1 || hasQuestionNumber
                        } else {
                            // 对于其他关键词，需要至少2个选项标记
                            enhancedFeatures.optionMarkerCount >= 2 || hasQuestionNumber
                        }
                        
                        // 如果分数很高（>=70）且选项标记为0，需要更严格的条件：
                        // 1. 必须有题目序号，或
                        // 2. 必须有非常明确的题目关键词（如"分类"、"正确的一项是"、"可以推出"等）
                        val hasHighScore = scoringResult.score >= 70.0
                        val hasStrictConditions = hasQuestionNumber || hasVeryStrongKeywords
                        
                        if ((scoringResult.score >= 15.0 && hasEnoughOptions) || (hasHighScore && hasStrictConditions && scoringResult.score >= 15.0)) {
                            Log.d("QuestionDetector", "检测到明显题目关键词（强信号），且有足够选项标记或题目序号或高分+严格条件，且分数>=15分，确认为题目，分数: ${scoringResult.score}")
                            "confirm"
                        } else {
                            Log.d("QuestionDetector", "检测到明显题目关键词，但条件不满足(分数:${scoringResult.score}, 选项标记:${enhancedFeatures.optionMarkerCount}, 题目序号:$hasQuestionNumber, 非常明确关键词:$hasVeryStrongKeywords)，忽略")
                            "ignore"
                        }
                    }
                    // 理论上不会走到这里，但为了安全起见
                    scoringResult.score >= weights.thresholdAuto -> {
                        Log.d("QuestionDetector", "达到auto_add阈值(${weights.thresholdAuto})")
                        "auto_add"
                    }
                    scoringResult.score >= weights.thresholdConfirm -> {
                        Log.d("QuestionDetector", "达到confirm阈值(${weights.thresholdConfirm})")
                        "confirm"
                    }
                    else -> {
                        Log.d("QuestionDetector", "分数过低(${scoringResult.score})，但已有强信号，确认为题目")
                        "confirm"  // 即使分数低，只要有强信号也确认
                    }
                }
            }
            
            // 有1个选项标记：稍微提高要求，但如果有明显的题目特征则放宽
            enhancedFeatures.optionMarkerCount == 1 -> {
                // 检查是否有明显的题目特征（如"分类"、"选择"、"正确的一项是"等）
                val hasQuestionIndicators = listOf(
                    "分类", "选择", "正确的一项是", "正确的是", 
                    "分为", "分成", "选出", "选出的是", "选出正确",
                    "哪一项", "哪一", "哪", "哪个", "哪些"
                ).any { text.contains(it) }
                
                if (hasQuestionIndicators) {
                    // 有明显的题目特征，放宽要求（降低到15分）
                    if (scoringResult.decision == "confirm" && scoringResult.score < 15.0) {
                        Log.d("QuestionDetector", "有1个选项标记且有明显题目特征，但分数过低(${scoringResult.score})，忽略")
                        "ignore"
                    } else {
                        Log.d("QuestionDetector", "有1个选项标记且有明显题目特征，确认为题目，分数: ${scoringResult.score}")
                        "confirm"
                    }
                } else {
                    // 没有明显题目特征，保持原有逻辑
                    if (scoringResult.decision == "confirm" && scoringResult.score < 25.0) {
                        "ignore"  // 1个选项时，confirm需要至少25分
                    } else {
                        scoringResult.decision
                    }
                }
            }
            
            // 没有选项标记且没有题型标记也没有题目序号：直接忽略
            else -> {
                // 这种情况已经在上面处理了（有题型标记或题目序号的情况），这里只处理没有这些强信号的情况
                when {
                    // 没有选项标记、题型标记、题目序号时，auto需要90分以上（几乎不可能）
                    scoringResult.decision == "auto_add" && scoringResult.score >= 90.0 -> "auto_add"
                    // 其他情况都忽略
                    else -> "ignore"
                }
            }
        }
        
        val isQuestion = when (finalDecision) {
            "auto_add" -> true
            "confirm" -> true  // 需要确认的也认为是题目
            else -> false
        }
        
        // 记录打分明细到日志
        Log.d("QuestionDetector", "========== 最终结果 ==========")
        Log.d("QuestionDetector", "打分: ${String.format("%.1f", scoringResult.score)}, 原始决策: ${scoringResult.decision}, 最终决策: $finalDecision")
        if (finalDecision != scoringResult.decision) {
            val reason = when {
                enhancedFeatures.optionMarkerCount == 0 -> "无选项标记，提高阈值要求"
                enhancedFeatures.optionMarkerCount == 1 -> "仅1个选项标记，提高阈值要求"
                hasExplicitTypeMarker -> "有明确题型标记，放宽要求"
                else -> "其他原因"
            }
            Log.d("QuestionDetector", "  决策调整原因: $reason")
        }
        Log.d("QuestionDetector", "打分明细:")
        if (scoringResult.reasons.isEmpty()) {
            Log.d("QuestionDetector", "  (无得分项)")
        } else {
            scoringResult.reasons.forEach { reason ->
                Log.d("QuestionDetector", "  - $reason")
            }
        }
        Log.d("QuestionDetector", "是否为题目: $isQuestion")
        Log.d("QuestionDetector", "==================================")
        
        val confidence = (scoringResult.score / 100.0).toFloat()
        
        // 如果判定为题目，尝试提取题干和选项
        return if (isQuestion) {
            val (questionText, options) = extractQuestionAndOptions(ocrResult)
            DetectionResult(true, confidence, questionText, options)
        } else {
            DetectionResult(false, confidence)
        }
    }
    
    /**
     * 统计有效选项数量（严格匹配）
     */
    private fun countValidOptions(lines: List<String>): Int {
        var count = 0
        val seenOptions = mutableSetOf<Char>()
        
        for (line in lines) {
            val trimmed = line.trim()
            
            // 匹配常见的选项格式
            val optionMatch = when {
                // A. B. C. D. 格式
                trimmed.matches(Regex("^([A-D])[.、)]\\s*.+")) -> {
                    Regex("^([A-D])").find(trimmed)?.groupValues?.get(1)?.firstOrNull()
                }
                // A 开头后跟空格和至少2个中文字符
                trimmed.matches(Regex("^([A-D])\\s+[\\u4e00-\\u9fa5]{2,}.*")) -> {
                    trimmed.first()
                }
                else -> null
            }
            
            // 如果匹配到选项且是新的选项（A、B、C、D 不重复）
            if (optionMatch != null && optionMatch !in seenOptions) {
                seenOptions.add(optionMatch)
                count++
            }
        }
        
        return count
    }
    
    /**
     * 提取题干和选项
     */
    private fun extractQuestionAndOptions(ocrResult: OcrResult): Pair<String, List<String>> {
        val lines = ocrResult.lines
        val options = mutableListOf<String>()
        val questionLines = mutableListOf<String>()
        
        for (line in lines) {
            val trimmedLine = line.trim()
            
            // 检查是否为选项行（多种格式）
            val isOption = when {
                // A. B. C. D. 格式
                trimmedLine.matches(Regex("^[A-D][.、)].*")) -> true
                // A 开头后跟空格和中文
                trimmedLine.matches(Regex("^[A-D]\\s+[\\u4e00-\\u9fa5].*")) -> true
                // 单独的 A B C D
                trimmedLine.matches(Regex("^[A-D]$")) -> true
                // 包含其他选项标记
                OPTION_PATTERNS.any { it.containsMatchIn(trimmedLine) } -> true
                else -> false
            }
            
            if (isOption) {
                options.add(trimmedLine)
            } else {
                // 排除一些不需要的行
                if (trimmedLine.isNotEmpty() && 
                    !trimmedLine.matches(Regex("^\\d+/\\d+$")) &&  // 排除 "5/69" 这种页码
                    trimmedLine != "单选题" && 
                    trimmedLine != "多选题" &&
                    trimmedLine != "判断题") {
                    questionLines.add(trimmedLine)
                }
            }
        }
        
        val questionText = questionLines.joinToString("\n")
        return Pair(questionText, options)
    }
}

