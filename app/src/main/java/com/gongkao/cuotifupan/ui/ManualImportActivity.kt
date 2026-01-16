package com.gongkao.cuotifupan.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.api.QuestionApiQueue
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.detector.QuestionDetector
import com.gongkao.cuotifupan.ocr.TextRecognizer
import com.gongkao.cuotifupan.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * 手动导入 Activity：显示未导入的图片列表，支持多选和放大查看
 */
class ManualImportActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    private lateinit var emptyView: View
    private lateinit var adapter: ImageGridAdapter
    
    private val imageList = mutableListOf<ImageInfo>()
    private val selectedImages = mutableSetOf<String>() // 选中的图片路径集合
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            loadImages()
        } else {
            // 权限被拒绝，显示说明对话框
            showPermissionExplanationDialog()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_manual_import)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "手动导入题目"
        
        initViews()
        ensureStoragePermissionAndLoad()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        progressBar = findViewById(R.id.progressBar)
        statusText = findViewById(R.id.statusText)
        emptyView = findViewById(R.id.emptyView)
        
        // 设置网格布局（3列）
        recyclerView.layoutManager = GridLayoutManager(this, 3)
        
        adapter = ImageGridAdapter(
            selectedImages,
            onImageClick = { imageInfo, position ->
                // 点击图片放大查看
                openImageFullscreen(position)
            },
            onItemClick = { imageInfo ->
                // 点击选择框切换选择状态
                toggleSelection(imageInfo)
            }
        )
        recyclerView.adapter = adapter
    }
    
    /**
     * 检查/申请存储权限后再加载图片
     */
    private fun ensureStoragePermissionAndLoad() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        val granted = ContextCompat.checkSelfPermission(this, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            loadImages()
        } else {
            // 检查是否已经显示过权限说明
            val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val hasShownPermissionExplanation = prefs.getBoolean("has_shown_permission_explanation", false)
            
            if (!hasShownPermissionExplanation) {
                // 首次请求权限，先显示说明对话框
                showPermissionExplanationDialog()
            } else {
                // 已经显示过说明，直接请求权限
                storagePermissionLauncher.launch(permission)
            }
        }
    }
    
    /**
     * 显示权限说明对话框
     */
    private fun showPermissionExplanationDialog() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.permission_explanation_title)
            .setMessage(R.string.permission_explanation_message)
            .setPositiveButton(R.string.permission_explanation_agree) { dialog, _ ->
                // 标记已显示过说明
                val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_shown_permission_explanation", true).apply()
                
                // 请求权限
                storagePermissionLauncher.launch(permission)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.permission_explanation_later) { dialog, _ ->
                // 用户选择稍后，标记已显示过说明
                val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_shown_permission_explanation", true).apply()
                
                Toast.makeText(
                    this,
                    "需要相册权限才能使用此功能，您可以在设置中手动授予权限",
                    Toast.LENGTH_LONG
                ).show()
                dialog.dismiss()
                finish() // 关闭当前页面
            }
            .setCancelable(false) // 不允许点击外部取消
            .show()
    }
    
    /**
     * 加载图片列表（只显示未导入的图片）
     */
    private fun loadImages() {
        progressBar.visibility = View.VISIBLE
        statusText.text = "正在加载图片..."
        emptyView.visibility = View.GONE
        recyclerView.visibility = View.GONE
        
        lifecycleScope.launch {
            val images = withContext(Dispatchers.IO) {
                queryUnimportedImages()
            }
            
            imageList.clear()
            imageList.addAll(images)
            adapter.submitList(imageList.toList())
            
            progressBar.visibility = View.GONE
            
            if (imageList.isEmpty()) {
                emptyView.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
                statusText.text = "没有未导入的图片"
            } else {
                emptyView.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
                statusText.text = "共 ${imageList.size} 张未导入的图片"
            }
        }
    }
    
    /**
     * 查询未导入的图片
     */
    private suspend fun queryUnimportedImages(): List<ImageInfo> = withContext(Dispatchers.IO) {
        val result = mutableListOf<ImageInfo>()
        
        try {
            // 获取已导入的图片路径集合和文件大小（用于去重）
            val database = AppDatabase.getDatabase(this@ManualImportActivity)
            val existingQuestions = database.questionDao().getAllQuestionsSync()
            
            // 收集所有相关路径（包括imagePath、originalImagePath、cleanedImagePath）
            val importedPaths = mutableSetOf<String>()
            existingQuestions.forEach { question ->
                importedPaths.add(question.imagePath)
                question.originalImagePath?.let { importedPaths.add(it) }
                question.cleanedImagePath?.let { importedPaths.add(it) }
            }
            
            // 获取已扫描并标记为题目的图片路径（这些是已导入的原始图片路径）
            val scannedQuestionPaths = database.scannedImageDao().getQuestionPaths().toSet()
            
            // 获取已排除的图片路径（用于记录文件大小，但不用于路径匹配，因为被排除的图片也可以手动导入）
            val excludedPaths = database.excludedImageDao().getAllPaths().toSet()
            
            // 合并所有已导入到题目的路径（包括处理后的路径、原始路径、原图路径、擦写后的路径）
            // 注意：不包括已排除的路径，这样用户可以手动导入被排除的图片
            val allImportedPaths = importedPaths + scannedQuestionPaths
            
            // 记录已导入图片的文件大小集合（用于去重，通过文件大小匹配，因为复制后大小应该相同）
            val importedSizes = mutableSetOf<Long>()
            existingQuestions.forEach { question ->
                // 检查所有相关路径的文件大小
                listOfNotNull(question.imagePath, question.originalImagePath, question.cleanedImagePath).forEach { path ->
                    try {
                        if (!path.startsWith("content://")) {  // URI路径无法直接获取大小
                            val file = java.io.File(path)
                            if (file.exists()) {
                                val fileSize = file.length()
                                if (fileSize > 0) {
                                    importedSizes.add(fileSize)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // 忽略错误
                    }
                }
            }
            
            // 同时记录已扫描题目的文件大小（不包括已排除的图片，因为被排除的图片应该可以重新导入）
            scannedQuestionPaths.forEach { path ->
                try {
                    if (!path.startsWith("content://")) {  // URI路径无法直接获取大小
                        val file = java.io.File(path)
                        if (file.exists()) {
                            val fileSize = file.length()
                            if (fileSize > 0) {
                                importedSizes.add(fileSize)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // 忽略错误
                }
            }
            
            Log.d("ManualImport", "已导入 ${importedPaths.size} 张图片（处理后路径），${scannedQuestionPaths.size} 张图片（原始路径），记录了 ${importedSizes.size} 个文件大小")
            
            // 查询所有图片
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val cursor = contentResolver.query(
                uri,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )
            
            cursor?.use {
                val idIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                
                // DATA 字段在 Android 10+ 可能为 null，需要处理
                val dataIndex = it.getColumnIndex(MediaStore.Images.Media.DATA)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idIndex)
                    val name = it.getString(nameIndex)
                    val dateAdded = it.getLong(dateIndex)
                    
                    // 获取图片路径
                    val path = if (dataIndex >= 0 && !it.isNull(dataIndex)) {
                        // 有 DATA 字段，直接使用
                        it.getString(dataIndex)
                    } else {
                        // Android 10+ 没有 DATA 字段，使用 URI 并尝试获取路径
                        val imageUri = Uri.withAppendedPath(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        getRealPathFromURI(imageUri) ?: imageUri.toString()
                    }
                    
                    // 检查是否已导入（通过路径和文件大小双重检查）
                    // 注意：手动导入应该显示所有未导入的图片，包括被排除的图片
                    // 这样用户可以手动选择导入被排除的图片
                    var isImported = false
                    
                    // 1. 先检查路径是否在题目中（不包括排除列表，因为排除的图片可以重新导入）
                    val isInQuestions = importedPaths.contains(path) || scannedQuestionPaths.contains(path)
                    if (isInQuestions) {
                        isImported = true
                        Log.d("ManualImport", "⏭️ 通过路径匹配检测到已在题目中: $name")
                    } else {
                        // 2. 如果路径不匹配，通过文件大小来判断（复制后文件大小应该相同）
                        // 注意：Android 10+ 如果path是URI（content://），无法直接获取文件大小，需要跳过大小检查
                        if (!path.startsWith("content://")) {
                            try {
                                val currentFile = java.io.File(path)
                                if (currentFile.exists()) {
                                    val currentSize = currentFile.length()
                                    if (currentSize > 0 && currentSize in importedSizes) {
                                        isImported = true
                                        Log.d("ManualImport", "⏭️ 通过文件大小检测到已导入: $name (大小: $currentSize)")
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略检查错误，继续处理
                            }
                        } else {
                            // 对于URI路径（Android 10+），尝试获取文件大小进行去重检查
                            try {
                                val uri = Uri.parse(path)
                                // 使用 openFileDescriptor 获取文件大小（比读取输入流更高效）
                                contentResolver.openFileDescriptor(uri, "r")?.use { parcelFileDescriptor ->
                                    val fileSize = parcelFileDescriptor.statSize
                                    if (fileSize > 0 && fileSize in importedSizes) {
                                        isImported = true
                                        Log.d("ManualImport", "⏭️ 通过URI文件大小检测到已导入: $name (大小: $fileSize)")
                                    }
                                }
                            } catch (e: Exception) {
                                // 忽略检查错误，继续处理（URI路径可能无法获取大小，直接允许导入）
                                Log.d("ManualImport", "无法获取URI文件大小，允许导入: $name (${e.message})")
                            }
                        }
                    }
                    
                    // 只添加未导入的图片
                    if (!isImported) {
                        // 不在这里验证图片文件，直接添加，验证放在导入时进行
                        // 这样可以避免在查询时因为文件验证导致卡顿
                        result.add(ImageInfo(id, path, name, dateAdded))
                    }
                }
            }
            
            Log.d("ManualImport", "查询到 ${result.size} 张未导入的图片")
        } catch (e: Exception) {
            Log.e("ManualImport", "查询图片失败", e)
        }
        
        result
    }
    
    /**
     * 切换图片选择状态
     */
    private fun toggleSelection(imageInfo: ImageInfo) {
        if (selectedImages.contains(imageInfo.path)) {
            selectedImages.remove(imageInfo.path)
        } else {
            selectedImages.add(imageInfo.path)
        }
        
        // 更新选中项
        val position = imageList.indexOfFirst { it.path == imageInfo.path }
        if (position >= 0) {
            adapter.notifyItemChanged(position)
        }
        
        // 更新标题显示选中数量
        updateTitle()
    }
    
    /**
     * 更新标题显示选中数量
     */
    private fun updateTitle() {
        if (selectedImages.isEmpty()) {
            supportActionBar?.title = "手动导入题目"
        } else {
            supportActionBar?.title = "已选择 ${selectedImages.size} 张"
        }
    }
    
    /**
     * 打开全屏查看图片
     */
    private fun openImageFullscreen(position: Int) {
        if (position < 0 || position >= imageList.size) return
        
        // 设置图片路径列表到缓存
        val imagePaths = imageList.map { it.path }
        ImagePathCache.setImagePaths(imagePaths)
        
        // 启动全屏查看 Activity
        val intent = Intent(this, ImageFullscreenActivity::class.java).apply {
            putExtra(ImageFullscreenActivity.EXTRA_CURRENT_POSITION, position)
        }
        startActivity(intent)
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.manual_import_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_import -> {
                // 导入选中的图片
                importSelectedImages()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    /**
     * 导入选中的图片
     */
    private fun importSelectedImages() {
        if (selectedImages.isEmpty()) {
            Toast.makeText(this, "请先选择要导入的图片", Toast.LENGTH_SHORT).show()
            return
        }
        
        progressBar.visibility = View.VISIBLE
        statusText.text = "正在导入 ${selectedImages.size} 张图片..."
        
        lifecycleScope.launch {
            var successCount = 0
            var questionCount = 0
            var errorCount = 0
            
            selectedImages.forEachIndexed { index, imagePathOrUri ->
                try {
                    // 更新状态
                    statusText.text = "正在处理 ${index + 1}/${selectedImages.size}..."
                    
                val result = withContext(Dispatchers.IO) {
                    // 如果是 URI，先转换为路径
                    val actualPath = if (imagePathOrUri.startsWith("content://")) {
                        getRealPathFromURI(Uri.parse(imagePathOrUri)) ?: imagePathOrUri
                    } else {
                        imagePathOrUri
                    }
                        
                        Log.d("ManualImport", "处理图片 ${index + 1}/${selectedImages.size}: $actualPath")
                    processAndImportImage(actualPath)
                }
                
                if (result.success) {
                    successCount++
                    if (result.isQuestion) {
                        questionCount++
                    }
                    } else {
                        errorCount++
                        Log.w("ManualImport", "处理失败: $imagePathOrUri")
                    }
                } catch (e: Exception) {
                    errorCount++
                    Log.e("ManualImport", "处理图片异常: $imagePathOrUri", e)
                }
            }
            
            progressBar.visibility = View.GONE
            
            val message = when {
                questionCount > 0 -> "✅ 成功导入 $questionCount 道题目（共处理 $successCount 张图片）"
                errorCount > 0 -> "⚠️ 处理了 $successCount 张图片，$errorCount 张失败"
                else -> "✅ 成功处理 $successCount 张图片"
            }
            
            Toast.makeText(this@ManualImportActivity, message, Toast.LENGTH_LONG).show()
            
            // 清空选择并刷新列表
            selectedImages.clear()
            updateTitle()
            // 立即刷新列表（题目已经同步保存到数据库）
            loadImages()
        }
    }
    
    /**
     * 验证图片文件是否有效
     * @param imagePath 图片路径
     * @return true 如果文件存在、非0字节且可以解码为有效图片
     */
    private suspend fun isValidImageFile(imagePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = java.io.File(imagePath)
            
            // 检查文件是否存在
            if (!file.exists()) {
                return@withContext false
            }
            
            // 检查文件大小，如果为0则等待并重试
            var fileSize = file.length()
            val isEditedImage = imagePath.contains("_edited_", ignoreCase = true)
            
            if (fileSize == 0L) {
                // 对于编辑后的图片，即使时间戳较旧，也尝试重试
                val shouldRetry = if (isEditedImage) {
                    true // 编辑后的图片总是尝试重试
                } else {
                    val lastModified = file.lastModified()
                    val timeSinceModified = System.currentTimeMillis() - lastModified
                    timeSinceModified < 5000L // 普通图片只在5秒内重试
                }
                
                if (shouldRetry) {
                    val maxRetries = if (isEditedImage) 10 else 6
                    var retryCount = 0
                    while (retryCount < maxRetries && fileSize == 0L) {
                        kotlinx.coroutines.delay(500)
                        fileSize = file.length()
                        retryCount++
                        if (fileSize > 0L) {
                            break
                        }
                    }
                }
                
                // 如果重试后仍然为0，认为无效
                if (fileSize == 0L) {
                    return@withContext false
                }
            }
            
            // 验证图片是否有效（使用 ImageAccessHelper，兼容 Android 10+ Scoped Storage）
            com.gongkao.cuotifupan.util.ImageAccessHelper.isValidImage(this@ManualImportActivity, imagePath)
        } catch (e: Exception) {
            Log.w("ManualImport", "验证图片文件失败: $imagePath", e)
            false
        }
    }
    
    /**
     * 从 URI 获取真实文件路径
     */
    private fun getRealPathFromURI(uri: Uri): String? {
        var result: String? = null
        
        // Android 10+ 使用不同的方式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                contentResolver.openInputStream(uri)?.use { inputStream ->
                    // 创建临时文件
                    val tempFile = java.io.File(cacheDir, "temp_${System.currentTimeMillis()}.jpg")
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    result = tempFile.absolutePath
                }
            } catch (e: Exception) {
                Log.e("ManualImport", "从 URI 读取文件失败", e)
            }
        } else {
            // Android 9 及以下使用传统方式
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(MediaStore.Images.ImageColumns.DATA)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        
        return result
    }
    
    /**
     * 处理并导入图片
     */
    private suspend fun processAndImportImage(imagePath: String): ImportResult {
        return try {
            // 验证图片是否有效
            if (!com.gongkao.cuotifupan.util.ImageAccessHelper.isValidImage(this@ManualImportActivity, imagePath)) {
                Log.w("ManualImport", "🚫 图片文件无效或无法访问: $imagePath")
                return ImportResult(false, false)
            }
            
            // 对于 Android 10+ 的 Scoped Storage，需要先复制到临时文件
            val (workingFilePath, tempFile) = if (imagePath.startsWith(cacheDir.absolutePath) ||
                                     imagePath.startsWith(filesDir.absolutePath)) {
                // 已经是应用私有文件，直接使用
                Pair(imagePath, null)
            } else {
                // 复制到临时文件
                // 处理URI路径的情况（Android 10+）
                val fileName = if (imagePath.startsWith("content://")) {
                    // URI路径，从URI中提取文件名，或使用时间戳
                    val uri = Uri.parse(imagePath)
                    uri.lastPathSegment ?: "image_${System.currentTimeMillis()}.jpg"
                } else {
                    java.io.File(imagePath).name
                }
                val tempFile = java.io.File(cacheDir, "temp_${System.currentTimeMillis()}_${fileName}")
                val copySuccess = com.gongkao.cuotifupan.util.ImageAccessHelper.copyToPrivateStorage(
                    this@ManualImportActivity, imagePath, tempFile
                )
                if (!copySuccess) {
                    Log.e("ManualImport", "❌ 无法复制图片到临时文件: $imagePath")
                    return ImportResult(false, false)
                }
                Log.d("ManualImport", "✅ 图片已复制到临时文件: ${tempFile.absolutePath}")
                Pair(tempFile.absolutePath, tempFile)
            }
            
            try {
                // 自动处理图片：旋转和裁剪
                val processedImagePath = withContext(Dispatchers.IO) {
                    com.gongkao.cuotifupan.util.ImageEditor.autoProcessImage(workingFilePath)
                }
            
            // 检查是否已导入（使用处理后的图片路径）
            val database = AppDatabase.getDatabase(this@ManualImportActivity)
            val existingQuestions = database.questionDao().getAllQuestionsSync()
            if (existingQuestions.any { it.imagePath == processedImagePath }) {
                return ImportResult(false, false) // 已存在，跳过
            }
            
            // OCR 识别（使用处理后的图片）
            val recognizer = TextRecognizer()
            val ocrResult = recognizer.recognizeText(processedImagePath)
            
            if (!ocrResult.success) {
                Log.w("ManualImport", "OCR识别失败: ${ocrResult.errorMessage}")
                return ImportResult(false, false)
            }
            
            Log.i("ManualImport", "========== ML Kit OCR 识别结果 ==========")
            Log.i("ManualImport", "OCR识别成功，文本长度: ${ocrResult.rawText.length}")
            Log.i("ManualImport", "  - 文本内容: [${ocrResult.rawText.take(500)}]")
            if (ocrResult.rawText.length > 500) {
                Log.i("ManualImport", "  - 文本内容(续): [${ocrResult.rawText.substring(500).take(500)}]")
            }
            
            // 同时使用 PaddleOCR 识别并对比
            try {
                Log.i("ManualImport", "========== PaddleOCR 识别开始 ==========")
                val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(this@ManualImportActivity, processedImagePath)
                if (bitmap != null) {
                    // 初始化 PaddleOCR（如果还未初始化）
                    if (!com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.isInitialized()) {
                        val initSuccess = com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.init(this@ManualImportActivity)
                        Log.i("ManualImport", "PaddleOCR 初始化: ${if (initSuccess) "成功" else "失败"}")
                    }
                    
                    // 使用 PaddleOCR 识别
                    val paddleResult = com.gongkao.cuotifupan.ocr.paddle.PaddleOcrHelper.recognizeText(bitmap)
                    Log.i("ManualImport", "========== PaddleOCR 识别结果 ==========")
                    if (paddleResult != null) {
                        Log.i("ManualImport", "  - 文本长度: ${paddleResult.length}")
                        Log.i("ManualImport", "  - 文本内容: [${paddleResult.take(500)}]")
                        if (paddleResult.length > 500) {
                            Log.i("ManualImport", "  - 文本内容(续): [${paddleResult.substring(500).take(500)}]")
                        }
                    } else {
                        Log.w("ManualImport", "  - 识别结果: null（识别失败）")
                    }
                    
                    // 对比结果
                    Log.i("ManualImport", "========== OCR 结果对比 ==========")
                    Log.i("ManualImport", "ML Kit 结果长度: ${ocrResult.rawText.length}")
                    Log.i("ManualImport", "PaddleOCR 结果长度: ${paddleResult?.length ?: 0}")
                    Log.i("ManualImport", "结果是否相同: ${ocrResult.rawText == paddleResult}")
                    if (ocrResult.rawText != paddleResult) {
                        Log.i("ManualImport", "结果不同，差异分析:")
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
                                    Log.i("ManualImport", "  位置 $i: ML Kit='${mlKitText.substring(start, end)}' vs PaddleOCR='${paddleText.substring(start, end)}'")
                                }
                            }
                        }
                        if (diffCount > 10) {
                            Log.i("ManualImport", "  ... 还有 ${diffCount - 10} 个差异位置")
                        }
                        if (mlKitText.length != paddleText.length) {
                            Log.i("ManualImport", "  长度差异: ${mlKitText.length - paddleText.length} 字符")
                        }
                    }
                    Log.i("ManualImport", "=====================================")
                    
                    bitmap.recycle()
                } else {
                    Log.w("ManualImport", "无法解码图片为 Bitmap，跳过 PaddleOCR 识别")
                }
            } catch (e: Exception) {
                Log.e("ManualImport", "PaddleOCR 识别过程出错", e)
            }
            
            // 手动导入时，跳过题目检测，直接导入（用户手动选择就是要导入的）
            // 但仍然使用 QuestionDetector 来提取题干和选项信息
            val detector = QuestionDetector()
            val detection = detector.detect(ocrResult)
            
            // 手动导入时，无论检测结果如何，都强制导入
            Log.i("ManualImport", "手动导入模式：跳过题目检测，直接导入（检测置信度: ${detection.confidence}）")
                
                // 判断题目类型（文字题 vs 图推题）
                val questionType = determineQuestionType(ocrResult.rawText, detection.questionText)
                Log.i("ManualImport", "题目类型判断: $questionType")
            
            // 如果检测结果不是题目，使用OCR原始文本作为题干
            val questionText = if (detection.isQuestion && detection.questionText.isNotBlank()) {
                detection.questionText
            } else {
                // 使用OCR原始文本的前200个字符作为题干
                ocrResult.rawText.take(200).trim()
            }
                
                // 先创建题目对象（用于生成ID）
                val question = Question(
                    imagePath = processedImagePath,  // 临时路径，稍后会更新
                    rawText = ocrResult.rawText,  // 初始使用前端OCR结果
                questionText = questionText,  // 使用检测的题干或OCR文本
                    frontendRawText = ocrResult.rawText,  // 保存前端OCR结果，用于发送给后端
                    options = JSONArray(detection.options).toString(),
                confidence = if (detection.isQuestion) detection.confidence else 0.5f,  // 手动导入时给予默认置信度
                    questionType = questionType  // 根据关键词判断类型
                )
                
                // 保存图片到永久存储
                val permanentImagePath = com.gongkao.cuotifupan.util.ImageAccessHelper.saveImageToPermanentStorage(
                    this@ManualImportActivity, processedImagePath, question.id
                )
                
                // 如果保存失败，使用原路径（可能是应用私有文件）
                // 但如果原路径是临时文件，需要确保不会被删除
                val finalImagePath = if (permanentImagePath != null) {
                    permanentImagePath
                } else {
                    // 如果保存失败，检查是否是临时文件
                    // 如果是临时文件且保存失败，返回错误（不应该使用会被删除的临时文件）
                    if (processedImagePath.startsWith(cacheDir.absolutePath) && 
                        processedImagePath.contains("temp_")) {
                        Log.e("ManualImport", "❌ 无法保存图片到永久存储，且原路径是临时文件: $processedImagePath")
                        return ImportResult(false, false)
                    }
                    processedImagePath
                }
                
                Log.d("ManualImport", "最终图片路径: $finalImagePath")
                
                // 更新题目对象，使用永久存储路径
                val finalQuestion = question.copy(imagePath = finalImagePath)
            
            // 手动导入时，先直接保存到数据库（确保立即可见）
            database.questionDao().insert(finalQuestion)
            Log.i("ManualImport", "✅ 题目已保存到数据库: ${finalQuestion.id}, 图片路径: ${finalQuestion.imagePath}")
            
            // 记录扫描结果（标记为题目）
            try {
                val originalPath = imagePath // 原始图片路径
                val originalFile = java.io.File(originalPath)
                if (originalFile.exists()) {
                    val scannedImage = com.gongkao.cuotifupan.data.ScannedImage(
                        imagePath = originalPath,
                        fileName = originalFile.name,
                        fileSize = originalFile.length(),
                        isQuestion = true, // 手动导入的标记为题目
                        mediaStoreId = 0 // 手动导入可能没有 MediaStore ID
                    )
                    database.scannedImageDao().insert(scannedImage)
                    Log.d("ManualImport", "已记录扫描结果: ${originalFile.name} (手动导入，标记为题目)")
                }
            } catch (e: Exception) {
                Log.e("ManualImport", "记录扫描结果失败", e)
            }
                
                // 根据题目类型处理
                if (questionType == "TEXT") {
                // 文字题：异步调用后端API获取题目内容（后台更新，不影响导入结果）
                    Log.i("ManualImport", "📤 文字题，准备调用后端API获取题目内容")
                    Log.i("ManualImport", "   - 题目ID: ${finalQuestion.id}")
                    Log.i("ManualImport", "   - 图片路径: ${finalQuestion.imagePath}")
                    
                    try {
                        QuestionApiQueue.enqueue(
                            question = finalQuestion,
                            onSuccess = { response ->
                                withContext(Dispatchers.IO) {
                                    try {
                                        Log.i("ManualImport", "✅ 后端API调用成功")
                                        // 更新题目信息（使用后端返回的完整文字，替换前端OCR的结果）
                                        val updatedQuestion = finalQuestion.copy(
                                            backendQuestionId = response.id,
                                            backendQuestionText = response.questionText,
                                            rawText = response.rawText,  // 更新为后端返回的rawText
                                            questionText = response.questionText,  // 更新为后端返回的questionText
                                            options = JSONArray(response.options).toString(),  // 更新为后端返回的options
                                            answerLoaded = false
                                        )
                                        database.questionDao().update(updatedQuestion)
                                        Log.i("ManualImport", "✅ 文字题已更新到数据库")
                                    } catch (e: Exception) {
                                        Log.e("ManualImport", "更新题目失败", e)
                                    }
                                }
                            },
                            onError = { error ->
                                    Log.e("ManualImport", "❌ 后端API调用失败: ${error.message}")
                            // API请求失败不影响，题目已经保存
                            }
                        )
                    } catch (e: Exception) {
                        Log.e("ManualImport", "调用API队列失败", e)
                    // 调用失败不影响，题目已经保存
                }
                    }
            
            // 手动导入时，无论题目类型如何，都返回成功
                    return ImportResult(true, true)
            } finally {
                // 清理临时文件
                tempFile?.also {
                    try {
                        if (it.exists()) {
                            it.delete()
                            Log.d("ManualImport", "🗑️ 临时文件已删除: ${it.absolutePath}")
                        }
                    } catch (e: Exception) {
                        Log.w("ManualImport", "清理临时文件失败: ${it.absolutePath}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ManualImport", "处理图片失败: $imagePath", e)
            ImportResult(false, false)
        }
    }
    
    /**
     * 判断题目类型（文字题 vs 图推题）
     * 基于OCR文本中的关键词和特征来判断
     */
    private fun determineQuestionType(rawText: String, questionText: String): String {
        Log.d("ManualImport", "========== 开始判断题目类型 ==========")
        Log.d("ManualImport", "rawText预览: ${rawText.take(100)}...")
        Log.d("ManualImport", "questionText预览: ${questionText.take(100)}...")
        
        val combinedText = (rawText + " " + questionText).lowercase()
        val trimmedText = combinedText.trim()
        val textLength = trimmedText.length
        
        // 图推题的强关键词（优先级最高，即使有"单选题"等标记也优先判断为图推题）
        val strongGraphicKeywords = listOf(
            "填入问号", "问号处", "填入问号处",
            "从所给的", "从所给", "从所", "从所始",
            "选择最合适的一个填入问号", "选择最合适的一个填入",
            "呈现一定的规律性", "呈现一定的规律", "呈现规律性", "呈现规律",
            "图形", "图形分为", "图形分类", "图形推理",
            "六个图形", "四个图形", "五个图形", "三个图形"
        )
        
        // 优先检查图推题强关键词
        val hasStrongGraphicKeyword = strongGraphicKeywords.any { keyword ->
            combinedText.contains(keyword)
        }
        
        if (hasStrongGraphicKeyword) {
            return "GRAPHIC"
        }
        
        // 文字题的关键词（如果包含这些，判断为文字题）
        val textQuestionKeywords = listOf(
            "最恰当的一项", "最恰当的是", "最怡当的一项", "最怡当的是",
            "正确的是", "错误的是", "不正确的是",
            "填入画横线", "填入划横线", "填入横线", "填入画橫线", "填入划橫线",
            "镇入画横线", "镇入画橫线", "镇入划横线", "镇入划橫线",
            "慎入画横线", "慎入画橫线", "慎入划横线", "慎入划橫线",
            "顷入画横线", "顷入画橫线", "顷入划横线", "顷入划橫线",
            "画横线部分", "画橫线部分", "划横线部分", "划橫线部分",
            // OCR错误变体
            "面橫线部分", "面横线部分", "面橫线", "面横线",  // "画"可能识别为"面"
            "填入面橫线", "填入面横线", "镇入面橫线", "镇入面横线",
            "慎入面橫线", "慎入面横线", "顷入面橫线", "顷入面横线",
            "多选题", "判断题", "填空题", "问答题"
            // 注意：不包含"单选题"和"选择题"，因为图推题也可能是单选题
        )
        
        // 如果包含文字题关键词，判断为文字题
        val hasTextQuestionKeyword = textQuestionKeywords.any { keyword ->
            combinedText.contains(keyword)
        }
        
        if (hasTextQuestionKeyword) {
            Log.i("ManualImport", "✅ 检测到文字题关键词，判断为文字题")
            return "TEXT"
        }
        
        // 图推题的其他关键词（优先级较低）
        val graphicKeywords = listOf(
            "规律性", "规律",
            "分为两类", "分为", "分类",
            "选择最合适", "选择最恰当", "选择最"
        )
        
        // 检查是否包含图推题关键词
        val hasGraphicKeyword = graphicKeywords.any { keyword ->
            combinedText.contains(keyword)
        }
        
        if (hasGraphicKeyword) {
            return "GRAPHIC"
        }
        
        // 特殊检测：OCR文本很短且包含问号，可能是图推题
        // 计算有效文本长度（去掉换行符、空格、选项标记、数字选项标记、问号）
        val cleanText = trimmedText
            .replace(Regex("[\\n\\r\\s]"), "")
            .replace(Regex("[a-d]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[①②③④⑤⑥⑦⑧⑨⑩]"), "")
            .replace(Regex("[?？]"), "")
        val cleanTextLength = cleanText.length
        val hasQuestionMark = trimmedText.contains("?") || trimmedText.contains("？")
        
        // 计算选项标记和数字选项标记的比例
        val optionCount = trimmedText.replace(Regex("[\\n\\r\\s]"), "").let { text ->
            text.count { it in "aAbBcCdD" || it in "①②③④⑤⑥⑦⑧⑨⑩" || it in "?？" }
        }
        val totalLength = trimmedText.replace(Regex("[\\n\\r\\s]"), "").length
        val optionRatio = if (totalLength > 0) optionCount.toDouble() / totalLength else 0.0
        
        // 如果有效文本很短（少于30个字符）且包含问号，且选项标记比例高（>50%），很可能是图推题
        if (cleanTextLength < 30 && hasQuestionMark && optionRatio > 0.5) {
            return "GRAPHIC"
        }
        
        // 如果有效文本很短（少于25个字符）且包含问号，可能是图推题
        if (cleanTextLength < 25 && hasQuestionMark) {
            return "GRAPHIC"
        }
        
        // 如果有效文本很短（少于25个字符）且选项标记比例高（>50%），可能是图推题
        if (cleanTextLength < 25 && optionRatio > 0.5) {
            return "GRAPHIC"
        }
        
        // 如果文本很短（少于等于25个字符）且没有明显的题目关键词，可能是图推题
        if (cleanTextLength <= 25) {
            val hasTextKeywords = listOf(
                "下列", "正确的是", "错误的是", "属于", "不属于",
                "选择", "关于", "描述", "定义", "特点", "作用", "影响", "原因",
                "根据", "按照", "哪个", "什么", "如何", "怎样", "为什么",
                "最恰当", "填入", "画横线", "画橫线", "画横线部分", "画橫线部分"
            ).any { keyword -> combinedText.contains(keyword) }
            
            if (!hasTextKeywords && optionRatio > 0.35) {
                return "GRAPHIC"
            }
        }
        
        // 如果文本很长（>100字符），更可能是文字题
        if (textLength > 100) {
            Log.i("ManualImport", "✅ 文本较长(${textLength}字符)，判断为文字题")
            return "TEXT"
        }
        
        // 默认标记为文字题
        Log.i("ManualImport", "✅ 默认判断为文字题")
        Log.d("ManualImport", "========== 题目类型判断完成 ==========")
        return "TEXT"
    }
    
    /**
     * 图片信息
     */
    data class ImageInfo(
        val id: Long,
        val path: String,
        val name: String,
        val dateAdded: Long
    )
    
    /**
     * 导入结果
     */
    private data class ImportResult(
        val success: Boolean,
        val isQuestion: Boolean
    )
}
