package com.gongkao.cuotifupan.ui.practice

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.ui.practice.MathQuestionGenerator.PracticeType

/**
 * 数学练习主界面 Fragment
 */
class MathPracticeFragment : Fragment() {
    
    private lateinit var practiceTypeContainer: LinearLayout
    private lateinit var questionCountEditText: EditText
    private lateinit var startPracticeButton: Button
    private lateinit var viewHistoryButton: Button
    
    private var selectedPracticeType: PracticeType? = null
    private var questionCount: Int = 10
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(false)  // 不显示菜单
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        try {
            android.util.Log.d("MathPracticeFragment", "开始创建视图")
            val view = inflater.inflate(R.layout.fragment_math_practice, container, false)
            android.util.Log.d("MathPracticeFragment", "视图创建成功")
            return view
        } catch (e: Exception) {
            android.util.Log.e("MathPracticeFragment", "创建视图失败", e)
            e.printStackTrace()
            return null
        }
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        try {
            (activity as? AppCompatActivity)?.supportActionBar?.title = "数学练习"
            
            initViews(view)
            setupPracticeTypeButtons()
        } catch (e: Exception) {
            android.util.Log.e("MathPracticeFragment", "初始化失败", e)
            e.printStackTrace()
        }
    }
    
    private fun initViews(view: View) {
        try {
            android.util.Log.d("MathPracticeFragment", "初始化视图")
            practiceTypeContainer = view.findViewById(R.id.practiceTypeContainer)
            questionCountEditText = view.findViewById(R.id.questionCountEditText)
            startPracticeButton = view.findViewById(R.id.startPracticeButton)
            viewHistoryButton = view.findViewById(R.id.viewHistoryButton)
            android.util.Log.d("MathPracticeFragment", "视图初始化成功")
            
            // 确保按钮可点击
            startPracticeButton.isClickable = true
            startPracticeButton.isEnabled = true
            viewHistoryButton.isClickable = true
            viewHistoryButton.isEnabled = true
            
            android.util.Log.d("MathPracticeFragment", "按钮状态 - startPracticeButton: clickable=${startPracticeButton.isClickable}, enabled=${startPracticeButton.isEnabled}")
            android.util.Log.d("MathPracticeFragment", "按钮状态 - viewHistoryButton: clickable=${viewHistoryButton.isClickable}, enabled=${viewHistoryButton.isEnabled}")
        } catch (e: Exception) {
            android.util.Log.e("MathPracticeFragment", "初始化视图失败", e)
            e.printStackTrace()
            throw e
        }
        
        questionCountEditText.setText(questionCount.toString())
        
        startPracticeButton.setOnClickListener {
            android.util.Log.d("MathPracticeFragment", "开始练习按钮被点击")
            if (selectedPracticeType == null) {
                android.util.Log.d("MathPracticeFragment", "未选择练习类型")
                Toast.makeText(requireContext(), "请选择练习类型", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            val countText = questionCountEditText.text.toString()
            val count = countText.toIntOrNull() ?: 10
            if (count <= 0 || count > 100) {
                android.util.Log.d("MathPracticeFragment", "题目数量无效: $count")
                Toast.makeText(requireContext(), "题目数量应在1-100之间", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            // 获取答题模式
            val answerModeRadioGroup = view.findViewById<android.widget.RadioGroup>(R.id.answerModeRadioGroup)
            val showAfterAll = answerModeRadioGroup.checkedRadioButtonId == R.id.modeShowAfterAllRadio
            
            android.util.Log.d("MathPracticeFragment", "启动练习: ${selectedPracticeType!!.name}, 数量: $count, 模式: ${if (showAfterAll) "全部做完后显示" else "每做一道就显示"}")
            try {
                startPractice(selectedPracticeType!!, count, showAfterAll)
                android.util.Log.d("MathPracticeFragment", "已调用startPractice")
            } catch (e: Exception) {
                android.util.Log.e("MathPracticeFragment", "启动练习失败", e)
                e.printStackTrace()
                Toast.makeText(requireContext(), "启动练习失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        viewHistoryButton.setOnClickListener {
            android.util.Log.d("MathPracticeFragment", "查看历史按钮被点击")
            try {
                val intent = Intent(requireContext(), MathPracticeHistoryActivity::class.java)
                startActivity(intent)
                android.util.Log.d("MathPracticeFragment", "历史Activity已启动")
            } catch (e: Exception) {
                android.util.Log.e("MathPracticeFragment", "启动历史Activity失败", e)
                e.printStackTrace()
                Toast.makeText(requireContext(), "打开历史记录失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun setupPracticeTypeButtons() {
        // 按类别组织练习类型
        val categories = mapOf(
            "增长相关" to listOf(
                PracticeType.ESTIMATE_PREVIOUS,
                PracticeType.ESTIMATE_GROWTH,
                PracticeType.PERCENTAGE_CALC,
                PracticeType.INCREMENT_COMPARE,
                PracticeType.BASE_PERIOD_COMPARE,
                PracticeType.ANNUAL_GROWTH_RATE
            ),
            "比重相关" to listOf(
                PracticeType.FRACTION_CALC_NUM_LESS,
                PracticeType.FRACTION_CALC_NUM_MORE,
                PracticeType.FRACTION_COMPARE,
                PracticeType.BASE_PERIOD_PROPORTION,
                PracticeType.ANNUAL_AVERAGE
            ),
            "基础运算" to listOf(
                PracticeType.TWO_DIGIT_ADD_SUB,
                PracticeType.THREE_DIGIT_ADD,
                PracticeType.THREE_DIGIT_SUB,
                PracticeType.THREE_DIGIT_ADD_SUB,
                PracticeType.MIXED_ADD_SUB,
                PracticeType.MULTI_NUMBER_ADD
            ),
            "乘法运算" to listOf(
                PracticeType.TWO_DIGIT_MUL_ONE,
                PracticeType.TWO_DIGIT_MUL_11,
                PracticeType.TWO_DIGIT_MUL_15,
                PracticeType.TWO_DIGIT_MUL_TWO,
                PracticeType.THREE_DIGIT_MUL_ONE,
                PracticeType.MULTIPLICATION_ESTIMATE
            ),
            "除法运算" to listOf(
                PracticeType.THREE_DIGIT_DIV_ONE,
                PracticeType.THREE_DIGIT_DIV_TWO,
                PracticeType.FIVE_DIGIT_DIV_THREE,
                PracticeType.THREE_DIGIT_DIV_FOUR
            ),
            "其他" to listOf(
                PracticeType.ROUND_TO_HUNDRED,
                PracticeType.COMMON_SQUARES
            )
        )
        
        // 获取练习类型列表容器（LinearLayout）
        val practiceTypeList = practiceTypeContainer.findViewById<LinearLayout>(R.id.practiceTypeList)
        
        android.util.Log.d("MathPracticeFragment", "查找练习类型列表容器:")
        android.util.Log.d("MathPracticeFragment", "  - practiceTypeContainer: ${practiceTypeContainer.javaClass.simpleName}")
        android.util.Log.d("MathPracticeFragment", "  - practiceTypeList: ${practiceTypeList?.javaClass?.simpleName}")
        android.util.Log.d("MathPracticeFragment", "  - practiceTypeList 是否为空: ${practiceTypeList == null}")
        
        if (practiceTypeList != null) {
            android.util.Log.d("MathPracticeFragment", "使用 practiceTypeList 添加练习类型按钮")
            categories.forEach { (categoryName, types) ->
                // 添加分类标题
                val categoryTitle = TextView(requireContext()).apply {
                    text = categoryName
                    textSize = 16f
                    setPadding(16, 16, 16, 8)
                }
                practiceTypeList.addView(categoryTitle)
                
                // 添加该分类下的练习类型按钮
                types.forEach { type ->
                    val button = Button(requireContext()).apply {
                        text = type.displayName
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(16, 8, 16, 8)
                        }
                        isClickable = true
                        isEnabled = true
                        setOnClickListener {
                            android.util.Log.d("MathPracticeFragment", "练习类型按钮被点击: ${type.displayName}")
                            selectedPracticeType = type
                            updateButtonSelection()
                            // 移除Toast，避免Toast队列过多
                            // Toast.makeText(requireContext(), "已选择: ${type.displayName}", Toast.LENGTH_SHORT).show()
                        }
                        tag = type
                    }
                    practiceTypeList.addView(button)
                }
            }
        } else {
            android.util.Log.w("MathPracticeFragment", "未找到 practiceTypeList，直接添加到 practiceTypeContainer")
            // 如果找不到LinearLayout，直接添加到 ConstraintLayout（虽然不理想，但可以工作）
            categories.forEach { (categoryName, types) ->
                // 添加分类标题
                val categoryTitle = TextView(requireContext()).apply {
                    text = categoryName
                    textSize = 16f
                    setPadding(16, 16, 16, 8)
                }
                practiceTypeContainer.addView(categoryTitle)
                
                // 添加该分类下的练习类型按钮
                types.forEach { type ->
                    val button = Button(requireContext()).apply {
                        text = type.displayName
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            setMargins(16, 8, 16, 8)
                        }
                        isClickable = true
                        isEnabled = true
                        setOnClickListener {
                            android.util.Log.d("MathPracticeFragment", "练习类型按钮被点击: ${type.displayName}")
                            selectedPracticeType = type
                            updateButtonSelection()
                        }
                        tag = type
                    }
                    practiceTypeContainer.addView(button)
                }
            }
        }
    }
    
    private fun updateButtonSelection() {
        // 获取练习类型列表容器（LinearLayout）
        val practiceTypeList = practiceTypeContainer.findViewById<LinearLayout>(R.id.practiceTypeList)
            ?: practiceTypeContainer as? LinearLayout
        
        val container = practiceTypeList ?: practiceTypeContainer
        for (i in 0 until container.childCount) {
            val view = container.getChildAt(i)
            if (view is Button && view.tag is PracticeType) {
                val type = view.tag as PracticeType
                val isSelected = (type == selectedPracticeType)
                view.isSelected = isSelected
                
                // 获取原始文字（去掉对钩）
                val originalText = type.displayName
                val currentText = view.text.toString()
                val textWithoutCheck = if (currentText.endsWith("√")) {
                    currentText.substring(0, currentText.length - 1)
                } else {
                    currentText
                }
                
                // 选中时在文字后面添加对钩，未选中时移除对钩
                if (isSelected) {
                    view.text = "$originalText√"
                    view.alpha = 1.0f
                } else {
                    view.text = originalText
                    view.alpha = 0.6f
                }
            }
        }
    }
    
    private fun startPractice(type: PracticeType, count: Int, showAfterAll: Boolean = true) {
        try {
            android.util.Log.d("MathPracticeFragment", "创建Intent: type=${type.name}, count=$count, showAfterAll=$showAfterAll")
            val intent = Intent(requireContext(), MathPracticeActivity::class.java).apply {
                putExtra("practice_type", type.name)
                putExtra("question_count", count)
                putExtra("show_after_all", showAfterAll)
            }
            android.util.Log.d("MathPracticeFragment", "启动Activity")
            startActivity(intent)
            android.util.Log.d("MathPracticeFragment", "Activity已启动")
        } catch (e: Exception) {
            android.util.Log.e("MathPracticeFragment", "启动Activity失败", e)
            e.printStackTrace()
            throw e
        }
    }
    
}

