package com.gongkao.cuotifupan.util

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.detector.QuestionDetector
import com.gongkao.cuotifupan.ocr.TextRecognizer
import com.gongkao.cuotifupan.api.QuestionApiQueue
import com.gongkao.cuotifupan.util.ImageEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File

/**
 * 图片扫描工具类（用于首次启动时的一次性扫描）
 */
object ImageScanner {
    
    private const val TAG = "ImageScanner"
    
    // 进度回调
    var onProgressUpdate: ((String) -> Unit)? = null
    
    /**
     * 扫描最近图片（首次启动专用）
     */
    suspend fun scanRecentImages(context: Context, limit: Int, isFirstLaunch: Boolean = true, onProgress: ((String) -> Unit)? = null) = withContext(Dispatchers.IO) {
        onProgressUpdate = onProgress
        try {
            Log.i(TAG, "🔍 开始扫描最近 $limit 张图片...")
            
            val database = AppDatabase.getDatabase(context)
            val processedImagePaths = mutableSetOf<String>()
            val processedImageSizes = mutableSetOf<Long>()
            
            // 如果不是首次启动，获取已存在的图片路径
            if (!isFirstLaunch) {
                val existingQuestions = database.questionDao().getAllQuestionsSync()
                existingQuestions.forEach { question ->
                    processedImagePaths.add(question.imagePath)
                    try {
                        val file = File(question.imagePath)
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
                
                val excludedPaths = database.excludedImageDao().getAllPaths()
                excludedPaths.forEach { excludedPath ->
                    processedImagePaths.add(excludedPath)
                    try {
                        val file = File(excludedPath)
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
            }
            
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
            ) ?: return@withContext
            
            var scannedCount = 0
            var processedCount = 0
            var foundQuestions = 0
            
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
                Log.i(TAG, "📷 相册共有 $accessibleImageCount 张可访问图片，将检查最近 $totalToScan 张")
                
                onProgressUpdate?.invoke("正在扫描前$totalToScan 张图片...")
                
                while (it.moveToNext() && scannedCount < actualLimit) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val name = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    
                    scannedCount++
                    
                    // 更新进度
                    if (scannedCount % 10 == 0 || scannedCount == 1) {
                        onProgressUpdate?.invoke("已扫描 $scannedCount/$totalToScan 张图片...")
                    }
                    
                    // 检查是否已处理过
                    var isProcessed = false
                    if (path in processedImagePaths) {
                        isProcessed = true
                    } else {
                        try {
                            val file = File(path)
                            if (file.exists()) {
                                val fileSize = file.length()
                                if (fileSize > 0 && fileSize in processedImageSizes) {
                                    isProcessed = true
                                }
                            }
                        } catch (e: Exception) {
                            // 忽略错误
                        }
                    }
                    
                    if (isProcessed) {
                        continue
                    }
                    
                    // 处理图片
                    try {
                        processImage(context, path, name, database)
                        processedCount++
                        foundQuestions++
                    } catch (e: Exception) {
                        Log.e(TAG, "处理图片失败: $name", e)
                    }
                }
            }
            
            Log.i(TAG, "✅ 扫描完成: 共扫描 $scannedCount 张，处理 $processedCount 张，发现 $foundQuestions 道题目")
            onProgressUpdate?.invoke("扫描完成！发现 $foundQuestions 道题目")
        } catch (e: Exception) {
            Log.e(TAG, "扫描失败", e)
            onProgressUpdate?.invoke("扫描失败: ${e.message}")
        } finally {
            onProgressUpdate = null
        }
    }
    
    /**
     * 处理新图片（公开方法，供外部调用）
     */
    suspend fun processNewImage(context: Context, imagePath: String, imageName: String, database: AppDatabase) {
        processImage(context, imagePath, imageName, database)
    }
    
    /**
     * 处理单张图片
     */
    private suspend fun processImage(context: Context, imagePath: String, imageName: String, database: AppDatabase) {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                return
            }
            
            Log.i(TAG, "开始处理图片: $imageName")
            
            // 自动处理图片（旋转等）
            val processedImagePath = ImageEditor.autoProcessImage(imagePath)
            
            // OCR 识别
            val recognizer = TextRecognizer()
            val ocrResult = recognizer.recognizeText(processedImagePath)
            
            Log.i(TAG, "========== ML Kit OCR 识别结果 ==========")
            Log.i(TAG, "  - success: ${ocrResult.success}")
            Log.i(TAG, "  - rawText长度: ${ocrResult.rawText.length}")
            Log.i(TAG, "  - rawText内容: [${ocrResult.rawText.take(500)}]")
            if (ocrResult.rawText.length > 500) {
                Log.i(TAG, "  - rawText内容(续): [${ocrResult.rawText.substring(500).take(500)}]")
            }
            
            // 同时使用 PaddleOCR 识别并对比
            try {
                Log.i(TAG, "========== PaddleOCR 识别开始 ==========")
                val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(context, processedImagePath)
                if (bitmap != null) {
                    // 初始化 PaddleOCR（如果还未初始化）
                    if (!com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.isInitialized()) {
                        val initSuccess = com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.init(context)
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
            
            if (!ocrResult.success || ocrResult.rawText.isBlank()) {
                Log.d(TAG, "OCR识别失败或文本为空，跳过: $imageName")
                return
            }
            
            // 题目检测
            val detector = QuestionDetector()
            val detection = detector.detect(ocrResult)
            
            if (!detection.isQuestion) {
                Log.d(TAG, "不是题目，跳过: $imageName")
                return
            }
            
            // 判断题目类型
            val questionType = determineQuestionType(ocrResult.rawText, detection.questionText)
            
            // 创建题目对象
            val question = Question(
                imagePath = processedImagePath,
                rawText = ocrResult.rawText,
                questionText = detection.questionText,
                frontendRawText = ocrResult.rawText,
                options = JSONArray(detection.options).toString(),
                confidence = detection.confidence,
                questionType = questionType
            )
            
            // 保存图片到永久存储
            val permanentImagePath = ImageAccessHelper.saveImageToPermanentStorage(
                context, processedImagePath, question.id
            )
            
            val finalImagePath = permanentImagePath ?: processedImagePath
            val finalQuestion = question.copy(imagePath = finalImagePath)
            
            // 检查是否已存在相同内容的题目（通过OCR文本和题目文本判断）
            val existingQuestions = database.questionDao().getAllQuestionsSync()
            val isDuplicate = existingQuestions.any { existing ->
                // 检查OCR文本是否相似（允许一定差异）
                val rawTextSimilar = existing.rawText.isNotBlank() && 
                    finalQuestion.rawText.isNotBlank() &&
                    (existing.rawText == finalQuestion.rawText ||
                     existing.rawText.take(100) == finalQuestion.rawText.take(100) ||
                     existing.rawText.length > 50 && finalQuestion.rawText.length > 50 &&
                     kotlin.math.abs(existing.rawText.length - finalQuestion.rawText.length) < 20 &&
                     existing.rawText.substring(0, minOf(50, existing.rawText.length)) == 
                     finalQuestion.rawText.substring(0, minOf(50, finalQuestion.rawText.length)))
                
                // 检查题目文本是否相同
                val questionTextSame = existing.questionText.isNotBlank() && 
                    finalQuestion.questionText.isNotBlank() &&
                    existing.questionText == finalQuestion.questionText
                
                rawTextSimilar || questionTextSame
            }
            
            if (isDuplicate) {
                Log.d(TAG, "⚠️ 题目已存在，跳过保存: $imageName")
                Log.d(TAG, "   题目文本: ${finalQuestion.questionText.take(50)}...")
                return
            }
            
            // 保存到数据库
            database.questionDao().insert(finalQuestion)
            Log.i(TAG, "✅ 题目已保存: ${finalQuestion.id}")
            
            // 如果是文字题，调用后端API
            if (questionType == "TEXT") {
                QuestionApiQueue.enqueue(
                    question = finalQuestion,
                    onSuccess = { response ->
                        withContext(Dispatchers.IO) {
                            try {
                                val updatedQuestion = finalQuestion.copy(
                                    backendQuestionId = response.id,
                                    backendQuestionText = response.questionText,
                                    rawText = response.rawText,
                                    questionText = response.questionText,
                                    options = JSONArray(response.options).toString(),
                                    answerLoaded = false
                                )
                                database.questionDao().update(updatedQuestion)
                                Log.i(TAG, "✅ 文字题已更新: ${updatedQuestion.id}")
                            } catch (e: Exception) {
                                Log.e(TAG, "更新题目失败", e)
                            }
                        }
                    },
                    onError = { error ->
                        Log.e(TAG, "❌ 后端API调用失败: ${error.message}")
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理图片失败: $imageName", e)
        }
    }
    
    /**
     * 检查图片是否是题目（不保存，仅检查）
     */
    suspend fun checkIfQuestion(context: Context, imagePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(imagePath)
            if (!file.exists()) {
                return@withContext false
            }
            
            // 自动处理图片（旋转等）
            val processedImagePath = ImageEditor.autoProcessImage(imagePath)
            
            // OCR 识别
            val recognizer = TextRecognizer()
            val ocrResult = recognizer.recognizeText(processedImagePath)
            
            if (!ocrResult.success || ocrResult.rawText.isBlank()) {
                return@withContext false
            }
            
            // 题目检测
            val detector = QuestionDetector()
            val detection = detector.detect(ocrResult)
            
            return@withContext detection.isQuestion
        } catch (e: Exception) {
            Log.e(TAG, "检查图片是否是题目失败: $imagePath", e)
            false
        }
    }
    
    /**
     * 判断题目类型
     */
    private fun determineQuestionType(rawText: String, questionText: String): String {
        val combinedText = (rawText + " " + questionText).lowercase()
        
        val strongGraphicKeywords = listOf(
            "填入问号", "问号处", "填入问号处",
            "从所给的", "从所给", "呈现一定的规律性", "呈现一定的规律",
            "图形", "图形分为", "图形分类", "图形推理",
            "六个图形", "四个图形", "五个图形", "三个图形"
        )
        
        if (strongGraphicKeywords.any { combinedText.contains(it) }) {
            return "GRAPHIC"
        }
        
        return "TEXT"
    }
}

