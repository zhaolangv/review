package com.gongkao.cuotifupan.ui

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.ContentResolver
import android.content.IntentSender
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.util.ImageEditor
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.viewmodel.QuestionViewModel
import androidx.lifecycle.ViewModelProvider
import android.widget.ImageButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 图片编辑 Activity
 * 支持裁剪和旋转功能
 */
class ImageEditActivity : AppCompatActivity() {
    
    private lateinit var cropImageView: CropImageView
    private lateinit var rotateButton: Button
    private lateinit var cropButton: Button
    private lateinit var annotationButton: Button
    private lateinit var saveButton: Button
    private lateinit var cancelButton: Button
    private lateinit var cancelCropButton: Button
    private lateinit var applyCropButton: Button
    private lateinit var cropModeButtons: android.view.View
    private lateinit var progressBar: ProgressBar
    
    // 标注相关
    private lateinit var drawingOverlayView: DrawingOverlayView
    private lateinit var drawingToolbar: View
    private lateinit var btnDraw: ImageButton
    private lateinit var btnEraser: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var btnSaveDrawing: ImageButton
    private lateinit var btnCloseDrawing: ImageButton
    private lateinit var btnClearAnnotation: ImageButton
    private lateinit var colorRed: View
    private lateinit var colorBlue: View
    private lateinit var colorGreen: View
    private lateinit var colorBlack: View
    
    private var imagePath: String? = null
    private var originalImagePath: String? = null  // 保存原始图片路径
    private var currentBitmap: Bitmap? = null
    private var rotationDegrees = 0
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    
    // 标注相关
    private var questionId: String? = null
    private var currentQuestion: Question? = null
    private var isDrawingMode = false // 是否处于绘制模式
    private lateinit var viewModel: QuestionViewModel
    
    // 用于保存替换原图时的参数，以便权限授予后重试
    private var pendingReplaceOriginal: String? = null
    private var pendingEditedPath: String? = null
    
    // 权限请求 launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // 权限授予后，重试替换原图
            pendingReplaceOriginal?.let { original ->
                pendingEditedPath?.let { edited ->
                    pendingReplaceOriginal = null
                    pendingEditedPath = null
                    replaceOriginalImage(original, edited)
                }
            }
        } else {
            // 权限被拒绝，保存为新文件
            Toast.makeText(this, "需要存储权限才能替换原图，已保存为新文件", Toast.LENGTH_LONG).show()
            pendingReplaceOriginal?.let { original ->
                pendingEditedPath?.let { edited ->
                    pendingReplaceOriginal = null
                    pendingEditedPath = null
                    keepOriginalImage(edited)
                }
            }
        }
    }
    
    // RecoverableSecurityException 请求 launcher（Android 10+）
    private val requestRecoverableSecurityExceptionLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // 用户确认后，重试替换原图
            pendingReplaceOriginal?.let { original ->
                pendingEditedPath?.let { edited ->
                    pendingReplaceOriginal = null
                    pendingEditedPath = null
                    replaceOriginalImage(original, edited)
                }
            }
        } else {
            // 用户拒绝，保存为新文件
            Toast.makeText(this, "需要权限才能替换原图，已保存为新文件", Toast.LENGTH_LONG).show()
            pendingReplaceOriginal?.let { original ->
                pendingEditedPath?.let { edited ->
                    pendingReplaceOriginal = null
                    pendingEditedPath = null
                    keepOriginalImage(edited)
                }
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_edit)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "编辑图片"
        
        originalImagePath = intent.getStringExtra(EXTRA_IMAGE_PATH)
        questionId = intent.getStringExtra("question_id")
        imagePath = originalImagePath
        if (imagePath == null) {
            Toast.makeText(this, "图片路径不存在", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 初始化ViewModel（用于加载题目信息）
        viewModel = ViewModelProvider(this)[QuestionViewModel::class.java]
        
        initViews()
        loadImage()
        loadQuestion()
    }
    
    private fun initViews() {
        cropImageView = findViewById(R.id.cropImageView)
        rotateButton = findViewById(R.id.rotateButton)
        cropButton = findViewById(R.id.cropButton)
        annotationButton = findViewById(R.id.annotationButton)
        saveButton = findViewById(R.id.saveButton)
        cancelButton = findViewById(R.id.cancelButton)
        cancelCropButton = findViewById(R.id.cancelCropButton)
        applyCropButton = findViewById(R.id.applyCropButton)
        cropModeButtons = findViewById(R.id.cropModeButtons)
        progressBar = findViewById(R.id.progressBar)
        
        // 初始化标注相关视图
        drawingOverlayView = findViewById(R.id.drawingOverlayView)
        drawingToolbar = findViewById(R.id.drawingToolbar)
        btnDraw = findViewById(R.id.btnDraw)
        btnEraser = findViewById(R.id.btnEraser)
        btnUndo = findViewById(R.id.btnUndo)
        btnClear = findViewById(R.id.btnClear)
        btnSaveDrawing = findViewById(R.id.btnSaveDrawing)
        btnCloseDrawing = findViewById(R.id.btnCloseDrawing)
        btnClearAnnotation = findViewById(R.id.btnClearAnnotation)
        colorRed = findViewById(R.id.colorRed)
        colorBlue = findViewById(R.id.colorBlue)
        colorGreen = findViewById(R.id.colorGreen)
        colorBlack = findViewById(R.id.colorBlack)
        
        // 设置标注工具栏
        setupDrawingToolbar()
        
        rotateButton.setOnClickListener {
            if (cropImageView.isInCropMode()) {
                Toast.makeText(this, "请先完成或取消裁剪", Toast.LENGTH_SHORT).show()
            } else if (isDrawingMode) {
                Toast.makeText(this, "请先退出标注模式", Toast.LENGTH_SHORT).show()
            } else {
                rotateImage()
            }
        }
        
        cropButton.setOnClickListener {
            if (isDrawingMode) {
                Toast.makeText(this, "请先退出标注模式", Toast.LENGTH_SHORT).show()
            } else {
            enterCropMode()
            }
        }
        
        annotationButton.setOnClickListener {
            if (cropImageView.isInCropMode()) {
                Toast.makeText(this, "请先完成或取消裁剪", Toast.LENGTH_SHORT).show()
            } else {
                enableDrawingMode(true)
                drawingOverlayView.setDrawingMode(true)
                updateDrawingToolbarUI()
            }
        }
        
        cancelCropButton.setOnClickListener {
            exitCropMode()
        }
        
        applyCropButton.setOnClickListener {
            applyCrop()
        }
        
        saveButton.setOnClickListener {
            if (cropImageView.isInCropMode()) {
                // 如果处于裁剪模式，先应用裁剪，然后保存
                applyCropAndSave()
            } else if (isDrawingMode) {
                Toast.makeText(this, "请先保存或关闭标注模式", Toast.LENGTH_SHORT).show()
            } else {
                saveImage()
            }
        }
        
        cancelButton.setOnClickListener {
            finish()
        }
    }
    
    /**
     * 设置标注工具栏
     */
    private fun setupDrawingToolbar() {
        // 画笔按钮
        btnDraw.setOnClickListener {
            enableDrawingMode(true)
            drawingOverlayView.setDrawingMode(true)
            updateDrawingToolbarUI()
        }
        
        // 橡皮擦按钮
        btnEraser.setOnClickListener {
            enableDrawingMode(true)
            drawingOverlayView.setDrawingMode(false)
            updateDrawingToolbarUI()
        }
        
        // 撤销按钮
        btnUndo.setOnClickListener {
            drawingOverlayView.undo()
        }
        
        // 清除按钮
        btnClear.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("清除所有标注")
                .setMessage("确定要清除所有绘制内容吗？")
                .setPositiveButton("确定") { _, _ ->
                    drawingOverlayView.clearAll()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        // 保存按钮
        btnSaveDrawing.setOnClickListener {
            saveAnnotation()
        }
        
        // 关闭标注模式按钮
        btnCloseDrawing.setOnClickListener {
            enableDrawingMode(false)
            Toast.makeText(this, "标注模式已关闭", Toast.LENGTH_SHORT).show()
        }
        
        // 清除已保存的标注按钮
        btnClearAnnotation.setOnClickListener {
            clearAnnotation()
        }
        
        // 颜色选择
        colorRed.setOnClickListener {
            drawingOverlayView.setBrushColor(android.graphics.Color.parseColor("#FF3B30"))
            updateColorSelection(colorRed)
        }
        colorBlue.setOnClickListener {
            drawingOverlayView.setBrushColor(android.graphics.Color.parseColor("#007AFF"))
            updateColorSelection(colorBlue)
        }
        colorGreen.setOnClickListener {
            drawingOverlayView.setBrushColor(android.graphics.Color.parseColor("#34C759"))
            updateColorSelection(colorGreen)
        }
        colorBlack.setOnClickListener {
            drawingOverlayView.setBrushColor(android.graphics.Color.parseColor("#000000"))
            updateColorSelection(colorBlack)
        }
        
        // 默认选中红色
        updateColorSelection(colorRed)
    }
    
    /**
     * 更新标注工具栏UI状态
     */
    private fun updateDrawingToolbarUI() {
        val isDrawMode = drawingOverlayView.getDrawingMode()
        btnDraw.alpha = if (isDrawMode) 1.0f else 0.5f
        btnEraser.alpha = if (!isDrawMode) 1.0f else 0.5f
    }
    
    /**
     * 更新颜色选择状态
     */
    private fun updateColorSelection(selectedView: View) {
        // 重置所有颜色的缩放
        colorRed.scaleX = 1.0f
        colorRed.scaleY = 1.0f
        colorBlue.scaleX = 1.0f
        colorBlue.scaleY = 1.0f
        colorGreen.scaleX = 1.0f
        colorGreen.scaleY = 1.0f
        colorBlack.scaleX = 1.0f
        colorBlack.scaleY = 1.0f
        
        // 设置透明度
        colorRed.alpha = if (selectedView == colorRed) 1.0f else 0.5f
        colorBlue.alpha = if (selectedView == colorBlue) 1.0f else 0.5f
        colorGreen.alpha = if (selectedView == colorGreen) 1.0f else 0.5f
        colorBlack.alpha = if (selectedView == colorBlack) 1.0f else 0.5f
        
        // 选中项放大
        selectedView.scaleX = 1.2f
        selectedView.scaleY = 1.2f
    }
    
    /**
     * 启用/禁用标注模式
     */
    private fun enableDrawingMode(enabled: Boolean) {
        isDrawingMode = enabled
        drawingOverlayView.visibility = if (enabled) View.VISIBLE else View.GONE
        drawingToolbar.visibility = if (enabled) View.VISIBLE else View.GONE
        
        // 隐藏/显示其他按钮
        rotateButton.isEnabled = !enabled
        cropButton.isEnabled = !enabled
        
        if (enabled) {
            // 加载已有标注到绘制层（可继续编辑）
            currentQuestion?.let {
                loadAnnotation(it.annotationPath)
            }
            Toast.makeText(this, "标注模式：在图片上直接书写标注", Toast.LENGTH_SHORT).show()
        } else {
            // 退出标注模式时，清除绘制层（不保存）
            drawingOverlayView.clearAll()
        }
    }
    
    /**
     * 加载题目信息
     */
    private fun loadQuestion() {
        questionId?.let { id ->
            lifecycleScope.launch {
                try {
                    val question = withContext(Dispatchers.IO) {
                        viewModel.getQuestionById(id)
                    }
                    currentQuestion = question
                } catch (e: Exception) {
                    Log.e("ImageEditActivity", "加载题目失败", e)
                }
            }
        }
    }
    
    /**
     * 保存标注
     */
    private fun saveAnnotation() {
        val question = currentQuestion ?: run {
            Toast.makeText(this, "无法保存标注：未关联题目", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (!drawingOverlayView.hasDrawing()) {
            Toast.makeText(this, "没有绘制内容", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val savedPath = withContext(Dispatchers.IO) {
                    // 获取原图尺寸（用于导出标注图层）
                    val baseImagePath = question.originalImagePath ?: question.imagePath
                    val originalFile = File(baseImagePath)
                    if (!originalFile.exists()) {
                        return@withContext null
                    }
                    
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(originalFile.absolutePath, options)
                    val originalWidth = options.outWidth
                    val originalHeight = options.outHeight
                    
                    if (originalWidth <= 0 || originalHeight <= 0) {
                        return@withContext null
                    }
                    
                    // 导出标注图层到原图尺寸
                    val annotationBitmap = drawingOverlayView.exportAnnotation(originalWidth, originalHeight)
                        ?: return@withContext null
                    
                    // 保存标注图层为PNG（透明背景）
                    val annotationsDir = File(filesDir, "annotations")
                    if (!annotationsDir.exists()) {
                        annotationsDir.mkdirs()
                    }
                    
                    val annotationFile = File(annotationsDir, "annotation_${question.id}.png")
                    
                    FileOutputStream(annotationFile).use { out ->
                        annotationBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                    
                    annotationBitmap.recycle()
                    
                    annotationFile.absolutePath
                }
                
                if (savedPath != null) {
                    // 更新题目的标注路径
                    val database = AppDatabase.getDatabase(this@ImageEditActivity)
                    val updatedQuestion = question.copy(annotationPath = savedPath)
                    database.questionDao().update(updatedQuestion)
                    currentQuestion = updatedQuestion
                    
                    withContext(Dispatchers.Main) {
                        enableDrawingMode(false)
                        Toast.makeText(this@ImageEditActivity, "标注已保存（可随时清除重做）", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImageEditActivity, "保存失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageEditActivity", "保存标注失败", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ImageEditActivity, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 清除标注（删除标注图层，保留原图，可重新做题）
     */
    private fun clearAnnotation() {
        val question = currentQuestion ?: run {
            Toast.makeText(this, "无法清除标注：未关联题目", Toast.LENGTH_SHORT).show()
            return
        }
        
        if (question.annotationPath.isNullOrBlank()) {
            Toast.makeText(this, "没有已保存的标注", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("清除标注")
            .setMessage("确定要清除所有标注吗？此操作将删除已保存的标注，原图不受影响，可重新做题。")
            .setPositiveButton("确定") { _, _ ->
                lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            // 删除标注文件
                            val annotationFile = File(question.annotationPath!!)
                            if (annotationFile.exists()) {
                                annotationFile.delete()
                            }
                        }
                        
                        // 更新数据库
                        val database = AppDatabase.getDatabase(this@ImageEditActivity)
                        val updatedQuestion = question.copy(annotationPath = null)
                        database.questionDao().update(updatedQuestion)
                        currentQuestion = updatedQuestion
                        
                        withContext(Dispatchers.Main) {
                            drawingOverlayView.clearAll()
                            Toast.makeText(this@ImageEditActivity, "标注已清除，可重新做题", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.e("ImageEditActivity", "清除标注失败", e)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ImageEditActivity, "清除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 加载已有的标注图层
     */
    private fun loadAnnotation(annotationPath: String?) {
        if (annotationPath.isNullOrBlank()) {
            drawingOverlayView.clearAll()
            return
        }
        
        lifecycleScope.launch {
            try {
                val annotationBitmap = withContext(Dispatchers.IO) {
                    val file = File(annotationPath)
                    if (file.exists()) {
                        BitmapFactory.decodeFile(file.absolutePath)
                    } else {
                        null
                    }
                }
                
                if (annotationBitmap != null) {
                    // 等待View布局完成后加载标注
                    drawingOverlayView.post {
                        drawingOverlayView.loadAnnotation(annotationBitmap)
                        annotationBitmap.recycle()
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageEditActivity", "加载标注失败", e)
            }
        }
    }
    
    /**
     * 进入裁剪模式
     */
    private fun enterCropMode() {
        cropImageView.enterCropMode()
        cropModeButtons.visibility = View.VISIBLE
        cropButton.visibility = View.GONE
        rotateButton.isEnabled = false
    }
    
    /**
     * 退出裁剪模式
     */
    private fun exitCropMode() {
        cropImageView.exitCropMode()
        cropModeButtons.visibility = View.GONE
        cropButton.visibility = View.VISIBLE
        rotateButton.isEnabled = true
    }
    
    private fun loadImage() {
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            
            val bitmap = withContext(Dispatchers.IO) {
                imagePath?.let { BitmapFactory.decodeFile(it) }
            }
            
            if (bitmap != null) {
                currentBitmap = bitmap
                imageWidth = bitmap.width
                imageHeight = bitmap.height
                rotationDegrees = 0  // 重置旋转角度
                cropImageView.setBitmap(bitmap)
            } else {
                Toast.makeText(this@ImageEditActivity, "无法加载图片", Toast.LENGTH_SHORT).show()
                finish()
            }
            
            progressBar.visibility = View.GONE
        }
    }
    
    private fun rotateImage() {
        // 只在内存中旋转，不保存到文件
        if (currentBitmap == null) {
            Toast.makeText(this, "图片未加载", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            rotateButton.isEnabled = false
            
            val rotatedBitmap = withContext(Dispatchers.IO) {
                try {
                    // 创建旋转矩阵
                    val matrix = android.graphics.Matrix().apply {
                        postRotate(90f)
                    }
                    
                    // 旋转图片（只在内存中）
                    Bitmap.createBitmap(
                        currentBitmap!!,
                        0,
                        0,
                        currentBitmap!!.width,
                        currentBitmap!!.height,
                        matrix,
                        true
                    )
                } catch (e: Exception) {
                    Log.e("ImageEditActivity", "旋转失败", e)
                    null
                }
            }
            
            if (rotatedBitmap != null) {
                // 更新旋转角度
                rotationDegrees = (rotationDegrees + 90) % 360
                
                // 更新当前bitmap
                    currentBitmap?.recycle()
                currentBitmap = rotatedBitmap
                
                // 交换宽高（因为旋转了90度）
                val temp = imageWidth
                imageWidth = imageHeight
                imageHeight = temp
                
                // 更新显示
                cropImageView.setBitmap(rotatedBitmap)
                    Toast.makeText(this@ImageEditActivity, "已旋转90度", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@ImageEditActivity, "旋转失败", Toast.LENGTH_SHORT).show()
            }
            
            progressBar.visibility = View.GONE
            rotateButton.isEnabled = true
        }
    }
    
    /**
     * 应用裁剪（使用用户在CropImageView中选择的区域）
     * 只在内存中裁剪，不保存到文件
     */
    private fun applyCrop() {
        if (currentBitmap == null) {
            Toast.makeText(this, "图片未加载", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            applyCropButton.isEnabled = false
            cancelCropButton.isEnabled = false
            
            val cropRect = cropImageView.getCropRect()
            
            if (cropRect.width() <= 0 || cropRect.height() <= 0) {
                Toast.makeText(this@ImageEditActivity, "裁剪区域无效", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                applyCropButton.isEnabled = true
                cancelCropButton.isEnabled = true
                return@launch
            }
            
            val croppedBitmap = withContext(Dispatchers.IO) {
                try {
                    // 验证裁剪参数
                    val validX = cropRect.left.toInt().coerceIn(0, currentBitmap!!.width)
                    val validY = cropRect.top.toInt().coerceIn(0, currentBitmap!!.height)
                    val validWidth = cropRect.width().toInt().coerceIn(1, currentBitmap!!.width - validX)
                    val validHeight = cropRect.height().toInt().coerceIn(1, currentBitmap!!.height - validY)
                    
                    // 在内存中裁剪
                    Bitmap.createBitmap(
                        currentBitmap!!,
                        validX,
                        validY,
                        validWidth,
                        validHeight
                    )
                } catch (e: Exception) {
                    Log.e("ImageEditActivity", "裁剪失败", e)
                    null
                }
                }
                
            if (croppedBitmap != null) {
                // 更新当前bitmap
                    currentBitmap?.recycle()
                currentBitmap = croppedBitmap
                imageWidth = croppedBitmap.width
                imageHeight = croppedBitmap.height
                
                // 更新显示
                cropImageView.setBitmap(croppedBitmap)
                    Toast.makeText(this@ImageEditActivity, "裁剪完成", Toast.LENGTH_SHORT).show()
                    
                    // 退出裁剪模式
                    exitCropMode()
            } else {
                Toast.makeText(this@ImageEditActivity, "裁剪失败", Toast.LENGTH_SHORT).show()
            }
            
            progressBar.visibility = View.GONE
            applyCropButton.isEnabled = true
            cancelCropButton.isEnabled = true
        }
    }
    
    /**
     * 应用裁剪并保存（用于保存按钮）
     */
    private fun applyCropAndSave() {
        if (currentBitmap == null) {
            Toast.makeText(this, "图片未加载", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            saveButton.isEnabled = false
            
            val cropRect = cropImageView.getCropRect()
            
            if (cropRect.width() <= 0 || cropRect.height() <= 0) {
                Toast.makeText(this@ImageEditActivity, "裁剪区域无效", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
                return@launch
            }
            
            // 在内存中裁剪
            val croppedBitmap = withContext(Dispatchers.IO) {
                try {
                    val validX = cropRect.left.toInt().coerceIn(0, currentBitmap!!.width)
                    val validY = cropRect.top.toInt().coerceIn(0, currentBitmap!!.height)
                    val validWidth = cropRect.width().toInt().coerceIn(1, currentBitmap!!.width - validX)
                    val validHeight = cropRect.height().toInt().coerceIn(1, currentBitmap!!.height - validY)
                    
                    Bitmap.createBitmap(
                        currentBitmap!!,
                        validX,
                        validY,
                        validWidth,
                        validHeight
                    )
                } catch (e: Exception) {
                    Log.e("ImageEditActivity", "裁剪失败", e)
                    null
                }
                }
                
            if (croppedBitmap != null) {
                // 更新当前bitmap
                    currentBitmap?.recycle()
                currentBitmap = croppedBitmap
                imageWidth = croppedBitmap.width
                imageHeight = croppedBitmap.height
                    
                    // 退出裁剪模式
                    exitCropMode()
                    
                    // 直接保存（不显示提示）
                    progressBar.visibility = View.GONE
                    saveButton.isEnabled = true
                    saveImage()
            } else {
                Toast.makeText(this@ImageEditActivity, "裁剪失败", Toast.LENGTH_SHORT).show()
                progressBar.visibility = View.GONE
                saveButton.isEnabled = true
            }
        }
    }
    
    private fun saveImage() {
        val originalPath = originalImagePath ?: return
        
        if (currentBitmap == null) {
            Toast.makeText(this, "图片未加载", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 检查是否有编辑（旋转或裁剪）
        // 注意：currentBitmap 已经是旋转和裁剪后的最终状态，不需要再处理
        val originalBitmap = try {
            BitmapFactory.decodeFile(originalPath)
        } catch (e: Exception) {
            null
        }
        val hasEdit = originalBitmap == null || 
                     currentBitmap!!.width != originalBitmap.width || 
                     currentBitmap!!.height != originalBitmap.height ||
                     rotationDegrees != 0
        originalBitmap?.recycle()
        
        if (!hasEdit) {
            Toast.makeText(this, "图片未修改", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        lifecycleScope.launch {
            progressBar.visibility = View.VISIBLE
            saveButton.isEnabled = false
            
            // 保存当前编辑后的图片到临时文件
            // currentBitmap 已经是最终状态（包含所有旋转和裁剪），直接保存即可
            val editedPath = withContext(Dispatchers.IO) {
                try {
                    val bitmap = currentBitmap ?: return@withContext null
                    
                    Log.d("ImageEditActivity", "保存图片 - 当前bitmap尺寸: ${bitmap.width}x${bitmap.height}, 旋转角度: $rotationDegrees")
                    
                    // 保存到临时文件
                    val tempFile = File(originalPath).let { originalFile ->
                        val dir = originalFile.parentFile
                        val nameWithoutExt = originalFile.nameWithoutExtension
                        val ext = originalFile.extension
                        File(dir, "${nameWithoutExt}_edited_${System.currentTimeMillis()}.$ext")
                    }
                    
                    // 确保目录存在
                    tempFile.parentFile?.mkdirs()
                    
                    tempFile.outputStream().use { out ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    
                    Log.d("ImageEditActivity", "图片已保存到: ${tempFile.absolutePath}, 文件大小: ${tempFile.length()} bytes")
                    
                    tempFile.absolutePath
                } catch (e: Exception) {
                    Log.e("ImageEditActivity", "保存图片失败", e)
                    e.printStackTrace()
                    null
                }
            }
            
            progressBar.visibility = View.GONE
            saveButton.isEnabled = true
            
            if (editedPath == null) {
                Toast.makeText(this@ImageEditActivity, "保存失败", Toast.LENGTH_SHORT).show()
                return@launch
        }
        
        // 询问是否清除手写笔记
            AlertDialog.Builder(this@ImageEditActivity)
            .setTitle("清除手写笔记")
            .setMessage("是否自动清除图片中的手写笔记，只保留打印文字？\n\n此功能可以净化错题图片，方便重新练习。")
            .setPositiveButton("是，清除手写") { _, _ ->
                // 清除手写笔记
                lifecycleScope.launch {
                    progressBar.visibility = View.VISIBLE
                    saveButton.isEnabled = false
                    
                    // 初始化ImageEditor
                    ImageEditor.init(this@ImageEditActivity)
                    
                    val cleanedPath = withContext(Dispatchers.IO) {
                        ImageEditor.removeHandwrittenNotes(editedPath, 0.6f)
                    }
                    
                    progressBar.visibility = View.GONE
                    saveButton.isEnabled = true
                    
                    val finalPath = cleanedPath ?: editedPath
                    if (cleanedPath == null) {
                        Toast.makeText(this@ImageEditActivity, "清除手写笔记失败，使用原图", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@ImageEditActivity, "手写笔记已清除", Toast.LENGTH_SHORT).show()
                    }
                    
                    // 询问是否替换原图
                    askReplaceOriginal(originalPath, finalPath)
                }
            }
            .setNegativeButton("跳过") { _, _ ->
                // 直接询问是否替换原图
                askReplaceOriginal(originalPath, editedPath)
            }
            .setNeutralButton("取消") { _, _ ->
                // 取消时删除临时文件
                try {
                    File(editedPath).delete()
                } catch (e: Exception) {
                    Log.e("ImageEditActivity", "删除临时文件失败", e)
                }
            }
            .show()
        }
    }
    
    /**
     * 询问是否替换原图
     */
    private fun askReplaceOriginal(originalPath: String, editedPath: String) {
        AlertDialog.Builder(this)
            .setTitle("保存编辑")
            .setMessage("是否用编辑后的图片替换原图？\n\n选择\"是\"将替换原图，选择\"否\"将保留原图并保存为新文件。")
            .setPositiveButton("是") { _, _ ->
                    replaceOriginalImage(originalPath, editedPath)
            }
            .setNegativeButton("否") { _, _ ->
                    keepOriginalImage(editedPath)
            }
                .setNeutralButton("取消") { _, _ ->
                    // 取消时删除临时文件
                    try {
                        File(editedPath).delete()
                    } catch (e: Exception) {
                        Log.e("ImageEditActivity", "删除临时文件失败", e)
                    }
                }
                .show()
    }
    
    /**
     * 通过文件路径查找MediaStore中的图片URI（Android 10+兼容）
     */
    private fun findImageUriByPath(filePath: String): Uri? {
        return try {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            // 尝试使用DATA字段（Android 9及以下）
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val selection = "${MediaStore.Images.Media.DATA} = ?"
                val selectionArgs = arrayOf(filePath)
                val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        return Uri.withAppendedPath(uri, id.toString())
                    }
                }
            } else {
                // Android 10+: 尝试使用DATA字段（某些设备可能仍可用）
                try {
                    val selection = "${MediaStore.Images.Media.DATA} = ?"
                    val selectionArgs = arrayOf(filePath)
                    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                            return Uri.withAppendedPath(uri, id.toString())
                        }
                    }
                } catch (e: Exception) {
                    Log.w("ImageEditActivity", "使用DATA字段查找失败，尝试使用文件名", e)
                }
                
                // 如果DATA字段不可用，使用文件名查找（可能匹配多个文件，返回第一个）
                val fileName = File(filePath).name
                val selection = "${MediaStore.Images.Media.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf(fileName)
                val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                        val imageUri = Uri.withAppendedPath(uri, id.toString())
                        // 在Android 10+中，无法直接验证完整路径，但文件名匹配就认为找到了
                        return imageUri
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w("ImageEditActivity", "查找图片URI失败: $filePath", e)
            null
        }
    }
    
    /**
     * 替换原图：删除原图，将编辑后的图片复制到原图位置
     * 如果替换失败（权限问题），自动降级为保存为新文件
     */
    private fun replaceOriginalImage(originalPath: String, editedPath: String) {
        lifecycleScope.launch {
            try {
                val originalFile = File(originalPath)
                val editedFile = File(editedPath)
                
                if (!editedFile.exists()) {
                    Toast.makeText(this@ImageEditActivity, "编辑后的图片不存在", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val result = withContext(Dispatchers.IO) {
                    try {
                        Log.d("ImageEditActivity", "开始替换原图: $originalPath <- $editedPath")
                        
                        val originalExists = originalFile.exists()
                        val editedSize = editedFile.length()
                        
                        Log.d("ImageEditActivity", "原图存在: $originalExists, 编辑后大小: $editedSize")
                        
                        // 策略：先复制新文件到原图位置（覆盖），然后删除原图
                        // 这样可以确保即使删除失败，新文件也已经保存成功
                        
                        // 步骤1: 复制编辑后的图片到原图位置（覆盖）
                        // 在 Android 10+ 中，需要使用 MediaStore API 来更新文件
                        var copySuccess = false
                        try {
                            // 方法1: 尝试使用 MediaStore API 更新文件（Android 10+推荐）
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && originalExists) {
                                val imageUri = findImageUriByPath(originalPath)
                                if (imageUri != null) {
                                    try {
                                        contentResolver.openOutputStream(imageUri, "w")?.use { output ->
                                            FileInputStream(editedFile).use { input ->
                                                input.copyTo(output)
                                                output.flush()
                                            }
                                        }
                                        // 更新 MediaStore 的 date_modified 字段，确保图库能识别文件已更新
                                        try {
                                            val values = ContentValues().apply {
                                                put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                                            }
                                            contentResolver.update(imageUri, values, null, null)
                                            Log.d("ImageEditActivity", "已更新 MediaStore date_modified 字段")
                                        } catch (e: Exception) {
                                            Log.w("ImageEditActivity", "更新 MediaStore date_modified 失败", e)
                                        }
                                        // 通知 MediaStore 刷新，确保新文件能被识别
                                        try {
                                            contentResolver.notifyChange(imageUri, null)
                                            // 同时通知整个图片目录，确保图库应用能刷新
                                            contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
                                        } catch (e: Exception) {
                                            Log.w("ImageEditActivity", "通知MediaStore刷新失败", e)
                                        }
                                        copySuccess = true
                                        Log.i("ImageEditActivity", "通过MediaStore API更新文件成功")
                                    } catch (e: RecoverableSecurityException) {
                                        // Android 10+ 需要用户确认权限
                                        Log.w("ImageEditActivity", "需要用户确认权限（RecoverableSecurityException）", e)
                                        // 保存参数，在主线程启动用户确认对话框
                                        pendingReplaceOriginal = originalPath
                                        pendingEditedPath = editedPath
                                        // 在主线程启动用户确认对话框
                                        withContext(Dispatchers.Main) {
                                            try {
                                                val request = IntentSenderRequest.Builder(e.userAction.actionIntent.intentSender).build()
                                                requestRecoverableSecurityExceptionLauncher.launch(request)
                                            } catch (ex: Exception) {
                                                Log.e("ImageEditActivity", "无法启动RecoverableSecurityException对话框", ex)
                                                keepOriginalImage(editedPath)
                                            }
                                        }
                                        return@withContext "NEED_RECOVERABLE_PERMISSION"
                                    } catch (e: Exception) {
                                        Log.w("ImageEditActivity", "通过MediaStore API更新失败，尝试直接文件操作", e)
                                    }
                                }
                        }
                        
                            // 方法2: 如果 MediaStore API 失败，尝试直接文件操作（Android 9及以下，或应用自己创建的文件）
                            if (!copySuccess) {
                                // 如果原图存在，先尝试删除（为复制做准备）
                                if (originalExists) {
                                    try {
                                        originalFile.delete()
                                        Thread.sleep(100)
                                    } catch (e: Exception) {
                                        Log.w("ImageEditActivity", "复制前删除原图失败，继续尝试覆盖", e)
                                    }
                                }
                                
                                FileInputStream(editedFile).use { input ->
                            originalFile.outputStream().use { output ->
                                input.copyTo(output)
                                        output.flush()
                                    }
                                }
                                
                                // 确保文件写入完成
                                originalFile.setLastModified(System.currentTimeMillis())
                                
                                // 通知 MediaStore 刷新（Android 10+）
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    try {
                                        val imageUri = findImageUriByPath(originalPath)
                                        if (imageUri != null) {
                                            // 更新 MediaStore 的 date_modified 字段
                                            val values = ContentValues().apply {
                                                put(MediaStore.Images.Media.DATE_MODIFIED, System.currentTimeMillis() / 1000)
                                            }
                                            contentResolver.update(imageUri, values, null, null)
                                            Log.d("ImageEditActivity", "已更新 MediaStore date_modified 字段")
                                            
                                            // 通知 MediaStore 刷新
                                            contentResolver.notifyChange(imageUri, null)
                                            // 同时通知整个图片目录，确保图库应用能刷新
                                            contentResolver.notifyChange(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, null)
                                        }
                                    } catch (e: Exception) {
                                        Log.w("ImageEditActivity", "通知MediaStore刷新失败", e)
                                    }
                                }
                                
                                copySuccess = true
                                Log.i("ImageEditActivity", "直接文件操作成功")
                            }
                        } catch (e: Exception) {
                            // 检查是否是权限错误
                            val errorMessage = e.message ?: ""
                            val cause = e.cause
                            
                            // 检查多种权限错误情况
                            val isPermissionError = 
                                // FileNotFoundException 且包含权限相关错误信息
                                (e is java.io.FileNotFoundException && 
                                 (errorMessage.contains("EACCES", ignoreCase = true) ||
                                  errorMessage.contains("Permission denied", ignoreCase = true) ||
                                  errorMessage.contains("Permission to access file", ignoreCase = true))) ||
                                // 或者底层是 ErrnoException 且错误码是 EACCES (13)
                                (cause is android.system.ErrnoException && 
                                 (cause.errno == 13 || cause.message?.contains("EACCES", ignoreCase = true) == true)) ||
                                // 或者异常消息中包含 SecurityException 相关信息
                                (errorMessage.contains("SecurityException", ignoreCase = true) ||
                                 errorMessage.contains("has no access", ignoreCase = true))
                            
                            if (isPermissionError) {
                                Log.w("ImageEditActivity", "复制文件失败：权限不足，将请求权限", e)
                                return@withContext "NEED_PERMISSION" // 返回特殊值表示需要权限
                            } else {
                                Log.e("ImageEditActivity", "复制文件失败（非权限问题）", e)
                                e.printStackTrace()
                                return@withContext "FAILED" // 返回失败
                            }
                        }
                        
                        // 验证新文件是否存在且大小正确
                        if (!originalFile.exists()) {
                            Log.e("ImageEditActivity", "替换后原图文件不存在")
                            return@withContext false
                        }
                        
                        val newSize = originalFile.length()
                        if (newSize == 0L || newSize < editedSize * 0.9) {
                            Log.e("ImageEditActivity", "文件大小异常: 期望约 $editedSize, 实际 $newSize")
                            return@withContext false
                        }
                        
                        Log.d("ImageEditActivity", "文件复制完成，新文件大小: $newSize")
                        
                        // 步骤2: 如果使用MediaStore API更新，文件已经覆盖，不需要删除原图
                        // 如果使用直接文件操作，原图已经被覆盖，也不需要删除
                        // 注意：在Android 10+中，通过MediaStore API更新文件后，原图URI已经指向新内容，不需要删除
                        var deleted = true // 文件已经通过覆盖更新，视为"删除"了旧内容
                        if (copySuccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            // 使用MediaStore API更新时，文件已经覆盖，不需要额外操作
                            Log.i("ImageEditActivity", "通过MediaStore API更新，文件已覆盖为新内容")
                        } else if (copySuccess) {
                            // 直接文件操作时，文件已经覆盖
                            Log.i("ImageEditActivity", "通过直接文件操作更新，文件已覆盖为新内容")
                        }
                        
                        // 删除编辑后的临时文件
                        if (editedFile.exists() && editedFile.absolutePath != originalFile.absolutePath) {
                            val tempDeleted = editedFile.delete()
                            Log.i("ImageEditActivity", "删除临时文件: $editedPath, 结果: $tempDeleted")
                        }
                        
                        Log.i("ImageEditActivity", "替换成功: $originalPath (原图已${if(deleted) "删除" else "覆盖/重命名"})")
                        "SUCCESS"
                    } catch (e: Exception) {
                        Log.e("ImageEditActivity", "替换原图失败", e)
                        e.printStackTrace()
                        "FAILED"
                    }
                }
                
                when (result) {
                    "SUCCESS" -> {
                    // 更新当前图片路径为原图路径（因为已经替换了）
                    imagePath = originalPath
                    
                    // 返回原图路径（现在已经是编辑后的图片了）
                    val resultIntent = intent.apply {
                        putExtra(EXTRA_EDITED_IMAGE_PATH, originalPath)
                        putExtra(EXTRA_REPLACED_ORIGINAL, true)
                    }
                    setResult(RESULT_OK, resultIntent)
                    Toast.makeText(this@ImageEditActivity, "已替换原图", Toast.LENGTH_SHORT).show()
                    finish()
                    }
                    "NEED_PERMISSION" -> {
                        // 需要权限，请求权限
                        Log.i("ImageEditActivity", "需要存储权限，请求权限")
                        pendingReplaceOriginal = originalPath
                        pendingEditedPath = editedPath
                        
                        // 检查并请求权限
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                // Android 13+ 不需要 WRITE_EXTERNAL_STORAGE
                                null
                } else {
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                            }
                            
                            if (permission != null) {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    this@ImageEditActivity,
                                    permission
                                ) == PackageManager.PERMISSION_GRANTED
                                
                                if (!hasPermission) {
                                    requestPermissionLauncher.launch(permission)
                                    return@launch
                                }
                            }
                        }
                        
                        // 如果已经有权限或不需要权限，但依然失败，可能是其他原因
                        // 降级为保存为新文件
                        Log.w("ImageEditActivity", "已有权限但仍失败，保存为新文件")
                        keepOriginalImage(editedPath)
                    }
                    "NEED_RECOVERABLE_PERMISSION" -> {
                        // 用户确认对话框已经在捕获异常时启动，这里不需要做任何事
                        // 对话框的结果会在 requestRecoverableSecurityExceptionLauncher 的回调中处理
                        Log.i("ImageEditActivity", "已启动RecoverableSecurityException用户确认对话框")
                    }
                    else -> {
                        // 替换失败，自动降级为保存为新文件
                        Log.i("ImageEditActivity", "替换失败，自动保存为新文件")
                        // 注意：editedPath 是临时文件，需要保留，不要删除
                        keepOriginalImage(editedPath)
                    }
                }
            } catch (e: Exception) {
                Log.e("ImageEditActivity", "替换原图失败", e)
                e.printStackTrace()
                // 出错时也尝试保存为新文件
                keepOriginalImage(editedPath)
            }
        }
    }
    
    /**
     * 保留原图：编辑后的图片作为新文件保存
     */
    private fun keepOriginalImage(editedPath: String) {
        // 返回编辑后的图片路径（新文件）
        val resultIntent = intent.apply {
            putExtra(EXTRA_EDITED_IMAGE_PATH, editedPath)
            putExtra(EXTRA_REPLACED_ORIGINAL, false)
        }
        setResult(RESULT_OK, resultIntent)
        Toast.makeText(this, "已保存为新文件", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        currentBitmap?.recycle()
    }
    
    companion object {
        const val EXTRA_IMAGE_PATH = "image_path"
        const val EXTRA_EDITED_IMAGE_PATH = "edited_image_path"
        const val EXTRA_REPLACED_ORIGINAL = "replaced_original"  // 是否替换了原图
    }
}
