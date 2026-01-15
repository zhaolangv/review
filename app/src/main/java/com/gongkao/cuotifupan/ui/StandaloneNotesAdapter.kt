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
import com.gongkao.cuotifupan.data.StandaloneNote
import com.gongkao.cuotifupan.ui.TagManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * 独立笔记列表适配器（支持按日期分组显示）
 */
class StandaloneNotesAdapter(
    private val onItemClick: (StandaloneNote) -> Unit,
    private val onDelete: (StandaloneNote) -> Unit,
    private val onTagClick: (String) -> Unit,
    private val onQuestionClick: (StandaloneNote) -> Unit,
    private val onEditTags: ((StandaloneNote) -> Unit)? = null,
    private val onShowContent: ((StandaloneNote) -> Unit)? = null
) : ListAdapter<StandaloneNotesAdapter.ListItem, RecyclerView.ViewHolder>(StandaloneNotesAdapter.ListItemDiffCallback()) {
    
    private var isBatchMode: Boolean = false
    private val selectedNotes = mutableSetOf<String>()
    private var onSelectionChanged: ((Int) -> Unit)? = null
    
    fun setBatchMode(enabled: Boolean) {
        isBatchMode = enabled
        if (!enabled) {
            selectedNotes.clear()
        }
        notifyDataSetChanged()
    }
    
    fun setOnSelectionChanged(callback: (Int) -> Unit) {
        onSelectionChanged = callback
    }
    
    fun getSelectedNotes(): Set<String> = selectedNotes.toSet()
    
    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_NOTE = 1
    }
    
    /**
     * 列表项类型：日期标题或笔记
     */
    sealed class ListItem {
        data class DateHeader(val dateText: String, val dateKey: String) : ListItem()
        data class NoteItem(val note: StandaloneNote) : ListItem()
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ListItem.DateHeader -> TYPE_DATE_HEADER
            is ListItem.NoteItem -> TYPE_NOTE
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
            TYPE_NOTE -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_standalone_note, parent, false)
                NoteViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ListItem.DateHeader -> {
                if (holder is DateHeaderViewHolder) {
                    holder.bind(item.dateText)
                }
            }
            is ListItem.NoteItem -> {
                if (holder is NoteViewHolder) {
                    holder.bind(item.note)
                }
            }
        }
    }
    
    /**
     * 日期标题 ViewHolder
     */
    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.dateHeaderText)
        
        fun bind(dateText: String) {
            this.dateText.text = dateText
        }
    }
    
    inner class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val contentText: TextView = itemView.findViewById(R.id.noteContentText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val tagsRecyclerView: RecyclerView = itemView.findViewById(R.id.tagsRecyclerView)
        private val gotoQuestionButton: TextView = itemView.findViewById(R.id.gotoQuestionButton)
        private val selectionCheckBox: CheckBox = itemView.findViewById(R.id.selectionCheckBox)
        
        fun bind(note: StandaloneNote) {
            // 设置选择框（批量模式）
            if (isBatchMode) {
                selectionCheckBox.visibility = View.VISIBLE
                selectionCheckBox.isChecked = selectedNotes.contains(note.id)
                selectionCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedNotes.add(note.id)
                    } else {
                        selectedNotes.remove(note.id)
                    }
                    onSelectionChanged?.invoke(selectedNotes.size)
                }
            } else {
                selectionCheckBox.visibility = View.GONE
                selectionCheckBox.setOnCheckedChangeListener(null)
            }
            contentText.text = note.content
            timeText.text = formatTime(note.updatedAt)
            
            // 设置标签（与题目列表一致）
            val tags = TagManager.parseTags(note.tags)
            val allTags = tags.filter { it != "__NO_TYPE_TAG__" }.toMutableList() // 移除特殊标记（如果存在）
            
            // 显示标签（支持"增加标签"按钮，与题目列表一致）
            val tagAdapter = TagChipAdapter(
                onAddTagClick = onEditTags?.let { { it(note) } }
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
            if (note.questionId != null) {
                gotoQuestionButton.visibility = View.VISIBLE
                gotoQuestionButton.setOnClickListener {
                    onQuestionClick(note)
                }
            } else {
                gotoQuestionButton.visibility = View.GONE
            }
            
            // 点击笔记
            itemView.setOnClickListener {
                if (isBatchMode) {
                    // 批量模式：切换选择状态
                    selectionCheckBox.isChecked = !selectionCheckBox.isChecked
                } else {
                    // 正常模式：显示内容或跳转到编辑页面
                    onShowContent?.invoke(note) ?: onItemClick(note)
                }
            }
            
            // 长按删除
            itemView.setOnLongClickListener {
                if (!isBatchMode) {
                    onDelete(note)
                }
                true
            }
        }
        
        private fun formatTime(timestamp: Long): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }
    }
    
    class ListItemDiffCallback : DiffUtil.ItemCallback<StandaloneNotesAdapter.ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return when {
                oldItem is ListItem.DateHeader && newItem is ListItem.DateHeader -> 
                    oldItem.dateKey == newItem.dateKey
                oldItem is ListItem.NoteItem && newItem is ListItem.NoteItem -> 
                    oldItem.note.id == newItem.note.id
                else -> false
            }
        }
        
        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem == newItem
        }
    }
}

