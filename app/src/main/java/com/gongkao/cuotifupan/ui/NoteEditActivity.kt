package com.gongkao.cuotifupan.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.StandaloneNote
import com.gongkao.cuotifupan.ui.TagManager
import com.gongkao.cuotifupan.data.Question
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * 笔记编辑页面
 */
class NoteEditActivity : AppCompatActivity() {
    
    private lateinit var contentEditText: EditText
    private lateinit var tagsEditText: EditText
    
    private var noteId: String? = null
    private var isNewNote = true
    private var hasUnsavedChanges = false
    private var originalContent = ""
    private var originalTags = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_edit)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "编辑笔记"
        
        contentEditText = findViewById(R.id.contentEditText)
        tagsEditText = findViewById(R.id.tagsEditText)
        
        // 监听内容变化
        contentEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkForChanges()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        tagsEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkForChanges()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        // 标签输入框点击打开标签选择对话框
        tagsEditText.setOnClickListener {
            val currentTags = TagManager.parseTags(
                if (tagsEditText.text.toString().isNotBlank()) {
                    TagManager.formatTagsToJson(tagsEditText.text.toString().split(", ").map { it.trim() })
                } else {
                    ""
                }
            )
            TagEditDialog.show(this, currentTags) { newTags ->
                val tagsText = if (newTags.isEmpty()) "" else newTags.joinToString(", ")
                tagsEditText.setText(tagsText)
            }
        }
        
        noteId = intent.getStringExtra("note_id")
        if (noteId != null) {
            isNewNote = false
            loadNote()
        } else {
            supportActionBar?.title = "新建笔记"
        }
    }
    
    private fun loadNote() {
        lifecycleScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@NoteEditActivity)
            var note = database.standaloneNoteDao().getNoteById(noteId!!)
            
            // 如果数据库中找不到，可能是从题目中解析出来的笔记，需要从题目中查找
            if (note == null) {
                val allQuestions = database.questionDao().getAllQuestionsSync()
                for (question in allQuestions) {
                    if (question.userNotes.isNotBlank()) {
                        try {
                            val notesArray = org.json.JSONArray(question.userNotes)
                            for (i in 0 until notesArray.length()) {
                                val noteObj = notesArray.getJSONObject(i)
                                if (noteObj.optString("id") == noteId && noteObj.optString("type") == "note") {
                                    // 找到了对应的笔记
                                    note = StandaloneNote(
                                        id = noteId!!,
                                        content = noteObj.optString("content", ""),
                                        createdAt = noteObj.optLong("timestamp", System.currentTimeMillis()),
                                        updatedAt = noteObj.optLong("timestamp", System.currentTimeMillis()),
                                        questionId = question.id,
                                        tags = question.tags, // 继承题目的标签
                                        isFavorite = false
                                    )
                                    break
                                }
                            }
                            if (note != null) break
                        } catch (e: Exception) {
                            // 忽略解析错误
                        }
                    }
                }
            }
            
            if (note != null) {
                withContext(Dispatchers.Main) {
                    originalContent = note.content
                    val tags = TagManager.parseTags(note.tags)
                    originalTags = if (tags.isEmpty()) "" else tags.joinToString(", ")
                    contentEditText.setText(originalContent)
                    tagsEditText.setText(originalTags)
                    hasUnsavedChanges = false
                }
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.note_edit_menu, menu)
        return true
    }
    
    private fun checkForChanges() {
        val currentContent = contentEditText.text.toString().trim()
        val currentTags = tagsEditText.text.toString().trim()
        hasUnsavedChanges = (currentContent != originalContent) || (currentTags != originalTags)
    }
    
    override fun onBackPressed() {
        if (hasUnsavedChanges) {
            showSaveDialog()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                if (hasUnsavedChanges) {
                    showSaveDialog()
                } else {
                    finish()
                }
                true
            }
            R.id.action_save -> {
                saveNote()
                true
            }
            R.id.action_delete -> {
                if (!isNewNote) {
                    deleteNote()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun saveNote() {
        val content = contentEditText.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入笔记内容", Toast.LENGTH_SHORT).show()
            return
        }
        
        val tagsText = tagsEditText.text.toString().trim()
        val tags = if (tagsText.isNotBlank()) {
            tagsText.split(", ").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        val tagsJson = TagManager.formatTagsToJson(tags)
        
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@NoteEditActivity)
            val note = if (isNewNote) {
                StandaloneNote(
                    content = content,
                    tags = tagsJson,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                val existingNote = database.standaloneNoteDao().getNoteById(noteId!!)
                existingNote?.copy(
                    content = content,
                    tags = tagsJson,
                    updatedAt = System.currentTimeMillis()
                ) ?: return@launch
            }
            
            if (isNewNote) {
                database.standaloneNoteDao().insert(note)
            } else {
                // 如果笔记来自题目，需要同时更新题目中的笔记
                val existingNote = database.standaloneNoteDao().getNoteById(noteId!!)
                if (existingNote?.questionId != null) {
                    // 更新题目中的笔记
                    val question = database.questionDao().getQuestionById(existingNote.questionId!!)
                    if (question != null && question.userNotes.isNotBlank()) {
                        try {
                            val notesArray = org.json.JSONArray(question.userNotes)
                            val updatedNotesArray = org.json.JSONArray()
                            for (i in 0 until notesArray.length()) {
                                val noteObj = notesArray.getJSONObject(i)
                                if (noteObj.optString("id") == noteId && noteObj.optString("type") == "note") {
                                    // 更新这条笔记
                                    val updatedNoteObj = org.json.JSONObject()
                                    updatedNoteObj.put("id", noteId)
                                    updatedNoteObj.put("type", "note")
                                    updatedNoteObj.put("content", content)
                                    updatedNoteObj.put("timestamp", System.currentTimeMillis())
                                    updatedNotesArray.put(updatedNoteObj)
                                } else {
                                    updatedNotesArray.put(noteObj)
                                }
                            }
                            val updatedQuestion = question.copy(userNotes = updatedNotesArray.toString())
                            database.questionDao().update(updatedQuestion)
                        } catch (e: Exception) {
                            // 忽略解析错误
                        }
                    }
                }
                database.standaloneNoteDao().update(note)
            }
            
            hasUnsavedChanges = false
            originalContent = content
            originalTags = tagsText
            Toast.makeText(this@NoteEditActivity, "笔记已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun showSaveDialog() {
        AlertDialog.Builder(this)
            .setTitle("保存笔记")
            .setMessage("您有未保存的更改，是否保存？")
            .setPositiveButton("保存") { _, _ ->
                saveNote()
            }
            .setNegativeButton("不保存") { _, _ ->
                finish()
            }
            .setNeutralButton("取消", null)
            .show()
    }
    
    private fun deleteNote() {
        AlertDialog.Builder(this)
            .setTitle("删除笔记")
            .setMessage("确定要删除这条笔记吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val database = AppDatabase.getDatabase(this@NoteEditActivity)
                    val note = database.standaloneNoteDao().getNoteById(noteId!!)
                    if (note != null) {
                        database.standaloneNoteDao().delete(note)
                        Toast.makeText(this@NoteEditActivity, "笔记已删除", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

