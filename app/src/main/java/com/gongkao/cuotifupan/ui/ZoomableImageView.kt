package com.gongkao.cuotifupan.ui

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.ImageView

/**
 * 支持双指缩放和平移的 ImageView
 * 缩放时以手指位置为中心
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private var savedMatrix = Matrix()
    
    // 触摸状态
    private val NONE = 0
    private val DRAG = 1
    private val ZOOM = 2
    private var mode = NONE
    
    // 缩放相关
    private var minScale = 1.0f
    private var maxScale = 5.0f
    private var currentScale = 1.0f
    
    // 触摸点
    private val start = PointF()
    private val mid = PointF()
    private var oldDist = 1f
    
    // 平移边界
    private var matrixValues = FloatArray(9)
    private var viewWidth = 0
    private var viewHeight = 0
    private var drawableWidth = 0
    private var drawableHeight = 0
    
    private val scaleGestureDetector: ScaleGestureDetector
    
    init {
        scaleType = ScaleType.MATRIX
        scaleGestureDetector = ScaleGestureDetector(context, ScaleListener())
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
        resetImageMatrix()
    }
    
    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        drawable?.let {
            drawableWidth = it.intrinsicWidth
            drawableHeight = it.intrinsicHeight
            // 延迟重置矩阵，确保视图尺寸已更新
            post {
                if (viewWidth > 0 && viewHeight > 0) {
                    resetImageMatrix()
                }
            }
        }
    }
    
    /**
     * 重置图片矩阵，使图片居中显示
     */
    private fun resetImageMatrix() {
        if (drawableWidth == 0 || drawableHeight == 0 || viewWidth == 0 || viewHeight == 0) {
            return
        }
        
        matrix.reset()
        currentScale = 1.0f
        
        // 计算初始缩放比例，使图片适应视图
        val scaleX = viewWidth.toFloat() / drawableWidth
        val scaleY = viewHeight.toFloat() / drawableHeight
        val scale = scaleX.coerceAtMost(scaleY)
        
        minScale = scale
        currentScale = scale
        
        // 居中显示
        val dx = (viewWidth - drawableWidth * scale) / 2
        val dy = (viewHeight - drawableHeight * scale) / 2
        
        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)
        
        imageMatrix = matrix
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 先处理缩放手势
        scaleGestureDetector.onTouchEvent(event)
        
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                savedMatrix.set(matrix)
                start.set(event.x, event.y)
                mode = DRAG
                // 如果图片已放大，阻止父视图拦截触摸事件（防止 ViewPager2 滑动）
                if (currentScale > minScale + 0.1f) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                oldDist = spacing(event)
                if (oldDist > 10f) {
                    savedMatrix.set(matrix)
                    midPoint(mid, event)
                    mode = ZOOM
                    // 双指缩放时，始终阻止父视图拦截触摸事件
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                // 恢复允许父视图拦截触摸事件
                if (event.pointerCount <= 1) {
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                if (event.action == MotionEvent.ACTION_UP || event.pointerCount == 1) {
                    mode = NONE
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    // 单指拖动
                    matrix.set(savedMatrix)
                    val dx = event.x - start.x
                    val dy = event.y - start.y
                    
                    // 如果图片已放大，允许拖动并阻止父视图拦截
                    if (currentScale > minScale + 0.1f) {
                        matrix.postTranslate(dx, dy)
                        limitTranslation()
                        parent?.requestDisallowInterceptTouchEvent(true)
                    } else {
                        // 图片未放大，检查是否是水平滑动
                        // 如果是水平滑动，允许 ViewPager2 切换卡片
                        // 如果是垂直滑动，阻止 ViewPager2 拦截（允许滚动）
                        val absDx = kotlin.math.abs(dx)
                        val absDy = kotlin.math.abs(dy)
                        if (absDy > absDx) {
                            // 垂直滑动，阻止父视图拦截
                            parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                } else if (mode == ZOOM && event.pointerCount >= 2) {
                    // 双指缩放（使用 ScaleGestureDetector 处理，这里保留作为备用）
                    val newDist = spacing(event)
                    if (newDist > 10f && oldDist > 10f) {
                        matrix.set(savedMatrix)
                        val scale = newDist / oldDist
                        val newScale = currentScale * scale
                        
                        if (newScale in minScale..maxScale) {
                            // 以两指中点为中心缩放
                            midPoint(mid, event)
                            matrix.postScale(scale, scale, mid.x, mid.y)
                            currentScale = newScale
                            limitTranslation()
                        }
                        // 双指缩放时始终阻止父视图拦截
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
            }
        }
        
        imageMatrix = matrix
        return true
    }
    
    /**
     * 计算两点之间的距离
     */
    private fun spacing(event: MotionEvent): Float {
        if (event.pointerCount < 2) {
            return 0f
        }
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return kotlin.math.sqrt((x * x + y * y).toDouble()).toFloat()
    }
    
    /**
     * 计算两点的中点
     */
    private fun midPoint(point: PointF, event: MotionEvent) {
        if (event.pointerCount < 2) {
            point.set(event.getX(0), event.getY(0))
            return
        }
        val x = event.getX(0) + event.getX(1)
        val y = event.getY(0) + event.getY(1)
        point.set(x / 2, y / 2)
    }
    
    /**
     * 限制平移范围，防止图片移出视图
     */
    private fun limitTranslation() {
        matrix.getValues(matrixValues)
        val transX = matrixValues[Matrix.MTRANS_X]
        val transY = matrixValues[Matrix.MTRANS_Y]
        val scaleX = matrixValues[Matrix.MSCALE_X]
        val scaleY = matrixValues[Matrix.MSCALE_Y]
        
        val scaledWidth = drawableWidth * scaleX
        val scaledHeight = drawableHeight * scaleY
        
        val newTransX: Float
        val newTransY: Float
        
        // 限制X方向平移
        if (scaledWidth <= viewWidth) {
            // 图片宽度小于视图，居中显示
            newTransX = (viewWidth - scaledWidth) / 2
        } else {
            // 图片宽度大于视图，限制在边界内
            newTransX = transX.coerceIn(viewWidth - scaledWidth, 0f)
        }
        
        // 限制Y方向平移
        if (scaledHeight <= viewHeight) {
            // 图片高度小于视图，居中显示
            newTransY = (viewHeight - scaledHeight) / 2
        } else {
            // 图片高度大于视图，限制在边界内
            newTransY = transY.coerceIn(viewHeight - scaledHeight, 0f)
        }
        
        // 应用限制
        if (newTransX != transX || newTransY != transY) {
            matrixValues[Matrix.MTRANS_X] = newTransX
            matrixValues[Matrix.MTRANS_Y] = newTransY
            matrix.setValues(matrixValues)
        }
    }
    
    /**
     * 缩放手势监听器
     */
    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            savedMatrix.set(matrix)
            // 开始缩放时，阻止父视图拦截触摸事件
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newScale = currentScale * scaleFactor
            
            // 限制缩放范围
            if (newScale in minScale..maxScale) {
                matrix.set(savedMatrix)
                // 以手势焦点为中心缩放
                matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                currentScale = newScale
                limitTranslation()
                imageMatrix = matrix
            }
            
            // 缩放过程中始终阻止父视图拦截
            parent?.requestDisallowInterceptTouchEvent(true)
            return true
        }
        
        override fun onScaleEnd(detector: ScaleGestureDetector) {
            // 缩放结束，确保在有效范围内
            if (currentScale < minScale) {
                val scale = minScale / currentScale
                matrix.postScale(scale, scale, viewWidth / 2f, viewHeight / 2f)
                currentScale = minScale
                limitTranslation()
                imageMatrix = matrix
            } else if (currentScale > maxScale) {
                val scale = maxScale / currentScale
                matrix.postScale(scale, scale, viewWidth / 2f, viewHeight / 2f)
                currentScale = maxScale
                limitTranslation()
                imageMatrix = matrix
            }
            
            // 缩放结束后，如果图片已放大，继续阻止父视图拦截（允许拖动）
            // 如果图片未放大，恢复允许父视图拦截（允许 ViewPager2 滑动）
            if (currentScale <= minScale + 0.1f) {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
    }
}
