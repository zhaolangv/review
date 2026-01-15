package com.gongkao.cuotifupan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 手写输入板View
 * 用于在笔记区输入手写内容，支持识别为文字
 * 支持边写边识别功能
 */
class HandwritingInputView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 绘制路径
    private val drawingPath = Path()
    private val drawingPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        color = ContextCompat.getColor(context, android.R.color.black)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 6f
    }

    // 所有路径（用于导出）
    private val allPaths = mutableListOf<Pair<Path, Paint>>()

    // 当前正在绘制的路径
    private var currentPath: Path? = null
    
    private var startX = 0f
    private var startY = 0f
    
    // ML Kit Digital Ink 需要的笔画数据
    data class StrokePoint(val x: Float, val y: Float, val timestamp: Long)
    data class Stroke(val points: MutableList<StrokePoint>)
    
    // 所有笔画（用于ML Kit识别）
    private val allStrokes = mutableListOf<Stroke>()
    private var currentStroke: Stroke? = null

    // 绘制图层
    private var drawingBitmap: Bitmap? = null
    private var drawingCanvas: Canvas? = null

    // 手写内容变化监听器
    var onContentChanged: (() -> Unit)? = null
    
    // === 边写边识别功能 ===
    private val handler = Handler(Looper.getMainLooper())
    private var autoRecognizeRunnable: Runnable? = null
    
    // 自动识别延迟时间（毫秒）- 抬笔后多久触发识别
    var autoRecognizeDelay: Long = 800L
    
    // 是否启用边写边识别
    var enableAutoRecognize: Boolean = false
    
    // 抬笔后自动识别回调
    var onStrokeFinished: (() -> Unit)? = null
    
    // === 画布文字显示功能 ===
    /**
     * 已识别的文字信息
     */
    data class RecognizedText(
        val text: String,
        val x: Float,
        val y: Float,
        val fontSize: Float,
        val typeface: Typeface? = null,
        val textColor: Int = android.graphics.Color.BLACK
    )
    
    // 所有已识别的文字（在画布上显示）
    private val recognizedTexts = mutableListOf<RecognizedText>()
    
    // 文字绘制画笔
    private val textPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        color = android.graphics.Color.BLACK
        textAlign = Paint.Align.LEFT
        textSize = 60f // 默认字体大小
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        setBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
    }

    /**
     * 设置画笔颜色
     */
    fun setBrushColor(color: Int) {
        drawingPaint.color = color
    }

    /**
     * 设置画笔大小
     */
    fun setBrushSize(size: Float) {
        drawingPaint.strokeWidth = size
    }

    /**
     * 清除所有内容（包括已识别的文字）
     */
    fun clear() {
        allPaths.clear()
        allStrokes.clear()
        recognizedTexts.clear()
        drawingPath.reset()
        currentPath = null
        currentStroke = null
        drawingBitmap?.eraseColor(0)
        invalidate()
        onContentChanged?.invoke()
    }

    /**
     * 撤销上一步
     */
    fun undo() {
        if (allPaths.isNotEmpty()) {
            allPaths.removeAt(allPaths.size - 1)
            redrawAll()
            invalidate()
            onContentChanged?.invoke()
        }
    }

    /**
     * 检查是否有内容
     */
    fun hasContent(): Boolean = allPaths.isNotEmpty()

    /**
     * 重新绘制所有路径
     */
    private fun redrawAll() {
        drawingBitmap?.eraseColor(0)
        drawingCanvas?.let { canvas ->
            allPaths.forEach { (path, paint) ->
                canvas.drawPath(path, paint)
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        if (w > 0 && h > 0) {
            drawingBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawingCanvas = Canvas(drawingBitmap!!)
            drawingCanvas!!.drawColor(0, PorterDuff.Mode.CLEAR)
            redrawAll()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制白色背景
        canvas.drawColor(ContextCompat.getColor(context, android.R.color.white))
        
        // 绘制所有已识别的文字（在底层，这样手写内容会覆盖在上面）
        recognizedTexts.forEach { recognizedText ->
            textPaint.apply {
                color = recognizedText.textColor
                textSize = recognizedText.fontSize
                typeface = recognizedText.typeface ?: Typeface.DEFAULT
            }
            // 调试日志：检查字体是否正确设置
            if (recognizedText.typeface != null) {
                android.util.Log.d("HandwritingInputView", "绘制文字: '${recognizedText.text}', 字体: ${recognizedText.typeface.toString()}, 大小: ${recognizedText.fontSize}")
            }
            canvas.drawText(
                recognizedText.text,
                recognizedText.x,
                recognizedText.y,
                textPaint
            )
        }
        
        // 绘制所有已保存的路径
        drawingBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }
        
        // 绘制当前正在绘制的路径
        currentPath?.let { path ->
            canvas.drawPath(path, drawingPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val timestamp = System.currentTimeMillis()
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 取消之前的自动识别
                cancelAutoRecognize()
                
                startX = event.x
                startY = event.y
                
                currentPath = Path()
                currentPath?.moveTo(startX, startY)
                
                // 开始新的笔画
                currentStroke = Stroke(mutableListOf())
                currentStroke?.points?.add(StrokePoint(startX, startY, timestamp))
            }
            
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - startX)
                val dy = abs(event.y - startY)
                
                if (dx >= 4 || dy >= 4) {
                    currentPath?.quadTo(startX, startY, (event.x + startX) / 2, (event.y + startY) / 2)
                    
                    // 记录笔画点
                    currentStroke?.points?.add(StrokePoint(event.x, event.y, timestamp))
                    
                    startX = event.x
                    startY = event.y
                    invalidate()
                }
            }
            
            MotionEvent.ACTION_UP -> {
                currentPath?.lineTo(startX, startY)
                
                // 添加最后一个点
                currentStroke?.points?.add(StrokePoint(startX, startY, timestamp))
                
                currentPath?.let { path ->
                    val savedPath = Path(path)
                    val savedPaint = Paint(drawingPaint)
                    allPaths.add(Pair(savedPath, savedPaint))
                    
                    drawingCanvas?.drawPath(path, drawingPaint)
                }
                
                // 保存笔画
                currentStroke?.let { stroke ->
                    if (stroke.points.size > 1) {
                        allStrokes.add(Stroke(stroke.points.toMutableList()))
                    }
                }
                
                currentPath = null
                currentStroke = null
                invalidate()
                onContentChanged?.invoke()
                
                // 边写边识别：抬笔后延迟触发识别
                if (enableAutoRecognize && allStrokes.isNotEmpty()) {
                    scheduleAutoRecognize()
                }
            }
            
            MotionEvent.ACTION_CANCEL -> {
                currentPath = null
                currentStroke = null
                invalidate()
            }
        }
        
        return true
    }
    
    /**
     * 获取所有笔画数据（用于ML Kit识别）
     */
    fun getStrokes(): List<Stroke> {
        return allStrokes.toList()
    }

    /**
     * 获取手写内容的Bitmap（白色背景）
     */
    fun getHandwritingBitmap(): Bitmap? {
        if (width <= 0 || height <= 0) return null
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 白色背景
        canvas.drawColor(ContextCompat.getColor(context, android.R.color.white))
        
        // 绘制所有路径
        allPaths.forEach { (path, paint) ->
            canvas.drawPath(path, paint)
        }
        
        return bitmap
    }
    
    // === 边写边识别辅助方法 ===
    
    /**
     * 安排自动识别（延迟执行）
     */
    private fun scheduleAutoRecognize() {
        cancelAutoRecognize()
        autoRecognizeRunnable = Runnable {
            onStrokeFinished?.invoke()
        }
        handler.postDelayed(autoRecognizeRunnable!!, autoRecognizeDelay)
    }
    
    /**
     * 取消自动识别
     */
    private fun cancelAutoRecognize() {
        autoRecognizeRunnable?.let {
            handler.removeCallbacks(it)
        }
        autoRecognizeRunnable = null
    }
    
    /**
     * 清除当前的笔画数据（保留已识别的文字）
     * 用于边写边识别后清除已识别的手写内容
     */
    fun clearCurrentStrokes() {
        allPaths.clear()
        allStrokes.clear()
        drawingPath.reset()
        currentPath = null
        currentStroke = null
        drawingBitmap?.eraseColor(0)
        invalidate()
    }
    
    /**
     * 在画布上添加已识别的文字
     * 根据手写位置和大小自动调整文字位置和大小
     * @param text 识别后的文字
     * @param typeface 字体样式
     * @param fontSize 字体大小（像素，如果为 0 则根据手写大小自动计算）
     * @param textColor 文字颜色
     */
    fun addRecognizedText(
        text: String,
        typeface: Typeface? = null,
        fontSize: Float = 0f, // 0 表示自动计算
        textColor: Int = android.graphics.Color.BLACK
    ) {
        if (text.isBlank()) return
        
        // 计算手写内容的位置和大小（使用当前笔画的边界）
        val bounds = calculateStrokeBounds()
        
        // 计算文字位置和大小
        val x: Float
        val y: Float
        val finalFontSize: Float
        
        if (bounds != null) {
            // 有手写内容，使用手写的实际位置和大小
            val strokeWidth = bounds.width().toFloat()
            val strokeHeight = bounds.height().toFloat()
            
            // 根据手写大小自动计算字体大小
            // 优先使用宽度（因为中文字通常是方形的）
            // 字体大小约为手写宽度的 0.8-1.0 倍，但不超过高度
            finalFontSize = if (fontSize > 0f) {
                fontSize // 使用指定的字体大小
            } else {
                // 自动计算：取宽度和高度的较小值，然后乘以一个系数
                val autoSize = minOf(strokeWidth, strokeHeight) * 0.9f
                // 限制字体大小范围（最小 20，最大 200）
                autoSize.coerceIn(20f, 200f)
            }
            
            // 计算文字位置：在手写区域的中心
            // 先临时设置字体大小来计算文字边界
            val savedTextSize = textPaint.textSize
            val savedTypeface = textPaint.typeface
            textPaint.textSize = finalFontSize
            textPaint.typeface = typeface ?: Typeface.DEFAULT
            
            val textBounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, textBounds)
            
            // 计算文字中心位置
            val textWidth = textBounds.width().toFloat()
            val textHeight = textBounds.height().toFloat()
            
            // x 位置：手写区域的中心，减去文字宽度的一半
            x = bounds.left + (strokeWidth - textWidth) / 2f
            
            // y 位置：手写区域的中心，加上文字高度的一半（考虑基线）
            // 文字的基线在底部，所以需要调整
            val centerY = bounds.top + strokeHeight / 2f
            y = centerY + textHeight / 2f - textBounds.bottom
            
            // 恢复 textPaint 的属性
            textPaint.textSize = savedTextSize
            textPaint.typeface = savedTypeface
            
            android.util.Log.d("HandwritingInputView", 
                "手写位置: (${bounds.left}, ${bounds.top}), 大小: ${strokeWidth}x${strokeHeight}, " +
                "文字: '$text', 字体大小: $finalFontSize, 文字位置: ($x, $y)")
        } else {
            // 没有手写内容，使用默认位置和大小
            finalFontSize = if (fontSize > 0f) fontSize else 60f
            x = paddingStart + 20f
            y = paddingTop + finalFontSize
        }
        
        recognizedTexts.add(
            RecognizedText(
                text = text,
                x = x,
                y = y,
                fontSize = finalFontSize,
                typeface = typeface,
                textColor = textColor
            )
        )
        
        // 清除当前手写内容
        clearCurrentStrokes()
        
        invalidate()
    }
    
    /**
     * 计算当前手写笔画的边界
     */
    private fun calculateStrokeBounds(): Rect? {
        if (allStrokes.isEmpty()) return null
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        
        allStrokes.forEach { stroke ->
            stroke.points.forEach { point ->
                minX = min(minX, point.x)
                minY = min(minY, point.y)
                maxX = max(maxX, point.x)
                maxY = max(maxY, point.y)
            }
        }
        
        if (minX == Float.MAX_VALUE) return null
        
        return Rect(
            minX.toInt(),
            minY.toInt(),
            maxX.toInt(),
            maxY.toInt()
        )
    }
    
    /**
     * 获取所有已识别的文字（用于导出）
     */
    fun getRecognizedTexts(): List<RecognizedText> {
        return recognizedTexts.toList()
    }
    
    /**
     * 获取完整的文本内容（所有已识别文字拼接）
     */
    fun getRecognizedTextContent(): String {
        return recognizedTexts.joinToString("") { it.text }
    }
    
    /**
     * 检测视图销毁时清理
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cancelAutoRecognize()
    }
}

