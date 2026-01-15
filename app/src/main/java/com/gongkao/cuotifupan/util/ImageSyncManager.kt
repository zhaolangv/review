package com.gongkao.cuotifupan.util

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.data.ScannedImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 图片同步管理器
 * 负责对比应用中的题目和手机相册中的图片
 */
object ImageSyncManager {
    
    private const val TAG = "ImageSyncManager"
    
    /**
     * 同步结果数据类
     */
    data class SyncResult(
        val newImagesFound: Int = 0,           // 发现的新图片数量
        val newQuestionsFound: Int = 0,        // 发现的新题目数量
        val deletedImagesCount: Int = 0,       // 被删除的图片数量
        val invalidQuestionsDeleted: Int = 0,   // 删除的无效题目数量
        val totalChecked: Int = 0              // 总共检查的图片数量
    )
    
    /**
     * 执行完整的同步对比
     * @param context Context
     * @param scanLimit 扫描最近多少张图片（默认50）
     * @param onProgress 进度回调
     * @return SyncResult 同步结果
     */
    suspend fun performFullSync(
        context: Context,
        scanLimit: Int = 50,
        onProgress: ((String) -> Unit)? = null
    ): SyncResult = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🔄 开始完整同步对比...")
            onProgress?.invoke("开始同步对比...")
            
            val database = AppDatabase.getDatabase(context)
            
            // 第一步：检查已保存题目的图片是否还存在
            onProgress?.invoke("检查已保存题目的图片...")
            val cleanupResult = cleanupInvalidQuestions(context, database)
            
            Log.i(TAG, "✅ 清理完成：删除了 ${cleanupResult.deletedCount} 条无效题目")
            
            // 第二步：检查是否有新图片，并识别题目
            onProgress?.invoke("检查新图片并识别题目...")
            val scanResult = scanForNewImages(context, database, scanLimit, onProgress)
            
            Log.i(TAG, "✅ 扫描完成：发现 ${scanResult.newImagesFound} 张新图片，识别出 ${scanResult.newQuestionsFound} 道题目")
            
            // 第三步：对比应用里的图和手机里的图
            onProgress?.invoke("对比应用和相册中的图片...")
            val compareResult = compareAppAndGalleryImages(context, database)
            
            // 构建最终结果
            val result = SyncResult(
                newImagesFound = scanResult.newImagesFound,
                newQuestionsFound = scanResult.newQuestionsFound,
                deletedImagesCount = compareResult.deletedCount,
                invalidQuestionsDeleted = cleanupResult.deletedCount,
                totalChecked = cleanupResult.checkedCount + scanResult.totalChecked
            )
            
            Log.i(TAG, "✅ 对比完成：发现 ${compareResult.deletedCount} 张图片被删除")
            
            Log.i(TAG, "🎉 完整同步完成：")
            Log.i(TAG, "   - 发现新图片: ${result.newImagesFound} 张")
            Log.i(TAG, "   - 识别新题目: ${result.newQuestionsFound} 道")
            Log.i(TAG, "   - 删除无效题目: ${result.invalidQuestionsDeleted} 道")
            Log.i(TAG, "   - 发现删除图片: ${result.deletedImagesCount} 张")
            Log.i(TAG, "   - 总共检查: ${result.totalChecked} 张")
            
            onProgress?.invoke("同步完成！")
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "同步失败", e)
            onProgress?.invoke("同步失败: ${e.message}")
            SyncResult()
        }
    }
    
    /**
     * 清理图片文件已不存在的题目记录
     * 包括检查应用私有目录的图片和原始相册图片
     */
    private suspend fun cleanupInvalidQuestions(
        context: Context,
        database: AppDatabase
    ): CleanupResult = withContext(Dispatchers.IO) {
        val result = CleanupResult()
        try {
            val allQuestions = database.questionDao().getAllQuestionsSync()
            Log.d(TAG, "开始清理：找到 ${allQuestions.size} 条题目记录")
            
            // 获取相册中所有图片的路径和文件大小（用于匹配原始图片）
            val galleryImageInfo = mutableMapOf<Long, MutableList<String>>() // 文件大小 -> 路径列表
            val galleryImagePaths = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    galleryImagePaths.add(path)
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            val fileSize = file.length()
                            if (fileSize > 0) {
                                galleryImageInfo.getOrPut(fileSize) { mutableListOf() }.add(path)
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                }
            }
            
            Log.d(TAG, "相册中共有 ${galleryImagePaths.size} 张图片，记录了 ${galleryImageInfo.size} 个不同大小的文件")
            
            for (question in allQuestions) {
                result.checkedCount++
                val imagePath = question.imagePath
                var shouldDelete = false
                
                // 检查图片文件是否存在
                val imageFile = File(imagePath)
                val imageExists = imageFile.exists()
                
                if (!imageExists) {
                    // 图片文件不存在，需要删除
                    shouldDelete = true
                    Log.d(TAG, "图片文件不存在: ${question.id}, 路径: $imagePath")
                } else if (imagePath.startsWith(context.filesDir.absolutePath) || 
                          imagePath.startsWith(context.cacheDir.absolutePath)) {
                    // 图片在应用私有目录，只要文件存在就保留
                    // 这些图片可能是裁剪的图片或从相册复制过来的图片，都是应用管理的独立副本
                    // 不需要检查原始图片是否在相册中，因为应用私有目录的图片本身就是有效的
                    Log.d(TAG, "应用私有文件存在，保留题目: ${question.id}, 路径: $imagePath")
                } else if (imagePath.contains("/DCIM/Camera/")) {
                    // 图片在公共存储目录（DCIM/Camera），检查是否在相册中
                    // 如果用户从相册删除了图片，应该删除题目记录
                    if (imagePath !in galleryImagePaths) {
                        // 不在相册中，删除题目记录（即使文件可能还存在，但用户已经从相册删除了）
                        shouldDelete = true
                        Log.d(TAG, "公共存储图片已从相册删除: ${question.id}, 路径: $imagePath")
                    } else {
                        Log.d(TAG, "公共存储图片仍在相册中: ${question.id}, 路径: $imagePath")
                    }
                } else {
                    // 图片在外部存储，检查是否在相册中
                    if (imagePath !in galleryImagePaths) {
                        // 不在相册中，检查文件是否存在
                        if (!imageExists) {
                            shouldDelete = true
                            Log.d(TAG, "外部图片已删除: ${question.id}, 路径: $imagePath")
                        }
                    }
                }
                
                if (shouldDelete) {
                    try {
                        database.questionDao().delete(question)
                        result.deletedCount++
                        Log.d(TAG, "已删除无效题目: ${question.id}, 图片: $imagePath")
                    } catch (e: Exception) {
                        Log.e(TAG, "删除题目失败: ${question.id}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理失败", e)
        }
        result
    }
    
    /**
     * 扫描新图片并识别题目
     */
    private suspend fun scanForNewImages(
        context: Context,
        database: AppDatabase,
        limit: Int,
        onProgress: ((String) -> Unit)?
    ): ScanResult = withContext(Dispatchers.IO) {
        val result = ScanResult()
        try {
            // 第一步：快速检查图片总量和前50张是否变化
            onProgress?.invoke("检查图片变化...")
            val shouldScan = checkIfNeedScan(context, limit)
            if (!shouldScan) {
                Log.d(TAG, "图片总量和前$limit 张图片未变化，跳过扫描")
                return@withContext result
            }
            
            // 获取已扫描的图片记录
            val scannedImagePaths = database.scannedImageDao().getAllPaths().toSet()
            val scannedFileSizes = mutableSetOf<Long>()
            database.scannedImageDao().getRecentScanned(limit).forEach { scanned ->
                scannedFileSizes.add(scanned.fileSize)
            }
            
            // 获取已处理的图片路径和文件大小（用于去重）
            val processedPaths = mutableSetOf<String>()
            val processedFileSizes = mutableSetOf<Long>()
            val existingQuestions = database.questionDao().getAllQuestionsSync()
            existingQuestions.forEach { question ->
                processedPaths.add(question.imagePath)
                try {
                    val file = File(question.imagePath)
                    if (file.exists()) {
                        val fileSize = file.length()
                        if (fileSize > 0) {
                            processedFileSizes.add(fileSize)
                        }
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
            
            val excludedPaths = database.excludedImageDao().getAllPaths().toSet()
            processedPaths.addAll(excludedPaths)
            
            Log.d(TAG, "已处理图片: ${processedPaths.size} 张，已扫描记录: ${scannedImagePaths.size} 张，已记录文件大小: ${processedFileSizes.size} 个")
            
            // 查询相册中的图片
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.DISPLAY_NAME
            )
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            ) ?: return@withContext result
            
            // 保存前50张图片信息用于下次快速检查
            val topImagesInfo = mutableListOf<Pair<String, Long>>()
            
            cursor.use {
                var scannedCount = 0
                val totalToScan = minOf(it.count, limit)
                val totalImageCount = it.count
                
                // 保存图片总数
                PreferencesManager.saveImageCount(context, totalImageCount)
                
                onProgress?.invoke("扫描新图片: 0/$totalToScan")
                
                while (it.moveToNext() && scannedCount < limit) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    
                    scannedCount++
                    result.totalChecked++
                    
                    // 保存前50张图片信息
                    if (scannedCount <= limit) {
                        try {
                            val file = File(path)
                            if (file.exists()) {
                                topImagesInfo.add(Pair(name, file.length()))
                            }
                        } catch (e: Exception) {
                            // 忽略错误
                        }
                    }
                    
                    if (scannedCount % 10 == 0 || scannedCount == 1) {
                        onProgress?.invoke("扫描新图片: $scannedCount/$totalToScan")
                    }
                    
                    // 检查是否已处理过（通过路径）
                    if (path in processedPaths) {
                        continue
                    }
                    
                    // 检查是否已扫描过（通过扫描记录）
                    if (path in scannedImagePaths) {
                        val scannedRecord = database.scannedImageDao().getByPath(path)
                        if (scannedRecord != null && !scannedRecord.isQuestion) {
                            Log.d(TAG, "图片已扫描过且不是题目，跳过: $name")
                            continue
                        }
                    }
                    
                    // 检查文件是否存在
                    val file = File(path)
                    if (!file.exists()) {
                        continue
                    }
                    
                    // 检查文件大小是否已处理过（用于去重，即使路径不同）
                    try {
                        val fileSize = file.length()
                        if (fileSize > 0) {
                            // 检查是否在已处理文件大小中
                            if (fileSize in processedFileSizes) {
                                Log.d(TAG, "图片文件大小已处理过，跳过: $name (大小: $fileSize)")
                                continue
                            }
                            // 检查是否在已扫描文件大小中
                            if (fileSize in scannedFileSizes) {
                                Log.d(TAG, "图片文件大小已扫描过，跳过: $name (大小: $fileSize)")
                                continue
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略错误，继续处理
                    }
                    
                    // 发现新图片
                    result.newImagesFound++
                    
                    // 检查是否是题目并处理
                    var isQuestion = false
                    try {
                        isQuestion = ImageScanner.checkIfQuestion(context, path)
                        if (isQuestion) {
                            result.newQuestionsFound++
                            Log.d(TAG, "发现新题目: $name，开始处理...")
                            // 实际处理并保存题目（使用 ImageScanner 的完整处理流程）
                            ImageScanner.processNewImage(context, path, name, database)
                            Log.d(TAG, "✅ 新题目已处理并保存: $name")
                        } else {
                            Log.d(TAG, "新图片不是题目，跳过: $name")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "处理新图片失败: $name", e)
                    }
                    
                    // 记录扫描结果（无论是否是题目都记录）
                    try {
                        val fileSize = file.length()
                        val scannedImage = ScannedImage(
                            imagePath = path,
                            fileName = name,
                            fileSize = fileSize,
                            isQuestion = isQuestion,
                            mediaStoreId = id
                        )
                        database.scannedImageDao().insert(scannedImage)
                        Log.d(TAG, "已记录扫描结果: $name (是题目: $isQuestion)")
                    } catch (e: Exception) {
                        Log.e(TAG, "记录扫描结果失败: $name", e)
                    }
                }
                
                // 保存前50张图片信息
                if (topImagesInfo.isNotEmpty()) {
                    PreferencesManager.saveTopImagesInfo(context, topImagesInfo)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "扫描新图片失败", e)
        }
        result
    }
    
    /**
     * 对比应用里的图和手机里的图
     */
    private suspend fun compareAppAndGalleryImages(
        context: Context,
        database: AppDatabase
    ): CompareResult = withContext(Dispatchers.IO) {
        val result = CompareResult()
        try {
            val allQuestions = database.questionDao().getAllQuestionsSync()
            Log.d(TAG, "开始对比：应用中有 ${allQuestions.size} 道题目")
            
            // 获取相册中所有图片的路径集合
            val galleryImagePaths = mutableSetOf<String>()
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                null
            )
            
            cursor?.use {
                while (it.moveToNext()) {
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    galleryImagePaths.add(path)
                }
            }
            
            Log.d(TAG, "相册中共有 ${galleryImagePaths.size} 张图片")
            
            // 检查应用中的题目图片是否在相册中
            for (question in allQuestions) {
                val imagePath = question.imagePath
                
                // 如果是应用私有文件，跳过（这些文件不在相册中）
                if (imagePath.startsWith(context.filesDir.absolutePath) || 
                    imagePath.startsWith(context.cacheDir.absolutePath)) {
                    continue
                }
                
                // 检查图片是否在相册中
                if (imagePath !in galleryImagePaths) {
                    // 图片不在相册中，检查文件是否存在
                    val file = File(imagePath)
                    if (!file.exists()) {
                        result.deletedCount++
                        Log.d(TAG, "发现被删除的图片: $imagePath (题目ID: ${question.id})")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "对比失败", e)
        }
        result
    }
    
    /**
     * 检查图片文件是否存在
     */
    private fun checkImageExists(context: Context, imagePath: String): Boolean {
        return try {
            if (imagePath.startsWith(context.filesDir.absolutePath) || 
                imagePath.startsWith(context.cacheDir.absolutePath)) {
                // 应用私有文件，直接检查
                File(imagePath).exists()
            } else {
                // 外部文件，先检查文件，再使用 ImageAccessHelper
                val file = File(imagePath)
                if (file.exists()) {
                    true
                } else {
                    ImageAccessHelper.isValidImage(context, imagePath)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查图片失败: $imagePath", e)
            false
        }
    }
    
    /**
     * 检查是否需要扫描（通过图片总量和前N张图片信息）
     */
    private suspend fun checkIfNeedScan(context: Context, limit: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            // 查询相册中的图片总数
            val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA, MediaStore.Images.Media.DISPLAY_NAME)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val cursor = context.contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            ) ?: return@withContext true
            
            cursor.use {
                val currentImageCount = it.count
                val savedImageCount = PreferencesManager.getImageCount(context)
                
                // 检查图片总数是否变化
                if (currentImageCount != savedImageCount) {
                    Log.d(TAG, "图片总数变化: $savedImageCount -> $currentImageCount，需要扫描")
                    return@withContext true
                }
                
                // 检查前N张图片是否变化
                val savedTopImages = PreferencesManager.getTopImagesInfo(context)
                if (savedTopImages.isEmpty()) {
                    Log.d(TAG, "没有保存的前$limit 张图片信息，需要扫描")
                    return@withContext true
                }
                
                val currentTopImages = mutableListOf<Pair<String, Long>>()
                var count = 0
                while (it.moveToNext() && count < limit) {
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    try {
                        val file = File(path)
                        if (file.exists()) {
                            currentTopImages.add(Pair(name, file.length()))
                        }
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                    count++
                }
                
                // 比较前N张图片
                if (currentTopImages.size != savedTopImages.size) {
                    Log.d(TAG, "前$limit 张图片数量变化: ${savedTopImages.size} -> ${currentTopImages.size}，需要扫描")
                    return@withContext true
                }
                
                for (i in currentTopImages.indices) {
                    val current = currentTopImages[i]
                    val saved = savedTopImages.getOrNull(i)
                    if (saved == null || current.first != saved.first || current.second != saved.second) {
                        Log.d(TAG, "前$limit 张图片内容变化，需要扫描")
                        return@withContext true
                    }
                }
                
                Log.d(TAG, "图片总量和前$limit 张图片未变化，跳过扫描")
                return@withContext false
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查是否需要扫描失败", e)
            return@withContext true // 出错时默认需要扫描
        }
    }
    
    // 内部数据类
    private data class CleanupResult(
        var deletedCount: Int = 0,
        var checkedCount: Int = 0
    )
    
    private data class ScanResult(
        var newImagesFound: Int = 0,
        var newQuestionsFound: Int = 0,
        var totalChecked: Int = 0
    )
    
    private data class CompareResult(
        var deletedCount: Int = 0
    )
}

