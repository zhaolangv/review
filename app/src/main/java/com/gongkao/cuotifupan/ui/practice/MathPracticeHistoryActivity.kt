package com.gongkao.cuotifupan.ui.practice

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.MathPracticeSession
import com.gongkao.cuotifupan.ui.practice.MathQuestionGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

/**
 * 练习历史记录页面
 */
class MathPracticeHistoryActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: View
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_math_practice_history)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "练习历史"
        
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        loadHistory()
    }
    
    private fun loadHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@MathPracticeHistoryActivity)
            val sessions = database.mathPracticeSessionDao().getAllSessionsSync()
            
            withContext(Dispatchers.Main) {
                if (sessions.isEmpty()) {
                    recyclerView.visibility = View.GONE
                    emptyView.visibility = View.VISIBLE
                } else {
                    recyclerView.visibility = View.VISIBLE
                    emptyView.visibility = View.GONE
                    recyclerView.adapter = MathPracticeHistoryAdapter(sessions) { session ->
                        // 点击历史记录，跳转到结果页面查看详情
                        val intent = android.content.Intent(this@MathPracticeHistoryActivity, MathPracticeResultActivity::class.java).apply {
                            putExtra("session_id", session.id)
                        }
                        startActivity(intent)
                    }
                }
            }
        }
    }
}

class MathPracticeHistoryAdapter(
    private val sessions: List<MathPracticeSession>,
    private val onSessionClick: (MathPracticeSession) -> Unit
) : RecyclerView.Adapter<MathPracticeHistoryAdapter.HistoryViewHolder>() {
    
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_math_practice_history, parent, false)
        return HistoryViewHolder(view, onSessionClick)
    }
    
    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(sessions[position])
    }
    
    override fun getItemCount(): Int = sessions.size
    
    class HistoryViewHolder(
        itemView: View,
        private val onSessionClick: (MathPracticeSession) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val practiceTypeText: TextView = itemView.findViewById(R.id.practiceTypeText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val resultText: TextView = itemView.findViewById(R.id.resultText)
        private val dateText: TextView = itemView.findViewById(R.id.dateText)
        
        fun bind(session: MathPracticeSession) {
            try {
                val type = MathQuestionGenerator.PracticeType.valueOf(session.practiceType)
                practiceTypeText.text = type.displayName
            } catch (e: Exception) {
                practiceTypeText.text = session.practiceType
            }
            val hours = session.totalTimeSeconds / 3600
            val minutes = (session.totalTimeSeconds % 3600) / 60
            val seconds = session.totalTimeSeconds % 60
            timeText.text = "用时: ${hours}:${String.format("%02d", minutes)}:${String.format("%02d", seconds)}"
            resultText.text = "正确: ${session.correctCount} / 错误: ${session.wrongCount}"
            dateText.text = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(session.startTime))
            
            // 点击项，跳转到详情页面
            itemView.setOnClickListener {
                onSessionClick(session)
            }
        }
    }
}

