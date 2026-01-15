package com.gongkao.cuotifupan.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 数学练习会话实体
 */
@Entity(tableName = "math_practice_sessions")
data class MathPracticeSession(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    // 练习类型（如：两位数加减、三位数加法等）
    val practiceType: String,
    
    // 题目数量
    val questionCount: Int,
    
    // 开始时间
    val startTime: Long = System.currentTimeMillis(),
    
    // 结束时间
    val endTime: Long? = null,
    
    // 总用时（秒）
    val totalTimeSeconds: Int = 0,
    
    // 正确数量
    val correctCount: Int = 0,
    
    // 错误数量
    val wrongCount: Int = 0,
    
    // 题目数据（JSON字符串，包含所有题目和答案）
    val questionsData: String = "",
    
    // 是否完成
    val isCompleted: Boolean = false
)

