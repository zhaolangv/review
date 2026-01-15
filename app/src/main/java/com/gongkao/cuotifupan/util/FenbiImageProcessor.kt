package com.gongkao.cuotifupan.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 粉笔截图处理器 - 完整版v4
 * 
 * 核心改进：检测所有圆圈（包括灰色的未选中圆圈）来精确定位选项位置
 * 这样不管图片大小、选项间距如何变化，都能准确处理
 */
class ImageProcessor {

    companion object {
        private const val TAG = "FenbiImageProcessor"
    }

    data class Circle(
        val cx: Int,
        val cy: Int,
        val radius: Int,
        var letter: String = "?",
        var isColored: Boolean = false  // 是否是彩色圆圈
    )

    fun process(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        Log.d(TAG, "开始处理图片: ${width}x${height}")
        
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // 1. 检测彩色圆圈（正确/错误答案）
        val coloredCircles = detectColoredCircles(pixels, width, height)
        Log.d(TAG, "检测到 ${coloredCircles.size} 个彩色圆圈")
        
        // 2. 检测灰色圆圈（未选中选项）
        // 只有当检测到彩色圆圈时，才尝试检测灰色圆圈
        val grayCircles = if (coloredCircles.isNotEmpty()) {
            detectGrayCircles(pixels, width, height, coloredCircles)
        } else {
            emptyList()
        }
        Log.d(TAG, "检测到 ${grayCircles.size} 个灰色圆圈")
        
        if (coloredCircles.isEmpty() && grayCircles.isEmpty()) {
            // 如果没有检测到任何圆圈，只清除所有红色和绿色文字，然后返回
            clearAllColoredText(pixels, width, height)
            clearRedX(pixels, width, height)
            result.setPixels(pixels, 0, width, 0, 0, width, height)
            return result
        }
        
        // 3. 合并所有圆圈，找出所有选项位置
        val allDetectedCircles = (coloredCircles + grayCircles).sortedBy { it.cy }
        
        // 4. 推断ABCD位置
        val allCircles = if (allDetectedCircles.size >= 4) {
            // 如果检测到4个或更多圆圈，取垂直排列的前4个
            assignLettersToCircles(allDetectedCircles.take(4), width, height)
        } else if (allDetectedCircles.size >= 2) {
            // 检测到2-3个圆圈，根据实际间距推断
            inferFromMultipleCircles(allDetectedCircles, width, height)
        } else {
            // 只检测到1个圆圈，使用位置百分比推断
            inferFromSingleCircle(allDetectedCircles.first(), width, height)
        }
        
        // 5. 替换圆圈
        for (circle in allCircles) {
            Log.d(TAG, "处理: ${circle.letter} at Y=${circle.cy}")
            replaceCircle(pixels, width, height, circle)
        }
        
        clearRedX(pixels, width, height)
        
        // 清除所有红色和绿色文字（包括底部的"正确答案"、"你的答案"等）
        clearAllColoredText(pixels, width, height)
        
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        
        // 6. 智能裁剪
        val lastCircle = allCircles.maxByOrNull { it.cy }
        return if (lastCircle != null) {
            val lastCirclePercent = lastCircle.cy.toFloat() / height * 100
            
            if (lastCirclePercent < 60f) {
                val optionHeight = lastCircle.radius * 3
                val cropY = (lastCircle.cy + optionHeight * 2).coerceAtMost(bitmap.height)
                Log.d(TAG, "裁剪: D选项Y=${lastCircle.cy}(${String.format("%.1f", lastCirclePercent)}%), 裁剪点Y=$cropY")
                Bitmap.createBitmap(result, 0, 0, result.width, cropY)
            } else {
                val cropY = (lastCircle.cy + lastCircle.radius * 5).coerceAtMost(bitmap.height)
                Log.d(TAG, "D选项位置较低(${String.format("%.1f", lastCirclePercent)}%), 保留底部: 裁剪点Y=$cropY")
                if (cropY >= bitmap.height) result else Bitmap.createBitmap(result, 0, 0, result.width, cropY)
            }
        } else {
            result
        }
    }

    /**
     * 检测灰色圆圈（未选中的选项）
     */
    private fun detectGrayCircles(pixels: IntArray, width: Int, height: Int, coloredCircles: List<Circle>): List<Circle> {
        val circles = mutableListOf<Circle>()
        val visited = BooleanArray(width * height)
        
        // 标记彩色圆圈区域为已访问
        for (colored in coloredCircles) {
            val r = colored.radius + 20
            for (dy in -r..r) {
                for (dx in -r..r) {
                    val px = colored.cx + dx
                    val py = colored.cy + dy
                    if (px in 0 until width && py in 0 until height) {
                        visited[py * width + px] = true
                    }
                }
            }
        }
        
        // 搜索区域：左侧1/4，上15%到下75%
        val searchRight = width / 4
        val searchTop = (height * 0.15).toInt()
        val searchBottom = (height * 0.75).toInt()
        
        // 灰色圆圈的X坐标应该和彩色圆圈接近
        val expectedX = if (coloredCircles.isNotEmpty()) coloredCircles.map { it.cx }.average().toInt() else width / 8
        val xTolerance = width / 10
        
        for (y in searchTop until searchBottom) {
            for (x in 0 until searchRight) {
                val idx = y * width + x
                if (visited[idx]) continue
                
                val color = pixels[idx]
                if (isGrayCircleColor(color)) {
                    val region = floodFillGray(pixels, width, height, x, y, visited)
                    
                    if (region.size in 300..100000) {
                        val circle = fitCircle(region)
                        if (circle != null && circle.radius in 15..100) {
                            // 验证X坐标是否与彩色圆圈对齐
                            if (abs(circle.cx - expectedX) < xTolerance) {
                                // 验证不与已检测的圆圈太近
                                val tooClose = circles.any { abs(it.cy - circle.cy) < circle.radius * 2 }
                                if (!tooClose) {
                                    circles.add(circle)
                                    Log.d(TAG, "找到灰色圆圈: (${circle.cx}, ${circle.cy}), r=${circle.radius}")
                                }
                            }
                        }
                    }
                }
            }
        }
        
        return circles
    }

    /**
     * 检测是否是灰色圆圈的颜色
     * 灰色圆圈：饱和度很低，亮度在中等偏高范围
     */
    private fun isGrayCircleColor(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        val s = hsv[1]
        val v = hsv[2]
        
        // 灰色圆圈边框：饱和度<0.15，亮度在0.7-0.95之间
        // 排除纯白色背景
        val isGray = s < 0.15f && v in 0.7f..0.95f
        
        // 额外检查RGB值接近（灰色特征）
        val rgbSimilar = abs(r - g) < 30 && abs(g - b) < 30 && abs(r - b) < 30
        
        return isGray && rgbSimilar && r > 150 && r < 250
    }

    /**
     * 为检测到的圆圈分配字母
     */
    private fun assignLettersToCircles(circles: List<Circle>, width: Int, height: Int): List<Circle> {
        val sorted = circles.sortedBy { it.cy }
        val letters = listOf("A", "B", "C", "D")
        val result = mutableListOf<Circle>()
        
        // 计算平均间距
        if (sorted.size >= 2) {
            val spacing = (sorted.last().cy - sorted.first().cy) / (sorted.size - 1)
            Log.d(TAG, "检测到${sorted.size}个圆圈，平均间距=$spacing")
        }
        
        // 如果恰好4个圆圈，直接分配ABCD
        if (sorted.size == 4) {
            for ((i, circle) in sorted.withIndex()) {
                result.add(Circle(circle.cx, circle.cy, circle.radius, letters[i], circle.isColored))
            }
            return result
        }
        
        // 如果超过4个，取间距最均匀的4个
        val standardX = sorted.map { it.cx }.average().toInt()
        val standardRadius = sorted.map { it.radius }.average().toInt()
        
        // 计算相邻间距
        val spacings = mutableListOf<Int>()
        for (i in 0 until sorted.size - 1) {
            spacings.add(sorted[i + 1].cy - sorted[i].cy)
        }
        val avgSpacing = if (spacings.isNotEmpty()) spacings.average().toInt() else (height * 0.08).toInt()
        
        // 取前4个圆圈
        for (i in 0 until minOf(4, sorted.size)) {
            result.add(Circle(standardX, sorted[i].cy, standardRadius, letters[i], sorted[i].isColored))
        }
        
        // 如果不足4个，推断剩余的
        if (result.size < 4) {
            val lastCy = result.last().cy
            for (i in result.size until 4) {
                result.add(Circle(standardX, lastCy + (i - result.size + 1) * avgSpacing, standardRadius, letters[i], false))
            }
        }
        
        return result
    }

    /**
     * 从多个圆圈推断所有选项位置
     * 核心改进：根据每个圆圈的位置独立判断是哪个选项
     */
    private fun inferFromMultipleCircles(circles: List<Circle>, width: Int, height: Int): List<Circle> {
        val sorted = circles.sortedBy { it.cy }
        val letters = listOf("A", "B", "C", "D")
        val result = mutableListOf<Circle>()
        
        val standardX = sorted.map { it.cx }.average().toInt()
        val standardRadius = sorted.map { it.radius }.average().toInt()
        
        val first = sorted.first()
        val last = sorted.last()
        val totalDiff = last.cy - first.cy
        val firstPercent = first.cy.toFloat() / height * 100
        val lastPercent = last.cy.toFloat() / height * 100
        
        Log.d(TAG, "分析: 第1个Y%=${String.format("%.1f", firstPercent)}, 最后Y%=${String.format("%.1f", lastPercent)}")
        
        // 核心改进：优先假设第一个圆圈是A，根据间距判断最后一个是哪个
        val percentDiff = lastPercent - firstPercent
        
        // 根据间距判断隔了几个选项（两个圆圈之间）
        // 正常单选项间距约7-10%，所以：
        // - A-B/B-C/C-D: 7-10%
        // - A-C/B-D: 14-22%
        // - A-D: 22-30%
        // 如果第一个圆圈>35%，即使间距较大，也更可能是B-D而不是A-D
        val gapCount = when {
            percentDiff > 22f -> 3  // A-D（提高阈值，避免误判）
            // 如果第一个圆圈>35%且间距>20%，更可能是B-D（隔2个）
            percentDiff > 20f && firstPercent > 35f -> 2  // B-D
            percentDiff > 20f -> 3  // A-D（第一个圆圈较低）
            percentDiff > 10f -> 2  // A-C 或 B-D
            else -> 1               // 相邻选项
        }
        
        // 根据间距数和位置综合判断第一个圆圈是哪个选项
        // 核心思路：默认假设第一个彩色圆圈是B，只有位置很高时才认为是A
        // 原因：短题目A在25%左右，长题目A可能在35-40%，差异太大无法用固定阈值
        val firstIndex = when {
            gapCount == 3 -> 0  // 隔3个，一定是A-D
            gapCount == 2 -> {
                // 隔2个，可能是A-C或B-D
                // 如果<28%，一定是A-C
                // 如果间距>20%（长题目），即使35%以上也可能是A-C
                // 否则35%以上通常是B-D
                when {
                    firstPercent < 28f -> 0  // A-C
                    percentDiff > 20f -> 0  // A-C（间距很大，长题目，A位置较低）
                    firstPercent >= 35f -> 1  // B-D（35%以上通常是B）
                    else -> 1  // B-D（默认，28-35%之间也判断为B-D）
                }
            }
            else -> {
                // 相邻选项：综合考虑位置和间距
                when {
                    firstPercent > 55f -> 2  // C-D
                    // 精细判断：根据位置范围，优先处理42-43%的特殊情况
                    // 42-43%且间距<7%：C-D（病毒题目：42.8%, 49.3%）
                    firstPercent >= 42f && firstPercent < 43f && percentDiff < 7f -> 2  // C-D
                    // 43%以上且间距>6.5%：B-C（A嵌入题目的情况）
                    firstPercent >= 43f && percentDiff > 6.5f -> 1  // B-C
                    // 32-42%：B-C（短题目的B位置）
                    firstPercent >= 32f && firstPercent < 42f -> 1  // B-C
                    // 45%以上且间距<7%：C-D（位置更高）
                    firstPercent >= 45f && percentDiff < 7f -> 2  // C-D
                    // <28%：A-B
                    firstPercent < 28f -> 0  // A-B
                    // 其他情况默认B-C
                    else -> 1  // B-C
                }
            }
        }
        
        val lastIndex = (firstIndex + gapCount).coerceAtMost(3)
        
        // 计算单选项间距
        val singleSpacing = if (gapCount > 0) totalDiff / gapCount else (height * 0.08f).toInt()
        
        Log.d(TAG, "推断: ${letters[firstIndex]}-${letters.getOrElse(lastIndex) {"?"}} (隔${gapCount}个), 单选项间距=${singleSpacing}px")
        
        // 生成所有4个选项
        for (i in 0..3) {
            val cy = first.cy + (i - firstIndex) * singleSpacing
            result.add(Circle(standardX, cy, standardRadius, letters[i]))
        }
        
        return result
    }
    
    /**
     * 根据Y位置百分比估算是哪个选项
     * 完整截图中选项位置因题目长度而变化
     * 短题目：A≈27%, B≈35%, C≈43%, D≈51%
     * 长题目：A≈35%, B≈43%, C≈51%, D≈59%
     */
    private fun estimateOptionIndex(percent: Float): Int {
        // 放宽阈值，使用重叠范围来处理不同题目长度
        return when {
            percent < 35f -> 0  // A: <35%
            percent < 46f -> 1  // B: 35-46%
            percent < 55f -> 2  // C: 46-55%
            else -> 3           // D: >=55%
        }
    }

    /**
     * 从单个圆圈推断所有选项位置
     */
    private fun inferFromSingleCircle(circle: Circle, width: Int, height: Int): List<Circle> {
        val letters = listOf("A", "B", "C", "D")
        val result = mutableListOf<Circle>()
        
        val percent = circle.cy.toFloat() / height * 100
        val aspectRatio = height.toFloat() / width
        val isCroppedImage = aspectRatio < 1.5
        
        // 根据图片类型使用不同的间距
        val spacing = if (isCroppedImage) {
            (height * 0.13f).toInt()  // 增大到13%间距，避免选项太集中且整体上移
        } else {
            (height * 0.08f).toInt()
        }
        
        // 根据位置推断是哪个选项
        val index = if (isCroppedImage) {
            when {
                percent < 50f -> 0
                percent < 60f -> 1
                percent < 65f -> 2  // 65%以下才是C
                else -> 3           // 65%以上是D（65.9%判断为D，避免整体上移）
            }
        } else {
            when {
                percent < 32f -> 0
                percent < 42f -> 1
                percent < 52f -> 2
                else -> 3
            }
        }
        
        Log.d(TAG, "单个圆圈推断: Y=${circle.cy}(${String.format("%.1f", percent)}%), 高宽比=${String.format("%.2f", aspectRatio)}, 间距=$spacing, 判断为${letters[index]}")
        
        for (i in 0..3) {
            result.add(Circle(circle.cx, circle.cy + (i - index) * spacing, circle.radius, letters[i]))
        }
        
        return result
    }

    private fun detectColoredCircles(pixels: IntArray, width: Int, height: Int): MutableList<Circle> {
        val circles = mutableListOf<Circle>()
        val visited = BooleanArray(width * height)
        val searchRight = width / 4
        val searchTop = (height * 0.15).toInt()
        val searchBottom = (height * 0.75).toInt()
        
        for (y in searchTop until searchBottom) {
            for (x in 0 until searchRight) {
                val idx = y * width + x
                if (visited[idx]) continue
                if (isGreenOrRed(pixels[idx])) {
                    val region = floodFill(pixels, width, height, x, y, visited)
                    if (region.size in 500..150000) {
                        val circle = fitCircle(region)
                        if (circle != null && circle.radius in 15..100) {
                            circle.isColored = true
                            circles.add(circle)
                            Log.d(TAG, "找到彩色圆圈: (${circle.cx}, ${circle.cy}), r=${circle.radius}")
                        }
                    }
                }
            }
        }
        return circles
    }

    private fun isGreenOrRed(color: Int): Boolean {
        val hsv = FloatArray(3)
        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv)
        val h = hsv[0]; val s = hsv[1]; val v = hsv[2]
        return (h in 70f..170f && s > 0.3f && v > 0.3f) || ((h in 0f..30f || h in 330f..360f) && s > 0.3f && v > 0.3f)
    }

    private fun floodFill(pixels: IntArray, width: Int, height: Int, startX: Int, startY: Int, visited: BooleanArray): List<Pair<Int, Int>> {
        val region = mutableListOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startX, startY))
        while (queue.isNotEmpty() && region.size < 150000) {
            val (x, y) = queue.removeFirst()
            if (x < 0 || x >= width || y < 0 || y >= height) continue
            val idx = y * width + x
            if (visited[idx] || !isGreenOrRed(pixels[idx])) continue
            visited[idx] = true
            region.add(Pair(x, y))
            queue.add(Pair(x + 1, y)); queue.add(Pair(x - 1, y)); queue.add(Pair(x, y + 1)); queue.add(Pair(x, y - 1))
        }
        return region
    }

    private fun floodFillGray(pixels: IntArray, width: Int, height: Int, startX: Int, startY: Int, visited: BooleanArray): List<Pair<Int, Int>> {
        val region = mutableListOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startX, startY))
        while (queue.isNotEmpty() && region.size < 100000) {
            val (x, y) = queue.removeFirst()
            if (x < 0 || x >= width || y < 0 || y >= height) continue
            val idx = y * width + x
            if (visited[idx] || !isGrayCircleColor(pixels[idx])) continue
            visited[idx] = true
            region.add(Pair(x, y))
            queue.add(Pair(x + 1, y)); queue.add(Pair(x - 1, y)); queue.add(Pair(x, y + 1)); queue.add(Pair(x, y - 1))
        }
        return region
    }

    private fun fitCircle(region: List<Pair<Int, Int>>): Circle? {
        if (region.isEmpty()) return null
        val cx = region.map { it.first }.average().toInt()
        val cy = region.map { it.second }.average().toInt()
        val radius = region.map { sqrt(((it.first - cx) * (it.first - cx) + (it.second - cy) * (it.second - cy)).toDouble()) }.average().toInt()
        return Circle(cx, cy, radius)
    }

    private fun replaceCircle(pixels: IntArray, width: Int, height: Int, circle: Circle) {
        val cx = circle.cx; val cy = circle.cy; val radius = circle.radius
        if (cy < 0 || cy >= height) return
        val borderGray = Color.rgb(235, 235, 235)
        val letterGray = Color.rgb(165, 167, 180)
        
        val coverRadius = radius + 15
        for (dy in -coverRadius..coverRadius) {
            for (dx in -coverRadius..coverRadius) {
                val px = cx + dx; val py = cy + dy
                if (px in 0 until width && py in 0 until height && sqrt((dx * dx + dy * dy).toDouble()) <= coverRadius) {
                    pixels[py * width + px] = Color.WHITE
                }
            }
        }
        
        // 清除彩色残留
        val cleanRadius = coverRadius + 20
        for (dy in -cleanRadius..cleanRadius) {
            for (dx in -cleanRadius..cleanRadius) {
                val px = cx + dx; val py = cy + dy
                if (px in 0 until width && py in 0 until height && sqrt((dx * dx + dy * dy).toDouble()) <= cleanRadius) {
                    val hsv = FloatArray(3)
                    Color.RGBToHSV(Color.red(pixels[py * width + px]), Color.green(pixels[py * width + px]), Color.blue(pixels[py * width + px]), hsv)
                    if (hsv[1] > 0.02f) pixels[py * width + px] = Color.WHITE
                }
            }
        }
        
        // 额外加强圆圈下方的清除
        val extraCleanBottom = cleanRadius + 10
        for (dy in 0..extraCleanBottom) {
            for (dx in -extraCleanBottom..extraCleanBottom) {
                val px = cx + dx; val py = cy + dy
                if (px in 0 until width && py in 0 until height) {
                    val hsv = FloatArray(3)
                    Color.RGBToHSV(Color.red(pixels[py * width + px]), Color.green(pixels[py * width + px]), Color.blue(pixels[py * width + px]), hsv)
                    val h = hsv[0]
                    val s = hsv[1]
                    val isGreenResidue = h in 70f..170f && s > 0.01f
                    val isRedResidue = (h in 0f..30f || h in 330f..360f) && s > 0.01f
                    if (isGreenResidue || isRedResidue) {
                        pixels[py * width + px] = Color.WHITE
                    }
                }
            }
        }
        
        val circleR = (width * 0.049).toInt().coerceIn(35, 60)
        for (angle in 0 until 360) {
            val rad = Math.toRadians(angle.toDouble())
            for (t in -1..1) {
                val px = (cx + (circleR + t) * Math.cos(rad)).toInt()
                val py = (cy + (circleR + t) * Math.sin(rad)).toInt()
                if (px in 0 until width && py in 0 until height) pixels[py * width + px] = borderGray
            }
        }
        
        drawLetter(pixels, width, height, cx, cy, circle.letter, letterGray)
    }

    private fun drawLetter(pixels: IntArray, width: Int, height: Int, cx: Int, cy: Int, letter: String, color: Int) {
        val patterns = mapOf(
            "A" to arrayOf("00100", "01010", "10001", "10001", "11111", "10001", "10001"),
            "B" to arrayOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
            "C" to arrayOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
            "D" to arrayOf("11110", "10001", "10001", "10001", "10001", "10001", "11110")
        )
        val pattern = patterns[letter] ?: return
        val scale = (width / 150).coerceIn(4, 9)
        val startX = cx - (5 * scale) / 2; val startY = cy - (7 * scale) / 2
        for ((row, line) in pattern.withIndex()) {
            for ((col, ch) in line.withIndex()) {
                if (ch == '1') {
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            val px = startX + col * scale + dx; val py = startY + row * scale + dy
                            if (px in 0 until width && py in 0 until height) pixels[py * width + px] = color
                        }
                    }
                }
            }
        }
    }

    private fun clearRedX(pixels: IntArray, width: Int, height: Int) {
        for (y in 0 until (height * 0.12).toInt()) {
            for (x in (width * 0.7).toInt() until width) {
                val hsv = FloatArray(3)
                Color.RGBToHSV(Color.red(pixels[y * width + x]), Color.green(pixels[y * width + x]), Color.blue(pixels[y * width + x]), hsv)
                if ((hsv[0] in 0f..30f || hsv[0] in 330f..360f) && hsv[1] > 0.3f) pixels[y * width + x] = Color.WHITE
            }
        }
    }

    /**
     * 清除整个图片中的所有红色和绿色文字
     * 包括底部的"正确答案"、"你的答案"等
     */
    private fun clearAllColoredText(pixels: IntArray, width: Int, height: Int) {
        // 遍历整个图片，清除红色和绿色文字
        // 使用更低的阈值，确保清除所有红色和绿色
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                
                // 快速检查：如果RGB值明显偏向红色或绿色，直接清除
                // 红色：R明显大于G和B
                val isReddish = r > g + 30 && r > b + 30 && r > 100
                // 绿色：G明显大于R和B
                val isGreenish = g > r + 30 && g > b + 30 && g > 100
                
                if (isReddish || isGreenish) {
                    pixels[y * width + x] = Color.WHITE
                    continue
                }
                
                // HSV检查（更精确但较慢）
                val hsv = FloatArray(3)
                Color.RGBToHSV(r, g, b, hsv)
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]
                
                // 检测红色：色相0-30或330-360，饱和度>0.15，亮度>0.15（降低阈值）
                val isRed = (h in 0f..30f || h in 330f..360f) && s > 0.15f && v > 0.15f
                // 检测绿色：色相70-170，饱和度>0.15，亮度>0.15（降低阈值）
                val isGreen = h in 70f..170f && s > 0.15f && v > 0.15f
                
                if (isRed || isGreen) {
                    pixels[y * width + x] = Color.WHITE
                }
            }
        }
        
        Log.d(TAG, "已清除所有红色和绿色文字")
    }
}
