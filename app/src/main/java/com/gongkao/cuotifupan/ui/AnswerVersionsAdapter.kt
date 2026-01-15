package com.gongkao.cuotifupan.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.api.AnswerVersion

/**
 * 答案版本列表适配器
 */
class AnswerVersionsAdapter : ListAdapter<AnswerVersion, AnswerVersionsAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_answer_version, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val sourceNameTextView: TextView = itemView.findViewById(R.id.sourceNameTextView)
        private val sourceTypeTextView: TextView = itemView.findViewById(R.id.sourceTypeTextView)
        private val answerTextView: TextView = itemView.findViewById(R.id.answerTextView)
        private val explanationTextView: TextView = itemView.findViewById(R.id.explanationTextView)
        private val confidenceTextView: TextView = itemView.findViewById(R.id.confidenceTextView)
        private val preferredBadge: View = itemView.findViewById(R.id.preferredBadge)
        
        fun bind(answerVersion: AnswerVersion) {
            sourceNameTextView.text = answerVersion.sourceName
            sourceTypeTextView.text = answerVersion.sourceType
            answerTextView.text = "答案: ${answerVersion.answer}"
            explanationTextView.text = answerVersion.explanation
            confidenceTextView.text = "置信度: ${String.format("%.0f", answerVersion.confidence * 100)}%"
            
            // 显示用户偏好标记
            preferredBadge.visibility = if (answerVersion.isUserPreferred) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
    }
    
    class DiffCallback : DiffUtil.ItemCallback<AnswerVersion>() {
        override fun areItemsTheSame(oldItem: AnswerVersion, newItem: AnswerVersion): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: AnswerVersion, newItem: AnswerVersion): Boolean {
            return oldItem == newItem
        }
    }
}

