package com.gongkao.cuotifupan.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.data.StandaloneFlashcard
import com.gongkao.cuotifupan.util.SpacedRepetitionAlgorithm
import com.gongkao.cuotifupan.util.PreferencesManager
import com.gongkao.cuotifupan.viewmodel.QuestionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 记忆卡片复习页面（Anki 风格）
 * 使用间隔重复算法（SM-2）
 */
class FlashcardReviewActivity : AppCompatActivity() {
    
    private lateinit var viewModel: QuestionViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var flashcardAdapter: FlashcardPagerAdapter
    private lateinit var againButton: Button
    private lateinit var hardButton: Button
    private lateinit var goodButton: Button
    private lateinit var easyButton: Button
    private lateinit var statusText: TextView
    private lateinit var statsText: TextView
    
    private var dueFlashcards: List<FlashcardItem> = emptyList()
    private var currentPosition: Int = 0
    private var newCardCount: Int = 0
    private var learningCardCount: Int = 0
    private var reviewCardCount: Int = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_flashcard_review)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "卡片复习"
        
        viewModel = ViewModelProvider(this)[QuestionViewModel::class.java]
        
        viewPager = findViewById(R.id.flashcardViewPager)
        againButton = findViewById(R.id.againButton)
        hardButton = findViewById(R.id.hardButton)
        goodButton = findViewById(R.id.goodButton)
        easyButton = findViewById(R.id.easyButton)
        statusText = findViewById(R.id.statusText)
        statsText = findViewById(R.id.statsText)
        
        // 设置 ViewPager
        flashcardAdapter = FlashcardPagerAdapter()
        viewPager.adapter = flashcardAdapter
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        
        // 设置按钮点击事件
        againButton.setOnClickListener {
            reviewCurrentCard(SpacedRepetitionAlgorithm.Difficulty.AGAIN)
        }
        hardButton.setOnClickListener {
            reviewCurrentCard(SpacedRepetitionAlgorithm.Difficulty.HARD)
        }
        goodButton.setOnClickListener {
            reviewCurrentCard(SpacedRepetitionAlgorithm.Difficulty.GOOD)
        }
        easyButton.setOnClickListener {
            reviewCurrentCard(SpacedRepetitionAlgorithm.Difficulty.EASY)
        }
        
        // ViewPager 页面变化监听
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updateStatus()
            }
        })
        
        // 加载今日需要复习的卡片
        loadDueFlashcards()
    }
    
    private fun loadDueFlashcards() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@FlashcardReviewActivity)
            val currentTime = System.currentTimeMillis()
            
            // 获取今日需要复习的卡片
            val dueCards = withContext(Dispatchers.IO) {
                database.standaloneFlashcardDao().getDueCards(currentTime)
            }
            
            // 获取统计信息
            newCardCount = withContext(Dispatchers.IO) {
                database.standaloneFlashcardDao().getNewCardCount()
            }
            learningCardCount = withContext(Dispatchers.IO) {
                database.standaloneFlashcardDao().getLearningCardCount()
            }
            reviewCardCount = withContext(Dispatchers.IO) {
                database.standaloneFlashcardDao().getDueReviewCardCount(currentTime)
            }
            
            // 转换为 FlashcardItem
            dueFlashcards = dueCards.map { flashcard ->
                // 创建一个虚拟的 Question 对象用于显示标签等信息
                val virtualQuestion = Question(
                    id = flashcard.questionId ?: flashcard.id,
                    imagePath = "",
                    rawText = "",
                    questionText = "",
                    tags = flashcard.tags,
                    userNotes = "",
                    createdAt = flashcard.createdAt,
                    reviewState = flashcard.reviewState
                )
                
                FlashcardItem(
                    flashcardId = flashcard.id,
                    questionId = flashcard.questionId ?: flashcard.id,
                    front = flashcard.front,
                    back = flashcard.back,
                    frontImagePath = flashcard.frontImagePath,
                    backImagePath = flashcard.backImagePath,
                    timestamp = flashcard.createdAt,
                    question = virtualQuestion,
                    standaloneFlashcard = flashcard
                )
            }
            
            flashcardAdapter.submitList(dueFlashcards)
            updateStatus()
            updateStats()
        }
    }
    
    private fun updateStatus() {
        if (dueFlashcards.isEmpty()) {
            statusText.text = "今日没有需要复习的卡片"
            againButton.isEnabled = false
            hardButton.isEnabled = false
            goodButton.isEnabled = false
            easyButton.isEnabled = false
        } else {
            statusText.text = "${currentPosition + 1} / ${dueFlashcards.size}"
            againButton.isEnabled = true
            hardButton.isEnabled = true
            goodButton.isEnabled = true
            easyButton.isEnabled = true
        }
    }
    
    private fun updateStats() {
        statsText.text = "新: $newCardCount | 学习: $learningCardCount | 复习: $reviewCardCount"
    }
    
    private fun reviewCurrentCard(difficulty: SpacedRepetitionAlgorithm.Difficulty) {
        if (currentPosition >= dueFlashcards.size) return
        
        val flashcardItem = dueFlashcards[currentPosition]
        val standaloneFlashcard = flashcardItem.standaloneFlashcard ?: return
        
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@FlashcardReviewActivity)
            
            // 使用间隔重复算法更新卡片
            val updatedCard = SpacedRepetitionAlgorithm.reviewCard(standaloneFlashcard, difficulty)
            
            // 保存更新后的卡片
            withContext(Dispatchers.IO) {
                database.standaloneFlashcardDao().update(updatedCard)
            }
            
            // 显示反馈
            val feedbackText = when (difficulty) {
                SpacedRepetitionAlgorithm.Difficulty.AGAIN -> "标记为再次"
                SpacedRepetitionAlgorithm.Difficulty.HARD -> "标记为困难"
                SpacedRepetitionAlgorithm.Difficulty.GOOD -> "标记为良好"
                SpacedRepetitionAlgorithm.Difficulty.EASY -> "标记为简单"
            }
            Toast.makeText(this@FlashcardReviewActivity, feedbackText, Toast.LENGTH_SHORT).show()
            
            // 重新加载卡片列表（因为卡片状态可能已改变，需要从队列中移除）
            loadDueFlashcards()
            
            // 如果还有卡片，保持当前位置（因为列表更新了，当前位置的卡片已被移除）
            // 如果当前位置超出范围，显示完成提示
            if (dueFlashcards.isEmpty()) {
                Toast.makeText(this@FlashcardReviewActivity, "今日复习完成！", Toast.LENGTH_SHORT).show()
                viewPager.setCurrentItem(0, false)
            } else if (currentPosition >= dueFlashcards.size) {
                // 如果当前位置超出范围，跳转到最后一张
                viewPager.setCurrentItem(dueFlashcards.size - 1, true)
            }
        }
    }
    
    /**
     * 卡片数据类（用于适配器）
     */
    data class FlashcardItem(
        val flashcardId: String,
        val questionId: String,
        val front: String,
        val back: String,
        val frontImagePath: String? = null,
        val backImagePath: String? = null,
        val timestamp: Long,
        val question: Question,
        val standaloneFlashcard: StandaloneFlashcard? = null  // 保存原始卡片对象，用于复习
    )
}
