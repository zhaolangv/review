package com.gongkao.cuotifupan.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.CachePolicy
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.viewmodel.QuestionViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import android.app.Activity
import java.io.File
import com.gongkao.cuotifupan.ui.ImagePathCache

/**
 * 题目详情页（卡片式滑动视图）
 */
class QuestionDetailCardActivity : AppCompatActivity() {
    
    private lateinit var viewModel: QuestionViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var cardAdapter: QuestionCardAdapter
    
    private var allQuestions: List<Question> = emptyList()
    private var currentPosition: Int = 0
    private var isViewPagerInitialized = false
    private var currentEditingQuestion: Question? = null
    
    companion object {
        private const val REQUEST_CODE_EDIT_IMAGE = 1001
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_question_detail_card)
        
        viewModel = ViewModelProvider(this)[QuestionViewModel::class.java]
        
        viewPager = findViewById(R.id.questionViewPager)
        
        // 获取初始题目ID
        val questionId = intent.getStringExtra("question_id")
        
        // 观察题目列表
        viewModel.allQuestions.observe(this) { questions ->
            // 过滤掉图片文件不存在的题目
            val validQuestions = questions.filter { question ->
                val file = java.io.File(question.imagePath)
                if (!file.exists()) {
                    // 图片文件不存在，异步删除该题目
                    lifecycleScope.launch {
                        viewModel.delete(question)
                    }
                    false
                } else {
                    true
                }
            }
            
            allQuestions = validQuestions
            if (validQuestions.isNotEmpty()) {
                if (!isViewPagerInitialized) {
                    setupViewPager(validQuestions, questionId)
                    isViewPagerInitialized = true
                } else {
                    // 只更新列表，不重新设置 ViewPager
                    val savedPosition = viewPager.currentItem
                    cardAdapter.submitList(validQuestions)
                    viewPager.post {
                        if (savedPosition < validQuestions.size) {
                            viewPager.setCurrentItem(savedPosition, false)
                        }
                    }
                }
            } else {
                Toast.makeText(this, "没有有效的题目", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
    
    private fun setupViewPager(questions: List<Question>, initialQuestionId: String?) {
        // 找到初始位置
        val initialPosition = if (initialQuestionId != null) {
            questions.indexOfFirst { it.id == initialQuestionId }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        
        currentPosition = initialPosition
        
        cardAdapter = QuestionCardAdapter(
            onEditTags = { question ->
                showTagEditDialog(question)
            },
            onQuestionUpdate = { updatedQuestion ->
                // 更新题目（保存笔记等）
                lifecycleScope.launch {
                    // 先更新本地列表
                    val updatedList = allQuestions.map { 
                        if (it.id == updatedQuestion.id) updatedQuestion else it 
                    }
                    allQuestions = updatedList
                    
                    // 保存当前位置，避免刷新后跳转到第一个
                    val savedPosition = viewPager.currentItem
                    cardAdapter.submitList(updatedList)
                    
                    // 恢复之前的位置
                    viewPager.post {
                        if (savedPosition < updatedList.size) {
                            viewPager.setCurrentItem(savedPosition, false)
                        }
                    }
                    
                    // 最后更新数据库（避免触发观察者导致重复更新）
                    viewModel.update(updatedQuestion)
                }
            },
            onEditImage = { question ->
                editImage(question)
            },
            onDelete = { question ->
                deleteQuestion(question)
            },
            onImageClick = { question ->
                openImageFullscreenWithNotes(question, allQuestions)
            }
        )
        
        viewPager.adapter = cardAdapter
        cardAdapter.submitList(questions)
        
        // 延迟设置当前位置，确保列表已经提交
        viewPager.post {
            viewPager.setCurrentItem(initialPosition, false)
        }
        
        // 监听滑动
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                currentPosition = position
                // 更新标题显示当前位置
                supportActionBar?.title = "题目 ${position + 1}/${questions.size}"
            }
        })
        
        // 设置标题
        supportActionBar?.title = "题目 ${initialPosition + 1}/${questions.size}"
    }
    
    private fun getCurrentQuestion(): Question? {
        return if (currentPosition in allQuestions.indices) {
            allQuestions[currentPosition]
        } else {
            null
        }
    }
    
    private fun showTagEditDialog(question: Question) {
        // 解析当前标签
        val currentTags = try {
            if (question.tags.isNotBlank()) {
                JSONArray(question.tags).let { array ->
                    (0 until array.length()).map { array.getString(it) }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
        
        // 移除特殊标记（如果存在），直接使用保存的标签
        val tagsWithoutMarker = currentTags.filter { it != "__NO_TYPE_TAG__" }
        
        // 显示标签编辑对话框（不自动添加类型标签）
        TagEditDialog.show(this, tagsWithoutMarker) { newTags ->
            // 保存标签（移除特殊标记，如果存在）
            val finalTags = newTags.filter { it != "__NO_TYPE_TAG__" }
            val tagsJson = JSONArray(finalTags).toString()
            val updatedQuestion = question.copy(tags = tagsJson)
            
            Log.d("QuestionDetailCardActivity", "保存标签: 原标签=$currentTags, 新标签=$newTags, 最终标签=$finalTags, JSON=$tagsJson")
            Log.d("QuestionDetailCardActivity", "更新题目: ID=${question.id}, 原tags=${question.tags}, 新tags=$tagsJson")
            
            lifecycleScope.launch {
                // 在 IO 线程更新数据库
                withContext(Dispatchers.IO) {
                    // 先更新数据库
                    viewModel.update(updatedQuestion)
                    
                    // 同步更新相关笔记和记忆卡片的标签
                    val database = AppDatabase.getDatabase(this@QuestionDetailCardActivity)
                    val standaloneNotes = database.standaloneNoteDao().getAllNotesSync()
                    val standaloneFlashcards = database.standaloneFlashcardDao().getAllFlashcardsSync()
                    
                    // 更新相关笔记的标签
                    standaloneNotes.filter { it.questionId == question.id }.forEach { note ->
                        val updatedNote = note.copy(tags = tagsJson, updatedAt = System.currentTimeMillis())
                        database.standaloneNoteDao().update(updatedNote)
                    }
                    
                    // 更新相关记忆卡片的标签
                    standaloneFlashcards.filter { it.questionId == question.id }.forEach { flashcard ->
                        val updatedFlashcard = flashcard.copy(tags = tagsJson, updatedAt = System.currentTimeMillis())
                        database.standaloneFlashcardDao().update(updatedFlashcard)
                    }
                }
                
                // 在主线程更新 UI
                withContext(Dispatchers.Main) {
                    // 更新本地列表
                    val updatedList = allQuestions.map { if (it.id == question.id) updatedQuestion else it }
                    allQuestions = updatedList
                    
                    // 保存当前位置，避免刷新后跳转
                    val savedPosition = viewPager.currentItem
                    
                    // 强制刷新适配器
                    cardAdapter.submitList(updatedList) {
                        // 刷新完成后，确保当前项已更新
                        viewPager.post {
                            if (savedPosition < updatedList.size) {
                                viewPager.setCurrentItem(savedPosition, false)
                                Log.d("QuestionDetailCardActivity", "标签更新完成，已刷新到位置 $savedPosition")
                            }
                        }
                    }
                    
                    Toast.makeText(this@QuestionDetailCardActivity, "标签已更新", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun editImage(question: Question) {
        val imagePath = question.imagePath
        
        if (!java.io.File(imagePath).exists()) {
            Toast.makeText(this, "图片文件不存在", Toast.LENGTH_SHORT).show()
            return
        }
        
        currentEditingQuestion = question
        
        // 启动图片编辑Activity（包含裁剪、旋转和标注功能）
        val intent = Intent(this, ImageEditActivity::class.java).apply {
            putExtra(ImageEditActivity.EXTRA_IMAGE_PATH, imagePath)
            putExtra("question_id", question.id) // 传递question_id用于保存标注
        }
        startActivityForResult(intent, REQUEST_CODE_EDIT_IMAGE)
    }
    
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == REQUEST_CODE_EDIT_IMAGE && resultCode == Activity.RESULT_OK) {
            // 从全屏图片查看返回时，刷新当前题目（标注可能已更新）
            if (currentEditingQuestion != null) {
                // 刷新当前题目数据
                lifecycleScope.launch {
                    val database = com.gongkao.cuotifupan.data.AppDatabase.getDatabase(this@QuestionDetailCardActivity)
                    val updatedQuestion = database.questionDao().getQuestionById(currentEditingQuestion!!.id)
                    if (updatedQuestion != null) {
                        // 更新本地列表
                        val updatedList = allQuestions.map { if (it.id == updatedQuestion.id) updatedQuestion else it }
                        allQuestions = updatedList
                        
                        // 保存当前位置，避免刷新后跳转
                        val savedPosition = viewPager.currentItem
                        
                        // 强制刷新适配器
                        cardAdapter.submitList(updatedList) {
                            // 刷新完成后，确保当前项已更新
                            viewPager.post {
                                if (savedPosition < updatedList.size) {
                                    viewPager.setCurrentItem(savedPosition, false)
                                }
                            }
                        }
                    }
                }
            }
            
            // 兼容旧的ImageEditActivity返回格式（如果以后需要）
            val editedImagePath = data?.getStringExtra(ImageEditActivity.EXTRA_EDITED_IMAGE_PATH)
            val replacedOriginal = data?.getBooleanExtra(ImageEditActivity.EXTRA_REPLACED_ORIGINAL, false) ?: false
            
            if (editedImagePath != null && currentEditingQuestion != null) {
                val question = currentEditingQuestion!!
                
                // 如果替换了原图，清除 Coil 缓存
                if (replacedOriginal) {
                    clearImageCache(editedImagePath)
                }
                
                lifecycleScope.launch {
                    // 更新数据库中的图片路径（如果路径改变了）
                    val updatedQuestion = if (editedImagePath != question.imagePath) {
                        question.copy(imagePath = editedImagePath)
                    } else {
                        // 路径没变，但内容已更新（替换了原图）
                        // 创建一个新的 Question 对象，QuestionDiffCallback 会检查文件修改时间
                        question.copy(imagePath = editedImagePath)
                    }
                    
                    // 如果路径改变了，更新数据库
                    if (editedImagePath != question.imagePath) {
                        viewModel.update(updatedQuestion)
                    }
                    
                    // 更新本地列表
                    val updatedList = allQuestions.map { 
                        if (it.id == question.id) updatedQuestion else it 
                    }
                    allQuestions = updatedList
                    
                    // 保存当前位置，避免刷新后跳转到第一个
                    val savedPosition = viewPager.currentItem
                    
                    // 提交新列表，触发 DiffUtil 更新
                    cardAdapter.submitList(updatedList)
                    
                    // 如果替换了原图，强制刷新当前项（即使路径相同，文件内容已更新）
                    if (replacedOriginal) {
                        // 延迟一下，确保列表已经提交
                        viewPager.post {
                            // 强制通知 adapter 刷新当前项，触发重新加载图片
                            cardAdapter.notifyItemChanged(savedPosition)
                        }
                    }
                    
                    // 恢复之前的位置
                    viewPager.post {
                        if (savedPosition < updatedList.size) {
                            viewPager.setCurrentItem(savedPosition, false)
                        }
                    }
                    
                    Toast.makeText(this@QuestionDetailCardActivity, "图片已更新", Toast.LENGTH_SHORT).show()
                }
            }
            
            currentEditingQuestion = null
        }
    }
    
    /**
     * 清除指定图片的 Coil 缓存
     * 注意：由于 Coil API 的限制，这里只是标记需要刷新
     * 实际的缓存清除通过 QuestionDiffCallback 检查文件修改时间来实现
     */
    private fun clearImageCache(imagePath: String) {
        try {
            // 由于 Coil 的缓存键生成机制复杂，我们通过以下方式强制刷新：
            // 1. QuestionDiffCallback 会检查文件修改时间
            // 2. 如果文件修改时间改变，会触发 DiffUtil 刷新
            // 3. 重新加载图片时，由于文件已更新，Coil 会使用新的缓存键
            Log.d("QuestionDetailCardActivity", "标记图片需要刷新: $imagePath")
        } catch (e: Exception) {
            Log.w("QuestionDetailCardActivity", "处理图片刷新失败: $imagePath", e)
        }
    }
    
    private fun openImageFullscreenWithNotes(question: Question, allQuestions: List<Question>) {
        // 设置图片路径列表到缓存
        ImagePathCache.setImagePaths(allQuestions.map { it.imagePath })
        
        // 找到当前题目在列表中的位置
        val position = allQuestions.indexOfFirst { it.id == question.id }.takeIf { it >= 0 } ?: 0
        
        // 启动全屏查看 Activity（带笔记功能）
        val intent = Intent(this, ImageFullscreenWithNotesActivity::class.java).apply {
            putExtra("current_position", position)
            putExtra("question_id", question.id)
        }
        startActivity(intent)
    }
    
    private fun deleteQuestion(question: Question) {
        lifecycleScope.launch {
            // 删除题目时，只排除应用私有目录的路径（永久存储路径）
            // 不排除原始图片路径，这样用户可以重新导入这些图片
            val database = com.gongkao.cuotifupan.data.AppDatabase.getDatabase(this@QuestionDetailCardActivity)
            try {
                // 只排除应用私有目录的路径（永久存储路径）
                // 这样原始图片可以重新导入
                if (question.imagePath.startsWith(filesDir.absolutePath) || 
                    question.imagePath.startsWith(cacheDir.absolutePath)) {
                    // 应用私有目录的路径，添加到排除列表（防止重复使用）
                    val excludedImage = com.gongkao.cuotifupan.data.ExcludedImage(
                        imagePath = question.imagePath,
                        reason = "已删除的题目（永久存储路径）"
                    )
                    database.excludedImageDao().insert(excludedImage)
                    android.util.Log.d("QuestionDetailCardActivity", "已排除永久存储路径: ${question.imagePath}")
                }
                // 注意：不排除原始图片路径（originalImagePath），这样用户可以重新导入
            } catch (e: Exception) {
                android.util.Log.e("QuestionDetailCardActivity", "添加排除图片时出错", e)
            }
            
            // 删除图片文件（如果图片在应用私有目录）
            val imageFile = java.io.File(question.imagePath)
            if (imageFile.exists() && (question.imagePath.startsWith(filesDir.absolutePath) || 
                question.imagePath.startsWith(cacheDir.absolutePath))) {
                try {
                    imageFile.delete()
                    android.util.Log.d("QuestionDetailCardActivity", "已删除图片文件: ${question.imagePath}")
                } catch (e: Exception) {
                    android.util.Log.e("QuestionDetailCardActivity", "删除图片文件失败: ${question.imagePath}", e)
                }
            }
            
            // 删除数据库记录
            viewModel.delete(question)
            Toast.makeText(this@QuestionDetailCardActivity, "题目已删除", Toast.LENGTH_SHORT).show()
            
            // 如果删除后没有题目了，关闭页面
            val remainingQuestions = allQuestions.filter { it.id != question.id }
            if (remainingQuestions.isEmpty()) {
                finish()
            } else {
                // 更新列表，切换到下一个或上一个题目
                val currentPosition = viewPager.currentItem
                val newList = remainingQuestions
                allQuestions = newList
                cardAdapter.submitList(newList)
                
                // 调整位置
                viewPager.post {
                    if (currentPosition >= newList.size) {
                        // 如果当前位置超出范围，切换到最后一个
                        viewPager.setCurrentItem(newList.size - 1, false)
                    } else {
                        // 否则保持在当前位置（实际上是下一个题目）
                        viewPager.setCurrentItem(currentPosition, false)
                    }
                }
            }
        }
    }
    
    private fun setupKeyboardListener() {
        val rootView = window.decorView.rootView
        val rootViewTreeObserver = rootView.viewTreeObserver
        
        rootViewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            private var wasKeyboardVisible = false
            
            override fun onGlobalLayout() {
                val rect = android.graphics.Rect()
                rootView.getWindowVisibleDisplayFrame(rect)
                val screenHeight = rootView.height
                val keypadHeight = screenHeight - rect.bottom
                
                // 如果软键盘高度超过屏幕高度的15%，认为软键盘已显示
                val isKeyboardVisible = keypadHeight > screenHeight * 0.15
                
                if (isKeyboardVisible != wasKeyboardVisible) {
                    wasKeyboardVisible = isKeyboardVisible
                    
                    if (isKeyboardVisible) {
                        // 软键盘显示时，确保输入框可见
                        // 通过 ViewPager2 找到当前显示的 ViewHolder
                        ensureInputVisible()
                    }
                }
            }
        })
    }
    
    private fun ensureInputVisible() {
        // 延迟一下，等待布局调整完成
        viewPager.postDelayed({
            try {
                if (::cardAdapter.isInitialized) {
                    // 通过 adapter 通知当前 ViewHolder 滚动
                    val currentItem = viewPager.currentItem
                    cardAdapter.notifyInputFocus(currentItem)
                }
            } catch (e: Exception) {
                Log.e("QuestionDetailCardActivity", "Error ensuring input visible", e)
            }
        }, 100)
        
        // 再延迟一次，确保软键盘完全弹出后再次调整
        viewPager.postDelayed({
            try {
                if (::cardAdapter.isInitialized) {
                    val currentItem = viewPager.currentItem
                    cardAdapter.notifyInputFocus(currentItem)
                }
            } catch (e: Exception) {
                Log.e("QuestionDetailCardActivity", "Error ensuring input visible", e)
            }
        }, 400)
    }
}
