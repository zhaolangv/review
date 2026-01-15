package com.gongkao.cuotifupan.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.gongkao.cuotifupan.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.viewmodel.QuestionViewModel
import com.github.chrisbanes.photoview.PhotoView
import coil.load
import java.io.File

/**
 * 图片全屏查看（简化版，只支持全屏查看）
 */
class ImageFullscreenWithNotesActivity : AppCompatActivity() {
    
    private lateinit var photoView: PhotoView
    private lateinit var viewModel: QuestionViewModel
    
    private var currentQuestion: Question? = null
    private var allQuestions: List<Question> = emptyList()
    private var currentPosition: Int = 0
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_fullscreen_with_notes)
        
        viewModel = ViewModelProvider(this)[QuestionViewModel::class.java]
        
        photoView = findViewById(R.id.photoView)
        
        // 获取题目ID
        val questionId = intent.getStringExtra("question_id")
        val position = intent.getIntExtra("current_position", 0)
        currentPosition = position
        
        // 从 ViewModel 获取题目列表
        viewModel.allQuestions.observe(this) { questions ->
            allQuestions = questions
            if (questionId != null) {
                loadQuestion(questionId)
                // 找到当前题目在列表中的位置
                currentPosition = questions.indexOfFirst { it.id == questionId }.takeIf { it >= 0 } ?: position
            } else if (position < questions.size) {
                currentPosition = position
                loadQuestion(questions[position].id)
            }
        }
        
        // 设置左右滑动切换图片
        setupImageSwipe()
    }
    
    private fun setupImageSwipe() {
        // 使用手势检测实现左右滑动切换图片
        var startX = 0f
        var startY = 0f
        var isSwiping = false
        
        photoView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.x
                    startY = event.y
                    isSwiping = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    
                    // 判断是否主要是横向滑动
                    if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > 50) {
                        isSwiping = true
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    if (isSwiping) {
                        val dx = event.x - startX
                        
                        // 左滑（向右切换，显示下一张）
                        if (dx < -100 && currentPosition < allQuestions.size - 1) {
                            currentPosition++
                            loadQuestion(allQuestions[currentPosition].id)
                        }
                        // 右滑（向左切换，显示上一张）
                        else if (dx > 100 && currentPosition > 0) {
                            currentPosition--
                            loadQuestion(allQuestions[currentPosition].id)
                        }
                    }
                    false
                }
                else -> false
            }
        }
    }
    
    private fun loadQuestion(questionId: String) {
        lifecycleScope.launch {
            val question = withContext(Dispatchers.IO) {
                viewModel.getQuestionById(questionId)
            }
            question?.let {
                currentQuestion = it
                displayQuestion(it)
            }
        }
    }
    
    private fun displayQuestion(question: Question) {
        // 确定要显示的基础图片
        val baseImagePath = question.originalImagePath ?: question.imagePath
        
            // 直接显示原图
            loadBaseImage(baseImagePath)
    }
    
    /**
     * 加载基础图片（无标注）
     */
    private fun loadBaseImage(imagePath: String) {
        val file = File(imagePath)
        if (file.exists() && (imagePath.startsWith(filesDir.absolutePath) || 
            imagePath.startsWith(cacheDir.absolutePath) ||
            imagePath.contains("/DCIM/Camera/"))) {
            photoView.load(file)
        } else if (com.gongkao.cuotifupan.util.ImageAccessHelper.isValidImage(this, imagePath)) {
            val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(this, imagePath)
            if (bitmap != null) {
                photoView.setImageBitmap(bitmap)
            } else {
                photoView.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        } else {
            photoView.setImageResource(android.R.drawable.ic_menu_report_image)
            Toast.makeText(this, "图片文件不存在，可能已被删除", Toast.LENGTH_SHORT).show()
        }
    }
}
