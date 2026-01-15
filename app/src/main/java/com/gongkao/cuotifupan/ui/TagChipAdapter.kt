package com.gongkao.cuotifupan.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R

/**
 * 标签芯片适配器
 * 支持显示标签和"增加标签"按钮
 */
class TagChipAdapter(
    private val onAddTagClick: (() -> Unit)? = null
) : ListAdapter<TagChipAdapter.TagItem, RecyclerView.ViewHolder>(TagDiffCallback()) {
    
    companion object {
        const val TYPE_TAG = 0
        const val TYPE_ADD = 1
    }
    
    sealed class TagItem {
        data class Tag(val text: String) : TagItem()
        object AddButton : TagItem()
    }
    
    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TagItem.Tag -> TYPE_TAG
            is TagItem.AddButton -> TYPE_ADD
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tag_chip, parent, false)
        
        return when (viewType) {
            TYPE_ADD -> AddButtonViewHolder(view, onAddTagClick)
            else -> TagItemViewHolder(view)
        }
    }
    
    private var onTagClick: ((String) -> Unit)? = null
    
    fun setOnTagClickListener(listener: (String) -> Unit) {
        onTagClick = listener
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is TagItem.Tag -> {
                if (holder is TagItemViewHolder) {
                    holder.bind(item.text, onTagClick)
                }
            }
            is TagItem.AddButton -> {
                if (holder is AddButtonViewHolder) {
                    holder.bind()
                }
            }
        }
    }
    
    /**
     * 设置标签列表（自动添加"增加标签"按钮）
     */
    fun setTags(tags: List<String>) {
        val items: MutableList<TagItem> = tags.map { TagItem.Tag(it) }.toMutableList()
        if (onAddTagClick != null) {
            items.add(TagItem.AddButton)
    }
        submitList(items)
    }
    
    /**
     * 普通标签 ViewHolder
     */
    class TagItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tagText: TextView = itemView.findViewById(R.id.tagText)
        
        fun bind(tag: String, onTagClick: ((String) -> Unit)?) {
            tagText.text = if (tag.contains("-")) TagManager.formatTag(tag) else tag
            itemView.setOnClickListener {
                onTagClick?.invoke(tag)
            }
        }
    }
    
    /**
     * "增加标签"按钮 ViewHolder
     */
    class AddButtonViewHolder(
        itemView: View,
        private val onAddTagClick: (() -> Unit)?
    ) : RecyclerView.ViewHolder(itemView) {
        private val tagText: TextView = itemView.findViewById(R.id.tagText)
        
        fun bind() {
            tagText.text = "+ 增加标签"
            tagText.setTextColor(android.graphics.Color.WHITE)
            itemView.setOnClickListener {
                onAddTagClick?.invoke()
            }
        }
    }
    
    class TagDiffCallback : DiffUtil.ItemCallback<TagItem>() {
        override fun areItemsTheSame(oldItem: TagItem, newItem: TagItem): Boolean {
            return when {
                oldItem is TagItem.Tag && newItem is TagItem.Tag -> oldItem.text == newItem.text
                oldItem is TagItem.AddButton && newItem is TagItem.AddButton -> true
                else -> false
            }
        }
        
        override fun areContentsTheSame(oldItem: TagItem, newItem: TagItem): Boolean {
            return oldItem == newItem
        }
    }
}
