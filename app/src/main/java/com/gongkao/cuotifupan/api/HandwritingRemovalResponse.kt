package com.gongkao.cuotifupan.api

import com.google.gson.annotations.SerializedName

/**
 * 手写笔记清除API响应
 * 符合新接口规范：/api/handwriting/remove
 */
data class HandwritingRemovalResponse(
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("data")
    val data: RemovalData? = null,
    
    @SerializedName("error")
    val error: String? = null,
    
    @SerializedName("code")
    val code: Int? = null
)

/**
 * 手写擦除数据
 */
data class RemovalData(
    @SerializedName("image_url")
    val imageUrl: String? = null,
    
    @SerializedName("image_base64")
    val imageBase64: String? = null,
    
    @SerializedName("image_data_url")
    val imageDataUrl: String? = null,
    
    @SerializedName("image_format")
    val imageFormat: String? = null,  // "jpeg" 或 "png"
    
    @SerializedName("filename")
    val filename: String? = null,
    
    @SerializedName("provider")
    val provider: String? = null,  // "youdao" 或 "textin"
    
    @SerializedName("quota_type")
    val quotaType: String? = null,  // "free" 或 "pro"
    
    @SerializedName("remaining_quota")
    val remainingQuota: Int? = null,  // 剩余使用次数配额
    
    @SerializedName("used_count")
    val usedCount: Int? = null,  // 已使用次数
    
    @SerializedName("total_quota")
    val totalQuota: Int? = null,  // 总配额（仅当 quota_type="free" 时存在）
    
    @SerializedName("monthly_quota")
    val monthlyQuota: Int? = null,  // 每月总配额（仅当 quota_type="pro" 时存在）
    
    @SerializedName("next_period_quota")
    val nextPeriodQuota: Int? = null  // 下一周期预付费配额（仅Pro配额）
)

