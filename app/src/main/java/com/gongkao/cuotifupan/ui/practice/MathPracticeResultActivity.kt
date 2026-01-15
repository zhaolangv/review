package com.gongkao.cuotifupan.ui.practice

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * 数学练习结果页面
 */
class MathPracticeResultActivity : AppCompatActivity() {
    
    private lateinit var practiceTypeText: TextView
    private lateinit var timeText: TextView
    private lateinit var resultRecyclerView: RecyclerView
    private lateinit var restartButton: Button
    private lateinit var backButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_math_practice_result)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "练习结果"
        
        val sessionId = intent.getStringExtra("session_id")
        
        initViews()
        loadResult(sessionId)
    }
    
    private fun initViews() {
        practiceTypeText = findViewById(R.id.practiceTypeText)
        timeText = findViewById(R.id.timeText)
        resultRecyclerView = findViewById(R.id.resultRecyclerView)
        restartButton = findViewById(R.id.restartButton)
        backButton = findViewById(R.id.backButton)
        
        resultRecyclerView.layoutManager = LinearLayoutManager(this)
        
        restartButton.setOnClickListener {
            finish()
        }
        
        backButton.setOnClickListener {
            finish()
        }
    }
    
    private fun loadResult(sessionId: String?) {
        if (sessionId == null) {
            finish()
            return
        }
        
        lifecycleScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@MathPracticeResultActivity)
            val session = database.mathPracticeSessionDao().getSessionById(sessionId)
            
            withContext(Dispatchers.Main) {
                if (session != null) {
                    try {
                        val type = MathQuestionGenerator.PracticeType.valueOf(session.practiceType)
                        practiceTypeText.text = type.displayName
                    } catch (e: Exception) {
                        practiceTypeText.text = session.practiceType
                    }
                    val hours = session.totalTimeSeconds / 3600
                    val minutes = (session.totalTimeSeconds % 3600) / 60
                    val seconds = session.totalTimeSeconds % 60
                    timeText.text = "本次练习用时${hours}:${String.format("%02d", minutes)}:${String.format("%02d", seconds)}加油"
                    
                    val questions = JSONArray(session.questionsData)
                    val results = mutableListOf<QuestionResult>()
                    for (i in 0 until questions.length()) {
                        val q = questions.getJSONObject(i)
                        results.add(QuestionResult(
                            question = q.getString("question"),
                            correctAnswer = q.getDouble("correctAnswer"),
                            userAnswer = q.optString("userAnswer", "").toDoubleOrNull(),
                            isCorrect = q.getBoolean("isCorrect")
                        ))
                    }
                    
                    resultRecyclerView.adapter = MathPracticeResultAdapter(results)
                }
            }
        }
    }
    
    data class QuestionResult(
        val question: String,
        val correctAnswer: Double,
        val userAnswer: Double?,
        val isCorrect: Boolean
    )
}

