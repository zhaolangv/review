package com.gongkao.cuotifupan.ui

import android.graphics.BitmapFactory
import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.ScrollView
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
class FlashcardPagerAdapter(
    private val isFlipMode: Boolean = true
) : ListAdapter<FlashcardReviewActivity.FlashcardItem, FlashcardPagerAdapter.FlashcardViewHolder>(
    FlashcardDiffCallback()
) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FlashcardViewHolder {
        val layoutId = if (isFlipMode) {
            R.layout.item_flashcard_review
        } else {
            R.layout.item_flashcard_review_stacked
        }
        val view = LayoutInflater.from(parent.context)
            .inflate(layoutId, parent, false)
        return FlashcardViewHolder(view, isFlipMode)
    }
    
    override fun onBindViewHolder(holder: FlashcardViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class FlashcardViewHolder(itemView: View, private val isFlipMode: Boolean) : RecyclerView.ViewHolder(itemView) {
        private val flashcardFront: View? = itemView.findViewById(R.id.flashcardFront)
        private val flashcardBack: View? = itemView.findViewById(R.id.flashcardBack)
        private val flashcardFrontText: TextView = itemView.findViewById(R.id.flashcardFrontText)
        private val flashcardBackText: TextView = itemView.findViewById(R.id.flashcardBackText)
        private val flashcardFrontImage: ImageView? = itemView.findViewById(R.id.flashcardFrontImage)
        private val flashcardBackImage: ImageView? = itemView.findViewById(R.id.flashcardBackImage)
        
        // 上下模式特有视图
        private val showAnswerButton: Button? = itemView.findViewById(R.id.showAnswerButton)
        private val backScrollView: View? = itemView.findViewById(R.id.backScrollView)
        private val divider: View? = itemView.findViewById(R.id.divider)
        
        private var isFlipped = false
        private var isAnswerShown = false
        
        // 用于检测点击（而不是滚动）
        private var touchStartX = 0f
        private var touchStartY = 0f
        private var hasMoved = false
        
        fun bind(flashcard: FlashcardReviewActivity.FlashcardItem) {
            flashcardFrontText.text = flashcard.front.ifBlank { "提示内容" }
            flashcardBackText.text = flashcard.back.ifBlank { "记忆内容" }
            
            // 加载图片
            loadImage(flashcard.frontImagePath, flashcardFrontImage)
            loadImage(flashcard.backImagePath, flashcardBackImage)
            
            if (isFlipMode) {
                // 翻转模式：重置翻转状态
                isFlipped = false
                flashcardFront?.visibility = View.VISIBLE
                flashcardBack?.visibility = View.GONE
                flashcardFront?.rotationY = 0f
                flashcardBack?.rotationY = 180f
                flashcardFront?.alpha = 1f
                flashcardBack?.alpha = 1f
                
                // 移除旧的监听器
                itemView.setOnClickListener(null)
                itemView.setOnTouchListener(null)
                flashcardFront?.setOnClickListener(null)
                flashcardFront?.setOnTouchListener(null)
                flashcardBack?.setOnClickListener(null)
                flashcardBack?.setOnTouchListener(null)
                
                // 设置翻转模式的点击事件
                setupFlipModeListeners()
            } else {
                // 上下模式：重置显示状态
                isAnswerShown = false
                showAnswerButton?.visibility = View.VISIBLE
                backScrollView?.visibility = View.GONE
                divider?.visibility = View.GONE
                
                // 设置上下模式的点击事件
                setupStackedModeListeners()
            }
        }
        
        private fun setupFlipModeListeners() {
            // 使用递归方法找到 ScrollView
            val frontSV = findScrollView(flashcardFront)
            val backSV = findScrollView(flashcardBack)
            
            // 设置 ScrollView 的触摸监听器，让点击事件传递给父视图
            frontSV?.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.x
                        touchStartY = event.y
                        hasMoved = false
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = kotlin.math.abs(event.x - touchStartX)
                        val dy = kotlin.math.abs(event.y - touchStartY)
                        if (dx > 10 || dy > 10) {
                            hasMoved = true
                        }
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!hasMoved) {
                            flipCard()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            
            backSV?.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        touchStartX = event.x
                        touchStartY = event.y
                        hasMoved = false
                        false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = kotlin.math.abs(event.x - touchStartX)
                        val dy = kotlin.math.abs(event.y - touchStartY)
                        if (dx > 10 || dy > 10) {
                            hasMoved = true
                        }
                        false
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!hasMoved) {
                            flipCard()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            
            // 也在正面和背面视图上设置点击监听器作为备用
            flashcardFront?.setOnClickListener {
                android.util.Log.d("FlashcardPagerAdapter", "flashcardFront clicked, flipping card")
                flipCard()
            }
            
            flashcardBack?.setOnClickListener {
                android.util.Log.d("FlashcardPagerAdapter", "flashcardBack clicked, flipping card")
                flipCard()
            }
        }
        
        private fun setupStackedModeListeners() {
            // 上下模式：点击"显示答案"按钮显示答案
            showAnswerButton?.setOnClickListener {
                if (!isAnswerShown) {
                    isAnswerShown = true
                    showAnswerButton?.visibility = View.GONE
                    divider?.visibility = View.VISIBLE
                    backScrollView?.visibility = View.VISIBLE
                }
            }
        }
        
        private fun findScrollView(view: View?): ScrollView? {
            if (view == null) return null
            if (view is ScrollView) {
                return view
            }
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) {
                    val child = view.getChildAt(i)
                    val result = findScrollView(child)
                    if (result != null) {
                        return result
                    }
                }
            }
            return null
        }
        
        private fun flipCard() {
            if (!isFlipMode) return // 只在翻转模式下使用
            android.util.Log.d("FlashcardPagerAdapter", "flipCard() called, isFlipped=$isFlipped")
            val frontVisible = flashcardFront?.visibility == View.VISIBLE
            android.util.Log.d("FlashcardPagerAdapter", "frontVisible=$frontVisible")
            
            val front = flashcardFront ?: return
            val back = flashcardBack ?: return
            
            if (frontVisible) {
                // 从正面翻转到背面
                val frontAnimator = ObjectAnimator.ofFloat(front, "rotationY", 0f, 90f).apply {
                    duration = 150
                    interpolator = AccelerateDecelerateInterpolator()
                }
                
                val backAnimator = ObjectAnimator.ofFloat(back, "rotationY", 90f, 0f).apply {
                    duration = 150
                    interpolator = AccelerateDecelerateInterpolator()
                    startDelay = 150
                }
                
                frontAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        front.visibility = View.GONE
                        back.visibility = View.VISIBLE
                        back.rotationY = 90f
                    }
                })
                
                backAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        back.visibility = View.VISIBLE
                        back.rotationY = 90f
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        back.rotationY = 0f
                    }
                })
                
                frontAnimator.start()
                backAnimator.start()
            } else {
                // 从背面翻转到正面
                val backAnimator = ObjectAnimator.ofFloat(back, "rotationY", 0f, 90f).apply {
                    duration = 150
                    interpolator = AccelerateDecelerateInterpolator()
                }
                
                val frontAnimator = ObjectAnimator.ofFloat(front, "rotationY", 90f, 0f).apply {
                    duration = 150
                    interpolator = AccelerateDecelerateInterpolator()
                    startDelay = 150
                }
                
                backAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        back.visibility = View.GONE
                        front.visibility = View.VISIBLE
                        front.rotationY = 90f
                    }
                })
                
                frontAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        front.visibility = View.VISIBLE
                        front.rotationY = 90f
                    }
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        front.rotationY = 0f
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
                    // 先获取图片尺寸，不立即加载完整图片
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeFile(imagePath, options)
                    
                    // 获取 ImageView 的最大高度（400dp），转换为像素
                    val maxHeightPx = (400 * imageView.context.resources.displayMetrics.density).toInt()
                    
                    // 如果图片高度超过最大高度，进行缩放
                    val sampleSize = if (options.outHeight > maxHeightPx) {
                        (options.outHeight / maxHeightPx.toFloat()).toInt().coerceAtLeast(1)
                    } else {
                        1
                    }
                    
                    // 使用采样加载图片
                    val decodeOptions = BitmapFactory.Options().apply {
                        this.inSampleSize = sampleSize
                    }
                    
                    val bitmap = BitmapFactory.decodeFile(imagePath, decodeOptions)
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        imageView.visibility = View.VISIBLE
                        android.util.Log.d("FlashcardPagerAdapter", "图片加载成功: ${bitmap.width}x${bitmap.height}, sampleSize=$sampleSize")
                    } else {
                        imageView.visibility = View.GONE
                        android.util.Log.w("FlashcardPagerAdapter", "图片解码失败: $imagePath")
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

