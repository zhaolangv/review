package com.gongkao.cuotifupan.ui

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.ScannedImage
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.detector.QuestionDetector
import com.gongkao.cuotifupan.detector.QuestionRegionDetector
import com.gongkao.cuotifupan.detector.ImageBasedQuestionDetector
import com.gongkao.cuotifupan.detector.QuestionRegion
import com.gongkao.cuotifupan.ocr.TextRecognizer
import com.gongkao.cuotifupan.api.QuestionApiQueue
import com.gongkao.cuotifupan.api.HandwritingRemovalService
import com.gongkao.cuotifupan.util.ImageEditor
import com.gongkao.cuotifupan.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * 拍照裁剪Activity
 * 支持拍照后自动进入裁剪界面，类似iOS应用"上岸-考公错题本"的功能
 */
class CameraCaptureActivity : AppCompatActivity() {
    
    private lateinit var cropImageView: CropImageView
    private lateinit var captureButton: Button
    private lateinit var retakeButton: Button
    private lateinit var confirmButton: Button
    private lateinit var addCropButton: Button
    private lateinit var removeCropButton: Button
    private lateinit var removeHandwritingButton: Button
    private lateinit var cropButtonsContainer: android.view.View
    private lateinit var progressBar: ProgressBar
    
    private var autoDetectDialog: AlertDialog? = null
    private var importSuccessDialog: AlertDialog? = null
    private var autoDetectProgressText: TextView? = null
    
    private var photoUri: Uri? = null
    private var photoFile: File? = null
    private var currentBitmap: Bitmap? = null
    
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePicture()
        } else {
            Toast.makeText(this, "需要相机权限才能拍照", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && photoUri != null) {
            loadPhotoAndEnterCropMode()
        } else {
            Toast.makeText(this, "拍照失败，请重试", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera_capture)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "拍照裁剪题目"
        
        initViews()
        checkCameraPermissionAndCapture()
    }
    
    private fun initViews() {
        cropImageView = findViewById(R.id.cropImageView)
        captureButton = findViewById(R.id.captureButton)
        retakeButton = findViewById(R.id.retakeButton)
        confirmButton = findViewById(R.id.confirmButton)
        addCropButton = findViewById(R.id.addCropButton)
        removeCropButton = findViewById(R.id.removeCropButton)
        removeHandwritingButton = findViewById(R.id.removeHandwritingButton)
        cropButtonsContainer = findViewById(R.id.cropButtonsContainer)
        progressBar = findViewById(R.id.progressBar)
        
        // 初始化手写擦除服务
        HandwritingRemovalService.init(this)
        
        captureButton.setOnClickListener {
            checkCameraPermissionAndCapture()
        }
        
        retakeButton.setOnClickListener {
            // 重新拍照
            checkCameraPermissionAndCapture()
        }
        
        addCropButton.setOnClickListener {
            cropImageView.addCropRect()
            updateRemoveButtonState()
        }
        
        removeCropButton.setOnClickListener {
            if (cropImageView.removeSelectedCropRect()) {
                updateRemoveButtonState()
            } else {
                Toast.makeText(this, "没有可删除的裁剪框", Toast.LENGTH_SHORT).show()
            }
        }
        
        confirmButton.setOnClickListener {
            saveAndImport()
        }
        
        removeHandwritingButton.setOnClickListener {
            removeHandwriting()
        }
        
        // 初始状态：显示拍照按钮，隐藏裁剪相关按钮
        showCaptureMode()
    }
    
    private fun updateRemoveButtonState() {
        val count = cropImageView.getCropRectCount()
        removeCropButton.isEnabled = count > 0 // 至少有一个裁剪框才能删除
        
        // 显示当前选中框的编号（从1开始），如果没有选中框则显示总数量
        val selectedIndex = cropImageView.getSelectedCropIndex()
        val displayNumber = if (selectedIndex >= 0 && selectedIndex < count) {
            selectedIndex + 1
        } else if (count > 0) {
            count // 如果没有选中框，显示总数量
        } else {
            0 // 如果没有裁剪框，显示0
        }
        removeCropButton.text = "删除当前框($displayNumber)"
    }
    
    private fun checkCameraPermissionAndCapture() {
        val permission = Manifest.permission.CAMERA
        val hasPermission = ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            takePicture()
        } else {
            cameraPermissionLauncher.launch(permission)
        }
    }
    
    private fun takePicture() {
        try {
            // 创建临时文件保存照片
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
            photoFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            
            // 创建URI（Android 7.0+需要使用FileProvider）
            photoUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                FileProvider.getUriForFile(
                    this,
                    "${packageName}.fileprovider",
                    photoFile!!
                )
            } else {
                Uri.fromFile(photoFile)
            }
            
            // 启动相机
            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            Log.e("CameraCapture", "拍照失败", e)
            Toast.makeText(this, "拍照失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun loadPhotoAndEnterCropMode() {
        Log.i("CameraCapture", "========== loadPhotoAndEnterCropMode 开始 ==========")
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) {
                    photoFile?.let { file ->
                        if (file.exists()) {
                            Log.d("CameraCapture", "读取图片文件: ${file.absolutePath}")
                            // 读取图片，可能需要旋转
                            val options = BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                            BitmapFactory.decodeFile(file.absolutePath, options)
                            
                            // 计算缩放比例，避免内存溢出
                            var scale = 1
                            val maxSize = 2048 // 最大尺寸
                            if (options.outWidth > maxSize || options.outHeight > maxSize) {
                                scale = maxOf(
                                    options.outWidth / maxSize,
                                    options.outHeight / maxSize
                                )
                            }
                            
                            options.inJustDecodeBounds = false
                            options.inSampleSize = scale
                            
                            BitmapFactory.decodeFile(file.absolutePath, options)
                        } else {
                            Log.w("CameraCapture", "图片文件不存在")
                            null
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    if (bitmap != null) {
                        Log.i("CameraCapture", "✅ 图片加载成功，准备进入裁剪模式")
                        currentBitmap = bitmap
                        cropImageView.setBitmap(bitmap)
                        showCropMode()
                        // 隐藏进度条，因为图片已加载完成
                        progressBar.visibility = View.GONE
                        Log.i("CameraCapture", "✅ 已切换到裁剪模式，准备自动检测题目")
                        // 自动执行题目区域检测
                        autoDetectQuestions()
                        Log.i("CameraCapture", "✅ 已启动自动检测，将停留在裁剪页面等待用户操作")
                    } else {
                        Log.e("CameraCapture", "❌ 图片加载失败")
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@CameraCaptureActivity, "加载图片失败", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraCapture", "❌ 加载图片异常", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@CameraCaptureActivity, "加载图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun showCaptureMode() {
        captureButton.visibility = View.VISIBLE
        cropButtonsContainer.visibility = View.GONE
        cropImageView.visibility = View.GONE
    }
    
    private fun showCropMode() {
        captureButton.visibility = View.GONE
        cropButtonsContainer.visibility = View.VISIBLE
        cropImageView.visibility = View.VISIBLE
        cropImageView.setCropMode(true)
        // 更新按钮状态，确保显示正确的数字
        updateRemoveButtonState()
    }
    
    /**
     * 重置到拍照模式（清空当前图片和裁剪框）
     */
    private fun resetToCaptureMode() {
        // 清空当前图片
        currentBitmap = null
        cropImageView.setBitmap(null)
        // 切换到拍照模式
        showCaptureMode()
        // 清空临时文件引用
        photoFile = null
        photoUri = null
    }
    
    /**
     * 显示自动检测加载对话框
     */
    private fun showAutoDetectDialog() {
        if (autoDetectDialog == null) {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_scanning, null)
            autoDetectProgressText = dialogView.findViewById(R.id.progressText)
            val titleText = dialogView.findViewById<TextView>(R.id.titleText)
            titleText?.text = "正在自动裁剪"
            
            autoDetectDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create()
            
            autoDetectDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
            autoDetectDialog?.window?.setDimAmount(0.0f)
            autoDetectDialog?.window?.setGravity(android.view.Gravity.CENTER)
        }
        
        autoDetectProgressText?.text = "正在自动检测题目区域..."
        autoDetectDialog?.show()
    }
    
    /**
     * 隐藏自动检测加载对话框
     */
    private fun hideAutoDetectDialog() {
        autoDetectDialog?.dismiss()
        autoDetectDialog = null
        autoDetectProgressText = null
    }
    
    /**
     * 自动检测图片中的题目区域并创建裁剪框
     */
    private fun autoDetectQuestions() {
        val bitmap = currentBitmap ?: return
        
        showAutoDetectDialog()
        Log.i("CameraCapture", "📷 开始自动检测题目区域...")
        
        lifecycleScope.launch {
            try {
                // 1. OCR 识别
                // 注意：创建一个副本用于 OCR，避免原始 bitmap 被回收
                val bitmapCopy = withContext(Dispatchers.IO) {
                    bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                }
                
                val recognizer = TextRecognizer()
                val ocrResult = withContext(Dispatchers.IO) {
                    try {
                        recognizer.recognizeText(bitmapCopy)
                    } finally {
                        // OCR 完成后回收副本
                        if (!bitmapCopy.isRecycled) {
                            bitmapCopy.recycle()
                        }
                    }
                }
                
                if (!ocrResult.success) {
                    Log.w("CameraCapture", "⚠️ OCR 识别失败: ${ocrResult.errorMessage}")
                    withContext(Dispatchers.Main) {
                        hideAutoDetectDialog()
                        Toast.makeText(
                            this@CameraCaptureActivity,
                            "自动检测失败，请手动添加裁剪框",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }
                
                // 更新进度提示
                withContext(Dispatchers.Main) {
                    autoDetectProgressText?.text = "正在分析题目布局..."
                }
                
                Log.i("CameraCapture", "✅ OCR 识别完成，文本块数量: ${ocrResult.textBlocks.size}")
                
                // 2. 检测题目区域（OCR + 图像分析）
                Log.i("CameraCapture", "🔍 开始分析题目布局...")
                
                // 2.1 OCR 检测
                val ocrDetector = QuestionRegionDetector()
                val ocrRegions = ocrDetector.detectQuestionRegions(
                    ocrResult,
                    bitmap.width,
                    bitmap.height
                )
                Log.i("CameraCapture", "📊 OCR检测到 ${ocrRegions.size} 个题目区域")
                
                // 2.2 图像分析检测（使用投影分析）
                val imageDetector = ImageBasedQuestionDetector()
                val imageRegions = withContext(Dispatchers.IO) {
                    // 创建bitmap副本用于图像分析
                    val analysisBitmap = bitmap.copy(bitmap.config ?: Bitmap.Config.ARGB_8888, false)
                    try {
                        imageDetector.detectQuestionRegionsByProjection(analysisBitmap)
                    } finally {
                        if (!analysisBitmap.isRecycled) {
                            analysisBitmap.recycle()
                        }
                    }
                }
                Log.i("CameraCapture", "🖼️ 图像分析检测到 ${imageRegions.size} 个题目区域")
                
                // 2.3 融合OCR和图像分析结果
                // 优先使用OCR结果（更精确），图像分析作为辅助验证
                val regions = if (ocrRegions.isNotEmpty()) {
                    // 如果有OCR结果，使用OCR结果
                    ocrRegions
                } else {
                    // 如果OCR没有结果，使用图像分析结果
                    imageRegions.map { rect ->
                        QuestionRegion(
                            bounds = rect,
                            questionNumber = null,
                            confidence = 0.7f  // 图像分析的置信度较低
                        )
                    }
                }
                
                Log.i("CameraCapture", "📊 最终检测到 ${regions.size} 个题目区域（OCR: ${ocrRegions.size}, 图像: ${imageRegions.size}）")
                
                // 3. 设置裁剪框
                withContext(Dispatchers.Main) {
                    hideAutoDetectDialog()
                    Log.i("CameraCapture", "========== 自动检测完成，准备设置裁剪框 ==========")
                    Log.i("CameraCapture", "检测到的区域数量: ${regions.size}")
                    Log.i("CameraCapture", "当前Activity状态: isFinishing=${isFinishing}, isDestroyed=${isDestroyed}")
                    
                    if (regions.isNotEmpty()) {
                        Log.i("CameraCapture", "🎨 获取 Canvas 大小，准备绘制裁剪框...")
                        cropImageView.setAutoCropRegions(regions.map { it.bounds })
                        // 使用 post 确保在 CropImageView 布局完成并添加裁剪框后更新按钮状态
                        cropImageView.post { updateRemoveButtonState() }
                        updateRemoveButtonState()
                        
                        val finalCount = cropImageView.getCropRectCount()
                        val message = if (finalCount == regions.size) {
                            "自动裁剪完成，检测到 $finalCount 道题目"
                        } else {
                            "自动裁剪完成，检测到 $finalCount 道题目（部分区域已过滤）"
                        }
                        Toast.makeText(
                            this@CameraCaptureActivity,
                            "$message，如有错误请手动调整",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.i("CameraCapture", "✅ 自动框选完成: $message")
                        Log.i("CameraCapture", "✅ 将停留在裁剪页面，等待用户手动调整或确认导入")
                        Log.i("CameraCapture", "✅ 不会自动跳转或关闭页面")
                    } else {
                        Toast.makeText(
                            this@CameraCaptureActivity,
                            "未检测到题目，请手动添加裁剪框",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.w("CameraCapture", "⚠️ 未检测到题目区域")
                        Log.i("CameraCapture", "✅ 将停留在裁剪页面，等待用户手动添加裁剪框")
                    }
                    Log.i("CameraCapture", "========== 自动检测流程结束，页面将保持打开 ==========")
                }
            } catch (e: Exception) {
                Log.e("CameraCapture", "❌ 自动检测失败", e)
                withContext(Dispatchers.Main) {
                    hideAutoDetectDialog()
                    Toast.makeText(
                        this@CameraCaptureActivity,
                        "自动裁剪失败: ${e.message}，请手动调整",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * 手写擦除功能
     */
    private fun removeHandwriting() {
        val bitmap = currentBitmap ?: run {
            Toast.makeText(this, "没有图片可处理", Toast.LENGTH_SHORT).show()
            return
        }
        
        showAutoDetectDialog()
        autoDetectProgressText?.text = "正在擦除手写笔记..."
        
        lifecycleScope.launch {
            try {
                val processedBitmap = withContext(Dispatchers.IO) {
                    HandwritingRemovalService.removeHandwriting(bitmap)
                }
                
                withContext(Dispatchers.Main) {
                    hideAutoDetectDialog()
                    
                    if (processedBitmap != null) {
                        // 更新图片
                        currentBitmap = processedBitmap
                        cropImageView.setBitmap(processedBitmap)
                        Toast.makeText(
                            this@CameraCaptureActivity,
                            "手写擦除完成",
                            Toast.LENGTH_SHORT
                        ).show()
                        Log.i("CameraCapture", "✅ 手写擦除成功")
                    } else {
                        Toast.makeText(
                            this@CameraCaptureActivity,
                            "手写擦除失败，请稍后重试",
                            Toast.LENGTH_LONG
                        ).show()
                        Log.e("CameraCapture", "❌ 手写擦除失败")
                    }
                }
            } catch (e: HandwritingRemovalService.HandwritingRemovalException) {
                Log.e("CameraCapture", "❌ 手写擦除异常: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    hideAutoDetectDialog()
                    
                    // 对于额度用尽的情况，显示对话框提示
                    if (e.errorCode == "QUOTA_EXCEEDED") {
                        val context = this@CameraCaptureActivity
                        val redeemCodeUrl = com.gongkao.cuotifupan.util.ProManager.getRedeemCodeUrl(context)
                        
                        val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(context)
                            .setTitle("额度已用尽")
                            .setMessage(e.message ?: "您的使用额度已用尽")
                        
                        // 如果有兑换码链接，显示"如何领取兑换码"按钮
                        if (redeemCodeUrl != null && redeemCodeUrl.isNotBlank()) {
                            dialogBuilder.setPositiveButton("如何领取兑换码") { _, _ ->
                                // 跳转到如何领取兑换码页面
                                try {
                                    val intent = Intent(context, com.gongkao.cuotifupan.ui.HowToGetRedeemCodeActivity::class.java)
                                    startActivity(intent)
                                } catch (ex: Exception) {
                                    Log.e("CameraCapture", "跳转失败", ex)
                                    Toast.makeText(context, "无法打开页面", Toast.LENGTH_SHORT).show()
                                }
                            }
                            dialogBuilder.setNegativeButton("知道了", null)
                        } else {
                            // 没有兑换码链接，只显示"知道了"按钮
                            dialogBuilder.setPositiveButton("知道了", null)
                        }
                        
                        dialogBuilder.show()
                    } else {
                        Toast.makeText(
                            this@CameraCaptureActivity,
                            e.message ?: "手写擦除失败",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("CameraCapture", "❌ 手写擦除异常", e)
                withContext(Dispatchers.Main) {
                    hideAutoDetectDialog()
                    Toast.makeText(
                        this@CameraCaptureActivity,
                        "手写擦除失败: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    
    private fun saveAndImport() {
        progressBar.visibility = View.VISIBLE
        
        lifecycleScope.launch {
            try {
                // 获取裁剪框数量
                val cropRectCount = withContext(Dispatchers.Main) {
                    cropImageView.getCropRectCount()
                }
                Log.i("CameraCapture", "📊 裁剪框数量: $cropRectCount")
                
                // 获取所有裁剪后的图片
                val croppedBitmaps = withContext(Dispatchers.Main) {
                    cropImageView.getAllCroppedBitmaps()
                }
                
                Log.i("CameraCapture", "📊 裁剪后的图片数量: ${croppedBitmaps.size}")
                
                if (croppedBitmaps.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@CameraCaptureActivity, "没有可裁剪的图片，请添加裁剪框", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // 保存所有裁剪后的图片
                val savedPaths = mutableListOf<String>()
                val database = AppDatabase.getDatabase(this@CameraCaptureActivity)
                
                croppedBitmaps.forEachIndexed { index, bitmap ->
                    val savedPath = withContext(Dispatchers.IO) {
                        saveCroppedImage(bitmap, index)
                    }
                    if (savedPath != null) {
                        savedPaths.add(savedPath)
                        
                        // 立即记录到 ScannedImage 表，标记为已扫描（后续会导入为题目）
                        // 这样可以避免自动同步时重复导入
                        try {
                            val file = File(savedPath)
                            if (file.exists()) {
                                val scannedImage = ScannedImage(
                                    imagePath = savedPath,
                                    fileName = file.name,
                                    fileSize = file.length(),
                                    isQuestion = true, // 标记为题目，因为用户手动裁剪并导入
                                    scannedAt = System.currentTimeMillis(),
                                    mediaStoreId = 0L
                                )
                                database.scannedImageDao().insert(scannedImage)
                                Log.d("CameraCapture", "✅ 已记录裁剪图片到 ScannedImage: ${file.name}")
                            }
                        } catch (e: Exception) {
                            Log.e("CameraCapture", "记录 ScannedImage 失败", e)
                        }
                    }
                }
                
                Log.i("CameraCapture", "📊 保存的图片数量: ${savedPaths.size}")
                
                if (savedPaths.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.GONE
                        Toast.makeText(this@CameraCaptureActivity, "保存图片失败", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // 批量导入所有图片
                var successCount = 0
                var failCount = 0
                
                savedPaths.forEachIndexed { index, path ->
                    Log.i("CameraCapture", "处理第 ${index + 1}/${savedPaths.size} 张图片: $path")
                    
                    withContext(Dispatchers.Main) {
                        progressBar.visibility = View.VISIBLE
                    }
                    
                    val importResult = withContext(Dispatchers.IO) {
                        importImage(path)
                    }
                    
                    if (importResult) {
                        successCount++
                        Log.i("CameraCapture", "✅ 第 ${index + 1} 张图片导入成功")
                    } else {
                        failCount++
                        Log.e("CameraCapture", "❌ 第 ${index + 1} 张图片导入失败")
                    }
                }
                
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Log.i("CameraCapture", "========== 导入完成 ==========")
                    Log.i("CameraCapture", "成功导入: $successCount, 失败: $failCount")
                    Log.i("CameraCapture", "当前Activity状态: isFinishing=${isFinishing}, isDestroyed=${isDestroyed}")
                    
                    val message = when {
                        successCount > 0 && failCount == 0 -> "✅ 成功导入 $successCount 道题目！"
                        successCount > 0 && failCount > 0 -> "✅ 成功导入 $successCount 道题目，$failCount 道失败"
                        else -> "❌ 导入失败，请重试"
                    }
                    Toast.makeText(this@CameraCaptureActivity, message, Toast.LENGTH_LONG).show()
                    
                    if (successCount > 0) {
                        // 检查 Activity 是否还在运行
                        if (isFinishing || isDestroyed) {
                            Log.w("CameraCapture", "⚠️ Activity 已销毁，不显示对话框")
                            return@withContext
                        }
                        
                        Log.i("CameraCapture", "✅ 显示导入成功对话框，提供用户选择")
                        // 先关闭之前的对话框（如果存在）
                        importSuccessDialog?.dismiss()
                        
                        // 显示成功对话框，提供多个选项
                        importSuccessDialog = android.app.AlertDialog.Builder(this@CameraCaptureActivity)
                            .setTitle("导入成功")
                            .setMessage("成功导入 $successCount 道题目。\n\n请选择下一步操作：")
                            .setPositiveButton("继续调整") { _, _ ->
                                Log.i("CameraCapture", "✅ 用户选择：继续调整，停留在裁剪页面")
                                importSuccessDialog = null
                                // 继续停留在当前裁剪页面，用户可以继续调整裁剪框或添加新的裁剪框
                                // 已导入的裁剪框可以保留，也可以删除后重新添加
                            }
                            .setNeutralButton("重新拍照") { _, _ ->
                                Log.i("CameraCapture", "✅ 用户选择：重新拍照")
                                importSuccessDialog = null
                                // 清空当前图片，重新进入拍照模式
                                resetToCaptureMode()
                            }
                            .setNegativeButton("完成返回") { _, _ ->
                                Log.i("CameraCapture", "✅ 用户选择：完成返回，关闭页面")
                                importSuccessDialog = null
                                finish()
                            }
                            .setCancelable(false) // 不允许点击外部关闭，必须选择操作
                            .setOnDismissListener {
                                Log.i("CameraCapture", "对话框已关闭，但页面保持打开")
                                importSuccessDialog = null
                            }
                            .create()
                        
                        // 再次检查 Activity 状态
                        if (!isFinishing && !isDestroyed) {
                            importSuccessDialog?.show()
                            Log.i("CameraCapture", "✅ 对话框已显示，等待用户选择，不会自动关闭页面")
                        } else {
                            Log.w("CameraCapture", "⚠️ Activity 在显示对话框前已销毁")
                            importSuccessDialog = null
                        }
                    } else {
                        Log.i("CameraCapture", "⚠️ 导入失败，停留在裁剪页面，等待用户重试")
                    }
                    Log.i("CameraCapture", "========== 导入流程结束，页面将保持打开 ==========")
                }
            } catch (e: Exception) {
                Log.e("CameraCapture", "保存图片失败", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@CameraCaptureActivity, "保存图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun saveCroppedImage(bitmap: Bitmap, index: Int = 0): String? {
        return try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "cropped_${timeStamp}_${index + 1}.jpg"
            
            // 保存到公共存储目录（DCIM/Camera），这样图片会出现在相册中
            val savedPath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore API
                saveImageToGalleryQ(bitmap, imageFileName)
            } else {
                // Android 9 及以下使用传统方式
                saveImageToGalleryLegacy(bitmap, imageFileName)
            }
            
            // 回收bitmap
            bitmap.recycle()
            
            savedPath
        } catch (e: Exception) {
            Log.e("CameraCapture", "保存裁剪图片失败", e)
            null
        }
    }
    
    /**
     * Android 10+ 保存图片到相册
     */
    private fun saveImageToGalleryQ(bitmap: Bitmap, fileName: String): String? {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            
            val uri = contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return null
            
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
            }
            
            // 标记为已完成
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)
            
            // 通知媒体库刷新
            contentResolver.notifyChange(uri, null)
            
            // 获取实际文件路径
            val projection = arrayOf(MediaStore.Images.Media.DATA)
            val cursor = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val pathIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                    val path = it.getString(pathIndex)
                    Log.d("CameraCapture", "图片已保存到相册: $path")
                    return path
                }
            }
            
            // 如果无法获取路径，返回 URI 的字符串形式
            uri.toString()
        } catch (e: Exception) {
            Log.e("CameraCapture", "保存图片到相册失败 (Android 10+)", e)
            null
        }
    }
    
    /**
     * Android 9 及以下保存图片到相册
     */
    private fun saveImageToGalleryLegacy(bitmap: Bitmap, fileName: String): String? {
        return try {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM + "/Camera")
            if (!picturesDir.exists()) {
                picturesDir.mkdirs()
            }
            
            val imageFile = File(picturesDir, fileName)
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            // 通知媒体库刷新
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            }
            contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            
            // 通知媒体库刷新
            sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(imageFile)))
            
            Log.d("CameraCapture", "图片已保存到相册: ${imageFile.absolutePath}")
            imageFile.absolutePath
        } catch (e: Exception) {
            Log.e("CameraCapture", "保存图片到相册失败 (Android 9-)", e)
            null
        }
    }
    
    /**
     * 导入图片并创建题目
     */
    private suspend fun importImage(imagePath: String): Boolean {
        return try {
            Log.i("CameraCapture", "========== 开始导入图片 ==========")
            Log.i("CameraCapture", "图片路径: $imagePath")
            
            // 验证图片是否有效
            if (!com.gongkao.cuotifupan.util.ImageAccessHelper.isValidImage(this@CameraCaptureActivity, imagePath)) {
                Log.e("CameraCapture", "❌ 图片文件无效: $imagePath")
                return false
            }
            Log.i("CameraCapture", "✅ 图片文件验证通过")
            
            // 自动处理图片（旋转等，但不需要再次裁剪，因为已经裁剪过了）
            Log.i("CameraCapture", "开始自动处理图片（旋转等）...")
            val processedImagePath = ImageEditor.autoProcessImage(imagePath)
            Log.i("CameraCapture", "图片处理完成: $processedImagePath")
            
            // 检查是否已导入
            val database = AppDatabase.getDatabase(this@CameraCaptureActivity)
            val existingQuestions = database.questionDao().getAllQuestionsSync()
            if (existingQuestions.any { it.imagePath == processedImagePath }) {
                Log.w("CameraCapture", "⚠️ 题目已存在，跳过")
                return false
            }
            
            // OCR 识别
            Log.i("CameraCapture", "开始OCR识别...")
            val recognizer = TextRecognizer()
            val ocrResult = recognizer.recognizeText(processedImagePath)
            
            Log.i("CameraCapture", "========== ML Kit OCR 识别结果 ==========")
            Log.i("CameraCapture", "  - 成功: ${ocrResult.success}")
            Log.i("CameraCapture", "  - 文本长度: ${ocrResult.rawText.length}")
            Log.i("CameraCapture", "  - 文本内容: [${ocrResult.rawText.take(500)}]")
            if (ocrResult.rawText.length > 500) {
                Log.i("CameraCapture", "  - 文本内容(续): [${ocrResult.rawText.substring(500).take(500)}]")
            }
            Log.i("CameraCapture", "  - 错误信息: ${ocrResult.errorMessage ?: "无"}")
            
            // 同时使用 PaddleOCR 识别并对比
            try {
                Log.i("CameraCapture", "========== PaddleOCR 识别开始 ==========")
                val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(this@CameraCaptureActivity, processedImagePath)
                if (bitmap != null) {
                    // 初始化 PaddleOCR（如果还未初始化）
                    if (!com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.isInitialized()) {
                        val initSuccess = com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.init(this@CameraCaptureActivity)
                        Log.i("CameraCapture", "PaddleOCR 初始化: ${if (initSuccess) "成功" else "失败"}")
                    }
                    
                    // 使用 PaddleOCR 识别
                    val paddleResult = com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.recognizeText(bitmap)
                    Log.i("CameraCapture", "========== PaddleOCR 识别结果 ==========")
                    if (paddleResult != null) {
                        Log.i("CameraCapture", "  - 文本长度: ${paddleResult.length}")
                        Log.i("CameraCapture", "  - 文本内容: [${paddleResult.take(500)}]")
                        if (paddleResult.length > 500) {
                            Log.i("CameraCapture", "  - 文本内容(续): [${paddleResult.substring(500).take(500)}]")
                        }
                    } else {
                        Log.w("CameraCapture", "  - 识别结果: null（识别失败）")
                    }
                    
                    // 对比结果
                    Log.i("CameraCapture", "========== OCR 结果对比 ==========")
                    Log.i("CameraCapture", "ML Kit 结果长度: ${ocrResult.rawText.length}")
                    Log.i("CameraCapture", "PaddleOCR 结果长度: ${paddleResult?.length ?: 0}")
                    Log.i("CameraCapture", "结果是否相同: ${ocrResult.rawText == paddleResult}")
                    if (ocrResult.rawText != paddleResult) {
                        Log.i("CameraCapture", "结果不同，差异分析:")
                        val mlKitText = ocrResult.rawText
                        val paddleText = paddleResult ?: ""
                        val minLen = minOf(mlKitText.length, paddleText.length)
                        var diffCount = 0
                        for (i in 0 until minLen) {
                            if (mlKitText[i] != paddleText[i]) {
                                diffCount++
                                if (diffCount <= 10) { // 只打印前10个差异位置
                                    val start = maxOf(0, i - 10)
                                    val end = minOf(minLen, i + 10)
                                    Log.i("CameraCapture", "  位置 $i: ML Kit='${mlKitText.substring(start, end)}' vs PaddleOCR='${paddleText.substring(start, end)}'")
                                }
                            }
                        }
                        if (diffCount > 10) {
                            Log.i("CameraCapture", "  ... 还有 ${diffCount - 10} 个差异位置")
                        }
                        if (mlKitText.length != paddleText.length) {
                            Log.i("CameraCapture", "  长度差异: ${mlKitText.length - paddleText.length} 字符")
                        }
                    }
                    Log.i("CameraCapture", "=====================================")
                    
                    bitmap.recycle()
                } else {
                    Log.w("CameraCapture", "无法解码图片为 Bitmap，跳过 PaddleOCR 识别")
                }
            } catch (e: Exception) {
                Log.e("CameraCapture", "PaddleOCR 识别过程出错", e)
            }
            
            // 即使OCR失败，也创建题目记录（用户可以看到图片）
            val rawText = if (ocrResult.success && ocrResult.rawText.isNotBlank()) {
                ocrResult.rawText
            } else {
                Log.w("CameraCapture", "⚠️ OCR识别失败或文本为空，将创建空白题目")
                "（图片识别中，请手动编辑）"
            }
            
            // 使用 QuestionDetector 提取题干和选项信息
            val detector = QuestionDetector()
            val detection = if (ocrResult.success && ocrResult.rawText.isNotBlank()) {
                Log.i("CameraCapture", "开始题目检测...")
                detector.detect(ocrResult)
            } else {
                Log.w("CameraCapture", "⚠️ OCR失败，跳过题目检测")
                com.gongkao.cuotifupan.detector.DetectionResult(
                    isQuestion = false,
                    confidence = 0.0f,
                    questionText = "",
                    options = emptyList()
                )
            }
            
            Log.i("CameraCapture", "题目检测结果:")
            Log.i("CameraCapture", "  - 是否为题目: ${detection.isQuestion}")
            Log.i("CameraCapture", "  - 题干: ${detection.questionText.take(100)}")
            Log.i("CameraCapture", "  - 选项数量: ${detection.options.size}")
            Log.i("CameraCapture", "  - 置信度: ${detection.confidence}")
            
            // 判断题目类型
            val questionType = if (ocrResult.success && ocrResult.rawText.isNotBlank()) {
                determineQuestionType(ocrResult.rawText, detection.questionText)
            } else {
                "TEXT" // 默认类型
            }
            Log.i("CameraCapture", "题目类型: $questionType")
            
            // 如果检测结果不是题目，使用OCR原始文本作为题干
            val questionText = when {
                detection.isQuestion && detection.questionText.isNotBlank() -> detection.questionText
                ocrResult.success && ocrResult.rawText.isNotBlank() -> ocrResult.rawText.take(200).trim()
                else -> "（请手动编辑题目）"
            }
            
            // 创建题目对象
            val question = Question(
                imagePath = processedImagePath,
                rawText = rawText,
                questionText = questionText,
                frontendRawText = rawText,
                options = JSONArray(detection.options).toString(),
                confidence = if (detection.isQuestion) detection.confidence else 0.5f,
                questionType = questionType
            )
            
            Log.i("CameraCapture", "创建题目对象:")
            Log.i("CameraCapture", "  - ID: ${question.id}")
            Log.i("CameraCapture", "  - 图片路径: ${question.imagePath}")
            Log.i("CameraCapture", "  - 题干: ${question.questionText.take(100)}")
            
            // 保存图片到永久存储
            Log.i("CameraCapture", "保存图片到永久存储...")
            val permanentImagePath = com.gongkao.cuotifupan.util.ImageAccessHelper.saveImageToPermanentStorage(
                this@CameraCaptureActivity, processedImagePath, question.id
            )
            
            // 如果保存失败，使用原路径
            val finalImagePath = permanentImagePath ?: processedImagePath
            Log.i("CameraCapture", "最终图片路径: $finalImagePath")
            
            // 更新题目对象，使用永久存储路径
            val finalQuestion = question.copy(imagePath = finalImagePath)
            
            // 保存到数据库
            Log.i("CameraCapture", "保存题目到数据库...")
            database.questionDao().insert(finalQuestion)
            Log.i("CameraCapture", "✅ 题目已保存到数据库: ${finalQuestion.id}")
            Log.i("CameraCapture", "========== 导入完成 ==========")
            
            // 如果是文字题，异步调用后端API
            if (questionType == "TEXT") {
                Log.i("CameraCapture", "📤 文字题，准备调用后端API")
                try {
                    QuestionApiQueue.enqueue(
                        question = finalQuestion,
                        onSuccess = { response ->
                            withContext(Dispatchers.IO) {
                                try {
                                    Log.i("CameraCapture", "✅ 后端API调用成功")
                                    val updatedQuestion = finalQuestion.copy(
                                        backendQuestionId = response.id,
                                        backendQuestionText = response.questionText,
                                        rawText = response.rawText,
                                        questionText = response.questionText,
                                        options = JSONArray(response.options).toString(),
                                        answerLoaded = false
                                    )
                                    database.questionDao().update(updatedQuestion)
                                    Log.i("CameraCapture", "✅ 文字题已更新到数据库")
                                } catch (e: Exception) {
                                    Log.e("CameraCapture", "更新题目失败", e)
                                }
                            }
                        },
                        onError = { error ->
                            Log.e("CameraCapture", "❌ 后端API调用失败: ${error.message}")
                        }
                    )
                } catch (e: Exception) {
                    Log.e("CameraCapture", "调用API队列失败", e)
                }
            }
            
            true
        } catch (e: Exception) {
            Log.e("CameraCapture", "导入图片失败: $imagePath", e)
            false
        }
    }
    
    /**
     * 判断题目类型（文字题 vs 图推题）
     */
    private fun determineQuestionType(rawText: String, questionText: String): String {
        val combinedText = (rawText + " " + questionText).lowercase()
        val trimmedText = combinedText.trim()
        
        // 图推题的强关键词
        val strongGraphicKeywords = listOf(
            "填入问号", "问号处", "填入问号处",
            "从所给的", "从所给", "呈现一定的规律性", "呈现一定的规律",
            "图形", "图形分为", "图形分类", "图形推理",
            "六个图形", "四个图形", "五个图形", "三个图形"
        )
        
        if (strongGraphicKeywords.any { combinedText.contains(it) }) {
            return "GRAPHIC"
        }
        
        // 文字题的关键词
        val textQuestionKeywords = listOf(
            "最恰当的一项", "最恰当的是",
            "正确的是", "错误的是", "不正确的是",
            "填入画横线", "填入划横线", "填入横线",
            "多选题", "判断题", "填空题", "问答题"
        )
        
        if (textQuestionKeywords.any { combinedText.contains(it) }) {
            return "TEXT"
        }
        
        // 默认标记为文字题
        return "TEXT"
    }
    
    override fun onSupportNavigateUp(): Boolean {
        Log.i("CameraCapture", "用户点击返回按钮")
        Log.i("CameraCapture", "当前Activity状态: isFinishing=${isFinishing}, isDestroyed=${isDestroyed}")
        finish()
        return true
    }
    
    override fun onDestroy() {
        Log.i("CameraCapture", "========== onDestroy 被调用 ==========")
        Log.i("CameraCapture", "isFinishing: $isFinishing")
        
        // 关闭所有对话框，防止窗口泄漏
        hideAutoDetectDialog()
        importSuccessDialog?.dismiss()
        importSuccessDialog = null
        
        Log.i("CameraCapture", "调用栈:")
        Thread.currentThread().stackTrace.take(10).forEach {
            Log.i("CameraCapture", "  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})")
        }
        super.onDestroy()
        // 清理临时文件
        photoFile?.let { file ->
            if (file.exists()) {
                try {
                    file.delete()
                } catch (e: Exception) {
                    Log.w("CameraCapture", "删除临时文件失败", e)
                }
            }
        }
        currentBitmap?.recycle()
    }
}


