package com.gongkao.cuotifupan.ui

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.service.FloatingCaptureService
import com.gongkao.cuotifupan.util.BrandDetection

/**
 * 透明框选Activity
 * 让用户在当前屏幕上直接框选要截取的区域
 */
class SelectRegionActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SelectRegionActivity"
        
        // 保存选中的区域（屏幕坐标）
        var selectedRegion: RectF? = null
        
        fun start(context: Context) {
            val intent = Intent(context, SelectRegionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    private lateinit var regionSelectView: RegionSelectView
    private lateinit var btnConfirm: Button
    private lateinit var btnCancel: Button
    private lateinit var hintText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏透明
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
        
        setContentView(R.layout.activity_select_region)

        regionSelectView = findViewById(R.id.regionSelectView)
        btnConfirm = findViewById(R.id.btnConfirmRegion)
        btnCancel = findViewById(R.id.btnCancelRegion)
        hintText = findViewById(R.id.hintText)

        btnConfirm.setOnClickListener {
            val rect = regionSelectView.getSelectedRect()
            if (rect.width() < 50 || rect.height() < 50) {
                hintText.text = "请框选更大的区域"
                return@setOnClickListener
            }
            
            // 保存选中区域
            selectedRegion = rect
            Log.d(TAG, "选中区域: $rect")
            
            // 根据品牌决定是否显示提示
            if (BrandDetection.needsScreenCaptureSelectionHint()) {
                // 小米等品牌需要在权限对话框中选择"整个屏幕"还是"单个应用"，显示提示
                AlertDialog.Builder(this)
                    .setTitle("提示")
                    .setMessage("接下来会弹出权限申请，请选择「整个屏幕」（不要选择「单个应用」）")
                    .setPositiveButton("知道了") { _, _ ->
                        startScreenCapture()
                    }
                    .setCancelable(true)
                    .setOnCancelListener {
                        // 用户取消时恢复悬浮按钮
                        FloatingCaptureService.showFloatingButton()
                    }
                    .show()
            } else {
                // 其他品牌（如vivo、华为、三星、OPPO）不需要选择，直接启动
                startScreenCapture()
            }
        }
        
        btnCancel.setOnClickListener {
            selectedRegion = null
            // 恢复悬浮按钮
            FloatingCaptureService.showFloatingButton()
            finish()
        }
    }
    
    /**
     * 启动截图权限请求
     */
    private fun startScreenCapture() {
        Log.d(TAG, "准备启动截图权限请求")
        // 隐藏当前Activity，然后请求截图
        finish()
        
        // 延迟一下再截图，确保Activity已经完全消失
        android.os.Handler(mainLooper).postDelayed({
            try {
                Log.d(TAG, "启动ScreenCaptureActivity")
                ScreenCaptureActivity.start(this@SelectRegionActivity)
            } catch (e: Exception) {
                Log.e(TAG, "启动ScreenCaptureActivity失败", e)
                e.printStackTrace()
                Toast.makeText(this@SelectRegionActivity, "启动截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                // 恢复悬浮按钮显示
                FloatingCaptureService.showFloatingButton()
            }
        }, 500) // 增加延迟时间，确保Activity完全关闭
    }

    override fun onBackPressed() {
        selectedRegion = null
        FloatingCaptureService.showFloatingButton()
        super.onBackPressed()
    }
}

/**
 * 区域选择View
 * 支持框选后自由调整（移动、调整大小）
 */
class RegionSelectView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val selectedRect = RectF()
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var isCreating = false
    private var dragMode = DragMode.NONE
    
    private val touchRadius = 50f // 触摸半径，用于检测是否点击在控制点上
    private val minCropSize = 50f // 最小裁剪尺寸
    
    private enum class DragMode {
        NONE,           // 无操作
        CREATE,         // 创建新框
        MOVE,           // 移动整个框
        RESIZE_TOP_LEFT,    // 调整左上角
        RESIZE_TOP_RIGHT,   // 调整右上角
        RESIZE_BOTTOM_LEFT, // 调整左下角
        RESIZE_BOTTOM_RIGHT,// 调整右下角
        RESIZE_TOP,         // 调整上边
        RESIZE_BOTTOM,      // 调整下边
        RESIZE_LEFT,        // 调整左边
        RESIZE_RIGHT        // 调整右边
    }
    
    private val rectPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }
    
    private val dimPaint = Paint().apply {
        color = Color.argb(120, 0, 0, 0)
        style = Paint.Style.FILL
    }
    
    private val cornerPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 8f
        isAntiAlias = true
    }
    
    private val cornerFillPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val dashPaint = Paint().apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        if (selectedRect.width() > 10 && selectedRect.height() > 10) {
            // 绘制半透明遮罩（选框外的区域）
            // 上方
            canvas.drawRect(0f, 0f, width.toFloat(), selectedRect.top, dimPaint)
            // 下方
            canvas.drawRect(0f, selectedRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
            // 左方
            canvas.drawRect(0f, selectedRect.top, selectedRect.left, selectedRect.bottom, dimPaint)
            // 右方
            canvas.drawRect(selectedRect.right, selectedRect.top, width.toFloat(), selectedRect.bottom, dimPaint)
            
            // 绘制选框
            canvas.drawRect(selectedRect, rectPaint)
            
            // 绘制四角和边中点控制点
            val cornerSize = 20f
            val halfSize = cornerSize / 2
            
            // 四个角（圆形）
            canvas.drawCircle(selectedRect.left, selectedRect.top, halfSize, cornerFillPaint)
            canvas.drawCircle(selectedRect.right, selectedRect.top, halfSize, cornerFillPaint)
            canvas.drawCircle(selectedRect.left, selectedRect.bottom, halfSize, cornerFillPaint)
            canvas.drawCircle(selectedRect.right, selectedRect.bottom, halfSize, cornerFillPaint)
            
            // 四个边的中点（小圆点）
            canvas.drawCircle(selectedRect.centerX(), selectedRect.top, halfSize * 0.7f, cornerFillPaint)
            canvas.drawCircle(selectedRect.centerX(), selectedRect.bottom, halfSize * 0.7f, cornerFillPaint)
            canvas.drawCircle(selectedRect.left, selectedRect.centerY(), halfSize * 0.7f, cornerFillPaint)
            canvas.drawCircle(selectedRect.right, selectedRect.centerY(), halfSize * 0.7f, cornerFillPaint)
            
            // 绘制三等分线
            val thirdWidth = selectedRect.width() / 3
            val thirdHeight = selectedRect.height() / 3
            canvas.drawLine(selectedRect.left + thirdWidth, selectedRect.top, selectedRect.left + thirdWidth, selectedRect.bottom, dashPaint)
            canvas.drawLine(selectedRect.left + thirdWidth * 2, selectedRect.top, selectedRect.left + thirdWidth * 2, selectedRect.bottom, dashPaint)
            canvas.drawLine(selectedRect.left, selectedRect.top + thirdHeight, selectedRect.right, selectedRect.top + thirdHeight, dashPaint)
            canvas.drawLine(selectedRect.left, selectedRect.top + thirdHeight * 2, selectedRect.right, selectedRect.top + thirdHeight * 2, dashPaint)
        } else {
            // 没有选框时，绘制整体半透明
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), dimPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                
                // 检查是否点击在已有框上
                if (selectedRect.width() > 10 && selectedRect.height() > 10) {
                    dragMode = getDragMode(event.x, event.y)
                    Log.d("RegionSelectView", "ACTION_DOWN: dragMode=$dragMode, rect=${selectedRect}")
                    if (dragMode != DragMode.NONE) {
                        isDragging = true
                        return true
                    }
                }
                
                // 如果没有框或点击在框外，开始创建新框
                if (selectedRect.width() <= 10 || selectedRect.height() <= 10 || !selectedRect.contains(event.x, event.y)) {
                    dragMode = DragMode.CREATE
                    isCreating = true
                    selectedRect.set(event.x, event.y, event.x, event.y)
                    Log.d("RegionSelectView", "ACTION_DOWN: 创建新框 at (${event.x}, ${event.y})")
                    invalidate()
                    return true
                }
                
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging || isCreating) {
                    when (dragMode) {
                        DragMode.CREATE -> {
                            // 创建新框
                            val left = minOf(startX, event.x)
                            val top = minOf(startY, event.y)
                            val right = maxOf(startX, event.x)
                            val bottom = maxOf(startY, event.y)
                            
                            selectedRect.set(
                                left.coerceIn(0f, width.toFloat()),
                                top.coerceIn(0f, height.toFloat()),
                                right.coerceIn(0f, width.toFloat()),
                                bottom.coerceIn(0f, height.toFloat())
                            )
                            Log.d("RegionSelectView", "ACTION_MOVE: CREATE, rect=${selectedRect}")
                        }
                        DragMode.MOVE -> {
                            // 移动整个框
                            val dx = event.x - startX
                            val dy = event.y - startY
                            val newLeft = (selectedRect.left + dx).coerceIn(0f, width.toFloat() - selectedRect.width())
                            val newTop = (selectedRect.top + dy).coerceIn(0f, height.toFloat() - selectedRect.height())
                            val rectWidth = selectedRect.width()
                            val rectHeight = selectedRect.height()
                            selectedRect.set(newLeft, newTop, newLeft + rectWidth, newTop + rectHeight)
                            startX = event.x
                            startY = event.y
                            Log.d("RegionSelectView", "ACTION_MOVE: MOVE, rect=${selectedRect}")
                        }
                        DragMode.RESIZE_TOP_LEFT -> {
                            val newLeft = event.x.coerceIn(0f, selectedRect.right - minCropSize)
                            val newTop = event.y.coerceIn(0f, selectedRect.bottom - minCropSize)
                            selectedRect.set(newLeft, newTop, selectedRect.right, selectedRect.bottom)
                        }
                        DragMode.RESIZE_TOP_RIGHT -> {
                            val newRight = event.x.coerceIn(selectedRect.left + minCropSize, width.toFloat())
                            val newTop = event.y.coerceIn(0f, selectedRect.bottom - minCropSize)
                            selectedRect.set(selectedRect.left, newTop, newRight, selectedRect.bottom)
                        }
                        DragMode.RESIZE_BOTTOM_LEFT -> {
                            val newLeft = event.x.coerceIn(0f, selectedRect.right - minCropSize)
                            val newBottom = event.y.coerceIn(selectedRect.top + minCropSize, height.toFloat())
                            selectedRect.set(newLeft, selectedRect.top, selectedRect.right, newBottom)
                        }
                        DragMode.RESIZE_BOTTOM_RIGHT -> {
                            val newRight = event.x.coerceIn(selectedRect.left + minCropSize, width.toFloat())
                            val newBottom = event.y.coerceIn(selectedRect.top + minCropSize, height.toFloat())
                            selectedRect.set(selectedRect.left, selectedRect.top, newRight, newBottom)
                        }
                        DragMode.RESIZE_TOP -> {
                            val newTop = event.y.coerceIn(0f, selectedRect.bottom - minCropSize)
                            selectedRect.set(selectedRect.left, newTop, selectedRect.right, selectedRect.bottom)
                        }
                        DragMode.RESIZE_BOTTOM -> {
                            val newBottom = event.y.coerceIn(selectedRect.top + minCropSize, height.toFloat())
                            selectedRect.set(selectedRect.left, selectedRect.top, selectedRect.right, newBottom)
                        }
                        DragMode.RESIZE_LEFT -> {
                            val newLeft = event.x.coerceIn(0f, selectedRect.right - minCropSize)
                            selectedRect.set(newLeft, selectedRect.top, selectedRect.right, selectedRect.bottom)
                        }
                        DragMode.RESIZE_RIGHT -> {
                            val newRight = event.x.coerceIn(selectedRect.left + minCropSize, width.toFloat())
                            selectedRect.set(selectedRect.left, selectedRect.top, newRight, selectedRect.bottom)
                        }
                        else -> {}
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isCreating = false
                dragMode = DragMode.NONE
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    /**
     * 获取拖动模式
     */
    private fun getDragMode(x: Float, y: Float): DragMode {
        if (selectedRect.width() <= 10 || selectedRect.height() <= 10) {
            return DragMode.NONE
        }
        
        // 检查是否在四个角上
        if (isNearPoint(x, y, selectedRect.left, selectedRect.top, touchRadius)) {
            return DragMode.RESIZE_TOP_LEFT
        }
        if (isNearPoint(x, y, selectedRect.right, selectedRect.top, touchRadius)) {
            return DragMode.RESIZE_TOP_RIGHT
        }
        if (isNearPoint(x, y, selectedRect.left, selectedRect.bottom, touchRadius)) {
            return DragMode.RESIZE_BOTTOM_LEFT
        }
        if (isNearPoint(x, y, selectedRect.right, selectedRect.bottom, touchRadius)) {
            return DragMode.RESIZE_BOTTOM_RIGHT
        }
        
        // 检查是否在四个边的中点上
        if (isNearPoint(x, y, selectedRect.centerX(), selectedRect.top, touchRadius)) {
            return DragMode.RESIZE_TOP
        }
        if (isNearPoint(x, y, selectedRect.centerX(), selectedRect.bottom, touchRadius)) {
            return DragMode.RESIZE_BOTTOM
        }
        if (isNearPoint(x, y, selectedRect.left, selectedRect.centerY(), touchRadius)) {
            return DragMode.RESIZE_LEFT
        }
        if (isNearPoint(x, y, selectedRect.right, selectedRect.centerY(), touchRadius)) {
            return DragMode.RESIZE_RIGHT
        }
        
        // 检查是否在框内部（移动整个框）
        if (selectedRect.contains(x, y)) {
            return DragMode.MOVE
        }
        
        return DragMode.NONE
    }
    
    /**
     * 检查点是否在指定点附近
     */
    private fun isNearPoint(x: Float, y: Float, px: Float, py: Float, radius: Float): Boolean {
        val dx = x - px
        val dy = y - py
        return dx * dx + dy * dy <= radius * radius
    }

    fun getSelectedRect(): RectF = RectF(selectedRect)
}

