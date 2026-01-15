package com.gongkao.cuotifupan.api

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 百度手写识别 API 服务
 * 
 * 使用说明：
 * 1. 访问 https://ai.baidu.com/tech/ocr/handwriting 申请 API
 * 2. 创建应用获取 API Key 和 Secret Key
 * 3. 在 local.properties 中配置：
 *    BAIDU_OCR_API_KEY=你的API_KEY
 *    BAIDU_OCR_SECRET_KEY=你的SECRET_KEY
 * 
 * 免费额度：每天 500 次调用
 */
object BaiduOcrService {
    
    private const val TAG = "BaiduOcrService"
    
    // API 配置（需要用户自行申请）
    // TODO: 将这些值移到 local.properties 或配置文件中
    private var apiKey: String = ""
    private var secretKey: String = ""
    
    // Token 缓存
    private var accessToken: String? = null
    private var tokenExpireTime: Long = 0
    
    // API 地址
    private const val TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token"
    private const val HANDWRITING_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/handwriting"
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 配置 API 密钥
     */
    fun configure(apiKey: String, secretKey: String) {
        this.apiKey = apiKey
        this.secretKey = secretKey
        // 清除旧 Token
        accessToken = null
        tokenExpireTime = 0
    }
    
    /**
     * 检查是否已配置
     */
    fun isConfigured(): Boolean {
        return apiKey.isNotBlank() && secretKey.isNotBlank()
    }
    
    /**
     * 获取 Access Token
     */
    private suspend fun getAccessToken(): String? = withContext(Dispatchers.IO) {
        // 检查缓存的 Token 是否有效
        if (accessToken != null && System.currentTimeMillis() < tokenExpireTime) {
            return@withContext accessToken
        }
        
        if (!isConfigured()) {
            Log.e(TAG, "API 未配置，请先调用 configure() 方法")
            return@withContext null
        }
        
        try {
            val formBody = FormBody.Builder()
                .add("grant_type", "client_credentials")
                .add("client_id", apiKey)
                .add("client_secret", secretKey)
                .build()
            
            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(formBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                accessToken = json.getString("access_token")
                val expiresIn = json.getLong("expires_in")
                // 提前 5 分钟过期
                tokenExpireTime = System.currentTimeMillis() + (expiresIn - 300) * 1000
                Log.d(TAG, "获取 Access Token 成功")
                return@withContext accessToken
            } else {
                Log.e(TAG, "获取 Access Token 失败: $responseBody")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取 Access Token 异常", e)
            return@withContext null
        }
    }
    
    /**
     * 识别手写文字
     * @param bitmap 手写内容的图片
     * @return 识别结果文本，失败返回 null
     */
    suspend fun recognizeHandwriting(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        if (token == null) {
            Log.e(TAG, "无法获取 Access Token")
            return@withContext null
        }
        
        try {
            // 将 Bitmap 转换为 Base64
            val base64Image = bitmapToBase64(bitmap)
            
            val formBody = FormBody.Builder()
                .add("image", base64Image)
                .add("recognize_granularity", "big") // 不定位单字符位置
                .build()
            
            val request = Request.Builder()
                .url("$HANDWRITING_URL?access_token=$token")
                .post(formBody)
                .build()
            
            Log.d(TAG, "开始调用百度手写识别 API...")
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                
                // 检查是否有错误
                if (json.has("error_code")) {
                    val errorCode = json.getInt("error_code")
                    val errorMsg = json.optString("error_msg", "未知错误")
                    Log.e(TAG, "API 错误: $errorCode - $errorMsg")
                    return@withContext null
                }
                
                // 解析识别结果
                val wordsResult = json.optJSONArray("words_result")
                if (wordsResult != null && wordsResult.length() > 0) {
                    val result = StringBuilder()
                    for (i in 0 until wordsResult.length()) {
                        val item = wordsResult.getJSONObject(i)
                        val words = item.getString("words")
                        if (result.isNotEmpty()) {
                            result.append("\n")
                        }
                        result.append(words)
                    }
                    Log.d(TAG, "识别成功: ${result.toString()}")
                    return@withContext result.toString()
                } else {
                    Log.w(TAG, "识别结果为空")
                    return@withContext null
                }
            } else {
                Log.e(TAG, "API 请求失败: $responseBody")
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "手写识别异常", e)
            return@withContext null
        }
    }
    
    /**
     * 将 Bitmap 转换为 Base64 字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val byteArrayOutputStream = ByteArrayOutputStream()
        // 使用 JPEG 格式，质量 90%
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}

