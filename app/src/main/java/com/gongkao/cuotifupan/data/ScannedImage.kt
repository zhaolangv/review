package com.gongkao.cuotifupan.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 扫描过的图片记录
 */
@Entity(tableName = "scanned_images")
data class ScannedImage(
    @PrimaryKey
    val imagePath: String,
    
    // 文件名
    val fileName: String,
    
    // 文件大小（用于去重）
    val fileSize: Long,
    
    // 是否是题目
    val isQuestion: Boolean = false,
    
    // 扫描时间
    val scannedAt: Long = System.currentTimeMillis(),
    
    // MediaStore ID（用于快速检查）
    val mediaStoreId: Long = 0
)

