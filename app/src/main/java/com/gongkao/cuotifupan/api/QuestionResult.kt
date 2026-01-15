package com.gongkao.cuotifupan.api

import com.google.gson.annotations.SerializedName

/**
 * 单道题的处理结果（异步接口返回的格式）
 * 与 QuestionContentResponse 不同，这个类包含更详细的处理信息
 */
data class QuestionResult(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("question_text")
    val questionText: String? = null,  // ⚠️ 可空，可能为 null
    
    @SerializedName("options")
    val options: List<String>? = null,  // ⚠️ 可空，可能为空列表或 null
    
    @SerializedName("question_type")
    val questionType: String? = null,  // ⚠️ 可空
    
    @SerializedName("preliminary_answer")
    val preliminaryAnswer: String? = null,  // ⚠️ 可空，OCR 无法识别选项时可能为 null
    
    @SerializedName("answer_reason")
    val answerReason: String? = null,  // ⚠️ 可空
    
    @SerializedName("raw_text")
    val rawText: String? = null,  // ⚠️ OCR 原始文本，可空
    
    @SerializedName("ocr_time")
    val ocrTime: Double = 0.0,
    
    @SerializedName("ai_time")
    val aiTime: Double = 0.0,
    
    @SerializedName("total_time")
    val totalTime: Double = 0.0,
    
    @SerializedName("cost")
    val cost: Double = 0.0,
    
    @SerializedName("input_tokens")
    val inputTokens: Int = 0,
    
    @SerializedName("output_tokens")
    val outputTokens: Int = 0,
    
    @SerializedName("total_tokens")
    val totalTokens: Int = 0,
    
    @SerializedName("error")
    val error: String? = null  // ⚠️ 错误信息（处理失败时才有，是字符串不是对象）
)
