package com.gongkao.cuotifupan.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.*

/**
 * Pro 服务管理器
 * 管理 Pro 服务的激活状态、到期时间等
 */
object ProManager {
    
    private const val PREFS_NAME = "pro_preferences"
    private const val KEY_IS_PRO = "is_pro"
    private const val KEY_ACTIVATED_AT = "activated_at"
    private const val KEY_EXPIRES_AT = "expires_at"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_MONTHLY_QUOTA = "monthly_quota"
    private const val KEY_REMAINING_QUOTA = "remaining_quota"
    private const val KEY_USED_COUNT = "used_count"
    private const val KEY_NEXT_PERIOD_QUOTA = "next_period_quota"
    private const val KEY_TIER = "tier"
    private const val KEY_TIER_NAME = "tier_name"
    private const val KEY_QUOTA_UPDATED_AT = "quota_updated_at"
    
    // 免费额度相关字段
    private const val KEY_FREE_TOTAL_QUOTA = "free_total_quota"
    private const val KEY_FREE_USED_COUNT = "free_used_count"
    private const val KEY_FREE_REMAINING_QUOTA = "free_remaining_quota"
    private const val KEY_FREE_IS_AVAILABLE = "free_is_available"
    
    // 兑换码领取链接
    private const val KEY_REDEEM_CODE_URL = "redeem_code_url"
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
    
    /**
     * 解析 ISO 8601 日期字符串（支持多种格式）
     * 支持的格式：
     * - yyyy-MM-dd'T'HH:mm:ss
     * - yyyy-MM-dd'T'HH:mm:ss.SSS
     * - yyyy-MM-dd'T'HH:mm:ss.SSSSSS (微秒)
     * - yyyy-MM-dd'T'HH:mm:ss+00:00 (时区)
     * - yyyy-MM-dd'T'HH:mm:ss.SSS+00:00
     * - yyyy-MM-dd'T'HH:mm:ss.SSSSSS+00:00
     */
    private fun parseDate(dateString: String?): Date? {
        if (dateString.isNullOrBlank()) return null
        
        // 尝试多种日期格式
        val formats = listOf(
            // 基础格式
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            // 带微秒
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            // 带时区偏移（Z 或 +HH:mm 或 -HH:mm）
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSZ",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSX",
            "yyyy-MM-dd'T'HH:mm:ssXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX"
        )
        
        // 预处理：移除微秒部分（如果存在），因为 SimpleDateFormat 不支持微秒
        // 例如：2026-03-09T15:59:59.999000+00:00 -> 2026-03-09T15:59:59.999+00:00
        var processedDateString = dateString
        val microsecondsPattern = Regex("\\.(\\d{3})(\\d{3})([\\+\\-]|Z|$)")
        processedDateString = microsecondsPattern.replace(processedDateString) { matchResult ->
            val milliseconds = matchResult.groupValues[1]
            val zone = matchResult.groupValues[3]
            ".$milliseconds$zone"
        }
        
        // 预处理：将 'Z' 转换为 '+0000'
        processedDateString = processedDateString.replace("Z", "+0000")
        
        // 尝试解析
        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = sdf.parse(processedDateString)
                if (date != null) {
                    Log.d("ProManager", "成功解析日期: $dateString -> $date (格式: $format)")
                    return date
                }
            } catch (e: Exception) {
                // 继续尝试下一个格式
            }
        }
        
        Log.e("ProManager", "无法解析日期格式: $dateString")
        return null
    }
    
    /**
     * 获取 SharedPreferences
     */
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 检查是否为 Pro 用户
     */
    fun isPro(context: Context): Boolean {
        val prefs = getPrefs(context)
        val isPro = prefs.getBoolean(KEY_IS_PRO, false)
        
        if (!isPro) {
            return false
        }
        
        // 检查是否过期
        val expiresAt = prefs.getString(KEY_EXPIRES_AT, null)
        if (expiresAt != null) {
            val expiresDate = parseDate(expiresAt)
            if (expiresDate != null && expiresDate.before(Date())) {
                // 已过期，清除 Pro 状态
                Log.d("ProManager", "Pro 服务已过期，清除状态")
                clearProStatus(context)
                return false
            }
        }
        
        return true
    }
    
    /**
     * 激活 Pro 服务
     */
    fun activatePro(context: Context, activatedAt: String, expiresAt: String) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putBoolean(KEY_IS_PRO, true)
            putString(KEY_ACTIVATED_AT, activatedAt)
            putString(KEY_EXPIRES_AT, expiresAt)
            apply()
        }
        Log.d("ProManager", "Pro 服务已激活: $activatedAt -> $expiresAt")
    }
    
    /**
     * 清除 Pro 状态（仅清除 Pro 相关字段，保留配额信息）
     */
    fun clearProStatusOnly(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            remove(KEY_IS_PRO)
            remove(KEY_ACTIVATED_AT)
            remove(KEY_EXPIRES_AT)
            apply()
        }
        Log.d("ProManager", "Pro 状态已清除（保留配额信息）")
    }
    
    /**
     * 清除 Pro 状态（包括配额信息）
     */
    fun clearProStatus(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            remove(KEY_IS_PRO)
            remove(KEY_ACTIVATED_AT)
            remove(KEY_EXPIRES_AT)
            // 清除配额信息（Pro状态失效时，配额也应该清除）
            remove(KEY_MONTHLY_QUOTA)
            remove(KEY_REMAINING_QUOTA)
            remove(KEY_USED_COUNT)
            remove(KEY_NEXT_PERIOD_QUOTA)
            remove(KEY_TIER)
            remove(KEY_TIER_NAME)
            apply()
        }
        Log.d("ProManager", "Pro 状态已清除（包括配额信息）")
    }
    
    /**
     * 获取激活时间
     */
    fun getActivatedAt(context: Context): String? {
        return getPrefs(context).getString(KEY_ACTIVATED_AT, null)
    }
    
    /**
     * 获取过期时间
     */
    fun getExpiresAt(context: Context): String? {
        return getPrefs(context).getString(KEY_EXPIRES_AT, null)
    }
    
    /**
     * 获取剩余天数
     */
    fun getDaysRemaining(context: Context): Int {
        val expiresAt = getExpiresAt(context) ?: return 0
        val expiresDate = parseDate(expiresAt)
        if (expiresDate != null) {
            val now = Date()
            val diff = expiresDate.time - now.time
            val days = (diff / (1000 * 60 * 60 * 24)).toInt()
            return maxOf(0, days)
        }
        return 0
    }
    
    /**
     * 格式化过期时间显示
     */
    fun getExpiresAtFormatted(context: Context): String {
        val expiresAt = getExpiresAt(context) ?: return "未知"
        val date = parseDate(expiresAt)
        if (date != null) {
            val displayFormat = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault())
            return displayFormat.format(date)
        }
        return expiresAt
    }
    
    /**
     * 更新配额信息
     */
    fun updateQuota(
        context: Context, 
        monthlyQuota: Int, 
        remainingQuota: Int, 
        usedCount: Int,
        nextPeriodQuota: Int = 0,
        tier: String? = null,
        tierName: String? = null
    ) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putInt(KEY_MONTHLY_QUOTA, monthlyQuota)
            putInt(KEY_REMAINING_QUOTA, remainingQuota)
            putInt(KEY_USED_COUNT, usedCount)
            putInt(KEY_NEXT_PERIOD_QUOTA, nextPeriodQuota)
            if (tier != null) putString(KEY_TIER, tier)
            if (tierName != null) putString(KEY_TIER_NAME, tierName)
            putLong(KEY_QUOTA_UPDATED_AT, System.currentTimeMillis())
            apply()
        }
        Log.d("ProManager", "配额已更新: 月配额=$monthlyQuota, 剩余=$remainingQuota, 已用=$usedCount, 下一周期=$nextPeriodQuota")
    }
    
    /**
     * 获取月配额
     */
    fun getMonthlyQuota(context: Context): Int {
        return getPrefs(context).getInt(KEY_MONTHLY_QUOTA, 0)
    }
    
    /**
     * 获取剩余配额
     */
    fun getRemainingQuota(context: Context): Int {
        return getPrefs(context).getInt(KEY_REMAINING_QUOTA, 0)
    }
    
    /**
     * 获取已使用次数
     */
    fun getUsedCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_USED_COUNT, 0)
    }
    
    /**
     * 获取配额更新时间
     */
    fun getQuotaUpdatedAt(context: Context): Long {
        return getPrefs(context).getLong(KEY_QUOTA_UPDATED_AT, 0)
    }
    
    /**
     * 获取配额显示字符串
     */
    fun getQuotaDisplay(context: Context): String {
        val monthly = getMonthlyQuota(context)
        val remaining = getRemainingQuota(context)
        val used = getUsedCount(context)
        
        if (monthly <= 0) {
            return "配额: 未设置"
        }
        
        return "本月配额: $remaining/$monthly (已用 $used 次)"
    }
    
    /**
     * 获取下一周期配额
     */
    fun getNextPeriodQuota(context: Context): Int {
        return getPrefs(context).getInt(KEY_NEXT_PERIOD_QUOTA, 0)
    }
    
    /**
     * 获取套餐等级
     */
    fun getTier(context: Context): String? {
        return getPrefs(context).getString(KEY_TIER, null)
    }
    
    /**
     * 获取套餐名称
     */
    fun getTierName(context: Context): String? {
        return getPrefs(context).getString(KEY_TIER_NAME, null)
    }
    
    /**
     * 清除配额信息
     */
    fun clearQuota(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            remove(KEY_MONTHLY_QUOTA)
            remove(KEY_REMAINING_QUOTA)
            remove(KEY_USED_COUNT)
            remove(KEY_NEXT_PERIOD_QUOTA)
            remove(KEY_TIER)
            remove(KEY_TIER_NAME)
            remove(KEY_QUOTA_UPDATED_AT)
            apply()
        }
        Log.d("ProManager", "配额信息已清除")
    }
    
    /**
     * 更新免费额度信息
     */
    fun updateFreeQuota(
        context: Context,
        totalQuota: Int,
        usedCount: Int,
        remainingQuota: Int,
        isAvailable: Boolean
    ) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            putInt(KEY_FREE_TOTAL_QUOTA, totalQuota)
            putInt(KEY_FREE_USED_COUNT, usedCount)
            putInt(KEY_FREE_REMAINING_QUOTA, remainingQuota)
            putBoolean(KEY_FREE_IS_AVAILABLE, isAvailable)
            apply()
        }
        Log.d("ProManager", "免费额度已更新: 总配额=$totalQuota, 已用=$usedCount, 剩余=$remainingQuota, 可用=$isAvailable")
    }
    
    /**
     * 获取免费额度总配额
     */
    fun getFreeTotalQuota(context: Context): Int {
        return getPrefs(context).getInt(KEY_FREE_TOTAL_QUOTA, 0)
    }
    
    /**
     * 获取免费额度已使用次数
     */
    fun getFreeUsedCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_FREE_USED_COUNT, 0)
    }
    
    /**
     * 获取免费额度剩余配额
     */
    fun getFreeRemainingQuota(context: Context): Int {
        return getPrefs(context).getInt(KEY_FREE_REMAINING_QUOTA, 0)
    }
    
    /**
     * 检查免费额度是否可用
     */
    fun isFreeQuotaAvailable(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_FREE_IS_AVAILABLE, false)
    }
    
    /**
     * 获取当前可用配额信息（优先返回免费额度，否则返回Pro配额）
     * @return Pair<类型, Pair<剩余配额, 总配额>>，类型为 "free"、"pro" 或 "none"
     */
    fun getCurrentQuotaInfo(context: Context): Triple<String, Int, Int> {
        // 优先检查免费额度
        if (isFreeQuotaAvailable(context)) {
            val remaining = getFreeRemainingQuota(context)
            val total = getFreeTotalQuota(context)
            if (remaining > 0) {
                return Triple("free", remaining, total)
            }
        }
        
        // 检查Pro配额
        if (isPro(context)) {
            val remaining = getRemainingQuota(context)
            val monthly = getMonthlyQuota(context)
            if (remaining > 0) {
                return Triple("pro", remaining, monthly)
            }
        }
        
        // 没有可用配额
        return Triple("none", 0, 0)
    }
    
    /**
     * 保存兑换码领取链接
     */
    fun setRedeemCodeUrl(context: Context, url: String?) {
        val prefs = getPrefs(context)
        prefs.edit().apply {
            if (url != null && url.isNotBlank()) {
                putString(KEY_REDEEM_CODE_URL, url)
            } else {
                remove(KEY_REDEEM_CODE_URL)
            }
            apply()
        }
        Log.d("ProManager", "兑换码链接已更新: $url")
    }
    
    /**
     * 获取兑换码领取链接
     */
    fun getRedeemCodeUrl(context: Context): String? {
        return getPrefs(context).getString(KEY_REDEEM_CODE_URL, null)
    }
}

