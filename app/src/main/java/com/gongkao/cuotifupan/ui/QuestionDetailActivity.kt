package com.gongkao.cuotifupan.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.api.ApiClient
import com.gongkao.cuotifupan.api.AnswerVersion
import com.gongkao.cuotifupan.api.QuestionApiQueue
import com.gongkao.cuotifupan.api.QuestionDetailResponse
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.viewmodel.QuestionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gongkao.cuotifupan.util.ImageEditor
import com.gongkao.cuotifupan.util.PreferencesManager
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

/**
 * 题目详情页
 */
class QuestionDetailActivity : AppCompatActivity() {
    
    private lateinit var viewModel: QuestionViewModel
    private var currentQuestion: Question? = null
    
    // 当前显示的图片类型：true = 清除对错痕迹后，false = 原图
    private var showingHiddenOptionsImage: Boolean = false
    
    private lateinit var imageView: ImageView
    private lateinit var questionTextView: TextView
    private lateinit var optionsTextView: TextView
    private lateinit var notesEditText: EditText
    private lateinit var reviewGroup: RadioGroup
    private lateinit var saveButton: Button
    private lateinit var deleteButton: Button
    private lateinit var questionTypeText: TextView
    private lateinit var changeQuestionTypeButton: Button
    private lateinit var editImageButton: Button
    private lateinit var removeAnswerMarksButton: Button
    
    // 答案相关视图
    private lateinit var answerVersionsRecyclerView: RecyclerView
    private lateinit var answerVersionsAdapter: AnswerVersionsAdapter
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var answerSection: LinearLayout
    private lateinit var correctAnswerTextView: TextView
    private lateinit var explanationTextView: TextView
    private lateinit var tagsTextView: TextView
    private lateinit var knowledgePointsTextView: TextView
    
    private var questionDetailResponse: QuestionDetailResponse? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_question_detail)
        
        viewModel = ViewModelProvider(this)[QuestionViewModel::class.java]
        
        // 初始化视图
        imageView = findViewById(R.id.questionImageView)
        questionTextView = findViewById(R.id.questionTextView)
        optionsTextView = findViewById(R.id.optionsTextView)
        notesEditText = findViewById(R.id.notesEditText)
        reviewGroup = findViewById(R.id.reviewGroup)
        saveButton = findViewById(R.id.saveButton)
        deleteButton = findViewById(R.id.deleteButton)
        questionTypeText = findViewById(R.id.questionTypeText)
        changeQuestionTypeButton = findViewById(R.id.changeQuestionTypeButton)
        editImageButton = findViewById(R.id.editImageButton)
        removeAnswerMarksButton = findViewById(R.id.removeAnswerMarksButton)
        
        // 初始化答案相关视图
        answerVersionsRecyclerView = findViewById(R.id.answerVersionsRecyclerView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        answerSection = findViewById(R.id.answerSection)
        correctAnswerTextView = findViewById(R.id.correctAnswerTextView)
        explanationTextView = findViewById(R.id.explanationTextView)
        tagsTextView = findViewById(R.id.tagsTextView)
        knowledgePointsTextView = findViewById(R.id.knowledgePointsTextView)
        
        // 初始化答案版本列表
        answerVersionsAdapter = AnswerVersionsAdapter()
        answerVersionsRecyclerView.layoutManager = LinearLayoutManager(this)
        answerVersionsRecyclerView.adapter = answerVersionsAdapter
        
        // 获取题目ID
        val questionId = intent.getStringExtra("question_id")
        if (questionId != null) {
            loadQuestion(questionId)
        }
        
        // 保存按钮
        saveButton.setOnClickListener {
            saveChanges()
        }
        
        // 删除按钮
        deleteButton.setOnClickListener {
            deleteQuestion()
        }
        
        // 更改题目类型按钮
        changeQuestionTypeButton.setOnClickListener {
            showQuestionTypeDialog()
        }
        
        // 编辑图片按钮
        editImageButton.setOnClickListener {
            editImage()
        }
        
        // 清除对错标记按钮
        removeAnswerMarksButton.setOnClickListener {
            removeAnswerMarks()
        }
    }
    
    private fun loadQuestion(questionId: String) {
        lifecycleScope.launch {
            val question = viewModel.getQuestionById(questionId)
            if (question != null) {
                currentQuestion = question
                displayQuestion(question)
            } else {
                Toast.makeText(this@QuestionDetailActivity, "题目不存在", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun displayQuestion(question: Question) {
        // 初始化清除对错痕迹的显示状态
        // 如果有清除后的图片路径，默认显示清除后的图片
        if (!question.hiddenOptionsImagePath.isNullOrBlank()) {
            // 有清除后的图片，默认显示清除后的图片
            showingHiddenOptionsImage = true
        } else {
            // 没有清除后的图片，显示原图
            showingHiddenOptionsImage = false
        }
        
        // 显示题目类型
        when (question.questionType) {
            "MULTIPLE_CHOICE" -> {
                questionTypeText.text = "选择题"
                questionTypeText.setBackgroundResource(R.drawable.bg_question_type)
                // 选择题：检查答案是否已加载，如果未加载则请求
                if (question.answerLoaded && question.correctAnswer != null) {
                    displayLoadedAnswer(question)
                } else {
                    uploadQuestionAndGetAnswer(question)
                }
            }
            "TRUE_FALSE" -> {
                questionTypeText.text = "判断题"
                questionTypeText.setBackgroundResource(R.drawable.bg_question_type)
                // 判断题：检查答案是否已加载，如果未加载则请求
                if (question.answerLoaded && question.correctAnswer != null) {
                    displayLoadedAnswer(question)
                } else {
                    uploadQuestionAndGetAnswer(question)
                }
            }
            "SHORT_ANSWER" -> {
                questionTypeText.text = "简答题"
                questionTypeText.setBackgroundResource(R.drawable.bg_question_type)
                // 简答题：检查答案是否已加载，如果未加载则请求
                if (question.answerLoaded && question.correctAnswer != null) {
                    displayLoadedAnswer(question)
                } else {
                    uploadQuestionAndGetAnswer(question)
                }
            }
            "GRAPHIC" -> {
                questionTypeText.text = "图推题"
                questionTypeText.setBackgroundResource(R.drawable.bg_question_type_graphic)
                // 图推题不显示答案部分
                answerSection.visibility = View.GONE
            }
            "TEXT" -> {
                questionTypeText.text = "文字题"
                questionTypeText.setBackgroundResource(R.drawable.bg_question_type)
                // 文字题：检查答案是否已加载，如果未加载则请求
                if (question.answerLoaded && question.correctAnswer != null) {
                    // 答案已加载，直接显示
                    displayLoadedAnswer(question)
                } else {
                    // 答案未加载，请求完整答案
                    uploadQuestionAndGetAnswer(question)
                }
            }
            "UNKNOWN" -> {
                questionTypeText.text = "未知类型"
                questionTypeText.setBackgroundResource(R.drawable.bg_question_type)
                answerSection.visibility = View.GONE
            }
            else -> {
                questionTypeText.text = "未知类型"
                questionTypeText.setBackgroundResource(R.drawable.bg_question_type)
                answerSection.visibility = View.GONE
            }
        }
        
        // 确定要显示的图片路径（优先级：清除对错痕迹后 > 原图）
        val imagePathToShow = if (showingHiddenOptionsImage && !question.hiddenOptionsImagePath.isNullOrBlank()) {
            question.hiddenOptionsImagePath
        } else {
            question.originalImagePath ?: question.imagePath
        }
        
        // 显示图片（使用 ImageAccessHelper 兼容 Android 10+）
        val file = File(imagePathToShow)
        // 如果是应用私有文件或公共存储目录的文件，直接使用 Coil 加载
        if (file.exists() && (imagePathToShow.startsWith(filesDir.absolutePath) || 
            imagePathToShow.startsWith(cacheDir.absolutePath) ||
            imagePathToShow.contains("/DCIM/Camera/"))) {
            imageView.load(file)
        } else if (com.gongkao.cuotifupan.util.ImageAccessHelper.isValidImage(this, imagePathToShow)) {
            // 使用 ImageAccessHelper 加载（兼容 MediaStore URI）
            val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(this, imagePathToShow)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
            } else {
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        } else {
            // 图片文件不存在
            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            Toast.makeText(this, "图片文件不存在，可能已被删除", Toast.LENGTH_SHORT).show()
        }
        
        // 点击图片可以全屏查看并缩放
        imageView.setOnClickListener {
            openImageFullscreen(imagePathToShow)
        }
        
        // 显示题干（优先显示后端返回的完整题干）
        val displayQuestionText = question.backendQuestionText ?: question.questionText
        questionTextView.text = displayQuestionText
        
        // 显示选项
        try {
            val optionsArray = org.json.JSONArray(question.options)
            val optionsText = StringBuilder()
            for (i in 0 until optionsArray.length()) {
                optionsText.append(optionsArray.getString(i))
                if (i < optionsArray.length() - 1) {
                    optionsText.append("\n")
                }
            }
            optionsTextView.text = optionsText.toString()
        } catch (e: Exception) {
            optionsTextView.text = ""
        }
        
        // 显示笔记
        notesEditText.setText(question.userNotes)
        
        // 显示复盘状态
        when (question.reviewState) {
            "mastered" -> reviewGroup.check(R.id.radioMastered)
            "not_mastered" -> reviewGroup.check(R.id.radioNotMastered)
            else -> reviewGroup.check(R.id.radioUnreviewed)
        }
    }
    
    /**
     * 显示已加载的答案（从数据库读取）
     */
    private fun displayLoadedAnswer(question: Question) {
        answerSection.visibility = View.VISIBLE
        loadingProgressBar.visibility = View.GONE
        
        // 显示正确答案
        question.correctAnswer?.let {
            correctAnswerTextView.text = "正确答案: $it"
            correctAnswerTextView.visibility = View.VISIBLE
        } ?: run {
            correctAnswerTextView.visibility = View.GONE
        }
        
        // 显示解析
        question.explanation?.let {
            explanationTextView.text = "解析: $it"
            explanationTextView.visibility = View.VISIBLE
        } ?: run {
            explanationTextView.visibility = View.GONE
        }
        
        // 答案版本列表为空（因为数据库中没有存储）
        answerVersionsRecyclerView.visibility = View.GONE
        tagsTextView.visibility = View.GONE
        knowledgePointsTextView.visibility = View.GONE
    }
    
    /**
     * 获取题目详情（答案、解析、标签等）
     * 使用详情接口，按需加载完整答案
     */
    private fun uploadQuestionAndGetAnswer(question: Question) {
        lifecycleScope.launch {
            try {
                // 显示加载状态
                loadingProgressBar.visibility = View.VISIBLE
                answerSection.visibility = View.GONE
                
                // 检查是否有后端题目ID
                val questionId = question.backendQuestionId
                if (questionId == null) {
                    Toast.makeText(
                        this@QuestionDetailActivity,
                        "题目ID不存在，无法获取详情",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadingProgressBar.visibility = View.GONE
                    return@launch
                }
                
                // 调用详情接口获取完整答案和解析
                // API请求已禁用，跳过
                val response = if (com.gongkao.cuotifupan.api.QuestionApiQueue.API_ENABLED) {
                    ApiClient.questionApiService.getQuestionDetail(questionId)
                } else {
                    Log.d("QuestionDetailActivity", "API请求已禁用，跳过获取题目详情")
                    loadingProgressBar.visibility = View.GONE
                    return@launch
                }
                
                if (response.isSuccessful && response.body() != null) {
                    val detail = response.body()!!
                    questionDetailResponse = detail
                    
                    Log.d("QuestionDetailActivity", "获取题目详情成功: ${detail.id}")
                    
                    // 更新数据库中的答案信息
                    val updatedQuestion = question.copy(
                        answerLoaded = true,
                        correctAnswer = detail.correctAnswer,
                        explanation = detail.explanation
                    )
                    
                    viewModel.update(updatedQuestion)
                    currentQuestion = updatedQuestion
                    
                    // 显示答案详情
                    displayAnswerDetails(detail)
                } else {
                    Toast.makeText(
                        this@QuestionDetailActivity,
                        "获取答案失败: ${response.message()}",
                        Toast.LENGTH_SHORT
                    ).show()
                    loadingProgressBar.visibility = View.GONE
                }
            } catch (e: Exception) {
                Log.e("QuestionDetailActivity", "获取答案失败", e)
                Toast.makeText(
                    this@QuestionDetailActivity,
                    "获取答案失败: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
                loadingProgressBar.visibility = View.GONE
            }
        }
    }
    
    /**
     * 显示答案详情
     */
    private fun displayAnswerDetails(response: QuestionDetailResponse) {
        loadingProgressBar.visibility = View.GONE
        answerSection.visibility = View.VISIBLE
        
        // 显示正确答案
        response.correctAnswer?.let {
            correctAnswerTextView.text = "正确答案: $it"
            correctAnswerTextView.visibility = View.VISIBLE
        } ?: run {
            correctAnswerTextView.visibility = View.GONE
        }
        
        // 显示解析
        response.explanation?.let {
            explanationTextView.text = "解析: $it"
            explanationTextView.visibility = View.VISIBLE
        } ?: run {
            explanationTextView.visibility = View.GONE
        }
        
        // 显示标签
        response.tags?.takeIf { it.isNotEmpty() }?.let {
            tagsTextView.text = "标签: ${it.joinToString(", ")}"
            tagsTextView.visibility = View.VISIBLE
        } ?: run {
            tagsTextView.visibility = View.GONE
        }
        
        // 显示知识点
        response.knowledgePoints?.takeIf { it.isNotEmpty() }?.let {
            knowledgePointsTextView.text = "知识点: ${it.joinToString(", ")}"
            knowledgePointsTextView.visibility = View.VISIBLE
        } ?: run {
            knowledgePointsTextView.visibility = View.GONE
        }
        
        // 显示答案版本列表
        response.answerVersions.takeIf { it.isNotEmpty() }?.let {
            answerVersionsAdapter.submitList(it)
            answerVersionsRecyclerView.visibility = View.VISIBLE
        } ?: run {
            answerVersionsRecyclerView.visibility = View.GONE
        }
    }
    
    private fun saveChanges() {
        val question = currentQuestion ?: return
        
        val notes = notesEditText.text.toString()
        val reviewState = when (reviewGroup.checkedRadioButtonId) {
            R.id.radioMastered -> "mastered"
            R.id.radioNotMastered -> "not_mastered"
            else -> "unreviewed"
        }
        
        val updatedQuestion = question.copy(
            userNotes = notes,
            reviewState = reviewState
        )
        
        viewModel.update(updatedQuestion)
        Toast.makeText(this, "保存成功", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun deleteQuestion() {
        val question = currentQuestion ?: return
        
        // 显示确认对话框
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("删除题目")
            .setMessage("确定要删除这道题目吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    // 在删除前，尝试找到原始图片路径并添加到排除列表
                    val database = com.gongkao.cuotifupan.data.AppDatabase.getDatabase(this@QuestionDetailActivity)
                    try {
                        // 如果图片文件在应用私有目录，尝试通过文件大小匹配找到原始图片
                        val imageFile = java.io.File(question.imagePath)
                        if (imageFile.exists()) {
                            val fileSize = imageFile.length()
                            if (fileSize > 0) {
                                // 将永久存储路径添加到排除列表（防止通过路径匹配）
                                val excludedImage = com.gongkao.cuotifupan.data.ExcludedImage(
                                    imagePath = question.imagePath,
                                    reason = "已删除的题目（永久存储路径，文件大小: $fileSize）"
                                )
                                database.excludedImageDao().insert(excludedImage)
                                android.util.Log.d("QuestionDetailActivity", "已排除永久存储路径: ${question.imagePath} (文件大小: $fileSize)")
                                
                                // 尝试通过文件大小在相册中找到原始图片并排除
                                val contentResolver = contentResolver
                                val projection = arrayOf(
                                    android.provider.MediaStore.Images.Media._ID,
                                    android.provider.MediaStore.Images.Media.DATA,
                                    android.provider.MediaStore.Images.Media.SIZE
                                )
                                val cursor = contentResolver.query(
                                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                    projection,
                                    "${android.provider.MediaStore.Images.Media.SIZE} = ?",
                                    arrayOf(fileSize.toString()),
                                    "${android.provider.MediaStore.Images.Media.DATE_ADDED} DESC"
                                )
                                cursor?.use {
                                    // 遍历所有相同大小的图片，都添加到排除列表
                                    var excludedCount = 0
                                    while (it.moveToNext()) {
                                        val pathIndex = it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media.DATA)
                                        val path = it.getString(pathIndex)
                                        // 检查这个路径是否已经在排除列表中
                                        val existingExcluded = database.excludedImageDao().getByPath(path)
                                        if (existingExcluded == null) {
                                            // 将原始路径也添加到排除列表
                                            val originalExcludedImage = com.gongkao.cuotifupan.data.ExcludedImage(
                                                imagePath = path,
                                                reason = "已删除的题目（原始路径，文件大小: $fileSize）"
                                            )
                                            database.excludedImageDao().insert(originalExcludedImage)
                                            excludedCount++
                                            android.util.Log.d("QuestionDetailActivity", "已排除原始图片路径: $path (文件大小: $fileSize)")
                                        }
                                    }
                                    android.util.Log.d("QuestionDetailActivity", "共排除了 $excludedCount 个相同大小的原始图片路径")
                                }
                            } else {
                                // 文件大小为0，只排除永久存储路径
                                val excludedImage = com.gongkao.cuotifupan.data.ExcludedImage(
                                    imagePath = question.imagePath,
                                    reason = "已删除的题目（永久存储路径，文件大小为0）"
                                )
                                database.excludedImageDao().insert(excludedImage)
                                android.util.Log.d("QuestionDetailActivity", "已排除永久存储路径（文件大小为0）: ${question.imagePath}")
                            }
                        } else {
                            // 文件不存在，只排除永久存储路径
                            val excludedImage = com.gongkao.cuotifupan.data.ExcludedImage(
                                imagePath = question.imagePath,
                                reason = "已删除的题目（永久存储路径，文件不存在）"
                            )
                            database.excludedImageDao().insert(excludedImage)
                            android.util.Log.d("QuestionDetailActivity", "已排除永久存储路径（文件不存在）: ${question.imagePath}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("QuestionDetailActivity", "添加排除图片时出错", e)
                        // 即使出错，也至少排除永久存储路径
                        try {
                            val excludedImage = com.gongkao.cuotifupan.data.ExcludedImage(
                                imagePath = question.imagePath,
                                reason = "已删除的题目（永久存储路径，添加时出错: ${e.message}）"
                            )
                            database.excludedImageDao().insert(excludedImage)
                        } catch (e2: Exception) {
                            android.util.Log.e("QuestionDetailActivity", "添加排除图片失败", e2)
                        }
                    }
                    
                    // 删除图片文件（如果图片在应用私有目录）
                    val imageFile = java.io.File(question.imagePath)
                    if (imageFile.exists() && (question.imagePath.startsWith(filesDir.absolutePath) || 
                        question.imagePath.startsWith(cacheDir.absolutePath))) {
                        try {
                            imageFile.delete()
                            android.util.Log.d("QuestionDetailActivity", "已删除图片文件: ${question.imagePath}")
                        } catch (e: Exception) {
                            android.util.Log.e("QuestionDetailActivity", "删除图片文件失败: ${question.imagePath}", e)
                        }
                    }
                    
                    // 删除数据库记录
                    viewModel.delete(question)
                    Toast.makeText(this@QuestionDetailActivity, "题目已删除", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 打开全屏查看图片（支持缩放）
     */
    private fun openImageFullscreen(imagePath: String) {
        // 设置图片路径列表到缓存
        ImagePathCache.setImagePaths(listOf(imagePath))
        
        // 启动全屏查看 Activity
        val intent = Intent(this, ImageFullscreenActivity::class.java).apply {
            putExtra(ImageFullscreenActivity.EXTRA_CURRENT_POSITION, 0)
        }
        startActivity(intent)
    }
    
    /**
     * 显示题目类型选择对话框
     */
    private fun showQuestionTypeDialog() {
        val questionTypes = arrayOf(
            "选择题 (Multiple Choice)" to "MULTIPLE_CHOICE",
            "判断题 (True/False)" to "TRUE_FALSE",
            "简答题 (Short Answer)" to "SHORT_ANSWER",
            "图推题 (Graphic Reasoning)" to "GRAPHIC",
            "文字题 (Text Question)" to "TEXT",
            "未知类型 (Unknown)" to "UNKNOWN"
        )
        
        val currentType = currentQuestion?.questionType ?: "TEXT"
        val currentIndex = questionTypes.indexOfFirst { it.second == currentType }.takeIf { it >= 0 } ?: 0
        
        val items = questionTypes.map { it.first }.toTypedArray()
        
        AlertDialog.Builder(this)
            .setTitle("选择题目类型")
            .setSingleChoiceItems(items, currentIndex) { dialog, which ->
                val selectedType = questionTypes[which].second
                updateQuestionType(selectedType)
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 更新题目类型
     */
    private fun updateQuestionType(newType: String) {
        val question = currentQuestion ?: return
        
        val updatedQuestion = question.copy(questionType = newType)
        
        lifecycleScope.launch {
            viewModel.update(updatedQuestion)
            currentQuestion = updatedQuestion
            displayQuestion(updatedQuestion)
            Toast.makeText(this@QuestionDetailActivity, "题目类型已更新", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 编辑图片（裁剪和旋转）
     */
    private fun editImage() {
        val question = currentQuestion ?: return
        val imagePath = question.imagePath
        
        if (!File(imagePath).exists()) {
            Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 启动图片编辑Activity
        val intent = Intent(this, ImageEditActivity::class.java).apply {
            putExtra(ImageEditActivity.EXTRA_IMAGE_PATH, imagePath)
        }
        startActivityForResult(intent, REQUEST_CODE_EDIT_IMAGE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_EDIT_IMAGE && resultCode == RESULT_OK) {
            val editedImagePath = data?.getStringExtra(ImageEditActivity.EXTRA_EDITED_IMAGE_PATH)
            val replacedOriginal = data?.getBooleanExtra(ImageEditActivity.EXTRA_REPLACED_ORIGINAL, false) ?: false
            
            if (editedImagePath != null && currentQuestion != null) {
                // 更新数据库中的图片路径
                val updatedQuestion = currentQuestion!!.copy(imagePath = editedImagePath)
                
                lifecycleScope.launch {
                    viewModel.update(updatedQuestion)
                    currentQuestion = updatedQuestion
                    
                    // 重新显示图片
                    val file = File(editedImagePath)
                    if (file.exists()) {
                        imageView.load(file)
                        Log.d("QuestionDetailActivity", "图片已更新: $editedImagePath, 替换原图: $replacedOriginal")
                    } else {
                        Log.e("QuestionDetailActivity", "图片文件不存在: $editedImagePath")
                        Toast.makeText(this@QuestionDetailActivity, "图片文件不存在", Toast.LENGTH_SHORT).show()
                    }
                    
                    val message = if (replacedOriginal) {
                        "图片已替换原图"
                    } else {
                        "图片已保存为新文件"
                    }
                    Toast.makeText(this@QuestionDetailActivity, message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 清除粉笔错题截图的对错痕迹（支持切换）
     */
    private fun removeAnswerMarks() {
        val question = currentQuestion ?: return
        
        // 如果已有清除后的图片，实现切换功能
        if (!question.hiddenOptionsImagePath.isNullOrBlank()) {
            // 当前显示清除后的图片 -> 还原到原图
            if (showingHiddenOptionsImage) {
                restoreOriginalFromHiddenOptions(question)
            } else {
                // 当前显示原图 -> 直接切换到已清除的图片
                showHiddenOptionsImage(question)
            }
            return
        }
        
        // 没有清除后的图片，执行清除处理
        val originalImagePath = question.originalImagePath ?: question.imagePath
        
        if (!File(originalImagePath).exists()) {
            Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 执行清除操作的函数
        fun performClear() {
            lifecycleScope.launch {
                // 显示进度提示
                val progressDialog = android.app.ProgressDialog(this@QuestionDetailActivity).apply {
                    setMessage("正在清除对错痕迹...")
                    setCancelable(false)
                    show()
                }
                
                try {
                    // 初始化ImageEditor
                    ImageEditor.init(this@QuestionDetailActivity)
                    
                    // 在后台线程执行清除操作
                    val hiddenImagePath = withContext(Dispatchers.IO) {
                        ImageEditor.hideOptions(originalImagePath)
                    }
                    
                    progressDialog.dismiss()
                    
                    if (hiddenImagePath != null) {
                        // 保存清除后的图片路径
                        val updatedQuestion = question.copy(
                            hiddenOptionsImagePath = hiddenImagePath,
                            originalImagePath = originalImagePath  // 确保originalImagePath存在
                        )
                        viewModel.update(updatedQuestion)
                        currentQuestion = updatedQuestion
                        
                        // 切换到清除后的图片
                        showingHiddenOptionsImage = true
                        displayQuestion(updatedQuestion)
                        
                        Toast.makeText(this@QuestionDetailActivity, "已清除对错痕迹", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@QuestionDetailActivity, "清除失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    progressDialog.dismiss()
                    Toast.makeText(this@QuestionDetailActivity, "清除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // 检查是否需要显示提示（前5次显示）
        if (com.gongkao.cuotifupan.util.PreferencesManager.shouldShowClearMarksHint(this)) {
            // 显示确认对话框
            AlertDialog.Builder(this)
                .setTitle("清除对错痕迹")
                .setMessage("此功能用于清除粉笔错题截图中的对错痕迹，包括：\n\n• 选项圆圈的对错标记（绿色/红色）\n• 底部的答案提示文字（如\"正确答案\"、\"你的答案\"等）\n• 其他红色和绿色标记\n\n同时会智能裁剪掉答案说明行及以下内容。\n\n此操作将生成一张新的图片，原图不会被删除。如果不满意，可以手动调整。")
                .setPositiveButton("确定") { _, _ ->
                    // 增加提示显示次数
                    com.gongkao.cuotifupan.util.PreferencesManager.incrementClearMarksHintCount(this)
                    performClear()
                }
                .setNegativeButton("取消", null)
                .show()
        } else {
            // 已经显示过5次，直接执行清除
            performClear()
        }
    }
    
    /**
     * 显示已清除的图片
     */
    private fun showHiddenOptionsImage(question: Question) {
        showingHiddenOptionsImage = true
        displayQuestion(question)
    }
    
    /**
     * 还原到原图（从清除后的图片还原）
     */
    private fun restoreOriginalFromHiddenOptions(question: Question) {
        showingHiddenOptionsImage = false
        displayQuestion(question)
    }
    
    companion object {
        private const val REQUEST_CODE_EDIT_IMAGE = 1001
    }
}

