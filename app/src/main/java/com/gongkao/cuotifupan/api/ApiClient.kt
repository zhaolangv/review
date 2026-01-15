package com.gongkao.cuotifupan.api

import android.util.Log
import com.gongkao.cuotifupan.util.ApiLogDialog
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.Buffer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * API 客户端
 * 
 * 配置说明：
 * 1. Android 模拟器访问本地服务器：使用 "http://10.0.2.2:5000/"
 * 2. Android 真机访问本地服务器：使用电脑的局域网IP，如 "http://192.168.1.100:5000/"
 * 3. 使用 ngrok 等内网穿透：使用 ngrok 提供的地址，如 "https://xxxxx.ngrok-free.app/"
 * 
 * 注意：确保 BASE_URL 以 "/" 结尾
 */
object ApiClient {
    
    private const val TAG = "ApiClient"
    
    // 后端服务器地址
    // 请根据你的实际情况修改：
    // - Android 模拟器：http://10.0.2.2:5000/
    // - Android 真机（同一WiFi）：http://你的电脑IP:5000/ （如 http://192.168.1.100:5000/）
    // - ngrok 内网穿透：https://xxxxx.ngrok-free.app/
 const val BASE_URL = "https://gongkao.jnhongniang.xyz/"  // 真机使用电脑IP
    
    // 构建OkHttpClient，在debug版本添加日志拦截器
    private val okHttpClient = OkHttpClient.Builder()
        .apply {
            // 只在debug版本添加日志拦截器（通过检查类是否存在）
            try {
                val loggingClass = Class.forName("okhttp3.logging.HttpLoggingInterceptor")
                val interceptor = loggingClass.getDeclaredConstructor().newInstance()
                val setLevel = loggingClass.getMethod("setLevel", Class.forName("okhttp3.logging.HttpLoggingInterceptor\$Level"))
                val levelEnum = Class.forName("okhttp3.logging.HttpLoggingInterceptor\$Level")
                    .getField("BODY").get(null)
                setLevel.invoke(interceptor, levelEnum)
                addInterceptor(interceptor as okhttp3.Interceptor)
            } catch (e: Exception) {
                // 如果日志拦截器不可用（release版本），忽略
                // 在release版本中，okhttp.logging依赖不存在，所以会捕获异常
            }
        }
        .addInterceptor { chain ->
            val request = chain.request()
            val startTime = System.currentTimeMillis()
            
            // 收集请求信息
            val requestHeaders = mutableMapOf<String, String>()
            request.headers.forEach { header ->
                requestHeaders[header.first] = header.second
            }
            
            val requestBody = try {
                request.body?.let {
                    val buffer = Buffer()
                    it.writeTo(buffer)
                    buffer.readUtf8()
                }
            } catch (e: Exception) {
                null
            }
            
            val requestInfo = ApiLogDialog.RequestInfo(
                url = request.url.toString(),
                method = request.method,
                headers = requestHeaders,
                body = requestBody
            )
            
            // 记录请求信息（release版本也记录，使用Log.e确保不被移除）
            Log.e(TAG, "========== 发送HTTP请求 ==========")
            Log.e(TAG, "URL: ${request.url}")
            Log.e(TAG, "Method: ${request.method}")
            
            try {
                val response = chain.proceed(request)
                val duration = System.currentTimeMillis() - startTime
                
                // 收集响应信息
                val responseHeaders = mutableMapOf<String, String>()
                response.headers.forEach { header ->
                    responseHeaders[header.first] = header.second
                }
                
                val responseBody = try {
                    val peekBody = response.peekBody(2048) // 预览2KB
                    peekBody.string()
                } catch (e: Exception) {
                    null
                }
                
                val responseInfo = ApiLogDialog.ResponseInfo(
                    statusCode = response.code,
                    message = response.message,
                    headers = responseHeaders,
                    body = responseBody,
                    duration = duration
                )
                
                // 记录响应信息
                Log.e(TAG, "========== 收到HTTP响应 ==========")
                Log.e(TAG, "URL: ${request.url}")
                Log.e(TAG, "状态码: ${response.code}")
                Log.e(TAG, "响应消息: ${response.message}")
                Log.e(TAG, "耗时: ${duration}ms")
                
                if (!response.isSuccessful) {
                    val errorBody = response.peekBody(1024).string()
                    Log.e(TAG, "❌ HTTP请求失败")
                    Log.e(TAG, "状态码: ${response.code}")
                    Log.e(TAG, "错误消息: ${response.message}")
                    Log.e(TAG, "错误体: $errorBody")
                }
                
                // 显示日志弹窗（在主线程）
                showLogDialog(requestInfo, responseInfo, null)
                
                response
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                
                // 收集错误信息
                val errorInfo = ApiLogDialog.ErrorInfo(
                    exceptionType = e.javaClass.simpleName,
                    message = e.message,
                    stackTrace = e.stackTraceToString(),
                    duration = duration
                )
                
                Log.e(TAG, "❌ HTTP请求异常")
                Log.e(TAG, "URL: ${request.url}")
                Log.e(TAG, "异常类型: ${e.javaClass.simpleName}")
                Log.e(TAG, "异常消息: ${e.message}")
                Log.e(TAG, "耗时: ${duration}ms")
                e.printStackTrace()
                
                // 显示错误日志弹窗
                Log.e(TAG, "请求异常，准备显示错误日志弹窗")
                showLogDialog(requestInfo, null, errorInfo)
                
                throw e
            }
        }
        .connectTimeout(60, TimeUnit.SECONDS)  // 连接超时：60秒
        .readTimeout(600, TimeUnit.SECONDS)   // 读取超时：600秒（10分钟，OCR + AI 处理需要较长时间）
        .writeTimeout(180, TimeUnit.SECONDS)   // 写入超时：180秒（3分钟，上传大量Base64数据需要时间）
        .build()
    
    val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val questionApiService: QuestionApiService = retrofit.create(QuestionApiService::class.java)
    
    val redemptionApiService: RedemptionApiService = retrofit.create(RedemptionApiService::class.java)
    
    val migrationApiService: MigrationApiService = retrofit.create(MigrationApiService::class.java)
    
    // 保存当前Activity引用，用于显示弹窗
    @Volatile
    private var currentActivity: android.app.Activity? = null
    
    /**
     * 设置当前Activity（用于显示弹窗）
     */
    fun setCurrentActivity(activity: android.app.Activity?) {
        currentActivity = activity
    }
    
    /**
     * 显示日志弹窗（在主线程）
     */
    private fun showLogDialog(
        requestInfo: ApiLogDialog.RequestInfo,
        responseInfo: ApiLogDialog.ResponseInfo?,
        errorInfo: ApiLogDialog.ErrorInfo?
    ) {
        Log.e(TAG, "准备显示日志弹窗...")
        Log.e(TAG, "当前Activity: $currentActivity")
        
        // 优先使用保存的Activity，否则使用Application Context
        val context = currentActivity ?: com.gongkao.cuotifupan.SnapReviewApplication.getInstance()
        Log.e(TAG, "使用的Context: $context")
        
        if (context != null) {
            // 在主线程显示弹窗
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Log.e(TAG, "在主线程显示弹窗")
                try {
                    ApiLogDialog.showLogDialog(context, requestInfo, responseInfo, errorInfo)
                    Log.e(TAG, "弹窗显示调用完成")
                } catch (e: Exception) {
                    Log.e(TAG, "显示弹窗时出错", e)
                    e.printStackTrace()
                }
            }
        } else {
            Log.e(TAG, "❌ 无法显示弹窗：Context为null")
        }
    }
    
    init {
        Log.i(TAG, "========== API客户端初始化 ==========")
        Log.i(TAG, "BASE_URL: $BASE_URL")
        Log.i(TAG, "连接超时: 60秒")
        Log.i(TAG, "读取超时: 600秒（10分钟）")
        Log.i(TAG, "写入超时: 180秒（3分钟）")
        Log.i(TAG, "=====================================")
    }
}

