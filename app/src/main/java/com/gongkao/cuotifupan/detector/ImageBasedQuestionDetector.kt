package com.gongkao.cuotifupan.detector

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 基于图像算法的题目区域检测器
 * 使用投影分析、文本密度分析等方法识别题目块
 */
class ImageBasedQuestionDetector {
    
    companion object {
        private const val TAG = "ImageBasedQuestionDetector"
        
        // 文本密度阈值（用于区分文本区域和空白区域）
        private const val TEXT_DENSITY_THRESHOLD = 0.05f  // 5%的像素为文本（降低阈值，更容易检测到文本行）
        
        // 最小题目高度（像素）
        private const val MIN_QUESTION_HEIGHT = 80
        
        // 题目之间的最小间距（像素）- 增大这个值，避免过度合并
        private const val MIN_QUESTION_GAP = 80  // 增大间距阈值，避免把多个题目合并成一个
    }
    
    /**
     * 使用投影分析检测题目区域
     * @param bitmap 灰度图或二值化图像
     * @return 检测到的题目区域列表（按从上到下的顺序）
     */
    fun detectQuestionRegionsByProjection(bitmap: Bitmap): List<Rect> {
        // 1. 转换为灰度图（如果还不是）
        val grayBitmap = if (bitmap.config == Bitmap.Config.ALPHA_8) {
            bitmap
        } else {
            convertToGrayscale(bitmap)
        }
        
        // 2. 二值化（可选，提高对比度）
        val binaryBitmap = adaptiveThreshold(grayBitmap)
        
        // 3. 水平投影：计算每一行的文本密度
        val horizontalProjection = calculateHorizontalProjection(binaryBitmap)
        
        // 4. 识别文本行区域（文本密度高的区域）
        val textRegions = findTextRegions(horizontalProjection, binaryBitmap.width, binaryBitmap.height)
        
        // 5. 合并相邻的文本区域，形成题目块
        val questionRegions = mergeTextRegionsToQuestions(textRegions, binaryBitmap.width)
        
        // 清理临时bitmap
        if (grayBitmap != bitmap) {
            grayBitmap.recycle()
        }
        if (binaryBitmap != grayBitmap) {
            binaryBitmap.recycle()
        }
        
        Log.d(TAG, "图像分析检测到 ${questionRegions.size} 个题目区域")
        return questionRegions
    }
    
    /**
     * 转换为灰度图
     */
    private fun convertToGrayscale(bitmap: Bitmap): Bitmap {
        val grayBitmap = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ALPHA_8
        )
        
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            // 使用标准灰度转换公式
            val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            pixels[i] = Color.argb(255, gray, gray, gray)
        }
        
        grayBitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return grayBitmap
    }
    
    /**
     * 自适应阈值二值化
     */
    private fun adaptiveThreshold(bitmap: Bitmap): Bitmap {
        val binaryBitmap = Bitmap.createBitmap(
            bitmap.width,
            bitmap.height,
            Bitmap.Config.ALPHA_8
        )
        
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        // 计算平均灰度值作为阈值
        var sum = 0L
        for (pixel in pixels) {
            sum += Color.red(pixel)  // 灰度图中RGB值相同
        }
        val threshold = (sum / pixels.size).toInt()
        
        // 二值化：小于阈值的为黑色（文本），大于阈值的为白色（背景）
        for (i in pixels.indices) {
            val gray = Color.red(pixels[i])
            val binary = if (gray < threshold) 0 else 255
            pixels[i] = Color.argb(255, binary, binary, binary)
        }
        
        binaryBitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return binaryBitmap
    }
    
    /**
     * 计算水平投影（每一行的文本像素数量）
     */
    private fun calculateHorizontalProjection(bitmap: Bitmap): IntArray {
        val projection = IntArray(bitmap.height)
        
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        for (y in 0 until bitmap.height) {
            var textPixelCount = 0
            for (x in 0 until bitmap.width) {
                val index = y * bitmap.width + x
                val pixel = pixels[index]
                val gray = Color.red(pixel)
                // 黑色像素（文本）计数
                if (gray < 128) {
                    textPixelCount++
                }
            }
            projection[y] = textPixelCount
        }
        
        return projection
    }
    
    /**
     * 根据水平投影识别文本区域
     * 改进：使用更智能的阈值和区域识别策略
     */
    private fun findTextRegions(
        horizontalProjection: IntArray,
        imageWidth: Int,
        imageHeight: Int
    ): List<Rect> {
        val regions = mutableListOf<Rect>()
        
        // 计算投影的平均值和标准差，用于动态调整阈值
        var sum = 0L
        var maxDensity = 0
        for (density in horizontalProjection) {
            sum += density
            if (density > maxDensity) {
                maxDensity = density
            }
        }
        val avgDensity = sum / horizontalProjection.size
        val threshold = maxOf(
            (imageWidth * TEXT_DENSITY_THRESHOLD).toInt(),
            (avgDensity * 0.3).toInt()  // 使用平均密度的30%作为阈值
        )
        
        Log.d(TAG, "投影分析: 平均密度=$avgDensity, 最大密度=$maxDensity, 阈值=$threshold")
        
        var inTextRegion = false
        var regionStart = 0
        var consecutiveEmptyLines = 0
        
        for (y in horizontalProjection.indices) {
            val density = horizontalProjection[y]
            val isTextLine = density > threshold
            
            if (isTextLine && !inTextRegion) {
                // 进入文本区域
                regionStart = y
                inTextRegion = true
                consecutiveEmptyLines = 0
            } else if (!isTextLine && inTextRegion) {
                // 空白行，累计计数
                consecutiveEmptyLines++
                
                // 如果连续空白行太多（> 20行），认为离开了文本区域
                if (consecutiveEmptyLines > 20) {
                    val regionEnd = y - consecutiveEmptyLines
                    val height = regionEnd - regionStart
                    
                    if (height >= MIN_QUESTION_HEIGHT / 2) {
                        regions.add(Rect(0, regionStart, imageWidth, regionEnd))
                        Log.d(TAG, "检测到文本区域: top=$regionStart, bottom=$regionEnd, height=$height")
                    }
                    
                    inTextRegion = false
                    consecutiveEmptyLines = 0
                }
            } else if (isTextLine && inTextRegion) {
                // 在文本区域内，重置空白行计数
                consecutiveEmptyLines = 0
            }
        }
        
        // 处理最后一个区域
        if (inTextRegion) {
            val height = imageHeight - regionStart
            if (height >= MIN_QUESTION_HEIGHT / 2) {
                regions.add(Rect(0, regionStart, imageWidth, imageHeight))
                Log.d(TAG, "检测到最后一个文本区域: top=$regionStart, bottom=$imageHeight, height=$height")
            }
        }
        
        Log.d(TAG, "识别到 ${regions.size} 个文本区域")
        return regions
    }
    
    /**
     * 合并相邻的文本区域，形成题目块
     * 改进：更智能的合并策略，避免过度合并
     */
    private fun mergeTextRegionsToQuestions(
        textRegions: List<Rect>,
        imageWidth: Int
    ): List<Rect> {
        if (textRegions.isEmpty()) {
            return emptyList()
        }
        
        val questions = mutableListOf<Rect>()
        var currentQuestion: Rect? = null
        
        for (region in textRegions) {
            if (currentQuestion == null) {
                // 第一个区域
                currentQuestion = region
            } else {
                // 检查与当前题目的间距
                val gap = region.top - currentQuestion.bottom
                
                // 计算当前题目的高度，用于判断是否应该合并
                val currentHeight = currentQuestion.height()
                val regionHeight = region.height()
                
                // 如果间距很小（< MIN_QUESTION_GAP），且当前题目高度不太大，才合并
                // 这样可以避免把多个题目合并成一个
                if (gap <= MIN_QUESTION_GAP && currentHeight < imageWidth * 0.3f) {
                    // 间距小且题目不太大，合并到当前题目
                    currentQuestion = Rect(
                        currentQuestion.left,
                        currentQuestion.top,
                        currentQuestion.right,
                        region.bottom
                    )
                } else {
                    // 间距大或题目已经很大，开始新题目
                    if (currentQuestion.height() >= MIN_QUESTION_HEIGHT) {
                        questions.add(currentQuestion)
                    }
                    currentQuestion = region
                }
            }
        }
        
        // 添加最后一个题目
        if (currentQuestion != null && currentQuestion.height() >= MIN_QUESTION_HEIGHT) {
            questions.add(currentQuestion)
        }
        
        Log.d(TAG, "合并前文本区域数: ${textRegions.size}, 合并后题目数: ${questions.size}")
        return questions
    }
    
    /**
     * 结合OCR结果和图像分析结果，提高检测准确率
     * @param ocrRegions OCR检测到的题目区域
     * @param imageRegions 图像分析检测到的题目区域
     * @param imageWidth 图片宽度
     * @param imageHeight 图片高度
     * @return 融合后的题目区域列表
     */
    fun mergeOcrAndImageRegions(
        ocrRegions: List<Rect>,
        imageRegions: List<Rect>,
        imageWidth: Int,
        imageHeight: Int
    ): List<Rect> {
        if (ocrRegions.isEmpty() && imageRegions.isEmpty()) {
            return emptyList()
        }
        
        if (ocrRegions.isEmpty()) {
            return imageRegions
        }
        
        if (imageRegions.isEmpty()) {
            return ocrRegions
        }
        
        // 使用OCR结果作为主要依据，图像分析结果作为辅助
        // 对于OCR检测到的区域，检查图像分析是否也检测到了类似区域
        val mergedRegions = mutableListOf<Rect>()
        
        for (ocrRegion in ocrRegions) {
            // 查找图像分析中与OCR区域重叠的区域
            val overlappingRegions = imageRegions.filter { imageRegion ->
                val overlapRatio = calculateOverlapRatio(ocrRegion, imageRegion)
                overlapRatio > 0.3f  // 重叠度超过30%
            }
            
            if (overlappingRegions.isNotEmpty()) {
                // 找到重叠区域，使用OCR区域（更精确）
                mergedRegions.add(ocrRegion)
            } else {
                // 没有重叠，但OCR检测到了，仍然使用OCR结果
                mergedRegions.add(ocrRegion)
            }
        }
        
        // 检查图像分析检测到但OCR未检测到的区域
        for (imageRegion in imageRegions) {
            val hasOverlap = ocrRegions.any { ocrRegion ->
                calculateOverlapRatio(ocrRegion, imageRegion) > 0.3f
            }
            
            if (!hasOverlap && imageRegion.height() >= MIN_QUESTION_HEIGHT) {
                // OCR未检测到，但图像分析检测到了，可能是题目（但OCR识别失败）
                // 可以选择添加或忽略
                Log.d(TAG, "图像分析检测到但OCR未检测到的区域: $imageRegion")
            }
        }
        
        return mergedRegions
    }
    
    /**
     * 计算两个区域的重叠度（0.0 ~ 1.0）
     */
    private fun calculateOverlapRatio(rect1: Rect, rect2: Rect): Float {
        val left = max(rect1.left, rect2.left)
        val top = max(rect1.top, rect2.top)
        val right = min(rect1.right, rect2.right)
        val bottom = min(rect1.bottom, rect2.bottom)
        
        if (left >= right || top >= bottom) {
            return 0f
        }
        
        val overlapArea = (right - left) * (bottom - top)
        val rect1Area = rect1.width() * rect1.height()
        val rect2Area = rect2.width() * rect2.height()
        val unionArea = rect1Area + rect2Area - overlapArea
        
        return if (unionArea > 0) {
            overlapArea.toFloat() / unionArea
        } else {
            0f
        }
    }
}

