package com.gongkao.cuotifupan.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import com.gongkao.cuotifupan.util.VersionChecker
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * 手写擦除服务
 * 调用后端API清除图片中的手写笔记
 */
object HandwritingRemovalService {
    
    private const val TAG = "HandwritingRemoval"
    private const val ENDPOINT = "/api/handwriting/remove"
    
    /**
     * 移除手写笔记
     * 
     * @param bitmap 需要处理的图片
     * @return 处理后的图片Bitmap，失败返回null
     */
    suspend fun removeHandwriting(bitmap: Bitmap): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 将Bitmap保存为临时文件
                val appContext = this@HandwritingRemovalService.context ?: throw IllegalStateException("HandwritingRemovalService未初始化，请先调用init()")
                val tempFile = File.createTempFile("handwriting_", ".jpg", appContext.cacheDir)
                tempFile.deleteOnExit()
                
                FileOutputStream(tempFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                Log.d(TAG, "✅ 临时文件创建成功: ${tempFile.absolutePath}")
                
                // 2. 获取设备ID
                val deviceId = VersionChecker(appContext).getDeviceId()
                Log.d(TAG, "📱 设备ID: $deviceId")
                
                // 3. 构建请求
                val requestFile = tempFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("image", tempFile.name, requestFile)
                val deviceIdBody = deviceId.toRequestBody("text/plain".toMediaTypeOrNull())
                
                // 4. 使用Retrofit调用API
                val response = ApiClient.questionApiService.removeHandwriting(
                    image = imagePart,
                    deviceId = deviceIdBody,
                    saveToServer = null  // 不保存到服务器，直接返回图片数据
                )
                
                Log.d(TAG, "📥 响应状态码: ${response.code()}")
                
                if (!response.isSuccessful || response.body() == null) {
                    val errorBody = response.errorBody()?.string()
                    Log.e(TAG, "❌ 请求失败: ${response.code()} - $errorBody")
                    
                    // 处理特定的错误码
                    when (response.code()) {
                        403 -> {
                            // Pro相关错误
                            try {
                                val errorJson = org.json.JSONObject(errorBody ?: "{}")
                                val errorCode = errorJson.optString("error", "")
                                
                                // 尝试从错误响应中更新配额信息
                                val dataObj = errorJson.optJSONObject("data")
                                if (dataObj != null) {
                                    val freeQuotaExhausted = dataObj.optBoolean("free_quota_exhausted", false)
                                    
                                    if (freeQuotaExhausted) {
                                        // 免费额度已用完，更新免费额度信息
                                        val totalQuota = dataObj.optInt("total_quota", 0)
                                        val remainingQuota = dataObj.optInt("remaining_quota", 0)
                                        val usedCount = dataObj.optInt("used_count", 0)
                                        Log.d(TAG, "📊 从错误响应中获取免费额度: $remainingQuota/$totalQuota，已用: $usedCount")
                                        com.gongkao.cuotifupan.util.ProManager.updateFreeQuota(
                                            appContext,
                                            totalQuota = totalQuota,
                                            usedCount = usedCount,
                                            remainingQuota = remainingQuota,
                                            isAvailable = false
                                        )
                                    } else {
                                        // Pro配额错误，更新Pro配额信息
                                        val monthlyQuota = dataObj.optInt("monthly_quota", 0)
                                        val remainingQuota = dataObj.optInt("remaining_quota", 0)
                                        val usedCount = dataObj.optInt("used_count", 0)
                                        val nextPeriodQuota = dataObj.optInt("next_period_quota", 0)
                                        Log.d(TAG, "📊 从错误响应中获取Pro配额: $remainingQuota/$monthlyQuota，已用: $usedCount，下一周期: $nextPeriodQuota")
                                        // 保留现有的下一周期配额，如果错误响应中没有提供
                                        val currentNextPeriodQuota = if (nextPeriodQuota > 0) nextPeriodQuota else com.gongkao.cuotifupan.util.ProManager.getNextPeriodQuota(appContext)
                                        com.gongkao.cuotifupan.util.ProManager.updateQuota(
                                            appContext,
                                            monthlyQuota = monthlyQuota,
                                            remainingQuota = remainingQuota,
                                            usedCount = usedCount,
                                            nextPeriodQuota = currentNextPeriodQuota
                                        )
                                    }
                                }
                                
                                when (errorCode) {
                                    "NOT_PRO_USER" -> {
                                        Log.e(TAG, "❌ 用户不是Pro用户")
                                        throw HandwritingRemovalException("您还不是Pro用户，无法使用此功能", errorCode)
                                    }
                                    "PRO_EXPIRED" -> {
                                        Log.e(TAG, "❌ Pro服务已过期")
                                        throw HandwritingRemovalException("Pro服务已过期，请续费后使用", errorCode)
                                    }
                                    "QUOTA_EXCEEDED" -> {
                                        Log.e(TAG, "❌ 配额已用完")
                                        // 检查是免费额度还是Pro配额用完
                                        val dataObj = errorJson.optJSONObject("data")
                                        val freeQuotaExhausted = dataObj?.optBoolean("free_quota_exhausted", false) ?: false
                                        val message = if (freeQuotaExhausted) {
                                            "免费试用已用完，升级Pro可继续使用"
                                        } else {
                                            "本月使用次数已达上限，请下月再试或升级套餐"
                                        }
                                        throw HandwritingRemovalException(message, errorCode)
                                    }
                                    else -> {
                                        throw HandwritingRemovalException("Pro服务错误: $errorCode", errorCode)
                                    }
                                }
                            } catch (e: HandwritingRemovalException) {
                                throw e
                            } catch (e: Exception) {
                                throw HandwritingRemovalException("Pro服务错误", "UNKNOWN")
                            }
                        }
                        400 -> {
                            try {
                                val errorJson = org.json.JSONObject(errorBody ?: "{}")
                                val errorMsg = errorJson.optString("error", "请求参数错误")
                                throw HandwritingRemovalException(errorMsg, "INVALID_REQUEST")
                            } catch (e: HandwritingRemovalException) {
                                throw e
                            } catch (e: Exception) {
                                throw HandwritingRemovalException("请求参数错误", "INVALID_REQUEST")
                            }
                        }
                        else -> {
                            throw HandwritingRemovalException("服务器错误: ${response.code()}", "SERVER_ERROR")
                        }
                    }
                }
                
                val apiResponse = response.body()!!
                
                if (!apiResponse.success) {
                    val errorMsg = apiResponse.error ?: "未知错误"
                    Log.e(TAG, "❌ 手写擦除失败: $errorMsg")
                    throw HandwritingRemovalException(errorMsg, apiResponse.error ?: "UNKNOWN")
                }
                
                val data = apiResponse.data
                if (data == null) {
                    Log.e(TAG, "❌ 响应中没有data字段")
                    throw HandwritingRemovalException("服务器响应格式错误", "INVALID_RESPONSE")
                }
                
                // 记录并保存配额信息（根据 quota_type 区分免费额度和Pro配额）
                val quotaType = data.quotaType ?: "unknown"
                Log.d(TAG, "📊 配额类型: $quotaType，剩余配额: ${data.remainingQuota}，已使用: ${data.usedCount ?: 0}")
                
                when (quotaType) {
                    "free" -> {
                        // 更新免费额度
                        if (data.totalQuota != null && data.remainingQuota != null) {
                            Log.d(TAG, "📊 使用免费额度: ${data.remainingQuota}/${data.totalQuota}，已使用: ${data.usedCount ?: 0}")
                            com.gongkao.cuotifupan.util.ProManager.updateFreeQuota(
                                appContext,
                                totalQuota = data.totalQuota,
                                usedCount = data.usedCount ?: 0,
                                remainingQuota = data.remainingQuota,
                                isAvailable = data.remainingQuota > 0
                            )
                        }
                    }
                    "pro" -> {
                        // 更新Pro配额
                        if (data.monthlyQuota != null && data.remainingQuota != null) {
                            Log.d(TAG, "📊 使用Pro配额: ${data.remainingQuota}/${data.monthlyQuota}，已使用: ${data.usedCount ?: 0}，下一周期: ${data.nextPeriodQuota ?: 0}")
                            val currentNextPeriodQuota = com.gongkao.cuotifupan.util.ProManager.getNextPeriodQuota(appContext)
                            com.gongkao.cuotifupan.util.ProManager.updateQuota(
                                appContext,
                                monthlyQuota = data.monthlyQuota,
                                remainingQuota = data.remainingQuota,
                                usedCount = data.usedCount ?: 0,
                                nextPeriodQuota = data.nextPeriodQuota ?: currentNextPeriodQuota
                            )
                        }
                    }
                    else -> {
                        // 兼容旧版本：如果没有 quota_type，尝试根据字段判断
                        if (data.totalQuota != null) {
                            // 有 total_quota 字段，认为是免费额度
                            com.gongkao.cuotifupan.util.ProManager.updateFreeQuota(
                                appContext,
                                totalQuota = data.totalQuota,
                                usedCount = data.usedCount ?: 0,
                                remainingQuota = data.remainingQuota ?: 0,
                                isAvailable = (data.remainingQuota ?: 0) > 0
                            )
                        } else if (data.monthlyQuota != null) {
                            // 有 monthly_quota 字段，认为是Pro配额
                            val currentNextPeriodQuota = com.gongkao.cuotifupan.util.ProManager.getNextPeriodQuota(appContext)
                            com.gongkao.cuotifupan.util.ProManager.updateQuota(
                                appContext,
                                monthlyQuota = data.monthlyQuota,
                                remainingQuota = data.remainingQuota ?: 0,
                                usedCount = data.usedCount ?: 0,
                                nextPeriodQuota = data.nextPeriodQuota ?: currentNextPeriodQuota
                            )
                        }
                    }
                }
                
                Log.d(TAG, "✅ 手写擦除成功，使用服务: ${data.provider ?: "unknown"}")
                Log.d(TAG, "   图片URL: ${data.imageUrl}")
                Log.d(TAG, "   是否有Base64: ${!data.imageBase64.isNullOrBlank()}")
                
                // 5. 处理返回的图片（优先使用URL，其次使用base64）
                val processedBitmap = when {
                    // 优先使用 image_url（非null且非空）
                    !data.imageUrl.isNullOrBlank() -> {
                        Log.d(TAG, "   使用URL下载图片")
                        val imageUrlFull = if (data.imageUrl.startsWith("http")) {
                            data.imageUrl
                        } else {
                            "${ApiClient.BASE_URL.trimEnd('/')}/${data.imageUrl}"
                        }
                        downloadImageFromUrl(imageUrlFull)
                    }
                    // 其次使用 image_base64
                    !data.imageBase64.isNullOrBlank() -> {
                        Log.d(TAG, "   使用Base64解码图片")
                        decodeBase64Image(data.imageBase64)
                    }
                    // 最后尝试使用 image_data_url
                    !data.imageDataUrl.isNullOrBlank() -> {
                        Log.d(TAG, "   使用Data URL解码图片")
                        // 从 data URL 中提取 base64 部分
                        val base64Part = data.imageDataUrl.substringAfter("base64,")
                        if (base64Part.isNotBlank() && base64Part != data.imageDataUrl) {
                            decodeBase64Image(base64Part)
                        } else {
                            null
                        }
                    }
                    else -> {
                        Log.e(TAG, "❌ 响应中没有图片数据（URL和Base64都为空）")
                        null
                    }
                }
                
                if (processedBitmap != null) {
                    Log.d(TAG, "✅ 图片处理成功")
                } else {
                    Log.e(TAG, "❌ 图片处理失败")
                }
                
                // 清理临时文件
                tempFile.delete()
                
                processedBitmap
                
            } catch (e: HandwritingRemovalException) {
                Log.e(TAG, "❌ 手写擦除异常: ${e.message}", e)
                throw e  // 重新抛出，让调用者可以处理
            } catch (e: Exception) {
                Log.e(TAG, "❌ 手写擦除异常", e)
                throw HandwritingRemovalException("手写擦除失败: ${e.message}", "UNKNOWN")
            }
        }
    }
    
    /**
     * 手写擦除异常类
     */
    class HandwritingRemovalException(
        message: String,
        val errorCode: String
    ) : Exception(message)
    
    // 需要context来创建临时文件，使用单例模式存储
    private var context: android.content.Context? = null
    
    /**
     * 初始化服务（设置Context）
     */
    fun init(context: android.content.Context) {
        this.context = context.applicationContext
    }
    
    /**
     * 获取配额信息（从最后一次成功调用的响应中）
     * 注意：这是一个辅助方法，实际配额信息应该在每次调用时从响应中获取
     */
    fun getQuotaInfo(data: RemovalData?): String? {
        if (data == null) return null
        val remaining = data.remainingQuota ?: return null
        val monthly = data.monthlyQuota ?: return null
        val used = data.usedCount ?: 0
        return "剩余: $remaining/$monthly，已用: $used"
    }
    
    /**
     * 从URL下载图片
     */
    private fun downloadImageFromUrl(url: String): Bitmap? {
        return try {
            val client = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val inputStream = response.body?.byteStream()
                BitmapFactory.decodeStream(inputStream)
            } else {
                Log.e(TAG, "下载图片失败: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载图片失败", e)
            null
        }
    }
    
    /**
     * 解码Base64图片
     */
    private fun decodeBase64Image(base64: String): Bitmap? {
        return try {
            val imageBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Base64解码失败", e)
            null
        }
    }
}

