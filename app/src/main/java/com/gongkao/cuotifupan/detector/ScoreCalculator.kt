package com.gongkao.cuotifupan.detector

import android.util.Log
import kotlin.math.max
import kotlin.math.min

/**
 * 打分器：加权线性打分
 */
class ScoreCalculator(
    private val weights: ScoringWeights = ScoringWeights.default()
) {
    
    companion object {
        private const val TAG = "ScoreCalculator"
    }
    
    /**
     * 计算总分和决策
     */
    fun calculateScore(features: QuestionFeatures): ScoringResult {
        val reasons = mutableListOf<String>()
        var totalScore = 0.0
        
        // 核心原则：至少2个选项标记才是强信号（避免误识别）
        // 注意：有明确题型标记时的放宽逻辑在QuestionDetector中处理
        // 增强：即使选项标记<2，如果布局特征很强（如短行簇、对齐等），也应该给分
        val hasOptions = features.optionMarkerCount >= 2  // 至少2个选项标记才算
        // 检查是否有强布局特征（即使选项标记<2，布局特征也能说明是题目）
        val hasStrongLayoutFeatures = features.shortLineClusterCount >= 3 || 
                                     features.optionLeftAlignmentScore > 0.7 ||
                                     features.optionSpacingConsistency > 0.7 ||
                                     features.questionOptionSeparation > 0.7
        // 没有至少2个选项标记时，如果布局特征很强，仍然给分（但权重降低）
        val baseFeatureWeight = if (hasOptions) {
            1.0
        } else if (hasStrongLayoutFeatures) {
            0.5  // 布局特征很强时，给50%权重
        } else {
            0.0
        }
        
        Log.d(TAG, "========== 开始打分 ==========")
        Log.d(TAG, "选项标记数: ${features.optionMarkerCount}, hasOptions: $hasOptions, baseFeatureWeight: $baseFeatureWeight")
        
        // 1. 文本块数量（3-10个最合理）
        val blockScore = when {
            features.numTextBlocks in 3..10 -> 100.0
            features.numTextBlocks in 2..15 -> 50.0
            else -> 0.0
        }
        val blockContribution = blockScore * weights.numTextBlocks * baseFeatureWeight
        totalScore += blockContribution
        Log.d(TAG, "  文本块数量: ${features.numTextBlocks}个 -> 得分${String.format("%.1f", blockScore)}, 权重${weights.numTextBlocks}, 贡献${String.format("%.2f", blockContribution)}")
        if (blockScore > 0) reasons.add("文本块数量合理(${features.numTextBlocks}个)")
        
        // 2. 行数（5-30行最合理）
        val lineScore = when {
            features.numLines in 5..30 -> 100.0
            features.numLines in 3..50 -> 50.0
            else -> 0.0
        }
        val lineContribution = lineScore * weights.numLines * baseFeatureWeight
        totalScore += lineContribution
        Log.d(TAG, "  行数: ${features.numLines}行 -> 得分${String.format("%.1f", lineScore)}, 权重${weights.numLines}, 贡献${String.format("%.2f", lineContribution)}")
        if (lineScore > 0) reasons.add("行数合理(${features.numLines}行)")
        
        // 3. 平均行长度（10-50字符最合理）
        val avgLengthScore = when {
            features.avgLineLength in 10.0..50.0 -> 100.0
            features.avgLineLength in 5.0..80.0 -> 50.0
            else -> 0.0
        }
        val avgLengthContribution = avgLengthScore * weights.avgLineLength * baseFeatureWeight
        totalScore += avgLengthContribution
        Log.d(TAG, "  平均行长度: ${String.format("%.1f", features.avgLineLength)}字符 -> 得分${String.format("%.1f", avgLengthScore)}, 权重${weights.avgLineLength}, 贡献${String.format("%.2f", avgLengthContribution)}")
        if (avgLengthScore > 0) reasons.add("平均行长度合理(${String.format("%.1f", features.avgLineLength)}字符)")
        
        // 4. 短行比例（0.2-0.6最合理，说明有题干和选项）
        // 没有至少2个选项标记时，短行比例不应该给分（因为短行可能是其他内容）
        val shortLineScore = if (hasOptions) {
            when {
                features.shortLineRatio in 0.2..0.6 -> 100.0
                features.shortLineRatio in 0.1..0.8 -> 50.0
                else -> 0.0
            }
        } else {
            0.0  // 没有选项标记时，短行比例不给分
        }
        totalScore += shortLineScore * weights.shortLineRatio
        if (shortLineScore > 0) reasons.add("短行比例合理(${String.format("%.1f", features.shortLineRatio * 100)}%)")
        
        // 5. 选项标记数量（2-4个最理想）
        val optionScore = when {
            features.optionMarkerCount >= 4 -> 100.0
            features.optionMarkerCount == 3 -> 80.0
            features.optionMarkerCount == 2 -> 60.0
            features.optionMarkerCount == 1 -> 30.0
            else -> 0.0
        }
        val optionContribution = optionScore * weights.optionMarkerCount
        totalScore += optionContribution
        Log.d(TAG, "  选项标记: ${features.optionMarkerCount}个 -> 得分${String.format("%.1f", optionScore)}, 权重${weights.optionMarkerCount}, 贡献${String.format("%.2f", optionContribution)}")
        if (optionScore > 0) reasons.add("选项标记${features.optionMarkerCount}个")
        
        // 5.5. 短行簇检测（选项结构识别）- 完全禁用，避免误识别
        // 短行簇检测已经被证明会导致大量误识别，暂时完全禁用
        val clusterScore = 0.0
        totalScore += clusterScore
        
        // 6. 垂直对齐分数（越高越好）
        // 只有在有至少2个选项标记时才给分，避免误识别
        val alignmentScore = if (hasOptions) {
            features.bboxVerticalAlignmentScore * 100.0
        } else {
            0.0
        }
        totalScore += alignmentScore * weights.bboxVerticalAlignmentScore
        if (alignmentScore > 50) reasons.add("选项对齐良好(${String.format("%.1f", features.bboxVerticalAlignmentScore * 100)}%)")
        
        // 7. OCR置信度（没有选项标记时权重降低）
        val confidenceScore = features.ocrConfidenceMean * 100.0
        totalScore += confidenceScore * weights.ocrConfidenceMean * baseFeatureWeight
        if (confidenceScore > 70) reasons.add("OCR识别质量高")
        
        // 8. 表情/标点比例（应该较低，没有选项标记时权重降低）
        val punctScore = when {
            features.emojiOrPunctRatio < 0.1 -> 100.0
            features.emojiOrPunctRatio < 0.2 -> 50.0
            else -> 0.0
        }
        totalScore += punctScore * weights.emojiOrPunctRatio * baseFeatureWeight
        if (punctScore > 0) reasons.add("标点符号比例合理")
        
        // 9. 题目关键词
        // 有至少2个选项标记时，普通关键词给分
        // 或者有明确题型标记时，即使只有1个选项标记也给分
        val keywordScore = if (hasOptions) {
            // 有至少2个选项标记时，普通关键词给分
            if (features.hasQuestionKeywords) 100.0 else 0.0
        } else {
            // 没有至少2个选项标记时，关键词不给分（在QuestionDetector中会单独处理题型标记的情况）
            0.0
        }
        val keywordContribution = keywordScore * weights.hasQuestionKeywords
        totalScore += keywordContribution
        Log.d(TAG, "  题目关键词: ${features.hasQuestionKeywords} -> 得分${String.format("%.1f", keywordScore)}, 权重${weights.hasQuestionKeywords}, 贡献${String.format("%.2f", keywordContribution)}")
        if (keywordScore > 0) reasons.add("包含题目关键词")
        
        // 移除特殊加分逻辑，避免在没有选项标记时给额外分数
        
        // 10. 数学符号（可选，没有选项标记时权重降低）
        val mathScore = if (features.hasMathSymbols) 50.0 else 0.0
        totalScore += mathScore * weights.hasMathSymbols * baseFeatureWeight
        if (mathScore > 0) reasons.add("包含数学符号")
        
        // 11. 上半部分长行比例（题干通常在顶部）
        // 只有在有至少2个选项标记时才给分，避免误识别
        val topHalfScore = if (hasOptions) {
            when {
                features.topHalfLongLineRatio > 0.5 -> 100.0
                features.topHalfLongLineRatio > 0.3 -> 50.0
                else -> 0.0
            }
        } else {
            0.0  // 没有选项标记时，不给分
        }
        totalScore += topHalfScore * weights.topHalfLongLineRatio
        if (topHalfScore > 0) reasons.add("上半部分有长行(题干)")
        
        // 12. 下半部分短行（选项通常在底部）
        // 注意：只有在有至少2个选项标记时才给分，避免误识别
        val bottomHalfScore = if (hasOptions) {
            when {
                features.bottomHalfShortLines >= 3 -> 100.0
                features.bottomHalfShortLines >= 2 -> 50.0
                else -> 0.0
            }
        } else {
            0.0  // 没有选项标记时，不给分
        }
        totalScore += bottomHalfScore * weights.bottomHalfShortLines
        if (bottomHalfScore > 0) reasons.add("下半部分有${features.bottomHalfShortLines}个短行(选项)")
        
        // 13. 替代标记（特殊场景）
        // 只有在有至少2个选项标记时才给分
        val altMarkerScore = if (features.hasAlternativeMarkers && hasOptions) {
            50.0
        } else {
            0.0
        }
        totalScore += altMarkerScore * weights.hasAlternativeMarkers
        if (altMarkerScore > 0) reasons.add("检测到替代标记")
        
        // 14. 分隔符（可能表示选项）
        // 只有在有至少2个选项标记时才给分
        val separatorScore = if (features.hasSeparatorMarkers && hasOptions) {
            30.0
        } else {
            0.0
        }
        totalScore += separatorScore * weights.hasSeparatorMarkers
        if (separatorScore > 0) reasons.add("检测到分隔符")
        
        // 15. 选项左对齐度（布局/视觉特征）
        // 只有在有至少2个选项标记时才给分，避免误识别
        val leftAlignScore = if (hasOptions) {
            features.optionLeftAlignmentScore * 100.0
        } else {
            0.0
        }
        totalScore += leftAlignScore * 0.15  // 15%权重
        if (leftAlignScore > 50) reasons.add("选项左对齐良好(${String.format("%.1f", features.optionLeftAlignmentScore * 100)}%)")
        
        // 16. 选项间距一致性（布局/视觉特征）
        // 只有在有至少2个选项标记时才给分
        val spacingScore = if (hasOptions) {
            features.optionSpacingConsistency * 80.0
        } else {
            0.0
        }
        totalScore += spacingScore * 0.12  // 12%权重
        if (spacingScore > 40) reasons.add("选项间距一致(${String.format("%.1f", features.optionSpacingConsistency * 100)}%)")
        
        // 17. 文本块大小一致性（布局/视觉特征）
        // 只有在有至少2个选项标记时才给分
        val sizeConsistencyScore = if (hasOptions) {
            features.blockSizeConsistency * 60.0
        } else {
            0.0
        }
        totalScore += sizeConsistencyScore * 0.10  // 10%权重
        if (sizeConsistencyScore > 30) reasons.add("文本块大小一致(${String.format("%.1f", features.blockSizeConsistency * 100)}%)")
        
        // 18. 题目与选项位置分离度（布局/视觉特征）
        // 只有在有至少2个选项标记时才给分，避免误识别（这是关键修复！）
        val separationScore = if (hasOptions) {
            features.questionOptionSeparation * 100.0
        } else {
            0.0  // 没有至少2个选项标记时，不给分
        }
        totalScore += separationScore * 0.20  // 20%权重（重要特征）
        if (separationScore > 50) reasons.add("题目与选项位置分离明显(${String.format("%.1f", features.questionOptionSeparation * 100)}%)")
        
        // 归一化到0-100
        val normalizedScore = min(100.0, max(0.0, totalScore))
        
        Log.d(TAG, "========== 打分完成 ==========")
        Log.d(TAG, "原始总分: ${String.format("%.2f", totalScore)}")
        Log.d(TAG, "归一化分数: ${String.format("%.2f", normalizedScore)}")
        Log.d(TAG, "阈值: auto_add=${weights.thresholdAuto}, confirm=${weights.thresholdConfirm}")
        
        // 决策：核心原则 - 只有选项标记是题目的核心特征
        // 1. 有选项标记（>=2个）：正常阈值
        // 2. 有1个选项标记：提高阈值
        // 3. 没有选项标记：大幅提高阈值（需要非常强的其他特征）
        val decision = when {
            // 有2个以上选项标记：正常阈值（短行簇不算核心信号）
            features.optionMarkerCount >= 2 -> {
                val decision = when {
                    normalizedScore >= weights.thresholdAuto -> "auto_add"
                    normalizedScore >= weights.thresholdConfirm -> "confirm"
                    else -> "ignore"
                }
                Log.d(TAG, "决策: $decision (选项标记>=2, 分数${String.format("%.2f", normalizedScore)})")
                decision
            }
            // 有1个选项标记：提高阈值
            features.optionMarkerCount == 1 -> {
                val adjustedAuto = weights.thresholdAuto + 10.0
                val adjustedConfirm = weights.thresholdConfirm + 10.0
                val decision = when {
                    normalizedScore >= adjustedAuto -> "auto_add"
                    normalizedScore >= adjustedConfirm -> "confirm"
                    // 有题型标记时，稍微放宽
                    features.hasQuestionKeywords && normalizedScore >= 25.0 -> "confirm"
                    else -> "ignore"
                }
                Log.d(TAG, "决策: $decision (选项标记=1, 分数${String.format("%.2f", normalizedScore)}, 调整后阈值: auto=${String.format("%.2f", adjustedAuto)}, confirm=${String.format("%.2f", adjustedConfirm)})")
                decision
            }
            // 没有选项标记：检查是否有强布局特征（部分遮挡时可能选项标记识别不出来）
            else -> {
                // 没有选项标记时，如果布局特征很强，也应该给分
                // 检查是否有强布局特征（短行簇、对齐、间距等）
                val hasStrongLayout = hasStrongLayoutFeatures
                
                val decision = when {
                    // 有强布局特征时，降低阈值（部分遮挡时可能选项标记识别不出来）
                    hasStrongLayout -> {
                        when {
                            normalizedScore >= 50.0 -> "auto_add"  // 从90降低到50
                            normalizedScore >= 30.0 -> "confirm"   // 从60降低到30
                            else -> "ignore"
                        }
                    }
                    // 没有强布局特征时，需要非常高的分数
                    else -> {
                        when {
                            normalizedScore >= 90.0 -> "auto_add"
                            normalizedScore >= 60.0 -> "confirm"
                            else -> "ignore"
                        }
                    }
                }
                Log.d(TAG, "决策: $decision (选项标记=0, 分数${String.format("%.2f", normalizedScore)}, 强布局特征=$hasStrongLayout)")
                decision
            }
        }
        
        Log.d(TAG, "最终决策: $decision")
        Log.d(TAG, "==================================")
        
        return ScoringResult(
            score = normalizedScore,
            decision = decision,
            reasons = reasons
        )
    }
}

/**
 * 打分权重配置
 */
data class ScoringWeights(
    val numTextBlocks: Double = 0.05,
    val numLines: Double = 0.05,
    val avgLineLength: Double = 0.05,
    val shortLineRatio: Double = 0.08,
    val optionMarkerCount: Double = 0.15,  // 最重要
    val bboxVerticalAlignmentScore: Double = 0.12,
    val ocrConfidenceMean: Double = 0.05,
    val emojiOrPunctRatio: Double = 0.05,
    val hasQuestionKeywords: Double = 0.15,  // 最重要
    val hasMathSymbols: Double = 0.03,
    val topHalfLongLineRatio: Double = 0.08,
    val bottomHalfShortLines: Double = 0.10,
    val hasAlternativeMarkers: Double = 0.03,
    val hasSeparatorMarkers: Double = 0.02,
    
    // 阈值
    val thresholdAuto: Double = 40.0,      // 自动加入（进一步降低以提高识别率）
    val thresholdConfirm: Double = 15.0    // 需要确认（进一步降低以提高识别率）
) {
    companion object {
        fun default() = ScoringWeights()
    }
}

/**
 * 打分结果
 */
data class ScoringResult(
    val score: Double,           // 0-100
    val decision: String,        // "auto_add" | "confirm" | "ignore"
    val reasons: List<String>   // 打分明细
)

