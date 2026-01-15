package com.gongkao.cuotifupan.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 卡包/文件夹实体（类似 Anki 的 Deck）
 * 支持层级结构（通过 parentId 实现）
 */
@Entity(tableName = "flashcard_decks")
data class FlashcardDeck(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    // 卡包名称
    val name: String,
    
    // 父卡包 ID（null 表示根目录）
    val parentId: String? = null,
    
    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),
    
    // 更新时间
    val updatedAt: Long = System.currentTimeMillis(),
    
    // 卡包描述
    val description: String = "",
    
    // 排序顺序
    val sortOrder: Int = 0
)

