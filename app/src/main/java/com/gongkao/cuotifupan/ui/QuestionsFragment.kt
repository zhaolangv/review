package com.gongkao.cuotifupan.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.viewmodel.QuestionViewModel
import com.gongkao.cuotifupan.service.ImageMonitorService
import com.gongkao.cuotifupan.util.PreferencesManager
import com.gongkao.cuotifupan.util.ImageScanner
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import android.util.Log

/**
 * 题目列表 Fragment
 */
class QuestionsFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: QuestionAdapter
    private lateinit var viewModel: QuestionViewModel
    
    private var isBatchMode = false
    private val selectedQuestions = mutableSetOf<String>()
    private var previousQuestionCount = 0
    private var scanningDialog: androidx.appcompat.app.AlertDialog? = null
    private var scanningProgressText: TextView? = null
    
    // 权限请求 launcher
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // 权限授予后，显示重新扫描对话框
            showRescanDialog()
        } else {
            // 权限被拒绝，显示说明对话框
            showPermissionExplanationDialog()
        }
    }
    
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }
    
    override fun onResume() {
        super.onResume()
        // 每次Fragment显示时，确保标题是应用名称（除非在批量模式）
        if (!isBatchMode) {
            (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.app_name)
        }
        
        // 清理图片文件已不存在的题目记录
        cleanupInvalidQuestions()
    }
    
    override fun onPause() {
        super.onPause()
        // 当Fragment失去焦点时（切换到其他页面），自动退出批量模式
        if (isBatchMode) {
            exitBatchMode()
        }
    }
    
    /**
     * 清理图片文件已不存在的题目记录
     */
    private fun cleanupInvalidQuestions() {
        val context = context ?: return
        android.util.Log.d("QuestionsFragment", "开始清理无效题目...")
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val database = AppDatabase.getDatabase(context)
                    val allQuestions = database.questionDao().getAllQuestionsSync()
                    android.util.Log.d("QuestionsFragment", "找到 ${allQuestions.size} 条题目记录，开始检查图片文件...")
                    var deletedCount = 0
                    var checkedCount = 0
                    
                    for (question in allQuestions) {
                        checkedCount++
                        // 检查图片文件是否存在
                        val imageExists = try {
                            if (question.imagePath.startsWith(context.filesDir.absolutePath) || 
                                question.imagePath.startsWith(context.cacheDir.absolutePath)) {
                                // 应用私有文件，直接检查文件是否存在
                                val file = java.io.File(question.imagePath)
                                val exists = file.exists()
                                if (!exists) {
                                    android.util.Log.d("QuestionsFragment", "题目 ${question.id} 的图片文件不存在: ${question.imagePath}")
                                }
                                exists
                            } else {
                                // 外部文件，先尝试直接检查文件，如果失败再使用 ImageAccessHelper
                                val file = java.io.File(question.imagePath)
                                if (file.exists()) {
                                    true
                                } else {
                                    // 文件不存在，使用 ImageAccessHelper 检查（可能返回 false）
                                    val isValid = com.gongkao.cuotifupan.util.ImageAccessHelper.isValidImage(context, question.imagePath)
                                    if (!isValid) {
                                        android.util.Log.d("QuestionsFragment", "题目 ${question.id} 的图片文件无效: ${question.imagePath}")
                                    }
                                    isValid
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("QuestionsFragment", "检查题目 ${question.id} 的图片文件时出错: ${question.imagePath}", e)
                            false
                        }
                        
                        // 如果图片文件不存在，删除该题目记录
                        if (!imageExists) {
                            try {
                                database.questionDao().delete(question)
                                deletedCount++
                                android.util.Log.d("QuestionsFragment", "已删除题目 ${question.id}，图片路径: ${question.imagePath}")
                            } catch (e: Exception) {
                                android.util.Log.e("QuestionsFragment", "删除题目 ${question.id} 时出错", e)
                            }
                        }
                    }
                    
                    android.util.Log.d("QuestionsFragment", "清理完成：检查了 $checkedCount 条记录，删除了 $deletedCount 条无效记录")
                    if (deletedCount > 0) {
                        android.util.Log.i("QuestionsFragment", "✅ 清理了 $deletedCount 条图片文件已不存在的题目记录")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("QuestionsFragment", "清理无效题目时出错", e)
                }
            }
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_questions, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 确保标题是应用名称
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.app_name)
        
        viewModel = ViewModelProvider(requireActivity())[QuestionViewModel::class.java]
        
        recyclerView = view.findViewById(R.id.recyclerView)
        emptyView = view.findViewById(R.id.emptyView)
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        adapter = QuestionAdapter(
            onItemClick = { question ->
                if (!isBatchMode) {
                    val intent = Intent(requireContext(), QuestionDetailCardActivity::class.java)
                    intent.putExtra("question_id", question.id)
                    startActivity(intent)
                }
            },
            onEditTags = { question ->
                showTagEditDialog(question)
            },
            onDelete = { question ->
                deleteQuestion(question)
            },
            isBatchMode = isBatchMode,
            selectedQuestions = selectedQuestions,
            onSelectionChanged = { count ->
                (activity as? AppCompatActivity)?.supportActionBar?.title = if (count > 0) {
                    "已选择 $count 道题目"
                } else {
                    getString(R.string.app_name)
                }
            },
            isTimeSortMode = true
        )
        recyclerView.adapter = adapter
        
        // 观察题目列表
        viewModel.allQuestionsOrderByTime.observe(viewLifecycleOwner) { questions ->
            updateQuestionsList(questions)
        }
        
        // Fragment 首次显示时也执行清理
        cleanupInvalidQuestions()
    }
    
    private fun updateQuestionsList(questions: List<Question>) {
        val listItems = if (viewModel.sortMode == com.gongkao.cuotifupan.viewmodel.QuestionViewModel.SortMode.TIME && questions.isNotEmpty()) {
            groupQuestionsByDate(questions)
        } else {
            questions.map { QuestionAdapter.ListItem.QuestionItem(it) }
        }
        
        // 检测是否有新题目
        val hasNewQuestions = questions.size > previousQuestionCount
        previousQuestionCount = questions.size
        
        adapter.submitList(listItems) {
            // 如果有新题目，且列表超出页面，滚动到顶部
            if (hasNewQuestions && questions.isNotEmpty()) {
                recyclerView.post {
                    val layoutManager = recyclerView.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager
                    if (layoutManager != null) {
                        val firstVisiblePosition = layoutManager.findFirstVisibleItemPosition()
                        if (firstVisiblePosition > 0 || layoutManager.findLastVisibleItemPosition() < listItems.size - 1) {
                            recyclerView.smoothScrollToPosition(0)
                        }
                    }
                }
            }
        }
        
        emptyView.visibility = if (questions.isEmpty()) View.VISIBLE else View.GONE
    }
    
    private fun groupQuestionsByDate(questions: List<Question>): List<QuestionAdapter.ListItem> {
        val listItems = mutableListOf<QuestionAdapter.ListItem>()
        val calendar = java.util.Calendar.getInstance()
        val today = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val displayDateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
        
        var currentDateKey: String? = null
        
        questions.forEach { question ->
            calendar.timeInMillis = question.createdAt
            val dateKey = dateFormat.format(calendar.time)
            
            if (dateKey != currentDateKey) {
                currentDateKey = dateKey
                
                val dateText = when {
                    calendar.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                    calendar.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) -> {
                        "今天"
                    }
                    calendar.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) &&
                    calendar.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) - 1 -> {
                        "昨天"
                    }
                    calendar.get(java.util.Calendar.YEAR) == today.get(java.util.Calendar.YEAR) -> {
                        val monthDayFormat = java.text.SimpleDateFormat("MM月dd日", java.util.Locale.getDefault())
                        monthDayFormat.format(calendar.time)
                    }
                    else -> {
                        displayDateFormat.format(calendar.time)
                    }
                }
                
                listItems.add(QuestionAdapter.ListItem.DateHeader(dateText, dateKey))
            }
            
            listItems.add(QuestionAdapter.ListItem.QuestionItem(question))
        }
        
        return listItems
    }
    
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.questions_fragment_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_camera_capture -> {
                val intent = Intent(requireContext(), CameraCaptureActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_manual_import -> {
                val intent = Intent(requireContext(), ManualImportActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_rescan -> {
                checkPermissionAndRescan()
                true
            }
            R.id.action_clear_all -> {
                showClearAllDialog()
                true
            }
            R.id.action_sort_time -> {
                viewModel.setSortMode(com.gongkao.cuotifupan.viewmodel.QuestionViewModel.SortMode.TIME)
                adapter.setTimeSortMode(true)
                viewModel.allQuestionsOrderByTime.observe(viewLifecycleOwner) { questions ->
                    updateQuestionsList(questions)
                }
                Toast.makeText(requireContext(), "已切换为按时间排序", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sort_tag -> {
                showTagSortDialog()
                true
            }
            R.id.action_batch_tag -> {
                if (isBatchMode) {
                    exitBatchMode()
                } else {
                    enterBatchMode()
                }
                true
            }
            R.id.action_review -> {
                val intent = Intent(requireContext(), QuestionReviewActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showTagEditDialog(question: Question) {
        TagEditDialog.show(requireContext(), TagManager.parseTags(question.tags)) { newTags ->
            lifecycleScope.launch(Dispatchers.IO) {
                val tagsJson = org.json.JSONArray(newTags).toString()
                val updatedQuestion = question.copy(tags = tagsJson)
                viewModel.update(updatedQuestion)
                
                // 同步更新相关笔记和记忆卡片的标签
                val database = AppDatabase.getDatabase(requireContext())
                val standaloneNotes = database.standaloneNoteDao().getAllNotesSync()
                val standaloneFlashcards = database.standaloneFlashcardDao().getAllFlashcardsSync()
                
                // 更新相关笔记的标签
                standaloneNotes.filter { note -> note.questionId == question.id }.forEach { note ->
                    val updatedNote = note.copy(tags = tagsJson, updatedAt = System.currentTimeMillis())
                    database.standaloneNoteDao().update(updatedNote)
                }
                
                // 更新相关记忆卡片的标签
                standaloneFlashcards.filter { flashcard -> flashcard.questionId == question.id }.forEach { flashcard ->
                    val updatedFlashcard = flashcard.copy(tags = tagsJson, updatedAt = System.currentTimeMillis())
                    database.standaloneFlashcardDao().update(updatedFlashcard)
                }
            }
        }
    }
    
    private fun deleteQuestion(question: Question) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除题目")
            .setMessage("确定要删除这道题目吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    // 删除题目时，只排除应用私有目录的路径（永久存储路径）
                    // 不排除原始图片路径，这样用户可以重新导入这些图片
                    val database = com.gongkao.cuotifupan.data.AppDatabase.getDatabase(requireContext())
                    try {
                        val imageFile = java.io.File(question.imagePath)
                        // 只排除应用私有目录的路径（永久存储路径）
                        // 这样原始图片可以重新导入
                        if (question.imagePath.startsWith(requireContext().filesDir.absolutePath) || 
                            question.imagePath.startsWith(requireContext().cacheDir.absolutePath)) {
                            // 应用私有目录的路径，添加到排除列表（防止重复使用）
                            val excludedImage = com.gongkao.cuotifupan.data.ExcludedImage(
                                imagePath = question.imagePath,
                                reason = "已删除的题目（永久存储路径）"
                            )
                            database.excludedImageDao().insert(excludedImage)
                            android.util.Log.d("QuestionsFragment", "已排除永久存储路径: ${question.imagePath}")
                        }
                        // 注意：不排除原始图片路径（originalImagePath），这样用户可以重新导入
                    } catch (e: Exception) {
                        android.util.Log.e("QuestionsFragment", "添加排除图片时出错", e)
                    }
                    
                    viewModel.delete(question)
                    Toast.makeText(requireContext(), "题目已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 检查权限并执行重新扫描
     */
    private fun checkPermissionAndRescan() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        
        val granted = ContextCompat.checkSelfPermission(requireContext(), permission) ==
            PackageManager.PERMISSION_GRANTED
        
        if (granted) {
            // 已有权限，直接显示重新扫描对话框
            showRescanDialog()
        } else {
            // 没有权限，检查是否已经显示过权限说明
            val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
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
        
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.permission_explanation_title)
            .setMessage(R.string.permission_explanation_message)
            .setPositiveButton(R.string.permission_explanation_agree) { dialog, _ ->
                // 标记已显示过说明
                val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_shown_permission_explanation", true).apply()
                
                // 请求权限
                storagePermissionLauncher.launch(permission)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.permission_explanation_later) { dialog, _ ->
                // 用户选择稍后，标记已显示过说明
                val prefs = requireContext().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_shown_permission_explanation", true).apply()
                
                Toast.makeText(
                    requireContext(),
                    "需要相册权限才能重新扫描，您可以在设置中手动授予权限",
                    Toast.LENGTH_LONG
                ).show()
                dialog.dismiss()
            }
            .setCancelable(false) // 不允许点击外部取消
            .show()
    }
    
    private fun showRescanDialog() {
        // 创建输入框
        val input = EditText(requireContext()).apply {
            hint = "输入扫描数量（默认50）"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setText("50")
            selectAll() // 选中默认值，方便直接输入
        }
        
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
            addView(input)
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("重新扫描")
            .setMessage("将重新扫描相册中的图片，请输入扫描数量：")
            .setView(container)
            .setPositiveButton("确定") { _, _ ->
                val countText = input.text.toString().trim()
                val scanCount = if (countText.isNotBlank()) {
                    try {
                        countText.toInt().coerceIn(1, 1000) // 限制在1-1000之间
                    } catch (e: Exception) {
                        50 // 解析失败时使用默认值
                    }
                } else {
                    50 // 空值时使用默认值
                }
                
                // 执行重新扫描
                performRescan(scanCount)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 执行重新扫描
     */
    private fun performRescan(scanLimit: Int) {
        // 显示扫描对话框
        showScanningDialog(scanLimit)
        
        lifecycleScope.launch {
            try {
                // 使用 ImageScanner 工具类进行扫描
                withContext(Dispatchers.IO) {
                    ImageScanner.scanRecentImages(
                        requireContext(),
                        scanLimit,
                        isFirstLaunch = false,
                        onProgress = { progressText ->
                            updateScanningProgress(progressText)
                        }
                    )
                }
                
                withContext(Dispatchers.Main) {
                    hideScanningDialog()
                    Toast.makeText(requireContext(), "扫描完成", Toast.LENGTH_SHORT).show()
                    // LiveData 会自动更新，无需手动刷新
                }
            } catch (e: Exception) {
                android.util.Log.e("QuestionsFragment", "重新扫描失败", e)
                withContext(Dispatchers.Main) {
                    hideScanningDialog()
                    Toast.makeText(requireContext(), "扫描失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 显示扫描对话框
     */
    private fun showScanningDialog(scanLimit: Int) {
        if (scanningDialog == null) {
            val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_scanning, null)
            scanningProgressText = dialogView.findViewById(R.id.progressText)
            
            scanningDialog = AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(false) // 不允许取消
                .create()
            
            // 设置对话框样式 - 半透明，可以看到背景
            scanningDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
            scanningDialog?.window?.setDimAmount(0.0f) // 不遮挡背景，让用户看到卡片出现
            
            // 设置对话框位置在屏幕中央
            scanningDialog?.window?.setGravity(android.view.Gravity.CENTER)
        }
        
        // 更新进度文本
        scanningProgressText?.text = "正在扫描前$scanLimit 张图片是否为题目..."
        
        scanningDialog?.show()
    }
    
    /**
     * 更新扫描进度文本
     */
    private fun updateScanningProgress(text: String) {
        activity?.runOnUiThread {
            scanningProgressText?.text = text
        }
    }
    
    /**
     * 隐藏扫描对话框
     */
    private fun hideScanningDialog() {
        scanningDialog?.dismiss()
        scanningDialog = null
        scanningProgressText = null
    }
    
    private fun showClearAllDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("清空题库")
            .setMessage("确定要清空所有题目吗？此操作不可恢复。")
            .setPositiveButton("清空") { _, _ ->
                lifecycleScope.launch {
                    viewModel.deleteAll()
                    Toast.makeText(requireContext(), "题库已清空", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showTagSortDialog() {
        TagSortDialog.show(requireContext()) { selectedTag ->
            if (selectedTag.isNotEmpty()) {
                viewModel.setSortMode(com.gongkao.cuotifupan.viewmodel.QuestionViewModel.SortMode.TAG)
                adapter.setTimeSortMode(false)
                viewModel.getQuestionsByTag(selectedTag).observe(viewLifecycleOwner) { questions ->
                    updateQuestionsList(questions)
                }
                Toast.makeText(requireContext(), "已筛选标签: ${TagManager.formatTag(selectedTag)}", Toast.LENGTH_SHORT).show()
            } else {
                // 清空筛选，恢复按时间排序
                viewModel.setSortMode(com.gongkao.cuotifupan.viewmodel.QuestionViewModel.SortMode.TIME)
                adapter.setTimeSortMode(true)
                viewModel.allQuestionsOrderByTime.observe(viewLifecycleOwner) { questions ->
                    updateQuestionsList(questions)
                }
                Toast.makeText(requireContext(), "已清空标签筛选", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    fun enterBatchMode() {
        isBatchMode = true
        adapter.setBatchMode(true)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "选择题目（0）"
        (activity as? com.gongkao.cuotifupan.MainActivity)?.showBatchActionBar()
    }
    
    fun exitBatchMode() {
        isBatchMode = false
        selectedQuestions.clear()
        adapter.setBatchMode(false)
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.app_name)
        (activity as? com.gongkao.cuotifupan.MainActivity)?.hideBatchActionBar()
    }
    
    fun showBatchTagDialog() {
        val allTags = TagManager.getAllTags(requireContext())
        
        if (allTags.isEmpty()) {
            Toast.makeText(requireContext(), "没有可用标签，请先添加标签", Toast.LENGTH_SHORT).show()
            return
        }
        
        TagEditDialog.show(requireContext(), emptyList()) { selectedTags ->
            if (selectedTags.isNotEmpty()) {
                lifecycleScope.launch {
                    val questions = viewModel.allQuestionsOrderByTime.value ?: emptyList()
                    var updatedCount = 0
                    
                    selectedQuestions.forEach { questionId ->
                        val question = questions.find { it.id == questionId }
                        if (question != null) {
                            val currentTags = try {
                                if (question.tags.isNotBlank()) {
                                    org.json.JSONArray(question.tags).let { array ->
                                        (0 until array.length()).map { array.getString(it) }
                                    }
                                } else {
                                    emptyList()
                                }
                            } catch (e: Exception) {
                                emptyList()
                            }
                            
                            val mergedTags = (currentTags + selectedTags).distinct()
                            val tagsJson = org.json.JSONArray(mergedTags).toString()
                            val updatedQuestion = question.copy(tags = tagsJson)
                            
                            viewModel.update(updatedQuestion)
                            updatedCount++
                        }
                    }
                    
                    Toast.makeText(requireContext(), "已为 $updatedCount 道题目添加标签", Toast.LENGTH_SHORT).show()
                    exitBatchMode()
                }
            }
        }
    }
    
    /**
     * 显示批量删除对话框
     */
    fun showBatchDeleteDialog() {
        if (selectedQuestions.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择要删除的题目", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("批量删除")
            .setMessage("确定要删除选中的 ${selectedQuestions.size} 道题目吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val questions = viewModel.allQuestionsOrderByTime.value ?: emptyList()
                    val database = com.gongkao.cuotifupan.data.AppDatabase.getDatabase(requireContext())
                    var deletedCount = 0
                    
                    selectedQuestions.forEach { questionId ->
                        val question = questions.find { it.id == questionId }
                        if (question != null) {
                            try {
                                // 处理图片路径（只排除应用私有目录的路径）
                                if (question.imagePath.startsWith(requireContext().filesDir.absolutePath) || 
                                    question.imagePath.startsWith(requireContext().cacheDir.absolutePath)) {
                                    val excludedImage = com.gongkao.cuotifupan.data.ExcludedImage(
                                        imagePath = question.imagePath,
                                        reason = "已删除的题目（永久存储路径）"
                                    )
                                    database.excludedImageDao().insert(excludedImage)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("QuestionsFragment", "添加排除图片时出错", e)
                            }
                            
                            viewModel.delete(question)
                            deletedCount++
                        }
                    }
                    
                    Toast.makeText(requireContext(), "已删除 $deletedCount 道题目", Toast.LENGTH_SHORT).show()
                    exitBatchMode()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 显示批量擦写对话框
     */
    fun showBatchRemoveHandwritingDialog() {
        if (selectedQuestions.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择要擦写的题目", Toast.LENGTH_SHORT).show()
            return
        }
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("批量擦写")
            .setMessage("确定要擦除选中 ${selectedQuestions.size} 道题目的手写笔记吗？处理可能需要一些时间。")
            .setPositiveButton("确定") { _, _ ->
                batchRemoveHandwriting()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 批量擦写手写笔记
     */
    private fun batchRemoveHandwriting() {
        lifecycleScope.launch {
            val questions = viewModel.allQuestionsOrderByTime.value ?: emptyList()
            val selectedQuestionList = questions.filter { selectedQuestions.contains(it.id) }
            
            if (selectedQuestionList.isEmpty()) {
                Toast.makeText(requireContext(), "没有选中的题目", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // 显示进度对话框
            val progressDialog = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("批量擦写中")
                .setMessage("正在处理 0/${selectedQuestionList.size}...")
                .setCancelable(false)
                .create()
            
            progressDialog.show()
            
            var successCount = 0
            var failCount = 0
            var quotaExceededError: com.gongkao.cuotifupan.api.HandwritingRemovalService.HandwritingRemovalException? = null
            
            // 初始化手写擦除服务
            com.gongkao.cuotifupan.api.HandwritingRemovalService.init(requireContext().applicationContext)
            
            // 逐个处理题目
            selectedQuestionList.forEachIndexed { index, question ->
                try {
                    progressDialog.setMessage("正在处理 ${index + 1}/${selectedQuestionList.size}...")
                    
                    val result = withContext(Dispatchers.IO) {
                        try {
                            // 1. 确定原图路径
                            val originalImagePath = question.originalImagePath ?: question.imagePath
                            val imageFile = File(originalImagePath)
                            if (!imageFile.exists()) {
                                Log.e("QuestionsFragment", "原图文件不存在: $originalImagePath")
                                return@withContext null
                            }
                            
                            // 2. 加载原图
                            val originalBitmap = if (originalImagePath.startsWith(requireContext().filesDir.absolutePath) || 
                                originalImagePath.startsWith(requireContext().cacheDir.absolutePath) ||
                                originalImagePath.contains("/DCIM/Camera/")) {
                                android.graphics.BitmapFactory.decodeFile(originalImagePath)
                            } else {
                                com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(requireContext(), originalImagePath)
                            }
                            
                            if (originalBitmap == null) {
                                Log.e("QuestionsFragment", "无法加载原图: $originalImagePath")
                                return@withContext null
                            }
                            
                            // 3. 调用手写擦除服务
                            val processedBitmap = try {
                                com.gongkao.cuotifupan.api.HandwritingRemovalService.removeHandwriting(originalBitmap)
                            } catch (e: com.gongkao.cuotifupan.api.HandwritingRemovalService.HandwritingRemovalException) {
                                Log.e("QuestionsFragment", "手写擦除失败: ${question.id} - ${e.message} (${e.errorCode})")
                                throw e
                            }
                            originalBitmap.recycle()
                            
                            if (processedBitmap == null) {
                                Log.e("QuestionsFragment", "手写擦除失败: ${question.id}")
                                return@withContext null
                            }
                            
                            // 4. 保存擦写后的图片
                            val questionsDir = File(requireContext().filesDir, "questions")
                            if (!questionsDir.exists()) {
                                questionsDir.mkdirs()
                            }
                            
                            val extension = imageFile.extension.ifEmpty { "jpg" }
                            val cleanedImageFile = File(questionsDir, "question_${question.id}_cleaned.$extension")
                            
                            val format = when (extension.lowercase()) {
                                "png" -> android.graphics.Bitmap.CompressFormat.PNG
                                "webp" -> android.graphics.Bitmap.CompressFormat.WEBP
                                else -> android.graphics.Bitmap.CompressFormat.JPEG
                            }
                            
                            val quality = if (format == android.graphics.Bitmap.CompressFormat.PNG) 100 else 90
                            
                            FileOutputStream(cleanedImageFile).use { out ->
                                processedBitmap.compress(format, quality, out)
                            }
                            
                            processedBitmap.recycle()
                            
                            Pair(originalImagePath, cleanedImageFile.absolutePath)
                            
                        } catch (e: com.gongkao.cuotifupan.api.HandwritingRemovalService.HandwritingRemovalException) {
                            Log.e("QuestionsFragment", "批量擦写异常: ${question.id} - ${e.message} (${e.errorCode})", e)
                            // 记录第一个额度用尽的错误
                            if (e.errorCode == "QUOTA_EXCEEDED" && quotaExceededError == null) {
                                quotaExceededError = e
                            }
                            // 对于Pro相关错误，记录但不中断批量处理
                            null
                        } catch (e: Exception) {
                            Log.e("QuestionsFragment", "批量擦写异常: ${question.id}", e)
                            null
                        }
                    }
                    
                    if (result != null) {
                        val (originalImagePath, cleanedImagePath) = result
                        
                        // 5. 更新题目数据
                        val finalOriginalImagePath = question.originalImagePath ?: originalImagePath
                        val updatedQuestion = question.copy(
                            originalImagePath = finalOriginalImagePath,
                            cleanedImagePath = cleanedImagePath,
                            imagePath = cleanedImagePath  // 默认显示擦写后的图片
                        )
                        
                        viewModel.update(updatedQuestion)
                        successCount++
                    } else {
                        failCount++
                    }
                    
                } catch (e: Exception) {
                    Log.e("QuestionsFragment", "批量擦写失败: ${question.id}", e)
                    failCount++
                }
            }
            
            progressDialog.dismiss()
            
            // 如果遇到额度用尽错误，显示对话框提示
            val error = quotaExceededError
            if (error != null) {
                val context = requireContext()
                val redeemCodeUrl = com.gongkao.cuotifupan.util.ProManager.getRedeemCodeUrl(context)
                
                val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("额度已用尽")
                    .setMessage(error.message ?: "您的使用额度已用尽")
                
                // 如果有兑换码链接，显示"如何领取兑换码"按钮
                if (redeemCodeUrl != null && redeemCodeUrl.isNotBlank()) {
                    dialogBuilder.setPositiveButton("如何领取兑换码") { _, _ ->
                        // 跳转到如何领取兑换码页面
                        try {
                            val intent = Intent(context, com.gongkao.cuotifupan.ui.HowToGetRedeemCodeActivity::class.java)
                            startActivity(intent)
                        } catch (ex: Exception) {
                            Log.e("QuestionsFragment", "跳转失败", ex)
                            Toast.makeText(context, "无法打开页面", Toast.LENGTH_SHORT).show()
                        }
                    }
                    dialogBuilder.setNegativeButton("知道了", null)
                } else {
                    // 没有兑换码链接，只显示"知道了"按钮
                    dialogBuilder.setPositiveButton("知道了", null)
                }
                
                dialogBuilder.show()
            }
            
            // 显示结果
            val message = when {
                failCount == 0 -> "批量擦写完成！成功处理 $successCount 道题目"
                successCount == 0 -> "批量擦写失败！$failCount 道题目处理失败"
                else -> "批量擦写完成！成功 $successCount 道，失败 $failCount 道"
            }
            
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            exitBatchMode()
        }
    }
    
    
    override fun onDestroyView() {
        super.onDestroyView()
        // 隐藏扫描对话框
        hideScanningDialog()
    }
}


