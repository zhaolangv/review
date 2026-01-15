package com.gongkao.cuotifupan.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.gongkao.cuotifupan.MainActivity
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.api.QuestionApiQueue
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.detector.QuestionDetector
import com.gongkao.cuotifupan.ocr.TextRecognizer
import com.gongkao.cuotifupan.util.PreferencesManager
import kotlinx.coroutines.*
import org.json.JSONArray
import java.io.File

/**
 * 前台服务：实时监听相册新图片
 */
class ImageMonitorService : Service() {
    
    private val TAG = "ImageMonitorService"
    private val CHANNEL_ID = "image_monitor_service"
    private val NOTIFICATION_ID = 1000
    
    private var contentObserver: ContentObserver? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var lastProcessedImageId: Long = 0
    private val processingLock = java.util.concurrent.atomic.AtomicBoolean(false)  // 处理锁，防止并发重复处理
    private val processingPaths = java.util.concurrent.ConcurrentHashMap<String, Boolean>()  // 正在处理的图片路径集合
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "========== 服务创建 ==========")
        
        try {
            // 创建前台通知
            createNotificationChannel()
            val notification = createForegroundNotification()
            startForeground(NOTIFICATION_ID, notification)
            Log.d(TAG, "前台通知已创建")
            
            // 开始监听（实时监听不需要权限，ContentObserver会在权限授予后自动工作）
            startMonitoring()
            Log.d(TAG, "========== 服务启动成功 ==========")
        } catch (e: Exception) {
            Log.e(TAG, "服务创建失败", e)
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务启动")
        
        // 检查是否是权限授予后的启动
        val permissionGranted = intent?.getBooleanExtra("permission_granted", false) ?: false
        if (permissionGranted) {
            Log.d(TAG, "🔓 权限已授予，开始扫描...")
            triggerScan()
        }
        
        // 检查是否有自定义扫描数量（重新扫描时使用）
        val scanLimit = intent?.getIntExtra("scan_limit", -1) ?: -1
        if (scanLimit > 0) {
            Log.d(TAG, "🔄 重新扫描模式，扫描数量: $scanLimit")
            serviceScope.launch {
                lastProcessedImageId = PreferencesManager.getLastProcessedImageId(applicationContext)
                scanRecentImages(scanLimit, isFirstLaunch = false)
            }
        }
        
        return START_STICKY // 服务被杀后自动重启
    }
    
    /**
     * 触发扫描
     */
    private fun triggerScan() {
        serviceScope.launch {
            val isFirstLaunch = PreferencesManager.isFirstLaunch(applicationContext)
            
            if (isFirstLaunch) {
                Log.i(TAG, "🎉 首次启动，扫描最近150张图片...")
                Log.i(TAG, "   这将检测题目并调用后端API获取题目内容")
                scanRecentImages(50, isFirstLaunch = true)
                PreferencesManager.setFirstLaunchCompleted(applicationContext)
            } else {
                // 快速检查：是否有新图片
                Log.d(TAG, "🔍 检查是否有新图片需要处理...")
                val needScan = checkIfNeedScan()
                
                if (needScan) {
                    Log.i(TAG, "🔍 检测到新图片，开始检查...")
                    Log.i(TAG, "   将检测题目并调用后端API获取题目内容")
                    lastProcessedImageId = PreferencesManager.getLastProcessedImageId(applicationContext)
                    scanRecentImages(50, isFirstLaunch = false)
                } else {
                    Log.i(TAG, "✅ 没有新图片，跳过扫描")
                    Log.i(TAG, "   提示：如果没有看到API调用，可能是因为：")
                    Log.i(TAG, "   1. 没有新图片需要处理")
                    Log.i(TAG, "   2. 图片不是题目（会被跳过）")
                    Log.i(TAG, "   3. 检测到的是图推题（图推题不调用后端API）")
                    Log.i(TAG, "   4. 可以手动导入图片测试API调用")
                }
            }
            
            Log.d(TAG, "✅ 启动检查完成")
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "服务销毁")
        stopMonitoring()
        serviceScope.cancel()
    }
    
    /**
     * 开始监听相册
     */
    private fun startMonitoring() {
        try {
            contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    super.onChange(selfChange)
                    Log.d(TAG, "🔔 检测到相册变化（无URI）")
                    handleChange()
                }
                
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    Log.d(TAG, "🔔 检测到相册变化: $uri")
                    handleChange()
                }
                
                override fun onChange(selfChange: Boolean, uris: Collection<Uri>, flags: Int) {
                    super.onChange(selfChange, uris, flags)
                    Log.d(TAG, "🔔 检测到相册变化（多个）: ${uris.size} 张图片")
                    handleChange()
                }
                
                private fun handleChange() {
                    // 防止重复触发：如果正在处理，跳过
                    if (processingLock.get()) {
                        Log.d(TAG, "⏸️ 已有处理任务在进行，跳过本次触发")
                        return
                    }
                    
                    // 在协程中处理新图片
                    serviceScope.launch {
                        // 先快速检查最新图片名称，判断是否需要更长的延迟
                        val latestImageName = try {
                            val projection = arrayOf(MediaStore.Images.Media.DISPLAY_NAME)
                            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                            } else {
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                            }
                            val cursor = contentResolver.query(
                                uri,
                                projection,
                                null,
                                null,
                                "${MediaStore.Images.Media.DATE_ADDED} DESC"
                            )
                            cursor?.use {
                                if (it.moveToFirst()) {
                                    it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                                } else null
                            } ?: null
                        } catch (e: Exception) {
                            null
                        }
                        
                        // 如果是编辑后的图片，使用更长的延迟（3秒）
                        // 普通图片使用较短的延迟（1秒）
                        val delayTime = if (latestImageName?.contains("_edited_", ignoreCase = true) == true) {
                            Log.d(TAG, "检测到编辑后的图片，使用更长的延迟（3秒）")
                            3000L
                        } else {
                            1000L
                        }
                        
                        delay(delayTime) // 延迟，确保文件写入完成
                        checkAndProcessNewImage()
                    }
                }
            }.also {
                contentResolver.registerContentObserver(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    true,
                    it
                )
                Log.d(TAG, "✅ ContentObserver 已注册到: ${MediaStore.Images.Media.EXTERNAL_CONTENT_URI}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动监听失败", e)
        }
    }
    
    /**
     * 停止监听
     */
    private fun stopMonitoring() {
        contentObserver?.let {
            contentResolver.unregisterContentObserver(it)
        }
        contentObserver = null
        Log.d(TAG, "停止监听")
    }
    
    /**
     * 快速检查是否需要扫描（通过比较最新图片的时间戳和ID）
     */
    private suspend fun checkIfNeedScan(): Boolean = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            // Android 13+ 需要使用不同的 URI
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val latestId = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val latestTimestamp = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                    
                    val savedId = PreferencesManager.getLastProcessedImageId(applicationContext)
                    val savedTimestamp = PreferencesManager.getLatestImageTimestamp(applicationContext)
                    
                    Log.d(TAG, "快速检查 - 当前最新: ID=$latestId, Time=$latestTimestamp")
                    Log.d(TAG, "快速检查 - 上次记录: ID=$savedId, Time=$savedTimestamp")
                    
                    // 如果ID和时间戳都相同，说明没有新图片
                    if (latestId == savedId && latestTimestamp == savedTimestamp) {
                        Log.d(TAG, "✅ 最新图片未变化，无需扫描")
                        return@withContext false
                    }
                    
                    // 如果ID更大，说明有新图片
                    if (latestId > savedId) {
                        Log.d(TAG, "🆕 发现新图片 (ID从 $savedId 增加到 $latestId)")
                        return@withContext true
                    }
                    
                    // 时间戳变化了，可能有图片被删除或修改
                    if (latestTimestamp != savedTimestamp) {
                        Log.d(TAG, "⚠️ 图片时间戳变化，需要检查")
                        return@withContext true
                    }
                }
            }
            
            // 如果查询失败，安全起见进行扫描
            return@withContext true
            
        } catch (e: Exception) {
            Log.e(TAG, "快速检查失败", e)
            return@withContext true  // 出错时默认需要扫描
        }
    }
    
    /**
     * 获取最新图片ID
     */
    private fun getLatestImageId(): Long {
        try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            
            // Android 13+ 需要使用不同的 URI
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    return it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取最新图片ID失败", e)
        }
        return 0
    }
    
    /**
     * 检查并处理新图片
     */
    private suspend fun checkAndProcessNewImage() = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "📷 开始检查新图片...")
            
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            
            // Android 13+ 需要使用不同的 URI
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            
            if (cursor == null) {
                Log.e(TAG, "❌ 无法查询相册，cursor 为 null")
                return@withContext
            }
            
            cursor.use {
                val count = it.count
                Log.d(TAG, "📷 查询到 $count 张图片")
                
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    
                    // Android 10+ DATA字段可能为null，需要处理
                    val dataIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                    val path = if (dataIndex >= 0 && !it.isNull(dataIndex)) {
                        it.getString(dataIndex)
                    } else {
                        // Android 10+ 使用URI方式
                        val imageUri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        imageUri.toString()
                    }
                    
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                    
                    Log.d(TAG, "📷 最新图片: $name")
                    Log.d(TAG, "   ID: $id, 上次处理ID: $lastProcessedImageId")
                    Log.d(TAG, "   路径: $path")
                    Log.d(TAG, "   时间: $dateAdded")
                    Log.d(TAG, "   是否为URI: ${path.startsWith("content://")}")
                    
                    // 只处理新图片（ID更大的图片）
                    if (id > lastProcessedImageId) {
                        Log.d(TAG, "✅ 发现新图片（ID: $id > $lastProcessedImageId），准备处理")
                        // 检查是否正在处理或已处理过
                        if (processingPaths.containsKey(path)) {
                            Log.d(TAG, "⏭️ 图片正在处理中，跳过: $name")
                            return@withContext
                        }
                        
                        // 检查数据库中是否已存在
                        val database = AppDatabase.getDatabase(applicationContext)
                        val existingQuestions = database.questionDao().getAllQuestionsSync()
                        // 检查路径匹配（包括原始路径、原图路径和擦写后的路径）
                        val isInDatabase = existingQuestions.any { question ->
                            question.imagePath == path || 
                            question.originalImagePath == path || 
                            question.cleanedImagePath == path
                        }
                        if (isInDatabase) {
                            Log.d(TAG, "⏭️ 图片已在数据库中，跳过: $name")
                            // 更新lastProcessedImageId，避免重复检查
                            lastProcessedImageId = id
                            PreferencesManager.saveLastProcessedImageId(applicationContext, id)
                            return@withContext
                        }
                        
                        // 检查是否在已排除列表中
                        val excludedPaths = database.excludedImageDao().getAllPaths()
                        if (excludedPaths.contains(path)) {
                            Log.d(TAG, "⏭️ 图片已在排除列表中，跳过: $name")
                            // 更新lastProcessedImageId，避免重复检查
                            lastProcessedImageId = id
                            PreferencesManager.saveLastProcessedImageId(applicationContext, id)
                            return@withContext
                        }
                        
                        // 尝试获取处理锁
                        if (!processingLock.compareAndSet(false, true)) {
                            Log.d(TAG, "⏭️ 其他线程正在处理，跳过: $name")
                            return@withContext
                        }
                        
                        try {
                            // 标记为正在处理
                            processingPaths[path] = true
                            
                            Log.d(TAG, "🆕 发现新图片，开始处理...")
                            lastProcessedImageId = id
                            PreferencesManager.saveLastProcessedImageId(applicationContext, id)
                            
                            // 处理图片
                            processImage(path, name)
                        } finally {
                            // 释放锁和标记
                            processingLock.set(false)
                            processingPaths.remove(path)
                        }
                    } else {
                        Log.d(TAG, "⏭️ 不是新图片，跳过")
                    }
                } else {
                    Log.d(TAG, "📷 相册中没有图片")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 检查新图片失败", e)
        }
    }
    
    /**
     * 扫描最近的图片
     * @param limit 扫描数量
     * @param isFirstLaunch 是否首次启动
     */
    private suspend fun scanRecentImages(limit: Int, isFirstLaunch: Boolean) = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔍 开始扫描最近 $limit 张图片...")
            
            // 获取已处理的图片ID集合（通过数据库）
            val database = AppDatabase.getDatabase(applicationContext)
            val processedImagePaths = mutableSetOf<String>()
            val processedImageSizes = mutableSetOf<Long>() // 已处理图片的文件大小集合（用于去重）
            
            // 如果不是首次启动，获取已存在的图片路径（包括题目和已排除的图片）
            if (!isFirstLaunch) {
                // 获取已保存的题目路径
                val existingQuestions = database.questionDao().getAllQuestionsSync()
                existingQuestions.forEach { question ->
                    processedImagePaths.add(question.imagePath)
                    // 同时记录文件大小用于去重（通过文件大小匹配，因为复制后大小应该相同）
                    try {
                        val file = java.io.File(question.imagePath)
                        if (file.exists()) {
                            val fileSize = file.length()
                            if (fileSize > 0) {
                                processedImageSizes.add(fileSize)
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                }
                Log.d(TAG, "📋 已有 ${processedImagePaths.size} 道题目在数据库中，记录了 ${processedImageSizes.size} 个文件大小")
                
                // 获取已排除的图片路径
                val excludedPaths = database.excludedImageDao().getAllPaths()
                excludedPaths.forEach { excludedPath ->
                    processedImagePaths.add(excludedPath)
                    // 同时记录已排除图片的文件大小用于去重
                    try {
                        val file = java.io.File(excludedPath)
                        if (file.exists()) {
                            val fileSize = file.length()
                            if (fileSize > 0) {
                                processedImageSizes.add(fileSize)
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                }
                Log.d(TAG, "🚫 已有 ${excludedPaths.size} 张图片被排除")
            }
            
            // 显示扫描进度通知
            showScanningNotification(0, limit)
            
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            // Android 13+ 需要使用不同的 URI
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            
            if (cursor == null) {
                Log.e(TAG, "❌ 无法查询相册")
                return@withContext
            }
            
            var scannedCount = 0
            var processedCount = 0
            var skippedCount = 0
            var foundQuestions = 0
            var foundTextQuestions = 0 // 统计文字题数量，用于动态调整并发数
            
            cursor.use {
                val accessibleImageCount = it.count
                
                // 如果用户选择了"访问部分"权限，实际可访问的图片数量会小于预设的limit
                // 在这种情况下，使用实际可访问的图片数量作为扫描上限
                val actualLimit = if (accessibleImageCount < limit) {
                    Log.i(TAG, "📷 检测到部分权限：可访问 $accessibleImageCount 张图片（小于预设的 $limit 张），将按实际可访问数量扫描")
                    accessibleImageCount
                } else {
                    limit
                }
                
                val totalToScan = minOf(accessibleImageCount, actualLimit)
                Log.d(TAG, "📷 相册共有 $accessibleImageCount 张可访问图片，将检查最近 $totalToScan 张")
                
                while (it.moveToNext() && scannedCount < actualLimit) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    
                    // Android 10+ DATA字段可能为null，需要处理
                    val dataIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                    val path = if (dataIndex >= 0 && !it.isNull(dataIndex)) {
                        it.getString(dataIndex)
                    } else {
                        // Android 10+ 使用URI方式
                        val imageUri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        imageUri.toString()
                    }
                    
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    
                    scannedCount++
                    
                    // 检查是否已处理过（通过路径和文件大小双重检查）
                    var isProcessed = false
                    
                    // 1. 先检查路径是否直接匹配
                    if (path in processedImagePaths) {
                        isProcessed = true
                        Log.d(TAG, "⏭️ 通过路径匹配检测到已处理: $name")
                    } else {
                        // 2. 如果路径不匹配，通过文件大小来判断（复制后文件大小应该相同）
                        // 注意：Android 10+ 如果path是URI，无法直接获取文件大小，需要跳过大小检查
                        if (!path.startsWith("content://")) {
                            try {
                                val currentFile = java.io.File(path)
                                if (currentFile.exists()) {
                                    val currentSize = currentFile.length()
                                    if (currentSize > 0 && currentSize in processedImageSizes) {
                                        isProcessed = true
                                        Log.d(TAG, "⏭️ 通过文件大小检测到已处理: $name (大小: $currentSize)")
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略检查错误，继续处理
                            }
                        }
                    }
                    
                    if (isProcessed) {
                        skippedCount++
                        Log.d(TAG, "⏭️ 跳过已处理: $name")
                        continue
                    }
                    
                    // 快速验证文件是否存在且有效（避免处理无效文件）
                    if (!quickValidateImageFile(path, name)) {
                        skippedCount++
                        Log.d(TAG, "⏭️ 跳过无效文件: $name (文件不存在、为空或无法解码)")
                        continue
                    }
                    
                    processedCount++
                    
                    // 更新进度通知（每5张或最后一张时更新）
                    if (scannedCount % 5 == 0 || scannedCount >= limit || scannedCount >= totalToScan) {
                        showScanningNotification(scannedCount, totalToScan)
                    }
                    
                    Log.d(TAG, "🔍 处理第 $processedCount 张新图片: $name")
                    
                    // 处理图片（使用try-catch确保单个图片处理失败不会中断整个扫描）
                    val isQuestion = try {
                        processImageSilently(path, name)
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 处理图片时发生未捕获的异常: $name", e)
                        Log.e(TAG, "   异常类型: ${e.javaClass.simpleName}")
                        Log.e(TAG, "   异常消息: ${e.message}")
                        e.printStackTrace()
                        false // 处理失败，继续处理下一张图片
                    }
                    
                    if (isQuestion) {
                        foundQuestions++
                        Log.d(TAG, "✅ 发现题目 #$foundQuestions")
                        
                        // 检查刚插入的题目是否为文字题，并更新并发数
                        try {
                            val allQuestions = database.questionDao().getAllQuestionsSync()
                            val newQuestion = allQuestions.firstOrNull { it.imagePath == path }
                            if (newQuestion != null && newQuestion.questionType == "TEXT") {
                                foundTextQuestions++
                                // 动态调整并发数：根据文字题总数
                                com.gongkao.cuotifupan.api.QuestionApiQueue.adjustConcurrency(foundTextQuestions)
                                Log.d(TAG, "📊 已检测到 $foundTextQuestions 道文字题，当前并发数: ${com.gongkao.cuotifupan.api.QuestionApiQueue.getMaxConcurrency()}")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 更新题目统计时发生异常: $name", e)
                            // 继续处理，不影响后续图片
                        }
                    } else {
                        // 不是题目，记录为已排除的图片（避免重复检测）
                        try {
                            val excludedImage = com.gongkao.cuotifupan.data.ExcludedImage(
                                imagePath = path,
                                reason = "检测后不是题目"
                            )
                            database.excludedImageDao().insert(excludedImage)
                            Log.d(TAG, "🚫 已排除: $name")
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ 记录排除图片时发生异常: $name", e)
                            // 继续处理，不影响后续图片
                        }
                    }
                    
                    // 记录最新处理的ID和时间戳
                    if (scannedCount == 1) {
                        lastProcessedImageId = id
                        PreferencesManager.saveLastProcessedImageId(applicationContext, id)
                        
                        // 保存时间戳
                        val timestampIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                        val timestamp = it.getLong(timestampIndex)
                        PreferencesManager.saveLatestImageTimestamp(applicationContext, timestamp)
                    }
                    
                    // 避免处理太快，稍微延迟
                    delay(200)
                }
                
                // 扫描循环结束，更新最后一次进度通知
                if (scannedCount > 0) {
                    showScanningNotification(scannedCount, totalToScan)
                }
            }
            
            Log.d(TAG, "✅ 扫描完成：检查了 $scannedCount 张图片，跳过 $skippedCount 张，处理了 $processedCount 张，发现 $foundQuestions 道题目")
            
            // 扫描完成时，强制刷新批次，确保所有题目都被发送
            if (foundTextQuestions > 0) {
                try {
                    com.gongkao.cuotifupan.api.QuestionApiQueue.flushBatch()
                    Log.d(TAG, "🔄 扫描完成，已刷新批次，确保所有题目请求都已发送")
                } catch (e: Exception) {
                    Log.e(TAG, "刷新批次失败", e)
                }
            }
            
            // 总是显示完成通知（即使没有发现题目或没有处理图片）
            showScanCompleteNotification(foundQuestions, scannedCount, processedCount)
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ 批量扫描失败", e)
        }
    }
    
    /**
     * 快速验证图片文件是否有效（用于扫描阶段过滤）
     * @param imagePath 图片路径
     * @param imageName 图片名称（用于日志）
     * @return true 如果文件存在、非0字节且可以解码为有效图片
     */
    private suspend fun quickValidateImageFile(imagePath: String, imageName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 使用 ImageAccessHelper 验证（兼容 Android 10+ Scoped Storage）
            com.gongkao.cuotifupan.util.ImageAccessHelper.isValidImage(applicationContext, imagePath)
        } catch (e: Exception) {
            // 验证失败，返回 false（不记录日志，避免日志过多）
            false
        }
    }
    
    /**
     * 静默处理图片（不弹通知）
     */
    private suspend fun processImageSilently(imagePath: String, imageName: String): Boolean {
        return try {
            // 验证图片是否有效（使用 ImageAccessHelper，兼容 Android 10+ Scoped Storage）
            if (!com.gongkao.cuotifupan.util.ImageAccessHelper.isValidImage(applicationContext, imagePath)) {
                Log.w(TAG, "🚫 图片文件无效或无法访问: $imageName")
                return false
            }
            
            // 判断是否是编辑后的图片（文件名包含 _edited_）
            val isEditedImage = imageName.contains("_edited_", ignoreCase = true)
            
            // 获取图片尺寸信息（用于日志）
            val (width, height) = com.gongkao.cuotifupan.util.ImageAccessHelper.getImageSize(applicationContext, imagePath)
            Log.d(TAG, "📷 处理图片: $imageName (尺寸: ${width}x${height})")
            
            // 对于 Android 10+ 的 Scoped Storage，需要先复制到临时文件（如果不是应用私有文件）
            val (workingFilePath, tempFile) = if (imagePath.startsWith(applicationContext.cacheDir.absolutePath) ||
                                     imagePath.startsWith(applicationContext.filesDir.absolutePath)) {
                // 已经是应用私有文件，直接使用
                Pair(imagePath, null)
            } else {
                // 复制到临时文件
                val tempFile = File(applicationContext.cacheDir, "temp_${System.currentTimeMillis()}_${imageName}")
                val copySuccess = com.gongkao.cuotifupan.util.ImageAccessHelper.copyToPrivateStorage(
                    applicationContext, imagePath, tempFile
                )
                if (!copySuccess) {
                    Log.e(TAG, "❌ 无法复制图片到临时文件: $imageName")
                    return false
                }
                Log.d(TAG, "✅ 图片已复制到临时文件: ${tempFile.absolutePath}")
                Pair(tempFile.absolutePath, tempFile)
            }
            
            return try {
                processImageInternal(workingFilePath, imagePath, imageName)
            } finally {
                // 清理临时文件
                tempFile?.also {
                    try {
                        if (it.exists()) {
                            it.delete()
                            Log.d(TAG, "🗑️ 临时文件已删除: ${it.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "清理临时文件失败: ${it.absolutePath}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 静默处理图片失败: $imageName", e)
            Log.e(TAG, "   图片路径: $imagePath")
            Log.e(TAG, "   异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "   异常消息: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * 处理图片的内部逻辑（从工作文件路径）
     * @param workingFilePath 工作文件路径（可能是临时文件）
     * @param originalImagePath 原始图片路径（用于保存到数据库）
     * @param imageName 图片名称
     * @return true 如果是题目
     */
    private suspend fun processImageInternal(workingFilePath: String, originalImagePath: String, imageName: String): Boolean {
        return try {
            
            // 在处理前，先检查数据库中是否已经存在相同原始路径的题目
            // 因为图片会被复制到永久存储，路径会改变，所以需要通过原始路径来判断
            val database = AppDatabase.getDatabase(applicationContext)
            
            // 获取原始图片文件的唯一标识（大小+修改时间）用于去重
            val originalFile = try {
                java.io.File(originalImagePath)
            } catch (e: Exception) {
                null
            }
            
            // 如果原始文件存在，检查数据库中是否已有相同文件的题目或已排除
            if (originalFile != null && originalFile.exists()) {
                val fileSize = originalFile.length()
                
                // 1. 检查路径是否在已排除列表中
                val excludedPaths = database.excludedImageDao().getAllPaths()
                Log.d(TAG, "🔍 检查排除列表，共 ${excludedPaths.size} 条记录")
                if (originalImagePath in excludedPaths) {
                    Log.d(TAG, "⏭️ 图片已在排除列表中（路径匹配），跳过: $imageName")
                    Log.d(TAG, "   原始路径: $originalImagePath")
                    return false
                }
                
                // 2. 检查已排除图片的文件大小
                if (fileSize > 0) {
                    val excludedSizes = mutableSetOf<Long>()
                    excludedPaths.forEach { excludedPath ->
                        try {
                            val excludedFile = java.io.File(excludedPath)
                            if (excludedFile.exists()) {
                                val excludedFileSize = excludedFile.length()
                                if (excludedFileSize > 0) {
                                    excludedSizes.add(excludedFileSize)
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略错误
                        }
                    }
                    Log.d(TAG, "🔍 已排除图片文件大小集合: ${excludedSizes.size} 个，当前文件大小: $fileSize")
                    if (fileSize in excludedSizes) {
                        Log.d(TAG, "⏭️ 图片已在排除列表中（文件大小匹配），跳过: $imageName")
                        Log.d(TAG, "   原始路径: $originalImagePath")
                        Log.d(TAG, "   文件大小: $fileSize")
                        return false
                    }
                }
                
                // 3. 检查数据库中所有题目，看是否有相同原始路径或相同文件大小的
                val existingQuestions = database.questionDao().getAllQuestionsSync()
                val isDuplicate = existingQuestions.any { question ->
                    // 检查路径是否相同（可能是原始路径或永久存储路径）
                    if (question.imagePath == originalImagePath) {
                        return@any true
                    }
                    
                    // 检查永久存储路径对应的文件是否来自同一个原始文件
                    // 通过检查文件大小来判断（复制后文件大小应该相同）
                    // 注意：这种方法可能会有误判（不同文件可能有相同大小），但概率很低
                    if (fileSize > 0) {
                        try {
                            val savedFile = java.io.File(question.imagePath)
                            if (savedFile.exists()) {
                                val savedFileSize = savedFile.length()
                                // 如果文件大小相同且都大于0，且保存的文件在永久存储目录中，很可能是同一个文件
                                if (savedFileSize == fileSize && question.imagePath.startsWith(applicationContext.filesDir.absolutePath)) {
                                    return@any true
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略检查错误
                        }
                    }
                    false
                }
                
                if (isDuplicate) {
                    Log.d(TAG, "⏭️ 图片已在数据库中（通过文件大小检查），跳过: $imageName")
                    Log.d(TAG, "   原始路径: $originalImagePath")
                    Log.d(TAG, "   文件大小: $fileSize")
                    return false
                }
            } else {
                // 如果原始文件不存在，也检查一下数据库中是否有相同路径的题目或已排除
                val excludedPaths = database.excludedImageDao().getAllPaths()
                if (originalImagePath in excludedPaths) {
                    Log.d(TAG, "⏭️ 图片已在排除列表中（路径匹配），跳过: $imageName")
                    Log.d(TAG, "   原始路径: $originalImagePath")
                    return false
                }
                
                val existingQuestions = database.questionDao().getAllQuestionsSync()
                val isDuplicate = existingQuestions.any { question ->
                    question.imagePath == originalImagePath
                }
                
                if (isDuplicate) {
                    Log.d(TAG, "⏭️ 图片已在数据库中（路径匹配），跳过: $imageName")
                    Log.d(TAG, "   原始路径: $originalImagePath")
                    return false
                }
            }
            
            // 自动处理图片：旋转和裁剪
            val processedImagePath = try {
                withContext(Dispatchers.IO) {
                    com.gongkao.cuotifupan.util.ImageEditor.autoProcessImage(workingFilePath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "⚠️ 图片自动处理失败: $imageName", e)
                workingFilePath // 使用原图路径
            }
            
            // 使用处理后的图片路径进行OCR识别
            val ocrResult = try {
                withContext(Dispatchers.IO) {
                    val recognizer = TextRecognizer()
                    recognizer.recognizeText(processedImagePath)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ OCR识别异常: $imageName", e)
                Log.e(TAG, "   异常类型: ${e.javaClass.simpleName}")
                Log.e(TAG, "   异常消息: ${e.message}")
                e.printStackTrace()
                // 返回空的OCR结果，继续尝试图形题检测
                com.gongkao.cuotifupan.ocr.OcrResult("", emptyList(), emptyList(), false, e.message ?: "OCR识别异常")
            }
            
            // 记录OCR结果详情
            Log.d(TAG, "📝 OCR识别结果: $imageName")
            Log.d(TAG, "   - success: ${ocrResult.success}")
            Log.d(TAG, "   - rawText长度: ${ocrResult.rawText.length}")
            Log.d(TAG, "   - errorMessage: ${ocrResult.errorMessage ?: "无"}")
            if (ocrResult.rawText.isNotEmpty()) {
                Log.d(TAG, "   - rawText预览: ${ocrResult.rawText.take(100)}...")
            }
            
            // 如果OCR结果为空，尝试检测是否为图形推理题
            if (!ocrResult.success || ocrResult.rawText.isBlank()) {
                val ocrFailureReason = when {
                    !ocrResult.success -> ocrResult.errorMessage ?: "OCR识别失败"
                    ocrResult.rawText.isBlank() -> "OCR结果为空（图片可能没有文字或无法识别）"
                    else -> "未知原因"
                }
                Log.i(TAG, "⚠️ OCR结果为空，尝试检测图形推理题: $imageName")
                Log.i(TAG, "   失败原因: $ocrFailureReason")
                
                try {
                    val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(applicationContext, processedImagePath)
                    if (bitmap == null) {
                        Log.w(TAG, "🚫 图片解码失败，无法进行图形题检测: $imageName")
                        Log.w(TAG, "   图片路径: $processedImagePath")
                        return false
                    }
                    
                    Log.d(TAG, "   图片解码成功: ${bitmap.width}x${bitmap.height}")
                    
                    val graphicDetector = com.gongkao.cuotifupan.detector.GraphicQuestionDetector()
                    val graphicResult = graphicDetector.detect(bitmap)
                    
                    Log.i(TAG, "   图形题检测结果: isGraphicQuestion=${graphicResult.isGraphicQuestion}, confidence=${graphicResult.confidence}")
                    Log.i(TAG, "   详细: hasGrid=${graphicResult.hasGrid}, hasPattern=${graphicResult.hasPattern}, hasHighContrast=${graphicResult.hasHighContrast}")
                    Log.i(TAG, "   详细: hasOptionMarkers=${graphicResult.hasOptionMarkers}, hasQuestionMark=${graphicResult.hasQuestionMark}")
                    Log.i(TAG, "   原因: ${graphicResult.reason}")
                    
                    if (graphicResult.isGraphicQuestion) {
                        // 保存为图形推理题（使用处理后的图片路径）
                        val question = Question(
                            imagePath = processedImagePath,
                            rawText = "[图形推理题] ${graphicResult.reason}",
                            questionText = "图形推理题（需要人工识别）",
                            options = "",
                            confidence = graphicResult.confidence,
                            questionType = "GRAPHIC"  // 标记为图推题
                        )
                        
                        val database = AppDatabase.getDatabase(applicationContext)
                        database.questionDao().insert(question)
                        
                        Log.i(TAG, "✅ 图形推理题已保存到数据库")
                        Log.i(TAG, "   - 题目类型: ${question.questionType}")
                        Log.i(TAG, "   - 题目ID: ${question.id}")
                        Log.i(TAG, "   - 图片路径: ${question.imagePath}")
                        Log.i(TAG, "   - 检测原因: ${graphicResult.reason}")
                        
                        // 不再显示通知弹窗，直接加入题库
                        // NotificationHelper.showQuestionDetectedNotification(applicationContext, question)
                        
                        bitmap.recycle()
                        return true
                    } else {
                        Log.i(TAG, "🚫 不是图形推理题: $imageName (置信度: ${graphicResult.confidence}, 原因: ${graphicResult.reason})")
                    }
                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 图形题检测失败: $imageName", e)
                    Log.e(TAG, "   异常类型: ${e.javaClass.simpleName}")
                    Log.e(TAG, "   异常消息: ${e.message}")
                    e.printStackTrace()
                }
                
                Log.i(TAG, "🚫 已排除: $imageName (OCR失败且不是图形题)")
                return false
            }
            
            // 判断是否为题目
            val detector = QuestionDetector()
            val detection = try {
                detector.detect(ocrResult)
            } catch (e: Exception) {
                Log.e(TAG, "❌ 题目检测异常: $imageName", e)
                Log.e(TAG, "   异常类型: ${e.javaClass.simpleName}")
                Log.e(TAG, "   异常消息: ${e.message}")
                e.printStackTrace()
                return false
            }
            
            Log.d(TAG, "🔍 题目检测结果: $imageName")
            Log.d(TAG, "   - isQuestion: ${detection.isQuestion}")
            Log.d(TAG, "   - confidence: ${detection.confidence}")
            if (detection.isQuestion) {
                Log.d(TAG, "   - questionText预览: ${detection.questionText.take(50)}...")
                Log.d(TAG, "   - options数量: ${detection.options.size}")
            }
            
            if (detection.isQuestion) {
                // 判断题目类型（文字题 vs 图推题）
                val questionType = determineQuestionType(ocrResult.rawText, detection.questionText)
                
                // 先创建题目对象（用于生成ID）
                val question = Question(
                    imagePath = processedImagePath,  // 临时路径，稍后会更新
                    rawText = ocrResult.rawText,  // 初始使用前端OCR结果
                    questionText = detection.questionText,  // 初始使用前端提取的题干
                    frontendRawText = ocrResult.rawText,  // 保存前端OCR结果，用于发送
                    options = JSONArray(detection.options).toString(),
                    confidence = detection.confidence,
                    questionType = questionType  // 根据关键词判断类型
                )
                
                // 保存图片到永久存储
                val permanentImagePath = com.gongkao.cuotifupan.util.ImageAccessHelper.saveImageToPermanentStorage(
                    applicationContext, processedImagePath, question.id
                )
                
                // 如果保存失败，使用原路径（可能是应用私有文件）
                val finalImagePath = permanentImagePath ?: processedImagePath
                
                // 更新题目对象，使用永久存储路径
                val finalQuestion = question.copy(imagePath = finalImagePath)
                
                val database = AppDatabase.getDatabase(applicationContext)
                database.questionDao().insert(finalQuestion)
                
                val typeLabel = if (questionType == "GRAPHIC") "图推题" else "文字题"
                Log.i(TAG, "✅ ${typeLabel}已保存到数据库（类型：$questionType）")
                
                // 不再显示通知弹窗，直接加入题库
                // NotificationHelper.showQuestionDetectedNotification(applicationContext, finalQuestion)
                
                // 如果是文字题，需要调用后端API获取题目内容
                if (questionType == "TEXT") {
                    // 动态调整并发数：查询数据库中所有文字题数量（包含刚插入的）
                    val allTextQuestions = database.questionDao().getAllQuestionsSync()
                        .count { it.questionType == "TEXT" }
                    QuestionApiQueue.adjustConcurrency(allTextQuestions)
                    Log.d(TAG, "📊 数据库中已有 $allTextQuestions 道文字题，当前并发数: ${QuestionApiQueue.getMaxConcurrency()}")
                    
                    // 文字题：调用后端API获取题目内容（批量处理时只获取题目内容，不获取答案）
                    Log.i(TAG, "📤 文字题，准备调用后端API获取题目内容")
                    Log.i(TAG, "   - 题目ID: ${finalQuestion.id}")
                    Log.i(TAG, "   - 图片路径: ${finalQuestion.imagePath}")
                    Log.i(TAG, "   - 前端题干: ${finalQuestion.questionText.take(50)}...")
                    Log.i(TAG, "   - 准备调用 QuestionApiQueue.enqueue()...")
                    
                    try {
                        QuestionApiQueue.enqueue(
                            question = finalQuestion,
                            onSuccess = { response ->
                                serviceScope.launch {
                                    try {
                                        Log.i(TAG, "✅ 后端API调用成功")
                                        Log.i(TAG, "   - 后端题目ID: ${response.id}")
                                        Log.i(TAG, "   - 完整题干: ${response.questionText.take(50)}...")
                                        Log.i(TAG, "   - 是否重复: ${response.isDuplicate}")
                                        Log.i(TAG, "   - 来自缓存: ${response.fromCache}")
                                        
                                        // 更新题目信息（使用后端返回的完整文字，替换前端OCR的结果）
                                        // 注意：题目已经在 enqueue 之前插入，这里只需要更新
                                        val updatedQuestion = finalQuestion.copy(
                                            backendQuestionId = response.id,
                                            backendQuestionText = response.questionText,
                                            rawText = response.rawText,  // 更新为后端返回的rawText
                                            questionText = response.questionText,  // 更新为后端返回的questionText
                                            options = JSONArray(response.options).toString(),  // 更新为后端返回的options
                                            answerLoaded = false // 批量处理时不加载答案
                                        )
                                        
                                        database.questionDao().update(updatedQuestion)
                                        
                                        Log.i(TAG, "✅ 文字题已更新到数据库（题目内容）")
                                    } catch (e: Exception) {
                                        Log.e(TAG, "❌ 更新题目失败", e)
                                        e.printStackTrace()
                                    }
                                }
                            },
                            onError = { error ->
                                serviceScope.launch {
                                    try {
                                        // API请求失败，仍然保存题目（使用前端OCR结果）
                                        Log.e(TAG, "❌ 后端API调用失败: ${error.message}")
                                        Log.e(TAG, "   异常类型: ${error.javaClass.simpleName}")
                                        error.printStackTrace()
                                        
                                        Log.w(TAG, "使用前端OCR结果保存题目（题目已在前置步骤中保存）")
                                        // 注意：题目已经在 enqueue 之前插入，这里不需要再次插入
                                    } catch (e: Exception) {
                                        Log.e(TAG, "处理错误回调失败", e)
                                        e.printStackTrace()
                                    }
                                }
                            }
                        )
                        Log.i(TAG, "✅ QuestionApiQueue.enqueue() 调用完成")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 调用 QuestionApiQueue.enqueue() 失败", e)
                        e.printStackTrace()
                        // 注意：题目已经在 enqueue 之前插入，这里不需要再次插入
                    }
                }
                
                return true
            } else {
                Log.d(TAG, "🚫 不是题目: $imageName (置信度: ${detection.confidence})")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 处理图片内部逻辑失败: $imageName", e)
            Log.e(TAG, "   图片路径: $originalImagePath")
            Log.e(TAG, "   异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "   异常消息: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * 显示扫描进度通知
     */
    private fun showScanningNotification(current: Int, total: Int) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("正在扫描相册...")
            .setContentText("已扫描 $current / $total 张图片")
            .setProgress(total, current, false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }
    
    /**
     * 显示扫描完成通知
     */
    private fun showScanCompleteNotification(foundCount: Int, scannedCount: Int = 0, processedCount: Int = 0) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        val contentText = if (foundCount > 0) {
            "发现 $foundCount 道题目（已扫描 $scannedCount 张）"
        } else if (processedCount > 0) {
            "已扫描 $scannedCount 张图片，未发现新题目"
        } else {
            "已扫描 $scannedCount 张图片"
        }
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("扫描完成 ✅")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOngoing(false) // 完成后不再持续显示
            .build()
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID + 1, notification)
        
        // 3秒后自动取消通知
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            notificationManager.cancel(NOTIFICATION_ID + 1)
        }, 3000)
    }
    
    /**
     * 处理图片
     */
    private suspend fun processImage(imagePath: String, imageName: String) {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.w(TAG, "图片文件不存在: $imagePath")
                return
            }
            
            Log.i(TAG, "开始处理图片: $imageName")
            
            // 0. 自动处理图片：旋转和裁剪
            Log.i(TAG, "自动处理图片（旋转和裁剪）: $imageName")
            val processedImagePath = withContext(Dispatchers.IO) {
                com.gongkao.cuotifupan.util.ImageEditor.autoProcessImage(imagePath)
            }
            if (processedImagePath != imagePath) {
                Log.i(TAG, "图片已自动处理: $imagePath -> $processedImagePath")
            }
            
            // 1. OCR 识别（使用处理后的图片）
            Log.i(TAG, "开始OCR识别: $imageName")
            val recognizer = TextRecognizer()
            var ocrResult = recognizer.recognizeText(processedImagePath)
            
            // 详细记录OCR结果（ML Kit）
            Log.i(TAG, "========== ML Kit OCR 识别结果 ==========")
            Log.i(TAG, "  - success: ${ocrResult.success}")
            Log.i(TAG, "  - rawText长度: ${ocrResult.rawText.length}")
            Log.i(TAG, "  - rawText内容: [${ocrResult.rawText.take(500)}]")
            if (ocrResult.rawText.length > 500) {
                Log.i(TAG, "  - rawText内容(续): [${ocrResult.rawText.substring(500).take(500)}]")
            }
            Log.i(TAG, "  - lines数量: ${ocrResult.lines.size}")
            Log.i(TAG, "  - textBlocks数量: ${ocrResult.textBlocks.size}")
            Log.i(TAG, "  - errorMessage: ${ocrResult.errorMessage ?: "无"}")
            
            // 同时使用 PaddleOCR 识别并对比
            try {
                Log.i(TAG, "========== PaddleOCR 识别开始 ==========")
                val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(applicationContext, processedImagePath)
                if (bitmap != null) {
                    // 初始化 PaddleOCR（如果还未初始化）
                    if (!com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.isInitialized()) {
                        val initSuccess = com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.init(applicationContext)
                        Log.i(TAG, "PaddleOCR 初始化: ${if (initSuccess) "成功" else "失败"}")
                    }
                    
                    // 使用 PaddleOCR 识别
                    val paddleResult = com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.recognizeText(bitmap)
                    Log.i(TAG, "========== PaddleOCR 识别结果 ==========")
                    if (paddleResult != null) {
                        Log.i(TAG, "  - rawText长度: ${paddleResult.length}")
                        Log.i(TAG, "  - rawText内容: [${paddleResult.take(500)}]")
                        if (paddleResult.length > 500) {
                            Log.i(TAG, "  - rawText内容(续): [${paddleResult.substring(500).take(500)}]")
                        }
                    } else {
                        Log.w(TAG, "  - 识别结果: null（识别失败）")
                    }
                    
                    // 对比结果
                    Log.i(TAG, "========== OCR 结果对比 ==========")
                    Log.i(TAG, "ML Kit 结果长度: ${ocrResult.rawText.length}")
                    Log.i(TAG, "PaddleOCR 结果长度: ${paddleResult?.length ?: 0}")
                    Log.i(TAG, "结果是否相同: ${ocrResult.rawText == paddleResult}")
                    if (ocrResult.rawText != paddleResult) {
                        Log.i(TAG, "结果不同，差异分析:")
                        val mlKitText = ocrResult.rawText
                        val paddleText = paddleResult ?: ""
                        val minLen = minOf(mlKitText.length, paddleText.length)
                        var diffCount = 0
                        for (i in 0 until minLen) {
                            if (mlKitText[i] != paddleText[i]) {
                                diffCount++
                                if (diffCount <= 10) { // 只打印前10个差异位置
                                    val start = maxOf(0, i - 10)
                                    val end = minOf(minLen, i + 10)
                                    Log.i(TAG, "  位置 $i: ML Kit='${mlKitText.substring(start, end)}' vs PaddleOCR='${paddleText.substring(start, end)}'")
                                }
                            }
                        }
                        if (diffCount > 10) {
                            Log.i(TAG, "  ... 还有 ${diffCount - 10} 个差异位置")
                        }
                        if (mlKitText.length != paddleText.length) {
                            Log.i(TAG, "  长度差异: ${mlKitText.length - paddleText.length} 字符")
                        }
                    }
                    Log.i(TAG, "=====================================")
                    
                    bitmap.recycle()
                } else {
                    Log.w(TAG, "无法解码图片为 Bitmap，跳过 PaddleOCR 识别")
                }
            } catch (e: Exception) {
                Log.e(TAG, "PaddleOCR 识别过程出错", e)
            }
            
            // 如果OCR失败或结果为空，尝试检测是否为图形推理题
            val shouldCheckGraphic = !ocrResult.success || ocrResult.rawText.isBlank()
            Log.i(TAG, "是否需要检测图形题: $shouldCheckGraphic (success=${ocrResult.success}, isBlank=${ocrResult.rawText.isBlank()})")
            
            if (shouldCheckGraphic) {
                Log.w(TAG, "⚠️ OCR识别失败或结果为空: $imageName")
                Log.w(TAG, "   可能原因:")
                Log.w(TAG, "   1. 图片中没有文字（纯图形、图案等）")
                Log.w(TAG, "   2. 图片质量太低，无法识别")
                Log.w(TAG, "   3. 图片格式不支持")
                Log.w(TAG, "   错误信息: ${ocrResult.errorMessage ?: "无"}")
                Log.w(TAG, "   图片路径: $imagePath")
                Log.w(TAG, "   图片尺寸: ${file.length()} bytes")
                
                // 即使OCR结果为空，也记录一下，方便调试
                if (ocrResult.textBlocks.isNotEmpty()) {
                    Log.d(TAG, "   检测到 ${ocrResult.textBlocks.size} 个文字块，但文本为空")
                    ocrResult.textBlocks.forEachIndexed { index, block ->
                        Log.d(TAG, "     文字块 $index: 文本长度=${block.text.length}, 行数=${block.lines.size}")
                    }
                }
                
                // 尝试检测是否为图形推理题
                try {
                    Log.i(TAG, "🔍 开始检测图形推理题...")
                    val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(applicationContext, processedImagePath)
                    if (bitmap != null) {
                        Log.i(TAG, "   图片解码成功: ${bitmap.width}x${bitmap.height}")
                        val graphicDetector = com.gongkao.cuotifupan.detector.GraphicQuestionDetector()
                        val graphicResult = graphicDetector.detect(bitmap)
                        
                        Log.i(TAG, "   图形题检测结果:")
                        Log.i(TAG, "     - isGraphicQuestion: ${graphicResult.isGraphicQuestion}")
                        Log.i(TAG, "     - confidence: ${graphicResult.confidence}")
                        Log.i(TAG, "     - hasGrid: ${graphicResult.hasGrid}")
                        Log.i(TAG, "     - hasPattern: ${graphicResult.hasPattern}")
                        Log.i(TAG, "     - hasHighContrast: ${graphicResult.hasHighContrast}")
                        Log.i(TAG, "     - reason: ${graphicResult.reason}")
                        
                        if (graphicResult.isGraphicQuestion) {
                            Log.i(TAG, "✅ 检测到图形推理题，置信度: ${graphicResult.confidence}")
                            Log.d(TAG, "   检测原因: ${graphicResult.reason}")
                            
                            // 保存为图形推理题（再次检查，避免并发重复保存）
                            val database = AppDatabase.getDatabase(applicationContext)
                            val existingQuestions = database.questionDao().getAllQuestionsSync()
                            if (existingQuestions.any { it.imagePath == processedImagePath }) {
                                Log.d(TAG, "⚠️ 图形推理题已在数据库中，跳过保存: $imageName")
                                bitmap.recycle()
                                return
                            }
                            
                            // 先创建题目对象（用于生成ID）
                            val question = Question(
                                imagePath = processedImagePath,  // 临时路径，稍后会更新
                                rawText = "[图形推理题] ${graphicResult.reason}",
                                questionText = "图形推理题（需要人工识别）",
                                options = "",  // 图形题没有文字选项
                                confidence = graphicResult.confidence,
                                questionType = "GRAPHIC"  // 标记为图推题
                            )
                            
                            // 保存图片到永久存储
                            val permanentImagePath = com.gongkao.cuotifupan.util.ImageAccessHelper.saveImageToPermanentStorage(
                                applicationContext, processedImagePath, question.id
                            )
                            
                            // 如果保存失败，使用原路径（可能是应用私有文件）
                            val finalImagePath = permanentImagePath ?: processedImagePath
                            
                            // 更新题目对象，使用永久存储路径
                            val finalQuestion = question.copy(imagePath = finalImagePath)
                            
                            database.questionDao().insert(finalQuestion)
                            
                            Log.i(TAG, "✅ 图形推理题已保存到数据库")
                            Log.i(TAG, "   - 题目类型: ${finalQuestion.questionType}")
                            Log.i(TAG, "   - 题目ID: ${finalQuestion.id}")
                            Log.i(TAG, "   - 图片路径: ${finalQuestion.imagePath}")
                            
                            // 不再显示通知弹窗，直接加入题库
                            // NotificationHelper.showQuestionDetectedNotification(applicationContext, finalQuestion)
                            Log.i(TAG, "   - 检测原因: ${graphicResult.reason}")
                            
                            bitmap.recycle()
                            return
                        } else {
                            Log.i(TAG, "❌ 不是图形推理题，置信度: ${graphicResult.confidence}, 原因: ${graphicResult.reason}")
                            bitmap.recycle()
                        }
                    } else {
                        Log.e(TAG, "❌ 图片解码失败，无法进行图形题检测")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ 图形题检测失败", e)
                    Log.e(TAG, "   异常类型: ${e.javaClass.simpleName}")
                    Log.e(TAG, "   异常消息: ${e.message}")
                    Log.e(TAG, "   堆栈跟踪:")
                    e.printStackTrace()
                }
                
                return
            }
            
            Log.d(TAG, "OCR识别完成，文本长度: ${ocrResult.rawText.length}")
            Log.d(TAG, "========== OCR 识别文本 ==========")
            Log.d(TAG, ocrResult.rawText)
            Log.d(TAG, "========== 分行文本 ==========")
            ocrResult.lines.forEachIndexed { index, line ->
                Log.d(TAG, "行 $index: $line")
            }
            Log.d(TAG, "========== 布局信息 ==========")
            ocrResult.textBlocks.forEachIndexed { index, block ->
                block.lines.forEach { line ->
                    Log.d(TAG, "文字块 $index - ${line.text} | 位置: (${line.boundingBox.left}, ${line.boundingBox.top})")
                }
            }
            Log.d(TAG, "==================================")
            
            // 2. 判断是否为题目
            val detector = QuestionDetector()
            val detection = detector.detect(ocrResult)
            
            Log.d(TAG, "题目检测完成，是否为题目: ${detection.isQuestion}, 置信度: ${detection.confidence}")
            Log.d(TAG, "题干: ${detection.questionText.take(50)}...")
            Log.d(TAG, "选项数量: ${detection.options.size}")
            
            if (detection.isQuestion) {
                Log.i(TAG, "✅ 检测到题目！")
                Log.i(TAG, "   - 置信度: ${detection.confidence}")
                Log.i(TAG, "   - 题干预览: ${detection.questionText.take(50)}...")
                
                // 3. 判断题目类型（文字题 vs 图推题）
                val questionType = determineQuestionType(ocrResult.rawText, detection.questionText)
                Log.i(TAG, "题目类型判断: $questionType (基于OCR文本关键词)")
                
                // 4. 保存到数据库（再次检查，避免并发重复保存）
                val database = AppDatabase.getDatabase(applicationContext)
                val existingQuestions = database.questionDao().getAllQuestionsSync()
                if (existingQuestions.any { it.imagePath == processedImagePath }) {
                    Log.d(TAG, "⚠️ 题目已在数据库中，跳过保存: $imageName")
                    return
                }
                
                // 先创建题目对象（用于生成ID）
                val question = Question(
                    imagePath = processedImagePath,  // 临时路径，稍后会更新
                    rawText = ocrResult.rawText,  // 初始使用前端OCR结果
                    questionText = detection.questionText,  // 初始使用前端提取的题干
                    frontendRawText = ocrResult.rawText,  // 保存前端OCR结果，用于发送给后端
                    options = JSONArray(detection.options).toString(),
                    confidence = detection.confidence,
                    questionType = questionType  // 根据关键词判断类型
                )
                
                // 保存图片到永久存储
                val permanentImagePath = com.gongkao.cuotifupan.util.ImageAccessHelper.saveImageToPermanentStorage(
                    applicationContext, processedImagePath, question.id
                )
                
                // 如果保存失败，使用原路径（可能是应用私有文件）
                val finalImagePath = permanentImagePath ?: processedImagePath
                
                // 更新题目对象，使用永久存储路径
                val finalQuestion = question.copy(imagePath = finalImagePath)
                
                // 根据题目类型处理
                if (questionType == "TEXT") {
                    // 文字题：先保存到数据库，然后调用后端API获取题目内容
                    // 先插入数据库，以便统计总数
                    database.questionDao().insert(finalQuestion)
                    
                    // 动态调整并发数：查询数据库中所有文字题数量（包含刚插入的）
                    val allTextQuestions = database.questionDao().getAllQuestionsSync()
                        .count { it.questionType == "TEXT" }
                    QuestionApiQueue.adjustConcurrency(allTextQuestions)
                    Log.d(TAG, "📊 数据库中已有 $allTextQuestions 道文字题，当前并发数: ${QuestionApiQueue.getMaxConcurrency()}")
                    
                    // 文字题：调用后端API获取题目内容（批量处理时只获取题目内容，不获取答案）
                    Log.i(TAG, "📤 文字题，准备调用后端API获取题目内容")
                    Log.i(TAG, "   - 题目ID: ${finalQuestion.id}")
                    Log.i(TAG, "   - 图片路径: ${finalQuestion.imagePath}")
                    Log.i(TAG, "   - 前端题干: ${finalQuestion.questionText.take(50)}...")
                    Log.i(TAG, "   - 准备调用 QuestionApiQueue.enqueue()...")
                    
                    try {
                        QuestionApiQueue.enqueue(
                        question = finalQuestion,
                        onSuccess = { response ->
                            serviceScope.launch {
                                try {
                                    Log.i(TAG, "✅ 后端API调用成功")
                                    Log.i(TAG, "   - 后端题目ID: ${response.id}")
                                    Log.i(TAG, "   - 完整题干: ${response.questionText.take(50)}...")
                                    Log.i(TAG, "   - 是否重复: ${response.isDuplicate}")
                                    Log.i(TAG, "   - 来自缓存: ${response.fromCache}")
                                    
                                    // 更新题目信息（使用后端返回的完整题干）
                                    // 注意：题目已经在 enqueue 之前插入，这里只需要更新
                                    // 更新题目信息（使用后端返回的完整文字，替换前端OCR的结果）
                                    val updatedQuestion = finalQuestion.copy(
                                        backendQuestionId = response.id,
                                        backendQuestionText = response.questionText,
                                        rawText = response.rawText,  // 更新为后端返回的rawText
                                        questionText = response.questionText,  // 更新为后端返回的questionText
                                        options = JSONArray(response.options).toString(),  // 更新为后端返回的options
                                        answerLoaded = false // 批量处理时不加载答案
                                    )
                                    
                                    database.questionDao().update(updatedQuestion)
                                    
                                    Log.i(TAG, "✅ 文字题已更新到数据库（题目内容）")
                                    
                                    // 不再显示通知弹窗，直接加入题库
                                    // val finalQuestion = database.questionDao().getQuestionById(updatedQuestion.id)
                                    // if (finalQuestion != null) {
                                    //     NotificationHelper.showQuestionDetectedNotification(applicationContext, finalQuestion)
                                    // }
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ 更新题目失败", e)
                                    e.printStackTrace()
                                }
                            }
                        },
                        onError = { error ->
                            serviceScope.launch {
                                try {
                                    // API请求失败，仍然保存题目（使用前端OCR结果）
                                    Log.e(TAG, "❌ 后端API调用失败: ${error.message}")
                                    Log.e(TAG, "   异常类型: ${error.javaClass.simpleName}")
                                    error.printStackTrace()
                                    
                                    Log.w(TAG, "使用前端OCR结果保存题目（题目已在前置步骤中保存）")
                                    // 注意：题目已经在 enqueue 之前插入，这里不需要再次插入
                                    // 不再显示通知弹窗，直接加入题库
                                    // NotificationHelper.showQuestionDetectedNotification(applicationContext, finalQuestion)
                                } catch (e: Exception) {
                                    Log.e(TAG, "保存题目失败", e)
                                    e.printStackTrace()
                                }
                            }
                        }
                        )
                        Log.i(TAG, "✅ QuestionApiQueue.enqueue() 调用完成")
                    } catch (e: Exception) {
                        Log.e(TAG, "❌ 调用 QuestionApiQueue.enqueue() 失败", e)
                        e.printStackTrace()
                        // 注意：题目已经在 enqueue 之前插入，这里不需要再次插入
                        // 不再显示通知弹窗，直接加入题库
                        // NotificationHelper.showQuestionDetectedNotification(applicationContext, finalQuestion)
                    }
                } else {
                    // 图推题：直接保存，不调用后端
                    database.questionDao().insert(finalQuestion)
                    Log.i(TAG, "✅ 图推题已保存到数据库")
                    Log.i(TAG, "   - 题目类型: ${finalQuestion.questionType}")
                    Log.i(TAG, "   - 题目ID: ${finalQuestion.id}")
                    Log.i(TAG, "   - 图片路径: ${finalQuestion.imagePath}")
                    
                    // 不再显示通知弹窗，直接加入题库
                    // NotificationHelper.showQuestionDetectedNotification(applicationContext, finalQuestion)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理图片失败: $imagePath", e)
        }
    }
    
    /**
     * 判断题目类型（文字题 vs 图推题）
     * 基于OCR文本中的关键词和特征来判断
     */
    private fun determineQuestionType(rawText: String, questionText: String): String {
        val combinedText = (rawText + " " + questionText).lowercase()
        val trimmedText = combinedText.trim()
        val textLength = trimmedText.length
        
        Log.d(TAG, "========== 题目类型判断 ==========")
        Log.d(TAG, "原始文本长度: $textLength")
        Log.d(TAG, "原始文本预览: ${trimmedText.take(100)}")
        
        // 图推题的强关键词（优先级最高，即使有"单选题"等标记也优先判断为图推题）
        val strongGraphicKeywords = listOf(
            "填入问号", "问号处", "填入问号处",
            "从所给的", "从所给", "从所", "从所始",
            "选择最合适的一个填入问号", "选择最合适的一个填入",
            "呈现一定的规律性", "呈现一定的规律", "呈现规律性", "呈现规律",
            "图形", "图形分为", "图形分类", "图形推理",
            "六个图形", "四个图形", "五个图形", "三个图形"
        )
        
        // 优先检查图推题强关键词
        val hasStrongGraphicKeyword = strongGraphicKeywords.any { keyword ->
            combinedText.contains(keyword)
        }
        
        if (hasStrongGraphicKeyword) {
            Log.d(TAG, "检测到图推题强关键词，标记为图推题")
            return "GRAPHIC"
        }
        
        // 文字题的关键词（如果包含这些，判断为文字题）
        val textQuestionKeywords = listOf(
            "最恰当的一项", "最恰当的是", "最怡当的一项", "最怡当的是",
            "正确的是", "错误的是", "不正确的是",
            "填入画横线", "填入划横线", "填入横线", "填入画橫线", "填入划橫线",
            "镇入画横线", "镇入画橫线", "镇入划横线", "镇入划橫线",
            "慎入画横线", "慎入画橫线", "慎入划横线", "慎入划橫线",
            "顷入画横线", "顷入画橫线", "顷入划横线", "顷入划橫线",
            "画横线部分", "画橫线部分", "划横线部分", "划橫线部分",
            // OCR错误变体
            "面橫线部分", "面横线部分", "面橫线", "面横线",  // "画"可能识别为"面"
            "填入面橫线", "填入面横线", "镇入面橫线", "镇入面横线",
            "慎入面橫线", "慎入面横线", "顷入面橫线", "顷入面横线",
            "多选题", "判断题", "填空题", "问答题"
            // 注意：不包含"单选题"和"选择题"，因为图推题也可能是单选题
        )
        
        // 如果包含文字题关键词，判断为文字题
        val hasTextQuestionKeyword = textQuestionKeywords.any { keyword ->
            combinedText.contains(keyword)
        }
        
        if (hasTextQuestionKeyword) {
            Log.d(TAG, "检测到文字题关键词，标记为文字题")
            return "TEXT"
        }
        
        // 图推题的其他关键词（优先级较低）
        val graphicKeywords = listOf(
            "规律性", "规律",
            "分为两类", "分为", "分类",
            "选择最合适", "选择最恰当", "选择最"
        )
        
        // 检查是否包含图推题关键词
        val hasGraphicKeyword = graphicKeywords.any { keyword ->
            combinedText.contains(keyword)
        }
        
        if (hasGraphicKeyword) {
            Log.d(TAG, "检测到图推题关键词，标记为图推题")
            return "GRAPHIC"
        }
        
        // 特殊检测：OCR文本很短且包含问号，可能是图推题
        // 这种情况通常发生在OCR无法识别图形内容，只能识别到选项标记和问号
        // 计算有效文本长度（去掉换行符、空格、选项标记、数字选项标记、问号）
        val textWithoutNewlines = trimmedText.replace(Regex("[\\n\\r\\s]"), "")
        val cleanText = textWithoutNewlines
            .replace(Regex("[a-d]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[①②③④⑤⑥⑦⑧⑨⑩]"), "")
            .replace(Regex("[?？]"), "")
        val cleanTextLength = cleanText.length
        val hasQuestionMark = trimmedText.contains("?") || trimmedText.contains("？")
        
        // 计算选项标记和数字选项标记的比例
        val optionCount = textWithoutNewlines.count { 
            it in "aAbBcCdD" || it in "①②③④⑤⑥⑦⑧⑨⑩" || it in "?？" 
        }
        val totalLength = textWithoutNewlines.length
        val optionRatio = if (totalLength > 0) optionCount.toDouble() / totalLength else 0.0
        
        Log.d(TAG, "有效文本长度: $cleanTextLength")
        Log.d(TAG, "有效文本预览: ${cleanText.take(50)}")
        Log.d(TAG, "选项标记数量: $optionCount")
        Log.d(TAG, "总文本长度: $totalLength")
        Log.d(TAG, "选项比例: ${String.format("%.1f", optionRatio * 100)}%")
        Log.d(TAG, "包含问号: $hasQuestionMark")
        
        // 如果有效文本很短（少于30个字符）且包含问号，且选项标记比例高（>50%），很可能是图推题
        if (cleanTextLength < 30 && hasQuestionMark && optionRatio > 0.5) {
            Log.d(TAG, "检测到短文本+问号+高选项比例，推断为图推题（有效文本长度: $cleanTextLength, 选项比例: ${String.format("%.1f", optionRatio * 100)}%）")
            return "GRAPHIC"
        }
        
        // 如果有效文本很短（少于25个字符）且包含问号，可能是图推题
        if (cleanTextLength < 25 && hasQuestionMark) {
            Log.d(TAG, "检测到短文本+问号，推断为图推题（有效文本长度: $cleanTextLength）")
            return "GRAPHIC"
        }
        
        // 如果有效文本很短（少于25个字符）且选项标记比例高（>50%），可能是图推题
        // 这种情况通常是图形推理题，OCR只能识别到选项标记和少量其他字符
        if (cleanTextLength < 25 && optionRatio > 0.5) {
            Log.d(TAG, "检测到短文本+高选项比例，推断为图推题（有效文本长度: $cleanTextLength, 选项比例: ${String.format("%.1f", optionRatio * 100)}%）")
            return "GRAPHIC"
        }
        
        // 如果文本很短（少于等于30个字符）且没有明显的题目关键词，可能是图推题
        // 这种情况通常是OCR无法识别图形内容
        if (cleanTextLength <= 30) {
            val hasTextKeywords = listOf(
                "下列", "正确的是", "错误的是", "属于", "不属于",
                "选择", "关于", "描述", "定义", "特点", "作用", "影响", "原因",
                "根据", "按照", "哪个", "什么", "如何", "怎样", "为什么",
                "最恰当", "填入", "画横线", "画橫线", "画横线部分", "画橫线部分"
            ).any { keyword -> combinedText.contains(keyword) }
            
            Log.d(TAG, "检测文字题关键词: $hasTextKeywords")
            
            // 如果有效文本很短且选项比例较高，即使没有问号也可能是图推题
            if (!hasTextKeywords && optionRatio > 0.3) {
                Log.d(TAG, "✅ 检测到短文本且无文字题关键词+选项标记，推断为图推题（有效文本长度: $cleanTextLength, 选项比例: ${String.format("%.1f", optionRatio * 100)}%）")
                Log.d(TAG, "==================================")
                return "GRAPHIC"
            }
        }
        
        // 如果文本很长（>100字符），更可能是文字题
        if (textLength > 100) {
            Log.d(TAG, "检测到长文本，推断为文字题（文本长度: $textLength）")
            Log.d(TAG, "==================================")
            return "TEXT"
        }
        
        // 默认标记为文字题
        Log.d(TAG, "默认标记为文字题")
        Log.d(TAG, "==================================")
        return "TEXT"
    }
    
    /**
     * 显示题目检测通知
     */
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "图片监听服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "持续监听相册新图片"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建前台通知
     */
    private fun createForegroundNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("截题本正在运行")
            .setContentText("正在监听新图片...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    companion object {
        fun start(context: Context, scanLimit: Int = -1) {
            val intent = Intent(context, ImageMonitorService::class.java)
            if (scanLimit > 0) {
                intent.putExtra("scan_limit", scanLimit)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stop(context: Context) {
            val intent = Intent(context, ImageMonitorService::class.java)
            context.stopService(intent)
        }
    }
}

