package com.gongkao.cuotifupan.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * 题目实体
 */
@Entity(tableName = "questions")
data class Question(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    
    // 原图路径（主要图片路径，如果擦写过则显示擦写后的图片，否则显示原图）
    val imagePath: String,
    
    // 原始图片路径（擦写前的原图，用于切换查看）
    val originalImagePath: String? = null,
    
    // 擦写后的图片路径（如果存在，说明已擦写过）
    val cleanedImagePath: String? = null,
    
    // 标注图层路径（PNG透明背景，独立于原图）
    val annotationPath: String? = null,
    
    // OCR识别的原始文本（优先使用后端返回的，如果没有则使用前端OCR的）
    val rawText: String,
    
    // 题干（优先使用后端返回的，如果没有则使用前端提取的）
    val questionText: String,
    
    // 前端OCR识别的原始文本（仅用于发送给后端进行去重检查，不用于显示）
    val frontendRawText: String? = null,
    
    // 选项（JSON字符串，如 ["A xxx", "B xxx"]）
    val options: String = "",
    
    // 创建时间
    val createdAt: Long = System.currentTimeMillis(),
    
    // 复盘状态：unreviewed, mastered, not_mastered
    val reviewState: String = "unreviewed",
    
    // 用户笔记
    val userNotes: String = "",
    
    // 置信度（判题规则输出）
    val confidence: Float = 0f,
    
    // 题目类型：TEXT（文字题）、GRAPHIC（图推题）、UNKNOWN（未知类型）
    val questionType: String = "TEXT",
    
    // 后端返回的题目ID（用于去重匹配）
    val backendQuestionId: String? = null,
    
    // 后端返回的完整题干（AI提取的，优先于前端OCR）
    val backendQuestionText: String? = null,
    
    // 答案是否已从后端加载
    val answerLoaded: Boolean = false,
    
    // 后端返回的正确答案
    val correctAnswer: String? = null,
    
    // 后端返回的解析
    val explanation: String? = null,
    
    // 用户标签（JSON字符串，如 ["行测-言语理解-语句衔接", "申论-材料解读"]）
    val tags: String = ""
)

