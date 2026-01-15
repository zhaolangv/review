package com.gongkao.cuotifupan.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.StandaloneNote
import com.gongkao.cuotifupan.ui.TagManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.*

/**
 * 笔记列表页面
 */
class NotesListActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: StandaloneNotesAdapter
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
        setContentView(R.layout.activity_notes_list)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "笔记管理"
        
        initViews()
        loadNotes()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        searchEditText = findViewById(R.id.searchEditText)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = StandaloneNotesAdapter(
            onItemClick = { note ->
                // 点击笔记，跳转到编辑页面
                val intent = Intent(this, NoteEditActivity::class.java)
                intent.putExtra("note_id", note.id)
                startActivity(intent)
            },
            onDelete = { note ->
                deleteNote(note)
            },
            onTagClick = { tag ->
                // 点击标签，按标签筛选
                currentTag = tag
                loadNotes()
            },
            onQuestionClick = { note ->
                // 点击跳转到题目
                if (note.questionId != null) {
                    val intent = Intent(this, QuestionDetailCardActivity::class.java)
                    intent.putExtra("question_id", note.questionId)
                    startActivity(intent)
                }
            },
        )
        recyclerView.adapter = adapter
        
        // 搜索框监听
        searchEditText.setOnEditorActionListener { _, _, _ ->
            searchKeyword = searchEditText.text.toString().trim()
            loadNotes()
            true
        }
    }
    
    private fun loadNotes() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@NotesListActivity)
            val notes = when {
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
            
            notes.observe(this@NotesListActivity) { noteList ->
                // 如果是按标签排序，需要手动排序
                val sortedNotes = if (currentSortMode == SortMode.TAG && currentTag == null) {
                    noteList.sortedBy { TagManager.getFirstTag(it.tags) }
                } else {
                    noteList
                }
                
                // 按日期分组
                val listItems = groupNotesByDate(sortedNotes)
                
                adapter.submitList(listItems)
                emptyView.visibility = if (listItems.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
    
    private fun deleteNote(note: StandaloneNote) {
        AlertDialog.Builder(this)
            .setTitle("删除笔记")
            .setMessage("确定要删除这条笔记吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val database = AppDatabase.getDatabase(this@NotesListActivity)
                    database.standaloneNoteDao().delete(note)
                    loadNotes()
                    Toast.makeText(this@NotesListActivity, "笔记已删除", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.notes_list_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_add_note -> {
                // 添加新笔记
                val intent = Intent(this, NoteEditActivity::class.java)
                startActivity(intent)
                true
            }
            R.id.action_sort_time -> {
                currentSortMode = SortMode.UPDATE_TIME
                currentTag = null
                loadNotes()
                Toast.makeText(this, "已切换为按更新时间排序", Toast.LENGTH_SHORT).show()
                true
            }
            R.id.action_sort_create_time -> {
                currentSortMode = SortMode.CREATE_TIME
                currentTag = null
                loadNotes()
                Toast.makeText(this, "已切换为按创建时间排序", Toast.LENGTH_SHORT).show()
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
            val database = AppDatabase.getDatabase(this@NotesListActivity)
            val allNotes = database.standaloneNoteDao().getAllNotesSync()
            val allTags = mutableSetOf<String>()
            allNotes.forEach { note ->
                TagManager.parseTags(note.tags).forEach { tag ->
                    allTags.add(tag)
                }
            }
            
            if (allTags.isEmpty()) {
                Toast.makeText(this@NotesListActivity, "没有可用的标签", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            val tagArray = allTags.sorted().toTypedArray()
            AlertDialog.Builder(this@NotesListActivity)
                .setTitle("选择标签")
                .setItems(tagArray) { _, which ->
                    currentTag = tagArray[which]
                    currentSortMode = SortMode.TAG
                    loadNotes()
                    Toast.makeText(this@NotesListActivity, "已筛选标签: ${TagManager.formatTag(tagArray[which])}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
    
    private fun exportNotes() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@NotesListActivity)
            val notes = database.standaloneNoteDao().getAllNotesSync()
            
            if (notes.isEmpty()) {
                Toast.makeText(this@NotesListActivity, "没有可导出的笔记", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // TODO: 实现导出功能
            Toast.makeText(this@NotesListActivity, "导出功能开发中...", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun importNotes() {
        // TODO: 实现导入功能
        Toast.makeText(this, "导入功能开发中...", Toast.LENGTH_SHORT).show()
    }
    
    override fun onResume() {
        super.onResume()
        // 从编辑页面返回时刷新列表
        loadNotes()
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
    
    private fun showNoteContentDialog(note: StandaloneNote) {
        AlertDialog.Builder(this)
            .setTitle("笔记内容")
            .setMessage(note.content)
            .setPositiveButton("编辑") { _, _ ->
                val intent = Intent(this, NoteEditActivity::class.java)
                intent.putExtra("note_id", note.id)
                startActivity(intent)
            }
            .setNeutralButton("删除") { _, _ ->
                deleteNote(note)
            }
            .setNegativeButton("关闭", null)
            .show()
    }
}

