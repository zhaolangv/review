package com.gongkao.cuotifupan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.gongkao.cuotifupan.api.*
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.data.StandaloneNote
import com.gongkao.cuotifupan.data.StandaloneFlashcard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 数据迁移管理器
 * 负责数据的导出和导入
 */
class DataMigrationManager(private val context: Context) {
    
    private val TAG = "DataMigrationManager"
    private val db = AppDatabase.getDatabase(context)
    
    /**
     * 导出数据并创建迁移码
     * 
     * @param onProgress 进度回调 (current, total, message)
     * @return 导出结果（包含迁移码和过期时间），失败返回null
     */
    suspend fun exportData(
        onProgress: (Int, Int, String) -> Unit
    ): ExportResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "========== 开始导出数据 ==========")
            
            // 1. 收集数据
            onProgress(0, 100, "正在收集数据...")
            val questions = db.questionDao().getAllQuestionsSync()
            val notes = db.standaloneNoteDao().getAllNotesSync()
            val flashcards = db.standaloneFlashcardDao().getAllFlashcardsSync()
            
            Log.d(TAG, "收集到 ${questions.size} 道题目, ${notes.size} 条笔记, ${flashcards.size} 张卡片")
            
            // 2. 转换数据格式
            onProgress(10, 100, "正在转换数据格式...")
            val questionData = questions.map { question ->
                QuestionMigrationData(
                    id = question.id,
                    imagePath = question.imagePath,
                    originalImagePath = question.originalImagePath,
                    cleanedImagePath = question.cleanedImagePath,
                    rawText = question.rawText,
                    questionText = question.questionText,
                    frontendRawText = question.frontendRawText,
                    options = question.options,
                    createdAt = question.createdAt,
                    reviewState = question.reviewState,
                    userNotes = question.userNotes,
                    confidence = question.confidence,
                    questionType = question.questionType,
                    backendQuestionId = question.backendQuestionId,
                    backendQuestionText = question.backendQuestionText,
                    answerLoaded = question.answerLoaded,
                    correctAnswer = question.correctAnswer,
                    explanation = question.explanation,
                    tags = question.tags
                )
            }
            
            val noteData = notes.map { note ->
                NoteMigrationData(
                    id = note.id,
                    content = note.content,
                    createdAt = note.createdAt,
                    updatedAt = note.updatedAt,
                    tags = note.tags,
                    questionId = note.questionId,
                    isFavorite = note.isFavorite
                )
            }
            
            val flashcardData = flashcards.map { flashcard ->
                FlashcardMigrationData(
                    id = flashcard.id,
                    front = flashcard.front,
                    back = flashcard.back,
                    createdAt = flashcard.createdAt,
                    updatedAt = flashcard.updatedAt,
                    tags = flashcard.tags,
                    questionId = flashcard.questionId,
                    isFavorite = flashcard.isFavorite,
                    reviewState = flashcard.reviewState
                )
            }
            
            // 3. 处理图片
            onProgress(20, 100, "正在处理图片...")
            val images = mutableListOf<MigrationImage>()
            val totalImages = questions.size * 3 // 每个题目最多3张图片（main, original, cleaned）
            var processedImages = 0
            
            questions.forEach { question ->
                // 处理主图片
                question.imagePath.let { path ->
                    if (path.isNotEmpty()) {
                        val base64 = encodeImageToBase64(path)
                        if (base64 != null) {
                            images.add(MigrationImage(
                                question_id = question.id,
                                image_type = "main",
                                image_base64 = base64
                            ))
                            processedImages++
                            onProgress(20 + (processedImages * 60 / totalImages), 100, "正在处理图片 ($processedImages/$totalImages)...")
                        }
                    }
                }
                
                // 处理原始图片
                question.originalImagePath?.let { path ->
                    if (path.isNotEmpty()) {
                        val base64 = encodeImageToBase64(path)
                        if (base64 != null) {
                            images.add(MigrationImage(
                                question_id = question.id,
                                image_type = "original",
                                image_base64 = base64
                            ))
                            processedImages++
                            onProgress(20 + (processedImages * 60 / totalImages), 100, "正在处理图片 ($processedImages/$totalImages)...")
                        }
                    }
                }
                
                // 处理擦写后的图片
                question.cleanedImagePath?.let { path ->
                    if (path.isNotEmpty()) {
                        val base64 = encodeImageToBase64(path)
                        if (base64 != null) {
                            images.add(MigrationImage(
                                question_id = question.id,
                                image_type = "cleaned",
                                image_base64 = base64
                            ))
                            processedImages++
                            onProgress(20 + (processedImages * 60 / totalImages), 100, "正在处理图片 ($processedImages/$totalImages)...")
                        }
                    }
                }
            }
            
            Log.d(TAG, "处理了 ${images.size} 张图片")
            
            // 4. 构建请求
            onProgress(85, 100, "正在上传数据...")
            val deviceId = VersionChecker(context).getDeviceId()
            val request = CreateMigrationRequest(
                device_id = deviceId,
                data = MigrationData(
                    questions = questionData,
                    notes = noteData,
                    flashcards = flashcardData
                ),
                images = images
            )
            
            // 5. 发送请求
            val response = ApiClient.migrationApiService.createMigration(request)
            
            // 检查HTTP状态码
            if (!response.isSuccessful) {
                val errorBody = try {
                    response.errorBody()?.string()
                } catch (e: Exception) {
                    null
                }
                val errorMessage = if (errorBody != null && errorBody.isNotEmpty()) {
                    "服务器错误 (${response.code()}): $errorBody"
                } else {
                    "服务器错误 (${response.code()}): ${response.message()}"
                }
                Log.e(TAG, "❌ HTTP请求失败")
                Log.e(TAG, "   状态码: ${response.code()}")
                Log.e(TAG, "   错误消息: ${response.message()}")
                Log.e(TAG, "   错误体: $errorBody")
                onProgress(100, 100, "导出失败: $errorMessage")
                return@withContext null
            }
            
            // 检查响应体
            val responseBody = response.body()
            if (responseBody == null) {
                Log.e(TAG, "❌ 响应体为空")
                onProgress(100, 100, "导出失败: 服务器返回空响应")
                return@withContext null
            }
            
            // 检查业务逻辑是否成功
            if (responseBody.success == true) {
                val data = responseBody.data
                val migrationCode = data?.migration_code
                val expiresAt = data?.expires_at
                
                if (migrationCode != null && expiresAt != null) {
                    Log.d(TAG, "✅ 数据导出成功，迁移码: $migrationCode")
                    onProgress(100, 100, "导出完成")
                    ExportResult(migrationCode, expiresAt)
                } else {
                    Log.e(TAG, "❌ 响应数据不完整")
                    Log.e(TAG, "   migrationCode: $migrationCode")
                    Log.e(TAG, "   expiresAt: $expiresAt")
                    onProgress(100, 100, "导出失败: 响应数据不完整")
                    null
                }
            } else {
                val errorMessage = responseBody.message ?: responseBody.error ?: "导出失败"
                Log.e(TAG, "❌ 数据导出失败: $errorMessage")
                Log.e(TAG, "   错误码: ${responseBody.error}")
                onProgress(100, 100, "导出失败: $errorMessage")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "数据导出异常", e)
            onProgress(100, 100, "导出失败: ${e.message}")
            null
        }
    }
    
    /**
     * 导入数据
     * 
     * @param migrationCode 迁移码
     * @param onProgress 进度回调 (current, total, message)
     * @return 导入结果统计，失败返回null
     */
    suspend fun importData(
        migrationCode: String,
        onProgress: (Int, Int, String) -> Unit
    ): ImportResult? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "========== 开始导入数据 ==========")
            Log.d(TAG, "迁移码: $migrationCode")
            
            // 1. 获取迁移数据
            onProgress(0, 100, "正在获取迁移数据...")
            val deviceId = VersionChecker(context).getDeviceId()
            val request = RetrieveMigrationRequest(
                migration_code = migrationCode,
                device_id = deviceId
            )
            
            val response = ApiClient.migrationApiService.retrieveMigration(request)
            
            if (!response.isSuccessful || response.body()?.success != true) {
                val errorMessage = response.body()?.message ?: "获取迁移数据失败"
                Log.e(TAG, "❌ 获取迁移数据失败: $errorMessage")
                onProgress(100, 100, "导入失败: $errorMessage")
                return@withContext null
            }
            
            val migrationData = response.body()?.data ?: run {
                Log.e(TAG, "❌ 迁移数据为空")
                onProgress(100, 100, "导入失败: 迁移数据为空")
                return@withContext null
            }
            
            Log.d(TAG, "获取到 ${migrationData.questions.size} 道题目, ${migrationData.notes.size} 条笔记, ${migrationData.flashcards.size} 张卡片, ${migrationData.images.size} 张图片")
            
            // 2. 下载图片
            onProgress(10, 100, "正在下载图片...")
            val imageMap = mutableMapOf<Pair<String, String>, String>() // (question_id, image_type) -> local_path
            val totalImages = migrationData.images.size
            var downloadedImages = 0
            
            migrationData.images.forEach { imageInfo ->
                val localPath = downloadImage(imageInfo.image_url, imageInfo.question_id, imageInfo.image_type)
                if (localPath != null) {
                    imageMap[Pair(imageInfo.question_id, imageInfo.image_type)] = localPath
                    downloadedImages++
                    onProgress(10 + (downloadedImages * 30 / totalImages), 100, "正在下载图片 ($downloadedImages/$totalImages)...")
                }
            }
            
            Log.d(TAG, "下载了 ${imageMap.size} 张图片")
            
            // 3. 导入题目
            onProgress(40, 100, "正在导入题目...")
            var importedQuestions = 0
            migrationData.questions.forEach { questionData ->
                val question = Question(
                    id = questionData.id,
                    imagePath = imageMap[Pair(questionData.id, "main")] ?: questionData.imagePath,
                    originalImagePath = imageMap[Pair(questionData.id, "original")] ?: questionData.originalImagePath,
                    cleanedImagePath = imageMap[Pair(questionData.id, "cleaned")] ?: questionData.cleanedImagePath,
                    rawText = questionData.rawText,
                    questionText = questionData.questionText,
                    frontendRawText = questionData.frontendRawText,
                    options = questionData.options,
                    createdAt = questionData.createdAt,
                    reviewState = questionData.reviewState,
                    userNotes = questionData.userNotes,
                    confidence = questionData.confidence,
                    questionType = questionData.questionType,
                    backendQuestionId = questionData.backendQuestionId,
                    backendQuestionText = questionData.backendQuestionText,
                    answerLoaded = questionData.answerLoaded,
                    correctAnswer = questionData.correctAnswer,
                    explanation = questionData.explanation,
                    tags = questionData.tags
                )
                db.questionDao().insert(question)
                importedQuestions++
                onProgress(40 + (importedQuestions * 20 / migrationData.questions.size), 100, "正在导入题目 ($importedQuestions/${migrationData.questions.size})...")
            }
            
            // 4. 导入笔记
            onProgress(60, 100, "正在导入笔记...")
            var importedNotes = 0
            migrationData.notes.forEach { noteData ->
                val note = StandaloneNote(
                    id = noteData.id,
                    content = noteData.content,
                    createdAt = noteData.createdAt,
                    updatedAt = noteData.updatedAt,
                    tags = noteData.tags,
                    questionId = noteData.questionId,
                    isFavorite = noteData.isFavorite
                )
                db.standaloneNoteDao().insert(note)
                importedNotes++
                onProgress(60 + (importedNotes * 15 / migrationData.notes.size), 100, "正在导入笔记 ($importedNotes/${migrationData.notes.size})...")
            }
            
            // 5. 导入卡片
            onProgress(75, 100, "正在导入记忆卡片...")
            var importedFlashcards = 0
            migrationData.flashcards.forEach { flashcardData ->
                val flashcard = StandaloneFlashcard(
                    id = flashcardData.id,
                    front = flashcardData.front,
                    back = flashcardData.back,
                    createdAt = flashcardData.createdAt,
                    updatedAt = flashcardData.updatedAt,
                    tags = flashcardData.tags,
                    questionId = flashcardData.questionId,
                    isFavorite = flashcardData.isFavorite,
                    reviewState = flashcardData.reviewState
                )
                db.standaloneFlashcardDao().insert(flashcard)
                importedFlashcards++
                onProgress(75 + (importedFlashcards * 15 / migrationData.flashcards.size), 100, "正在导入记忆卡片 ($importedFlashcards/${migrationData.flashcards.size})...")
            }
            
            // 6. 确认迁移完成（重要：通知服务器删除数据）
            onProgress(90, 100, "正在确认迁移完成...")
            try {
                val confirmRequest = ConfirmMigrationRequest(
                    migration_code = migrationCode,
                    device_id = deviceId
                )
                val confirmResponse = ApiClient.migrationApiService.confirmMigration(confirmRequest)
                
                if (confirmResponse.isSuccessful && confirmResponse.body()?.success == true) {
                    Log.d(TAG, "✅ 迁移确认成功，服务器已删除数据")
                } else {
                    val errorMessage = confirmResponse.body()?.message ?: "确认失败"
                    Log.w(TAG, "⚠️ 迁移确认失败: $errorMessage（数据会保留1天后自动删除）")
                    // 确认失败不影响导入结果，数据会在1天后自动删除
                }
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ 迁移确认异常: ${e.message}（数据会保留1天后自动删除）", e)
                // 确认失败不影响导入结果，数据会在1天后自动删除
            }
            
            onProgress(100, 100, "导入完成")
            
            val result = ImportResult(
                questionsCount = importedQuestions,
                notesCount = importedNotes,
                flashcardsCount = importedFlashcards,
                imagesCount = imageMap.size
            )
            
            Log.d(TAG, "✅ 数据导入成功")
            Log.d(TAG, "   题目: $importedQuestions")
            Log.d(TAG, "   笔记: $importedNotes")
            Log.d(TAG, "   卡片: $importedFlashcards")
            Log.d(TAG, "   图片: ${imageMap.size}")
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "数据导入异常", e)
            onProgress(100, 100, "导入失败: ${e.message}")
            null
        }
    }
    
    /**
     * 将图片编码为Base64
     */
    private fun encodeImageToBase64(imagePath: String): String? {
        return try {
            val bitmap = ImageAccessHelper.decodeBitmap(context, imagePath)
            if (bitmap == null) {
                Log.w(TAG, "无法解码图片: $imagePath")
                return null
            }
            
            // 压缩图片（降低质量以减小体积）
            val outputStream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
            val imageBytes = outputStream.toByteArray()
            bitmap.recycle()
            
            Base64.encodeToString(imageBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "编码图片失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 从URL下载图片
     */
    private fun downloadImage(imageUrl: String, questionId: String, imageType: String): String? {
        return try {
            // 构建完整URL
            val fullUrl = if (imageUrl.startsWith("http")) {
                imageUrl
            } else {
                "${ApiClient.BASE_URL.trimEnd('/')}/$imageUrl"
            }
            
            // 下载图片
            val client = OkHttpClient()
            val request = Request.Builder().url(fullUrl).build()
            val response = client.newCall(request).execute()
            
            if (!response.isSuccessful) {
                Log.e(TAG, "下载图片失败: HTTP ${response.code}")
                return null
            }
            
            val imageBytes = response.body?.bytes() ?: return null
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            if (bitmap == null) {
                Log.e(TAG, "解码下载的图片失败")
                return null
            }
            
            // 保存到本地
            val questionsDir = File(context.filesDir, "questions")
            if (!questionsDir.exists()) {
                questionsDir.mkdirs()
            }
            
            val extension = when (imageType) {
                "cleaned" -> "cleaned.jpg"
                "original" -> "original.jpg"
                else -> "jpg"
            }
            val fileName = "question_${questionId}_${imageType}.$extension"
            val destFile = File(questionsDir, fileName)
            
            val outputStream = destFile.outputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            outputStream.close()
            bitmap.recycle()
            
            Log.d(TAG, "图片下载成功: ${destFile.absolutePath}")
            destFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "下载图片失败: $imageUrl", e)
            null
        }
    }
    
    /**
     * 导出结果
     */
    data class ExportResult(
        val migrationCode: String,
        val expiresAt: String // ISO 8601格式，如 "2026-01-15T23:59:59"
    )
    
    /**
     * 导入结果
     */
    data class ImportResult(
        val questionsCount: Int,
        val notesCount: Int,
        val flashcardsCount: Int,
        val imagesCount: Int
    )
}

