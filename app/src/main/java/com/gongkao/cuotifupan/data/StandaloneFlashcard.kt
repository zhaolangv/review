package com.gongkao.cuotifupan.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 独立记忆卡片实体（不依赖题目）
 * 支持 Anki 风格的间隔重复算法
 */
@Entity(tableName = "standalone_flashcards")
data class StandaloneFlashcard(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    // 提示（正面）
    val front: String,
    
    // 内容（背面）
    val back: String,
    
    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),
    
    // 更新时间
    val updatedAt: Long = System.currentTimeMillis(),
    
    // 用户标签（JSON字符串，如 ["学习", "重要"]）
    val tags: String = "",
    
    // 关联的题目ID（可选，如果有则说明这个卡片来自某个题目）
    val questionId: String? = null,
    
    // 卡包ID（类似 Anki 的 Deck）
    val deckId: String? = null,
    
    // 是否收藏
    val isFavorite: Boolean = false,
    
    // 掌握状态：new, learning, review, relearning (兼容旧数据：unreviewed, mastered, not_mastered)
    val reviewState: String = "new",
    
    // 间隔重复相关字段（Anki 风格）
    // 下次复习时间（时间戳，毫秒）
    val nextReviewTime: Long = 0L,
    
    // 复习间隔（天数）
    val interval: Long = 0L,
    
    // 易用度因子（Ease Factor），默认 2.5（类似 Anki）
    val easeFactor: Double = 2.5,
    
    // 复习次数
    val reviewCount: Int = 0,
    
    // 连续正确次数（用于判断是否毕业）
    val consecutiveCorrect: Int = 0,
    
    // 图片支持（Anki 风格）
    // 正面图片路径
    val frontImagePath: String? = null,
    
    // 背面图片路径
    val backImagePath: String? = null
)

