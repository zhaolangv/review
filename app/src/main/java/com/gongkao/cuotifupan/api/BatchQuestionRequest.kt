package com.gongkao.cuotifupan.api

import com.google.gson.annotations.SerializedName

/**
 * 批量题目请求项
 * 根据后端API规范，每个图片项必须包含 filename 和 data 字段
 */
data class BatchQuestionItem(
    @SerializedName("filename")
    val filename: String,  // 图片文件名（必需）
    
    @SerializedName("data")
    val data: String  // 完整的 base64 data URL，格式：data:image/jpeg;base64,xxxxx（必需）
)

/**
 * 批量题目请求
 */
data class BatchQuestionRequest(
    @SerializedName("images")
    val images: List<BatchQuestionItem>,  // 图片数组
    
    @SerializedName("max_workers")
    val maxWorkers: Int = 10  // 最大并发工作线程数
)
