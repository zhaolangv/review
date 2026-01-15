package com.gongkao.cuotifupan.detector

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 图形推理题检测器
 * 
 * 用于检测图片是否为图形推理题（如Raven's Progressive Matrices）
 * 这类题目主要是图形模式，没有文字，OCR无法识别
 */
class GraphicQuestionDetector {
    
    companion object {
        private const val TAG = "GraphicQuestionDetector"
        
        // 检测阈值（降低阈值，提高检测敏感度）
        private const val GRID_DETECTION_THRESHOLD = 0.2  // 网格检测阈值（降低）
        private const val PATTERN_REGULARITY_THRESHOLD = 0.4  // 图案规律性阈值（降低）
        private const val COLOR_CONTRAST_THRESHOLD = 0.3  // 颜色对比度阈值（降低）
        private const val FINAL_SCORE_THRESHOLD = 0.4f  // 最终评分阈值（降低）
    }
    
    /**
     * 检测结果
     */
    data class GraphicQuestionResult(
        val isGraphicQuestion: Boolean,
        val confidence: Float,
        val hasGrid: Boolean = false,
        val hasPattern: Boolean = false,
        val hasHighContrast: Boolean = false,
        val hasOptionMarkers: Boolean = false,  // 是否检测到选项标记（A/B/C/D）
        val hasQuestionMark: Boolean = false,  // 是否检测到问号标记
        val reason: String = ""
    )
    
    /**
     * 检测是否为图形推理题
     */
    fun detect(bitmap: Bitmap): GraphicQuestionResult {
        try {
            // 1. 检测网格布局
            val hasGrid = detectGridLayout(bitmap)
            
            // 2. 检测图案规律性
            val hasPattern = detectPatternRegularity(bitmap)
            
            // 3. 检测颜色对比度（图形题通常是黑白对比）
            val hasHighContrast = detectHighContrast(bitmap)
            
            // 4. 检测选项标记（A/B/C/D）- 图形推理题通常在底部有选项标记
            val hasOptionMarkers = detectOptionMarkers(bitmap)
            
            // 5. 检测问号标记（图形推理题可能有问号标记缺失部分）
            val hasQuestionMark = detectQuestionMark(bitmap)
            
            // 6. 综合判断
            var score = 0f
            var reasons = mutableListOf<String>()
            
            if (hasGrid) {
                score += 0.3f
                reasons.add("检测到网格布局")
            }
            
            if (hasPattern) {
                score += 0.25f
                reasons.add("检测到规律图案")
            }
            
            if (hasHighContrast) {
                score += 0.2f
                reasons.add("检测到高对比度（黑白）")
            }
            
            // 选项标记是强信号，加分更多
            if (hasOptionMarkers) {
                score += 0.25f
                reasons.add("检测到选项标记（A/B/C/D）")
            }
            
            // 问号标记也是强信号
            if (hasQuestionMark) {
                score += 0.2f
                reasons.add("检测到问号标记")
            }
            
            // 判断逻辑：
            // 1. 如果有网格+高对比度，且分数足够高，也认为是图形题（即使没有检测到选项标记等）
            // 2. 或者有网格+（选项标记或问号标记或图案）
            val hasStrongSignals = hasOptionMarkers || hasQuestionMark || hasPattern
            val hasGridAndContrast = hasGrid && hasHighContrast
            // 如果网格+高对比度且分数>=0.5，认为是图形题（Raven's Progressive Matrices类型）
            // 或者有网格+强信号（选项标记/问号标记/图案）
            val isGraphicQuestion = score >= FINAL_SCORE_THRESHOLD && hasGrid && 
                                   (hasStrongSignals || (hasGridAndContrast && score >= 0.5f))
            
            val reason = if (reasons.isNotEmpty()) reasons.joinToString("; ") else "未检测到图形题特征"
            
            Log.d(TAG, "图形题检测结果: isGraphicQuestion=$isGraphicQuestion, score=$score, reason=$reason")
            Log.d(TAG, "   详细: hasGrid=$hasGrid, hasPattern=$hasPattern, hasHighContrast=$hasHighContrast")
            Log.d(TAG, "   详细: hasOptionMarkers=$hasOptionMarkers, hasQuestionMark=$hasQuestionMark, hasStrongSignals=$hasStrongSignals")
            
            return GraphicQuestionResult(
                isGraphicQuestion = isGraphicQuestion,
                confidence = score,
                hasGrid = hasGrid,
                hasPattern = hasPattern,
                hasHighContrast = hasHighContrast,
                hasOptionMarkers = hasOptionMarkers,
                hasQuestionMark = hasQuestionMark,
                reason = reason
            )
        } catch (e: Exception) {
            Log.e(TAG, "图形题检测失败", e)
            return GraphicQuestionResult(
                isGraphicQuestion = false,
                confidence = 0f,
                reason = "检测异常: ${e.message}"
            )
        }
    }
    
    /**
     * 检测网格布局
     * 通过检测水平和垂直方向的规律性边缘来判断是否有网格
     */
    private fun detectGridLayout(bitmap: Bitmap): Boolean {
        try {
            val width = bitmap.width
            val height = bitmap.height
            
            // 采样检测（提高性能）
            val sampleStep = maxOf(1, minOf(width, height) / 50)
            
            // 检测水平方向的规律性
            val horizontalRegularity = detectDirectionRegularity(bitmap, true, sampleStep)
            
            // 检测垂直方向的规律性
            val verticalRegularity = detectDirectionRegularity(bitmap, false, sampleStep)
            
            // 如果水平和垂直方向都有一定的规律性，认为有网格
            val hasGrid = horizontalRegularity > GRID_DETECTION_THRESHOLD && 
                         verticalRegularity > GRID_DETECTION_THRESHOLD
            
            Log.d(TAG, "网格检测: horizontal=$horizontalRegularity, vertical=$verticalRegularity, hasGrid=$hasGrid")
            
            return hasGrid
        } catch (e: Exception) {
            Log.e(TAG, "网格检测失败", e)
            return false
        }
    }
    
    /**
     * 检测某个方向的规律性
     */
    private fun detectDirectionRegularity(bitmap: Bitmap, isHorizontal: Boolean, step: Int): Float {
        val width = bitmap.width
        val height = bitmap.height
        
        // 采样点
        val samplePoints = if (isHorizontal) {
            (0 until height step step).toList()
        } else {
            (0 until width step step).toList()
        }
        
        if (samplePoints.size < 3) return 0f
        
        // 计算每个采样点的边缘强度
        val edgeStrengths = samplePoints.map { pos ->
            calculateEdgeStrength(bitmap, pos, isHorizontal)
        }
        
        // 计算规律性（通过检测边缘强度的周期性）
        val regularity = calculateRegularity(edgeStrengths)
        
        return regularity
    }
    
    /**
     * 计算边缘强度
     */
    private fun calculateEdgeStrength(bitmap: Bitmap, position: Int, isHorizontal: Boolean): Float {
        val width = bitmap.width
        val height = bitmap.height
        
        var totalDiff = 0f
        var count = 0
        
        if (isHorizontal) {
            // 水平方向：检测垂直边缘
            val y = position
            if (y >= height) return 0f
            
            for (x in 1 until width) {
                val pixel1 = bitmap.getPixel(x - 1, y)
                val pixel2 = bitmap.getPixel(x, y)
                val diff = abs(getBrightness(pixel1) - getBrightness(pixel2))
                totalDiff += diff
                count++
            }
        } else {
            // 垂直方向：检测水平边缘
            val x = position
            if (x >= width) return 0f
            
            for (y in 1 until height) {
                val pixel1 = bitmap.getPixel(x, y - 1)
                val pixel2 = bitmap.getPixel(x, y)
                val diff = abs(getBrightness(pixel1) - getBrightness(pixel2))
                totalDiff += diff
                count++
            }
        }
        
        return if (count > 0) totalDiff / count else 0f
    }
    
    /**
     * 计算规律性（通过检测周期性）
     */
    private fun calculateRegularity(values: List<Float>): Float {
        if (values.size < 3) return 0f
        
        // 计算平均值
        val avg = values.average().toFloat()
        
        // 计算标准差
        val variance = values.map { (it - avg) * (it - avg) }.average()
        val stdDev = sqrt(variance.toDouble()).toFloat()
        
        // 如果标准差较小，说明规律性较强
        // 归一化到0-1
        val regularity = if (avg > 0) {
            maxOf(0f, 1f - (stdDev / avg))
        } else {
            0f
        }
        
        return regularity
    }
    
    /**
     * 检测图案规律性
     * 通过检测图像中是否有重复的图案
     */
    private fun detectPatternRegularity(bitmap: Bitmap): Boolean {
        try {
            val width = bitmap.width
            val height = bitmap.height
            
            // 将图像分成多个区域，检测区域之间的相似性
            val gridSize = 4  // 4x4网格
            val regionWidth = width / gridSize
            val regionHeight = height / gridSize
            
            if (regionWidth < 10 || regionHeight < 10) return false
            
            // 计算每个区域的平均亮度
            val regionBrightness = Array(gridSize) { Array(gridSize) { 0f } }
            
            for (i in 0 until gridSize) {
                for (j in 0 until gridSize) {
                    var totalBrightness = 0f
                    var count = 0
                    
                    val startX = j * regionWidth
                    val endX = minOf((j + 1) * regionWidth, width)
                    val startY = i * regionHeight
                    val endY = minOf((i + 1) * regionHeight, height)
                    
                    for (x in startX until endX) {
                        for (y in startY until endY) {
                            val pixel = bitmap.getPixel(x, y)
                            totalBrightness += getBrightness(pixel)
                            count++
                        }
                    }
                    
                    regionBrightness[i][j] = if (count > 0) totalBrightness / count else 0f
                }
            }
            
            // 检测是否有规律性（如棋盘格、条纹等）
            val regularity = calculateGridRegularity(regionBrightness, gridSize)
            
            val hasPattern = regularity > PATTERN_REGULARITY_THRESHOLD
            Log.d(TAG, "图案规律性检测: regularity=$regularity, hasPattern=$hasPattern")
            
            return hasPattern
        } catch (e: Exception) {
            Log.e(TAG, "图案规律性检测失败", e)
            return false
        }
    }
    
    /**
     * 计算网格规律性
     */
    private fun calculateGridRegularity(brightness: Array<Array<Float>>, size: Int): Float {
        // 检测棋盘格模式
        var checkerboardScore = 0f
        var checkerboardCount = 0
        
        for (i in 0 until size) {
            for (j in 0 until size) {
                val current = brightness[i][j]
                // 检查相邻区域是否相反（棋盘格特征）
                if (i > 0) {
                    val diff = abs(current - brightness[i - 1][j])
                    checkerboardScore += diff
                    checkerboardCount++
                }
                if (j > 0) {
                    val diff = abs(current - brightness[i][j - 1])
                    checkerboardScore += diff
                    checkerboardCount++
                }
            }
        }
        
        val avgDiff = if (checkerboardCount > 0) checkerboardScore / checkerboardCount else 0f
        // 归一化（假设最大对比度为1.0）
        return minOf(1f, avgDiff)
    }
    
    /**
     * 检测高对比度（黑白对比）
     */
    private fun detectHighContrast(bitmap: Bitmap): Boolean {
        try {
            val width = bitmap.width
            val height = bitmap.height
            
            // 采样检测
            val sampleCount = minOf(1000, width * height / 100)
            val step = maxOf(1, (width * height) / sampleCount)
            
            var totalContrast = 0f
            var count = 0
            
            for (i in 0 until sampleCount) {
                val index = i * step
                val x = index % width
                val y = index / width
                
                if (x < width && y < height) {
                    val pixel = bitmap.getPixel(x, y)
                    val brightness = getBrightness(pixel)
                    
                    // 检查是否为极端值（接近0或1）
                    val isExtreme = brightness < 0.2f || brightness > 0.8f
                    if (isExtreme) {
                        totalContrast += 1f
                    }
                    count++
                }
            }
            
            val contrastRatio = if (count > 0) totalContrast / count else 0f
            val hasHighContrast = contrastRatio > COLOR_CONTRAST_THRESHOLD
            
            Log.d(TAG, "对比度检测: contrastRatio=$contrastRatio, hasHighContrast=$hasHighContrast")
            
            return hasHighContrast
        } catch (e: Exception) {
            Log.e(TAG, "对比度检测失败", e)
            return false
        }
    }
    
    /**
     * 获取像素亮度（0-1）
     */
    private fun getBrightness(pixel: Int): Float {
        val r = Color.red(pixel) / 255f
        val g = Color.green(pixel) / 255f
        val b = Color.blue(pixel) / 255f
        // 使用标准亮度公式
        return 0.299f * r + 0.587f * g + 0.114f * b
    }
    
    /**
     * 检测选项标记（A/B/C/D）
     * 图形推理题通常在图片底部有选项标记
     * 简化版：检测底部区域是否有字母形状特征
     */
    private fun detectOptionMarkers(bitmap: Bitmap): Boolean {
        try {
            val width = bitmap.width
            val height = bitmap.height
            
            // 检测图片底部1/3区域（选项通常在这里）
            val bottomStartY = height * 2 / 3
            val bottomEndY = height
            
            // 采样检测（提高性能）
            val sampleStep = maxOf(10, width / 50)
            val yStep = maxOf(10, (bottomEndY - bottomStartY) / 30)
            
            // 检测是否有类似字母的形状（通过检测边缘密度）
            var letterLikeCount = 0
            var totalSamples = 0
            
            for (y in bottomStartY until bottomEndY step yStep) {
                for (x in 0 until width step sampleStep) {
                    totalSamples++
                    // 检测局部区域是否有字母形状特征（高边缘密度）
                    if (detectLetterLikeShape(bitmap, x, y, width, height)) {
                        letterLikeCount++
                    }
                }
            }
            
            // 如果检测到多个字母形状，认为可能有选项标记
            val letterRatio = if (totalSamples > 0) letterLikeCount.toFloat() / totalSamples else 0f
            val hasOptionMarkers = letterRatio > 0.05f  // 5%以上的区域有字母特征
            
            Log.d(TAG, "选项标记检测: letterLikeCount=$letterLikeCount, totalSamples=$totalSamples, ratio=$letterRatio, hasOptionMarkers=$hasOptionMarkers")
            
            return hasOptionMarkers
        } catch (e: Exception) {
            Log.e(TAG, "选项标记检测失败", e)
            return false
        }
    }
    
    /**
     * 检测局部区域是否有字母形状特征
     * 简化版：检测是否有高对比度的边缘（字母通常有清晰的边缘）
     */
    private fun detectLetterLikeShape(bitmap: Bitmap, centerX: Int, centerY: Int, width: Int, height: Int): Boolean {
        val regionSize = 40  // 检测区域大小
        val startX = maxOf(0, centerX - regionSize / 2)
        val endX = minOf(width, centerX + regionSize / 2)
        val startY = maxOf(0, centerY - regionSize / 2)
        val endY = minOf(height, centerY + regionSize / 2)
        
        if (endX <= startX || endY <= startY) return false
        
        // 检测区域内是否有高对比度的边缘
        var edgeCount = 0
        var totalPixels = 0
        
        for (y in startY until endY - 1) {
            for (x in startX until endX - 1) {
                if (x < width - 1 && y < height - 1) {
                    val pixel1 = bitmap.getPixel(x, y)
                    val pixel2 = bitmap.getPixel(x + 1, y)
                    val pixel3 = bitmap.getPixel(x, y + 1)
                    
                    val brightness1 = getBrightness(pixel1)
                    val brightness2 = getBrightness(pixel2)
                    val brightness3 = getBrightness(pixel3)
                    
                    // 检测边缘（亮度差异大）
                    if (abs(brightness1 - brightness2) > 0.4 || abs(brightness1 - brightness3) > 0.4) {
                        edgeCount++
                    }
                    totalPixels++
                }
            }
        }
        
        // 如果边缘比例高，可能是字母形状
        val edgeRatio = if (totalPixels > 0) edgeCount.toFloat() / totalPixels else 0f
        return edgeRatio > 0.12f  // 12%以上的边缘比例
    }
    
    /**
     * 检测问号标记
     * 图形推理题可能有问号标记缺失部分
     * 简化版：检测中间区域是否有圆形+曲线形状
     */
    private fun detectQuestionMark(bitmap: Bitmap): Boolean {
        try {
            val width = bitmap.width
            val height = bitmap.height
            
            // 检测图片中间区域（问号通常在缺失部分）
            val centerX = width / 2
            val centerY = height / 2
            val regionSize = minOf(width, height) / 3
            
            val startX = maxOf(0, centerX - regionSize / 2)
            val endX = minOf(width, centerX + regionSize / 2)
            val startY = maxOf(0, centerY - regionSize / 2)
            val endY = minOf(height, centerY + regionSize / 2)
            
            // 采样检测圆形和曲线特征
            var circularCount = 0
            var curveCount = 0
            var totalSamples = 0
            
            val step = maxOf(5, regionSize / 20)
            
            for (y in startY until endY step step) {
                for (x in startX until endX step step) {
                    totalSamples++
                    if (detectCircularShape(bitmap, x, y, width, height)) {
                        circularCount++
                    }
                    if (detectCurveShape(bitmap, x, y, width, height)) {
                        curveCount++
                    }
                }
            }
            
            // 如果同时检测到圆形和曲线，可能是问号
            val circularRatio = if (totalSamples > 0) circularCount.toFloat() / totalSamples else 0f
            val curveRatio = if (totalSamples > 0) curveCount.toFloat() / totalSamples else 0f
            val hasQuestionMark = circularRatio > 0.03f && curveRatio > 0.03f
            
            Log.d(TAG, "问号标记检测: circularCount=$circularCount, curveCount=$curveCount, totalSamples=$totalSamples")
            Log.d(TAG, "   比例: circularRatio=$circularRatio, curveRatio=$curveRatio, hasQuestionMark=$hasQuestionMark")
            
            return hasQuestionMark
        } catch (e: Exception) {
            Log.e(TAG, "问号标记检测失败", e)
            return false
        }
    }
    
    /**
     * 检测圆形形状特征
     */
    private fun detectCircularShape(bitmap: Bitmap, centerX: Int, centerY: Int, width: Int, height: Int): Boolean {
        val radius = 15
        val startX = maxOf(0, centerX - radius)
        val endX = minOf(width, centerX + radius)
        val startY = maxOf(0, centerY - radius)
        val endY = minOf(height, centerY + radius)
        
        // 检测圆形边缘（亮度变化）
        var edgeCount = 0
        var totalCount = 0
        
        for (y in startY until endY) {
            for (x in startX until endX) {
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                
                // 检测是否在圆形边缘附近
                val radiusFloat = radius.toFloat()
                if (distance in (radiusFloat - 3f)..(radiusFloat + 3f)) {
                    if (x < width - 1 && y < height - 1) {
                        val pixel1 = bitmap.getPixel(x, y)
                        val pixel2 = bitmap.getPixel(x + 1, y)
                        val brightness1 = getBrightness(pixel1)
                        val brightness2 = getBrightness(pixel2)
                        
                        if (abs(brightness1 - brightness2) > 0.3) {
                            edgeCount++
                        }
                        totalCount++
                    }
                }
            }
        }
        
        return totalCount > 10 && edgeCount.toFloat() / totalCount > 0.25f
    }
    
    /**
     * 检测曲线形状特征
     */
    private fun detectCurveShape(bitmap: Bitmap, centerX: Int, centerY: Int, width: Int, height: Int): Boolean {
        val regionSize = 20
        val startX = maxOf(0, centerX - regionSize / 2)
        val endX = minOf(width - 1, centerX + regionSize / 2)
        val startY = maxOf(0, centerY - regionSize / 2)
        val endY = minOf(height - 1, centerY + regionSize / 2)
        
        if (endX <= startX || endY <= startY) return false
        
        var curveCount = 0
        var totalCount = 0
        
        for (y in startY until endY) {
            for (x in startX until endX) {
                if (x < width - 1 && y < height - 1) {
                    val pixel1 = bitmap.getPixel(x, y)
                    val pixel2 = bitmap.getPixel(x + 1, y)
                    val pixel3 = bitmap.getPixel(x, y + 1)
                    val pixel4 = bitmap.getPixel(x + 1, y + 1)
                    
                    val b1 = getBrightness(pixel1)
                    val b2 = getBrightness(pixel2)
                    val b3 = getBrightness(pixel3)
                    val b4 = getBrightness(pixel4)
                    
                    // 检测是否有弯曲的边缘模式（对角线差异大）
                    val diag1 = abs(b1 - b4)
                    val diag2 = abs(b2 - b3)
                    val diff1 = abs(b1 - b2)
                    val diff2 = abs(b1 - b3)
                    
                    // 如果对角线差异大，可能是曲线
                    if (diag1 > 0.3 || diag2 > 0.3) {
                        curveCount++
                    }
                    totalCount++
                }
            }
        }
        
        return totalCount > 10 && curveCount.toFloat() / totalCount > 0.15f
    }
}

