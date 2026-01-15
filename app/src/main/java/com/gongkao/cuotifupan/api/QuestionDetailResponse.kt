package com.gongkao.cuotifupan.api

import com.google.gson.annotations.SerializedName

/**
 * 题目详情响应（包含答案、解析、标签、知识点等）
 * 用于 GET /api/questions/{question_id}/detail 接口
 */
data class QuestionDetailResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("question_id")
    val questionId: String?,
    
    // ========== 多来源答案集合 ==========
    @SerializedName("answer_versions")
    val answerVersions: List<AnswerVersion>,
    
    // ========== 汇总/便捷字段 ==========
    @SerializedName("correct_answer")
    val correctAnswer: String?,
    
    @SerializedName("explanation")
    val explanation: String?,
    
    @SerializedName("tags")
    val tags: List<String>?,
    
    @SerializedName("knowledge_points")
    val knowledgePoints: List<String>?,
    
    // ========== 来源信息 ==========
    @SerializedName("source")
    val source: String?,
    
    @SerializedName("source_url")
    val sourceUrl: String?,
    
    @SerializedName("encountered_date")
    val encounteredDate: String?,
    
    // ========== 学习进度相关 ==========
    @SerializedName("is_error")
    val isError: Boolean?,
    
    @SerializedName("error_reason")
    val errorReason: String?,
    
    @SerializedName("difficulty")
    val difficulty: Int?,
    
    @SerializedName("time_spent")
    val timeSpent: Int?,
    
    @SerializedName("last_reviewed")
    val lastReviewed: String?,
    
    @SerializedName("attempts")
    val attempts: Int?,
    
    @SerializedName("spaced_repetition")
    val spacedRepetition: SpacedRepetition?,
    
    @SerializedName("user_note")
    val userNote: String?,
    
    @SerializedName("similar_questions")
    val similarQuestions: List<String>?,
    
    @SerializedName("priority")
    val priority: String?,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("updated_at")
    val updatedAt: String?
)

/**
 * 答案版本
 */
data class AnswerVersion(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("source_name")
    val sourceName: String,
    
    @SerializedName("source_type")
    val sourceType: String, // "机构" 或 "AI"
    
    @SerializedName("answer")
    val answer: String,
    
    @SerializedName("explanation")
    val explanation: String,
    
    @SerializedName("confidence")
    val confidence: Double,
    
    @SerializedName("is_user_preferred")
    val isUserPreferred: Boolean,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("updated_at")
    val updatedAt: String?
)

/**
 * 间隔重复数据
 */
data class SpacedRepetition(
    @SerializedName("ef")
    val ef: Double,
    
    @SerializedName("interval")
    val interval: Int,
    
    @SerializedName("repetitions")
    val repetitions: Int
)

