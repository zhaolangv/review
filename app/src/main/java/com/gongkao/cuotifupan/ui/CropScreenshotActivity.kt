package com.gongkao.cuotifupan.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.service.FloatingCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 框选截图Activity
 * 显示截图并让用户框选需要的区域
 */
class CropScreenshotActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CropScreenshotActivity"
        private const val EXTRA_SCREENSHOT_PATH = "screenshot_path"
        
        // 临时存储截图bitmap
        var screenshotBitmap: Bitmap? = null
        
        fun start(context: Context, screenshotPath: String? = null) {
            val intent = Intent(context, CropScreenshotActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                screenshotPath?.let { putExtra(EXTRA_SCREENSHOT_PATH, it) }
            }
            context.startActivity(intent)
        }
    }

    private lateinit var imageView: ImageView
    private lateinit var cropOverlay: CropOverlayView
    private lateinit var btnConfirm: Button
    private lateinit var btnCancel: Button
    
    private var bitmap: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crop_screenshot)

        imageView = findViewById(R.id.screenshotImageView)
        cropOverlay = findViewById(R.id.cropOverlay)
        btnConfirm = findViewById(R.id.btnConfirmCrop)
        btnCancel = findViewById(R.id.btnCancelCrop)

        // 加载截图
        loadScreenshot()

        btnConfirm.setOnClickListener {
            cropAndSave()
        }

        btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun loadScreenshot() {
        // 优先使用内存中的bitmap
        bitmap = screenshotBitmap
        screenshotBitmap = null // 清除引用
        
        if (bitmap == null) {
            // 从文件加载
            val path = intent.getStringExtra(EXTRA_SCREENSHOT_PATH)
            if (path != null) {
                bitmap = BitmapFactory.decodeFile(path)
            }
        }
        
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap)
        } else {
            Toast.makeText(this, "无法加载截图", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun cropAndSave() {
        val bmp = bitmap ?: return
        val cropRect = cropOverlay.getCropRect()
        
        if (cropRect.width() < 50 || cropRect.height() < 50) {
            Toast.makeText(this, "请框选更大的区域", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                // 计算实际裁剪区域（相对于原图）
                val imageViewRect = RectF(0f, 0f, imageView.width.toFloat(), imageView.height.toFloat())
                val bitmapRect = RectF(0f, 0f, bmp.width.toFloat(), bmp.height.toFloat())
                
                // 计算图片在ImageView中的实际显示区域
                val matrix = imageView.imageMatrix
                val values = FloatArray(9)
                matrix.getValues(values)
                val scaleX = values[android.graphics.Matrix.MSCALE_X]
                val scaleY = values[android.graphics.Matrix.MSCALE_Y]
                val transX = values[android.graphics.Matrix.MTRANS_X]
                val transY = values[android.graphics.Matrix.MTRANS_Y]
                
                // 将框选区域转换为bitmap坐标
                val left = ((cropRect.left - transX) / scaleX).toInt().coerceIn(0, bmp.width - 1)
                val top = ((cropRect.top - transY) / scaleY).toInt().coerceIn(0, bmp.height - 1)
                val right = ((cropRect.right - transX) / scaleX).toInt().coerceIn(left + 1, bmp.width)
                val bottom = ((cropRect.bottom - transY) / scaleY).toInt().coerceIn(top + 1, bmp.height)
                
                val width = right - left
                val height = bottom - top
                
                if (width < 10 || height < 10) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CropScreenshotActivity, "选择区域太小", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // 裁剪
                val croppedBitmap = Bitmap.createBitmap(bmp, left, top, width, height)
                
                // 保存到文件
                val savedPath = withContext(Dispatchers.IO) {
                    saveToFile(croppedBitmap)
                }
                
                if (savedPath != null) {
                    // 创建题目
                    createQuestion(savedPath)
                    
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CropScreenshotActivity, "题目已导入", Toast.LENGTH_SHORT).show()
                        croppedBitmap.recycle()
                        finish()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@CropScreenshotActivity, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "裁剪保存失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@CropScreenshotActivity, "处理失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveToFile(bitmap: Bitmap): String? {
        return try {
            val questionsDir = File(filesDir, "questions")
            if (!questionsDir.exists()) {
                questionsDir.mkdirs()
            }
            
            val fileName = "question_${System.currentTimeMillis()}.jpg"
            val file = File(questionsDir, fileName)
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "保存文件失败", e)
            null
        }
    }

    private suspend fun createQuestion(imagePath: String) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@CropScreenshotActivity)
            val question = Question(
                id = UUID.randomUUID().toString(),
                imagePath = imagePath,
                rawText = "",
                questionText = "快速截图导入",
                createdAt = System.currentTimeMillis(),
                reviewState = "unreviewed"
            )
            database.questionDao().insert(question)
            Log.d(TAG, "题目已创建: ${question.id}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        bitmap?.recycle()
        bitmap = null
        // 恢复悬浮按钮显示
        FloatingCaptureService.showFloatingButton()
    }
}

/**
 * 框选覆盖层View
 * 支持框选后自由调整（移动、调整大小）
 */
class CropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val cropRect = RectF()
    private var startX = 0f
    private var startY = 0f
    private var isDragging = false
    private var isCreating = false // 是否正在创建新框
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
        color = Color.argb(150, 0, 0, 0)
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

    init {
        // 初始化默认框选区域（屏幕中心）
        post {
            val margin = 100f
            cropRect.set(margin, margin * 2, width - margin, height - margin * 2)
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 只有在有框的情况下才绘制
        if (cropRect.width() > 0 && cropRect.height() > 0) {
            // 绘制半透明遮罩
            // 上方
            canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
            // 下方
            canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
            // 左方
            canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
            // 右方
            canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)
            
            // 绘制选框
            canvas.drawRect(cropRect, rectPaint)
            
            // 绘制四角和边中点控制点
            val cornerSize = 20f
            val halfSize = cornerSize / 2
            
            // 四个角（圆形）
            canvas.drawCircle(cropRect.left, cropRect.top, halfSize, cornerFillPaint)
            canvas.drawCircle(cropRect.right, cropRect.top, halfSize, cornerFillPaint)
            canvas.drawCircle(cropRect.left, cropRect.bottom, halfSize, cornerFillPaint)
            canvas.drawCircle(cropRect.right, cropRect.bottom, halfSize, cornerFillPaint)
            
            // 四个边的中点（小圆点）
            canvas.drawCircle(cropRect.centerX(), cropRect.top, halfSize * 0.7f, cornerFillPaint)
            canvas.drawCircle(cropRect.centerX(), cropRect.bottom, halfSize * 0.7f, cornerFillPaint)
            canvas.drawCircle(cropRect.left, cropRect.centerY(), halfSize * 0.7f, cornerFillPaint)
            canvas.drawCircle(cropRect.right, cropRect.centerY(), halfSize * 0.7f, cornerFillPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startX = event.x
                startY = event.y
                
                // 检查是否点击在已有框上
                if (cropRect.width() > 0 && cropRect.height() > 0) {
                    dragMode = getDragMode(event.x, event.y)
                    if (dragMode != DragMode.NONE) {
                        isDragging = true
                        return true
                    }
                }
                
                // 如果没有框或点击在框外，开始创建新框
                if (cropRect.width() <= 0 || cropRect.height() <= 0 || !cropRect.contains(event.x, event.y)) {
                    dragMode = DragMode.CREATE
                    isCreating = true
                    cropRect.set(event.x, event.y, event.x, event.y)
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
                            
                            cropRect.set(
                                left.coerceIn(0f, width.toFloat()),
                                top.coerceIn(0f, height.toFloat()),
                                right.coerceIn(0f, width.toFloat()),
                                bottom.coerceIn(0f, height.toFloat())
                            )
                        }
                        DragMode.MOVE -> {
                            // 移动整个框
                            val dx = event.x - startX
                            val dy = event.y - startY
                            val newLeft = (cropRect.left + dx).coerceIn(0f, width.toFloat() - cropRect.width())
                            val newTop = (cropRect.top + dy).coerceIn(0f, height.toFloat() - cropRect.height())
                            val width = cropRect.width()
                            val height = cropRect.height()
                            cropRect.set(newLeft, newTop, newLeft + width, newTop + height)
                            startX = event.x
                            startY = event.y
                        }
                        DragMode.RESIZE_TOP_LEFT -> {
                            val newLeft = event.x.coerceIn(0f, cropRect.right - minCropSize)
                            val newTop = event.y.coerceIn(0f, cropRect.bottom - minCropSize)
                            cropRect.set(newLeft, newTop, cropRect.right, cropRect.bottom)
                        }
                        DragMode.RESIZE_TOP_RIGHT -> {
                            val newRight = event.x.coerceIn(cropRect.left + minCropSize, width.toFloat())
                            val newTop = event.y.coerceIn(0f, cropRect.bottom - minCropSize)
                            cropRect.set(cropRect.left, newTop, newRight, cropRect.bottom)
                        }
                        DragMode.RESIZE_BOTTOM_LEFT -> {
                            val newLeft = event.x.coerceIn(0f, cropRect.right - minCropSize)
                            val newBottom = event.y.coerceIn(cropRect.top + minCropSize, height.toFloat())
                            cropRect.set(newLeft, cropRect.top, cropRect.right, newBottom)
                        }
                        DragMode.RESIZE_BOTTOM_RIGHT -> {
                            val newRight = event.x.coerceIn(cropRect.left + minCropSize, width.toFloat())
                            val newBottom = event.y.coerceIn(cropRect.top + minCropSize, height.toFloat())
                            cropRect.set(cropRect.left, cropRect.top, newRight, newBottom)
                        }
                        DragMode.RESIZE_TOP -> {
                            val newTop = event.y.coerceIn(0f, cropRect.bottom - minCropSize)
                            cropRect.set(cropRect.left, newTop, cropRect.right, cropRect.bottom)
                        }
                        DragMode.RESIZE_BOTTOM -> {
                            val newBottom = event.y.coerceIn(cropRect.top + minCropSize, height.toFloat())
                            cropRect.set(cropRect.left, cropRect.top, cropRect.right, newBottom)
                        }
                        DragMode.RESIZE_LEFT -> {
                            val newLeft = event.x.coerceIn(0f, cropRect.right - minCropSize)
                            cropRect.set(newLeft, cropRect.top, cropRect.right, cropRect.bottom)
                        }
                        DragMode.RESIZE_RIGHT -> {
                            val newRight = event.x.coerceIn(cropRect.left + minCropSize, width.toFloat())
                            cropRect.set(cropRect.left, cropRect.top, newRight, cropRect.bottom)
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
        if (cropRect.width() <= 0 || cropRect.height() <= 0) {
            return DragMode.NONE
        }
        
        // 检查是否在四个角上
        if (isNearPoint(x, y, cropRect.left, cropRect.top, touchRadius)) {
            return DragMode.RESIZE_TOP_LEFT
        }
        if (isNearPoint(x, y, cropRect.right, cropRect.top, touchRadius)) {
            return DragMode.RESIZE_TOP_RIGHT
        }
        if (isNearPoint(x, y, cropRect.left, cropRect.bottom, touchRadius)) {
            return DragMode.RESIZE_BOTTOM_LEFT
        }
        if (isNearPoint(x, y, cropRect.right, cropRect.bottom, touchRadius)) {
            return DragMode.RESIZE_BOTTOM_RIGHT
        }
        
        // 检查是否在四个边的中点上
        if (isNearPoint(x, y, cropRect.centerX(), cropRect.top, touchRadius)) {
            return DragMode.RESIZE_TOP
        }
        if (isNearPoint(x, y, cropRect.centerX(), cropRect.bottom, touchRadius)) {
            return DragMode.RESIZE_BOTTOM
        }
        if (isNearPoint(x, y, cropRect.left, cropRect.centerY(), touchRadius)) {
            return DragMode.RESIZE_LEFT
        }
        if (isNearPoint(x, y, cropRect.right, cropRect.centerY(), touchRadius)) {
            return DragMode.RESIZE_RIGHT
        }
        
        // 检查是否在框内部（移动整个框）
        if (cropRect.contains(x, y)) {
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

    fun getCropRect(): RectF = RectF(cropRect)
}

