package com.gongkao.cuotifupan.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import coil.load
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.util.PreferencesManager
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

/**
 * 题目确认对话框 Activity（全屏显示，可在后台弹出）
 */
class QuestionConfirmDialogActivity : AppCompatActivity() {
    
    private lateinit var question: Question
    private var isAdded = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置窗口属性，使其可以在其他应用之上显示
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            window.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        } else {
            @Suppress("DEPRECATION")
            window.setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT)
        }
        window.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        window.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD)
        window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        
        // 获取题目信息
        val questionId = intent.getStringExtra("question_id") ?: run {
            finish()
            return
        }
        
        // 从数据库获取题目
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@QuestionConfirmDialogActivity)
            val questionFromDb = database.questionDao().getQuestionById(questionId)
            
            if (questionFromDb == null) {
                finish()
                return@launch
            }
            
            question = questionFromDb
            showDialog()
        }
    }
    
    private fun showDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_question_confirm, null)
        
        val imageView = dialogView.findViewById<ImageView>(R.id.questionImage)
        val questionText = dialogView.findViewById<TextView>(R.id.questionText)
        val notesInput = dialogView.findViewById<EditText>(R.id.notesInput)
        val addButton = dialogView.findViewById<Button>(R.id.addButton)
        val skipButton = dialogView.findViewById<Button>(R.id.skipButton)
        
        // 加载图片
        val file = File(question.imagePath)
        if (file.exists()) {
            imageView.load(file)
        }
        
        // 显示题目文本
        questionText.text = question.questionText.take(100) + if (question.questionText.length > 100) "..." else ""
        
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        // 设置对话框窗口属性
        dialog.window?.setFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        )
        
        addButton.setOnClickListener {
            val notes = notesInput.text.toString().trim()
            if (notes.isNotEmpty()) {
                // 保存笔记
                saveNotes(notes)
            }
            isAdded = true
            dialog.dismiss()
            finish()
        }
        
        skipButton.setOnClickListener {
            // 如果用户选择跳过，删除题目
            lifecycleScope.launch {
                val database = AppDatabase.getDatabase(this@QuestionConfirmDialogActivity)
                database.questionDao().delete(question)
            }
            dialog.dismiss()
            finish()
        }
        
        dialog.show()
    }
    
    private fun saveNotes(notes: String) {
        lifecycleScope.launch {
            try {
                val database = AppDatabase.getDatabase(this@QuestionConfirmDialogActivity)
                
                // 创建笔记项
                val noteItem = mapOf(
                    "id" to System.currentTimeMillis().toString(),
                    "type" to "note",
                    "content" to notes,
                    "front" to "",
                    "back" to "",
                    "timestamp" to System.currentTimeMillis()
                )
                
                // 解析现有笔记
                val existingNotes = try {
                    if (question.userNotes.isNotBlank()) {
                        JSONArray(question.userNotes).let { array ->
                            (0 until array.length()).map { index ->
                                array.getJSONObject(index)
                            }.toMutableList()
                        }
                    } else {
                        mutableListOf()
                    }
                } catch (e: Exception) {
                    mutableListOf()
                }
                
                // 添加新笔记
                existingNotes.add(org.json.JSONObject(noteItem))
                
                // 保存到题目
                val updatedQuestion = question.copy(
                    userNotes = JSONArray(existingNotes).toString()
                )
                
                database.questionDao().update(updatedQuestion)
                
                Toast.makeText(this@QuestionConfirmDialogActivity, "已加入题库并保存笔记", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@QuestionConfirmDialogActivity, "保存笔记失败", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (!isAdded && ::question.isInitialized) {
            // 如果用户没有点击"加入题库"，删除题目
            lifecycleScope.launch {
                try {
                    val database = AppDatabase.getDatabase(this@QuestionConfirmDialogActivity)
                    database.questionDao().delete(question)
                } catch (e: Exception) {
                    // 忽略删除失败的情况
                }
            }
        }
    }
}
