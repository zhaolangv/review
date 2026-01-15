package com.gongkao.cuotifupan.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 兑换码相关 API 接口
 */
interface RedemptionApiService {
    
    /**
     * 验证兑换码（检查是否有效，但不激活）
     * 
     * @param code 兑换码
     * @param deviceId 设备ID
     */
    @POST("api/redeem/verify")
    suspend fun verifyCode(
        @Body request: VerifyCodeRequest
    ): Response<VerifyCodeResponse>
    
    /**
     * 激活兑换码
     * 
     * @param code 兑换码
     * @param deviceId 设备ID
     */
    @POST("api/redeem/activate")
    suspend fun activateCode(
        @Body request: ActivateCodeRequest
    ): Response<ActivateCodeResponse>
    
    /**
     * 查询 Pro 状态
     * 
     * @param deviceId 设备ID
     */
    @GET("api/pro/status")
    suspend fun getProStatus(
        @Query("device_id") deviceId: String
    ): Response<ProStatusResponse>
}

/**
 * 验证兑换码请求
 */
data class VerifyCodeRequest(
    val code: String,
    val device_id: String
)

/**
 * 激活兑换码请求
 */
data class ActivateCodeRequest(
    val code: String,
    val device_id: String
)

/**
 * 验证兑换码响应
 */
data class VerifyCodeResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val data: VerifyCodeData? = null
)

data class VerifyCodeData(
    val code: String,
    val valid: Boolean,
    val expires_at: String? = null,
    val duration_days: Int? = null,
    val pro_status: ProStatus? = null
)

/**
 * 激活兑换码响应
 */
data class ActivateCodeResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val data: ActivateCodeData? = null
)

data class ActivateCodeData(
    val pro_status: ProStatus
)

/**
 * Pro 状态响应
 */
data class ProStatusResponse(
    val success: Boolean,
    val data: ProStatus? = null
)

/**
 * Pro 状态信息
 */
data class ProStatus(
    val is_pro: Boolean,
    val activated_at: String? = null,
    val expires_at: String? = null,
    val days_remaining: Int? = null,
    val monthly_quota: Int? = null,
    val remaining_quota: Int? = null,
    val used_count: Int? = null,
    val next_period_quota: Int? = null,
    val tier: String? = null,
    val tier_name: String? = null,
    val current_month: String? = null,
    val free_quota: FreeQuotaInfo? = null
)

/**
 * 免费额度信息（在Pro状态响应中）
 */
data class FreeQuotaInfo(
    val device_id: String? = null,
    val total_quota: Int = 0,
    val used_count: Int = 0,
    val remaining_quota: Int = 0,
    val is_available: Boolean = false,
    val first_used_at: String? = null,
    val last_used_at: String? = null
)

