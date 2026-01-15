package com.gongkao.cuotifupan.api

import android.util.Log
import com.gongkao.cuotifupan.api.QuestionContentResponse
import com.gongkao.cuotifupan.api.BatchQuestionRequest
import com.gongkao.cuotifupan.api.BatchQuestionItem
import com.gongkao.cuotifupan.data.Question
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import android.util.Base64 as AndroidBase64

/**
 * 题目API请求队列管理器
 * 用于控制并发数，避免同时发送过多请求
 */
object QuestionApiQueue {
    
    // API请求开关：设置为 false 时，所有API请求将被禁用（不删除代码）
    const val API_ENABLED = false
    
    private const val TAG = "QuestionApiQueue"
    private const val BATCH_SIZE_SMALL = 10 // 题量少时的批次大小
    private const val BATCH_SIZE_LARGE = 20 // 题量多时的批次大小
    private const val THRESHOLD = 20 // 题量阈值，超过此值使用大批次
    private const val BATCH_TIMEOUT_MS = 2000L // 批次超时时间（2秒），即使未满也发送
    
    @Volatile
    private var batchSize = BATCH_SIZE_SMALL // 当前批次大小
    
    // 批次缓冲区
    private val batchBuffer = mutableListOf<RequestTask>()
    private val batchLock = java.util.concurrent.locks.ReentrantLock()
    private var lastBatchTime = System.currentTimeMillis()
    
    private val requestQueue = Channel<RequestTask>(Channel.UNLIMITED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var activeRequests = 0
    private val activeRequestsFlow = MutableStateFlow(0)
    
    init {
        // 启动队列处理器
        Log.i(TAG, "QuestionApiQueue 初始化，启动队列处理器...")
        startQueueProcessor()
        startBatchProcessor()
        Log.i(TAG, "队列处理器已启动，批次大小: $batchSize")
    }
    
    /**
     * 获取当前活跃请求数
     */
    val activeRequestsCount: StateFlow<Int> = activeRequestsFlow
    
    /**
     * 请求任务
     */
    data class RequestTask(
        val question: Question,
        val onSuccess: suspend (QuestionContentResponse) -> Unit,
        val onError: suspend (Throwable) -> Unit
    )
    
    /**
     * 启动批次处理器（定期检查并发送批量请求）
     */
    private fun startBatchProcessor() {
        scope.launch {
            Log.i(TAG, "批次处理器已启动，每500ms检查一次")
            while (true) {
                delay(500) // 每500ms检查一次
                
                batchLock.lock()
                try {
                    val now = System.currentTimeMillis()
                    val timeSinceLastBatch = now - lastBatchTime
                    val shouldFlush = batchBuffer.size >= batchSize || 
                                     (batchBuffer.isNotEmpty() && timeSinceLastBatch >= BATCH_TIMEOUT_MS)
                    
                    // 添加调试日志
                    if (batchBuffer.isNotEmpty()) {
                        Log.d(TAG, "批次检查: 缓冲区=${batchBuffer.size}/$batchSize, 距离上次=${timeSinceLastBatch}ms, 应发送=$shouldFlush")
                    }
                    
                    if (shouldFlush) {
                        val batch = batchBuffer.toList()
                        batchBuffer.clear()
                        lastBatchTime = now
                        
                        if (batch.isNotEmpty()) {
                            Log.i(TAG, "📦 批次已满或超时，发送批量请求: ${batch.size} 个题目")
                            // 发送批量请求
                            activeRequests++
                            activeRequestsFlow.value = activeRequests
                            
                            launch {
                                try {
                                    processBatchRequest(batch)
                                } catch (e: Exception) {
                                    Log.e(TAG, "批量请求处理异常", e)
                                    e.printStackTrace()
                                    // 批量请求失败，逐个调用错误回调
                                    batch.forEach { task ->
                                        try {
                                            task.onError(e)
                                        } catch (e2: Exception) {
                                            Log.e(TAG, "调用错误回调失败", e2)
                                        }
                                    }
                                } finally {
                                    activeRequests--
                                    activeRequestsFlow.value = activeRequests
                                }
                            }
                        }
                    }
                } finally {
                    batchLock.unlock()
                }
            }
        }
    }
    
    /**
     * 启动队列处理器（保留用于单个请求的兼容性）
     */
    private fun startQueueProcessor() {
        // 现在主要使用批量请求，但保留单个请求的处理能力
        // 如果需要，可以在这里处理单个请求
    }
    
    /**
     * 带重试机制的API调用（仅对超时错误重试）
     */
    private suspend fun <T> retryOnTimeout(maxRetries: Int = 2, block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                // 只对超时错误重试
                val isTimeout = e is java.net.SocketTimeoutException || 
                               e.message?.contains("timeout", ignoreCase = true) == true
                
                if (isTimeout && attempt < maxRetries - 1) {
                    val retryDelay = (attempt + 1) * 2000L // 递增延迟：2秒、4秒
                    Log.w(TAG, "⏱️ 请求超时，${retryDelay / 1000}秒后重试 (${attempt + 1}/$maxRetries)")
                    delay(retryDelay)
                } else {
                    throw e // 不是超时错误或已达到最大重试次数，直接抛出
                }
            }
        }
        throw lastException ?: Exception("重试失败")
    }
    
    /**
     * 轮询任务状态直到完成
     * 
     * 使用智能轮询策略，减少服务器压力：
     * 1. 任务未开始（pending）：间隔 5-10 秒
     * 2. 任务处理中（processing）：
     *    - 有进度：间隔 3 秒（快速响应）
     *    - 无进度：间隔逐渐增加（5-15 秒），避免无效查询
     * 3. 查询失败/异常：使用指数退避，最多 30 秒
     * 
     * 这样可以在保证及时性的同时，大幅减少服务器压力。
     * 假设 100 个用户同时使用：
     * - 固定 2 秒间隔：每秒 50 次查询
     * - 智能间隔（平均 5 秒）：每秒 20 次查询（减少 60%）
     */
    private suspend fun pollTaskUntilComplete(
        taskId: String,
        taskMap: Map<Int, RequestTask>,
        totalItems: Int
    ) {
        var pollCount = 0
        val maxPolls = 600 // 最多轮询30分钟（平均每3秒一次）
        var lastProgress = 0
        var consecutiveNoProgress = 0 // 连续无进度次数
        
        /**
         * 智能计算轮询间隔
         * @param status 任务状态
         * @param progress 已完成数量
         * @param total 总数量
         * @return 下次轮询间隔（毫秒）
         */
        fun calculatePollInterval(status: String, progress: Int, total: Int): Long {
            return when (status) {
                "pending" -> {
                    // 任务未开始，间隔较长（5-10秒）
                    5000L + (pollCount * 500L).coerceAtMost(5000L) // 最多10秒
                }
                "processing" -> {
                    // 任务处理中，根据进度动态调整
                    val percentage = if (total > 0) (progress.toFloat() / total * 100).toInt() else 0
                    
                    if (progress > lastProgress) {
                        // 有进度，间隔较短（3-5秒）
                        consecutiveNoProgress = 0
                        3000L
                    } else {
                        // 无进度，逐渐增加间隔（5-15秒）
                        consecutiveNoProgress++
                        (3000L + consecutiveNoProgress * 1000L).coerceAtMost(15000L)
                    }
                }
                else -> {
                    // 其他状态，使用默认间隔
                    3000L
                }
            }
        }
        
        var nextPollInterval = 3000L // 初始间隔3秒
        
        while (pollCount < maxPolls) {
            try {
                // 首次查询不等待，后续根据上次查询结果决定间隔
                if (pollCount > 0) {
                    delay(nextPollInterval)
                }
                
                pollCount++
                
                // 查询任务状态
                if (!API_ENABLED) {
                    Log.d(TAG, "API请求已禁用，跳过查询任务状态")
                    return
                }
                val statusResponse = ApiClient.questionApiService.getTaskStatus(taskId)
                
                if (statusResponse.isSuccessful && statusResponse.body() != null) {
                    val statusBody = statusResponse.body()!!
                    val task = statusBody.task
                    val progress = task.progress
                    
                    // 计算进度百分比
                    val percentage = if (progress.total > 0) {
                        (progress.completed.toFloat() / progress.total * 100).toInt()
                    } else 0
                    
                    // 更新进度追踪
                    val hasProgress = progress.completed > lastProgress
                    if (!hasProgress && task.status == "processing") {
                        consecutiveNoProgress++
                    } else {
                        consecutiveNoProgress = 0
                    }
                    lastProgress = progress.completed
                    
                    // 计算下次轮询间隔（基于当前状态）
                    nextPollInterval = calculatePollInterval(
                        task.status,
                        progress.completed,
                        progress.total
                    )
                    
                    Log.d(TAG, "📊 任务状态查询 #$pollCount: ${task.status}, 进度: ${progress.completed}/${progress.total} ($percentage%), 下次间隔: ${nextPollInterval/1000}秒")
                    
                    when (task.status) {
                        "completed" -> {
                            Log.i(TAG, "✅ 任务已完成，获取结果...")
                            
                            // 获取任务结果
                            if (!API_ENABLED) {
                                Log.d(TAG, "API请求已禁用，跳过获取任务结果")
                                return
                            }
                            val resultResponse = ApiClient.questionApiService.getTaskResult(taskId)
                            
                            if (resultResponse.isSuccessful && resultResponse.body() != null) {
                                val resultBody = resultResponse.body()!!
                                
                                Log.d(TAG, "📥 任务结果响应: success=${resultBody.success}, status=${resultBody.status}")
                                
                                if (resultBody.success && resultBody.result != null) {
                                    val batchResult = resultBody.result!!
                                    Log.i(TAG, "✅ 批量处理完成")
                                    Log.i(TAG, "   - 总数: ${batchResult.total}")
                                    Log.i(TAG, "   - 成功: ${batchResult.successCount}")
                                    Log.i(TAG, "   - 失败: ${batchResult.failedCount}")
                                    Log.i(TAG, "   - 结果数量: ${batchResult.results.size}")
                                    
                                    // 验证结果数量是否匹配
                                    if (batchResult.results.size != taskMap.size) {
                                        Log.w(TAG, "⚠️ 警告: 返回结果数量 (${batchResult.results.size}) 与任务数量 (${taskMap.size}) 不匹配")
                                    }
                                    
                                    // 处理每个题目的结果
                                    batchResult.results.forEachIndexed { index, result ->
                                        val task = taskMap[index]
                                        if (task != null) {
                                            if (result.success) {
                                                // 将 QuestionResult 转换为 QuestionContentResponse
                                                // 注意：异步接口返回的格式与同步接口不同，需要转换
                                                val questionText = result.questionText?.takeIf { it.isNotBlank() } 
                                                    ?: "未识别到题目"
                                                val options = result.options?.takeIf { it.isNotEmpty() } 
                                                    ?: emptyList()
                                                val rawText = result.rawText ?: ""
                                                val questionType = result.questionType?.takeIf { it.isNotBlank() } 
                                                    ?: "UNKNOWN"
                                                
                                                val questionContent = QuestionContentResponse(
                                                    id = "",  // 异步接口不返回 id，使用空字符串（后续可能需要生成）
                                                    screenshot = null,
                                                    rawText = rawText,
                                                    questionText = questionText,
                                                    questionType = questionType,
                                                    options = options,
                                                    ocrConfidence = null,
                                                    fromCache = false,
                                                    isDuplicate = false,
                                                    savedToDb = false,
                                                    similarityScore = null,
                                                    matchedQuestionId = null
                                                )
                                                
                                                Log.i(TAG, "✅ 题目 #${index + 1} 处理成功")
                                                Log.d(TAG, "   - 题目文本: ${questionText.take(50)}${if (questionText.length > 50) "..." else ""}")
                                                Log.d(TAG, "   - 选项数量: ${options.size}")
                                                Log.d(TAG, "   - 题目类型: $questionType")
                                                Log.d(TAG, "   - 初步答案: ${result.preliminaryAnswer ?: "未提供"}")
                                                Log.d(TAG, "   - OCR时间: ${result.ocrTime}秒, AI时间: ${result.aiTime}秒")
                                                
                                                task.onSuccess(questionContent)
                                            } else {
                                                // 错误信息是字符串，不是对象
                                                val errorMsg = result.error ?: "未知错误"
                                                Log.e(TAG, "❌ 题目 #${index + 1} 处理失败: $errorMsg")
                                                task.onError(Exception("批量请求失败: $errorMsg"))
                                            }
                                        } else {
                                            Log.w(TAG, "⚠️ 题目 #${index + 1} 的结果没有对应的任务")
                                        }
                                    }
                                    
                                    Log.i(TAG, "========== 异步批量处理完成 ==========")
                                    return // 成功完成，退出轮询
                                } else {
                                    val errorMsg = resultBody.error ?: resultBody.message ?: "获取结果失败"
                                    Log.e(TAG, "❌ 获取任务结果失败")
                                    Log.e(TAG, "   - success: ${resultBody.success}")
                                    Log.e(TAG, "   - status: ${resultBody.status}")
                                    Log.e(TAG, "   - error: ${resultBody.error}")
                                    Log.e(TAG, "   - message: ${resultBody.message}")
                                    taskMap.values.forEach { task ->
                                        task.onError(Exception("获取任务结果失败: $errorMsg"))
                                    }
                                    return
                                }
                            } else {
                                val errorMsg = resultResponse.message() ?: "获取结果失败"
                                val errorBody = resultResponse.errorBody()?.string()
                                Log.e(TAG, "❌ 获取任务结果HTTP失败")
                                Log.e(TAG, "   - 状态码: ${resultResponse.code()}")
                                Log.e(TAG, "   - 错误消息: $errorMsg")
                                Log.e(TAG, "   - 错误体: $errorBody")
                                taskMap.values.forEach { task ->
                                    task.onError(Exception("获取任务结果失败: $errorMsg (${resultResponse.code()})"))
                                }
                                return
                            }
                        }
                        
                        "failed" -> {
                            Log.e(TAG, "❌ 任务处理失败")
                            
                            // 尝试获取错误信息
                            val resultResponse = ApiClient.questionApiService.getTaskResult(taskId)
                            val errorMsg = if (resultResponse.isSuccessful && resultResponse.body() != null) {
                                resultResponse.body()!!.error ?: "任务处理失败"
                            } else {
                                "任务处理失败"
                            }
                            
                            Log.e(TAG, "   - 错误: $errorMsg")
                            taskMap.values.forEach { task ->
                                task.onError(Exception("任务处理失败: $errorMsg"))
                            }
                            return
                        }
                        
                        "pending" -> {
                            // 任务未开始，继续轮询
                            if (pollCount % 5 == 0) { // 每25-50秒记录一次详细日志
                                Log.i(TAG, "⏳ 任务等待中... (${progress.completed}/${progress.total}, $percentage%)")
                            }
                        }
                        
                        "processing" -> {
                            // 任务处理中，继续轮询
                            val logInterval = if (hasProgress) 10 else 5 // 有进度时每30秒记录，无进度时每15-25秒记录
                            if (pollCount % logInterval == 0) {
                                val statusMsg = if (hasProgress) {
                                    "任务处理中"
                                } else {
                                    "任务处理中（无新进度，已等待 ${consecutiveNoProgress * 3} 秒）"
                                }
                                Log.i(TAG, "⏳ $statusMsg... (${progress.completed}/${progress.total}, $percentage%)")
                            }
                        }
                    }
                } else {
                    Log.w(TAG, "⚠️ 查询任务状态失败: ${statusResponse.code()}, ${statusResponse.message()}")
                    // 查询失败，使用更长的间隔（指数退避）
                    nextPollInterval = (nextPollInterval * 1.5).toLong().coerceAtMost(30000L) // 最多30秒
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ 轮询任务状态异常", e)
                // 异常时使用更长的间隔（指数退避）
                nextPollInterval = (nextPollInterval * 1.5).toLong().coerceAtMost(30000L) // 最多30秒
                
                if (pollCount >= maxPolls) {
                    // 达到最大轮询次数，放弃
                    taskMap.values.forEach { task ->
                        task.onError(Exception("轮询超时：任务处理时间过长"))
                    }
                    return
                }
            }
        }
        
        // 达到最大轮询次数
        Log.e(TAG, "❌ 轮询超时：已达到最大轮询次数 ($maxPolls)")
        taskMap.values.forEach { task ->
            task.onError(Exception("轮询超时：任务处理时间过长（超过30分钟）"))
        }
    }
    
    /**
     * 处理批量请求
     */
    private suspend fun processBatchRequest(batch: List<RequestTask>) {
        // API请求已禁用，直接返回
        if (!API_ENABLED) {
            Log.d(TAG, "API请求已禁用，跳过批量请求: ${batch.size} 个题目")
            return
        }
        try {
            Log.i(TAG, "========== 开始处理批量API请求 ==========")
            Log.i(TAG, "批次大小: ${batch.size}")
            
            // 构建批量请求
            val batchItems = mutableListOf<BatchQuestionItem>()
            val taskMap = mutableMapOf<Int, RequestTask>() // 索引映射
            
            batch.forEachIndexed { index, task ->
                // 读取图片（图片现在保存在应用私有目录，可以直接使用 File 读取）
                val imageFile = File(task.question.imagePath)
                if (!imageFile.exists()) {
                    Log.e(TAG, "图片文件不存在: ${task.question.imagePath}")
                    task.onError(Exception("图片文件不存在"))
                    return@forEachIndexed
                }
                
                // 读取图片并转换为 base64
                val imageBytes = imageFile.readBytes()
                val imageBase64 = AndroidBase64.encodeToString(imageBytes, AndroidBase64.NO_WRAP)
                
                // 从文件路径提取文件名
                val filename = imageFile.name
                
                // 检测图片类型（根据文件扩展名）
                val mimeType = when (filename.substringAfterLast('.', "").lowercase()) {
                    "png" -> "image/png"
                    "gif" -> "image/gif"
                    "webp" -> "image/webp"
                    "bmp" -> "image/bmp"
                    else -> "image/jpeg"  // 默认使用 jpeg
                }
                
                // 构建完整的 data URL 格式：data:image/jpeg;base64,xxxxx
                val dataUrl = "data:$mimeType;base64,$imageBase64"
                
                val batchItem = BatchQuestionItem(
                    filename = filename,
                    data = dataUrl
                )
                
                batchItems.add(batchItem)
                taskMap[index] = task
            }
            
            if (batchItems.isEmpty()) {
                Log.e(TAG, "批量请求中没有有效题目")
                return
            }
            
            val batchRequest = BatchQuestionRequest(
                images = batchItems,
                maxWorkers = 10  // 设置最大并发工作线程数
            )
            
            // 计算请求体大小（估算）
            val estimatedSize = batchItems.sumOf { 
                it.data.length + it.filename.length
            }
            
            // 使用异步接口（避免超时问题）
            Log.i(TAG, "📡 ========== 提交异步批量处理任务 ==========")
            Log.i(TAG, "   - 完整URL: ${ApiClient.BASE_URL}api/questions/extract/batch/async")
            Log.i(TAG, "   - 方法: POST")
            Log.i(TAG, "   - 图片数量: ${batchItems.size}")
            Log.i(TAG, "   - 请求体大小（估算）: ${estimatedSize / 1024} KB")
            Log.i(TAG, "   - Content-Type: application/json")
            Log.i(TAG, "   - BatchQuestionRequest.images.size: ${batchRequest.images.size}")
            Log.i(TAG, "   - max_workers: ${batchRequest.maxWorkers}")
            batchItems.forEachIndexed { index, item ->
                val dataPrefix = item.data.take(50) // 显示 data URL 的前缀
                Log.d(TAG, "   - 图片 #${index + 1}: filename=${item.filename}, data长度=${item.data.length}, data前缀=${dataPrefix}...")
            }
            Log.i(TAG, "📡 正在提交异步任务...")
            
            // 1. 提交异步任务
            val submitResponse = try {
                if (!API_ENABLED) {
                    Log.d(TAG, "API请求已禁用，跳过提交批量任务")
                    return
                }
                retryOnTimeout(maxRetries = 2) {
                    ApiClient.questionApiService.submitBatchAsync(batchRequest)
                }
            } catch (e: java.net.UnknownHostException) {
                Log.e(TAG, "❌ 网络连接失败：无法解析主机名")
                Log.e(TAG, "   - 错误: ${e.message}")
                Log.e(TAG, "   - 请检查 BASE_URL 配置是否正确: ${ApiClient.BASE_URL}")
                Log.e(TAG, "   - 如果是模拟器，请使用: http://10.0.2.2:5000/")
                Log.e(TAG, "   - 如果是真机，请使用电脑的局域网IP，如: http://192.168.1.100:5000/")
                throw e
            } catch (e: java.net.ConnectException) {
                Log.e(TAG, "❌ 网络连接失败：无法连接到服务器")
                Log.e(TAG, "   - 错误: ${e.message}")
                Log.e(TAG, "   - 请确保后端服务正在运行")
                Log.e(TAG, "   - 请检查 BASE_URL 配置: ${ApiClient.BASE_URL}")
                throw e
            } catch (e: java.net.SocketTimeoutException) {
                Log.e(TAG, "❌ 网络请求超时")
                Log.e(TAG, "   - 错误: ${e.message}")
                Log.e(TAG, "   - 请检查网络连接或增加超时时间")
                throw e
            } catch (e: retrofit2.HttpException) {
                Log.e(TAG, "❌ HTTP错误")
                Log.e(TAG, "   - 状态码: ${e.code()}")
                Log.e(TAG, "   - 错误消息: ${e.message()}")
                val errorBody = e.response()?.errorBody()?.string()
                Log.e(TAG, "   - 错误体: $errorBody")
                throw e
            } catch (e: com.google.gson.JsonSyntaxException) {
                Log.e(TAG, "❌ JSON解析失败：后端返回的数据格式不正确")
                Log.e(TAG, "   - 错误: ${e.message}")
                Log.e(TAG, "   - 请检查后端API返回的数据格式是否匹配")
                throw e
            }
            
            Log.i(TAG, "📥 ========== 收到异步任务提交响应 ==========")
            Log.i(TAG, "   - 状态码: ${submitResponse.code()}")
            Log.i(TAG, "   - 是否成功: ${submitResponse.isSuccessful}")
            
            if (submitResponse.isSuccessful && submitResponse.body() != null) {
                val submitBody = submitResponse.body()!!
                val taskId = submitBody.taskId
                Log.i(TAG, "✅ 异步任务提交成功")
                Log.i(TAG, "   - 任务ID: $taskId")
                Log.i(TAG, "   - 消息: ${submitBody.message}")
                
                // 2. 轮询查询任务状态直到完成
                Log.i(TAG, "🔄 开始轮询任务状态...")
                pollTaskUntilComplete(taskId, taskMap, batchItems.size)
                
            } else {
                val errorMsg = submitResponse.message() ?: "未知错误"
                val errorBody = submitResponse.errorBody()?.string()
                Log.e(TAG, "❌ 异步任务提交失败")
                Log.e(TAG, "   - 状态码: ${submitResponse.code()}")
                Log.e(TAG, "   - 错误消息: $errorMsg")
                Log.e(TAG, "   - 错误体: $errorBody")
                
                // 所有题目都失败
                batch.forEach { task ->
                    task.onError(Exception("提交异步任务失败: $errorMsg (${submitResponse.code()})"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "========== 处理批量请求异常 ==========")
            Log.e(TAG, "异常类型: ${e.javaClass.name}")
            Log.e(TAG, "异常消息: ${e.message}")
            Log.e(TAG, "BASE_URL: ${ApiClient.BASE_URL}")
            
            // 根据异常类型提供更详细的错误信息
            when (e) {
                is java.net.UnknownHostException -> {
                    Log.e(TAG, "❌ 无法解析主机名，请检查 BASE_URL 配置")
                    Log.e(TAG, "   当前配置: ${ApiClient.BASE_URL}")
                    Log.e(TAG, "   建议：")
                    Log.e(TAG, "   - Android 模拟器: http://10.0.2.2:5000/")
                    Log.e(TAG, "   - Android 真机: http://你的电脑IP:5000/")
                }
                is java.net.ConnectException -> {
                    Log.e(TAG, "❌ 无法连接到服务器，请确保后端服务正在运行")
                    Log.e(TAG, "   检查步骤：")
                    Log.e(TAG, "   1. 确认后端服务已启动（运行在 http://127.0.0.1:5000）")
                    Log.e(TAG, "   2. 确认 BASE_URL 配置正确")
                    Log.e(TAG, "   3. 如果是真机，确保手机和电脑在同一WiFi网络")
                }
                is java.net.SocketTimeoutException -> {
                    Log.e(TAG, "❌ 请求超时，可能是网络慢或后端处理时间过长")
                }
                is retrofit2.HttpException -> {
                    Log.e(TAG, "❌ HTTP错误: ${e.code()}")
                    val errorBody = e.response()?.errorBody()?.string()
                    Log.e(TAG, "   错误体: $errorBody")
                }
                is com.google.gson.JsonSyntaxException -> {
                    Log.e(TAG, "❌ JSON解析失败，后端返回的数据格式可能不匹配")
                }
                else -> {
                    Log.e(TAG, "❌ 未知异常")
                    e.printStackTrace()
                }
            }
            Log.e(TAG, "=====================================")
            
            // 批量请求异常，所有题目都失败
            batch.forEach { task ->
                try {
                    task.onError(e)
                } catch (e2: Exception) {
                    Log.e(TAG, "调用错误回调失败", e2)
                }
            }
        }
    }
    
    /**
     * 处理单个请求（保留用于兼容性）
     */
    private suspend fun processRequest(task: RequestTask) {
        try {
            Log.i(TAG, "========== 开始处理API请求 ==========")
            Log.i(TAG, "题目ID: ${task.question.id}")
            Log.i(TAG, "图片路径: ${task.question.imagePath}")
            Log.i(TAG, "题目类型: ${task.question.questionType}")
            Log.i(TAG, "当前活跃请求数: $activeRequests")
            
            val imageFile = File(task.question.imagePath)
            if (!imageFile.exists()) {
                Log.e(TAG, "图片文件不存在: ${task.question.imagePath}")
                task.onError(Exception("图片文件不存在"))
                return
            }
            
            // 创建 MultipartBody.Part
            val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
            
            // 创建其他字段（可选参数）
            val rawTextPart = task.question.rawText.takeIf { it.isNotBlank() }
                ?.toRequestBody("text/plain".toMediaTypeOrNull())
            val questionTextPart = task.question.questionText.takeIf { it.isNotBlank() }
                ?.toRequestBody("text/plain".toMediaTypeOrNull())
            val optionsPart = task.question.options.takeIf { it.isNotBlank() }
                ?.toRequestBody("text/plain".toMediaTypeOrNull())
            val questionTypePart = task.question.questionType.takeIf { it.isNotBlank() }
                ?.toRequestBody("text/plain".toMediaTypeOrNull())
            
            val forceReanalyzePart = null
            
            // 发送请求
            Log.i(TAG, "📡 发送HTTP请求到后端...")
            Log.i(TAG, "   - 接口: POST /api/questions/analyze")
            Log.i(TAG, "   - 图片文件: ${imageFile.name} (${imageFile.length()} bytes)")
            Log.i(TAG, "   - raw_text: ${rawTextPart != null}")
            Log.i(TAG, "   - question_text: ${questionTextPart != null}")
            Log.i(TAG, "   - options: ${optionsPart != null}")
            
            if (!API_ENABLED) {
                Log.d(TAG, "API请求已禁用，跳过分析题目请求")
                return
            }
            val response = ApiClient.questionApiService.analyzeQuestion(
                image = imagePart,
                rawText = rawTextPart,
                questionText = questionTextPart,
                options = optionsPart,
                questionType = questionTypePart,
                forceReanalyze = forceReanalyzePart
            )
            
            Log.i(TAG, "📥 收到HTTP响应")
            Log.i(TAG, "   - 状态码: ${response.code()}")
            Log.i(TAG, "   - 是否成功: ${response.isSuccessful}")
            Log.i(TAG, "   - 响应消息: ${response.message()}")
            
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                Log.i(TAG, "✅ API请求成功")
                Log.i(TAG, "   - 后端题目ID: ${body.id}")
                Log.i(TAG, "   - from_cache: ${body.fromCache}")
                Log.i(TAG, "   - is_duplicate: ${body.isDuplicate}")
                Log.i(TAG, "   - saved_to_db: ${body.savedToDb}")
                Log.i(TAG, "========== API请求处理完成 ==========")
                task.onSuccess(body)
            } else {
                val errorMsg = response.message() ?: "未知错误"
                val errorBody = response.errorBody()?.string()
                Log.e(TAG, "❌ API请求失败")
                Log.e(TAG, "   - 状态码: ${response.code()}")
                Log.e(TAG, "   - 错误消息: $errorMsg")
                Log.e(TAG, "   - 错误体: $errorBody")
                Log.e(TAG, "========== API请求处理失败 ==========")
                task.onError(Exception("请求失败: $errorMsg (${response.code()})"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理请求异常: ${task.question.id}", e)
            task.onError(e)
        }
    }
    
    /**
     * 添加请求到批次缓冲区（批量收集，达到批次大小时一次性发送）
     * @param question 题目
     * @param onSuccess 成功回调
     * @param onError 失败回调
     */
    suspend fun enqueue(
        question: Question,
        onSuccess: suspend (QuestionContentResponse) -> Unit,
        onError: suspend (Throwable) -> Unit
    ) {
        // API请求已禁用，直接返回
        if (!API_ENABLED) {
            Log.d(TAG, "API请求已禁用，跳过请求: ${question.id}")
            return
        }
        
        try {
            Log.i(TAG, "========== 准备加入批次 ==========")
            Log.i(TAG, "题目ID: ${question.id}")
            Log.i(TAG, "图片路径: ${question.imagePath}")
            Log.i(TAG, "题目类型: ${question.questionType}")
            
            val task = RequestTask(question, onSuccess, onError)
            
            // 快速获取锁，只做必要的操作，避免长时间持有锁
            val batchToSend: List<RequestTask>?
            batchLock.lock()
            try {
                // 更新最后批次时间（当第一个题目加入时）
                if (batchBuffer.isEmpty()) {
                    lastBatchTime = System.currentTimeMillis()
                    Log.d(TAG, "批次缓冲区为空，重置批次时间")
                }
                
                batchBuffer.add(task)
                val currentBatchSize = batchSize
                val currentBufferSize = batchBuffer.size
                Log.i(TAG, "✅ 请求已加入批次缓冲区: $currentBufferSize/$currentBatchSize")
                
                // 如果批次已满，准备发送，但不在锁内发送（避免阻塞）
                if (currentBufferSize >= currentBatchSize) {
                    val batch = batchBuffer.toList()
                    batchBuffer.clear()
                    lastBatchTime = System.currentTimeMillis()
                    batchToSend = batch
                } else {
                    batchToSend = null
                }
            } finally {
                batchLock.unlock()
            }
            
            // 在锁外发送，避免阻塞识别过程
            if (batchToSend != null) {
                scope.launch {
                    try {
                        Log.i(TAG, "📦 批次已满，立即发送批量请求: ${batchToSend.size} 个题目")
                        
                        // 发送批量请求
                        activeRequests++
                        activeRequestsFlow.value = activeRequests
                        
                        processBatchRequest(batchToSend)
                    } catch (e: Exception) {
                        Log.e(TAG, "批量请求处理异常", e)
                        e.printStackTrace()
                        // 批量请求失败，逐个调用错误回调
                        batchToSend.forEach { task ->
                            try {
                                task.onError(e)
                            } catch (e2: Exception) {
                                Log.e(TAG, "调用错误回调失败", e2)
                            }
                        }
                    } finally {
                        activeRequests--
                        activeRequestsFlow.value = activeRequests
                    }
                }
            }
            
            // enqueue 立即返回，不等待发送完成，识别过程可以继续
            
            Log.i(TAG, "   - 题目ID: ${question.id}")
            Log.i(TAG, "   - 图片路径: ${question.imagePath}")
            Log.i(TAG, "   - 当前批次状态: ${batchBuffer.size}/$batchSize, 活跃请求=$activeRequests")
            Log.i(TAG, "========== 加入批次完成 ==========")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 加入批次失败", e)
            e.printStackTrace()
            // 即使加入批次失败，也尝试调用错误回调
            try {
                onError(e)
            } catch (e2: Exception) {
                Log.e(TAG, "调用错误回调失败", e2)
            }
        }
    }
    
    /**
     * 根据题量动态调整批次大小
     * @param totalTextQuestions 检测到的文字题总数
     */
    fun adjustConcurrency(totalTextQuestions: Int) {
        val newBatchSize = if (totalTextQuestions >= THRESHOLD) {
            BATCH_SIZE_LARGE
        } else {
            BATCH_SIZE_SMALL
        }
        
        if (newBatchSize != batchSize) {
            val oldSize = batchSize
            batchSize = newBatchSize
            Log.i(TAG, "📊 动态调整批次大小: $oldSize -> $newBatchSize (检测到 $totalTextQuestions 道文字题)")
        }
    }
    
    /**
     * 获取当前批次大小
     */
    fun getMaxConcurrency(): Int = batchSize
    
    /**
     * 强制刷新批次（立即发送缓冲区中的所有请求）
     */
    suspend fun flushBatch() {
        // API请求已禁用，直接返回
        if (!API_ENABLED) {
            Log.d(TAG, "API请求已禁用，跳过刷新批次")
            return
        }
        
        // 在锁内只复制数据，避免在锁内调用 suspend 函数
        val batchCopy: List<RequestTask>
        batchLock.lock()
        try {
            if (batchBuffer.isEmpty()) {
                return // 没有数据需要刷新
            }
            batchCopy = batchBuffer.toList()
            batchBuffer.clear()
            lastBatchTime = System.currentTimeMillis()
        } finally {
            batchLock.unlock()
        }
        
        Log.i(TAG, "🔄 强制刷新批次，发送批量请求: ${batchCopy.size} 个题目")
        
        activeRequests++
        activeRequestsFlow.value = activeRequests
        
        try {
            processBatchRequest(batchCopy)
        } catch (e: Exception) {
            Log.e(TAG, "强制刷新批次失败", e)
            batchCopy.forEach { task ->
                try {
                    task.onError(e)
                } catch (e2: Exception) {
                    Log.e(TAG, "调用错误回调失败", e2)
                }
            }
        } finally {
            activeRequests--
            activeRequestsFlow.value = activeRequests
        }
    }
    
    /**
     * 获取队列长度（近似值）
     */
    fun getQueueSize(): Int {
        return requestQueue.tryReceive().let { 
            if (it.isSuccess) {
                // 如果成功接收，说明队列不为空，但无法准确知道长度
                // 这里返回一个近似值
                return@let -1
            } else {
                return@let 0
            }
        }
    }
}
