package com.gongkao.cuotifupan.api

import com.google.gson.annotations.SerializedName

/**
 * 异步任务提交响应
 */
data class AsyncTaskSubmitResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("task_id")
    val taskId: String,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("status_url")
    val statusUrl: String?,
    
    @SerializedName("result_url")
    val resultUrl: String?
)

/**
 * 任务进度信息
 */
data class TaskProgress(
    @SerializedName("total")
    val total: Int,
    
    @SerializedName("completed")
    val completed: Int,
    
    @SerializedName("failed")
    val failed: Int,
    
    @SerializedName("current_item")
    val currentItem: String?
)

/**
 * 任务状态信息
 */
data class TaskStatusInfo(
    @SerializedName("id")
    val id: String,
    
    @SerializedName("type")
    val type: String,
    
    @SerializedName("status")
    val status: String, // pending, processing, completed, failed
    
    @SerializedName("progress")
    val progress: TaskProgress,
    
    @SerializedName("created_at")
    val createdAt: String?,
    
    @SerializedName("started_at")
    val startedAt: String?,
    
    @SerializedName("completed_at")
    val completedAt: String?,
    
    @SerializedName("total_time")
    val totalTime: Double?,
    
    @SerializedName("has_result")
    val hasResult: Boolean,
    
    @SerializedName("has_error")
    val hasError: Boolean
)

/**
 * 任务状态查询响应
 */
data class TaskStatusResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("task")
    val task: TaskStatusInfo
)

/**
 * 任务结果响应
 */
data class TaskResultResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("result")
    val result: BatchQuestionResponse?,
    
    @SerializedName("message")
    val message: String?,
    
    @SerializedName("error")
    val error: String?,
    
    @SerializedName("progress")
    val progress: TaskProgress?
)
