package com.gongkao.cuotifupan.util

import com.gongkao.cuotifupan.data.StandaloneFlashcard
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

/**
 * 间隔重复算法（类似 Anki 的 SM-2 算法）
 * 根据用户的回答质量，计算下次复习时间和间隔
 */
object SpacedRepetitionAlgorithm {
    
    // 难度级别
    enum class Difficulty {
        AGAIN,    // 再次（答错或忘记）
        HARD,     // 困难（记得但不熟练）
        GOOD,     // 良好（正常记忆）
        EASY      // 简单（很容易记住）
    }
    
    // 默认参数（类似 Anki）
    private const val INITIAL_INTERVAL_MINUTES = 1L      // 首次复习间隔：1分钟
    private const val MIN_INTERVAL_MINUTES = 10L         // 最小间隔：10分钟
    private const val EASY_BONUS = 1.3                   // Easy 的额外奖励因子
    private const val MIN_EASE_FACTOR = 1.3              // 最小易用度因子
    private const val MAX_EASE_FACTOR = 2.5              // 最大易用度因子（初始值）
    private const val EASE_FACTOR_DECREMENT = 0.15       // Hard 时减少的易用度
    private const val GRADUATING_INTERVAL_DAYS = 1L      // 毕业间隔：1天（连续答对几次后进入复习队列）
    private const val EASY_INTERVAL_DAYS = 4L            // Easy 的初始间隔：4天
    
    /**
     * 处理卡片复习，返回更新后的卡片
     */
    fun reviewCard(
        card: StandaloneFlashcard,
        difficulty: Difficulty,
        currentTime: Long = System.currentTimeMillis()
    ): StandaloneFlashcard {
        val oneMinute = 60 * 1000L
        val oneHour = 60 * oneMinute
        val oneDay = 24 * oneHour
        
        return when (card.reviewState) {
            "new" -> handleNewCard(card, difficulty, currentTime, oneMinute, oneDay)
            "learning" -> handleLearningCard(card, difficulty, currentTime, oneMinute, oneHour, oneDay)
            "review", "relearning" -> handleReviewCard(card, difficulty, currentTime, oneMinute, oneDay)
            else -> card // 兼容旧数据
        }
    }
    
    /**
     * 处理新卡片
     */
    private fun handleNewCard(
        card: StandaloneFlashcard,
        difficulty: Difficulty,
        currentTime: Long,
        oneMinute: Long,
        oneDay: Long
    ): StandaloneFlashcard {
        return when (difficulty) {
            Difficulty.AGAIN -> {
                // 重新学习，间隔 1 分钟
                card.copy(
                    reviewState = "learning",
                    nextReviewTime = currentTime + INITIAL_INTERVAL_MINUTES * oneMinute,
                    interval = INITIAL_INTERVAL_MINUTES,
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = 0,
                    updatedAt = currentTime
                )
            }
            Difficulty.HARD -> {
                // 困难，间隔 5 分钟，减少易用度
                val newEaseFactor = max(MIN_EASE_FACTOR, card.easeFactor - EASE_FACTOR_DECREMENT)
                card.copy(
                    reviewState = "learning",
                    nextReviewTime = currentTime + 5 * oneMinute,
                    interval = 5,
                    easeFactor = newEaseFactor,
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = 1,
                    updatedAt = currentTime
                )
            }
            Difficulty.GOOD -> {
                // 良好，间隔 10 分钟，进入学习队列
                card.copy(
                    reviewState = "learning",
                    nextReviewTime = currentTime + MIN_INTERVAL_MINUTES * oneMinute,
                    interval = MIN_INTERVAL_MINUTES,
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = 1,
                    updatedAt = currentTime
                )
            }
            Difficulty.EASY -> {
                // 简单，直接进入复习队列，间隔 4 天
                card.copy(
                    reviewState = "review",
                    nextReviewTime = currentTime + EASY_INTERVAL_DAYS * oneDay,
                    interval = EASY_INTERVAL_DAYS,
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = 1,
                    updatedAt = currentTime
                )
            }
        }
    }
    
    /**
     * 处理学习中的卡片
     */
    private fun handleLearningCard(
        card: StandaloneFlashcard,
        difficulty: Difficulty,
        currentTime: Long,
        oneMinute: Long,
        oneHour: Long,
        oneDay: Long
    ): StandaloneFlashcard {
        return when (difficulty) {
            Difficulty.AGAIN -> {
                // 重新学习，重置间隔
                card.copy(
                    reviewState = "learning",
                    nextReviewTime = currentTime + INITIAL_INTERVAL_MINUTES * oneMinute,
                    interval = INITIAL_INTERVAL_MINUTES,
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = 0,
                    updatedAt = currentTime
                )
            }
            Difficulty.HARD -> {
                // 困难，间隔 6 小时
                card.copy(
                    reviewState = "learning",
                    nextReviewTime = currentTime + 6 * oneHour,
                    interval = 6 * 60, // 分钟
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = card.consecutiveCorrect + 1,
                    updatedAt = currentTime
                )
            }
            Difficulty.GOOD -> {
                // 良好，如果连续答对 2 次，进入复习队列，否则继续学习
                val newConsecutiveCorrect = card.consecutiveCorrect + 1
                if (newConsecutiveCorrect >= 2) {
                    // 毕业，进入复习队列（1天 = 1440分钟）
                    val graduatingIntervalMinutes = GRADUATING_INTERVAL_DAYS * 24 * 60
                    card.copy(
                        reviewState = "review",
                        nextReviewTime = currentTime + GRADUATING_INTERVAL_DAYS * oneDay,
                        interval = graduatingIntervalMinutes,
                        reviewCount = card.reviewCount + 1,
                        consecutiveCorrect = newConsecutiveCorrect,
                        updatedAt = currentTime
                    )
                } else {
                    // 继续学习，间隔 1 小时
                    card.copy(
                        reviewState = "learning",
                        nextReviewTime = currentTime + oneHour,
                        interval = 60,
                        reviewCount = card.reviewCount + 1,
                        consecutiveCorrect = newConsecutiveCorrect,
                        updatedAt = currentTime
                    )
                }
            }
            Difficulty.EASY -> {
                // 简单，直接毕业，间隔 4 天
                val easyIntervalMinutes = EASY_INTERVAL_DAYS * 24 * 60
                card.copy(
                    reviewState = "review",
                    nextReviewTime = currentTime + EASY_INTERVAL_DAYS * oneDay,
                    interval = easyIntervalMinutes,
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = card.consecutiveCorrect + 1,
                    updatedAt = currentTime
                )
            }
        }
    }
    
    /**
     * 处理复习中的卡片（包括重新学习的卡片）
     */
    private fun handleReviewCard(
        card: StandaloneFlashcard,
        difficulty: Difficulty,
        currentTime: Long,
        oneMinute: Long,
        oneDay: Long
    ): StandaloneFlashcard {
        return when (difficulty) {
            Difficulty.AGAIN -> {
                // 答错，重新学习
                val newEaseFactor = max(MIN_EASE_FACTOR, card.easeFactor - 0.2)
                card.copy(
                    reviewState = "relearning",
                    nextReviewTime = currentTime + 10 * 60 * 1000L, // 10 分钟
                    interval = 10,
                    easeFactor = newEaseFactor,
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = 0,
                    updatedAt = currentTime
                )
            }
            Difficulty.HARD -> {
                // 困难，减少易用度，间隔缩短
                val newEaseFactor = max(MIN_EASE_FACTOR, card.easeFactor - EASE_FACTOR_DECREMENT)
                // interval 是分钟，转换为天数计算，再转回分钟
                val intervalDays = card.interval / (24.0 * 60.0)
                val newIntervalDays = max(0.1, intervalDays * newEaseFactor * 0.8) // 缩短 20%
                val newIntervalMinutes = (newIntervalDays * 24 * 60).roundToLong()
                card.copy(
                    reviewState = "review",
                    nextReviewTime = currentTime + newIntervalMinutes * oneMinute,
                    interval = newIntervalMinutes,
                    easeFactor = newEaseFactor,
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = card.consecutiveCorrect + 1,
                    updatedAt = currentTime
                )
            }
            Difficulty.GOOD -> {
                // 良好，正常间隔增长
                // interval 是分钟，转换为天数计算，再转回分钟
                val intervalDays = card.interval / (24.0 * 60.0)
                val newIntervalDays = intervalDays * card.easeFactor
                val newIntervalMinutes = (newIntervalDays * 24 * 60).roundToLong()
                card.copy(
                    reviewState = "review",
                    nextReviewTime = currentTime + newIntervalMinutes * oneMinute,
                    interval = newIntervalMinutes,
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = card.consecutiveCorrect + 1,
                    updatedAt = currentTime
                )
            }
            Difficulty.EASY -> {
                // 简单，增加易用度，间隔更长
                val newEaseFactor = min(MAX_EASE_FACTOR, card.easeFactor + 0.15)
                // interval 是分钟，转换为天数计算，再转回分钟
                val intervalDays = card.interval / (24.0 * 60.0)
                val newIntervalDays = intervalDays * newEaseFactor * EASY_BONUS
                val newIntervalMinutes = (newIntervalDays * 24.0 * 60.0).roundToLong()
                card.copy(
                    reviewState = "review",
                    nextReviewTime = currentTime + newIntervalMinutes * oneMinute,
                    interval = newIntervalMinutes,
                    easeFactor = newEaseFactor,
                    reviewCount = card.reviewCount + 1,
                    consecutiveCorrect = card.consecutiveCorrect + 1,
                    updatedAt = currentTime
                )
            }
        }
    }
    
    /**
     * 格式化间隔时间（用于显示）
     */
    fun formatInterval(intervalMinutes: Long): String {
        return when {
            intervalMinutes < 60 -> "${intervalMinutes}分钟"
            intervalMinutes < 1440 -> "${intervalMinutes / 60}小时"
            else -> "${intervalMinutes / 1440}天"
        }
    }
    
    /**
     * 格式化下次复习时间（用于显示）
     */
    fun formatNextReviewTime(nextReviewTime: Long): String {
        val now = System.currentTimeMillis()
        val diff = nextReviewTime - now
        
        return when {
            diff < 0 -> "已到期"
            diff < 60 * 1000 -> "即将到期"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟后"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时后"
            else -> "${diff / (24 * 60 * 60 * 1000)}天后"
        }
    }
}

