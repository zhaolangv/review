package com.gongkao.cuotifupan.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 无限画布视图
 * 支持：
 * - 滚动（平移）
 * - 缩放
 * - 图层绘制
 * - 套索工具
 */
class InfiniteCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    // 画布相关
    private var canvasBitmap: Bitmap? = null
    private var canvas: Canvas? = null
    private val canvasPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
    }
    
    // 变换矩阵（用于滚动和缩放）
    private val transformMatrix = Matrix()
    private var scaleFactor = 1.0f
    private var translateX = 0f
    private var translateY = 0f
    private var isPositionInitialized = false  // 标记位置是否已初始化
    
    // 手势检测
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var isScaling = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isPanning = false
    private var panStartX = 0f
    private var panStartY = 0f
    private var panThreshold = 20f // 平移阈值（像素）
    
    // 当前工具
    private var currentTool: HandwritingNoteActivity.DrawingTool = HandwritingNoteActivity.DrawingTool.PEN
    
    // 状态控制
    private var isPencilEnabled = true
    private var isWritingMode = true
    private var lassoMode: HandwritingNoteActivity.LassoMode = HandwritingNoteActivity.LassoMode.FREEHAND
    
    // 画布变换回调（用于同步PhotoView）
    var onCanvasTransformChanged: ((translateX: Float, translateY: Float, scale: Float) -> Unit)? = null
    
    // 获取当前变换值（用于初始化PhotoView）
    fun getTranslateX(): Float = translateX
    fun getTranslateY(): Float = translateY
    fun getScaleFactor(): Float = scaleFactor
    
    // 图层管理
    private var layerManager: LayerManager? = null
    
    // 当前绘制
    private var currentPath: Path? = null
    private var isDrawing = false
    
    // 套索工具（规则图形）
    private var lassoStartX = 0f
    private var lassoStartY = 0f
    private var lassoRect: RectF? = null
    
    // 画笔和橡皮擦大小（分离）
    private var penSize = 6f
    private var eraserSize = 20f
    
    private val currentPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 6f
    }
    
    // 套索工具
    private var lassoPath: Path? = null
    private val lassoPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLUE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
    }
    private var selectedStrokes: MutableList<Stroke> = mutableListOf()
    private var isJustClicked = false  // 标记是否只是点击（没有移动）
    private var isMovingSelected = false  // 标记是否正在移动选中的笔画
    private var isMovingLasso = false  // 标记是否正在移动套索
    
    // 撤销/重做
    private val undoStack = mutableListOf<CanvasState>()
    private val redoStack = mutableListOf<CanvasState>()
    
    // 手写识别相关
    private var onStrokeFinished: (() -> Unit)? = null
    private val currentStrokes = mutableListOf<Stroke>()
    
    // 智能识别：基于停顿时间
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoRecognizeRunnable: Runnable? = null
    private var lastStrokeEndTime = 0L
    private val pauseThreshold = 1200L // 停顿阈值：1.2秒，超过这个时间才识别
    private val recognizedStrokes = mutableListOf<Stroke>() // 已识别过的笔画（避免重复识别）
    
    // 已识别的文字（按图层存储）
    data class RecognizedText(
        val text: String,
        var x: Float,  // 改为 var，允许修改坐标
        var y: Float,  // 改为 var，允许修改坐标
        val fontSize: Float,
        val typeface: android.graphics.Typeface? = null,
        val textColor: Int = Color.BLACK,
        val layerId: Int = 1, // 所属图层ID
        val associatedStrokes: MutableList<Stroke> = mutableListOf() // 关联的笔画
    )
    private val recognizedTexts = mutableListOf<RecognizedText>()
    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        textAlign = Paint.Align.LEFT
        textSize = 60f
    }
    
    data class Stroke(
        val path: Path,
        val paint: Paint,
        val layerId: Int
    )
    
    data class CanvasState(
        val strokes: List<Stroke>
    )
    
    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setBackgroundColor(Color.TRANSPARENT) // 透明背景，让图片显示出来
        
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scale = detector.scaleFactor
                scaleFactor *= scale
                scaleFactor = scaleFactor.coerceIn(0.5f, 5.0f)
                // 通知外部变换已改变（用于同步PhotoView）
                onCanvasTransformChanged?.invoke(translateX, translateY, scaleFactor)
                invalidate()
                return true
            }
        })
    }
    
    fun setLayerManager(manager: LayerManager) {
        layerManager = manager
    }
    
    fun setOnStrokeFinished(callback: () -> Unit) {
        onStrokeFinished = callback
    }
    
    // 临时存储当前笔画的点（用于识别）
    private val currentStrokePoints = mutableListOf<com.gongkao.cuotifupan.ui.HandwritingInputView.StrokePoint>()
    private var currentStrokeStartTime = 0L
    
    fun getCurrentStrokes(): List<com.gongkao.cuotifupan.ui.HandwritingInputView.Stroke> {
        // 获取所有未识别的笔画（排除已识别过的）
        val strokes = mutableListOf<com.gongkao.cuotifupan.ui.HandwritingInputView.Stroke>()
        layerManager?.getCurrentLayer()?.strokes?.forEach { stroke ->
            // 如果这个笔画还没有被识别过，则添加到列表
            if (!recognizedStrokes.contains(stroke)) {
                // 从 Path 中提取点
                val points = extractPointsFromPath(stroke.path)
                if (points.isNotEmpty()) {
                    strokes.add(com.gongkao.cuotifupan.ui.HandwritingInputView.Stroke(points))
                }
            }
        }
        return strokes
    }
    
    /**
     * 从 Path 中提取笔画点
     */
    private fun extractPointsFromPath(path: Path): MutableList<com.gongkao.cuotifupan.ui.HandwritingInputView.StrokePoint> {
        val points = mutableListOf<com.gongkao.cuotifupan.ui.HandwritingInputView.StrokePoint>()
        val pathMeasure = android.graphics.PathMeasure(path, false)
        val length = pathMeasure.length
        
        if (length <= 0) return points
        
        var distance = 0f
        val coords = FloatArray(2)
        var timestamp = System.currentTimeMillis()
        
        while (distance < length && points.size < 1000) {
            pathMeasure.getPosTan(distance, coords, null)
            points.add(com.gongkao.cuotifupan.ui.HandwritingInputView.StrokePoint(
                coords[0], coords[1], timestamp
            ))
            distance += 10f
            timestamp += 10
        }
        
        return points
    }
    
    /**
     * 安排自动识别（基于停顿时间）
     */
    private fun scheduleAutoRecognize() {
        cancelAutoRecognize()
        autoRecognizeRunnable = Runnable {
            val currentTime = System.currentTimeMillis()
            val pauseDuration = currentTime - lastStrokeEndTime
            
            // 如果停顿时间超过阈值，才触发识别
            if (pauseDuration >= pauseThreshold) {
                onStrokeFinished?.invoke()
            } else {
                // 如果还没到阈值，继续等待
                val remainingTime = pauseThreshold - pauseDuration
                handler.postDelayed(autoRecognizeRunnable!!, remainingTime)
            }
        }
        // 延迟 pauseThreshold 时间后检查
        handler.postDelayed(autoRecognizeRunnable!!, pauseThreshold)
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
    
    fun addRecognizedText(
        text: String,
        typeface: android.graphics.Typeface? = null,
        fontSize: Float = 0f,
        textColor: Int = Color.BLACK
    ) {
        if (text.isBlank()) return
        
        // 计算位置（使用当前图层的最后一个笔画位置）
        val bounds = calculateLastStrokeBounds()
        val x: Float
        val y: Float
        val finalFontSize: Float
        
        if (bounds != null) {
            // 使用实际笔画的高度作为字体大小（精确匹配）
            val strokeHeight = (bounds.bottom - bounds.top).toFloat()
            finalFontSize = if (fontSize > 0f) fontSize else {
                // 字体大小 = 笔画高度，完全匹配
                max(20f, strokeHeight).coerceIn(20f, 200f)
            }
            
            // 使用笔画的左上角位置（精确对齐）
            x = bounds.left.toFloat()
            // y 位置：使用笔画顶部 + 字体大小（文字基线在底部）
            // 为了让文字顶部和笔画顶部对齐
            y = bounds.top.toFloat() + finalFontSize
        } else {
            finalFontSize = if (fontSize > 0f) fontSize else 60f
            x = 20f
            y = finalFontSize
        }
        
        // 获取当前图层ID
        val currentLayerId = layerManager?.getCurrentLayer()?.id ?: 1
        
        // 获取当前图层的所有未识别笔画（这些笔画将被关联到这个文字）
        val associatedStrokes = mutableListOf<Stroke>()
        layerManager?.getCurrentLayer()?.strokes?.forEach { stroke ->
            if (!recognizedStrokes.contains(stroke)) {
                recognizedStrokes.add(stroke)
                associatedStrokes.add(stroke)
            }
        }
        
        // 创建 RecognizedText 并关联笔画
        recognizedTexts.add(RecognizedText(text, x, y, finalFontSize, typeface, textColor, currentLayerId, associatedStrokes))
        
        // 清除临时笔画点用于下次识别
        currentStrokePoints.clear()
        
        invalidate()
    }
    
    /**
     * 计算所有未识别笔画的整体边界（用于精确定位文字）
     */
    private fun calculateLastStrokeBounds(): Rect? {
        val layer = layerManager?.getCurrentLayer() ?: return null
        if (layer.strokes.isEmpty()) return null
        
        // 计算所有未识别笔画的整体边界
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var hasUnrecognizedStroke = false
        
        layer.strokes.forEach { stroke ->
            // 只计算未识别的笔画
            if (!recognizedStrokes.contains(stroke)) {
                hasUnrecognizedStroke = true
                val bounds = RectF()
                stroke.path.computeBounds(bounds, true)
                minX = min(minX, bounds.left)
                minY = min(minY, bounds.top)
                maxX = max(maxX, bounds.right)
                maxY = max(maxY, bounds.bottom)
            }
        }
        
        if (!hasUnrecognizedStroke) return null
        
        return Rect(
            minX.toInt(),
            minY.toInt(),
            maxX.toInt(),
            maxY.toInt()
        )
    }
    
    fun setPencilEnabled(enabled: Boolean) {
        isPencilEnabled = enabled
    }
    
    fun setWritingMode(writing: Boolean) {
        isWritingMode = writing
    }
    
    fun setLassoMode(mode: HandwritingNoteActivity.LassoMode) {
        lassoMode = mode
    }
    
    fun setEraserSize(size: Float) {
        eraserSize = size.coerceIn(5f, 100f)
        // 只在当前是橡皮擦工具时才更新
        if (currentTool == HandwritingNoteActivity.DrawingTool.ERASER) {
            currentPaint.strokeWidth = eraserSize
        }
    }
    
    fun getEraserSize(): Float = eraserSize
    
    fun setTool(tool: HandwritingNoteActivity.DrawingTool) {
        currentTool = tool
        when (tool) {
            HandwritingNoteActivity.DrawingTool.PEN -> {
                currentPaint.color = Color.BLACK
                currentPaint.style = Paint.Style.STROKE
                currentPaint.xfermode = null
                currentPaint.strokeWidth = penSize // 使用画笔大小
                // 设置pencil效果：根据压力调整笔触
                if (isPencilEnabled) {
                    // strokeWidth 会在绘制时根据压力动态调整
                }
            }
            HandwritingNoteActivity.DrawingTool.ERASER -> {
                currentPaint.color = Color.WHITE
                currentPaint.style = Paint.Style.STROKE
                currentPaint.strokeWidth = eraserSize // 使用橡皮擦大小
                currentPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            HandwritingNoteActivity.DrawingTool.MASK -> {
                // 胶布工具：使用半透明白色覆盖
                currentPaint.color = Color.argb(200, 255, 255, 255)
                currentPaint.style = Paint.Style.FILL
                currentPaint.xfermode = null
            }
            HandwritingNoteActivity.DrawingTool.LASSO -> {
                // 套索工具不需要绘制
            }
        }
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.d("InfiniteCanvasView", "onSizeChanged被调用: w=$w, h=$h, oldw=$oldw, oldh=$oldh, 当前translateX=$translateX, translateY=$translateY, isPositionInitialized=$isPositionInitialized")
        if (w > 0 && h > 0) {
            // 创建更大的画布（支持无限扩展）
            val canvasWidth = max(w * 3, 3000)
            val canvasHeight = max(h * 3, 4000)
            
            // 如果画布已存在且尺寸相同，不需要重新创建
            val needRecreate = canvasBitmap == null || canvasBitmap!!.width != canvasWidth || canvasBitmap!!.height != canvasHeight
            Log.d("InfiniteCanvasView", "onSizeChanged: needRecreate=$needRecreate, canvasBitmap=${canvasBitmap != null}, canvasWidth=$canvasWidth, canvasHeight=$canvasHeight")
            if (needRecreate) {
                canvasBitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
                canvas = Canvas(canvasBitmap!!)
                // 不绘制白色背景，让图片显示出来
                // canvas!!.drawColor(Color.WHITE)
                
                // 只在首次初始化时设置初始位置，避免覆盖已有的画布位置
                if (!isPositionInitialized) {
                    // 初始位置：画布中心
                    translateX = (canvasWidth - w) / 2f
                    translateY = (canvasHeight - h) / 2f
                    isPositionInitialized = true
                    Log.d("InfiniteCanvasView", "首次初始化画布位置: translateX=$translateX, translateY=$translateY")
                } else {
                    Log.d("InfiniteCanvasView", "画布尺寸改变但保持当前位置: translateX=$translateX, translateY=$translateY")
                }
            }
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 应用变换（缩放和平移）
        canvas.save()
        transformMatrix.reset()
        // 先缩放，再平移（正确的变换顺序）
        transformMatrix.postScale(scaleFactor, scaleFactor)
        transformMatrix.postTranslate(translateX, translateY)
        canvas.concat(transformMatrix)
        
        
        // 绘制所有已识别的文字（按图层显示/隐藏）
        recognizedTexts.forEach { recognizedText: RecognizedText ->
            // 只绘制可见图层的文字
            val layer = layerManager?.getAllLayers()?.find { it.id == recognizedText.layerId }
            if (layer?.visible == true) {
                textPaint.apply {
                    color = recognizedText.textColor
                    textSize = recognizedText.fontSize
                    typeface = recognizedText.typeface ?: android.graphics.Typeface.DEFAULT
                }
                canvas.drawText(
                    recognizedText.text,
                    recognizedText.x,
                    recognizedText.y,
                    textPaint
                )
            }
        }
        
        // 绘制所有图层的笔画（跳过已识别的笔画，避免重叠）
        layerManager?.getAllLayers()?.forEach { layer ->
            if (layer.visible) {
                layer.strokes.forEach { stroke: Stroke ->
                    // 只绘制未识别的笔画，已识别的笔画不显示（已转换为美化文字）
                    if (!recognizedStrokes.contains(stroke)) {
                        canvas.drawPath(stroke.path, stroke.paint)
                    }
                }
            }
        }
        
        // 绘制当前正在绘制的路径
        currentPath?.let {
            canvas.drawPath(it, currentPaint)
        }
        
        // 绘制套索路径
        lassoPath?.let {
            canvas.drawPath(it, lassoPaint)
        }
        
        // 绘制规则套索图形
        lassoRect?.let { rect ->
            when (lassoMode) {
                HandwritingNoteActivity.LassoMode.RECTANGLE -> {
                    canvas.drawRect(rect, lassoPaint)
                }
                HandwritingNoteActivity.LassoMode.CIRCLE -> {
                    val radius = min(rect.width(), rect.height()) / 2f
                    canvas.drawCircle(rect.centerX(), rect.centerY(), radius, lassoPaint)
                }
                else -> {}
            }
        }
        
        // 不显示高亮框（用户要求不显示里面那个框）
        // 高亮显示选中的笔画
        // if (selectedStrokes.isNotEmpty()) {
        //     val highlightPaint = Paint().apply {
        //         isAntiAlias = true
        //         color = Color.argb(100, 100, 150, 255)
        //         style = Paint.Style.STROKE
        //         strokeWidth = 3f
        //         pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f)
        //     }
        //     selectedStrokes.forEach { stroke: Stroke ->
        //         val bounds = RectF()
        //         stroke.path.computeBounds(bounds, true)
        //         bounds.inset(-5f, -5f)
        //         canvas.drawRect(bounds, highlightPaint)
        //     }
        // }
        
        canvas.restore()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 查看模式下，只允许滚动和缩放，不允许绘制
        if (!isWritingMode) {
            // 在查看模式下，允许平移和缩放
            // 处理缩放手势
            scaleGestureDetector?.onTouchEvent(event)
            
            if (event.pointerCount == 1) {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                        parent?.requestDisallowInterceptTouchEvent(true) // 阻止父视图拦截
                        Log.d("InfiniteCanvasView", "查看模式 ACTION_DOWN: x=${event.x}, y=${event.y}")
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.x - lastTouchX
                        val dy = event.y - lastTouchY
                        translateX += dx
                        translateY += dy
                        lastTouchX = event.x
                        lastTouchY = event.y
                        // 通知外部变换已改变（用于同步PhotoView）
                        onCanvasTransformChanged?.invoke(translateX, translateY, scaleFactor)
                        invalidate()
                        parent?.requestDisallowInterceptTouchEvent(true) // 继续阻止父视图拦截
                        Log.d("InfiniteCanvasView", "查看模式 ACTION_MOVE: dx=$dx, dy=$dy, translateX=$translateX, translateY=$translateY")
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        parent?.requestDisallowInterceptTouchEvent(false) // 释放拦截
                        Log.d("InfiniteCanvasView", "查看模式 ACTION_UP/CANCEL")
                    }
                }
            } else if (event.pointerCount == 2) {
                // 双指缩放
                scaleGestureDetector?.onTouchEvent(event)
            }
            return true
        }
        
        // 处理缩放手势（书写模式下）
        scaleGestureDetector?.onTouchEvent(event)
        
        // 书写模式下不允许平移，只允许绘制
        // 如果笔触未启用，不允许绘制
        if (!isPencilEnabled && (currentTool == HandwritingNoteActivity.DrawingTool.PEN || 
            currentTool == HandwritingNoteActivity.DrawingTool.ERASER || 
            currentTool == HandwritingNoteActivity.DrawingTool.MASK)) {
            return false
        }
        
        // 将屏幕坐标转换为画布坐标
        val inverseMatrix = Matrix()
        transformMatrix.invert(inverseMatrix)
        val points = floatArrayOf(event.x, event.y)
        inverseMatrix.mapPoints(points)
        val canvasX = points[0]
        val canvasY = points[1]
        
        when (currentTool) {
            HandwritingNoteActivity.DrawingTool.PEN,
            HandwritingNoteActivity.DrawingTool.ERASER,
            HandwritingNoteActivity.DrawingTool.MASK -> {
                // 书写模式下只允许绘制，不允许平移
                handleDrawing(event, canvasX, canvasY)
            }
            HandwritingNoteActivity.DrawingTool.LASSO -> {
                handleLasso(event, canvasX, canvasY)
            }
        }
        
        return true
    }
    
    private fun handlePanning(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                panStartX = event.x
                panStartY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastTouchX
                val dy = event.y - lastTouchY
                translateX += dx
                translateY += dy
                lastTouchX = event.x
                lastTouchY = event.y
                onCanvasTransformChanged?.invoke(translateX, translateY, scaleFactor)
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isPanning = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
    
    /**
     * 根据压力更新笔触大小（适配平板pencil）
     */
    private fun updatePaintPressure(event: MotionEvent) {
        // 检查是否为手写笔输入（平板pencil）
        val isStylus = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
        } else {
            false
        }
        
        // 获取压力值
        // 手写笔的压力范围通常是 0.0 - 1.0，但某些设备可能不同
        var pressure = event.pressure
        
        // 对于手写笔，压力值通常在合理范围内；对于手指，压力通常接近1.0
        if (isStylus) {
            // 手写笔：使用实际压力值，范围可能在 0.0 - 1.0 或更大
            // 某些设备（如S Pen）的压力范围可能是 0.0 - 1.0
            // 某些设备可能是 0.0 - 1.4 或更大
            pressure = pressure.coerceIn(0.0f, 2.0f) // 允许更大的压力范围
            // 将压力值归一化到 0.3 - 1.2 倍 penSize
            val normalizedPressure = (pressure / 1.0f).coerceIn(0.0f, 1.5f)
            currentPaint.strokeWidth = penSize * (0.3f + normalizedPressure * 0.9f)
            Log.d("InfiniteCanvasView", "Pencil压力: $pressure (手写笔), 笔触大小: ${currentPaint.strokeWidth}")
        } else {
            // 手指输入：压力值通常接近1.0，但仍然可以稍微变化
            pressure = pressure.coerceIn(0.5f, 1.5f)
            // 手指的压力变化范围较小，使用较小的变化范围
            currentPaint.strokeWidth = penSize * (0.8f + (pressure - 0.5f) * 0.4f)
            Log.d("InfiniteCanvasView", "手指压力: $pressure, 笔触大小: ${currentPaint.strokeWidth}")
        }
    }
    
    private fun handleDrawing(event: MotionEvent, x: Float, y: Float) {
        val timestamp = System.currentTimeMillis()
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 取消之前的识别任务（用户继续写，说明还没写完）
                cancelAutoRecognize()
                
                // 保存状态用于撤销
                saveState()
                
                currentPath = Path()
                currentPath?.moveTo(x, y)
                lastTouchX = x
                lastTouchY = y
                isDrawing = true
                isPanning = false
                
                // 设置初始笔触大小（pencil效果：根据压力调整）
                if (isPencilEnabled && currentTool == HandwritingNoteActivity.DrawingTool.PEN) {
                    updatePaintPressure(event)
                }
                
                // 开始新的笔画点记录
                currentStrokePoints.clear()
                currentStrokeStartTime = timestamp
                currentStrokePoints.add(com.gongkao.cuotifupan.ui.HandwritingInputView.StrokePoint(x, y, timestamp))
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDrawing) return
                
                // 根据压力调整笔触大小（pencil效果）
                if (isPencilEnabled && currentTool == HandwritingNoteActivity.DrawingTool.PEN) {
                    updatePaintPressure(event)
                }
                
                val dx = abs(x - lastTouchX)
                val dy = abs(y - lastTouchY)
                if (dx >= 4 || dy >= 4) {
                    currentPath?.quadTo(lastTouchX, lastTouchY, (x + lastTouchX) / 2, (y + lastTouchY) / 2)
                    
                    // 记录笔画点
                    currentStrokePoints.add(com.gongkao.cuotifupan.ui.HandwritingInputView.StrokePoint(x, y, timestamp))
                    
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDrawing) {
                    isPanning = false
                    return
                }
                currentPath?.lineTo(lastTouchX, lastTouchY)
                
                // 添加最后一个点
                currentStrokePoints.add(com.gongkao.cuotifupan.ui.HandwritingInputView.StrokePoint(lastTouchX, lastTouchY, timestamp))
                
                // 保存到当前图层（保留手写内容）
                currentPath?.let { path ->
                    val savedPath = Path(path)
                    val savedPaint = Paint(currentPaint)
                    val layerId = layerManager?.getCurrentLayer()?.id ?: 1
                    
                    val stroke = Stroke(savedPath, savedPaint, layerId)
                    layerManager?.getCurrentLayer()?.strokes?.add(stroke)
                    
                    // 绘制到画布
                    canvas?.drawPath(savedPath, savedPaint)
                }
                
                currentPath = null
                isDrawing = false
                lastStrokeEndTime = timestamp
                
                // 取消之前的识别任务
                cancelAutoRecognize()
                
                // 智能识别：如果停顿时间超过阈值，才触发识别
                scheduleAutoRecognize()
                
                invalidate()
            }
        }
    }
    
    private fun handleLasso(event: MotionEvent, x: Float, y: Float) {
        // 检测点击是否在套索上
        fun isPointOnLasso(pointX: Float, pointY: Float, tolerance: Float = 20f): Boolean {
            // 检查自由形状套索
            lassoPath?.let { path ->
                val region = Region()
                val rectF = RectF()
                path.computeBounds(rectF, true)
                rectF.inset(-tolerance, -tolerance)
                val rect = Rect(
                    rectF.left.toInt(),
                    rectF.top.toInt(),
                    rectF.right.toInt(),
                    rectF.bottom.toInt()
                )
                val expandedBounds = Region(
                    rect.left - tolerance.toInt(),
                    rect.top - tolerance.toInt(),
                    rect.right + tolerance.toInt(),
                    rect.bottom + tolerance.toInt()
                )
                region.setPath(path, expandedBounds)
                
                val pointRegion = Region()
                val pointRect = Rect(
                    (pointX - tolerance).toInt(),
                    (pointY - tolerance).toInt(),
                    (pointX + tolerance).toInt(),
                    (pointY + tolerance).toInt()
                )
                pointRegion.set(pointRect)
                if (region.op(pointRegion, Region.Op.INTERSECT)) {
                    return true
                }
            }
            
            // 检查矩形/圆形套索
            lassoRect?.let { rect ->
                // 确保矩形有效
                if (rect.width() > 0 && rect.height() > 0) {
                    val expandedRect = RectF(
                        rect.left - tolerance,
                        rect.top - tolerance,
                        rect.right + tolerance,
                        rect.bottom + tolerance
                    )
                    if (expandedRect.contains(pointX, pointY)) {
                        return true
                    }
                }
            }
            return false
        }
        
        // 检测点击是否在选中的笔画上
        fun isPointOnSelectedStroke(pointX: Float, pointY: Float, tolerance: Float = 30f): Boolean {
            return selectedStrokes.any { stroke ->
                val bounds = RectF()
                stroke.path.computeBounds(bounds, true)
                bounds.inset(-tolerance, -tolerance)
                if (!bounds.contains(pointX, pointY)) {
                    return@any false
                }
                val pathBounds = Rect()
                bounds.round(pathBounds)
                if (pathBounds.width() <= 0 || pathBounds.height() <= 0) {
                    val centerX = bounds.centerX()
                    val centerY = bounds.centerY()
                    val distance = kotlin.math.sqrt((pointX - centerX) * (pointX - centerX) + (pointY - centerY) * (pointY - centerY))
                    return@any distance <= tolerance * 2
                }
                val region = Region()
                val regionBounds = Region(
                    pathBounds.left - tolerance.toInt(),
                    pathBounds.top - tolerance.toInt(),
                    pathBounds.right + tolerance.toInt(),
                    pathBounds.bottom + tolerance.toInt()
                )
                region.setPath(stroke.path, regionBounds)
                val pointRegion = Region()
                val pointRect = Rect(
                    (pointX - tolerance).toInt(),
                    (pointY - tolerance).toInt(),
                    (pointX + tolerance).toInt(),
                    (pointY + tolerance).toInt()
                )
                pointRegion.set(pointRect)
                region.op(pointRegion, Region.Op.INTERSECT)
            }
        }
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 如果有选中的笔画，优先允许移动选中的笔画（在套索内任意位置拖动都可以）
                if (selectedStrokes.isNotEmpty()) {
                    // 移动选中的笔画
                    isMovingSelected = true
                    isMovingLasso = false
                    lastTouchX = x
                    lastTouchY = y
                    isJustClicked = true
                    saveState()
                    Log.d("InfiniteCanvasView", "开始移动选中的笔画，选中 ${selectedStrokes.size} 个")
                    return // 重要：立即返回，不要继续执行绘制新套索的逻辑
                } else if (isPointOnLasso(x, y)) {
                    // 没有选中笔画时，点击套索可以移动套索本身
                    isMovingLasso = true
                    isMovingSelected = false
                    lastTouchX = x
                    lastTouchY = y
                    saveState()
                    Log.d("InfiniteCanvasView", "开始移动套索")
                    return // 重要：立即返回，不要继续执行绘制新套索的逻辑
                } else {
                    // 点击空白处，开始绘制新套索或清除选择
                    isMovingLasso = false
                    isMovingSelected = false
                    // 继续执行下面的套索绘制逻辑
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isMovingLasso) {
                    // 移动套索（同时移动选中的笔画）
                    val moveDx = x - lastTouchX
                    val moveDy = y - lastTouchY
                    
                    // 移动自由形状套索
                    lassoPath?.let { path ->
                        val matrix = Matrix()
                        matrix.postTranslate(moveDx, moveDy)
                        path.transform(matrix)
                    }
                    
                    // 移动矩形/圆形套索
                    lassoRect?.let { rect ->
                        // 直接移动，不检查有效性（因为移动过程中可能会暂时无效）
                        lassoRect = RectF(
                            rect.left + moveDx,
                            rect.top + moveDy,
                            rect.right + moveDx,
                            rect.bottom + moveDy
                        )
                        Log.d("InfiniteCanvasView", "移动套索: dx=$moveDx, dy=$moveDy, 新位置=(${lassoRect!!.left}, ${lassoRect!!.top}, ${lassoRect!!.right}, ${lassoRect!!.bottom}), size=(${lassoRect!!.width()}, ${lassoRect!!.height()})")
                    }
                    
                    // 同时移动选中的笔画和对应的文字
                    if (selectedStrokes.isNotEmpty()) {
                        Log.d("InfiniteCanvasView", "移动套索时同时移动 ${selectedStrokes.size} 个选中的笔画")
                        selectedStrokes.forEach { stroke: Stroke ->
                            val matrix = Matrix()
                            matrix.postTranslate(moveDx, moveDy)
                            stroke.path.transform(matrix)
                            
                            // 更新对应的 RecognizedText 坐标
                            recognizedTexts.forEach { recognizedText ->
                                if (recognizedText.associatedStrokes.contains(stroke)) {
                                    recognizedText.x += moveDx
                                    recognizedText.y += moveDy
                                    Log.d("InfiniteCanvasView", "更新文字位置: '${recognizedText.text}' -> (${recognizedText.x}, ${recognizedText.y})")
                                }
                            }
                        }
                        redrawCanvas()
                    }
                    
                    lastTouchX = x
                    lastTouchY = y
                    invalidate()
                    return
                } else if (isMovingSelected) {
                    // 移动选中的笔画（在套索内任意位置拖动都可以移动）
                    // 同时移动套索，让套索和内容一起移动
                    val moveDx = x - lastTouchX
                    val moveDy = y - lastTouchY
                    val dx = Math.abs(moveDx)
                    val dy = Math.abs(moveDy)
                    
                    // 只要有移动就立即更新（移除阈值，让移动更流畅）
                    if (dx > 0.1f || dy > 0.1f) {
                        isJustClicked = false
                        
                        Log.d("InfiniteCanvasView", "移动笔画和套索: dx=$moveDx, dy=$moveDy, 选中 ${selectedStrokes.size} 个")
                        
                        // 同时移动套索
                        lassoPath?.let { path ->
                            val matrix = Matrix()
                            matrix.postTranslate(moveDx, moveDy)
                            path.transform(matrix)
                        }
                        lassoRect?.let { rect ->
                            lassoRect = RectF(
                                rect.left + moveDx,
                                rect.top + moveDy,
                                rect.right + moveDx,
                                rect.bottom + moveDy
                            )
                        }
                        
                        // 移动选中的笔画
                        selectedStrokes.forEach { stroke: Stroke ->
                            val matrix = Matrix()
                            matrix.postTranslate(moveDx, moveDy)
                            stroke.path.transform(matrix)
                            
                            // 更新对应的 RecognizedText 坐标
                            recognizedTexts.forEach { recognizedText ->
                                if (recognizedText.associatedStrokes.contains(stroke)) {
                                    recognizedText.x += moveDx
                                    recognizedText.y += moveDy
                                    Log.d("InfiniteCanvasView", "更新文字位置: '${recognizedText.text}' -> (${recognizedText.x}, ${recognizedText.y})")
                                }
                            }
                        }
                        lastTouchX = x
                        lastTouchY = y
                        redrawCanvas()
                        invalidate()
                    }
                    return
                }
                // 如果没有移动套索或笔画，继续绘制套索
            }
            MotionEvent.ACTION_UP -> {
                if (isMovingLasso) {
                    // 套索移动完成，重新检测选中的笔画
                    // 确保套索区域有效
                    val isValid = when {
                        lassoPath != null -> {
                            val rectF = RectF()
                            lassoPath!!.computeBounds(rectF, true)
                            val valid = rectF.width() > 0 && rectF.height() > 0
                            if (!valid) {
                                Log.d("InfiniteCanvasView", "套索移动后区域无效: width=${rectF.width()}, height=${rectF.height()}")
                            }
                            valid
                        }
                        lassoRect != null -> {
                            val valid = lassoRect!!.width() > 0 && lassoRect!!.height() > 0
                            if (!valid) {
                                Log.d("InfiniteCanvasView", "套索移动后区域无效: width=${lassoRect!!.width()}, height=${lassoRect!!.height()}")
                            }
                            valid
                        }
                        else -> {
                            Log.d("InfiniteCanvasView", "套索移动后区域为空")
                            false
                        }
                    }
                    
                    if (isValid) {
                        detectStrokesInLasso()
                        Log.d("InfiniteCanvasView", "套索移动完成，已重新检测笔画，选中 ${selectedStrokes.size} 个")
                    } else {
                        Log.d("InfiniteCanvasView", "套索移动完成，但区域无效，跳过检测")
                        // 如果区域无效，清除套索
                        clearLassoSelection()
                    }
                    isMovingLasso = false
                    invalidate()
                    return
                } else if (isMovingSelected) {
                    isMovingSelected = false
                    isJustClicked = false
                    return
                }
                // 如果没有移动，继续绘制套索
            }
        }
        
        // 绘制新套索
        when (lassoMode) {
            HandwritingNoteActivity.LassoMode.FREEHAND -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lassoPath = Path()
                        lassoPath?.moveTo(x, y)
                        lassoRect = null
                        selectedStrokes.clear()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lassoPath?.lineTo(x, y)
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        lassoPath?.close()
                        // 检测套索内的笔画
                        detectStrokesInLasso()
                        invalidate()
                    }
                }
            }
            HandwritingNoteActivity.LassoMode.RECTANGLE,
            HandwritingNoteActivity.LassoMode.CIRCLE -> {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 开始新的套索绘制时，清除之前的套索路径和选中状态
                        lassoStartX = x
                        lassoStartY = y
                        lassoPath = null
                        lassoRect = RectF(lassoStartX, lassoStartY, x, y)
                        selectedStrokes.clear()
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lassoRect = RectF(
                            min(lassoStartX, x),
                            min(lassoStartY, y),
                            max(lassoStartX, x),
                            max(lassoStartY, y)
                        )
                        invalidate()
                    }
                    MotionEvent.ACTION_UP -> {
                        // 检测套索内的笔画
                        detectStrokesInLasso()
                        invalidate()
                    }
                }
            }
        }
    }
    
    private fun detectStrokesInLasso() {
        val region = Region()
        
        when (lassoMode) {
            HandwritingNoteActivity.LassoMode.FREEHAND -> {
                lassoPath?.let { path ->
                    val rectF = RectF()
                    path.computeBounds(rectF, true)
                    // 确保边界框有效
                    if (rectF.width() <= 0 || rectF.height() <= 0) {
                        Log.d("InfiniteCanvasView", "套索区域无效")
                        return
                    }
                    val rect = Rect(
                        rectF.left.toInt(),
                        rectF.top.toInt(),
                        rectF.right.toInt(),
                        rectF.bottom.toInt()
                    )
                    // 使用更大的边界框以确保 Region 正常工作
                    val expandedBounds = Region(
                        rect.left - 100,
                        rect.top - 100,
                        rect.right + 100,
                        rect.bottom + 100
                    )
                    region.setPath(path, expandedBounds)
                } ?: return
            }
            HandwritingNoteActivity.LassoMode.RECTANGLE -> {
                lassoRect?.let { rect ->
                    if (rect.width() <= 0 || rect.height() <= 0) {
                        Log.d("InfiniteCanvasView", "矩形套索区域无效")
                        return
                    }
                    region.set(
                        rect.left.toInt(),
                        rect.top.toInt(),
                        rect.right.toInt(),
                        rect.bottom.toInt()
                    )
                } ?: return
            }
            HandwritingNoteActivity.LassoMode.CIRCLE -> {
                lassoRect?.let { rect ->
                    if (rect.width() <= 0 || rect.height() <= 0) {
                        Log.d("InfiniteCanvasView", "圆形套索区域无效")
                        return
                    }
                    val radius = min(rect.width(), rect.height()) / 2f
                    val centerX = rect.centerX()
                    val centerY = rect.centerY()
                    // 创建圆形区域（使用Path）
                    val circlePath = Path()
                    circlePath.addCircle(centerX, centerY, radius, Path.Direction.CW)
                    val bounds = Rect(
                        (centerX - radius - 10).toInt(),
                        (centerY - radius - 10).toInt(),
                        (centerX + radius + 10).toInt(),
                        (centerY + radius + 10).toInt()
                    )
                    val expandedBounds = Region(bounds)
                    region.setPath(circlePath, expandedBounds)
                } ?: return
            }
        }
        
        selectedStrokes.clear()
        var totalStrokesChecked = 0
        var totalLayers = 0
        var totalVisibleLayers = 0
        var totalStrokesInLayers = 0
        
        // 获取套索边界用于日志
        val lassoBoundsStr = when {
            lassoPath != null -> {
                val rectF = RectF()
                lassoPath!!.computeBounds(rectF, true)
                "自由套索: left=${rectF.left}, top=${rectF.top}, right=${rectF.right}, bottom=${rectF.bottom}, width=${rectF.width()}, height=${rectF.height()}"
            }
            lassoRect != null -> {
                "矩形/圆形套索: left=${lassoRect!!.left}, top=${lassoRect!!.top}, right=${lassoRect!!.right}, bottom=${lassoRect!!.bottom}, width=${lassoRect!!.width()}, height=${lassoRect!!.height()}"
            }
            else -> "无套索"
        }
        Log.d("InfiniteCanvasView", "开始检测笔画，套索区域: $lassoBoundsStr")
        
        layerManager?.getAllLayers()?.forEach { layer ->
            totalLayers++
            if (layer.visible) {
                totalVisibleLayers++
                totalStrokesInLayers += layer.strokes.size
                layer.strokes.forEach { stroke: Stroke ->
                    totalStrokesChecked++
                    // 检查笔画是否在套索区域内（包括已识别的笔画，因为用户可能想移动它们）
                    val strokeRect = RectF()
                    stroke.path.computeBounds(strokeRect, true)
                    
                    // 先检查边界框是否相交
                    val strokeBounds = Rect(
                        strokeRect.left.toInt(),
                        strokeRect.top.toInt(),
                        strokeRect.right.toInt(),
                        strokeRect.bottom.toInt()
                    )
                    
                    if (!region.quickReject(strokeBounds)) {
                        // 更精确的检查：检查笔画路径是否与区域相交
                        val strokeRegion = Region()
                        val expandedStrokeBounds = Region(
                            strokeBounds.left - 10,
                            strokeBounds.top - 10,
                            strokeBounds.right + 10,
                            strokeBounds.bottom + 10
                        )
                        strokeRegion.setPath(stroke.path, expandedStrokeBounds)
                        if (region.op(strokeRegion, Region.Op.INTERSECT)) {
                            selectedStrokes.add(stroke)
                            val isRecognized = recognizedStrokes.contains(stroke)
                            Log.d("InfiniteCanvasView", "选中笔画: bounds=(${strokeRect.left}, ${strokeRect.top}, ${strokeRect.right}, ${strokeRect.bottom}), 已识别=$isRecognized")
                        }
                    }
                }
            }
        }
        
        Log.d("InfiniteCanvasView", "检测结果: 总图层=$totalLayers, 可见图层=$totalVisibleLayers, 图层中总笔画=$totalStrokesInLayers, 检查了 $totalStrokesChecked 个未识别笔画，选中了 ${selectedStrokes.size} 个笔画")
        
        // 保留套索路径和矩形，让用户看到选中的区域
        // 只在开始新的套索绘制时才清除
        
        // 通知选择完成
        if (selectedStrokes.isNotEmpty()) {
            onLassoSelectionComplete?.invoke()
        }
    }
    
    // 套索选择完成的回调
    var onLassoSelectionComplete: (() -> Unit)? = null
    
    // 删除选中的笔画
    fun deleteSelectedStrokes() {
        val count = selectedStrokes.size
        if (count > 0) {
            saveState()
            Log.d("InfiniteCanvasView", "删除选中的笔画，共 $count 个")
            selectedStrokes.forEach { stroke: Stroke ->
                layerManager?.getAllLayers()?.forEach { layer ->
                    layer.strokes.remove(stroke)
                }
                recognizedStrokes.remove(stroke) // 如果是已识别的笔画，也要从已识别列表中移除
            }
            selectedStrokes.clear()
            clearLassoSelection() // 删除后清除套索
            redrawCanvas()
            invalidate()
            Log.d("InfiniteCanvasView", "删除完成")
        } else {
            Log.d("InfiniteCanvasView", "没有选中的笔画可删除")
        }
    }
    
    // 清除套索选择
    fun clearLassoSelection() {
        lassoPath = null
        lassoRect = null
        selectedStrokes.clear()
        invalidate()
    }
    
    // 删除套索（包括路径和选中的笔画）
    fun deleteLasso() {
        val count = selectedStrokes.size
        Log.d("InfiniteCanvasView", "deleteLasso: 删除套索和选中的 $count 个笔画")
        // 先删除选中的笔画
        if (count > 0) {
            saveState()
            selectedStrokes.forEach { stroke: Stroke ->
                layerManager?.getAllLayers()?.forEach { layer ->
                    layer.strokes.remove(stroke)
                }
                recognizedStrokes.remove(stroke)
                // 删除对应的 RecognizedText
                recognizedTexts.removeAll { it.associatedStrokes.contains(stroke) }
            }
            selectedStrokes.clear()
            redrawCanvas()
        }
        // 然后清除套索
        clearLassoSelection()
        invalidate()
        Log.d("InfiniteCanvasView", "deleteLasso: 删除完成")
    }
    
    // 检查是否有套索
    fun hasLasso(): Boolean {
        return lassoPath != null || lassoRect != null
    }
    
    // 获取选中的笔画数量
    fun getSelectedStrokeCount(): Int = selectedStrokes.size
    
    private fun saveState() {
        val allStrokes = mutableListOf<Stroke>()
        layerManager?.getAllLayers()?.forEach { layer ->
            layer.strokes.forEach { stroke: Stroke ->
                allStrokes.add(stroke.copy())
            }
        }
        undoStack.add(CanvasState(allStrokes))
        redoStack.clear()
        
        // 限制撤销栈大小
        if (undoStack.size > 20) {
            undoStack.removeAt(0)
        }
    }
    
    fun undo() {
        if (undoStack.isEmpty()) return
        
        // 保存当前状态到重做栈
        val allStrokes = mutableListOf<Stroke>()
        layerManager?.getAllLayers()?.forEach { layer ->
            layer.strokes.forEach { allStrokes.add(it.copy()) }
        }
        redoStack.add(CanvasState(allStrokes))
        
        // 恢复上一个状态
        val previousState = undoStack.removeAt(undoStack.size - 1)
        restoreState(previousState)
    }
    
    fun redo() {
        if (redoStack.isEmpty()) return
        
        // 保存当前状态到撤销栈
        val allStrokes = mutableListOf<Stroke>()
        layerManager?.getAllLayers()?.forEach { layer ->
            layer.strokes.forEach { allStrokes.add(it.copy()) }
        }
        undoStack.add(CanvasState(allStrokes))
        
        // 恢复下一个状态
        val nextState = redoStack.removeAt(redoStack.size - 1)
        restoreState(nextState)
    }
    
    private fun restoreState(state: CanvasState) {
        // 清空所有图层
        layerManager?.getAllLayers()?.forEach { layer ->
            layer.strokes.clear()
        }
        
        // 恢复笔画到对应图层
        state.strokes.forEach { stroke ->
            layerManager?.getAllLayers()?.find { it.id == stroke.layerId }?.strokes?.add(stroke)
        }
        
        redrawCanvas()
        invalidate()
    }
    
    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
    
    fun clearCurrentLayer() {
        layerManager?.getCurrentLayer()?.strokes?.clear()
        redrawCanvas()
        invalidate()
    }
    
    private fun redrawCanvas() {
        // 不绘制白色背景，让图片显示出来
        // canvas?.drawColor(Color.WHITE)
        layerManager?.getAllLayers()?.forEach { layer ->
            if (layer.visible) {
                layer.strokes.forEach { stroke: Stroke ->
                    // 只绘制未识别的笔画，已识别的笔画不显示（已转换为美化文字）
                    if (!recognizedStrokes.contains(stroke)) {
                        canvas?.drawPath(stroke.path, stroke.paint)
                    }
                }
            }
        }
    }
}

