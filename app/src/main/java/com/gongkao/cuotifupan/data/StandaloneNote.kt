package com.gongkao.cuotifupan.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 独立笔记实体（不依赖题目）
 */
@Entity(tableName = "standalone_notes")
data class StandaloneNote(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    // 笔记内容
    val content: String,
    
    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),
    
    // 更新时间
    val updatedAt: Long = System.currentTimeMillis(),
    
    // 用户标签（JSON字符串，如 ["学习", "重要"]）
    val tags: String = "",
    
    // 关联的题目ID（可选，如果有则说明这个笔记来自某个题目）
    val questionId: String? = null,
    
    // 是否收藏
    val isFavorite: Boolean = false
)

