package com.gongkao.cuotifupan.ocr.digitalink

import android.util.Log
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizer
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import com.google.mlkit.vision.digitalink.RecognitionResult
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.gongkao.cuotifupan.ui.HandwritingInputView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * ML Kit Digital Ink Recognition 帮助类
 * 基于笔画轨迹的在线手写识别（无需转换为图片）
 * 
 * 优势：
 * - 直接使用笔画数据，无需转换为 Bitmap
 * - 利用笔画顺序、速度等动态信息，识别准确率更高
 * - 支持实时识别
 * - 支持中文识别
 */
object DigitalInkRecognitionHelper {
    private const val TAG = "DigitalInkRecognition"
    
    private var recognizer: DigitalInkRecognizer? = null
    private var modelIdentifier: DigitalInkRecognitionModelIdentifier? = null
    private var isInitialized = false
    
    /**
     * 初始化识别器（中文模型）
     */
    suspend fun init(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && recognizer != null) {
            return@withContext true
        }
        
        try {
            // 获取中文模型标识符（简体中文）
            modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hani-CN")
            if (modelIdentifier == null) {
                Log.e(TAG, "无法获取中文模型标识符")
                return@withContext false
            }
            
            // 创建模型
            val model = DigitalInkRecognitionModel.builder(modelIdentifier!!).build()
            
            // 检查模型是否已下载
            val remoteModelManager = RemoteModelManager.getInstance()
            val downloadConditions = DownloadConditions.Builder().build()
            
            // 如果模型未下载，先下载
            val isDownloaded = suspendCancellableCoroutine<Boolean> { continuation ->
                remoteModelManager.isModelDownloaded(model)
                    .addOnSuccessListener { downloaded ->
                        if (downloaded) {
                            Log.d(TAG, "模型已下载")
                            continuation.resume(true)
                        } else {
                            Log.d(TAG, "模型未下载，开始下载中文手写识别模型...")
                            Log.d(TAG, "这可能需要一些时间，请保持网络连接")
                            
                            remoteModelManager.download(model, downloadConditions)
                                .addOnSuccessListener {
                                    Log.d(TAG, "✅ 模型下载成功，可以开始识别")
                                    continuation.resume(true)
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "❌ 模型下载失败", e)
                                    Log.e(TAG, "错误: ${e.message}")
                                    Log.w(TAG, "将尝试使用本地模型（如果存在）")
                                    // 即使下载失败，也尝试创建识别器（可能已有本地模型）
                                    continuation.resume(true)
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "检查模型状态失败", e)
                        Log.w(TAG, "将尝试使用本地模型（如果存在）")
                        // 即使检查失败，也尝试创建识别器（模型可能已在本地）
                        continuation.resume(true)
                    }
            }
            
            // 创建识别器
            val options = DigitalInkRecognizerOptions.builder(model).build()
            recognizer = DigitalInkRecognition.getClient(options)
            
            isInitialized = true
            Log.d(TAG, "✅ Digital Ink Recognition 初始化成功（中文模型）")
            Log.d(TAG, "模型标识: ${modelIdentifier?.languageTag ?: "unknown"}")
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "Digital Ink Recognition 初始化失败", e)
            isInitialized = false
            recognizer = null
            return@withContext false
        }
    }
    
    /**
     * 识别手写文字（基于笔画轨迹）
     * 
     * @param strokes 笔画数据列表
     * @return 识别结果文本，失败返回 null
     */
    suspend fun recognizeText(strokes: List<HandwritingInputView.Stroke>): String? = withContext(Dispatchers.IO) {
        if (!isInitialized || recognizer == null) {
            Log.e(TAG, "Digital Ink Recognition 未初始化")
            return@withContext null
        }
        
        if (strokes.isEmpty()) {
            Log.w(TAG, "笔画数据为空")
            return@withContext null
        }
        
        try {
            // 将笔画数据转换为 ML Kit 的 Ink 格式
            val ink = convertStrokesToInk(strokes)
            
            // 执行识别
            val result = suspendCancellableCoroutine<String?> { continuation ->
                recognizer?.recognize(ink)
                    ?.addOnSuccessListener { recognitionResult: RecognitionResult ->
                        val candidates = recognitionResult.candidates
                        if (candidates.isNotEmpty()) {
                            // 获取置信度最高的结果
                            val bestCandidate = candidates[0]
                            val recognizedText = bestCandidate.text
                            val score = bestCandidate.score
                            
                            Log.d(TAG, "识别成功: $recognizedText (置信度: $score)")
                            
                            // 如果置信度太低，记录警告（但不拒绝结果）
                            val scoreValue = score ?: 0f
                            if (scoreValue < 0.5f) {
                                Log.w(TAG, "识别置信度较低: $scoreValue，结果: $recognizedText")
                            }
                            
                            // 记录所有候选结果（用于调试）
                            if (candidates.size > 1) {
                                Log.d(TAG, "其他候选结果:")
                                candidates.drop(1).take(3).forEachIndexed { index, candidate ->
                                    Log.d(TAG, "  [${index + 2}] ${candidate.text} (置信度: ${candidate.score})")
                                }
                            }
                            
                            continuation.resume(recognizedText)
                        } else {
                            Log.w(TAG, "识别结果为空（笔画数量: ${strokes.size}）")
                            continuation.resume(null)
                        }
                    }
                    ?.addOnFailureListener { e ->
                        Log.e(TAG, "识别失败", e)
                        Log.e(TAG, "错误详情: ${e.message}")
                        continuation.resume(null)
                    }
                
                continuation.invokeOnCancellation {
                    // 可以在这里取消识别任务
                }
            }
            
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "识别过程出错", e)
            return@withContext null
        }
    }
    
    /**
     * 将应用的笔画数据转换为 ML Kit 的 Ink 格式
     * 
     * 优化点：
     * 1. 确保时间戳是递增的（ML Kit 要求）
     * 2. 过滤掉距离太近的点（减少噪声）
     * 3. 确保每个笔画至少有两个点
     */
    private fun convertStrokesToInk(strokes: List<HandwritingInputView.Stroke>): Ink {
        val inkBuilder = Ink.builder()
        
        // 获取第一个点的时间戳作为基准
        val baseTimestamp = strokes.firstOrNull()?.points?.firstOrNull()?.timestamp ?: System.currentTimeMillis()
        
        // 最小点间距（像素），用于过滤噪声
        val minDistance = 2.0f
        
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            
            val strokeBuilder = Ink.Stroke.builder()
            var lastPointX = 0f
            var lastPointY = 0f
            var hasLastPoint = false
            var lastRelativeTimestamp = 0L
            
            stroke.points.forEach { point ->
                // 计算相对于基准时间的时间戳（毫秒）
                var relativeTimestamp = (point.timestamp - baseTimestamp).coerceAtLeast(0L)
                
                // 确保时间戳是递增的（ML Kit 要求）
                if (relativeTimestamp <= lastRelativeTimestamp) {
                    relativeTimestamp = lastRelativeTimestamp + 1L
                }
                lastRelativeTimestamp = relativeTimestamp
                
                // 过滤掉距离太近的点（减少噪声，提高识别率）
                if (hasLastPoint) {
                    val dx = point.x - lastPointX
                    val dy = point.y - lastPointY
                    val distance = kotlin.math.sqrt(dx * dx + dy * dy)
                    
                    if (distance < minDistance && stroke.points.size > 2) {
                        // 如果距离太近，跳过这个点（除非是最后一个点）
                        return@forEach
                    }
                }
                
                strokeBuilder.addPoint(
                    Ink.Point.create(point.x, point.y, relativeTimestamp)
                )
                
                lastPointX = point.x
                lastPointY = point.y
                hasLastPoint = true
            }
            
            val builtStroke = strokeBuilder.build()
            // 确保笔画至少有两个点
            if (builtStroke.points.size >= 2) {
                inkBuilder.addStroke(builtStroke)
            }
        }
        
        return inkBuilder.build()
    }
    
    /**
     * 释放资源
     */
    fun release() {
        recognizer?.close()
        recognizer = null
        isInitialized = false
        Log.d(TAG, "Digital Ink Recognition 资源已释放")
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitialized
}

