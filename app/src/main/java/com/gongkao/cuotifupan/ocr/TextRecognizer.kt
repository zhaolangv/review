package com.gongkao.cuotifupan.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Rect
import android.util.Log
import kotlin.math.max
import kotlin.math.min
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/**
 * OCR 识别器（使用 ML Kit）
 * 
 * ML Kit 是 Google 提供的机器学习工具包，支持中文识别
 * 完全离线运行，不需要网络连接
 */
class TextRecognizer {
    
    companion object {
        private const val TAG = "TextRecognizer"
        
        // 使用ML Kit进行OCR识别
        private val mlKitRecognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
    }
    
    /**
     * 获取当前使用的OCR引擎信息
     */
    fun getOcrEngineInfo(): String {
        return "ML Kit (离线)"
    }
    
    /**
     * 图片预处理：提高OCR识别准确率
     * 包括：去除遮挡、增强对比度、灰度转换
     */
    private fun preprocessImage(bitmap: Bitmap): Bitmap {
        // 1. 如果图片太大，先缩放（建议宽度不超过1920px）
        var processedBitmap = bitmap
        val maxWidth = 1920
        if (bitmap.width > maxWidth) {
            val scale = maxWidth.toFloat() / bitmap.width
            val scaledHeight = (bitmap.height * scale).toInt()
            processedBitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, scaledHeight, true)
            if (processedBitmap != bitmap) {
                bitmap.recycle() // 回收原图
            }
        }
        
        // 2. 去除遮挡层（红色/绿色笔触等）
        val deoccludedBitmap = removeOverlay(processedBitmap)
        if (deoccludedBitmap != processedBitmap && processedBitmap != bitmap) {
            processedBitmap.recycle()
        }
        processedBitmap = deoccludedBitmap
        
        // 3. 转换为灰度图并增强对比度（提高文字识别率）
        val grayBitmap = Bitmap.createBitmap(
            processedBitmap.width,
            processedBitmap.height,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(grayBitmap)
        val paint = Paint()
        
        // 增强对比度和亮度
        val colorMatrix = ColorMatrix().apply {
            // 转换为灰度
            setSaturation(0f)
            // 增强对比度（1.3倍）
            val contrast = 1.3f
            val scale = contrast
            val translate = (-.5f * scale + .5f) * 255f
            set(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
        }
        
        paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(processedBitmap, 0f, 0f, paint)
        
        // 如果创建了新bitmap，回收中间bitmap
        if (processedBitmap != bitmap) {
            processedBitmap.recycle()
        }
        
        return grayBitmap
    }
    
    /**
     * 去除遮挡层（红色/绿色笔触等）
     * 通过检测非文字颜色的区域，尝试恢复底层文字
     */
    private fun removeOverlay(bitmap: Bitmap): Bitmap {
        try {
            val width = bitmap.width
            val height = bitmap.height
            val config = bitmap.config ?: Bitmap.Config.ARGB_8888
            val resultBitmap = bitmap.copy(config, true)
            
            // 采样检测（提高性能）
            val sampleStep = maxOf(1, minOf(width, height) / 200)
            
            // 统计背景色（通常是白色或浅色）
            val backgroundColors = mutableListOf<Int>()
            for (y in 0 until height step sampleStep * 5) {
                for (x in 0 until width step sampleStep * 5) {
                    val pixel = bitmap.getPixel(x, y)
                    val brightness = getPixelBrightness(pixel)
                    // 如果是浅色（可能是背景），记录颜色
                    if (brightness > 0.8f) {
                        backgroundColors.add(pixel)
                    }
                }
            }
            
            // 计算平均背景色
            val avgBackground = if (backgroundColors.isNotEmpty()) {
                val avgR = backgroundColors.map { Color.red(it) }.average().toInt()
                val avgG = backgroundColors.map { Color.green(it) }.average().toInt()
                val avgB = backgroundColors.map { Color.blue(it) }.average().toInt()
                Color.rgb(avgR, avgG, avgB)
            } else {
                Color.WHITE
            }
            
            // 检测并去除遮挡层（红色、绿色等非文字颜色）
            var removedCount = 0
            for (y in 0 until height step sampleStep) {
                for (x in 0 until width step sampleStep) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    
                    // 检测是否是遮挡颜色（红色、绿色等鲜艳颜色）
                    val isOverlay = detectOverlayColor(r, g, b)
                    
                    if (isOverlay) {
                        // 将遮挡区域替换为背景色
                        resultBitmap.setPixel(x, y, avgBackground)
                        removedCount++
                        
                        // 同时处理周围像素（去除遮挡的扩散效果）
                        for (dy in -1..1) {
                            for (dx in -1..1) {
                                val nx = x + dx * sampleStep
                                val ny = y + dy * sampleStep
                                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                    val neighborPixel = bitmap.getPixel(nx, ny)
                                    val nr = Color.red(neighborPixel)
                                    val ng = Color.green(neighborPixel)
                                    val nb = Color.blue(neighborPixel)
                                    if (detectOverlayColor(nr, ng, nb)) {
                                        resultBitmap.setPixel(nx, ny, avgBackground)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            if (removedCount > 0) {
                Log.d(TAG, "去除遮挡层: 处理了约 $removedCount 个像素点")
            }
            
            return resultBitmap
        } catch (e: Exception) {
            Log.w(TAG, "去除遮挡层失败，使用原图", e)
            return bitmap
        }
    }
    
    /**
     * 检测是否是遮挡颜色（红色、绿色等鲜艳颜色）
     */
    private fun detectOverlayColor(r: Int, g: Int, b: Int): Boolean {
        // 检测鲜艳的红色（R值高，G和B值低）
        val isRed = r > 200 && g < 150 && b < 150 && (r - g) > 50 && (r - b) > 50
        
        // 检测鲜艳的绿色（G值高，R和B值低）
        val isGreen = g > 200 && r < 150 && b < 150 && (g - r) > 50 && (g - b) > 50
        
        // 检测其他鲜艳颜色（蓝色、黄色等）
        val isBlue = b > 200 && r < 150 && g < 150 && (b - r) > 50 && (b - g) > 50
        val isYellow = r > 200 && g > 200 && b < 100 && (r + g - b) > 200
        
        // 检测高饱和度颜色（可能是遮挡）
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val saturation = if (max > 0) (max - min).toFloat() / max else 0f
        val isHighSaturation = saturation > 0.5f && max > 180
        
        return isRed || isGreen || isBlue || isYellow || isHighSaturation
    }
    
    /**
     * 获取像素亮度
     */
    private fun getPixelBrightness(pixel: Int): Float {
        val r = Color.red(pixel) / 255f
        val g = Color.green(pixel) / 255f
        val b = Color.blue(pixel) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
    
    /**
     * 识别图片中的文字
     */
    suspend fun recognizeText(imagePath: String): OcrResult {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.w(TAG, "⚠️ 图片文件不存在: $imagePath")
                return OcrResult("", emptyList(), emptyList(), false, "文件不存在")
            }
            
            val fileSize = file.length()
            if (fileSize == 0L) {
                Log.w(TAG, "⚠️ 图片文件为空: $imagePath (大小: 0 bytes)")
                return OcrResult("", emptyList(), emptyList(), false, "文件为空")
            }
            
            Log.d(TAG, "🔍 开始解码图片: $imagePath (大小: ${fileSize / 1024}KB)")
            var bitmap = BitmapFactory.decodeFile(imagePath)
            if (bitmap == null) {
                Log.e(TAG, "❌ 图片解码失败: $imagePath")
                Log.e(TAG, "   文件大小: ${fileSize / 1024}KB")
                Log.e(TAG, "   可能原因: 1) 图片格式不支持 2) 图片损坏 3) 内存不足")
                
                // 尝试使用Options获取更多信息
                val options = BitmapFactory.Options()
                options.inJustDecodeBounds = true
                BitmapFactory.decodeFile(imagePath, options)
                Log.e(TAG, "   图片信息: width=${options.outWidth}, height=${options.outHeight}, mimeType=${options.outMimeType}")
                
                return OcrResult("", emptyList(), emptyList(), false, "图片解码失败 (${options.outMimeType ?: "未知格式"})")
            }
            
            Log.d(TAG, "✅ 图片解码成功: ${bitmap.width}x${bitmap.height}")
            
            // 图片预处理：提高识别准确率
            val processedBitmap = preprocessImage(bitmap)
            if (processedBitmap != bitmap) {
                bitmap.recycle() // 回收原图
            }
            
            // 使用ML Kit识别
            val result = recognizeWithMLKit(processedBitmap)
            
            processedBitmap.recycle()
            result
        } catch (e: Exception) {
            Log.e(TAG, "❌ OCR识别失败: $imagePath", e)
            Log.e(TAG, "   异常类型: ${e.javaClass.simpleName}")
            Log.e(TAG, "   异常消息: ${e.message}")
            e.printStackTrace()
            OcrResult("", emptyList(), emptyList(), false, e.message ?: "识别失败")
        }
    }
    
    /**
     * 识别 Bitmap
     */
    suspend fun recognizeText(bitmap: Bitmap): OcrResult {
        return try {
            // 图片预处理：提高识别准确率
            val processedBitmap = preprocessImage(bitmap)
            
            // 使用ML Kit识别
            val result = recognizeWithMLKit(processedBitmap)
            
            // 如果处理了图片，回收处理后的bitmap
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "识别失败", e)
            OcrResult("", emptyList(), emptyList(), false, e.message ?: "识别失败")
        }
    }
    
    /**
     * 使用ML Kit进行OCR识别（完全离线）
     */
    private suspend fun recognizeWithMLKit(bitmap: Bitmap): OcrResult {
        return withContext(Dispatchers.IO) {
            try {
                val startTime = System.currentTimeMillis()
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val visionText = mlKitRecognizer.process(inputImage).await()
                val duration = System.currentTimeMillis() - startTime
                
                val rawText = visionText.text
                val lines = visionText.textBlocks.flatMap { block ->
                    block.lines.map { it.text }
                }
                
                // 提取文字块和位置信息（包括角点，用于倾斜检测）
                val textBlocks = visionText.textBlocks.map { block ->
                    val blockBox = block.boundingBox ?: Rect()
                    val blockLines = block.lines.map { line ->
                        // 提取角点信息（如果可用）
                        val cornerPoints = try {
                            // ML Kit的TextLine有cornerPoints属性，返回4个Point
                            line.cornerPoints?.map { android.graphics.Point(it.x, it.y) } ?: emptyList()
                        } catch (e: Exception) {
                            // 如果cornerPoints不可用，使用boundingBox的四个角
                            val box = line.boundingBox ?: Rect()
                            if (box.width() > 0 && box.height() > 0) {
                                listOf(
                                    android.graphics.Point(box.left, box.top),      // 左上
                                    android.graphics.Point(box.right, box.top),   // 右上
                                    android.graphics.Point(box.right, box.bottom), // 右下
                                    android.graphics.Point(box.left, box.bottom)   // 左下
                                )
                            } else {
                                emptyList()
                            }
                        }
                        
                        TextLine(
                            text = line.text,
                            boundingBox = line.boundingBox ?: Rect(),
                            cornerPoints = cornerPoints
                        )
                    }
                    TextBlock(
                        text = block.text,
                        boundingBox = blockBox,
                        lines = blockLines
                    )
                }
                
                // 详细记录识别结果
                Log.d(TAG, "✅ ML Kit识别完成，耗时: ${duration}ms")
                Log.d(TAG, "   识别文本长度: ${rawText.length}")
                Log.d(TAG, "   文本块数量: ${visionText.textBlocks.size}")
                Log.d(TAG, "   行数: ${lines.size}")
                if (rawText.isNotEmpty()) {
                    Log.d(TAG, "   识别文本预览: ${rawText.take(100)}...")
                } else {
                    Log.w(TAG, "   ⚠️ 识别结果为空（图片可能没有文字或无法识别）")
                }
                
                OcrResult(
                    rawText = rawText,
                    lines = lines,
                    textBlocks = textBlocks,
                    success = true
                )
            } catch (e: Exception) {
                Log.e(TAG, "❌ ML Kit识别失败", e)
                Log.e(TAG, "   异常类型: ${e.javaClass.simpleName}")
                Log.e(TAG, "   异常消息: ${e.message}")
                Log.e(TAG, "   图片尺寸: ${bitmap.width}x${bitmap.height}")
                e.printStackTrace()
                OcrResult("", emptyList(), emptyList(), false, e.message ?: "识别失败")
            }
        }
    }
}
