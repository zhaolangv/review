package com.gongkao.cuotifupan.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.StandaloneFlashcard
import com.gongkao.cuotifupan.ui.TagManager
import com.gongkao.cuotifupan.util.SpacedRepetitionAlgorithm
import java.text.SimpleDateFormat
import java.util.*

/**
 * 独立记忆卡片列表适配器（支持按日期分组显示）
 */
class StandaloneFlashcardsAdapter(
    private val onItemClick: (StandaloneFlashcard) -> Unit,
    private val onDelete: (StandaloneFlashcard) -> Unit,
    private val onTagClick: (String) -> Unit,
    private val onQuestionClick: (StandaloneFlashcard) -> Unit,
    private val onEditTags: ((StandaloneFlashcard) -> Unit)? = null,
    private val onShowContent: ((StandaloneFlashcard) -> Unit)? = null
) : ListAdapter<StandaloneFlashcardsAdapter.ListItem, RecyclerView.ViewHolder>(StandaloneFlashcardsAdapter.ListItemDiffCallback()) {
    
    private var isBatchMode: Boolean = false
    private val selectedFlashcards = mutableSetOf<String>()
    private var onSelectionChanged: ((Int) -> Unit)? = null
    
    fun setBatchMode(enabled: Boolean) {
        isBatchMode = enabled
        if (!enabled) {
            selectedFlashcards.clear()
        }
        notifyDataSetChanged()
    }
    
    fun setOnSelectionChanged(callback: (Int) -> Unit) {
        onSelectionChanged = callback
    }
    
    fun getSelectedFlashcards(): Set<String> = selectedFlashcards.toSet()
    
    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_FLASHCARD = 1
    }
    
    /**
     * 列表项类型：日期标题或记忆卡片
     */
    sealed class ListItem {
        data class DateHeader(val dateText: String, val dateKey: String, val level: Int = 0) : ListItem()
        data class FlashcardItem(val flashcard: StandaloneFlashcard) : ListItem()
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ListItem.DateHeader -> TYPE_DATE_HEADER
            is ListItem.FlashcardItem -> TYPE_FLASHCARD
            else -> throw IllegalArgumentException("Unknown item type: ${item.javaClass}")
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            TYPE_FLASHCARD -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_standalone_flashcard, parent, false)
                FlashcardViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.DateHeader -> {
                if (holder is DateHeaderViewHolder) {
                    holder.bind(item.dateText, item.level)
                }
            }
            is ListItem.FlashcardItem -> {
                if (holder is FlashcardViewHolder) {
                    holder.bind(item.flashcard)
                }
            }
        }
    }
    
    /**
     * 日期标题 ViewHolder
     */
    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateHeaderText)
        private val density = itemView.context.resources.displayMetrics.density
        
        fun bind(dateText: String, level: Int = 0) {
            this.dateText.text = dateText
            // 根据层级设置缩进（每级缩进24dp，基础缩进16dp）
            val paddingStartDp = 16 + (level * 24)
            val paddingStartPx = (paddingStartDp * density).toInt()
            val paddingEndPx = (16 * density).toInt() // 保持右侧padding为16dp
            val paddingTopPx = (12 * density).toInt()
            val paddingBottomPx = (8 * density).toInt()
            
            this.dateText.setPadding(
                paddingStartPx,
                paddingTopPx,
                paddingEndPx,
                paddingBottomPx
            )
        }
    }
    
    inner class FlashcardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val frontText: TextView = itemView.findViewById(R.id.frontText)
        private val backText: TextView = itemView.findViewById(R.id.backText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val reviewInfoText: TextView = itemView.findViewById(R.id.reviewInfoText)
        private val tagsRecyclerView: RecyclerView = itemView.findViewById(R.id.tagsRecyclerView)
        private val gotoQuestionButton: TextView = itemView.findViewById(R.id.gotoQuestionButton)
        private val selectionCheckBox: CheckBox = itemView.findViewById(R.id.selectionCheckBox)
        
        fun bind(flashcard: StandaloneFlashcard) {
            // 设置选择框（批量模式）
            if (isBatchMode) {
                selectionCheckBox.visibility = View.VISIBLE
                selectionCheckBox.isChecked = selectedFlashcards.contains(flashcard.id)
                selectionCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedFlashcards.add(flashcard.id)
                    } else {
                        selectedFlashcards.remove(flashcard.id)
                    }
                    onSelectionChanged?.invoke(selectedFlashcards.size)
                }
            } else {
                selectionCheckBox.visibility = View.GONE
                selectionCheckBox.setOnCheckedChangeListener(null)
            }
            frontText.text = "提示：${flashcard.front}"
            backText.text = "内容：${flashcard.back}"
            timeText.text = formatTime(flashcard.updatedAt)
            
            // 显示间隔重复信息（Anki 风格）
            reviewInfoText.text = formatReviewInfo(flashcard)
            
            // 设置标签（与题目列表一致，支持"增加标签"按钮）
            val tags = TagManager.parseTags(flashcard.tags)
            val allTags = tags.filter { it != "__NO_TYPE_TAG__" }.toMutableList() // 移除特殊标记（如果存在）
            
            val tagAdapter = TagChipAdapter(
                onAddTagClick = onEditTags?.let { { it(flashcard) } }
            )
            tagAdapter.setTags(allTags)
            tagAdapter.setOnTagClickListener { tag ->
                onTagClick(tag)
            }
            tagsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                itemView.context,
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                false
            )
            tagsRecyclerView.adapter = tagAdapter
            tagsRecyclerView.visibility = View.VISIBLE
            
            // 如果有关联题目，显示跳转按钮
            if (flashcard.questionId != null) {
                gotoQuestionButton.visibility = View.VISIBLE
                gotoQuestionButton.setOnClickListener {
                    onQuestionClick(flashcard)
                }
            } else {
                gotoQuestionButton.visibility = View.GONE
            }
            
            // 点击卡片
            itemView.setOnClickListener {
                if (isBatchMode) {
                    // 批量模式：切换选择状态
                    selectionCheckBox.isChecked = !selectionCheckBox.isChecked
                } else {
                    // 正常模式：显示内容或跳转到编辑页面
                    onShowContent?.invoke(flashcard) ?: onItemClick(flashcard)
                }
            }
            
            // 长按删除
            itemView.setOnLongClickListener {
                if (!isBatchMode) {
                    onDelete(flashcard)
                }
                true
            }
        }
        
        private fun formatTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
        
        private fun formatReviewInfo(flashcard: StandaloneFlashcard): String {
            // 兼容旧数据：处理可能存在的旧状态值
            val normalizedState = when (flashcard.reviewState) {
                "new", "unreviewed", "not_reviewed" -> "new"
                "learning" -> "learning"
                "review", "mastered" -> "review"
                "relearning", "not_mastered" -> "relearning"
                else -> "new"  // 默认当作新卡片
            }
            
            val stateText = when (normalizedState) {
                "new" -> "新"
                "learning" -> "学习"
                "review" -> "复习"
                "relearning" -> "重学"
                else -> "新"
            }
            
            val infoParts = mutableListOf<String>()
            infoParts.add(stateText)
            
            // 显示复习次数
            if (flashcard.reviewCount > 0) {
                infoParts.add("${flashcard.reviewCount}次")
            }
            
            // 显示下次复习时间
            if (flashcard.nextReviewTime > 0) {
                val now = System.currentTimeMillis()
                val nextReviewText = if (flashcard.nextReviewTime <= now) {
                    "已到期"
                } else {
                    val days = (flashcard.nextReviewTime - now) / (24 * 60 * 60 * 1000)
                    when {
                        days == 0L -> "今天"
                        days == 1L -> "明天"
                        days < 7L -> "${days}天后"
                        days < 30L -> "${days / 7}周后"
                        else -> "${days / 30}月后"
                    }
                }
                infoParts.add(nextReviewText)
            }
            
            // 显示间隔（如果有）
            if (flashcard.interval > 0) {
                val intervalText = SpacedRepetitionAlgorithm.formatInterval(flashcard.interval)
                infoParts.add("间隔: $intervalText")
            }
            
            return infoParts.joinToString(" | ")
        }
    }
    
    class ListItemDiffCallback : DiffUtil.ItemCallback<StandaloneFlashcardsAdapter.ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return when {
                oldItem is ListItem.DateHeader && newItem is ListItem.DateHeader -> 
                    oldItem.dateKey == newItem.dateKey
                oldItem is ListItem.FlashcardItem && newItem is ListItem.FlashcardItem -> 
                    oldItem.flashcard.id == newItem.flashcard.id
                else -> false
            }
        }
        
        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem == newItem
        }
    }
}

