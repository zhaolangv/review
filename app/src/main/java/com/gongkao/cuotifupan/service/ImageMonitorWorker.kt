package com.gongkao.cuotifupan.service

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.gongkao.cuotifupan.api.QuestionApiQueue
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.detector.QuestionDetector
import com.gongkao.cuotifupan.ocr.TextRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * 监听相册新图片的 Worker
 */
class ImageMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    private val TAG = "ImageMonitorWorker"
    
    override suspend fun doWork(): Result {
        Log.d(TAG, "开始监听相册新图片")
        
        // 获取最新的图片
        val latestImage = getLatestImage()
        if (latestImage != null) {
            processNewImage(latestImage)
        }
        
        return Result.success()
    }
    
    /**
     * 获取最新的图片
     */
    private suspend fun getLatestImage(): ImageInfo? = withContext(Dispatchers.IO) {
        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            val cursor = applicationContext.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    val path = it.getString(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    val dateAdded = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED))
                    
                    // 只处理最近1分钟内的图片
                    val currentTime = System.currentTimeMillis() / 1000
                    if (currentTime - dateAdded < 60) {
                        return@withContext ImageInfo(id, path, dateAdded)
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "获取最新图片失败", e)
            null
        }
    }
    
    /**
     * 处理新图片
     */
    private suspend fun processNewImage(imageInfo: ImageInfo) {
        try {
            // 1. OCR 识别
            val recognizer = TextRecognizer()
            val ocrResult = recognizer.recognizeText(imageInfo.path)
            
            if (!ocrResult.success) {
                Log.e(TAG, "OCR识别失败: ${ocrResult.errorMessage}")
                return
            }
            
            // 2. 判断是否为题目
            val detector = QuestionDetector()
            val detection = detector.detect(ocrResult)
            
            if (detection.isQuestion) {
                Log.d(TAG, "检测到题目，置信度: ${detection.confidence}")
                
                // 3. 判断题目类型（文字题 vs 图推题）
                val questionType = determineQuestionType(ocrResult.rawText, detection.questionText)
                
                val question = Question(
                    imagePath = imageInfo.path,
                    rawText = ocrResult.rawText,
                    questionText = detection.questionText,
                    options = JSONArray(detection.options).toString(),
                    confidence = detection.confidence,
                    questionType = questionType  // 根据关键词判断类型
                )
                
                val database = AppDatabase.getDatabase(applicationContext)
                
                // 根据题目类型处理
                if (questionType == "TEXT") {
                    // 文字题：调用后端API获取题目内容
                    Log.d(TAG, "📤 文字题，加入API请求队列（只获取题目内容）")
                    
                    QuestionApiQueue.enqueue(
                        question = question,
                        onSuccess = { response ->
                            withContext(Dispatchers.IO) {
                                try {
                                    // 更新题目信息
                                    val updatedQuestion = question.copy(
                                        backendQuestionId = response.id,
                                        backendQuestionText = response.questionText,
                                        answerLoaded = false
                                    )
                                    
                                    database.questionDao().update(updatedQuestion)
                                    
                                    Log.d(TAG, "✅ 文字题已更新（题目内容）")
                                    
                                    // 显示通知
                                    val finalQuestion = database.questionDao().getQuestionById(updatedQuestion.id)
                                    if (finalQuestion != null) {
                                        showQuestionNotification(applicationContext, finalQuestion)
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "更新题目失败", e)
                                }
                            }
                        },
                        onError = { error ->
                            withContext(Dispatchers.IO) {
                                try {
                                    // API请求失败，仍然保存题目
                                    Log.w(TAG, "API请求失败，使用前端OCR结果保存: ${error.message}")
                                    database.questionDao().insert(question)
                                    showQuestionNotification(applicationContext, question)
                                } catch (e: Exception) {
                                    Log.e(TAG, "保存题目失败", e)
                                }
                            }
                        }
                    )
                } else {
                    // 图推题：直接保存
                    database.questionDao().insert(question)
                    Log.d(TAG, "✅ 图推题已保存到数据库")
                    showQuestionNotification(applicationContext, question)
                }
            } else {
                Log.d(TAG, "不是题目，置信度: ${detection.confidence}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理图片失败", e)
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
            return "GRAPHIC"
        }
        
        // 特殊检测：OCR文本很短且包含问号，可能是图推题
        // 计算有效文本长度（去掉换行符、空格、选项标记、数字选项标记、问号）
        val cleanText = trimmedText
            .replace(Regex("[\\n\\r\\s]"), "")
            .replace(Regex("[a-d]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[①②③④⑤⑥⑦⑧⑨⑩]"), "")
            .replace(Regex("[?？]"), "")
        val cleanTextLength = cleanText.length
        val hasQuestionMark = trimmedText.contains("?") || trimmedText.contains("？")
        
        // 计算选项标记和数字选项标记的比例
        val optionCount = trimmedText.replace(Regex("[\\n\\r\\s]"), "").let { text ->
            text.count { it in "aAbBcCdD" || it in "①②③④⑤⑥⑦⑧⑨⑩" || it in "?？" }
        }
        val totalLength = trimmedText.replace(Regex("[\\n\\r\\s]"), "").length
        val optionRatio = if (totalLength > 0) optionCount.toDouble() / totalLength else 0.0
        
        // 如果有效文本很短（少于30个字符）且包含问号，且选项标记比例高（>50%），很可能是图推题
        if (cleanTextLength < 30 && hasQuestionMark && optionRatio > 0.5) {
            return "GRAPHIC"
        }
        
        // 如果有效文本很短（少于25个字符）且包含问号，可能是图推题
        if (cleanTextLength < 25 && hasQuestionMark) {
            return "GRAPHIC"
        }
        
        // 如果有效文本很短（少于25个字符）且选项标记比例高（>50%），可能是图推题
        if (cleanTextLength < 25 && optionRatio > 0.5) {
            return "GRAPHIC"
        }
        
        // 如果文本很短（少于等于25个字符）且没有明显的题目关键词，可能是图推题
        if (cleanTextLength <= 25) {
            val hasTextKeywords = listOf(
                "下列", "正确的是", "错误的是", "属于", "不属于",
                "选择", "关于", "描述", "定义", "特点", "作用", "影响", "原因",
                "根据", "按照", "哪个", "什么", "如何", "怎样", "为什么",
                "最恰当", "填入", "画横线", "画橫线", "画横线部分", "画橫线部分"
            ).any { keyword -> combinedText.contains(keyword) }
            
            if (!hasTextKeywords && optionRatio > 0.35) {
                return "GRAPHIC"
            }
        }
        
        // 如果文本很长（>100字符），更可能是文字题
        if (textLength > 100) {
            return "TEXT"
        }
        
        // 默认标记为文字题
        return "TEXT"
    }
    
    /**
     * 显示通知
     */
    private fun showQuestionNotification(context: Context, question: Question) {
        // 不再显示通知弹窗，直接加入题库
        // NotificationHelper.showQuestionDetectedNotification(context, question)
    }
    
    data class ImageInfo(
        val id: Long,
        val path: String,
        val dateAdded: Long
    )
}

