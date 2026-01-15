package com.gongkao.cuotifupan.ui.practice

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R

/**
 * 练习结果适配器
 */
class MathPracticeResultAdapter(
    private val results: List<MathPracticeResultActivity.QuestionResult>
) : RecyclerView.Adapter<MathPracticeResultAdapter.ResultViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_math_practice_result, parent, false)
        return ResultViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(results[position], position + 1)
    }
    
    override fun getItemCount(): Int = results.size
    
    class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val questionText: TextView = itemView.findViewById(R.id.questionText)
        private val correctAnswerText: TextView = itemView.findViewById(R.id.correctAnswerText)
        private val userAnswerText: TextView = itemView.findViewById(R.id.userAnswerText)
        
        fun bind(result: MathPracticeResultActivity.QuestionResult, index: Int) {
            questionText.text = "$index. ${result.question}"
            correctAnswerText.text = "= ${formatNumber(result.correctAnswer)}"
            
            if (result.userAnswer != null) {
                userAnswerText.text = "${formatNumber(result.userAnswer)}${if (result.isCorrect) "✓" else "✗"}"
                userAnswerText.setTextColor(
                    if (result.isCorrect) {
                        itemView.context.getColor(android.R.color.holo_green_dark)
                    } else {
                        itemView.context.getColor(android.R.color.holo_red_dark)
                    }
                )
            } else {
                userAnswerText.text = "未作答"
                userAnswerText.setTextColor(itemView.context.getColor(android.R.color.darker_gray))
            }
        }
        
        private fun formatNumber(num: Double): String {
            return if (num % 1.0 == 0.0) {
                num.toInt().toString()
            } else {
                String.format("%.2f", num)
            }
        }
    }
}

