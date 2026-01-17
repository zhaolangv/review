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
import android.widget.Button
import android.widget.EditText
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
import com.gongkao.cuotifupan.data.FlashcardDeck
import com.gongkao.cuotifupan.data.StandaloneFlashcard
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
import java.util.UUID

/**
 * 记忆卡片列表 Fragment
 */
class FlashcardsFragment : Fragment() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: StandaloneFlashcardsAdapter
    private lateinit var searchEditText: EditText
    private lateinit var viewModeButton: com.google.android.material.button.MaterialButton
    
    private var currentSortMode: SortMode = SortMode.UPDATE_TIME
    private var currentTag: String? = null
    private var currentDeckId: String? = null
    private var searchKeyword: String = ""
    private var isBatchMode = false
    private var isDeckView = false // false=标签视图, true=文件夹视图
    private val selectedFlashcards = mutableSetOf<String>()
    
    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { importFlashcardsFromFile(it) }
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
        return inflater.inflate(R.layout.activity_flashcards_list, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        (activity as? AppCompatActivity)?.supportActionBar?.title = "记忆卡片管理"
        
        initViews()
        loadFlashcards()
    }
    
    override fun onResume() {
        super.onResume()
        // 从编辑/复习页面返回时刷新列表
        loadFlashcards()
    }
    
    override fun onPause() {
        super.onPause()
        // 当Fragment失去焦点时（切换到其他页面），自动退出批量模式
        if (isBatchMode) {
            exitBatchMode()
        }
    }
    
    private fun initViews() {
        recyclerView = view?.findViewById(R.id.recyclerView) ?: return
        emptyView = view?.findViewById(R.id.emptyView) ?: return
        searchEditText = view?.findViewById(R.id.searchEditText) ?: return
        viewModeButton = view?.findViewById(R.id.viewModeButton) ?: return
        val startReviewButton = view?.findViewById<Button>(R.id.startReviewButton) ?: return
        
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = StandaloneFlashcardsAdapter(
            onItemClick = { flashcard ->
                val intent = Intent(requireContext(), FlashcardEditActivity::class.java)
                intent.putExtra("flashcard_id", flashcard.id)
                startActivity(intent)
            },
            onDelete = { flashcard ->
                deleteFlashcard(flashcard)
            },
            onTagClick = { tag ->
                currentTag = tag
                loadFlashcards()
            },
            onEditTags = { flashcard ->
                showTagEditDialog(flashcard)
            },
            onQuestionClick = { flashcard ->
                if (flashcard.questionId != null) {
                    val intent = Intent(requireContext(), QuestionDetailCardActivity::class.java)
                    intent.putExtra("question_id", flashcard.questionId)
                    startActivity(intent)
                }
            },
        )
        recyclerView.adapter = adapter
        
        // 设置选择变化监听
        adapter.setOnSelectionChanged { count ->
            (activity as? AppCompatActivity)?.supportActionBar?.title = "选择记忆卡片（$count）"
        }
        
        searchEditText.setOnEditorActionListener { _, _, _ ->
            searchKeyword = searchEditText.text.toString().trim()
            loadFlashcards()
            true
        }
        
        // 视图切换按钮
        viewModeButton.setOnClickListener {
            isDeckView = !isDeckView
            currentDeckId = null
            currentTag = null
            viewModeButton.text = if (isDeckView) "文件夹视图" else "标签视图"
            loadFlashcards()
        }
        
        // 开始复习按钮
        startReviewButton.setOnClickListener {
            val intent = Intent(requireContext(), FlashcardReviewActivity::class.java)
            startActivity(intent)
        }
    }
    
    fun enterBatchMode() {
        isBatchMode = true
        adapter.setBatchMode(true)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "选择记忆卡片（0）"
        (activity as? com.gongkao.cuotifupan.MainActivity)?.showBatchActionBar()
    }
    
    fun exitBatchMode() {
        isBatchMode = false
        selectedFlashcards.clear()
        adapter.setBatchMode(false)
        (activity as? AppCompatActivity)?.supportActionBar?.title = "记忆卡片管理"
        (activity as? com.gongkao.cuotifupan.MainActivity)?.hideBatchActionBar()
        loadFlashcards()
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
                    val flashcards = database.standaloneFlashcardDao().getAllFlashcardsSync()
                    var updatedCount = 0
                    
                    // 从 adapter 获取选中的记忆卡片
                    val selectedIds = adapter.getSelectedFlashcards()
                    
                    selectedIds.forEach { flashcardId ->
                        val flashcard = flashcards.find { it.id == flashcardId }
                        if (flashcard != null) {
                            val currentTags = TagManager.parseTags(flashcard.tags)
                            val mergedTags = (currentTags + selectedTags).distinct()
                            val tagsJson = TagManager.formatTags(mergedTags)
                            val updatedFlashcard = flashcard.copy(tags = tagsJson, updatedAt = System.currentTimeMillis())
                            
                            withContext(Dispatchers.IO) {
                                database.standaloneFlashcardDao().update(updatedFlashcard)
                            }
                            updatedCount++
                        }
                    }
                    
                    Toast.makeText(requireContext(), "已为 $updatedCount 条记忆卡片添加标签", Toast.LENGTH_SHORT).show()
                    exitBatchMode()
                }
            }
        }
    }
    
    /**
     * 显示批量删除对话框
     */
    fun showBatchDeleteDialog() {
        val selectedIds = adapter.getSelectedFlashcards()
        
        if (selectedIds.isEmpty()) {
            Toast.makeText(requireContext(), "请先选择要删除的记忆卡片", Toast.LENGTH_SHORT).show()
            return
        }
        
        AlertDialog.Builder(requireContext())
            .setTitle("批量删除")
            .setMessage("确定要删除选中的 ${selectedIds.size} 张记忆卡片吗？此操作不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val database = AppDatabase.getDatabase(requireContext())
                    val flashcards = database.standaloneFlashcardDao().getAllFlashcardsSync()
                    var deletedCount = 0
                    
                    selectedIds.forEach { flashcardId ->
                        val flashcard = flashcards.find { it.id == flashcardId }
                        if (flashcard != null) {
                            withContext(Dispatchers.IO) {
                                database.standaloneFlashcardDao().delete(flashcard)
                            }
                            deletedCount++
                        }
                    }
                    
                    Toast.makeText(requireContext(), "已删除 $deletedCount 张记忆卡片", Toast.LENGTH_SHORT).show()
                    exitBatchMode()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun loadFlashcards() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(requireContext())
            
            // 1. 从独立记忆卡片表中获取
            val standaloneFlashcards = when {
                searchKeyword.isNotEmpty() -> {
                    database.standaloneFlashcardDao().searchFlashcards(searchKeyword)
                }
                currentTag != null -> {
                    database.standaloneFlashcardDao().getFlashcardsByTag(currentTag!!)
                }
                else -> {
                    when (currentSortMode) {
                        SortMode.UPDATE_TIME -> database.standaloneFlashcardDao().getAllFlashcards()
                        SortMode.CREATE_TIME -> database.standaloneFlashcardDao().getAllFlashcardsOrderByCreatedTime()
                        SortMode.TAG -> database.standaloneFlashcardDao().getAllFlashcards()
                    }
                }
            }
            
            // 2. 从所有题目的 userNotes 中提取记忆卡片
            val allQuestions = database.questionDao().getAllQuestionsSync()
            val flashcardsFromQuestions = mutableListOf<StandaloneFlashcard>()
            
            allQuestions.forEach { question ->
                if (question.userNotes.isNotBlank()) {
                    val parsedNotes = parseNotesFromQuestion(question.userNotes)
                    parsedNotes.filter { it.type == "flashcard" }.forEach { noteItem ->
                        // 检查是否已经在独立表中存在（通过ID匹配）
                        val existingFlashcard = flashcardsFromQuestions.find { it.id == noteItem.id }
                        if (existingFlashcard == null) {
                            flashcardsFromQuestions.add(StandaloneFlashcard(
                                id = noteItem.id,
                                front = noteItem.front,
                                back = noteItem.back,
                                createdAt = noteItem.timestamp,
                                updatedAt = noteItem.timestamp,
                                questionId = question.id,
                                tags = question.tags, // 继承题目的标签
                                isFavorite = false,
                                reviewState = "new"  // 新卡片，使用 Anki 风格的状态
                            ))
                        } else {
                            // 如果已存在，更新标签以保持与题目同步
                            val index = flashcardsFromQuestions.indexOf(existingFlashcard)
                            if (index >= 0) {
                                flashcardsFromQuestions[index] = existingFlashcard.copy(tags = question.tags)
                            }
                        }
                    }
                }
            }
            
            // 3. 合并数据并去重（优先使用有标签的版本）
            standaloneFlashcards.observe(viewLifecycleOwner) { standaloneFlashcardList ->
                // 创建一个映射，优先使用有标签的版本
                val flashcardsMap = mutableMapOf<String, StandaloneFlashcard>()
                
                // 先添加独立表中的记忆卡片
                standaloneFlashcardList.forEach { flashcard ->
                    flashcardsMap[flashcard.id] = flashcard
                }
                
                // 然后添加从题目中解析的记忆卡片，如果标签不为空则覆盖
                flashcardsFromQuestions.forEach { flashcard ->
                    val existing = flashcardsMap[flashcard.id]
                    if (existing == null) {
                        // 如果不存在，直接添加
                        flashcardsMap[flashcard.id] = flashcard
                    } else if (existing.tags.isBlank() && flashcard.tags.isNotBlank()) {
                        // 如果现有标签为空，但题目有标签，使用题目版本并更新数据库
                        flashcardsMap[flashcard.id] = flashcard
                        // 异步更新数据库中的标签
                        lifecycleScope.launch(Dispatchers.IO) {
                            val database = AppDatabase.getDatabase(requireContext())
                            database.standaloneFlashcardDao().update(flashcard)
                        }
                    } else if (flashcard.tags.isNotBlank()) {
                        // 如果题目有标签，使用题目版本（保持与题目同步）
                        flashcardsMap[flashcard.id] = flashcard
                    }
                }
                
                val allFlashcards = flashcardsMap.values.toList()
                
                // 应用搜索和标签过滤
                val filteredFlashcards = when {
                    searchKeyword.isNotEmpty() -> {
                        allFlashcards.filter { 
                            it.front.contains(searchKeyword, ignoreCase = true) || 
                            it.back.contains(searchKeyword, ignoreCase = true) 
                        }
                    }
                    currentTag != null -> {
                        allFlashcards.filter { TagManager.parseTags(it.tags).contains(currentTag) }
                    }
                    else -> allFlashcards
                }
                
                // 排序
                val sortedFlashcards = when (currentSortMode) {
                    SortMode.UPDATE_TIME -> filteredFlashcards.sortedByDescending { it.updatedAt }
                    SortMode.CREATE_TIME -> filteredFlashcards.sortedByDescending { it.createdAt }
                    SortMode.TAG -> {
                        if (currentTag == null) {
                            filteredFlashcards.sortedBy { TagManager.getFirstTag(it.tags) }
                        } else {
                            filteredFlashcards
                        }
                    }
                }
                
                // 按视图模式分组
                if (isDeckView) {
                    // 文件夹视图：按卡包分组
                    lifecycleScope.launch {
                        val listItems = groupFlashcardsByDeck(sortedFlashcards)
                        android.util.Log.d("FlashcardsFragment", "加载记忆卡片: 独立表 ${standaloneFlashcardList.size} 条, 题目中 ${flashcardsFromQuestions.size} 条, 合并后 ${sortedFlashcards.size} 条, 分组后 ${listItems.size} 项")
                        adapter.submitList(listItems)
                        emptyView.visibility = if (listItems.isEmpty()) View.VISIBLE else View.GONE
                    }
                } else {
                    // 标签视图：按日期分组
                    val listItems = groupFlashcardsByDate(sortedFlashcards)
                    android.util.Log.d("FlashcardsFragment", "加载记忆卡片: 独立表 ${standaloneFlashcardList.size} 条, 题目中 ${flashcardsFromQuestions.size} 条, 合并后 ${sortedFlashcards.size} 条, 分组后 ${listItems.size} 项")
                    adapter.submitList(listItems)
                    emptyView.visibility = if (listItems.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }
    
    /**
     * 按卡包分组（文件夹视图）- 支持层级结构
     */
    private suspend fun groupFlashcardsByDeck(flashcards: List<StandaloneFlashcard>): List<StandaloneFlashcardsAdapter.ListItem> {
        val database = AppDatabase.getDatabase(requireContext())
        val decks = withContext(Dispatchers.IO) {
            database.flashcardDeckDao().getAllDecksSync()
        }
        
        val listItems = mutableListOf<StandaloneFlashcardsAdapter.ListItem>()
        
        // 1. 按卡包分组
        val deckMap = mutableMapOf<String?, MutableList<StandaloneFlashcard>>()
        flashcards.forEach { flashcard ->
            val deckId = flashcard.deckId
            if (!deckMap.containsKey(deckId)) {
                deckMap[deckId] = mutableListOf()
            }
            deckMap[deckId]?.add(flashcard)
        }
        
        // 2. 构建卡包层级结构（parentId -> List<FlashcardDeck>）
        val childrenMap = mutableMapOf<String?, MutableList<FlashcardDeck>>()
        decks.forEach { deck ->
            val parentId = deck.parentId
            if (!childrenMap.containsKey(parentId)) {
                childrenMap[parentId] = mutableListOf()
            }
            childrenMap[parentId]?.add(deck)
        }
        
        // 3. 递归函数：添加卡包及其卡片
        fun addDeckAndCards(deck: FlashcardDeck, level: Int) {
            val deckFlashcards = deckMap[deck.id] ?: emptyList()
            if (deckFlashcards.isNotEmpty()) {
                listItems.add(StandaloneFlashcardsAdapter.ListItem.DateHeader(deck.name, "deck_${deck.id}", level))
                deckFlashcards.forEach { flashcard ->
                    listItems.add(StandaloneFlashcardsAdapter.ListItem.FlashcardItem(flashcard))
                }
            }
            
            // 递归添加子卡包
            val childDecks = childrenMap[deck.id] ?: emptyList()
            childDecks.forEach { childDeck ->
                addDeckAndCards(childDeck, level + 1)
            }
        }
        
        // 4. 从根卡包开始递归显示
        val rootDecks = childrenMap[null] ?: emptyList()
        rootDecks.forEach { rootDeck ->
            addDeckAndCards(rootDeck, 0)
        }
        
        // 5. 显示未分类的卡片
        val uncategorizedFlashcards = deckMap[null] ?: emptyList()
        if (uncategorizedFlashcards.isNotEmpty()) {
            listItems.add(StandaloneFlashcardsAdapter.ListItem.DateHeader("未分类", "deck_null", 0))
            uncategorizedFlashcards.forEach { flashcard ->
                listItems.add(StandaloneFlashcardsAdapter.ListItem.FlashcardItem(flashcard))
            }
        }
        
        return listItems
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
            // 如果不是JSON格式，返回空列表（记忆卡片必须是JSON格式）
            emptyList()
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
    
    private fun showTagEditDialog(flashcard: StandaloneFlashcard) {
        TagEditDialog.show(requireContext(), TagManager.parseTags(flashcard.tags)) { newTags ->
            lifecycleScope.launch(Dispatchers.IO) {
                val database = AppDatabase.getDatabase(requireContext())
                val tagsJson = org.json.JSONArray(newTags).toString()
                val updatedFlashcard = flashcard.copy(tags = tagsJson, updatedAt = System.currentTimeMillis())
                
                // 更新独立表中的记忆卡片
                if (database.standaloneFlashcardDao().getFlashcardById(flashcard.id) != null) {
                    database.standaloneFlashcardDao().update(updatedFlashcard)
                }
                
                // 如果记忆卡片来自题目，同时更新题目中的记忆卡片标签（但不覆盖题目的标签）
                if (flashcard.questionId != null) {
                    val question = database.questionDao().getQuestionById(flashcard.questionId)
                    if (question != null) {
                        // 更新题目的标签（合并记忆卡片的标签和题目的标签）
                        val questionTags = TagManager.parseTags(question.tags).toMutableList()
                        val flashcardTags = newTags.toMutableList()
                        val mergedTags = (questionTags + flashcardTags).distinct()
                        val updatedQuestion = question.copy(tags = org.json.JSONArray(mergedTags).toString())
                        database.questionDao().update(updatedQuestion)
                    }
                }
                
                withContext(Dispatchers.Main) {
                    loadFlashcards()
                    Toast.makeText(requireContext(), "标签已更新", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun deleteFlashcard(flashcard: StandaloneFlashcard) {
        AlertDialog.Builder(requireContext())
            .setTitle("删除记忆卡片")
            .setMessage("确定要删除这张记忆卡片吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val database = AppDatabase.getDatabase(requireContext())
                    database.standaloneFlashcardDao().delete(flashcard)
                    loadFlashcards()
                    Toast.makeText(requireContext(), "记忆卡片已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.flashcards_list_menu, menu)
        super.onCreateOptionsMenu(menu, inflater)
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_add_flashcard -> {
                val intent = Intent(requireContext(), FlashcardEditActivity::class.java)
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
                loadFlashcards()
                Toast.makeText(requireContext(), "已切换为按更新时间排序", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sort_create_time -> {
                currentSortMode = SortMode.CREATE_TIME
                currentTag = null
                loadFlashcards()
                Toast.makeText(requireContext(), "已切换为按创建时间排序", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sort_tag -> {
                showTagSortDialog()
                true
            }
            R.id.action_export -> {
                // 如果在批量模式下，导出选中的卡片；否则导出所有卡片
                if (isBatchMode && selectedFlashcards.isNotEmpty()) {
                    exportFlashcards(selectedFlashcards)
                } else {
                    exportFlashcards(null)
                }
                true
            }
            R.id.action_import -> {
                importFlashcards()
                true
            }
            R.id.action_deck_manage -> {
                val intent = Intent(requireContext(), DeckManageActivity::class.java)
                startActivity(intent)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showTagSortDialog() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(requireContext())
            val allFlashcards = database.standaloneFlashcardDao().getAllFlashcardsSync()
            val allTags = mutableSetOf<String>()
            allFlashcards.forEach { flashcard ->
                TagManager.parseTags(flashcard.tags).forEach { tag ->
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
                    loadFlashcards()
                    Toast.makeText(requireContext(), "已筛选标签: ${TagManager.formatTag(tagArray[which])}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    /**
     * 导出记忆卡片（参考 Anki 风格，包含完整信息）
     * @param selectedIds 如果指定，则只导出选中的卡片；否则导出所有卡片
     */
    private fun exportFlashcards(selectedIds: Set<String>? = null) {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(requireContext())
            
            // 获取要导出的卡片（选中的或全部的）
            val flashcards = if (selectedIds != null && selectedIds.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    database.standaloneFlashcardDao().getAllFlashcardsSync()
                        .filter { it.id in selectedIds }
                }
            } else {
                withContext(Dispatchers.IO) {
                    database.standaloneFlashcardDao().getAllFlashcardsSync()
                }
            }
            
            if (flashcards.isEmpty()) {
                Toast.makeText(requireContext(), "没有可导出的记忆卡片", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // 获取所有卡包信息（用于构建层级路径）
            val allDecks = withContext(Dispatchers.IO) {
                database.flashcardDeckDao().getAllDecksSync()
            }
            val deckMap = allDecks.associateBy { it.id }
            
            /**
             * 获取卡包的完整路径（如 "父卡包 > 子卡包 > 孙卡包"）
             */
            fun getDeckPath(deckId: String?): String? {
                if (deckId == null) return null
                val deck = deckMap[deckId] ?: return null
                val pathParts = mutableListOf<String>()
                var currentDeck: FlashcardDeck? = deck
                
                // 向上查找所有父卡包
                while (currentDeck != null) {
                    pathParts.add(0, currentDeck.name)
                    val parentId = currentDeck.parentId
                    currentDeck = if (parentId != null) deckMap[parentId] else null
                }
                
                return if (pathParts.isEmpty()) null else pathParts.joinToString(" > ")
            }
            
            // 收集所有涉及的卡包（用于导出卡包结构）
            val involvedDeckIds = flashcards.mapNotNull { it.deckId }.toSet()
            val involvedDecks = allDecks.filter { it.id in involvedDeckIds }
            
            // 构建卡包层级结构
            val decksArray = JSONArray()
            involvedDecks.forEach { deck ->
                val deckObj = JSONObject().apply {
                    put("id", deck.id)
                    put("name", deck.name)
                    put("parentId", deck.parentId ?: JSONObject.NULL)
                    put("description", deck.description)
                    put("createdAt", deck.createdAt)
                    put("updatedAt", deck.updatedAt)
                    put("sortOrder", deck.sortOrder)
                }
                decksArray.put(deckObj)
            }
            
            // 将记忆卡片转换为 JSON（包含完整信息，参考 Anki）
            val jsonArray = JSONArray()
            flashcards.forEach { flashcard ->
                val flashcardObj = JSONObject().apply {
                    put("id", flashcard.id) // 唯一标识（类似 Anki 的 GUID）
                    put("front", flashcard.front)
                    put("back", flashcard.back)
                    put("createdAt", flashcard.createdAt)
                    put("updatedAt", flashcard.updatedAt)
                    put("tags", flashcard.tags)
                    put("questionId", flashcard.questionId ?: JSONObject.NULL)
                    put("isFavorite", flashcard.isFavorite)
                    // 复习进度信息（Anki 风格）
                    put("reviewState", flashcard.reviewState)
                    put("nextReviewTime", flashcard.nextReviewTime)
                    put("interval", flashcard.interval)
                    put("easeFactor", flashcard.easeFactor)
                    put("reviewCount", flashcard.reviewCount)
                    put("consecutiveCorrect", flashcard.consecutiveCorrect)
                    // 卡包信息（包含路径字符串，便于重建层级）
                    put("deckId", flashcard.deckId ?: JSONObject.NULL)
                    put("deckPath", getDeckPath(flashcard.deckId) ?: JSONObject.NULL)
                    // 媒体文件路径（相对路径或文件名）
                    put("frontImagePath", flashcard.frontImagePath ?: JSONObject.NULL)
                    put("backImagePath", flashcard.backImagePath ?: JSONObject.NULL)
                }
                jsonArray.put(flashcardObj)
            }
            
            val exportData = JSONObject().apply {
                put("version", 2) // 版本号升级，表示包含完整信息
                put("type", "flashcards")
                put("exportTime", System.currentTimeMillis())
                put("count", flashcards.size)
                put("decks", decksArray) // 卡包结构（层级信息）
                put("flashcards", jsonArray)
            }
            
            // 保存到文件
            val success = withContext(Dispatchers.IO) {
                saveExportFile(exportData.toString(), "flashcards_export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json")
            }
            
            if (success) {
                val message = if (selectedIds != null && selectedIds.isNotEmpty()) {
                    "成功导出 ${flashcards.size} 条选中的记忆卡片到 Downloads 目录"
                } else {
                    "成功导出 ${flashcards.size} 条记忆卡片到 Downloads 目录"
                }
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(requireContext(), "导出失败，请检查存储权限", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun importFlashcards() {
        // 显示导入格式选择对话框
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("选择导入格式")
            .setItems(arrayOf("CSV/TSV (类似 Anki)", "JSON (应用格式)")) { _, which ->
                when (which) {
                    0 -> {
                        // CSV/TSV 格式
                        filePickerLauncher.launch("*/*")
                    }
                    1 -> {
                        // JSON 格式
                        filePickerLauncher.launch("application/json")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun importFlashcardsFromFile(uri: Uri) {
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
                
                // 判断文件格式：CSV/TSV 还是 JSON
                val isJson = content.trimStart().startsWith("{") || content.trimStart().startsWith("[")
                
                if (isJson) {
                    importJsonFormat(content)
                } else {
                    importCsvFormat(content, uri)
                }
                
            } catch (e: Exception) {
                // 如果不是 JSON，尝试 CSV/TSV 格式
                try {
                    val fileContent = withContext(Dispatchers.IO) {
                        requireContext().contentResolver.openInputStream(uri)?.use { inputStream ->
                            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                                reader.readText()
                            }
                        }
                    } ?: run {
                        Toast.makeText(requireContext(), "无法读取文件", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    
                    val isJson = fileContent.trimStart().startsWith("{") || fileContent.trimStart().startsWith("[")
                    if (!isJson) {
                        importCsvFormat(fileContent, uri)
                    } else {
                        android.util.Log.e("FlashcardsFragment", "导入失败", e)
                        Toast.makeText(requireContext(), "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e2: Exception) {
                    android.util.Log.e("FlashcardsFragment", "导入失败", e)
                    Toast.makeText(requireContext(), "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    /**
     * 导入 JSON 格式（应用原有格式，参考 Anki 风格）
     */
    private fun importJsonFormat(content: String) {
        // 显示导入选项对话框（参考 Anki 的导入选项）
        // 简化实现：直接使用默认选项导入（类似 Anki 的默认导入）
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("导入选项")
            .setMessage("导入时将：\n• 保留复习进度信息\n• 更新已有卡片（通过ID匹配）\n• 重建卡包层级结构")
            .setPositiveButton("导入") { _, _ ->
                // 使用默认选项进行导入
                performJsonImport(content, includeReviewProgress = true, updateExisting = true)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 获取卡包的完整路径（辅助函数）
     */
    private fun getDeckPath(deck: FlashcardDeck, allDecks: List<FlashcardDeck>): String {
        val deckMap = allDecks.associateBy { it.id }
        val pathParts = mutableListOf<String>()
        var currentDeck: FlashcardDeck? = deck
        
        while (currentDeck != null) {
            pathParts.add(0, currentDeck.name)
            val parentId = currentDeck.parentId
            currentDeck = if (parentId != null) deckMap[parentId] else null
        }
        
        return pathParts.joinToString(" > ")
    }
    
    /**
     * 执行 JSON 导入（参考 Anki 风格，支持选项）
     * @param content JSON 内容
     * @param includeReviewProgress 是否包含复习进度（类似 Anki 的 "Allow HTML in fields" 等选项）
     * @param updateExisting 是否更新已有卡片（通过 ID 判断，类似 Anki 的 GUID 机制）
     */
    private fun performJsonImport(content: String, includeReviewProgress: Boolean = true, updateExisting: Boolean = true) {
        lifecycleScope.launch {
            try {
                val jsonObj = JSONObject(content)
                val version = jsonObj.optInt("version", 1)
                val type = jsonObj.optString("type", "")
                
                if (type != "flashcards") {
                    Toast.makeText(requireContext(), "文件格式不正确", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                val database = AppDatabase.getDatabase(requireContext())
                
                // 获取所有现有卡包（用于重建层级）
                val existingDecks = withContext(Dispatchers.IO) {
                    database.flashcardDeckDao().getAllDecksSync()
                }
                val existingDeckMap = existingDecks.associateBy { it.id }
                
                // 导入卡包结构（如果导出的文件包含卡包信息）
                val deckIdMapping = mutableMapOf<String, String>() // 旧ID -> 新ID映射
                if (jsonObj.has("decks") && version >= 2) {
                    val decksArray = jsonObj.getJSONArray("decks")
                    withContext(Dispatchers.IO) {
                        // 按层级顺序导入卡包（先导入父卡包）
                        val allImportedDecks = mutableListOf<Pair<FlashcardDeck, String?>>() // (deck, parentId)
                        for (i in 0 until decksArray.length()) {
                            val deckObj = decksArray.getJSONObject(i)
                            val oldId = deckObj.getString("id")
                            val deckPath = if (deckObj.has("deckPath") && !deckObj.isNull("deckPath")) {
                                deckObj.getString("deckPath")
                            } else {
                                null
                            }
                            
                            // 检查是否已存在相同名称和层级的卡包
                            val existingDeck = if (deckPath != null) {
                                // 通过路径查找
                                existingDecks.find { existingDeck ->
                                    getDeckPath(existingDeck, existingDecks) == deckPath
                                }
                            } else {
                                existingDecks.find { it.name == deckObj.getString("name") && it.parentId == null }
                            }
                            
                            if (existingDeck != null) {
                                // 已存在，使用现有ID
                                deckIdMapping[oldId] = existingDeck.id
                            } else {
                                // 需要创建新卡包
                                val oldParentId = if (deckObj.isNull("parentId")) null else deckObj.getString("parentId")
                                val parentId = oldParentId?.let { deckIdMapping[it] } // 使用映射后的父ID
                                val newDeck = FlashcardDeck(
                                    id = UUID.randomUUID().toString(),
                                    name = deckObj.getString("name"),
                                    parentId = parentId,
                                    description = deckObj.optString("description", ""),
                                    createdAt = deckObj.optLong("createdAt", System.currentTimeMillis()),
                                    updatedAt = deckObj.optLong("updatedAt", System.currentTimeMillis()),
                                    sortOrder = deckObj.optInt("sortOrder", 0)
                                )
                                database.flashcardDeckDao().insert(newDeck)
                                deckIdMapping[oldId] = newDeck.id
                                allImportedDecks.add(Pair(newDeck, oldParentId))
                            }
                        }
                    }
                }
                
                val flashcardsArray = jsonObj.getJSONArray("flashcards")
                var successCount = 0
                var updateCount = 0
                var skipCount = 0
                
                withContext(Dispatchers.IO) {
                    for (i in 0 until flashcardsArray.length()) {
                        val flashcardObj = flashcardsArray.getJSONObject(i)
                        val flashcardId = flashcardObj.optString("id", UUID.randomUUID().toString())
                        
                        // 处理卡包ID（使用映射后的ID）
                        val oldDeckId = if (flashcardObj.isNull("deckId")) null else flashcardObj.optString("deckId")
                        val newDeckId = if (oldDeckId != null) {
                            deckIdMapping[oldDeckId] ?: run {
                                // 如果找不到映射，尝试通过路径查找
                                if (flashcardObj.has("deckPath") && !flashcardObj.isNull("deckPath")) {
                                    val deckPath = flashcardObj.getString("deckPath")
                                    existingDecks.find { getDeckPath(it, existingDecks) == deckPath }?.id
                                } else {
                                    null
                                }
                            }
                        } else {
                            null
                        }
                        
                        val flashcard = StandaloneFlashcard(
                            id = flashcardId, // 保留原ID（类似 Anki 的 GUID）
                            front = flashcardObj.getString("front"),
                            back = flashcardObj.getString("back"),
                            createdAt = flashcardObj.optLong("createdAt", System.currentTimeMillis()),
                            updatedAt = flashcardObj.optLong("updatedAt", System.currentTimeMillis()),
                            tags = flashcardObj.optString("tags", ""),
                            deckId = newDeckId,
                            questionId = if (flashcardObj.isNull("questionId")) null else flashcardObj.optString("questionId"),
                            isFavorite = flashcardObj.optBoolean("isFavorite", false),
                            // 根据选项决定是否导入复习进度
                            reviewState = if (includeReviewProgress) flashcardObj.optString("reviewState", "new") else "new",
                            nextReviewTime = if (includeReviewProgress) flashcardObj.optLong("nextReviewTime", 0L) else 0L,
                            interval = if (includeReviewProgress) flashcardObj.optLong("interval", 0L) else 0L,
                            easeFactor = if (includeReviewProgress) flashcardObj.optDouble("easeFactor", 2.5) else 2.5,
                            reviewCount = if (includeReviewProgress) flashcardObj.optInt("reviewCount", 0) else 0,
                            consecutiveCorrect = if (includeReviewProgress) flashcardObj.optInt("consecutiveCorrect", 0) else 0,
                            // 媒体文件路径（需要根据实际情况处理）
                            frontImagePath = if (flashcardObj.isNull("frontImagePath")) null else flashcardObj.optString("frontImagePath"),
                            backImagePath = if (flashcardObj.isNull("backImagePath")) null else flashcardObj.optString("backImagePath")
                        )
                        
                        // 检查是否已存在（通过ID判断，类似 Anki 的 GUID 机制）
                        val existing = database.standaloneFlashcardDao().getFlashcardById(flashcard.id)
                        if (existing == null) {
                            // 不存在，插入新卡片
                            database.standaloneFlashcardDao().insert(flashcard)
                            successCount++
                        } else if (updateExisting) {
                            // 已存在且允许更新，更新卡片
                            database.standaloneFlashcardDao().update(flashcard)
                            updateCount++
                        } else {
                            // 已存在但不更新，跳过
                            skipCount++
                        }
                    }
                }
                
                val message = buildString {
                    if (successCount > 0) append("新增 $successCount 条")
                    if (updateCount > 0) {
                        if (isNotEmpty()) append("，")
                        append("更新 $updateCount 条")
                    }
                    if (skipCount > 0) {
                        if (isNotEmpty()) append("，")
                        append("跳过 $skipCount 条重复")
                    }
                }
                Toast.makeText(requireContext(), "导入完成：$message", Toast.LENGTH_LONG).show()
                
                // 刷新列表
                loadFlashcards()
                
            } catch (e: Exception) {
                android.util.Log.e("FlashcardsFragment", "导入JSON失败", e)
                Toast.makeText(requireContext(), "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 导入 CSV/TSV 格式（类似 Anki）
     * 支持格式：
     * - CSV: 字段用逗号分隔，如：front,back 或 front,back,tags
     * - TSV: 字段用制表符分隔，如：front\tback 或 front\tback\ttags
     */
    private fun importCsvFormat(content: String, uri: Uri) {
        lifecycleScope.launch {
            try {
                // 检测分隔符（CSV 用逗号，TSV 用制表符）
                val delimiter = if (content.contains("\t")) "\t" else ","
                
                // 显示格式选择对话框（让用户选择列的含义）
                val lines = content.trim().split("\n")
                if (lines.isEmpty()) {
                    Toast.makeText(requireContext(), "文件为空", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 取第一行作为示例
                val firstLine = lines[0].trim()
                val columns = firstLine.split(delimiter)
                
                // 简化：直接使用默认映射导入（第一列=正面，第二列=背面，第三列=标签，第四列=卡包）
                val selectedMapping = mutableListOf<Int>()
                selectedMapping.add(0) // 第一列默认是正面
                if (columns.size > 1) {
                    selectedMapping.add(1) // 第二列默认是背面
                }
                if (columns.size > 2) {
                    selectedMapping.add(2) // 第三列默认是标签
                }
                if (columns.size > 3) {
                    selectedMapping.add(3) // 第四列默认是卡包
                }
                while (selectedMapping.size < columns.size) {
                    selectedMapping.add(4) // 其他列默认跳过
                }
                
                importCsvWithMapping(lines, delimiter, selectedMapping)
                
            } catch (e: Exception) {
                android.util.Log.e("FlashcardsFragment", "导入CSV失败", e)
                Toast.makeText(requireContext(), "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 使用列映射导入 CSV/TSV
     */
    private fun importCsvWithMapping(lines: List<String>, delimiter: String, mapping: List<Int>) {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(requireContext())
            var successCount = 0
            var errorCount = 0
            
            withContext(Dispatchers.IO) {
                for (i in lines.indices) {
                    try {
                        val line = lines[i].trim()
                        if (line.isEmpty()) continue
                        
                        val columns = line.split(delimiter)
                        if (columns.size < 2) {
                            errorCount++
                            continue
                        }
                        
                        // 根据映射提取字段
                        var front = ""
                        var back = ""
                        var tags = ""
                        var deckName: String? = null
                        
                        for (colIndex in columns.indices) {
                            if (colIndex >= mapping.size) break
                            val mappingType = mapping[colIndex]
                            val value = columns[colIndex].trim().trim('"') // 移除可能的引号
                            
                            when (mappingType) {
                                0 -> front = value // 正面
                                1 -> back = value  // 背面
                                2 -> tags = value  // 标签
                                3 -> deckName = value // 卡包
                            }
                        }
                        
                        if (front.isEmpty() || back.isEmpty()) {
                            errorCount++
                            continue
                        }
                        
                        // 查找卡包ID
                        var deckId: String? = null
                        if (deckName != null && deckName.isNotEmpty()) {
                            val decks = database.flashcardDeckDao().getAllDecksSync()
                            val deck = decks.find { it.name == deckName }
                            deckId = deck?.id
                        }
                        
                        // 格式化标签
                        val tagsJson = if (tags.isNotEmpty()) {
                            val tagList = tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                            TagManager.formatTagsToJson(tagList)
                        } else {
                            ""
                        }
                        
                        val flashcard = StandaloneFlashcard(
                            id = UUID.randomUUID().toString(),
                            front = front,
                            back = back,
                            tags = tagsJson,
                            deckId = deckId,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            reviewState = "new"
                        )
                        
                        database.standaloneFlashcardDao().insert(flashcard)
                        successCount++
                        
                    } catch (e: Exception) {
                        android.util.Log.e("FlashcardsFragment", "导入第 ${i + 1} 行失败", e)
                        errorCount++
                    }
                }
            }
            
            val message = when {
                errorCount > 0 -> "成功导入 $successCount 条记忆卡片，$errorCount 条失败"
                else -> "成功导入 $successCount 条记忆卡片"
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                loadFlashcards()
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
            android.util.Log.e("FlashcardsFragment", "保存文件失败", e)
            false
        }
    }
    
    private fun showFlashcardContentDialog(flashcard: StandaloneFlashcard) {
        AlertDialog.Builder(requireContext())
            .setTitle("记忆卡片")
            .setMessage("提示：${flashcard.front}\n\n内容：${flashcard.back}")
            .setPositiveButton("编辑") { _, _ ->
                val intent = Intent(requireContext(), FlashcardEditActivity::class.java)
                intent.putExtra("flashcard_id", flashcard.id)
                startActivity(intent)
            }
            .setNeutralButton("删除") { _, _ ->
                deleteFlashcard(flashcard)
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun groupFlashcardsByDate(flashcards: List<StandaloneFlashcard>): List<StandaloneFlashcardsAdapter.ListItem> {
        val listItems = mutableListOf<StandaloneFlashcardsAdapter.ListItem>()
        val calendar = java.util.Calendar.getInstance()
        val today = java.util.Calendar.getInstance()
        val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val displayDateFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
        
        var currentDateKey: String? = null
        
        flashcards.forEach { flashcard ->
            calendar.timeInMillis = flashcard.createdAt
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
                
                listItems.add(StandaloneFlashcardsAdapter.ListItem.DateHeader(dateText, dateKey))
            }
            
            listItems.add(StandaloneFlashcardsAdapter.ListItem.FlashcardItem(flashcard))
        }
        
        return listItems
    }
}

