package com.gongkao.cuotifupan.api

import com.google.gson.annotations.SerializedName

/**
 * 批量题目响应项（兼容旧格式）
 * 注意：异步接口返回的是 QuestionResult，不是这个格式
 */
data class BatchQuestionResult(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("question")
    val question: QuestionContentResponse?,
    
    @SerializedName("error")
    val error: BatchError?
)

/**
 * 批量错误信息（兼容旧格式）
 */
data class BatchError(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("message")
    val message: String
)

/**
 * 批量题目响应（异步接口返回的格式）
 * 包含详细的统计信息和结果列表
 */
data class BatchQuestionResponse(
    @SerializedName("results")
    val results: List<QuestionResult>,  // ⚠️ 注意：异步接口返回的是 QuestionResult 列表
    
    @SerializedName("total")
    val total: Int,
    
    @SerializedName("success_count")
    val successCount: Int,
    
    @SerializedName("failed_count")
    val failedCount: Int,
    
    @SerializedName("total_time")
    val totalTime: Double = 0.0,
    
    @SerializedName("avg_time_per_question")
    val avgTimePerQuestion: Double = 0.0,
    
    @SerializedName("total_cost")
    val totalCost: Double = 0.0,
    
    // 兼容字段（文档中提到了 statistics）
    @SerializedName("statistics")
    val statistics: Statistics? = null
)

/**
 * 统计信息（可选，可能包含在 statistics 字段中）
 */
data class Statistics(
    @SerializedName("total")
    val total: Int,
    
    @SerializedName("success_count")
    val successCount: Int,
    
    @SerializedName("failed_count")
    val failedCount: Int,
    
    @SerializedName("total_time")
    val totalTime: Double,
    
    @SerializedName("avg_time_per_question")
    val avgTimePerQuestion: Double,
    
    @SerializedName("total_cost")
    val totalCost: Double
)
