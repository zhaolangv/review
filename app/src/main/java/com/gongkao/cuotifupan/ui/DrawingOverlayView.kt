package com.gongkao.cuotifupan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.gongkao.cuotifupan.R
import kotlin.math.abs

/**
 * 绘制覆盖层View，用于在图片上进行手写标注
 * 支持在缩放和平移状态下正确绘制
 */
class DrawingOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 绘制路径
    private val drawingPath = Path()
    private val drawingPaint = Paint().apply {
        isAntiAlias = true
        isDither = true
        color = ContextCompat.getColor(context, android.R.color.holo_red_dark)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 8f
    }

    // 橡皮擦画笔
    private val eraserPaint = Paint().apply {
        isAntiAlias = true
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 20f
    }

    // 当前绘制模式：true=画笔，false=橡皮擦
    private var isDrawingMode = true
    
    /**
     * 获取当前绘制模式
     */
    fun getDrawingMode(): Boolean = isDrawingMode

    // 绘制的所有路径（用于撤销功能）
    private val allPaths = mutableListOf<DrawPath>()

    // 当前正在绘制的路径
    private var currentPath: Path? = null
    private var currentPaint: Paint? = null

    private var startX = 0f
    private var startY = 0f

    // 绘制图层（用于合成最终图片）
    private var drawingBitmap: Bitmap? = null
    private var drawingCanvas: Canvas? = null

    // 画笔颜色
    private var brushColor: Int = ContextCompat.getColor(context, android.R.color.holo_red_dark)

    // 画笔大小（基础值）
    private var brushSize: Float = 8f
    
    // 当前缩放比例（用于调整画笔粗细）
    private var currentScale: Float = 1.0f
    
    // 变换矩阵（用于将屏幕坐标转换为图片坐标）
    private var transformMatrix: Matrix = Matrix()
    private var inverseMatrix: Matrix = Matrix()
    
    // 图片显示区域
    private var imageRect: RectF = RectF()

    data class DrawPath(
        val path: Path,
        val paint: Paint
    )

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null) // 使用软件渲染以支持橡皮擦
        // 设置白色背景，用于手写模式
        setBackgroundColor(android.graphics.Color.WHITE)
    }
    
    /**
     * 设置变换矩阵（用于与PhotoView同步）
     * @param matrix PhotoView的变换矩阵
     * @param scale 当前缩放比例
     */
    fun setTransformMatrix(matrix: Matrix, scale: Float) {
        transformMatrix.set(matrix)
        if (!matrix.invert(inverseMatrix)) {
            inverseMatrix.reset()
        }
        currentScale = scale
        invalidate()
    }
    
    /**
     * 设置图片显示区域
     */
    fun setImageRect(rect: RectF) {
        imageRect.set(rect)
    }

    /**
     * 设置绘制模式
     * @param isDrawing true=画笔模式，false=橡皮擦模式
     */
    fun setDrawingMode(isDrawing: Boolean) {
        isDrawingMode = isDrawing
    }

    /**
     * 设置画笔颜色
     */
    fun setBrushColor(color: Int) {
        brushColor = color
        drawingPaint.color = color
    }

    /**
     * 设置画笔大小
     */
    fun setBrushSize(size: Float) {
        brushSize = size
        drawingPaint.strokeWidth = size
        eraserPaint.strokeWidth = size * 2.5f // 橡皮擦比画笔大一些
    }

    /**
     * 清除所有绘制
     */
    fun clearAll() {
        allPaths.clear()
        drawingPath.reset()
        currentPath = null
        drawingBitmap?.eraseColor(0)
        invalidate()
    }

    /**
     * 撤销上一步
     */
    fun undo() {
        if (allPaths.isNotEmpty()) {
            allPaths.removeAt(allPaths.size - 1)
            drawingBitmap?.eraseColor(0)
            // 重新绘制所有路径
            drawingCanvas?.let { canvas ->
                allPaths.forEach { drawPath ->
                    canvas.drawPath(drawPath.path, drawPath.paint)
                }
            }
            invalidate()
        }
    }

    /**
     * 检查是否有绘制内容
     */
    fun hasDrawing(): Boolean {
        return allPaths.isNotEmpty()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // 创建绘制图层
        if (w > 0 && h > 0) {
            drawingBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawingCanvas = Canvas(drawingBitmap!!)
            drawingCanvas!!.drawColor(0, PorterDuff.Mode.CLEAR)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制所有已保存的路径
        drawingBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }
        
        // 绘制当前正在绘制的路径
        currentPath?.let { path ->
            currentPaint?.let { paint ->
                canvas.drawPath(path, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 获取触摸点坐标
        val touchX = event.x
        val touchY = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = touchX
                startY = touchY
                
                currentPath = Path()
                currentPath?.moveTo(startX, startY)
                
                // 根据缩放比例调整画笔粗细（缩放越大，画笔相对越细）
                currentPaint = if (isDrawingMode) {
                    Paint(drawingPaint).apply {
                        strokeWidth = brushSize / currentScale
                    }
                } else {
                    Paint(eraserPaint).apply {
                        strokeWidth = (brushSize * 2.5f) / currentScale
                    }
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(touchX - startX)
                val dy = abs(touchY - startY)
                
                // 根据缩放调整移动阈值
                val threshold = 4f / currentScale
                if (dx >= threshold || dy >= threshold) {
                    currentPath?.quadTo(startX, startY, (touchX + startX) / 2, (touchY + startY) / 2)
                    startX = touchX
                    startY = touchY
                    invalidate()
                }
            }
            
            MotionEvent.ACTION_UP -> {
                currentPath?.lineTo(startX, startY)
                
                currentPath?.let { path ->
                    currentPaint?.let { paint ->
                        if (isDrawingMode) {
                            // 画笔模式：保存路径并绘制
                            val savedPath = Path(path)
                            val savedPaint = Paint(paint)
                            allPaths.add(DrawPath(savedPath, savedPaint))
                            
                            // 绘制到bitmap上
                            drawingCanvas?.drawPath(path, paint)
                        } else {
                            // 橡皮擦模式：直接清除bitmap上的像素，不保存路径
                            // 清除后需要重新绘制所有路径，确保被擦除的部分不会重新出现
                            drawingCanvas?.drawPath(path, paint)
                            
                            // 重新绘制所有路径，这样被擦除的部分就不会再出现了
                            drawingBitmap?.eraseColor(0)
                            drawingCanvas?.let { canvas ->
                                allPaths.forEach { drawPath ->
                                    canvas.drawPath(drawPath.path, drawPath.paint)
                                }
                            }
                        }
                    }
                }
                
                currentPath = null
                currentPaint = null
                invalidate()
            }
            
            MotionEvent.ACTION_CANCEL -> {
                currentPath = null
                currentPaint = null
                invalidate()
            }
        }
        
        return true
    }

    /**
     * 获取绘制后的Bitmap（透明背景，只包含绘制内容）
     */
    fun getDrawingBitmap(): Bitmap? {
        return drawingBitmap?.copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * 将绘制内容合并到原图上
     */
    fun mergeWithBitmap(originalBitmap: Bitmap): Bitmap {
        val result = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        
        // 先绘制原图
        canvas.drawBitmap(originalBitmap, 0f, 0f, null)
        
        // 再绘制标注
        drawingBitmap?.let { bitmap ->
            canvas.drawBitmap(bitmap, 0f, 0f, null)
        }
        
        return result
    }
    
    /**
     * 加载已有的标注图层
     * @param annotationBitmap 标注图层的Bitmap
     */
    fun loadAnnotation(annotationBitmap: Bitmap?) {
        if (annotationBitmap == null) return
        
        // 确保drawingBitmap已初始化
        if (drawingBitmap == null && width > 0 && height > 0) {
            drawingBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            drawingCanvas = Canvas(drawingBitmap!!)
        }
        
        drawingCanvas?.let { canvas ->
            // 清除当前内容
            canvas.drawColor(0, PorterDuff.Mode.CLEAR)
            
            // 缩放标注到当前View大小
            val scaledBitmap = Bitmap.createScaledBitmap(
                annotationBitmap,
                width,
                height,
                true
            )
            canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
            scaledBitmap.recycle()
        }
        
        invalidate()
    }
    
    /**
     * 获取标注图层的路径数据（用于序列化）
     */
    fun getPathCount(): Int = allPaths.size
    
    /**
     * 导出标注到指定尺寸的Bitmap（用于保存时匹配原图尺寸）
     * @param targetWidth 目标宽度
     * @param targetHeight 目标高度
     */
    fun exportAnnotation(targetWidth: Int, targetHeight: Int): Bitmap? {
        if (width <= 0 || height <= 0) return null
        
        // 创建目标尺寸的Bitmap
        val exportBitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val exportCanvas = Canvas(exportBitmap)
        
        // 计算缩放比例
        val scaleX = targetWidth.toFloat() / width
        val scaleY = targetHeight.toFloat() / height
        
        // 缩放并绘制所有路径
        exportCanvas.save()
        exportCanvas.scale(scaleX, scaleY)
        
        allPaths.forEach { drawPath ->
            exportCanvas.drawPath(drawPath.path, drawPath.paint)
        }
        
        exportCanvas.restore()
        
        return exportBitmap
    }
}

