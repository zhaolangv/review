package com.gongkao.cuotifupan.util

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import android.util.Log
import com.gongkao.cuotifupan.api.ApiClient
import com.gongkao.cuotifupan.api.VersionResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * 版本检查器
 * 使用 Retrofit API 检查版本更新
 */
class VersionChecker(private val context: Context) {
    
    private val TAG = "VersionChecker"
    
    // 保存Activity引用，用于显示弹窗
    private var activity: Activity? = null
    
    /**
     * 设置Activity引用（可选，用于显示弹窗）
     */
    fun setActivity(activity: Activity?) {
        this.activity = activity
    }
    
    /**
     * 获取设备ID（Android ID或UUID）
     */
    fun getDeviceId(): String {
        return try {
            // 优先使用Android ID（推荐）
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: run {
                // 如果Android ID不可用，使用本地存储的UUID
                val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                var deviceId = prefs.getString("device_id", null)
                if (deviceId == null) {
                    deviceId = UUID.randomUUID().toString()
                    prefs.edit().putString("device_id", deviceId).apply()
                }
                deviceId
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取设备ID失败", e)
            UUID.randomUUID().toString()
        }
    }
    
    /**
     * 获取应用版本号
     */
    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "获取版本号失败", e)
            "1.0.0"
        }
    }
    
    /**
     * 比较版本号
     * @param currentVersion 当前版本，如 "1.0.0"
     * @param latestVersion 最新版本，如 "2.0.0"
     * @return true 如果 latestVersion > currentVersion
     */
    private fun compareVersions(currentVersion: String, latestVersion: String): Boolean {
        try {
            val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
            val latestParts = latestVersion.split(".").map { it.toIntOrNull() ?: 0 }
            
            val maxLength = maxOf(currentParts.size, latestParts.size)
            
            for (i in 0 until maxLength) {
                val current = currentParts.getOrElse(i) { 0 }
                val latest = latestParts.getOrElse(i) { 0 }
                
                when {
                    latest > current -> return true
                    latest < current -> return false
                    // 相等则继续比较下一段
                }
            }
            
            // 所有段都相等
            return false
        } catch (e: Exception) {
            Log.e(TAG, "版本比较失败: $e")
            // 比较失败时，使用字符串比较作为后备
            return latestVersion != currentVersion
        }
    }
    
    /**
     * 检查版本并处理更新
     * 
     * @param onUpdateRequired 当需要更新时回调 (latestVersion, downloadUrl, releaseNotes, required)
     * @param onNoUpdate 当不需要更新时回调
     * @param onError 当检查失败时回调
     */
    fun checkVersion(
        onUpdateRequired: (latestVersion: String, downloadUrl: String, releaseNotes: String, required: Boolean) -> Unit,
        onNoUpdate: () -> Unit = {},
        onError: (error: String) -> Unit = {}
    ) {
        val appVersion = getAppVersion()
        val deviceId = getDeviceId()
        
        Log.e(TAG, "开始检查版本: 当前版本=$appVersion, 设备ID=$deviceId")
        Log.e(TAG, "BASE_URL: ${com.gongkao.cuotifupan.api.ApiClient.BASE_URL}")
        
        // 使用协程调用 Retrofit API
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.e(TAG, "准备调用版本检查API...")
                Log.e(TAG, "参数: clientVersion=$appVersion, deviceId=$deviceId")
                
                // 确保参数不为null（使用空字符串代替null，避免类型转换问题）
                val clientVersionParam = appVersion ?: ""
                val deviceIdParam = deviceId ?: ""
                
                Log.e(TAG, "准备创建 Retrofit Call...")
                
                // 使用 Call API 而不是 suspend 函数，避免泛型类型转换问题
                val call = ApiClient.questionApiService.checkVersion(
                    clientVersion = clientVersionParam,
                    deviceId = deviceIdParam
                )
                
                Log.e(TAG, "Call 创建成功，准备执行...")
                
                // 在 IO 线程执行 Call
                val response = call.execute()
                
                Log.e(TAG, "收到版本检查响应: 状态码=${response.code()}, 成功=${response.isSuccessful}")
                
                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    val errorMsg = "HTTP ${response.code()}: ${response.message()}"
                    Log.e(TAG, "版本检查失败: $errorMsg")
                    Log.e(TAG, "错误体: $errorBody")
                    withContext(Dispatchers.Main) {
                        onError(errorMsg)
                    }
                    return@launch
                }
                
                val versionResponse = response.body()
                if (versionResponse == null) {
                    val errorMsg = "响应体为空"
                    Log.e(TAG, errorMsg)
                    withContext(Dispatchers.Main) {
                        onError(errorMsg)
                    }
                    return@launch
                }
                
                Log.e(TAG, "收到版本检查响应: success=${versionResponse.success}, status=${versionResponse.status}")
                
                if (!versionResponse.success) {
                    val errorMsg = "版本检查失败: ${versionResponse.status}"
                    Log.e(TAG, errorMsg)
                    withContext(Dispatchers.Main) {
                        onError(errorMsg)
                    }
                    return@launch
                }
                
                val serverVersion = versionResponse.version.appVersion
                val latestVersion = versionResponse.update.latestVersion
                val downloadUrl = versionResponse.update.downloadUrl
                val releaseNotes = versionResponse.update.releaseNotes
                val required = versionResponse.update.required
                
                Log.d(TAG, "版本检查完成:")
                Log.d(TAG, "  - 当前版本: $appVersion")
                Log.d(TAG, "  - 服务器版本: $serverVersion")
                Log.d(TAG, "  - 最新版本: $latestVersion")
                Log.d(TAG, "  - 需要更新: $required")
                
                // 处理 Pro 和配额信息
                val proInfo = versionResponse.pro
                if (proInfo != null) {
                    Log.d(TAG, "收到Pro信息:")
                    Log.d(TAG, "  - isPro: ${proInfo.isPro}")
                    Log.d(TAG, "  - expiresAt: ${proInfo.expiresAt}")
                    Log.d(TAG, "  - 配额: ${proInfo.remainingQuota}/${proInfo.monthlyQuota}")
                    
                    // 先根据服务器返回的 Pro 状态更新本地
                    if (proInfo.isPro && proInfo.expiresAt != null) {
                        // 服务器确认是 Pro 用户，激活本地状态
                        val activatedAt = ProManager.getActivatedAt(context) ?: versionResponse.timestamp
                        ProManager.activatePro(context, activatedAt, proInfo.expiresAt)
                        Log.d(TAG, "Pro 状态已激活: $activatedAt -> ${proInfo.expiresAt}")
                    } else {
                        // 服务器返回 isPro=false，清除本地 Pro 状态
                        ProManager.clearProStatusOnly(context)
                        Log.d(TAG, "服务器返回 isPro=false，已清除本地 Pro 状态")
                    }
                    
                    // 然后更新本地配额信息（使用服务器返回的最新值，覆盖旧数据）
                    ProManager.updateQuota(
                        context,
                        monthlyQuota = proInfo.monthlyQuota,
                        remainingQuota = proInfo.remainingQuota,
                        usedCount = proInfo.usedCount,
                        nextPeriodQuota = proInfo.nextPeriodQuota,
                        tier = proInfo.tier,
                        tierName = proInfo.tierName
                    )
                }
                
                // 处理免费额度信息
                val freeQuota = versionResponse.freeQuota
                if (freeQuota != null) {
                    Log.d(TAG, "收到免费额度信息:")
                    Log.d(TAG, "  - 总配额: ${freeQuota.totalQuota}")
                    Log.d(TAG, "  - 已用: ${freeQuota.usedCount}")
                    Log.d(TAG, "  - 剩余: ${freeQuota.remainingQuota}")
                    Log.d(TAG, "  - 可用: ${freeQuota.isAvailable}")
                    
                    ProManager.updateFreeQuota(
                        context,
                        totalQuota = freeQuota.totalQuota,
                        usedCount = freeQuota.usedCount,
                        remainingQuota = freeQuota.remainingQuota,
                        isAvailable = freeQuota.isAvailable
                    )
                }
                
                // 保存兑换码领取链接（如果提供）
                val redeemCodeUrl = versionResponse.redeemCodeUrl
                if (redeemCodeUrl != null && redeemCodeUrl.isNotBlank()) {
                    Log.d(TAG, "收到兑换码链接: $redeemCodeUrl")
                    ProManager.setRedeemCodeUrl(context, redeemCodeUrl)
                } else {
                    // 如果后端返回 null 或空字符串，清除之前保存的链接
                    Log.d(TAG, "未收到兑换码链接（null或空字符串），清除已保存的链接")
                    ProManager.setRedeemCodeUrl(context, null)
                }
                
                // 判断是否需要更新：
                // 1. 如果服务器标记为必需更新（required = true），则强制提示
                // 2. 如果最新版本与当前版本不同，则提示更新
                val versionDifferent = appVersion != latestVersion
                val needsUpdate = required || versionDifferent
                
                withContext(Dispatchers.Main) {
                    if (needsUpdate) {
                        if (required) {
                            Log.i(TAG, "服务器要求强制更新: $latestVersion (当前: $appVersion)")
                        } else {
                            Log.i(TAG, "发现版本不一致: 最新版本=$latestVersion, 当前版本=$appVersion")
                        }
                        onUpdateRequired(latestVersion, downloadUrl, releaseNotes, required)
                    } else {
                        Log.d(TAG, "应用已是最新版本")
                        onNoUpdate()
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "版本检查异常", e)
                Log.e(TAG, "异常类型: ${e.javaClass.name}")
                Log.e(TAG, "异常消息: ${e.message}")
                e.printStackTrace()
                val errorMsg = "版本检查失败: ${e.message ?: e.javaClass.simpleName}"
                withContext(Dispatchers.Main) {
                    onError(errorMsg)
                }
            }
        }
    }
    
    /**
     * 下载APK
     */
    fun downloadAPK(downloadUrl: String, filename: String = "app-update.apk") {
        val apiBaseUrl = com.gongkao.cuotifupan.api.ApiClient.BASE_URL
        val fullUrl = if (downloadUrl.startsWith("http")) {
            downloadUrl
        } else {
            // 如果 downloadUrl 以 / 开头，直接拼接；否则添加 /
            val baseUrl = apiBaseUrl.removeSuffix("/")
            val path = if (downloadUrl.startsWith("/")) downloadUrl else "/$downloadUrl"
            "$baseUrl$path"
        }
        
        Log.d(TAG, "开始下载APK: $fullUrl")
        
        try {
            val request = DownloadManager.Request(Uri.parse(fullUrl))
            request.setDestinationInExternalPublicDir(
                Environment.DIRECTORY_DOWNLOADS,
                filename
            )
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            )
            request.setTitle("应用更新")
            request.setDescription("正在下载新版本...")
            request.setMimeType("application/vnd.android.package-archive")
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            
            Log.d(TAG, "APK下载已启动，下载ID: $downloadId")
        } catch (e: Exception) {
            Log.e(TAG, "启动下载失败", e)
        }
    }
}
