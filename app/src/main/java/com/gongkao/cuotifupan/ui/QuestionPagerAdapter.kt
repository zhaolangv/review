package com.gongkao.cuotifupan.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.Question
import java.io.File

/**
 * 题目 ViewPager 适配器
 */
class QuestionPagerAdapter(
    private val onQuestionClick: (Question) -> Unit
) : ListAdapter<Question, QuestionPagerAdapter.QuestionViewHolder>(
    QuestionDiffCallback()
) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_question_review, parent, false)
        return QuestionViewHolder(view, onQuestionClick)
    }
    
    override fun onBindViewHolder(holder: QuestionViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class QuestionViewHolder(
        itemView: View,
        private val onQuestionClick: (Question) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val questionImageView: ImageView = itemView.findViewById(R.id.questionImageView)
        private val questionTextView: TextView = itemView.findViewById(R.id.questionTextView)
        private val optionsTextView: TextView = itemView.findViewById(R.id.optionsTextView)
        private val toggleImageButton: ImageButton = itemView.findViewById(R.id.toggleImageButton)
        
        private var currentQuestion: Question? = null
        private var showingCleanedImage: Boolean = true // 默认显示擦写后的图片
        
        fun bind(question: Question) {
            currentQuestion = question
            
            // 设置图片切换按钮的显示状态
            if (!question.cleanedImagePath.isNullOrBlank() && !question.originalImagePath.isNullOrBlank()) {
                // 有擦写后的图片和原图，显示切换按钮
                toggleImageButton.visibility = View.VISIBLE
                // 默认显示擦写后的图片
                showingCleanedImage = true
                updateToggleButtonIcon()
                
                // 设置切换按钮点击事件
                toggleImageButton.setOnClickListener {
                    toggleImage(question)
                }
            } else {
                // 没有擦写后的图片，隐藏切换按钮
                toggleImageButton.visibility = View.GONE
                showingCleanedImage = false
            }
            
            // 确定要显示的图片路径（如果有擦写后的图片，默认显示擦写后的）
            val imagePathToShow = if (!question.cleanedImagePath.isNullOrBlank() && showingCleanedImage) {
                question.cleanedImagePath
            } else {
                question.originalImagePath ?: question.imagePath
            }
            
            // 加载图片
            loadImage(imagePathToShow)
            
            // 显示题干
            questionTextView.text = question.questionText.ifBlank { "题目内容" }
            
            // 显示选项
            val options = try {
                if (question.options.isNotBlank()) {
                    org.json.JSONArray(question.options).let { array ->
                        (0 until array.length()).joinToString("\n") { 
                            array.getString(it) 
                        }
                    }
                } else {
                    ""
                }
            } catch (e: Exception) {
                ""
            }
            optionsTextView.text = options.ifBlank { "暂无选项" }
            
            // 点击跳转到详情页（但不在图片和切换按钮上响应）
            itemView.setOnClickListener {
                // 如果点击的是切换按钮，不跳转
                if (it.id != R.id.toggleImageButton && it.id != R.id.questionImageView) {
                    onQuestionClick(question)
                }
            }
        }
        
        /**
         * 切换显示原图或擦写后的图片
         */
        private fun toggleImage(question: Question) {
            // 如果没有擦写后的图片，无法切换
            if (question.cleanedImagePath.isNullOrBlank() || question.originalImagePath.isNullOrBlank()) {
                return
            }
            
            showingCleanedImage = !showingCleanedImage
            
            // 确定要显示的图片路径
            val imagePathToShow = if (showingCleanedImage && !question.cleanedImagePath.isNullOrBlank()) {
                question.cleanedImagePath
            } else {
                question.originalImagePath ?: question.imagePath
            }
            
            // 加载图片
            loadImage(imagePathToShow)
            
            // 更新按钮图标提示
            updateToggleButtonIcon()
        }
        
        /**
         * 更新切换按钮图标
         */
        private fun updateToggleButtonIcon() {
            val question = currentQuestion ?: return
            if (question.cleanedImagePath.isNullOrBlank() || question.originalImagePath.isNullOrBlank()) {
                toggleImageButton.visibility = View.GONE
                return
            }
            
            // 根据当前显示状态设置图标和提示
            if (showingCleanedImage) {
                toggleImageButton.contentDescription = "切换到原图"
                toggleImageButton.setImageResource(android.R.drawable.ic_menu_view)
            } else {
                toggleImageButton.contentDescription = "切换到擦写后"
                toggleImageButton.setImageResource(android.R.drawable.ic_menu_view)
            }
        }
        
        /**
         * 加载图片
         */
        private fun loadImage(imagePath: String) {
            val context = itemView.context
            val file = File(imagePath)
            
            if (file.exists() && (imagePath.startsWith(context.filesDir.absolutePath) || 
                imagePath.startsWith(context.cacheDir.absolutePath))) {
                // 应用私有文件，直接使用 Coil 加载
                questionImageView.load(file)
            } else {
                // 使用 ImageAccessHelper 加载（兼容 MediaStore URI）
                val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(
                    context, 
                    imagePath
                )
                if (bitmap != null) {
                    questionImageView.setImageBitmap(bitmap)
                } else {
                    questionImageView.setImageResource(android.R.drawable.ic_menu_report_image)
                }
            }
        }
    }
    
    class QuestionDiffCallback : DiffUtil.ItemCallback<Question>() {
        override fun areItemsTheSame(oldItem: Question, newItem: Question): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Question, newItem: Question): Boolean {
            return oldItem == newItem
        }
    }
}

