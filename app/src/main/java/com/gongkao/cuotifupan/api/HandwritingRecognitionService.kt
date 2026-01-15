package com.gongkao.cuotifupan.api

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.gongkao.cuotifupan.SnapReviewApplication
import com.gongkao.cuotifupan.ocr.digitalink.DigitalInkRecognitionHelper
import com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper
import com.gongkao.cuotifupan.ocr.trocr.TrOCROcrHelper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * 手写识别服务
 * 优先级：TrOCR > PaddleOCR > ML Kit
 */
object HandwritingRecognitionService {
    
    private const val TAG = "HandwritingRecognition"
    
    // ML Kit 识别器实例（作为备用方案）
    private val mlKitRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
    }
    
    // Digital Ink Recognition 初始化标志（在线手写识别，基于笔画轨迹）
    private var digitalInkInitialized = false
    
    // PaddleOCR 初始化标志
    private var paddleOcrInitialized = false
    
    // TrOCR 初始化标志
    private var trocrInitialized = false
    
    /**
     * 识别手写文字（从Bitmap）
     * @param bitmap 手写内容的图片
     * @return 识别结果文本，失败返回null
     */
    suspend fun recognizeHandwriting(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始识别手写，图片尺寸: ${bitmap.width}x${bitmap.height}")
            
            val context = SnapReviewApplication.getInstance()
            if (context != null) {
                // 1. 优先使用 TrOCR（如果模型文件存在）
                if (!trocrInitialized) {
                    trocrInitialized = TrOCROcrHelper.init(context)
                    if (trocrInitialized) {
                        Log.d(TAG, "TrOCR 初始化成功")
                    } else {
                        Log.d(TAG, "TrOCR 初始化失败（可能模型文件不存在）")
                    }
                }
                
                if (trocrInitialized && TrOCROcrHelper.isInitialized()) {
                    try {
                        val trocrResult = TrOCROcrHelper.recognizeText(bitmap)
                        if (trocrResult != null && trocrResult.isNotBlank()) {
                            Log.d(TAG, "TrOCR 识别成功: $trocrResult")
                            return@withContext trocrResult
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "TrOCR 识别出错", e)
                    }
                }
                
                // 2. 使用 PaddleOCR（本地识别，无需网络）
                // 初始化 PaddleOCR（如果还未初始化）
                if (!paddleOcrInitialized) {
                    paddleOcrInitialized = PaddleOcrHelper.init(context)
                    if (paddleOcrInitialized) {
                        Log.d(TAG, "PaddleOCR 初始化成功")
                    }
                }
                
                // 如果 PaddleOCR 已初始化，尝试使用它
                if (paddleOcrInitialized && PaddleOcrHelper.isInitialized()) {
                    try {
                        val paddleResult = PaddleOcrHelper.recognizeText(bitmap)
                        if (paddleResult != null && paddleResult.isNotBlank()) {
                            Log.d(TAG, "PaddleOCR 识别成功: $paddleResult")
                            return@withContext paddleResult
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "PaddleOCR 识别出错", e)
                    }
                }
            }
            
            // 3. 回退到 ML Kit（作为备用）
            Log.d(TAG, "使用 ML Kit 识别")
            val processedBitmap = preprocessBitmap(bitmap)
            
            val image = InputImage.fromBitmap(processedBitmap, 0)
            
            // 执行识别
            val result = suspendCancellableCoroutine<String?> { continuation ->
                mlKitRecognizer.process(image)
                    .addOnSuccessListener { visionText ->
                        val recognizedText = visionText.text
                        Log.d(TAG, "ML Kit 识别完成，文本块数量: ${visionText.textBlocks.size}")
                        if (recognizedText.isNotBlank()) {
                            Log.d(TAG, "ML Kit 识别成功: $recognizedText")
                            continuation.resume(recognizedText)
                        } else {
                            Log.w(TAG, "ML Kit 识别结果为空（文本块数量: ${visionText.textBlocks.size}）")
                            continuation.resume(null)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "ML Kit 识别失败", e)
                        continuation.resume(null)
                    }
            }
            
            // 释放预处理后的bitmap（如果是新创建的）
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "手写识别失败", e)
            null
        }
    }
    
    /**
     * 预处理图片：增强对比度，转换为灰度
     */
    private fun preprocessBitmap(bitmap: Bitmap): Bitmap {
        // 如果图片太小，先放大
        var processedBitmap = bitmap
        val minWidth = 200
        val minHeight = 200
        
        if (bitmap.width < minWidth || bitmap.height < minHeight) {
            val scale = maxOf(minWidth.toFloat() / bitmap.width, minHeight.toFloat() / bitmap.height) * 2f
            val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(minWidth)
            val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(minHeight)
            processedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
            Log.d(TAG, "放大图片: ${bitmap.width}x${bitmap.height} -> ${newWidth}x${newHeight}")
        }
        
        // 转换为灰度图并增强对比度
        val grayBitmap = Bitmap.createBitmap(
            processedBitmap.width,
            processedBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(grayBitmap)
        val paint = android.graphics.Paint()
        
        // 增强对比度和亮度
        val colorMatrix = android.graphics.ColorMatrix().apply {
            // 转换为灰度
            setSaturation(0f)
            // 增强对比度（1.5倍）
            val contrast = 1.5f
            val scale = contrast
            val translate = (-.5f * scale + .5f) * 255f
            set(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(processedBitmap, 0f, 0f, paint)
        
        // 如果创建了新bitmap，回收中间bitmap
        if (processedBitmap != bitmap) {
            processedBitmap.recycle()
        }
        
        return grayBitmap
    }
    
    /**
     * 识别手写文字（从笔画数据）
     * 优先级：Digital Ink Recognition > OCR (图片识别)
     * @param strokes 笔画数据列表
     * @return 识别结果文本，失败返回null
     */
    suspend fun recognizeHandwriting(strokes: List<com.gongkao.cuotifupan.ui.HandwritingInputView.Stroke>): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始识别手写（从笔画数据），笔画数量: ${strokes.size}")
            
            // 1. 优先使用 Digital Ink Recognition（基于笔画轨迹的在线识别）
            if (!digitalInkInitialized) {
                digitalInkInitialized = DigitalInkRecognitionHelper.init()
                if (digitalInkInitialized) {
                    Log.d(TAG, "Digital Ink Recognition 初始化成功")
                } else {
                    Log.w(TAG, "Digital Ink Recognition 初始化失败，将使用 OCR")
                }
            }
            
            if (digitalInkInitialized && DigitalInkRecognitionHelper.isInitialized()) {
                try {
                    val digitalInkResult = DigitalInkRecognitionHelper.recognizeText(strokes)
                    if (digitalInkResult != null && digitalInkResult.isNotBlank()) {
                        Log.d(TAG, "Digital Ink Recognition 识别成功: $digitalInkResult")
                        return@withContext digitalInkResult
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Digital Ink Recognition 识别出错，回退到 OCR", e)
                }
            }
            
            // 2. 回退到 OCR（将笔画数据转换为图片后识别）
            Log.d(TAG, "使用 OCR 识别（将笔画转换为图片）")
            val bitmap = strokesToBitmap(strokes)
            if (bitmap == null) {
                Log.e(TAG, "无法将笔画转换为图片")
                return@withContext null
            }
            
            // 使用图片识别
            val result = recognizeHandwriting(bitmap)
            
            // 释放临时 Bitmap
            bitmap.recycle()
            
            return@withContext result
        } catch (e: Exception) {
            Log.e(TAG, "手写识别失败", e)
            null
        }
    }
    
    /**
     * 将笔画数据转换为 Bitmap
     */
    private fun strokesToBitmap(strokes: List<com.gongkao.cuotifupan.ui.HandwritingInputView.Stroke>): Bitmap? {
        if (strokes.isEmpty()) {
            Log.w(TAG, "笔画数据为空")
            return null
        }
        
        // 计算边界
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        strokes.forEach { stroke ->
            stroke.points.forEach { point ->
                minX = minOf(minX, point.x)
                minY = minOf(minY, point.y)
                maxX = maxOf(maxX, point.x)
                maxY = maxOf(maxY, point.y)
            }
        }
        
        Log.d(TAG, "笔画边界: ($minX, $minY) -> ($maxX, $maxY)")
        
        // 添加边距
        val padding = 30f
        val width = (maxX - minX + padding * 2).toInt().coerceAtLeast(200)
        val height = (maxY - minY + padding * 2).toInt().coerceAtLeast(200)
        
        Log.d(TAG, "生成图片尺寸: ${width}x${height}")
        
        // 创建 Bitmap（使用更高的分辨率以提高识别率）
        val scale = 2f // 放大2倍以提高清晰度
        val scaledWidth = (width * scale).toInt()
        val scaledHeight = (height * scale).toInt()
        val bitmap = Bitmap.createBitmap(scaledWidth, scaledHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        
        // 白色背景
        canvas.drawColor(android.graphics.Color.WHITE)
        
        // 绘制笔画（使用更粗的线条）
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 12f * scale // 放大后的线条宽度
            isAntiAlias = true
            strokeJoin = android.graphics.Paint.Join.ROUND
            strokeCap = android.graphics.Paint.Cap.ROUND
        }
        
        strokes.forEach { stroke ->
            if (stroke.points.size < 2) return@forEach
            
            val path = android.graphics.Path()
            val firstPoint = stroke.points[0]
            val offsetX = (firstPoint.x - minX + padding) * scale
            val offsetY = (firstPoint.y - minY + padding) * scale
            path.moveTo(offsetX, offsetY)
            
            for (i in 1 until stroke.points.size) {
                val point = stroke.points[i]
                val x = (point.x - minX + padding) * scale
                val y = (point.y - minY + padding) * scale
                path.lineTo(x, y)
            }
            
            canvas.drawPath(path, paint)
        }
        
        Log.d(TAG, "笔画转换完成，最终图片尺寸: ${scaledWidth}x${scaledHeight}")
        return bitmap
    }
    
    /**
     * 释放资源
     */
    fun release() {
        mlKitRecognizer.close()
        DigitalInkRecognitionHelper.release()
        digitalInkInitialized = false
        PaddleOcrHelper.release()
        paddleOcrInitialized = false
        TrOCROcrHelper.release()
        trocrInitialized = false
    }
}
