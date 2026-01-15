package com.gongkao.cuotifupan.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.StandaloneFlashcard
import com.gongkao.cuotifupan.ui.TagManager
import kotlinx.coroutines.launch

/**
 * 记忆卡片列表页面
 */
class FlashcardsListActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: StandaloneFlashcardsAdapter
    private lateinit var searchEditText: EditText
    
    private var currentSortMode: SortMode = SortMode.UPDATE_TIME
    private var currentTag: String? = null
    private var searchKeyword: String = ""
    
    enum class SortMode {
        UPDATE_TIME,  // 按更新时间
        CREATE_TIME,  // 按创建时间
        TAG           // 按标签
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flashcards_list)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "记忆卡片管理"
        
        initViews()
        loadFlashcards()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        searchEditText = findViewById(R.id.searchEditText)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = StandaloneFlashcardsAdapter(
            onItemClick = { flashcard ->
                // 点击卡片，跳转到编辑页面
                val intent = Intent(this, FlashcardEditActivity::class.java)
                intent.putExtra("flashcard_id", flashcard.id)
                startActivity(intent)
            },
            onDelete = { flashcard ->
                deleteFlashcard(flashcard)
            },
            onTagClick = { tag ->
                // 点击标签，按标签筛选
                currentTag = tag
                loadFlashcards()
            },
            onQuestionClick = { flashcard ->
                // 点击跳转到题目
                if (flashcard.questionId != null) {
                    val intent = Intent(this, QuestionDetailCardActivity::class.java)
                    intent.putExtra("question_id", flashcard.questionId)
                    startActivity(intent)
                }
            },
        )
        recyclerView.adapter = adapter
        
        // 搜索框监听
        searchEditText.setOnEditorActionListener { _, _, _ ->
            searchKeyword = searchEditText.text.toString().trim()
            loadFlashcards()
            true
        }
    }
    
    private fun loadFlashcards() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@FlashcardsListActivity)
            val flashcards = when {
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
            
            flashcards.observe(this@FlashcardsListActivity) { flashcardList ->
                // 如果是按标签排序，需要手动排序
                val sortedFlashcards = if (currentSortMode == SortMode.TAG && currentTag == null) {
                    flashcardList.sortedBy { TagManager.getFirstTag(it.tags) }
                } else {
                    flashcardList
                }
                
                // 按日期分组
                val listItems = groupFlashcardsByDate(sortedFlashcards)
                
                adapter.submitList(listItems)
                emptyView.visibility = if (listItems.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun deleteFlashcard(flashcard: StandaloneFlashcard) {
        AlertDialog.Builder(this)
            .setTitle("删除记忆卡片")
            .setMessage("确定要删除这张记忆卡片吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val database = AppDatabase.getDatabase(this@FlashcardsListActivity)
                    database.standaloneFlashcardDao().delete(flashcard)
                    loadFlashcards()
                    Toast.makeText(this@FlashcardsListActivity, "记忆卡片已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.flashcards_list_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_add_flashcard -> {
                // 添加新卡片
                val intent = Intent(this, FlashcardEditActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_sort_time -> {
                currentSortMode = SortMode.UPDATE_TIME
                currentTag = null
                loadFlashcards()
                Toast.makeText(this, "已切换为按更新时间排序", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sort_create_time -> {
                currentSortMode = SortMode.CREATE_TIME
                currentTag = null
                loadFlashcards()
                Toast.makeText(this, "已切换为按创建时间排序", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sort_tag -> {
                showTagSortDialog()
                true
            }
            R.id.action_export -> {
                exportFlashcards()
                true
            }
            R.id.action_import -> {
                importFlashcards()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showTagSortDialog() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@FlashcardsListActivity)
            val allFlashcards = database.standaloneFlashcardDao().getAllFlashcardsSync()
            val allTags = mutableSetOf<String>()
            allFlashcards.forEach { flashcard ->
                TagManager.parseTags(flashcard.tags).forEach { tag ->
                    allTags.add(tag)
                }
            }
            
            if (allTags.isEmpty()) {
                Toast.makeText(this@FlashcardsListActivity, "没有可用的标签", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val tagArray = allTags.sorted().toTypedArray()
            AlertDialog.Builder(this@FlashcardsListActivity)
                .setTitle("选择标签")
                .setItems(tagArray) { _, which ->
                    currentTag = tagArray[which]
                    currentSortMode = SortMode.TAG
                    loadFlashcards()
                    Toast.makeText(this@FlashcardsListActivity, "已筛选标签: ${TagManager.formatTag(tagArray[which])}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    private fun exportFlashcards() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@FlashcardsListActivity)
            val flashcards = database.standaloneFlashcardDao().getAllFlashcardsSync()
            
            if (flashcards.isEmpty()) {
                Toast.makeText(this@FlashcardsListActivity, "没有可导出的记忆卡片", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // TODO: 实现导出功能
            Toast.makeText(this@FlashcardsListActivity, "导出功能开发中...", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun importFlashcards() {
        // TODO: 实现导入功能
        Toast.makeText(this, "导入功能开发中...", Toast.LENGTH_SHORT).show()
    }
    
    override fun onResume() {
        super.onResume()
        // 从编辑页面返回时刷新列表
        loadFlashcards()
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
    
    private fun showFlashcardContentDialog(flashcard: StandaloneFlashcard) {
        AlertDialog.Builder(this)
            .setTitle("记忆卡片")
            .setMessage("提示：${flashcard.front}\n\n内容：${flashcard.back}")
            .setPositiveButton("编辑") { _, _ ->
                val intent = Intent(this, FlashcardEditActivity::class.java)
                intent.putExtra("flashcard_id", flashcard.id)
                startActivity(intent)
            }
            .setNeutralButton("删除") { _, _ ->
                deleteFlashcard(flashcard)
            }
            .setNegativeButton("关闭", null)
            .show()
    }
}

