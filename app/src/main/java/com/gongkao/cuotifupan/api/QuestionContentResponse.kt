package com.gongkao.cuotifupan.api

import com.google.gson.annotations.SerializedName

/**
 * 题目内容响应（只包含题目内容，不包含答案和解析）
 * 用于 POST /api/questions/analyze 接口
 */
data class QuestionContentResponse(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("screenshot")
    val screenshot: String?,
    
    @SerializedName("raw_text")
    val rawText: String = "",  // 默认空字符串，避免 null
    
    @SerializedName("question_text")
    val questionText: String = "",  // 默认空字符串，避免 null
    
    @SerializedName("question_type")
    val questionType: String = "UNKNOWN",  // 默认值
    
    @SerializedName("options")
    val options: List<String> = emptyList(),  // 默认空列表，避免 null
    
    @SerializedName("ocr_confidence")
    val ocrConfidence: Double?,
    
    // ========== 缓存和去重相关字段 ==========
    @SerializedName("from_cache")
    val fromCache: Boolean?,
    
    @SerializedName("is_duplicate")
    val isDuplicate: Boolean?,
    
    @SerializedName("saved_to_db")
    val savedToDb: Boolean?,
    
    @SerializedName("similarity_score")
    val similarityScore: Double?,
    
    @SerializedName("matched_question_id")
    val matchedQuestionId: String?
)
