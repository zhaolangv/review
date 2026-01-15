package com.gongkao.cuotifupan.ui

import android.graphics.BitmapFactory
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.ui.TagManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 记忆卡片 ViewPager 适配器
 */
class FlashcardPagerAdapter : ListAdapter<FlashcardReviewActivity.FlashcardItem, FlashcardPagerAdapter.FlashcardViewHolder>(
    FlashcardDiffCallback()
) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlashcardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_flashcard_review, parent, false)
        return FlashcardViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: FlashcardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class FlashcardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val flashcardFront: View = itemView.findViewById(R.id.flashcardFront)
        private val flashcardBack: View = itemView.findViewById(R.id.flashcardBack)
        private val flashcardFrontText: TextView = itemView.findViewById(R.id.flashcardFrontText)
        private val flashcardBackText: TextView = itemView.findViewById(R.id.flashcardBackText)
        private val flashcardFrontImage: ImageView? = itemView.findViewById(R.id.flashcardFrontImage)
        private val flashcardBackImage: ImageView? = itemView.findViewById(R.id.flashcardBackImage)
        
        private var isFlipped = false
        
        fun bind(flashcard: FlashcardReviewActivity.FlashcardItem) {
            flashcardFrontText.text = flashcard.front.ifBlank { "提示内容" }
            flashcardBackText.text = flashcard.back.ifBlank { "记忆内容" }
            
            // 加载图片
            loadImage(flashcard.frontImagePath, flashcardFrontImage)
            loadImage(flashcard.backImagePath, flashcardBackImage)
            
            // 重置翻转状态
            isFlipped = false
            flashcardFront.visibility = View.VISIBLE
            flashcardBack.visibility = View.GONE
            flashcardFront.rotationY = 0f
            flashcardBack.rotationY = 180f
            flashcardFront.alpha = 1f
            flashcardBack.alpha = 1f
            
            // 移除旧的点击监听器，避免重复绑定
            itemView.setOnClickListener(null)
            // 点击翻转
            itemView.setOnClickListener {
                flipCard()
            }
        }
        
        private fun flipCard() {
            val frontVisible = flashcardFront.visibility == View.VISIBLE
            
            if (frontVisible) {
                // 从正面翻转到背面
                val frontAnimator = ObjectAnimator.ofFloat(flashcardFront, "rotationY", 0f, 90f).apply {
                    duration = 150
                    interpolator = AccelerateDecelerateInterpolator()
                }
                
                val backAnimator = ObjectAnimator.ofFloat(flashcardBack, "rotationY", 90f, 0f).apply {
                    duration = 150
                    interpolator = AccelerateDecelerateInterpolator()
                    startDelay = 150
                }
                
                frontAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        flashcardFront.visibility = View.GONE
                        flashcardBack.visibility = View.VISIBLE
                        flashcardBack.rotationY = 90f
                    }
                })
                
                backAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        flashcardBack.visibility = View.VISIBLE
                        flashcardBack.rotationY = 90f
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        flashcardBack.rotationY = 0f
                    }
                })
                
                frontAnimator.start()
                backAnimator.start()
            } else {
                // 从背面翻转到正面
                val backAnimator = ObjectAnimator.ofFloat(flashcardBack, "rotationY", 0f, 90f).apply {
                    duration = 150
                    interpolator = AccelerateDecelerateInterpolator()
                }
                
                val frontAnimator = ObjectAnimator.ofFloat(flashcardFront, "rotationY", 90f, 0f).apply {
                    duration = 150
                    interpolator = AccelerateDecelerateInterpolator()
                    startDelay = 150
                }
                
                backAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        flashcardBack.visibility = View.GONE
                        flashcardFront.visibility = View.VISIBLE
                        flashcardFront.rotationY = 90f
                    }
                })
                
                frontAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        flashcardFront.visibility = View.VISIBLE
                        flashcardFront.rotationY = 90f
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        flashcardFront.rotationY = 0f
                    }
                })
                
                backAnimator.start()
                frontAnimator.start()
            }
            
            isFlipped = !isFlipped
        }
        
        private fun loadImage(imagePath: String?, imageView: ImageView?) {
            if (imageView == null) return
            
            if (imagePath != null && File(imagePath).exists()) {
                // 在主线程加载图片（因为 ViewHolder 已经绑定到视图）
                try {
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        imageView.visibility = View.VISIBLE
                    } else {
                        imageView.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    android.util.Log.e("FlashcardPagerAdapter", "加载图片失败", e)
                    imageView.visibility = View.GONE
                }
            } else {
                imageView.visibility = View.GONE
                imageView.setImageBitmap(null)
            }
        }
    }
    
    class FlashcardDiffCallback : DiffUtil.ItemCallback<FlashcardReviewActivity.FlashcardItem>() {
        override fun areItemsTheSame(
            oldItem: FlashcardReviewActivity.FlashcardItem,
            newItem: FlashcardReviewActivity.FlashcardItem
        ): Boolean {
            return oldItem.flashcardId == newItem.flashcardId
        }
        
        override fun areContentsTheSame(
            oldItem: FlashcardReviewActivity.FlashcardItem,
            newItem: FlashcardReviewActivity.FlashcardItem
        ): Boolean {
            return oldItem == newItem
        }
    }
}

