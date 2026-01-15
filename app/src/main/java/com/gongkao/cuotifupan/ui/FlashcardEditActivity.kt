package com.gongkao.cuotifupan.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.FlashcardDeck
import com.gongkao.cuotifupan.data.StandaloneFlashcard
import com.gongkao.cuotifupan.ui.TagManager
import org.json.JSONArray
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 记忆卡片编辑页面
 */
class FlashcardEditActivity : AppCompatActivity() {
    
    private lateinit var frontEditText: EditText
    private lateinit var backEditText: EditText
    private lateinit var tagsEditText: EditText
    private lateinit var deckEditText: EditText
    private lateinit var frontImageView: ImageView
    private lateinit var backImageView: ImageView
    private lateinit var frontImageButton: Button
    private lateinit var backImageButton: Button
    private lateinit var frontImageDeleteButton: Button
    private lateinit var backImageDeleteButton: Button
    
    private var flashcardId: String? = null
    private var isNewFlashcard = true
    private var hasUnsavedChanges = false
    private var originalFront = ""
    private var originalBack = ""
    private var originalTags = ""
    private var originalDeckId: String? = null
    private var currentDeckId: String? = null
    private var frontImagePath: String? = null
    private var backImagePath: String? = null
    private var originalFrontImagePath: String? = null
    private var originalBackImagePath: String? = null
    
    // 图片选择器
    private var isSelectingFrontImage = false
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImageSelected(it) }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flashcard_edit)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "编辑记忆卡片"
        
        frontEditText = findViewById(R.id.frontEditText)
        backEditText = findViewById(R.id.backEditText)
        tagsEditText = findViewById(R.id.tagsEditText)
        deckEditText = findViewById(R.id.deckEditText)
        frontImageView = findViewById(R.id.frontImageView)
        backImageView = findViewById(R.id.backImageView)
        frontImageButton = findViewById(R.id.frontImageButton)
        backImageButton = findViewById(R.id.backImageButton)
        frontImageDeleteButton = findViewById(R.id.frontImageDeleteButton)
        backImageDeleteButton = findViewById(R.id.backImageDeleteButton)
        
        // 监听内容变化
        frontEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkForChanges()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        
        backEditText.addTextChangedListener(object : android.text.TextWatcher {
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
        
        // 卡包输入框点击打开卡包选择对话框
        deckEditText.setOnClickListener {
            showDeckSelectDialog()
        }
        
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
        
        // 图片按钮点击事件
        frontImageButton.setOnClickListener {
            isSelectingFrontImage = true
            imagePickerLauncher.launch("image/*")
        }
        
        backImageButton.setOnClickListener {
            isSelectingFrontImage = false
            imagePickerLauncher.launch("image/*")
        }
        
        // 删除图片按钮
        frontImageDeleteButton.setOnClickListener {
            frontImagePath = null
            frontImageView.visibility = ImageView.GONE
            frontImageDeleteButton.visibility = Button.GONE
            frontImageView.setImageBitmap(null)
            checkForChanges()
        }
        
        backImageDeleteButton.setOnClickListener {
            backImagePath = null
            backImageView.visibility = ImageView.GONE
            backImageDeleteButton.visibility = Button.GONE
            backImageView.setImageBitmap(null)
            checkForChanges()
        }
        
        flashcardId = intent.getStringExtra("flashcard_id")
        if (flashcardId != null) {
            isNewFlashcard = false
            loadFlashcard()
        } else {
            supportActionBar?.title = "新建记忆卡片"
        }
    }
    
    private fun loadFlashcard() {
        lifecycleScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@FlashcardEditActivity)
            var flashcard = database.standaloneFlashcardDao().getFlashcardById(flashcardId!!)
            
            // 如果数据库中找不到，可能是从题目中解析出来的记忆卡片，需要从题目中查找
            if (flashcard == null) {
                val allQuestions = database.questionDao().getAllQuestionsSync()
                for (question in allQuestions) {
                    if (question.userNotes.isNotBlank()) {
                        try {
                            val notesArray = org.json.JSONArray(question.userNotes)
                            for (i in 0 until notesArray.length()) {
                                val noteObj = notesArray.getJSONObject(i)
                                if (noteObj.optString("id") == flashcardId && noteObj.optString("type") == "flashcard") {
                                    // 找到了对应的记忆卡片
                                    flashcard = StandaloneFlashcard(
                                        id = flashcardId!!,
                                        front = noteObj.optString("front", ""),
                                        back = noteObj.optString("back", ""),
                                        createdAt = noteObj.optLong("timestamp", System.currentTimeMillis()),
                                        updatedAt = noteObj.optLong("timestamp", System.currentTimeMillis()),
                                        questionId = question.id,
                                        tags = question.tags, // 继承题目的标签
                                        isFavorite = false
                                    )
                                    break
                                }
                            }
                            if (flashcard != null) break
                        } catch (e: Exception) {
                            // 忽略解析错误
                        }
                    }
                }
            }
            
            if (flashcard != null) {
                // 加载卡包信息
                val deckId = flashcard.deckId
                val deckName = if (deckId != null) {
                    val deck = database.flashcardDeckDao().getDeckById(deckId)
                    deck?.name ?: "未分类"
                } else {
                    "未分类"
                }
                
                withContext(Dispatchers.Main) {
                    originalFront = flashcard.front
                    originalBack = flashcard.back
                    originalDeckId = flashcard.deckId
                    currentDeckId = flashcard.deckId
                    originalFrontImagePath = flashcard.frontImagePath
                    originalBackImagePath = flashcard.backImagePath
                    frontImagePath = flashcard.frontImagePath
                    backImagePath = flashcard.backImagePath
                    val tags = TagManager.parseTags(flashcard.tags)
                    originalTags = if (tags.isEmpty()) "" else tags.joinToString(", ")
                    frontEditText.setText(originalFront)
                    backEditText.setText(originalBack)
                    tagsEditText.setText(originalTags)
                    deckEditText.setText(deckName)
                    
                    // 加载图片
                    loadImage(frontImagePath, frontImageView, frontImageDeleteButton)
                    loadImage(backImagePath, backImageView, backImageDeleteButton)
                    
                    hasUnsavedChanges = false
                }
            }
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.flashcard_edit_menu, menu)
        return true
    }
    
    private fun checkForChanges() {
        val currentFront = frontEditText.text.toString().trim()
        val currentBack = backEditText.text.toString().trim()
        val currentTags = tagsEditText.text.toString().trim()
        hasUnsavedChanges = (currentFront != originalFront) || 
                           (currentBack != originalBack) || 
                           (currentTags != originalTags) ||
                           (currentDeckId != originalDeckId) ||
                           (frontImagePath != originalFrontImagePath) ||
                           (backImagePath != originalBackImagePath)
    }
    
    /**
     * 处理图片选择
     */
    private fun handleImageSelected(uri: Uri) {
        lifecycleScope.launch {
            try {
                val imagePath = saveImageFromUri(uri)
                if (imagePath != null) {
                    if (isSelectingFrontImage) {
                        frontImagePath = imagePath
                        loadImage(imagePath, frontImageView, frontImageDeleteButton)
                    } else {
                        backImagePath = imagePath
                        loadImage(imagePath, backImageView, backImageDeleteButton)
                    }
                    checkForChanges()
                } else {
                    Toast.makeText(this@FlashcardEditActivity, "保存图片失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("FlashcardEditActivity", "处理图片失败", e)
                Toast.makeText(this@FlashcardEditActivity, "处理图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 从 URI 保存图片到应用内部目录
     */
    private suspend fun saveImageFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap == null) return@withContext null
            
            // 创建 flashcards 目录
            val flashcardsDir = File(filesDir, "flashcards")
            if (!flashcardsDir.exists()) {
                flashcardsDir.mkdirs()
            }
            
            // 生成唯一文件名
            val fileName = "flashcard_${System.currentTimeMillis()}_${if (isSelectingFrontImage) "front" else "back"}.jpg"
            val file = File(flashcardsDir, fileName)
            
            // 保存图片
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            bitmap.recycle()
            file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("FlashcardEditActivity", "保存图片失败", e)
            null
        }
    }
    
    /**
     * 加载并显示图片
     */
    private fun loadImage(imagePath: String?, imageView: ImageView, deleteButton: Button) {
        if (imagePath != null && File(imagePath).exists()) {
            lifecycleScope.launch(Dispatchers.IO) {
                val bitmap = BitmapFactory.decodeFile(imagePath)
                withContext(Dispatchers.Main) {
                    imageView.setImageBitmap(bitmap)
                    imageView.visibility = ImageView.VISIBLE
                    deleteButton.visibility = Button.VISIBLE
                }
            }
        } else {
            imageView.visibility = ImageView.GONE
            deleteButton.visibility = Button.GONE
        }
    }
    
    private fun showDeckSelectDialog() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@FlashcardEditActivity)
            val decks = withContext(Dispatchers.IO) {
                database.flashcardDeckDao().getRootDecksSync()
            }
            
            val deckNames = listOf("未分类") + decks.map { it.name }
            val deckIds = listOf<String?>(null) + decks.map { it.id }
            
            val currentIndex = if (currentDeckId == null) {
                0
            } else {
                deckIds.indexOf(currentDeckId) + 1
            }.coerceAtLeast(0)
            
            withContext(Dispatchers.Main) {
                androidx.appcompat.app.AlertDialog.Builder(this@FlashcardEditActivity)
                    .setTitle("选择卡包")
                    .setSingleChoiceItems(deckNames.toTypedArray(), currentIndex.coerceAtMost(deckNames.size - 1)) { dialog, which ->
                        if (which == 0) {
                            // 未分类
                            currentDeckId = null
                            deckEditText.setText("未分类")
                        } else {
                            val selectedDeck = decks[which - 1]
                            currentDeckId = selectedDeck.id
                            deckEditText.setText(selectedDeck.name)
                        }
                        checkForChanges()
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
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
                saveFlashcard()
                true
            }
            R.id.action_delete -> {
                if (!isNewFlashcard) {
                    deleteFlashcard()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun saveFlashcard() {
        val front = frontEditText.text.toString().trim()
        val back = backEditText.text.toString().trim()
        
        if (front.isEmpty() || back.isEmpty()) {
            Toast.makeText(this, "请输入提示和内容", Toast.LENGTH_SHORT).show()
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
            val database = AppDatabase.getDatabase(this@FlashcardEditActivity)
            val flashcard = if (isNewFlashcard) {
                StandaloneFlashcard(
                    front = front,
                    back = back,
                    tags = tagsJson,
                    deckId = currentDeckId,
                    frontImagePath = frontImagePath,
                    backImagePath = backImagePath,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            } else {
                val existingFlashcard = database.standaloneFlashcardDao().getFlashcardById(flashcardId!!)
                existingFlashcard?.copy(
                    front = front,
                    back = back,
                    tags = tagsJson,
                    deckId = currentDeckId,
                    frontImagePath = frontImagePath,
                    backImagePath = backImagePath,
                    updatedAt = System.currentTimeMillis()
                ) ?: return@launch
            }
            
            // 删除旧的图片文件（如果有变化）
            if (!isNewFlashcard) {
                val oldFlashcard = database.standaloneFlashcardDao().getFlashcardById(flashcardId!!)
                oldFlashcard?.let { old ->
                    if (old.frontImagePath != frontImagePath && old.frontImagePath != null) {
                        File(old.frontImagePath).delete()
                    }
                    if (old.backImagePath != backImagePath && old.backImagePath != null) {
                        File(old.backImagePath).delete()
                    }
                }
            }
            
            if (isNewFlashcard) {
                database.standaloneFlashcardDao().insert(flashcard)
            } else {
                // 如果记忆卡片来自题目，需要同时更新题目中的记忆卡片
                val existingFlashcard = database.standaloneFlashcardDao().getFlashcardById(flashcardId!!)
                if (existingFlashcard?.questionId != null) {
                    // 更新题目中的记忆卡片
                    val question = database.questionDao().getQuestionById(existingFlashcard.questionId!!)
                    if (question != null && question.userNotes.isNotBlank()) {
                        try {
                            val notesArray = JSONArray(question.userNotes)
                            val updatedNotesArray = JSONArray()
                            for (i in 0 until notesArray.length()) {
                                val noteObj = notesArray.getJSONObject(i)
                                if (noteObj.optString("id") == flashcardId && noteObj.optString("type") == "flashcard") {
                                    // 更新这条记忆卡片
                                    val updatedNoteObj = org.json.JSONObject()
                                    updatedNoteObj.put("id", flashcardId)
                                    updatedNoteObj.put("type", "flashcard")
                                    updatedNoteObj.put("front", front)
                                    updatedNoteObj.put("back", back)
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
                database.standaloneFlashcardDao().update(flashcard)
            }
            
            hasUnsavedChanges = false
            originalFront = front
            originalBack = back
            originalTags = tagsText
            originalDeckId = currentDeckId
            Toast.makeText(this@FlashcardEditActivity, "记忆卡片已保存", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun showSaveDialog() {
        AlertDialog.Builder(this)
            .setTitle("保存记忆卡片")
            .setMessage("您有未保存的更改，是否保存？")
            .setPositiveButton("保存") { _, _ ->
                saveFlashcard()
            }
            .setNegativeButton("不保存") { _, _ ->
                finish()
            }
            .setNeutralButton("取消", null)
            .show()
    }
    
    private fun deleteFlashcard() {
        AlertDialog.Builder(this)
            .setTitle("删除记忆卡片")
            .setMessage("确定要删除这张记忆卡片吗？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    val database = AppDatabase.getDatabase(this@FlashcardEditActivity)
                    val flashcard = database.standaloneFlashcardDao().getFlashcardById(flashcardId!!)
                    if (flashcard != null) {
                        database.standaloneFlashcardDao().delete(flashcard)
                        Toast.makeText(this@FlashcardEditActivity, "记忆卡片已删除", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}

