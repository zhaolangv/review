package com.gongkao.cuotifupan.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 粉笔截图处理器 - 完整版v3
 * 
 * 核心改进：通过验证推断的A位置是否合理来选择正确的选项组合
 */
class ImageProcessor {

    companion object {
        private const val TAG = "FenbiImageProcessor"
    }

    data class Circle(
        val cx: Int,
        val cy: Int,
        val radius: Int,
        var letter: String = "?"
    )

    fun process(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        Log.d(TAG, "开始处理图片: ${width}x${height}")
        
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val coloredCircles = detectColoredCircles(pixels, width, height)
        
        Log.d(TAG, "检测到 ${coloredCircles.size} 个彩色圆圈")
        
        if (coloredCircles.isEmpty()) {
            return result
        }
        
        val allCircles = inferAllOptionCircles(coloredCircles, width, height)
        
        for (circle in allCircles) {
            Log.d(TAG, "处理: ${circle.letter} at Y=${circle.cy}")
            replaceCircle(pixels, width, height, circle)
        }
        
        clearRedX(pixels, width, height)
        
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        
        val lastCircle = allCircles.maxByOrNull { it.cy }
        return if (lastCircle != null) {
            val cropY = (lastCircle.cy + lastCircle.radius + 100).coerceAtMost(bitmap.height)
            Log.d(TAG, "裁剪: D选项Y=${lastCircle.cy}, 裁剪点Y=$cropY")
            Bitmap.createBitmap(result, 0, 0, result.width, cropY)
        } else {
            result
        }
    }

    private fun inferAllOptionCircles(coloredCircles: List<Circle>, width: Int, height: Int): List<Circle> {
        val result = mutableListOf<Circle>()
        val letters = listOf("A", "B", "C", "D")
        
        val sorted = coloredCircles.sortedBy { it.cy }
        val standardX = sorted.map { it.cx }.average().toInt()
        val standardRadius = sorted.map { it.radius }.average().toInt()
        
        if (sorted.size == 1) {
            val circle = sorted.first()
            val percent = circle.cy.toFloat() / height * 100
            val spacing = (height * 0.08f).toInt()
            val index = when {
                percent < 32f -> 0
                percent < 42f -> 1
                percent < 52f -> 2
                else -> 3
            }
            for (i in 0..3) {
                result.add(Circle(standardX, circle.cy + (i - index) * spacing, standardRadius, letters[i]))
            }
        } else if (sorted.size >= 2) {
            val first = sorted.first()
            val last = sorted.last()
            val yDiff = last.cy - first.cy
            val percentDiff = yDiff.toFloat() / height * 100
            val firstPercent = first.cy.toFloat() / height * 100
            val lastPercent = last.cy.toFloat() / height * 100
            
            Log.d(TAG, "分析: 第1个Y%=${String.format("%.1f", firstPercent)}, 最后Y%=${String.format("%.1f", lastPercent)}, 间距=${String.format("%.1f", percentDiff)}%")
            
            val (firstIndex, lastIndex) = determineOptionPair(firstPercent, lastPercent, percentDiff)
            
            Log.d(TAG, "推断: ${letters[firstIndex]}-${letters[lastIndex]}")
            
            val actualGap = lastIndex - firstIndex
            val spacing = if (actualGap > 0) yDiff / actualGap else (height * 0.08f).toInt()
            
            for (i in 0..3) {
                result.add(Circle(standardX, first.cy + (i - firstIndex) * spacing, standardRadius, letters[i]))
            }
        }
        
        return result
    }

    private fun determineOptionPair(firstPercent: Float, lastPercent: Float, percentDiff: Float): Pair<Int, Int> {
        if (percentDiff > 20f) {
            return Pair(0, 3)
        }
        
        if (percentDiff > 13f) {
            val singleSpacing = percentDiff / 2
            val aPositionIfAC = firstPercent
            val aPositionIfBD = firstPercent - singleSpacing
            
            Log.d(TAG, "验证: 若A-C则A在${String.format("%.1f", aPositionIfAC)}%, 若B-D则A在${String.format("%.1f", aPositionIfBD)}%")
            
            val acValid = aPositionIfAC in 20f..38f
            val bdValid = aPositionIfBD in 20f..38f
            
            return when {
                acValid && !bdValid -> Pair(0, 2)
                bdValid && !acValid -> Pair(1, 3)
                else -> {
                    val acDist = abs(aPositionIfAC - 27f)
                    val bdDist = abs(aPositionIfBD - 27f)
                    if (acDist < bdDist) Pair(0, 2) else Pair(1, 3)
                }
            }
        }
        
        // 对于相邻选项，综合考虑A和D的位置合理性
        val singleSpacing = percentDiff
        
        // 特殊处理：根据第一个圆圈的位置判断选项组合
        // - 如果>60%：非常靠下，判断为C-D
        // - 如果48-60%且最后一个<58%：中度下移，判断为B-C
        if (firstPercent > 60f) {
            Log.d(TAG, "相邻选项判断: 检测到选项极度下移(第1个=${String.format("%.1f", firstPercent)}%), 判断为C-D")
            return Pair(2, 3)
        } else if (firstPercent > 48f && firstPercent <= 60f && lastPercent < 58f) {
            Log.d(TAG, "相邻选项判断: 检测到选项中度下移(第1个=${String.format("%.1f", firstPercent)}%), 判断为B-C")
            return Pair(1, 2)
        }
        
        val candidates = listOf(Pair(0, 1), Pair(1, 2), Pair(2, 3))
        
        var bestPair = Pair(1, 2)
        var bestScore = Float.MAX_VALUE
        
        for ((fi, li) in candidates) {
            val aPosition = firstPercent - fi * singleSpacing
            val dPosition = firstPercent + (3 - fi) * singleSpacing
            
            // 计算得分：A和D距离各自理想位置的加权距离
            // A理想位置27%，D理想位置53%
            // A权重更高，因为A位置通常更稳定
            val aIdeal = 27f
            val dIdeal = 53f
            val aDist = abs(aPosition - aIdeal)
            val dDist = abs(dPosition - dIdeal)
            val score = aDist * 1.5f + dDist
            
            // 只考虑A在22-45%且D在46-65%范围内的组合
            // 放宽范围以适应不同题目的布局差异
            if (aPosition in 22f..45f && dPosition in 46f..65f) {
                if (score < bestScore) {
                    bestScore = score
                    bestPair = Pair(fi, li)
                }
            }
        }
        
        val finalAPosition = firstPercent - bestPair.first * singleSpacing
        val finalDPosition = firstPercent + (3 - bestPair.first) * singleSpacing
        Log.d(TAG, "相邻选项判断: ${listOf("A", "B", "C", "D")[bestPair.first]}-${listOf("A", "B", "C", "D")[bestPair.second]}, 推断A在${String.format("%.1f", finalAPosition)}%, D在${String.format("%.1f", finalDPosition)}%")
        
        return bestPair
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
        
        val cleanRadius = coverRadius + 10
        for (dy in -cleanRadius..cleanRadius) {
            for (dx in -cleanRadius..cleanRadius) {
                val px = cx + dx; val py = cy + dy
                if (px in 0 until width && py in 0 until height && sqrt((dx * dx + dy * dy).toDouble()) <= cleanRadius) {
                    val hsv = FloatArray(3)
                    Color.RGBToHSV(Color.red(pixels[py * width + px]), Color.green(pixels[py * width + px]), Color.blue(pixels[py * width + px]), hsv)
                    if (hsv[1] > 0.05f) pixels[py * width + px] = Color.WHITE
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
}
