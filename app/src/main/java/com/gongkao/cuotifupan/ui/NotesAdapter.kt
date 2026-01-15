package com.gongkao.cuotifupan.ui

import android.animation.ObjectAnimator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.NoteItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * 笔记列表适配器（支持笔记和记忆卡片）
 */
class NotesAdapter(
    private val onNoteDelete: ((NoteItem) -> Unit)? = null,
    private val showSimpleFlashcard: Boolean = false // 是否显示简单格式的卡片（不翻转）
) : ListAdapter<NoteItem, RecyclerView.ViewHolder>(NoteDiffCallback()) {
    
    companion object {
        private const val VIEW_TYPE_NOTE = 0
        private const val VIEW_TYPE_FLASHCARD = 1
    }
    
    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        return if (item.type == "flashcard") VIEW_TYPE_FLASHCARD else VIEW_TYPE_NOTE
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_NOTE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_note, parent, false)
                NoteViewHolder(view, onNoteDelete)
            }
            VIEW_TYPE_FLASHCARD -> {
                val layoutId = if (showSimpleFlashcard) R.layout.item_flashcard_simple else R.layout.item_flashcard
                val view = LayoutInflater.from(parent.context)
                    .inflate(layoutId, parent, false)
                if (showSimpleFlashcard) {
                    SimpleFlashcardViewHolder(view, onNoteDelete)
                } else {
                    FlashcardViewHolder(view, onNoteDelete)
                }
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is NoteViewHolder -> holder.bind(item)
            is FlashcardViewHolder -> holder.bind(item)
            is SimpleFlashcardViewHolder -> holder.bind(item)
        }
    }
    
    class NoteViewHolder(
        itemView: View,
        private val onNoteDelete: ((NoteItem) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val noteContent: TextView = itemView.findViewById(R.id.noteContent)
        private val noteTime: TextView = itemView.findViewById(R.id.noteTime)
        
        fun bind(note: NoteItem) {
            noteContent.text = note.content
            
            val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            noteTime.text = dateFormat.format(Date(note.timestamp))
            
            // 设置长按监听器，删除笔记
            itemView.setOnLongClickListener {
                onNoteDelete?.invoke(note)
                true
            }
        }
    }
    
    class FlashcardViewHolder(
        itemView: View,
        private val onNoteDelete: ((NoteItem) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val flashcardFront: View = itemView.findViewById(R.id.flashcardFront)
        private val flashcardBack: View = itemView.findViewById(R.id.flashcardBack)
        private val flashcardFrontText: TextView = itemView.findViewById(R.id.flashcardFrontText)
        private val flashcardBackText: TextView = itemView.findViewById(R.id.flashcardBackText)
        private val flashcardTime: TextView = itemView.findViewById(R.id.flashcardTime)
        
        private var isFlipped = false
        
        fun bind(note: NoteItem) {
            flashcardFrontText.text = note.front
            flashcardBackText.text = note.back
            
            val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            flashcardTime.text = dateFormat.format(Date(note.timestamp))
            
            // 重置翻转状态
            isFlipped = false
            flashcardFront.visibility = View.VISIBLE
            flashcardBack.visibility = View.GONE
            flashcardFront.rotationY = 0f
            flashcardBack.rotationY = 180f
            
            // 点击翻转
            itemView.setOnClickListener {
                flipCard()
            }
            
            // 长按删除
            itemView.setOnLongClickListener {
                onNoteDelete?.invoke(note)
                true
            }
        }
        
        private fun flipCard() {
            val frontVisible = flashcardFront.visibility == View.VISIBLE
            
            // 创建翻转动画
            val frontAnimator = ObjectAnimator.ofFloat(
                flashcardFront,
                "rotationY",
                if (frontVisible) 0f else 180f,
                if (frontVisible) 90f else 90f
            ).apply {
                duration = 150
                interpolator = AccelerateDecelerateInterpolator()
            }
            
            val backAnimator = ObjectAnimator.ofFloat(
                flashcardBack,
                "rotationY",
                if (frontVisible) 180f else 90f,
                if (frontVisible) 90f else 0f
            ).apply {
                duration = 150
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = 150
            }
            
            // 在动画中间切换可见性
            frontAnimator.addUpdateListener { animator ->
                val value = animator.animatedValue
                if (value is Float && value > 45f && frontVisible) {
                    flashcardFront.visibility = View.GONE
                    flashcardBack.visibility = View.VISIBLE
                }
            }
            
            backAnimator.addUpdateListener { animator ->
                val value = animator.animatedValue
                if (value is Float && value < 135f && !frontVisible) {
                    flashcardBack.visibility = View.GONE
                    flashcardFront.visibility = View.VISIBLE
                }
            }
            
            frontAnimator.start()
            backAnimator.start()
            
            isFlipped = !isFlipped
        }
    }
    
    class SimpleFlashcardViewHolder(
        itemView: View,
        private val onNoteDelete: ((NoteItem) -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val flashcardContent: TextView = itemView.findViewById(R.id.flashcardContent)
        private val flashcardTime: TextView = itemView.findViewById(R.id.flashcardTime)
        
        fun bind(note: NoteItem) {
            // 显示为"提示：内容"格式
            flashcardContent.text = "提示：${note.front}\n内容：${note.back}"
            
            val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            flashcardTime.text = dateFormat.format(Date(note.timestamp))
            
            // 长按删除
            itemView.setOnLongClickListener {
                onNoteDelete?.invoke(note)
                true
            }
        }
    }
    
    class NoteDiffCallback : DiffUtil.ItemCallback<NoteItem>() {
        override fun areItemsTheSame(oldItem: NoteItem, newItem: NoteItem): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: NoteItem, newItem: NoteItem): Boolean {
            return oldItem == newItem
        }
    }
}
