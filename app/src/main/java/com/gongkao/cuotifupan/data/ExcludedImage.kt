package com.gongkao.cuotifupan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已排除的图片实体（检测过但不是题目的图片）
 */
@Entity(tableName = "excluded_images")
data class ExcludedImage(
    @PrimaryKey
    val imagePath: String,
    
    // 排除时间
    val excludedAt: Long = System.currentTimeMillis(),
    
    // 排除原因（可选）
    val reason: String = ""
)

