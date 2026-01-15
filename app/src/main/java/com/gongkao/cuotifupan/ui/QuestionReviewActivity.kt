package com.gongkao.cuotifupan.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.viewmodel.QuestionViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

/**
 * 题目复习页面
 */
class QuestionReviewActivity : AppCompatActivity() {
    
    private lateinit var viewModel: QuestionViewModel
    private lateinit var viewPager: ViewPager2
    private lateinit var questionAdapter: QuestionPagerAdapter
    private lateinit var masteredButton: Button
    private lateinit var notMasteredButton: Button
    private lateinit var statusText: TextView
    private lateinit var filterByTimeButton: Button
    private lateinit var filterByTagButton: Button
    private lateinit var selectedFilterText: TextView
    
    private var allQuestions: List<Question> = emptyList()
    private var currentPosition: Int = 0
    private var selectedTimeFilter: Long? = null  // 时间戳（毫秒）
    private var selectedTag: String? = null
    private var currentFilterType: FilterType? = null  // 当前选择的筛选类型
    
    enum class FilterType {
        TIME, TAG
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_question_review)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "题目复习"
        
        viewModel = ViewModelProvider(this)[QuestionViewModel::class.java]
        
        viewPager = findViewById(R.id.questionViewPager)
        masteredButton = findViewById(R.id.masteredButton)
        notMasteredButton = findViewById(R.id.notMasteredButton)
        statusText = findViewById(R.id.statusText)
        filterByTimeButton = findViewById(R.id.filterByTimeButton)
        filterByTagButton = findViewById(R.id.filterByTagButton)
        selectedFilterText = findViewById(R.id.selectedFilterText)
        
        // 设置 ViewPager
        questionAdapter = QuestionPagerAdapter(
            onQuestionClick = { question ->
                // 点击题目跳转到详情页
                val intent = Intent(this, QuestionDetailCardActivity::class.java)
                intent.putExtra("question_id", question.id)
                startActivity(intent)
            }
        )
        viewPager.adapter = questionAdapter
        viewPager.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        
        // 监听页面变化
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updateStatus()
            }
        })
        
        // 初始化筛选按钮
        updateFilterButtons()
        
        // 筛选按钮点击事件
        filterByTimeButton.setOnClickListener {
            if (currentFilterType == FilterType.TIME) {
                // 如果已经选择了时间筛选，清除选择
                currentFilterType = null
                selectedTimeFilter = null
                selectedTag = null
            } else {
                // 切换到时间筛选
                currentFilterType = FilterType.TIME
                selectedTag = null
                showTimeFilterBottomSheet()
            }
            updateFilterButtons()
            loadUnreviewedQuestions()
        }
        
        filterByTagButton.setOnClickListener {
            if (currentFilterType == FilterType.TAG) {
                // 如果已经选择了标签筛选，清除选择
                currentFilterType = null
                selectedTag = null
                selectedTimeFilter = null
            } else {
                // 切换到标签筛选
                currentFilterType = FilterType.TAG
                selectedTimeFilter = null
                showTagFilterBottomSheet()
            }
            updateFilterButtons()
            loadUnreviewedQuestions()
        }
        
        // 加载未复盘的题目
        loadUnreviewedQuestions()
        
        // 按钮点击事件
        masteredButton.setOnClickListener {
            markCurrentQuestion("mastered")
        }
        
        notMasteredButton.setOnClickListener {
            markCurrentQuestion("not_mastered")
        }
    }
    
    private fun updateFilterButtons() {
        // 更新按钮状态
        filterByTimeButton.isSelected = currentFilterType == FilterType.TIME
        filterByTagButton.isSelected = currentFilterType == FilterType.TAG
        
        // 更新选中文本显示
        val filterText = when (currentFilterType) {
            FilterType.TIME -> {
                val timeText = when (selectedTimeFilter) {
                    null -> "全部时间"
                    getTodayStartTime() -> "今天"
                    getDaysAgoTime(3) -> "最近3天"
                    getDaysAgoTime(7) -> "最近7天"
                    getDaysAgoTime(30) -> "最近30天"
                    else -> "已选择时间"
                }
                "时间：$timeText"
            }
            FilterType.TAG -> {
                val tagText = selectedTag ?: "全部标签"
                "标签：$tagText"
            }
            null -> ""
        }
        selectedFilterText.text = filterText
    }
    
    private fun showTimeFilterBottomSheet() {
        val bottomSheetView = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_filter, null)
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)
        
        val titleText = bottomSheetView.findViewById<TextView>(R.id.bottomSheetTitle)
        val recyclerView = bottomSheetView.findViewById<RecyclerView>(R.id.optionsRecyclerView)
        
        titleText.text = "选择时间范围"
        
        val timeOptions = listOf(
            "全部" to null,
            "今天" to getTodayStartTime(),
            "最近3天" to getDaysAgoTime(3),
            "最近7天" to getDaysAgoTime(7),
            "最近30天" to getDaysAgoTime(30)
        )
        
        // 先确定当前选中项
        val currentSelected = timeOptions.find { it.second == selectedTimeFilter }?.first ?: "全部"
        
        val adapter = FilterChipAdapter(timeOptions.map { it.first }) { selectedText ->
            selectedTimeFilter = timeOptions.find { it.first == selectedText }?.second
            updateFilterButtons()
            loadUnreviewedQuestions()
            bottomSheetDialog.dismiss()
        }
        
        // 设置当前选中项（在创建 adapter 之后）
        adapter.setSelectedItem(currentSelected)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
        
        bottomSheetDialog.show()
    }
    
    private fun showTagFilterBottomSheet() {
        lifecycleScope.launch {
            val allTags = withContext(Dispatchers.IO) {
                val database = com.gongkao.cuotifupan.data.AppDatabase.getDatabase(this@QuestionReviewActivity)
                val questions = database.questionDao().getAllQuestionsSync()
                    .filter { it.reviewState == "unreviewed" }
                
                // 获取所有未复习题目的标签
                val tags = mutableSetOf<String>()
                questions.forEach { question ->
                    tags.addAll(TagManager.parseTags(question.tags))
                }
                tags.toList().sorted()
            }
            
            val bottomSheetView = LayoutInflater.from(this@QuestionReviewActivity).inflate(R.layout.bottom_sheet_filter, null)
            val bottomSheetDialog = BottomSheetDialog(this@QuestionReviewActivity)
            bottomSheetDialog.setContentView(bottomSheetView)
            
            val titleText = bottomSheetView.findViewById<TextView>(R.id.bottomSheetTitle)
            val recyclerView = bottomSheetView.findViewById<RecyclerView>(R.id.optionsRecyclerView)
            
            titleText.text = "选择标签"
            
            val tagOptions = mutableListOf("全部标签")
            tagOptions.addAll(allTags)
            
            // 先确定当前选中项
            val currentSelected = selectedTag ?: "全部标签"
            
            val adapter = FilterChipAdapter(tagOptions) { selectedText ->
                if (selectedText == "全部标签") {
                    selectedTag = null
                } else {
                    selectedTag = selectedText
                }
                updateFilterButtons()
                loadUnreviewedQuestions()
                bottomSheetDialog.dismiss()
            }
            
            // 设置当前选中项（在创建 adapter 之后）
            adapter.setSelectedItem(currentSelected)
            
            recyclerView.layoutManager = LinearLayoutManager(this@QuestionReviewActivity)
            recyclerView.adapter = adapter
            
            bottomSheetDialog.show()
        }
    }
    
    private fun getTodayStartTime(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun getDaysAgoTime(days: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -days)
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
    
    private fun loadUnreviewedQuestions() {
        viewModel.getQuestionsByReviewState("unreviewed").observe(this) { questions ->
            // 根据筛选条件过滤题目（时间和标签互斥）
            var filtered = questions
            
            when (currentFilterType) {
                FilterType.TIME -> {
                    // 按时间筛选
                    if (selectedTimeFilter != null) {
                        filtered = filtered.filter { it.createdAt >= selectedTimeFilter!! }
                    }
                    // 按时间排序（最新的在前）
                    filtered = filtered.sortedByDescending { it.createdAt }
                }
                FilterType.TAG -> {
                    // 按标签筛选
                    if (selectedTag != null) {
                        filtered = filtered.filter { question ->
                            TagManager.parseTags(question.tags).contains(selectedTag)
                        }
                    }
                    // 按第一个标签排序，相同标签的按时间排序
                    filtered = filtered.sortedWith(compareBy<Question> { TagManager.getFirstTag(it.tags) }
                        .thenByDescending { it.createdAt })
                }
                null -> {
                    // 没有筛选，按时间排序（最新的在前）
                    filtered = filtered.sortedByDescending { it.createdAt }
                }
            }
            
            val sortedQuestions = filtered
            
            allQuestions = sortedQuestions
            questionAdapter.submitList(sortedQuestions)
            
            if (sortedQuestions.isEmpty()) {
                val emptyMessage = when (currentFilterType) {
                    FilterType.TAG -> {
                        if (selectedTag != null) "标签「$selectedTag」下没有未复盘的题目"
                        else "没有未复盘的题目"
                    }
                    FilterType.TIME -> {
                        if (selectedTimeFilter != null) "选择的时间范围内没有未复盘的题目"
                        else "没有未复盘的题目"
                    }
                    null -> "没有未复盘的题目"
                }
                statusText.text = emptyMessage
                masteredButton.isEnabled = false
                notMasteredButton.isEnabled = false
            } else {
                if (currentPosition >= sortedQuestions.size) {
                    currentPosition = 0
                }
                viewPager.setCurrentItem(currentPosition, false)
                updateStatus()
                masteredButton.isEnabled = true
                notMasteredButton.isEnabled = true
            }
        }
    }
    
    private fun updateStatus() {
        if (allQuestions.isNotEmpty()) {
            statusText.text = "${currentPosition + 1} / ${allQuestions.size}"
        }
    }
    
    private fun markCurrentQuestion(state: String) {
        if (currentPosition >= allQuestions.size) return
        
        val question = allQuestions[currentPosition]
        
        lifecycleScope.launch {
            val updatedQuestion = question.copy(reviewState = state)
            viewModel.update(updatedQuestion)
            
            Toast.makeText(
                this@QuestionReviewActivity,
                if (state == "mastered") "已标记为已掌握" else "已标记为未掌握",
                Toast.LENGTH_SHORT
            ).show()
            
            // 从列表中移除已复盘的题目
            val updatedList = allQuestions.toMutableList()
            updatedList.removeAt(currentPosition)
            allQuestions = updatedList
            questionAdapter.submitList(updatedList)
            
            // 自动切换到下一张
            if (updatedList.isNotEmpty()) {
                // 如果当前是最后一张，切换到前一张
                if (currentPosition >= updatedList.size) {
                    currentPosition = updatedList.size - 1
                }
                // 如果列表还有题目，切换到当前位置（因为前面的被移除了，当前位置就是下一张）
                viewPager.setCurrentItem(currentPosition, true)
                updateStatus()
            } else {
                statusText.text = "所有题目已复盘完成"
                masteredButton.isEnabled = false
                notMasteredButton.isEnabled = false
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
    
    /**
     * 筛选芯片适配器（用于显示可点击的筛选选项）
     */
    private class FilterChipAdapter(
        private val items: List<String>,
        private val onItemClick: (String) -> Unit
    ) : RecyclerView.Adapter<FilterChipAdapter.ViewHolder>() {
        
        private var selectedItem: String? = null
        
        fun setSelectedItem(item: String) {
            val oldPosition = if (selectedItem != null) items.indexOf(selectedItem) else -1
            selectedItem = item
            val newPosition = items.indexOf(item)
            
            if (oldPosition >= 0 && oldPosition < items.size) {
                notifyItemChanged(oldPosition)
            }
            if (newPosition >= 0 && newPosition < items.size) {
                notifyItemChanged(newPosition)
            }
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_tag_chip, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val isSelected = item == selectedItem
            holder.bind(item, isSelected) {
                onItemClick(item)
            }
        }
        
        override fun getItemCount(): Int = items.size
        
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tagText: TextView = itemView.findViewById(R.id.tagText)
            
            fun bind(text: String, isSelected: Boolean, onClick: () -> Unit) {
                tagText.text = text
                
                // 设置选中状态：选中时不透明，未选中时半透明
                if (isSelected) {
                    itemView.alpha = 1.0f
                    itemView.setBackgroundResource(R.drawable.bg_question_type)
                    tagText.setTextColor(android.graphics.Color.WHITE)
                } else {
                    itemView.alpha = 0.5f
                    itemView.setBackgroundResource(R.drawable.bg_question_type)
                    tagText.setTextColor(android.graphics.Color.WHITE)
                }
                
                itemView.setOnClickListener { onClick() }
            }
        }
    }
}

