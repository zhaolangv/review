package com.gongkao.cuotifupan.ui

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
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
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.StandaloneNote
import com.gongkao.cuotifupan.ui.TagManager
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 笔记列表 Fragment
 */
class NotesFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: StandaloneNotesAdapter
    private lateinit var searchEditText: EditText
    
    private var currentSortMode: SortMode = SortMode.UPDATE_TIME
    private var currentTag: String? = null
    private var searchKeyword: String = ""
    private var isBatchMode = false
    private val selectedNotes = mutableSetOf<String>()
    
    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importNotesFromFile(it) }
    }
    
    enum class SortMode {
        UPDATE_TIME,  // 按更新时间
        CREATE_TIME,  // 按创建时间
        TAG           // 按标签
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.activity_notes_list, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        (activity as? AppCompatActivity)?.supportActionBar?.title = "笔记管理"
        
        initViews()
        loadNotes()
    }
    
    private fun initViews() {
        recyclerView = view?.findViewById(R.id.recyclerView) ?: return
        emptyView = view?.findViewById(R.id.emptyView) ?: return
        searchEditText = view?.findViewById(R.id.searchEditText) ?: return
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = StandaloneNotesAdapter(
            onItemClick = { note ->
                val intent = Intent(requireContext(), NoteEditActivity::class.java)
                intent.putExtra("note_id", note.id)
                startActivity(intent)
            },
            onDelete = { note ->
                deleteNote(note)
            },
            onTagClick = { tag ->
                currentTag = tag
                loadNotes()
            },
            onEditTags = { note ->
                showTagEditDialog(note)
            },
            onQuestionClick = { note ->
                if (note.questionId != null) {
                    val intent = Intent(requireContext(), QuestionDetailCardActivity::class.java)
                    intent.putExtra("question_id", note.questionId)
                    startActivity(intent)
                }
            },
        )
        recyclerView.adapter = adapter
        
        // 设置选择变化监听
        adapter.setOnSelectionChanged { count ->
            (activity as? AppCompatActivity)?.supportActionBar?.title = "选择笔记（$count）"
        }
        
        searchEditText.setOnEditorActionListener { _, _, _ ->
            searchKeyword = searchEditText.text.toString().trim()
            loadNotes()
            true
        }
    }
    
    fun enterBatchMode() {
        isBatchMode = true
        adapter.setBatchMode(true)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "选择笔记（0）"
        (activity as? com.gongkao.cuotifupan.MainActivity)?.showBatchActionBar()
    }
    
    fun exitBatchMode() {
        isBatchMode = false
        selectedNotes.clear()
        adapter.setBatchMode(false)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "笔记管理"
        (activity as? com.gongkao.cuotifupan.MainActivity)?.hideBatchActionBar()
        loadNotes()
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
                    val database = AppDatabase.getDatabase(requireContext())
                    val notes = database.standaloneNoteDao().getAllNotesSync()
                    var updatedCount = 0
                    
                    // 从 adapter 获取选中的笔记
                    val selectedIds = adapter.getSelectedNotes()
                    
                    selectedIds.forEach { noteId ->
                        val note = notes.find { it.id == noteId }
                        if (note != null) {
                            val currentTags = TagManager.parseTags(note.tags)
                            val mergedTags = (currentTags + selectedTags).distinct()
                            val tagsJson = TagManager.formatTags(mergedTags)
                            val updatedNote = note.copy(tags = tagsJson, updatedAt = System.currentTimeMillis())
                            
                            withContext(Dispatchers.IO) {
                                database.standaloneNoteDao().update(updatedNote)
                            }
                            updatedCount++
                        }
                    }
                    
                    Toast.makeText(requireContext(), "已为 $updatedCount 条笔记添加标签", Toast.LENGTH_SHORT).show()
                    exitBatchMode()
                }
            }
        }
    }
    
    /**
     * 显示批量删除对话框
     */
    fun showBatchDeleteDialog() {
        val selectedIds = adapter.getSelectedNotes()
        
        if (selectedIds.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择要删除的笔记", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("批量删除")
            .setMessage("确定要删除选中的 ${selectedIds.size} 条笔记吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val database = AppDatabase.getDatabase(requireContext())
                    val notes = database.standaloneNoteDao().getAllNotesSync()
                    var deletedCount = 0
                    
                    selectedIds.forEach { noteId ->
                        val note = notes.find { it.id == noteId }
                        if (note != null) {
                            withContext(Dispatchers.IO) {
                                database.standaloneNoteDao().delete(note)
                            }
                            deletedCount++
                        }
                    }
                    
                    Toast.makeText(requireContext(), "已删除 $deletedCount 条笔记", Toast.LENGTH_SHORT).show()
                    exitBatchMode()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun loadNotes() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(requireContext())
            
            // 1. 从独立笔记表中获取
            val standaloneNotes = when {
                searchKeyword.isNotEmpty() -> {
                    database.standaloneNoteDao().searchNotes(searchKeyword)
                }
                currentTag != null -> {
                    database.standaloneNoteDao().getNotesByTag(currentTag!!)
                }
                else -> {
                    when (currentSortMode) {
                        SortMode.UPDATE_TIME -> database.standaloneNoteDao().getAllNotes()
                        SortMode.CREATE_TIME -> database.standaloneNoteDao().getAllNotesOrderByCreatedTime()
                        SortMode.TAG -> database.standaloneNoteDao().getAllNotes()
                    }
                }
            }
            
            // 2. 从所有题目的 userNotes 中提取笔记
            val allQuestions = database.questionDao().getAllQuestionsSync()
            val notesFromQuestions = mutableListOf<StandaloneNote>()
            
            allQuestions.forEach { question ->
                if (question.userNotes.isNotBlank()) {
                    val parsedNotes = parseNotesFromQuestion(question.userNotes)
                    parsedNotes.filter { it.type == "note" }.forEach { noteItem ->
                        // 检查是否已经在独立表中存在（通过ID匹配）
                        val existingNote = notesFromQuestions.find { it.id == noteItem.id }
                        if (existingNote == null) {
                            notesFromQuestions.add(StandaloneNote(
                                id = noteItem.id,
                                content = noteItem.content,
                                createdAt = noteItem.timestamp,
                                updatedAt = noteItem.timestamp,
                                questionId = question.id,
                                tags = question.tags, // 继承题目的标签
                                isFavorite = false
                            ))
                        } else {
                            // 如果已存在，更新标签以保持与题目同步
                            val index = notesFromQuestions.indexOf(existingNote)
                            if (index >= 0) {
                                notesFromQuestions[index] = existingNote.copy(tags = question.tags)
                            }
                        }
                    }
                }
            }
            
            // 3. 合并数据并去重（优先使用有标签的版本）
            standaloneNotes.observe(viewLifecycleOwner) { standaloneNoteList ->
                // 创建一个映射，优先使用有标签的版本
                val notesMap = mutableMapOf<String, StandaloneNote>()
                
                // 先添加独立表中的笔记
                standaloneNoteList.forEach { note ->
                    notesMap[note.id] = note
                }
                
                // 然后添加从题目中解析的笔记，如果标签不为空则覆盖
                notesFromQuestions.forEach { note ->
                    val existing = notesMap[note.id]
                    if (existing == null) {
                        // 如果不存在，直接添加
                        notesMap[note.id] = note
                    } else if (existing.tags.isBlank() && note.tags.isNotBlank()) {
                        // 如果现有标签为空，但题目有标签，使用题目版本并更新数据库
                        notesMap[note.id] = note
                        // 异步更新数据库中的标签
                        lifecycleScope.launch(Dispatchers.IO) {
                            val database = AppDatabase.getDatabase(requireContext())
                            database.standaloneNoteDao().update(note)
                        }
                    } else if (note.tags.isNotBlank()) {
                        // 如果题目有标签，使用题目版本（保持与题目同步）
                        notesMap[note.id] = note
                    }
                }
                
                val allNotes = notesMap.values.toList()
                
                // 应用搜索和标签过滤
                val filteredNotes = when {
                    searchKeyword.isNotEmpty() -> {
                        allNotes.filter { it.content.contains(searchKeyword, ignoreCase = true) }
                    }
                    currentTag != null -> {
                        allNotes.filter { TagManager.parseTags(it.tags).contains(currentTag) }
                    }
                    else -> allNotes
                }
                
                // 排序
                val sortedNotes = when (currentSortMode) {
                    SortMode.UPDATE_TIME -> filteredNotes.sortedByDescending { it.updatedAt }
                    SortMode.CREATE_TIME -> filteredNotes.sortedByDescending { it.createdAt }
                    SortMode.TAG -> {
                        if (currentTag == null) {
                            filteredNotes.sortedBy { TagManager.getFirstTag(it.tags) }
                        } else {
                            filteredNotes
                        }
                    }
                }
                
                // 按日期分组
                val listItems = groupNotesByDate(sortedNotes)
                
                android.util.Log.d("NotesFragment", "加载笔记: 独立表 ${standaloneNoteList.size} 条, 题目中 ${notesFromQuestions.size} 条, 合并后 ${sortedNotes.size} 条, 分组后 ${listItems.size} 项")
                adapter.submitList(listItems)
                emptyView.visibility = if (listItems.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun parseNotesFromQuestion(notesJson: String): List<NoteItem> {
        return try {
            if (notesJson.isNotBlank()) {
                JSONArray(notesJson).let { array ->
                    (0 until array.length()).map { index ->
                        val noteObj = array.getJSONObject(index)
                        val type = noteObj.optString("type", "note")
                        NoteItem(
                            id = noteObj.optString("id", index.toString()),
                            type = type,
                            content = noteObj.optString("content", ""),
                            front = noteObj.optString("front", ""),
                            back = noteObj.optString("back", ""),
                            timestamp = noteObj.optLong("timestamp", System.currentTimeMillis())
                        )
                    }
                }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            // 如果不是JSON格式，当作单条笔记处理
            if (notesJson.isNotBlank()) {
                listOf(NoteItem(
                    id = "0",
                    type = "note",
                    content = notesJson,
                    front = "",
                    back = "",
                    timestamp = System.currentTimeMillis()
                ))
            } else {
                emptyList()
            }
        }
    }
    
    private data class NoteItem(
        val id: String,
        val type: String,
        val content: String,
        val front: String,
        val back: String,
        val timestamp: Long
    )
    
    private fun showTagEditDialog(note: StandaloneNote) {
        TagEditDialog.show(requireContext(), TagManager.parseTags(note.tags)) { newTags ->
            lifecycleScope.launch(Dispatchers.IO) {
                val database = AppDatabase.getDatabase(requireContext())
                val tagsJson = org.json.JSONArray(newTags).toString()
                val updatedNote = note.copy(tags = tagsJson, updatedAt = System.currentTimeMillis())
                
                // 更新独立表中的笔记
                if (database.standaloneNoteDao().getNoteById(note.id) != null) {
                    database.standaloneNoteDao().update(updatedNote)
                }
                
                // 如果笔记来自题目，同时更新题目中的笔记标签（但不覆盖题目的标签）
                if (note.questionId != null) {
                    val question = database.questionDao().getQuestionById(note.questionId)
                    if (question != null) {
                        // 更新题目的标签（合并笔记的标签和题目的标签）
                        val questionTags = TagManager.parseTags(question.tags).toMutableList()
                        val noteTags = newTags.toMutableList()
                        val mergedTags = (questionTags + noteTags).distinct()
                        val updatedQuestion = question.copy(tags = org.json.JSONArray(mergedTags).toString())
                        database.questionDao().update(updatedQuestion)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    loadNotes()
                    Toast.makeText(requireContext(), "标签已更新", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun deleteNote(note: StandaloneNote) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除笔记")
            .setMessage("确定要删除这条笔记吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val database = AppDatabase.getDatabase(requireContext())
                    database.standaloneNoteDao().delete(note)
                    loadNotes()
                    Toast.makeText(requireContext(), "笔记已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.notes_list_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_note -> {
                val intent = Intent(requireContext(), NoteEditActivity::class.java)
                startActivity(intent)
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
            R.id.action_sort_time -> {
                currentSortMode = SortMode.UPDATE_TIME
                currentTag = null
                loadNotes()
                Toast.makeText(requireContext(), "已切换为按更新时间排序", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sort_create_time -> {
                currentSortMode = SortMode.CREATE_TIME
                currentTag = null
                loadNotes()
                Toast.makeText(requireContext(), "已切换为按创建时间排序", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sort_tag -> {
                showTagSortDialog()
                true
            }
            R.id.action_export -> {
                exportNotes()
                true
            }
            R.id.action_import -> {
                importNotes()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showTagSortDialog() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(requireContext())
            val allNotes = database.standaloneNoteDao().getAllNotesSync()
            val allTags = mutableSetOf<String>()
            allNotes.forEach { note ->
                TagManager.parseTags(note.tags).forEach { tag ->
                    allTags.add(tag)
                }
            }
            
            if (allTags.isEmpty()) {
                Toast.makeText(requireContext(), "没有可用的标签", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val tagArray = allTags.sorted().toTypedArray()
            AlertDialog.Builder(requireContext())
                .setTitle("选择标签")
                .setItems(tagArray) { _, which ->
                    currentTag = tagArray[which]
                    currentSortMode = SortMode.TAG
                    loadNotes()
                    Toast.makeText(requireContext(), "已筛选标签: ${TagManager.formatTag(tagArray[which])}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    private fun exportNotes() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(requireContext())
            val notes = database.standaloneNoteDao().getAllNotesSync()
            
            if (notes.isEmpty()) {
                Toast.makeText(requireContext(), "没有可导出的笔记", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // 将笔记转换为 JSON
            val jsonArray = JSONArray()
            notes.forEach { note ->
                val noteObj = JSONObject().apply {
                    put("id", note.id)
                    put("content", note.content)
                    put("createdAt", note.createdAt)
                    put("updatedAt", note.updatedAt)
                    put("tags", note.tags)
                    put("questionId", note.questionId ?: JSONObject.NULL)
                    put("isFavorite", note.isFavorite)
                }
                jsonArray.put(noteObj)
            }
            
            val exportData = JSONObject().apply {
                put("version", 1)
                put("type", "notes")
                put("exportTime", System.currentTimeMillis())
                put("count", notes.size)
                put("notes", jsonArray)
            }
            
            // 保存到文件
            val success = withContext(Dispatchers.IO) {
                saveExportFile(exportData.toString(), "notes_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json")
            }
            
            if (success) {
                Toast.makeText(requireContext(), "成功导出 ${notes.size} 条笔记到 Downloads 目录", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "导出失败，请检查存储权限", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun importNotes() {
        filePickerLauncher.launch("application/json")
    }
    
    private fun importNotesFromFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                        BufferedReader(InputStreamReader(inputStream)).use { reader ->
                            reader.readText()
                        }
                    }
                } ?: run {
                    Toast.makeText(requireContext(), "无法读取文件", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val jsonObj = JSONObject(content)
                val version = jsonObj.optInt("version", 1)
                val type = jsonObj.optString("type", "")
                
                if (type != "notes") {
                    Toast.makeText(requireContext(), "文件格式不正确", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val notesArray = jsonObj.getJSONArray("notes")
                val database = AppDatabase.getDatabase(requireContext())
                
                var successCount = 0
                var skipCount = 0
                
                withContext(Dispatchers.IO) {
                    for (i in 0 until notesArray.length()) {
                        val noteObj = notesArray.getJSONObject(i)
                        val note = StandaloneNote(
                            id = noteObj.optString("id", ""),
                            content = noteObj.getString("content"),
                            createdAt = noteObj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = noteObj.optLong("updatedAt", System.currentTimeMillis()),
                            tags = noteObj.optString("tags", ""),
                            questionId = if (noteObj.isNull("questionId")) null else noteObj.optString("questionId"),
                            isFavorite = noteObj.optBoolean("isFavorite", false)
                        )
                        
                        // 检查是否已存在
                        val existing = database.standaloneNoteDao().getNoteById(note.id)
                        if (existing == null) {
                            database.standaloneNoteDao().insert(note)
                            successCount++
                        } else {
                            skipCount++
                        }
                    }
                }
                
                val message = when {
                    skipCount > 0 -> "成功导入 $successCount 条笔记，跳过 $skipCount 条重复笔记"
                    else -> "成功导入 $successCount 条笔记"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                
                // 刷新列表
                loadNotes()
                
            } catch (e: Exception) {
                android.util.Log.e("NotesFragment", "导入失败", e)
                Toast.makeText(requireContext(), "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun saveExportFile(content: String, fileName: String): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ 使用 MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                
                val uri = requireContext().contentResolver.insert(
                    MediaStore.Files.getContentUri("external"),
                    contentValues
                )
                
                uri?.let {
                    requireContext().contentResolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                    }
                    true
                } ?: false
            } else {
                // Android 9 及以下使用传统方式
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val file = java.io.File(downloadsDir, fileName)
                file.writeText(content)
                true
            }
        } catch (e: Exception) {
            android.util.Log.e("NotesFragment", "保存文件失败", e)
            false
        }
    }
    
    override fun onResume() {
        super.onResume()
        loadNotes()
    }
    
    private fun showNoteContentDialog(note: StandaloneNote) {
        AlertDialog.Builder(requireContext())
            .setTitle("笔记内容")
            .setMessage(note.content)
            .setPositiveButton("编辑") { _, _ ->
                val intent = Intent(requireContext(), NoteEditActivity::class.java)
                intent.putExtra("note_id", note.id)
                startActivity(intent)
            }
            .setNeutralButton("删除") { _, _ ->
                deleteNote(note)
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun groupNotesByDate(notes: List<StandaloneNote>): List<StandaloneNotesAdapter.ListItem> {
        val listItems = mutableListOf<StandaloneNotesAdapter.ListItem>()
        val calendar = java.util.Calendar.getInstance()
        val today = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val displayDateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
        
        var currentDateKey: String? = null
        
        notes.forEach { note ->
            calendar.timeInMillis = note.createdAt
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
                
                listItems.add(StandaloneNotesAdapter.ListItem.DateHeader(dateText, dateKey))
            }
            
            listItems.add(StandaloneNotesAdapter.ListItem.NoteItem(note))
        }
        
        return listItems
    }
}

