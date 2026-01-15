package com.gongkao.cuotifupan.api

import com.google.gson.annotations.SerializedName

/**
 * 版本检查API响应
 * 对应 GET /api/version 接口
 */
data class VersionResponse(
    @SerializedName("service")
    val service: String,
    
    @SerializedName("status")
    val status: String,
    
    @SerializedName("success")
    val success: Boolean,
    
    @SerializedName("timestamp")
    val timestamp: String,
    
    @SerializedName("update")
    val update: UpdateInfo,
    
    @SerializedName("version")
    val version: VersionInfo,
    
    // Pro 服务和配额信息
    @SerializedName("pro")
    val pro: ProInfo? = null,
    
    // 免费额度信息
    @SerializedName("free_quota")
    val freeQuota: FreeQuota? = null,
    
    // 兑换码领取链接（可选，如果未设置则为 null）
    @SerializedName("redeem_code_url")
    val redeemCodeUrl: String? = null
)

/**
 * Pro 服务信息
 */
data class ProInfo(
    @SerializedName("is_pro")
    val isPro: Boolean = false,
    
    @SerializedName("expires_at")
    val expiresAt: String? = null,
    
    @SerializedName("monthly_quota")
    val monthlyQuota: Int = 0,
    
    @SerializedName("remaining_quota")
    val remainingQuota: Int = 0,
    
    @SerializedName("used_count")
    val usedCount: Int = 0,
    
    @SerializedName("next_period_quota")
    val nextPeriodQuota: Int = 0,
    
    @SerializedName("tier")
    val tier: String? = null,
    
    @SerializedName("tier_name")
    val tierName: String? = null,
    
    @SerializedName("current_month")
    val currentMonth: String? = null
)

/**
 * 更新信息
 */
data class UpdateInfo(
    @SerializedName("download_url")
    val downloadUrl: String,
    
    @SerializedName("latest_version")
    val latestVersion: String,
    
    @SerializedName("release_notes")
    val releaseNotes: String,
    
    @SerializedName("required")
    val required: Boolean
)

/**
 * 免费额度信息
 */
data class FreeQuota(
    @SerializedName("total_quota")
    val totalQuota: Int = 0,
    
    @SerializedName("used_count")
    val usedCount: Int = 0,
    
    @SerializedName("remaining_quota")
    val remainingQuota: Int = 0,
    
    @SerializedName("is_available")
    val isAvailable: Boolean = false,
    
    @SerializedName("first_used_at")
    val firstUsedAt: String? = null,
    
    @SerializedName("last_used_at")
    val lastUsedAt: String? = null
)

/**
 * 版本信息
 */
data class VersionInfo(
    @SerializedName("api_version")
    val apiVersion: String,
    
    @SerializedName("app_version")
    val appVersion: String,
    
    @SerializedName("build_time")
    val buildTime: String,
    
    @SerializedName("flask_version")
    val flaskVersion: String,
    
    @SerializedName("platform")
    val platform: String,
    
    @SerializedName("platform_version")
    val platformVersion: String,
    
    @SerializedName("python_version")
    val pythonVersion: String
)

