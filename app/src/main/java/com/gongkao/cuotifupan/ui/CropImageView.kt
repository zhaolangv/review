package com.gongkao.cuotifupan.ui

import android.content.Context
import android.graphics.*
import android.graphics.Region
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * 可裁剪的图片视图
 * 支持手指拖动选择裁剪区域，支持多个裁剪框
 */
class CropImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    private var bitmap: Bitmap? = null
    private var bitmapRect = RectF()
    
    /**
     * 四边形裁剪框（4个顶点）
     * 顺序：左上、右上、右下、左下
     */
    class QuadCropRect(
        var topLeft: PointF,
        var topRight: PointF,
        var bottomRight: PointF,
        var bottomLeft: PointF
    ) {
        fun toPath(): Path {
            val path = Path()
            path.moveTo(topLeft.x, topLeft.y)
            path.lineTo(topRight.x, topRight.y)
            path.lineTo(bottomRight.x, bottomRight.y)
            path.lineTo(bottomLeft.x, bottomLeft.y)
            path.close()
            return path
        }
        
        fun getBoundingRect(): RectF {
            val left = minOf(topLeft.x, topRight.x, bottomRight.x, bottomLeft.x)
            val top = minOf(topLeft.y, topRight.y, bottomRight.y, bottomLeft.y)
            val right = maxOf(topLeft.x, topRight.x, bottomRight.x, bottomLeft.x)
            val bottom = maxOf(topLeft.y, topRight.y, bottomRight.y, bottomLeft.y)
            return RectF(left, top, right, bottom)
        }
        
        fun contains(x: Float, y: Float): Boolean {
            // 使用射线法判断点是否在四边形内
            val path = toPath()
            val region = Region()
            val clipBounds = Rect()
            path.computeBounds(RectF(), true)
            val bounds = RectF()
            path.computeBounds(bounds, true)
            clipBounds.set(
                bounds.left.toInt(),
                bounds.top.toInt(),
                bounds.right.toInt(),
                bounds.bottom.toInt()
            )
            region.setPath(path, Region(clipBounds))
            return region.contains(x.toInt(), y.toInt())
        }
    }
    
    private val cropRects = mutableListOf<QuadCropRect>() // 多个四边形裁剪框
    private var selectedCropIndex = -1 // 当前选中的裁剪框索引
    
    private var cropMode = false // 是否处于裁剪模式
    
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.WHITE
    }
    
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.YELLOW // 选中的裁剪框用黄色高亮
    }
    
    private val dimPaint = Paint().apply {
        color = Color.argb(128, 0, 0, 0) // 半透明黑色遮罩
    }
    
    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    
    private val selectedCornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.YELLOW // 选中的裁剪框控制点用黄色
    }
    
    private var isDragging = false
    private var dragMode = DragMode.NONE
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    private val cornerSize = 40f // 角落控制点大小
    private val minCropSize = 100f // 最小裁剪尺寸
    
    enum class DragMode {
        NONE,
        MOVE,           // 移动整个裁剪框
        DRAG_TOP_LEFT,      // 拖动左上角
        DRAG_TOP_RIGHT,     // 拖动右上角
        DRAG_BOTTOM_LEFT,    // 拖动左下角
        DRAG_BOTTOM_RIGHT   // 拖动右下角
    }
    
    fun setBitmap(bitmap: Bitmap?) {
        this.bitmap = bitmap
        if (bitmap != null) {
        calculateBitmapRect()
        } else {
            bitmapRect.setEmpty()
        }
        Log.d("CropImageView", "setBitmap: 清空前 cropRects.size=${cropRects.size}")
        // 设置新图片时，清空所有旧的裁剪框，不自动创建初始裁剪框
        // 用户需要手动点击"添加裁剪框"按钮来创建裁剪框
        cropRects.clear()
        selectedCropIndex = -1
        Log.d("CropImageView", "setBitmap: 清空后 cropRects.size=${cropRects.size}")
        invalidate()
    }
    
    private fun calculateBitmapRect() {
        if (bitmap == null) return
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val bitmapWidth = bitmap!!.width.toFloat()
        val bitmapHeight = bitmap!!.height.toFloat()
        
        val scale = min(viewWidth / bitmapWidth, viewHeight / bitmapHeight)
        val scaledWidth = bitmapWidth * scale
        val scaledHeight = bitmapHeight * scale
        
        val left = (viewWidth - scaledWidth) / 2
        val top = (viewHeight - scaledHeight) / 2
        
        bitmapRect.set(left, top, left + scaledWidth, top + scaledHeight)
    }
    
    private fun initializeCropRect() {
        // 初始裁剪框为图片的80%大小，居中（矩形）
        val margin = 0.1f
        val width = bitmapRect.width() * (1 - 2 * margin)
        val height = bitmapRect.height() * (1 - 2 * margin)
        val left = bitmapRect.left + bitmapRect.width() * margin
        val top = bitmapRect.top + bitmapRect.height() * margin
        
        Log.d("CropImageView", "initializeCropRect: 清空前数量=${cropRects.size}")
        cropRects.clear()
        cropRects.add(QuadCropRect(
            topLeft = PointF(left, top),
            topRight = PointF(left + width, top),
            bottomRight = PointF(left + width, top + height),
            bottomLeft = PointF(left, top + height)
        ))
        selectedCropIndex = 0
        Log.d("CropImageView", "initializeCropRect: 创建后数量=${cropRects.size}")
    }
    
    private val pendingAutoCropRegions = mutableListOf<Rect>() // 待处理的自动裁剪区域

    /**
     * 批量设置裁剪框（用于自动检测题目区域）
     * @param regions 每道题的区域列表（Bitmap 坐标）
     */
    fun setAutoCropRegions(regions: List<Rect>) {
        if (bitmap == null || regions.isEmpty()) {
            Log.d("CropImageView", "setAutoCropRegions: bitmap 为空或 regions 为空")
            return
        }
        
        // 检查 bitmapRect 是否已经计算（布局是否完成）
        if (bitmapRect.width() <= 0 || bitmapRect.height() <= 0) {
            Log.d("CropImageView", "setAutoCropRegions: 布局未完成，暂存 ${regions.size} 个区域")
            pendingAutoCropRegions.clear()
            pendingAutoCropRegions.addAll(regions)
            return
        }
        
        applyAutoCropRegions(regions)
    }
    
    private fun applyAutoCropRegions(regions: List<Rect>) {
        Log.d("CropImageView", "applyAutoCropRegions: 设置 ${regions.size} 个自动检测区域")
        
        // 清空现有裁剪框
        cropRects.clear()
        
        // 将 Bitmap 坐标转换为 View 坐标，并创建裁剪框
        regions.forEach { rect ->
            val viewRect = bitmapRectToViewRect(rect)
            
            // 验证区域有效性
            // 1. 检查顶部是否超出照片（如果top < bitmapRect.top，则过滤掉）
            if (viewRect.top < bitmapRect.top) {
                Log.w("CropImageView", "过滤顶部超出照片的裁剪框: top=${viewRect.top}, bitmapRect.top=${bitmapRect.top}")
                return@forEach
            }
            
            // 2. 检查基本尺寸
            if (viewRect.width() > 20 && viewRect.height() > 20) {
                val quadRect = QuadCropRect(
                    topLeft = PointF(viewRect.left, viewRect.top),
                    topRight = PointF(viewRect.right, viewRect.top),
                    bottomRight = PointF(viewRect.right, viewRect.bottom),
                    bottomLeft = PointF(viewRect.left, viewRect.bottom)
                )
                cropRects.add(quadRect)
                Log.d("CropImageView", "添加裁剪框: ${viewRect.left}, ${viewRect.top}, ${viewRect.right}, ${viewRect.bottom}")
            } else {
                Log.w("CropImageView", "忽略无效裁剪框: width=${viewRect.width()}, height=${viewRect.height()}")
            }
        }
        
        // 选中第一个裁剪框
        if (cropRects.isNotEmpty()) {
            selectedCropIndex = 0
        } else {
            selectedCropIndex = -1
        }
        
        Log.d("CropImageView", "applyAutoCropRegions: 成功创建 ${cropRects.size} 个裁剪框（过滤后）")
        invalidate()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateBitmapRect()
        Log.d("CropImageView", "onSizeChanged: cropRects.size=${cropRects.size}")
        
        // 如果有待处理的自动裁剪区域，现在应用
        if (pendingAutoCropRegions.isNotEmpty()) {
            Log.d("CropImageView", "onSizeChanged: 应用暂存的 ${pendingAutoCropRegions.size} 个区域")
            applyAutoCropRegions(pendingAutoCropRegions)
            pendingAutoCropRegions.clear()
        }
    }

    /**
     * 将 Bitmap 坐标转换为 View 坐标
     */
    private fun bitmapRectToViewRect(rect: Rect): RectF {
        val bitmap = this.bitmap ?: return RectF()
        
        // 检查 bitmap 是否已被回收
        if (bitmap.isRecycled) {
            Log.w("CropImageView", "⚠️ Bitmap 已被回收，无法转换坐标")
            return RectF()
        }
        
        if (bitmapRect.width() <= 0 || bitmapRect.height() <= 0) return RectF()
        
        val scaleX = bitmapRect.width() / bitmap.width
        val scaleY = bitmapRect.height() / bitmap.height
        
        return RectF(
            bitmapRect.left + rect.left * scaleX,
            bitmapRect.top + rect.top * scaleY,
            bitmapRect.left + rect.right * scaleX,
            bitmapRect.top + rect.bottom * scaleY
        )
    }
    
    /**
     * 获取当前 Bitmap
     */
    fun getCurrentBitmap(): Bitmap? = bitmap
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val bitmap = this.bitmap ?: return
        
        // 检查 bitmap 是否已被回收
        if (bitmap.isRecycled) {
            Log.w("CropImageView", "⚠️ Bitmap 已被回收，无法绘制")
            return
        }
        
        // 绘制图片
        try {
        canvas.drawBitmap(bitmap, null, bitmapRect, null)
        } catch (e: RuntimeException) {
            if (e.message?.contains("recycled") == true) {
                Log.e("CropImageView", "❌ 尝试绘制已回收的 Bitmap", e)
                return
            }
            throw e
        }
        
        // 只有在裁剪模式下才显示裁剪框
        if (cropMode && cropRects.isNotEmpty()) {
            // 绘制遮罩（所有裁剪区域外的部分）
            drawDimmedArea(canvas)
            
            // 绘制所有裁剪框
            cropRects.forEachIndexed { index, quadRect ->
                val isSelected = index == selectedCropIndex
                val rectPaint = if (isSelected) selectedPaint else paint
                
                // 绘制四边形边框
                val path = quadRect.toPath()
                canvas.drawPath(path, rectPaint)
                
                // 绘制角落控制点（只有选中的裁剪框显示控制点）
                if (isSelected) {
                    drawCorners(canvas, quadRect)
                }
            }
        }
    }
    
    private fun drawDimmedArea(canvas: Canvas) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // 使用 Path.op 来创建遮罩效果
            val outerPath = Path().apply {
                addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            }
            
            // 为每个裁剪区域创建遮罩（从外部矩形中减去裁剪区域）
            cropRects.forEach { quadRect ->
                val cropPath = quadRect.toPath()
                outerPath.op(cropPath, Path.Op.DIFFERENCE)
            }
            
            canvas.drawPath(outerPath, dimPaint)
        } else {
            // 对于旧版本，绘制整个视图的遮罩，然后绘制裁剪区域（使用 CLEAR 模式）
            // 但这种方式比较复杂，这里简化为只绘制外部遮罩
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        }
    }
    
    private fun drawCorners(canvas: Canvas, quadRect: QuadCropRect) {
        val halfSize = cornerSize / 2
        
        // 四个顶点
        canvas.drawCircle(quadRect.topLeft.x, quadRect.topLeft.y, halfSize, selectedCornerPaint)
        canvas.drawCircle(quadRect.topRight.x, quadRect.topRight.y, halfSize, selectedCornerPaint)
        canvas.drawCircle(quadRect.bottomRight.x, quadRect.bottomRight.y, halfSize, selectedCornerPaint)
        canvas.drawCircle(quadRect.bottomLeft.x, quadRect.bottomLeft.y, halfSize, selectedCornerPaint)
    }
    
    private fun getSelectedCropRect(): QuadCropRect? {
        return if (selectedCropIndex >= 0 && selectedCropIndex < cropRects.size) {
            cropRects[selectedCropIndex]
        } else {
            null
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 只有在裁剪模式下才处理触摸事件
        if (!cropMode) {
            return false
        }
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                
                // 优先检查是否在选中的裁剪框的顶点上（拖动顶点优先级最高）
                if (selectedCropIndex >= 0) {
                    dragMode = getDragMode(event.x, event.y)
                    if (dragMode != DragMode.NONE && dragMode != DragMode.MOVE) {
                        // 如果是在拖动顶点，直接开始拖动
                        isDragging = true
                        return true
                    }
                }
                
                // 再检查是否点击了某个裁剪框（切换选中）
                val clickedIndex = findCropRectAt(event.x, event.y)
                if (clickedIndex >= 0 && clickedIndex != selectedCropIndex) {
                    selectedCropIndex = clickedIndex
                    invalidate()
                    return true
                }
                
                // 最后检查是否在选中的裁剪框内部（移动整个裁剪框）
                if (selectedCropIndex >= 0) {
                    dragMode = getDragMode(event.x, event.y)
                    isDragging = dragMode != DragMode.NONE
                    return isDragging
                }
                
                return false
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isDragging && selectedCropIndex >= 0) {
                    val dx = event.x - lastTouchX
                    val dy = event.y - lastTouchY
                    val quadRect = cropRects[selectedCropIndex]
                    
                    when (dragMode) {
                        DragMode.MOVE -> {
                            // 移动整个裁剪框
                            moveQuadCropRect(quadRect, dx, dy)
                        }
                        DragMode.DRAG_TOP_LEFT -> {
                            // 拖动左上角
                            quadRect.topLeft.x += dx
                            quadRect.topLeft.y += dy
                            constrainPointToBitmap(quadRect.topLeft)
                        }
                        DragMode.DRAG_TOP_RIGHT -> {
                            // 拖动右上角
                            quadRect.topRight.x += dx
                            quadRect.topRight.y += dy
                            constrainPointToBitmap(quadRect.topRight)
                        }
                        DragMode.DRAG_BOTTOM_RIGHT -> {
                            // 拖动右下角
                            quadRect.bottomRight.x += dx
                            quadRect.bottomRight.y += dy
                            constrainPointToBitmap(quadRect.bottomRight)
                        }
                        DragMode.DRAG_BOTTOM_LEFT -> {
                            // 拖动左下角
                            quadRect.bottomLeft.x += dx
                            quadRect.bottomLeft.y += dy
                            constrainPointToBitmap(quadRect.bottomLeft)
                        }
                        else -> {}
                    }
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                    invalidate()
                    return true
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                dragMode = DragMode.NONE
                return true
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    /**
     * 查找点击位置所在的裁剪框索引
     */
    private fun findCropRectAt(x: Float, y: Float): Int {
        // 从后往前查找（后添加的在上面）
        for (i in cropRects.size - 1 downTo 0) {
            val quadRect = cropRects[i]
            // 扩大点击区域，方便点击
            val touchRadius = cornerSize * 2
            val boundingRect = quadRect.getBoundingRect()
            val expandedRect = RectF(
                boundingRect.left - touchRadius,
                boundingRect.top - touchRadius,
                boundingRect.right + touchRadius,
                boundingRect.bottom + touchRadius
            )
            if (expandedRect.contains(x, y) && quadRect.contains(x, y)) {
                return i
            }
        }
        return -1
    }
    
    private fun getDragMode(x: Float, y: Float): DragMode {
        val quadRect = getSelectedCropRect() ?: return DragMode.NONE
        // 增大触摸半径，提高灵敏度
        val touchRadius = cornerSize * 1.5f
        
        // 优先检查是否在四个顶点（使用更大的触摸半径）
        // 按距离排序，选择最近的顶点
        val corners = listOf(
            Pair(quadRect.topLeft, DragMode.DRAG_TOP_LEFT),
            Pair(quadRect.topRight, DragMode.DRAG_TOP_RIGHT),
            Pair(quadRect.bottomRight, DragMode.DRAG_BOTTOM_RIGHT),
            Pair(quadRect.bottomLeft, DragMode.DRAG_BOTTOM_LEFT)
        )
        
        var closestCorner: Pair<PointF, DragMode>? = null
        var minDistance = Float.MAX_VALUE
        
        corners.forEach { (point, mode) ->
            val distance = kotlin.math.sqrt(
                (x - point.x) * (x - point.x) + (y - point.y) * (y - point.y)
            )
            if (distance <= touchRadius && distance < minDistance) {
                minDistance = distance
                closestCorner = Pair(point, mode)
            }
        }
        
        if (closestCorner != null) {
            return closestCorner!!.second
        }
        
        // 检查是否在裁剪框内部（移动整个裁剪框）
        if (quadRect.contains(x, y)) {
            return DragMode.MOVE
        }
        
        return DragMode.NONE
    }
    
    private fun isNearPoint(x: Float, y: Float, px: Float, py: Float, radius: Float): Boolean {
        val dx = x - px
        val dy = y - py
        return dx * dx + dy * dy <= radius * radius
    }
    
    /**
     * 将点约束到图片边界内
     */
    private fun constrainPointToBitmap(point: PointF) {
        point.x = point.x.coerceIn(bitmapRect.left, bitmapRect.right)
        point.y = point.y.coerceIn(bitmapRect.top, bitmapRect.bottom)
    }
    
    /**
     * 移动整个四边形裁剪框
     */
    private fun moveQuadCropRect(quadRect: QuadCropRect, dx: Float, dy: Float) {
        // 计算移动后的边界框
        val boundingRect = quadRect.getBoundingRect()
        val newLeft = boundingRect.left + dx
        val newTop = boundingRect.top + dy
        val newRight = boundingRect.right + dx
        val newBottom = boundingRect.bottom + dy
        
        // 检查是否超出图片边界
        if (newLeft >= bitmapRect.left && newRight <= bitmapRect.right &&
            newTop >= bitmapRect.top && newBottom <= bitmapRect.bottom) {
            // 移动所有顶点
            quadRect.topLeft.x += dx
            quadRect.topLeft.y += dy
            quadRect.topRight.x += dx
            quadRect.topRight.y += dy
            quadRect.bottomRight.x += dx
            quadRect.bottomRight.y += dy
            quadRect.bottomLeft.x += dx
            quadRect.bottomLeft.y += dy
        }
    }
    
    /**
     * 设置裁剪模式
     */
    fun setCropMode(enabled: Boolean) {
        cropMode = enabled
        Log.d("CropImageView", "setCropMode: enabled=$enabled, cropRects.size=${cropRects.size}")
        // 不再自动创建初始裁剪框，用户需要手动点击"添加裁剪框"按钮
        // if (cropMode && cropRects.isEmpty()) {
        //     Log.d("CropImageView", "setCropMode: 裁剪框为空，初始化裁剪框")
        //     initializeCropRect()
        // }
        invalidate()
    }
    
    /**
     * 添加新的裁剪框
     */
    fun addCropRect() {
        if (bitmap == null) return
        
        // 创建新的裁剪框，默认大小为图片的60%，位置稍微偏移避免重叠
        val margin = 0.2f
        val width = bitmapRect.width() * 0.6f
        val height = bitmapRect.height() * 0.6f
        
        // 计算位置，避免与现有裁剪框重叠
        val offsetX = cropRects.size * 50f // 每个裁剪框偏移50像素
        val offsetY = cropRects.size * 50f
        val left = (bitmapRect.left + bitmapRect.width() * margin + offsetX).coerceAtMost(bitmapRect.right - width)
        val top = (bitmapRect.top + bitmapRect.height() * margin + offsetY).coerceAtMost(bitmapRect.bottom - height)
        
        val newQuadRect = QuadCropRect(
            topLeft = PointF(left, top),
            topRight = PointF(left + width, top),
            bottomRight = PointF(left + width, top + height),
            bottomLeft = PointF(left, top + height)
        )
        cropRects.add(newQuadRect)
        selectedCropIndex = cropRects.size - 1
        Log.d("CropImageView", "添加裁剪框，当前数量: ${cropRects.size}")
        invalidate()
    }
    
    /**
     * 删除当前选中的裁剪框
     */
    fun removeSelectedCropRect(): Boolean {
        if (selectedCropIndex >= 0 && selectedCropIndex < cropRects.size) {
            cropRects.removeAt(selectedCropIndex)
            // 调整选中索引
            if (cropRects.isEmpty()) {
                selectedCropIndex = -1
            } else if (selectedCropIndex >= cropRects.size) {
                selectedCropIndex = cropRects.size - 1
            }
            Log.d("CropImageView", "删除裁剪框，当前数量: ${cropRects.size}")
            invalidate()
            return true
        }
        return false
    }
    
    /**
     * 获取裁剪框数量
     */
    fun getCropRectCount(): Int {
        val count = cropRects.size
        Log.d("CropImageView", "获取裁剪框数量: $count")
        return count
    }
    
    /**
     * 是否有选中的裁剪框
     */
    fun hasSelectedCropRect(): Boolean = selectedCropIndex >= 0 && selectedCropIndex < cropRects.size
    
    /**
     * 获取当前选中的裁剪框索引
     */
    fun getSelectedCropIndex(): Int = selectedCropIndex
    
    /**
     * 进入裁剪模式
     */
    fun enterCropMode() {
        setCropMode(true)
        // 如果进入裁剪模式时没有裁剪框，自动创建一个初始裁剪框
        if (cropMode && cropRects.isEmpty() && bitmap != null && bitmapRect.width() > 0 && bitmapRect.height() > 0) {
            Log.d("CropImageView", "进入裁剪模式，自动创建初始裁剪框")
            addCropRect()
        }
    }
    
    /**
     * 退出裁剪模式
     */
    fun exitCropMode() {
        setCropMode(false)
    }
    
    /**
     * 是否处于裁剪模式
     */
    fun isInCropMode(): Boolean = cropMode
    
    /**
     * 获取所有裁剪后的Bitmap列表
     * 使用透视变换将四边形裁剪为矩形
     */
    fun getAllCroppedBitmaps(): List<Bitmap> {
        if (bitmap == null || cropRects.isEmpty()) return emptyList()
        
        val bitmaps = mutableListOf<Bitmap>()
        val sourceBitmap = this.bitmap!!
        val scaleX = sourceBitmap.width / bitmapRect.width()
        val scaleY = sourceBitmap.height / bitmapRect.height()
        
        Log.d("CropImageView", "开始生成裁剪图片，裁剪框数量: ${cropRects.size}")
        
        cropRects.forEachIndexed { index, quadRect ->
            Log.d("CropImageView", "处理第 ${index + 1} 个裁剪框")
            try {
                // 将视图坐标转换为原始图片坐标
                val srcPoints = floatArrayOf(
                    (quadRect.topLeft.x - bitmapRect.left) * scaleX,
                    (quadRect.topLeft.y - bitmapRect.top) * scaleY,
                    (quadRect.topRight.x - bitmapRect.left) * scaleX,
                    (quadRect.topRight.y - bitmapRect.top) * scaleY,
                    (quadRect.bottomRight.x - bitmapRect.left) * scaleX,
                    (quadRect.bottomRight.y - bitmapRect.top) * scaleY,
                    (quadRect.bottomLeft.x - bitmapRect.left) * scaleX,
                    (quadRect.bottomLeft.y - bitmapRect.top) * scaleY
                )
                
                // 约束到图片边界
                for (i in srcPoints.indices step 2) {
                    srcPoints[i] = srcPoints[i].coerceIn(0f, sourceBitmap.width.toFloat())
                    srcPoints[i + 1] = srcPoints[i + 1].coerceIn(0f, sourceBitmap.height.toFloat())
                }
                
                // 计算目标矩形的尺寸（使用边界框）
                val boundingRect = quadRect.getBoundingRect()
                val dstWidth = ((boundingRect.width()) * scaleX).toInt().coerceAtLeast(100)
                val dstHeight = ((boundingRect.height()) * scaleY).toInt().coerceAtLeast(100)
                
                // 目标矩形（矩形）
                val dstPoints = floatArrayOf(
                    0f, 0f,
                    dstWidth.toFloat(), 0f,
                    dstWidth.toFloat(), dstHeight.toFloat(),
                    0f, dstHeight.toFloat()
                )
                
                // 使用Matrix进行透视变换
                val matrix = Matrix()
                val success = matrix.setPolyToPoly(srcPoints, 0, dstPoints, 0, 4)
                
                if (success && dstWidth > 0 && dstHeight > 0) {
                    try {
                        val cropped = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(cropped)
                        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
                        canvas.drawBitmap(sourceBitmap, matrix, paint)
                        bitmaps.add(cropped)
                        Log.d("CropImageView", "✅ 第 ${index + 1} 个裁剪框生成成功，尺寸: ${dstWidth}x${dstHeight}")
                    } catch (e: Exception) {
                        Log.e("CropImageView", "❌ 第 ${index + 1} 个裁剪框创建Bitmap失败", e)
                    }
                } else {
                    Log.w("CropImageView", "❌ 第 ${index + 1} 个裁剪框透视变换失败或尺寸无效: success=$success, width=$dstWidth, height=$dstHeight")
                }
            } catch (e: Exception) {
                Log.e("CropImageView", "❌ 第 ${index + 1} 个裁剪框处理失败", e)
                // 忽略单个裁剪框的错误，继续处理其他
            }
        }
        
        Log.d("CropImageView", "裁剪图片生成完成，共生成 ${bitmaps.size} 张图片")
        return bitmaps
    }
    
    /**
     * 获取当前选中裁剪框的Bitmap（兼容旧接口）
     */
    fun getCroppedBitmap(): Bitmap? {
        val allBitmaps = getAllCroppedBitmaps()
        return if (selectedCropIndex >= 0 && selectedCropIndex < allBitmaps.size) {
            allBitmaps[selectedCropIndex]
        } else {
            allBitmaps.firstOrNull()
        }
    }
    
    /**
     * 获取裁剪区域（相对于原始图片的坐标）- 用于选中的裁剪框（返回边界框）
     */
    fun getCropRect(): Rect {
        val selectedRect = getSelectedCropRect() ?: return Rect()
        if (bitmap == null) return Rect()
        
        val bitmap = this.bitmap!!
        val scaleX = bitmap.width / bitmapRect.width()
        val scaleY = bitmap.height / bitmapRect.height()
        
        val boundingRect = selectedRect.getBoundingRect()
        val left = ((boundingRect.left - bitmapRect.left) * scaleX).toInt()
        val top = ((boundingRect.top - bitmapRect.top) * scaleY).toInt()
        val right = ((boundingRect.right - bitmapRect.left) * scaleX).toInt()
        val bottom = ((boundingRect.bottom - bitmapRect.top) * scaleY).toInt()
        
        return Rect(
            left.coerceAtLeast(0).coerceAtMost(bitmap.width),
            top.coerceAtLeast(0).coerceAtMost(bitmap.height),
            right.coerceAtLeast(0).coerceAtMost(bitmap.width),
            bottom.coerceAtLeast(0).coerceAtMost(bitmap.height)
        )
    }
}
