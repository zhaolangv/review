package com.gongkao.cuotifupan.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.NoteItem
import com.gongkao.cuotifupan.data.Question
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 题目列表适配器（支持按日期分组显示）
 */
class QuestionAdapter(
    private val onItemClick: (Question) -> Unit,
    private val onEditTags: (Question) -> Unit,
    private val onDelete: (Question) -> Unit,
    private var isBatchMode: Boolean = false,
    private val selectedQuestions: MutableSet<String> = mutableSetOf(),
    private val onSelectionChanged: ((Int) -> Unit)? = null,
    private var isTimeSortMode: Boolean = false
) : ListAdapter<QuestionAdapter.ListItem, RecyclerView.ViewHolder>(ListItemDiffCallback()) {
    
    companion object {
        private const val TYPE_DATE_HEADER = 0
        private const val TYPE_QUESTION = 1
    }
    
    /**
     * 列表项类型：日期标题或题目
     */
    sealed class ListItem {
        data class DateHeader(val dateText: String, val dateKey: String) : ListItem()
        data class QuestionItem(val question: Question) : ListItem()
    }
    
    fun setBatchMode(enabled: Boolean) {
        isBatchMode = enabled
        if (!enabled) {
            selectedQuestions.clear()
            onSelectionChanged?.invoke(0)
        }
        notifyDataSetChanged()
    }
    
    fun setTimeSortMode(enabled: Boolean) {
        isTimeSortMode = enabled
        notifyDataSetChanged()
    }
    
    fun getSelectedCount(): Int = selectedQuestions.size
    
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is ListItem.DateHeader -> TYPE_DATE_HEADER
            is ListItem.QuestionItem -> TYPE_QUESTION
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_header, parent, false)
                DateHeaderViewHolder(view)
            }
            TYPE_QUESTION -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_question, parent, false)
                QuestionViewHolder(view)
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
            is ListItem.QuestionItem -> {
                if (holder is QuestionViewHolder) {
                    holder.bind(item.question, onItemClick, onEditTags, onDelete, isBatchMode, selectedQuestions, onSelectionChanged, isTimeSortMode)
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
    
    /**
     * 题目 ViewHolder
     */
    class QuestionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.questionThumbnail)
        private val tagsRecyclerView: RecyclerView = itemView.findViewById(R.id.tagsRecyclerView)
        private val notesPreviewText: TextView = itemView.findViewById(R.id.notesPreviewText)
        private val timeText: TextView = itemView.findViewById(R.id.timeText)
        private val reviewStateText: TextView = itemView.findViewById(R.id.reviewStateText)
        private val questionTypeText: TextView = itemView.findViewById(R.id.questionTypeText)
        private val selectionCheckBox: CheckBox = itemView.findViewById(R.id.selectionCheckBox)
        private val decorImageView: ImageView = itemView.findViewById(R.id.decorImageView)
        
        fun bind(question: Question, onItemClick: (Question) -> Unit, onEditTags: (Question) -> Unit, onDelete: (Question) -> Unit, isBatchMode: Boolean, selectedQuestions: MutableSet<String>, onSelectionChanged: ((Int) -> Unit)?, isTimeSortMode: Boolean) {
            // 加载缩略图（使用 ImageAccessHelper 兼容 Android 10+）
            val context = itemView.context
            val file = File(question.imagePath)
            
            // 如果是应用私有文件或公共存储目录的文件，直接使用 Coil 加载
            if (file.exists() && (question.imagePath.startsWith(context.filesDir.absolutePath) || 
                question.imagePath.startsWith(context.cacheDir.absolutePath) ||
                question.imagePath.contains("/DCIM/Camera/"))) {
                // 使用文件 URI，Coil 可以处理公共存储目录的文件
                thumbnail.load(file)
            } else if (com.gongkao.cuotifupan.util.ImageAccessHelper.isValidImage(context, question.imagePath)) {
                // 使用 ImageAccessHelper 加载（兼容 MediaStore URI）
                val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(context, question.imagePath)
                if (bitmap != null) {
                    thumbnail.setImageBitmap(bitmap)
                } else {
                    thumbnail.setImageResource(android.R.drawable.ic_menu_report_image)
                }
            } else {
                // 图片文件不存在，显示占位图
                thumbnail.setImageResource(android.R.drawable.ic_menu_report_image)
            }
            
            // 解析标签
            val tags = try {
                if (question.tags.isNotBlank()) {
                    org.json.JSONArray(question.tags).let { array ->
                        (0 until array.length()).map { array.getString(it) }
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
            
            // 直接使用保存的标签，不自动添加类型标签
            val allTags = tags.filter { it != "__NO_TYPE_TAG__" }.toMutableList() // 移除特殊标记（如果存在）
            
            // 显示标签（支持"增加标签"按钮）
            val tagsAdapter = TagChipAdapter(
                onAddTagClick = {
                    onEditTags(question)
                }
            )
            tagsRecyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                itemView.context,
                androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                false
            )
            tagsRecyclerView.adapter = tagsAdapter
            tagsAdapter.setTags(allTags)
            
            // 显示笔记（每题只显示一个笔记，只显示笔记类型，不显示记忆卡片）
            val notes = parseNotes(question.userNotes)
            val noteItems = notes.filter { it.type == "note" } // 只显示笔记类型
            if (noteItems.isNotEmpty()) {
                // 只显示第一个笔记
                val firstNote = noteItems.first()
                notesPreviewText.text = "笔记：${firstNote.content}"
                notesPreviewText.visibility = View.VISIBLE
            } else {
                // 无笔记时只显示"无笔记"
                notesPreviewText.text = "无笔记"
                notesPreviewText.visibility = View.VISIBLE
            }
            
            // 显示时间（按时间排序时显示详细日期）
            if (isTimeSortMode) {
                // 按时间排序时，显示详细日期
                val questionDate = Date(question.createdAt)
                val today = Calendar.getInstance()
                val questionCalendar = Calendar.getInstance().apply {
                    time = questionDate
                }
                
                val dateText = when {
                    // 今天
                    questionCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    questionCalendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> {
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        "今天 ${timeFormat.format(questionDate)}"
                    }
                    // 昨天
                    questionCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                    questionCalendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) - 1 -> {
                        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                        "昨天 ${timeFormat.format(questionDate)}"
                    }
                    // 今年
                    questionCalendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) -> {
                        val dateFormat = SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault())
                        dateFormat.format(questionDate)
                    }
                    // 其他年份
                    else -> {
                        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 HH:mm", Locale.getDefault())
                        dateFormat.format(questionDate)
                    }
                }
                timeText.text = dateText
            } else {
                // 非时间排序时，显示简短时间
                val dateFormat = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                timeText.text = dateFormat.format(Date(question.createdAt))
            }
            
            // 显示复盘状态
            reviewStateText.text = when (question.reviewState) {
                "mastered" -> "✓ 已掌握"
                "not_mastered" -> "✗ 未掌握"
                else -> "未复盘"
            }
            
            // 根据复盘状态设置不同的装饰图片
            when (question.reviewState) {
                "mastered" -> {
                    // 已掌握：使用不同的装饰图片
                    decorImageView.setImageResource(R.drawable.decor_question_mastered)
                    decorImageView.clearColorFilter()
                    decorImageView.alpha = 0.6f
                }
                "not_mastered" -> {
                    // 未掌握：使用不同的装饰图片
                    decorImageView.setImageResource(R.drawable.decor_question_not_mastered)
                    decorImageView.clearColorFilter()
                    decorImageView.alpha = 0.6f
                }
                else -> {
                    // 未复盘：使用原装饰图片
                    decorImageView.setImageResource(R.drawable.decor_question_chiikawa)
                    decorImageView.clearColorFilter()
                    decorImageView.alpha = 0.6f
                }
            }
            
            // 隐藏题目类型TextView（因为已经作为标签显示了）
            questionTypeText.visibility = View.GONE
            
            // 设置选择框（批量模式）
            if (isBatchMode) {
                selectionCheckBox.visibility = View.VISIBLE
                selectionCheckBox.isChecked = selectedQuestions.contains(question.id)
                selectionCheckBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        selectedQuestions.add(question.id)
                    } else {
                        selectedQuestions.remove(question.id)
                    }
                    onSelectionChanged?.invoke(selectedQuestions.size)
                }
            } else {
                selectionCheckBox.visibility = View.GONE
            }
            
            // 设置点击事件
            itemView.setOnClickListener {
                if (isBatchMode) {
                    // 批量模式：切换选择状态
                    selectionCheckBox.isChecked = !selectionCheckBox.isChecked
                } else {
                    // 普通模式：跳转到详情页
                onItemClick(question)
                }
            }
            
            // 设置长按事件：显示删除对话框
            itemView.setOnLongClickListener {
                if (!isBatchMode) {
                    // 显示删除确认对话框
                    androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                        .setTitle("删除题目")
                        .setMessage("确定要删除这道题目吗？此操作不可恢复。")
                        .setPositiveButton("删除") { _, _ ->
                            onDelete(question)
                        }
                        .setNegativeButton("取消", null)
                        .show()
                    true
                } else {
                    false
                }
            }
        }
        
        private fun parseNotes(notesJson: String): List<NoteItem> {
            return try {
                if (notesJson.isNotBlank()) {
                    org.json.JSONArray(notesJson).let { array ->
                        (0 until array.length()).map { index ->
                            val noteObj = array.getJSONObject(index)
                            val type = noteObj.optString("type", "note")
                            // 向后兼容：如果没有 type 字段，检查是否有 content 字段
                            if (type == "note" && !noteObj.has("type") && noteObj.has("content")) {
                                NoteItem(
                                    id = noteObj.optString("id", index.toString()),
                                    type = "note",
                                    content = noteObj.optString("content", ""),
                                    front = "",
                                    back = "",
                                    timestamp = noteObj.optLong("timestamp", System.currentTimeMillis())
                                )
                            } else {
                                NoteItem(
                                    id = noteObj.optString("id", index.toString()),
                                    type = type,
                                    content = noteObj.optString("content", ""),
                                    front = noteObj.optString("front", ""),
                                    back = noteObj.optString("back", ""),
                                    timestamp = noteObj.optLong("timestamp", System.currentTimeMillis())
                                )
                            }
                        }
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                // 如果不是JSON格式，当作单条笔记处理
                if (notesJson.isNotBlank()) {
                    listOf(NoteItem(
                        id = "0",
                        type = "note",
                        content = notesJson,
                        front = "",
                        back = "",
                        timestamp = System.currentTimeMillis()
                    ))
                } else {
                    emptyList()
                }
            }
        }
    }
    
    class ListItemDiffCallback : DiffUtil.ItemCallback<ListItem>() {
        override fun areItemsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return when {
                oldItem is ListItem.DateHeader && newItem is ListItem.DateHeader -> 
                    oldItem.dateKey == newItem.dateKey
                oldItem is ListItem.QuestionItem && newItem is ListItem.QuestionItem -> 
                    oldItem.question.id == newItem.question.id
                else -> false
            }
        }
        
        override fun areContentsTheSame(oldItem: ListItem, newItem: ListItem): Boolean {
            return oldItem == newItem
        }
    }
}
