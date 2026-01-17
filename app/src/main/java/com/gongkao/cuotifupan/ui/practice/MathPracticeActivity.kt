package com.gongkao.cuotifupan.ui.practice

import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.animation.ScaleAnimation
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.inputmethod.InputMethodManager
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.MathPracticeSession
import com.gongkao.cuotifupan.ui.practice.MathQuestionGenerator.MathQuestion
import com.gongkao.cuotifupan.ui.practice.MathQuestionGenerator.PracticeType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 数学练习答题界面
 */
class MathPracticeActivity : AppCompatActivity() {
    
    private lateinit var questionNumberText: TextView
    private lateinit var timerText: TextView
    private lateinit var questionText: TextView
    private lateinit var answerInput: EditText
    private lateinit var hintText: TextView
    private lateinit var keyboardContainer: LinearLayout
    private var isUsingSystemKeyboard = false  // 键盘切换状态
    private lateinit var confirmButton: Button
    private lateinit var restartButton: Button
    private lateinit var clearButton: Button
    private lateinit var backspaceButton: Button
    
    // 比较按钮和选项按钮
    private lateinit var compareButtonsContainer: LinearLayout
    private lateinit var greaterButton: Button
    private lateinit var lessButton: Button
    private lateinit var optionButtonsContainer: LinearLayout
    private lateinit var optionAButton: Button
    private lateinit var optionBButton: Button
    
    private var practiceType: PracticeType? = null
    private var questions: List<MathQuestion> = emptyList()
    private var currentQuestionIndex: Int = 0
    private var userAnswers: MutableList<Pair<MathQuestion, Double?>> = mutableListOf()
    private var startTime: Long = 0
    private var timerHandler: Handler? = null
    private var timerRunnable: Runnable? = null
    private var elapsedSeconds: Int = 0
    private var currentSessionId: String? = null
    
    // 答题模式：true=全部做完后显示答案，false=每做一道就显示答案
    private var showAfterAll: Boolean = true
    
    // 当前选中的比较选项或选项
    private var selectedComparison: String? = null  // "greater" 或 "less"
    private var selectedOption: String? = null  // "A" 或 "B"
    
    // 保存按钮的原始文字（用于添加/移除对钩）
    private var greaterButtonOriginalText = "大于"
    private var lessButtonOriginalText = "小于"
    private var optionAButtonOriginalText = "A"
    private var optionBButtonOriginalText = "B"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("MathPracticeActivity", "onCreate开始")
        
        try {
            setContentView(R.layout.activity_math_practice)
            android.util.Log.d("MathPracticeActivity", "布局已设置")
            
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            
            val typeName = intent.getStringExtra("practice_type")
            val questionCount = intent.getIntExtra("question_count", 10)
            showAfterAll = intent.getBooleanExtra("show_after_all", true)
            android.util.Log.d("MathPracticeActivity", "接收参数: type=$typeName, count=$questionCount, showAfterAll=$showAfterAll")
            
            practiceType = PracticeType.valueOf(typeName ?: PracticeType.TWO_DIGIT_ADD_SUB.name)
            android.util.Log.d("MathPracticeActivity", "准备生成题目，类型: ${practiceType!!.name} (${practiceType!!.displayName}), 数量: $questionCount")
            questions = MathQuestionGenerator.generateQuestions(practiceType!!, questionCount)
            android.util.Log.d("MathPracticeActivity", "生成题目完成: ${questions.size}道")
            // 验证生成的题目类型是否正确
            questions.forEachIndexed { index, question ->
                if (question.practiceType != practiceType) {
                    android.util.Log.e("MathPracticeActivity", "⚠️ 题目类型不匹配！第${index + 1}题: 期望 ${practiceType!!.name}, 实际 ${question.practiceType.name}")
                }
            }
            
            initViews()
            setupKeyboard()
            startPractice()
            android.util.Log.d("MathPracticeActivity", "初始化完成")
        } catch (e: Exception) {
            android.util.Log.e("MathPracticeActivity", "onCreate失败", e)
            e.printStackTrace()
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    
    private fun initViews() {
        questionNumberText = findViewById(R.id.questionNumberText)
        timerText = findViewById(R.id.timerText)
        questionText = findViewById(R.id.questionText)
        answerInput = findViewById(R.id.answerInput)
        hintText = findViewById(R.id.hintText)
        keyboardContainer = findViewById(R.id.keyboardContainer)
        confirmButton = findViewById(R.id.confirmButton)
        restartButton = findViewById(R.id.restartButton)
        clearButton = findViewById(R.id.clearButton)
        backspaceButton = findViewById(R.id.backspaceButton)
        
        // 初始化比较按钮和选项按钮
        compareButtonsContainer = findViewById(R.id.compareButtonsContainer)
        greaterButton = findViewById(R.id.greaterButton)
        lessButton = findViewById(R.id.lessButton)
        optionButtonsContainer = findViewById(R.id.optionButtonsContainer)
        optionAButton = findViewById(R.id.optionAButton)
        optionBButton = findViewById(R.id.optionBButton)
        
        // 底部按钮（重开、清空、退格、数字键盘）保持默认样式，不设置特殊样式
        
        confirmButton.setOnClickListener { 
            android.util.Log.d("MathPracticeActivity", "确定按钮被点击")
            confirmAnswer() 
        }
        restartButton.setOnClickListener { 
            android.util.Log.d("MathPracticeActivity", "重开按钮被点击")
            restartPractice() 
        }
        
        clearButton.setOnClickListener {
            // 切换到自定义键盘
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(answerInput.windowToken, 0)
            findViewById<View>(R.id.keyboardWrapper)?.visibility = View.VISIBLE
            isUsingSystemKeyboard = false
            answerInput.clearFocus()
            
            android.util.Log.d("MathPracticeActivity", "清空按钮被点击")
            answerInput.setText("")
        }
        backspaceButton.setOnClickListener {
            // 切换到自定义键盘
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(answerInput.windowToken, 0)
            findViewById<View>(R.id.keyboardWrapper)?.visibility = View.VISIBLE
            isUsingSystemKeyboard = false
            answerInput.clearFocus()
            
            android.util.Log.d("MathPracticeActivity", "退格按钮被点击")
            val current = answerInput.text.toString()
            if (current.isNotEmpty()) {
                answerInput.setText(current.substring(0, current.length - 1))
            }
        }
        
        // 确保所有按钮可点击
        confirmButton.isClickable = true
        confirmButton.isEnabled = true
        restartButton.isClickable = true
        restartButton.isEnabled = true
        clearButton.isClickable = true
        clearButton.isEnabled = true
        backspaceButton.isClickable = true
        backspaceButton.isEnabled = true
        greaterButton.isClickable = true
        greaterButton.isEnabled = true
        lessButton.isClickable = true
        lessButton.isEnabled = true
        optionAButton.isClickable = true
        optionAButton.isEnabled = true
        optionBButton.isClickable = true
        optionBButton.isEnabled = true
        
        // 比较按钮点击事件
        greaterButton.setOnClickListener {
            selectedComparison = "greater"
            selectedOption = null
            updateButtonSelection()
            updateQuestionDisplay()  // 更新题目显示
            android.util.Log.d("MathPracticeActivity", "选择：大于")
        }
        
        lessButton.setOnClickListener {
            selectedComparison = "less"
            selectedOption = null
            updateButtonSelection()
            updateQuestionDisplay()  // 更新题目显示
            android.util.Log.d("MathPracticeActivity", "选择：小于")
        }
        
        // 选项按钮点击事件
        optionAButton.setOnClickListener {
            selectedOption = "A"
            selectedComparison = null
            updateButtonSelection()
            android.util.Log.d("MathPracticeActivity", "选择：A")
        }
        
        optionBButton.setOnClickListener {
            selectedOption = "B"
            selectedComparison = null
            updateButtonSelection()
            android.util.Log.d("MathPracticeActivity", "选择：B")
        }
        
        // 点击输入框：如果当前是自定义键盘，切换到系统键盘；如果当前是系统键盘，切换到自定义键盘
        answerInput.setOnClickListener {
            val keyboardWrapper = findViewById<View>(R.id.keyboardWrapper)
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            
            if (!isUsingSystemKeyboard) {
                // 当前是自定义键盘，切换到系统键盘
                answerInput.requestFocus()
                imm.showSoftInput(answerInput, InputMethodManager.SHOW_IMPLICIT)
                keyboardWrapper?.visibility = View.GONE
                isUsingSystemKeyboard = true
            } else {
                // 当前是系统键盘，切换到自定义键盘
                imm.hideSoftInputFromWindow(answerInput.windowToken, 0)
                keyboardWrapper?.visibility = View.VISIBLE
                isUsingSystemKeyboard = false
                answerInput.clearFocus()
            }
        }
        
        // 当输入框失去焦点时，如果使用系统键盘，切换回自定义键盘
        answerInput.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && isUsingSystemKeyboard) {
                val keyboardWrapper = findViewById<View>(R.id.keyboardWrapper)
                keyboardWrapper?.visibility = View.VISIBLE
                isUsingSystemKeyboard = false
            }
        }
        
        // 自定义键盘按钮点击时，确保使用自定义键盘（隐藏系统键盘）
        val keyboardWrapper = findViewById<View>(R.id.keyboardWrapper)
        // 为自定义键盘的所有按钮添加点击监听，确保点击时切换到自定义键盘
        // 这个逻辑已经在各个按钮的onClickListener中处理了，不需要额外处理
        
        // 初始化按钮样式（确保未选中状态显示灰色背景）
        updateButtonSelection()
    }
    
    private fun updateButtonSelection() {
        // 更新比较按钮的选中状态
        greaterButton.isSelected = (selectedComparison == "greater")
        lessButton.isSelected = (selectedComparison == "less")
        
        // 更新选项按钮的选中状态
        optionAButton.isSelected = (selectedOption == "A")
        optionBButton.isSelected = (selectedOption == "B")
        
        // 设置选中状态的背景色（使用OKLCH颜色，转换为RGB近似值）
        // 选中：oklch(0.55 0.15 250) -> 现代蓝色，RGB近似值: #5B8DEF
        // 未选中背景：灰色
        // 未选中文字：oklch(0.25 0.02 240) -> 深灰色，RGB近似值: #3D3D3D
        // 选中文字：oklch(0.99 0 0) -> 白色，RGB: #FFFFFF
        val selectedColor = android.graphics.Color.parseColor("#5B8DEF")  // oklch(0.55 0.15 250) 现代蓝色
        val unselectedBgColor = android.graphics.Color.parseColor("#9E9E9E")  // 灰色背景
        val unselectedTextColor = android.graphics.Color.parseColor("#3D3D3D")  // oklch(0.25 0.02 240) 深灰色
        val selectedTextColor = android.graphics.Color.parseColor("#FFFFFF")  // oklch(0.99 0 0) 白色
        val borderColor = android.graphics.Color.parseColor("#DCDCDC")  // 边框颜色
        
        // 更新比较按钮
        updateButtonStyle(greaterButton, greaterButton.isSelected, selectedColor, unselectedBgColor, borderColor, selectedTextColor, unselectedTextColor)
        updateButtonStyle(lessButton, lessButton.isSelected, selectedColor, unselectedBgColor, borderColor, selectedTextColor, unselectedTextColor)
        
        // 更新选项按钮
        updateButtonStyle(optionAButton, optionAButton.isSelected, selectedColor, unselectedBgColor, borderColor, selectedTextColor, unselectedTextColor)
        updateButtonStyle(optionBButton, optionBButton.isSelected, selectedColor, unselectedBgColor, borderColor, selectedTextColor, unselectedTextColor)
    }
    
    private fun updateQuestionDisplay() {
        val question = questions[currentQuestionIndex]
        // 如果是比较题，更新题目显示，将问号或"vs"替换为选中的符号，并设置不同样式
        if (question.practiceType == PracticeType.FRACTION_COMPARE ||
            question.practiceType == PracticeType.INCREMENT_COMPARE ||
            question.practiceType == PracticeType.BASE_PERIOD_COMPARE) {
            val originalQuestion = question.question
            
            val symbol = when (selectedComparison) {
                "greater" -> ">"
                "less" -> "<"
                else -> "?"
            }
            
            // 根据题目类型处理不同的格式
            val displayQuestion = when {
                // 分数比大小：包含问号
                originalQuestion.contains("?") -> originalQuestion.replace("?", symbol)
                // 增量比大小和基期比大小：包含"vs"
                originalQuestion.contains(" vs ") -> originalQuestion.replace(" vs ", " $symbol ")
                else -> originalQuestion  // 如果都不包含，保持原样
            }
            
            // 使用SpannableString来设置不同样式
            val spannable = SpannableString(displayQuestion)
            
            // 找到符号的位置
            val symbolIndex = displayQuestion.indexOf(symbol)
            if (symbolIndex >= 0) {
                // 设置符号的颜色和大小（更醒目）
                val symbolColor = when (selectedComparison) {
                    "greater" -> android.graphics.Color.parseColor("#2196F3")  // 蓝色
                    "less" -> android.graphics.Color.parseColor("#FF9800")  // 橙色
                    else -> android.graphics.Color.parseColor("#757575")  // 灰色
                }
                spannable.setSpan(
                    ForegroundColorSpan(symbolColor),
                    symbolIndex,
                    symbolIndex + symbol.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // 符号稍微大一点
                spannable.setSpan(
                    RelativeSizeSpan(1.3f),
                    symbolIndex,
                    symbolIndex + symbol.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                // 符号加粗
                spannable.setSpan(
                    android.text.style.StyleSpan(Typeface.BOLD),
                    symbolIndex,
                    symbolIndex + symbol.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            
            questionText.text = spannable
        }
    }
    
    private fun updateButtonStyle(button: Button, isSelected: Boolean, selectedColor: Int, unselectedBgColor: Int, borderColor: Int, selectedTextColor: Int, unselectedTextColor: Int) {
        // 获取按钮的原始文字
        val originalText = when (button.id) {
            R.id.greaterButton -> greaterButtonOriginalText
            R.id.lessButton -> lessButtonOriginalText
            R.id.optionAButton -> optionAButtonOriginalText
            R.id.optionBButton -> optionBButtonOriginalText
            else -> {
                val text = button.text.toString()
                if (text.endsWith("√")) text.substring(0, text.length - 1) else text
            }
        }
        
        // 判断是否是大于/小于按钮
        val isCompareButton = (button.id == R.id.greaterButton || button.id == R.id.lessButton)
        
        if (isCompareButton) {
            // 大于/小于按钮：字体颜色始终是白色，只改变背景色
            button.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))  // 始终白色
            button.setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            button.elevation = if (isSelected) 4f else 2f
            button.textSize = if (isSelected) 18f else 16f
            button.text = originalText  // 不添加对钩
            
            // 设置高度44px
            val heightInPx = (44 * resources.displayMetrics.density).toInt()
            button.minHeight = heightInPx
            button.layoutParams?.height = heightInPx
            
            // 清除可能存在的backgroundTint，确保使用自定义drawable
            button.backgroundTintList = null
            
            if (isSelected) {
                // 选中状态：现代蓝色背景
                val selectedDrawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(selectedColor)  // oklch(0.55 0.15 250) 现代蓝色 #5B8DEF
                    cornerRadius = 8f * resources.displayMetrics.density
                    // 无边框
                }
                button.setBackground(selectedDrawable)
                
                // 使用动画平滑过渡
                val scaleAnim = ScaleAnimation(
                    if (button.scaleX == 0f) 1.0f else button.scaleX, 
                    1.12f,
                    if (button.scaleY == 0f) 1.0f else button.scaleY, 
                    1.12f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f)
                scaleAnim.duration = 200
                scaleAnim.fillAfter = true
                button.startAnimation(scaleAnim)
            } else {
                // 未选中状态：灰色背景
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(unselectedBgColor)  // 灰色背景 #9E9E9E
                    setStroke((1 * resources.displayMetrics.density).toInt(), borderColor)  // 边框1px #DCDCDC
                    cornerRadius = 8f * resources.displayMetrics.density
                }
                button.setBackground(drawable)
                
                // 使用动画平滑过渡回正常大小
                val currentScale = if (button.scaleX == 0f) 1.0f else button.scaleX
                if (currentScale > 1.0f) {
                    val scaleAnim = ScaleAnimation(
                        currentScale, 1.0f,
                        if (button.scaleY == 0f) 1.0f else button.scaleY, 1.0f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f)
                    scaleAnim.duration = 200
                    scaleAnim.fillAfter = true
                    button.startAnimation(scaleAnim)
                }
            }
        } else {
            // 选项按钮（A/B）：使用原来的逻辑
            if (isSelected) {
                // 选中状态：oklch(0.55 0.15 250) 背景 + oklch(0.99 0 0) 文字
                button.setTextColor(selectedTextColor)  // oklch(0.99 0 0) 白色文字
                button.setTypeface(null, android.graphics.Typeface.BOLD)
                button.elevation = 4f
                button.textSize = 18f
                
                // 设置高度44px
                val heightInPx = (44 * resources.displayMetrics.density).toInt()
                button.minHeight = heightInPx
                button.layoutParams?.height = heightInPx
                
                // 选项按钮：添加对钩
                button.text = "$originalText√"
                
                // 选中状态：现代蓝色背景，无边框，圆角8px
                val selectedDrawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(selectedColor)  // oklch(0.55 0.15 250)
                    cornerRadius = 8f * resources.displayMetrics.density  // 圆角8px
                    // 无边框
                }
                button.background = selectedDrawable
                
                // 使用动画平滑过渡
                val scaleAnim = ScaleAnimation(
                    if (button.scaleX == 0f) 1.0f else button.scaleX, 
                    1.12f,
                    if (button.scaleY == 0f) 1.0f else button.scaleY, 
                    1.12f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                    android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f)
                scaleAnim.duration = 200
                scaleAnim.fillAfter = true
                button.startAnimation(scaleAnim)
            } else {
                // 未选中状态：oklch(0.88 0.01 240) 背景 + oklch(0.25 0.02 240) 文字
                button.setTextColor(unselectedTextColor)  // oklch(0.25 0.02 240) 深灰色文字
                button.setTypeface(null, android.graphics.Typeface.NORMAL)
                button.elevation = 2f
                button.textSize = 16f
                button.text = originalText  // 移除对钩
                
                // 设置高度44px
                val heightInPx = (44 * resources.displayMetrics.density).toInt()
                button.minHeight = heightInPx
                button.layoutParams?.height = heightInPx
                
                // 使用动画平滑过渡回正常大小
                val currentScale = if (button.scaleX == 0f) 1.0f else button.scaleX
                if (currentScale > 1.0f) {
                    val scaleAnim = ScaleAnimation(
                        currentScale, 1.0f,
                        if (button.scaleY == 0f) 1.0f else button.scaleY, 1.0f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f)
                    scaleAnim.duration = 200
                    scaleAnim.fillAfter = true
                    button.startAnimation(scaleAnim)
                }
                
                // 未选中状态：浅灰色背景，边框1px #DCDCDC，圆角8px
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    setColor(unselectedBgColor)  // oklch(0.88 0.01 240)
                    setStroke((1 * resources.displayMetrics.density).toInt(), borderColor)  // 边框1px #DCDCDC
                    cornerRadius = 8f * resources.displayMetrics.density  // 圆角8px
                }
                button.background = drawable
            }
        }
    }
    
    private fun setupKeyboard() {
        // 创建3x3数字键盘布局
        val numbers = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9")
        )
        
        numbers.forEach { row ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            
            row.forEach { number ->
                val button = Button(this).apply {
                    text = number
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        setMargins(4, 4, 4, 4)
                    }
                    isClickable = true
                    isEnabled = true
                    // 底部数字键盘按钮保持默认样式
                    setOnClickListener {
                        // 切换到自定义键盘
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(answerInput.windowToken, 0)
                        findViewById<View>(R.id.keyboardWrapper)?.visibility = View.VISIBLE
                        isUsingSystemKeyboard = false
                        answerInput.clearFocus()
                        
                        val current = answerInput.text.toString()
                        answerInput.setText(current + number)
                        // 添加点击反馈
                        android.util.Log.d("MathPracticeActivity", "数字按钮被点击: $number")
                    }
                }
                rowLayout.addView(button)
            }
            
            keyboardContainer.addView(rowLayout)
        }
        
        // 添加底部行：负号、小数点、0
        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        listOf("-", ".", "0").forEach { char ->
            val button = Button(this).apply {
                text = char
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    setMargins(4, 4, 4, 4)
                }
                isClickable = true
                isEnabled = true
                // 底部数字键盘按钮保持默认样式
                setOnClickListener {
                    // 切换到自定义键盘
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(answerInput.windowToken, 0)
                    findViewById<View>(R.id.keyboardWrapper)?.visibility = View.VISIBLE
                    isUsingSystemKeyboard = false
                    answerInput.clearFocus()
                    
                    val current = answerInput.text.toString()
                    if (char == "-") {
                        // 负号只能放在开头，且只能有一个
                        if (current.startsWith("-")) {
                            // 如果已经有负号，则移除负号
                            answerInput.setText(current.removePrefix("-"))
                        } else {
                            // 如果没有负号，则添加负号到开头
                            answerInput.setText("-$current")
                        }
                    } else {
                        answerInput.setText(current + char)
                    }
                    // 添加点击反馈
                    android.util.Log.d("MathPracticeActivity", "数字按钮被点击: $char")
                }
            }
            bottomRow.addView(button)
        }
        
        keyboardContainer.addView(bottomRow)
    }
    
    private fun startPractice() {
        startTime = System.currentTimeMillis()
        currentQuestionIndex = 0
        userAnswers.clear()
        elapsedSeconds = 0
        
        // 创建练习会话
        lifecycleScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@MathPracticeActivity)
            val session = MathPracticeSession(
                practiceType = practiceType!!.name,
                questionCount = questions.size,
                startTime = startTime
            )
            currentSessionId = session.id
            database.mathPracticeSessionDao().insert(session)
        }
        
        startTimer()
        showCurrentQuestion()
    }
    
    private fun startTimer() {
        timerHandler = Handler(Looper.getMainLooper())
        timerRunnable = object : Runnable {
            override fun run() {
                elapsedSeconds++
                val hours = elapsedSeconds / 3600
                val minutes = (elapsedSeconds % 3600) / 60
                val seconds = elapsedSeconds % 60
                timerText.text = String.format("%d:%02d:%02d", hours, minutes, seconds)
                timerHandler?.postDelayed(this, 1000)
            }
        }
        timerHandler?.post(timerRunnable!!)
    }
    
    private fun stopTimer() {
        timerHandler?.removeCallbacks(timerRunnable!!)
    }
    
    private fun showCurrentQuestion() {
        if (currentQuestionIndex >= questions.size) {
            finishPractice()
            return
        }
        
        val question = questions[currentQuestionIndex]
        questionNumberText.text = "${currentQuestionIndex + 1}/${questions.size}"
        
        // 验证题目类型是否与练习类型一致
        if (question.practiceType != practiceType) {
            android.util.Log.e("MathPracticeActivity", "⚠️ 题目类型不匹配！期望: ${practiceType!!.name}, 实际: ${question.practiceType.name}")
            android.util.Log.e("MathPracticeActivity", "   题目内容: ${question.question}")
        }
        
        // 重置选中状态
        selectedComparison = null
        selectedOption = null
        answerInput.setText("")
        
        // 根据题目的实际类型显示不同的UI（使用 question.practiceType 而不是 practiceType）
        when (question.practiceType) {
            PracticeType.FRACTION_COMPARE,
            PracticeType.INCREMENT_COMPARE,
            PracticeType.BASE_PERIOD_COMPARE -> {
                // 比较题：显示比较按钮，隐藏数字键盘和输入框
                compareButtonsContainer.visibility = View.VISIBLE
                optionButtonsContainer.visibility = View.GONE
                answerInput.visibility = View.GONE
                findViewById<View>(R.id.keyboardWrapper)?.visibility = View.GONE
                
                // 格式化题目显示（初始显示问号，选中后会更新）
                questionText.textSize = 36f
                
                hintText.visibility = View.GONE
                
                // 使用updateQuestionDisplay来显示题目（会应用样式）
                updateQuestionDisplay()
            }
            PracticeType.ONE_TABLE_CALC -> {
                // 选择题：显示选项按钮，隐藏数字键盘和输入框
                compareButtonsContainer.visibility = View.GONE
                optionButtonsContainer.visibility = View.VISIBLE
                answerInput.visibility = View.GONE
                findViewById<View>(R.id.keyboardWrapper)?.visibility = View.GONE
                
                questionText.text = question.question
                questionText.textSize = 36f
                
                // 解析选项（如果有）
                if (question.question.contains("A:") && question.question.contains("B:")) {
                    val parts = question.question.split(" vs ")
                    if (parts.size == 2) {
                        optionAButtonOriginalText = parts[0].trim()
                        optionBButtonOriginalText = parts[1].trim()
                        optionAButton.text = optionAButtonOriginalText
                        optionBButton.text = optionBButtonOriginalText
                    }
                } else {
                    optionAButtonOriginalText = "A"
                    optionBButtonOriginalText = "B"
                    optionAButton.text = optionAButtonOriginalText
                    optionBButton.text = optionBButtonOriginalText
                }
                
                hintText.visibility = View.GONE
            }
            else -> {
                // 普通计算题：显示数字键盘和输入框，隐藏比较按钮和选项按钮
                compareButtonsContainer.visibility = View.GONE
                optionButtonsContainer.visibility = View.GONE
                answerInput.visibility = View.VISIBLE
                // 默认显示自定义键盘
                findViewById<View>(R.id.keyboardWrapper)?.visibility = View.VISIBLE
                // 重置键盘状态
                isUsingSystemKeyboard = false
                
                // 如果题目已经包含等号（如凑整百练习的"57+?=100"），不再添加等号
                val displayQuestion = if (question.question.contains("=")) {
                    question.question
                } else {
                    question.question + "="
                }
                questionText.text = displayQuestion
                questionText.textSize = 48f
                
                // 根据题目类型显示提示
                when (question.practiceType) {
                    PracticeType.THREE_DIGIT_DIV_FOUR -> {
                        hintText.text = "建议写到小数点后2~3位\n允许误差范围:±2%"
                        hintText.visibility = View.VISIBLE
                    }
                    PracticeType.ESTIMATE_PREVIOUS,
                    PracticeType.ESTIMATE_GROWTH -> {
                        hintText.text = "允许误差范围:±3%"
                        hintText.visibility = View.VISIBLE
                    }
                    PracticeType.PERCENTAGE_CALC -> {
                        hintText.text = "允许误差范围:±2%"
                        hintText.visibility = View.VISIBLE
                    }
                    else -> {
                        // 所有其他计算题显示标准提示
                        hintText.text = "允许误差范围: ±5%\n合格: 48s 良好: 40s 优秀: 32s"
                        hintText.visibility = View.VISIBLE
                    }
                }
            }
        }
        
        updateButtonSelection()
    }
    
    private fun confirmAnswer() {
        val currentQuestion = questions[currentQuestionIndex]
        val userAnswer: Double?
        
        // 根据题目的实际类型获取答案（使用 question.practiceType 而不是 practiceType）
        when (currentQuestion.practiceType) {
            PracticeType.FRACTION_COMPARE,
            PracticeType.INCREMENT_COMPARE,
            PracticeType.BASE_PERIOD_COMPARE -> {
                // 比较题：1.0表示大于，-1.0表示小于
                if (selectedComparison == null) {
                    Toast.makeText(this, "请选择比较结果", Toast.LENGTH_SHORT).show()
                    return
                }
                userAnswer = if (selectedComparison == "greater") 1.0 else -1.0
            }
            PracticeType.ONE_TABLE_CALC -> {
                // 选择题：根据选项确定答案
                if (selectedOption == null) {
                    Toast.makeText(this, "请选择答案", Toast.LENGTH_SHORT).show()
                    return
                }
                // 这里需要根据选项确定答案，暂时使用题目答案
                userAnswer = currentQuestion.answer
            }
            else -> {
                // 普通计算题：从输入框获取
                val answerText = answerInput.text.toString().trim()
                if (answerText.isEmpty()) {
                    Toast.makeText(this, "请输入答案", Toast.LENGTH_SHORT).show()
                    return
                }
                userAnswer = answerText.toDoubleOrNull()
            }
        }
        
        // 检查答案是否正确
        val isCorrect = userAnswer != null && isAnswerCorrect(currentQuestion.answer, userAnswer, currentQuestion.practiceType)
        userAnswers.add(Pair(currentQuestion, userAnswer))
        
        if (showAfterAll) {
            // 全部做完后显示答案模式：不显示对错提示，直接进入下一题
            currentQuestionIndex++
            showCurrentQuestion()
        } else {
            // 每做一道就显示答案模式：立即显示对错提示和答案
            val message = if (isCorrect) {
                "✓ 正确\n正确答案：${formatAnswer(currentQuestion.answer, currentQuestion.practiceType)}"
            } else {
                "✗ 错误\n正确答案：${formatAnswer(currentQuestion.answer, currentQuestion.practiceType)}"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            
            // 延迟一下再进入下一题，让用户看到对错提示
            Handler(Looper.getMainLooper()).postDelayed({
                currentQuestionIndex++
                showCurrentQuestion()
            }, 2000)  // 延迟2秒，让用户有时间看答案
        }
    }
    
    private fun finishPractice() {
        stopTimer()
        
        val endTime = System.currentTimeMillis()
        val correctCount = userAnswers.count { (question, userAnswer) ->
            userAnswer != null && isAnswerCorrect(question.answer, userAnswer, question.practiceType)
        }
        val wrongCount = userAnswers.size - correctCount
        
        // 保存练习结果
        lifecycleScope.launch(Dispatchers.IO) {
            val database = AppDatabase.getDatabase(this@MathPracticeActivity)
            currentSessionId?.let { sessionId ->
                val session = database.mathPracticeSessionDao().getSessionById(sessionId)
                session?.let {
                    val questionsData = JSONArray().apply {
                        userAnswers.forEachIndexed { index, (question, userAnswer) ->
                            put(JSONObject().apply {
                                put("index", index)
                                put("question", question.question)
                                put("correctAnswer", question.answer)
                                put("userAnswer", userAnswer ?: "")
                                put("isCorrect", userAnswer != null && isAnswerCorrect(question.answer, userAnswer, question.practiceType))
                            })
                        }
                    }
                    
                    val updatedSession = it.copy(
                        endTime = endTime,
                        totalTimeSeconds = elapsedSeconds,
                        correctCount = correctCount,
                        wrongCount = wrongCount,
                        questionsData = questionsData.toString(),
                        isCompleted = true
                    )
                    database.mathPracticeSessionDao().update(updatedSession)
                }
            }
            
            withContext(Dispatchers.Main) {
                // 跳转到结果页面
                val intent = android.content.Intent(this@MathPracticeActivity, MathPracticeResultActivity::class.java).apply {
                    putExtra("session_id", currentSessionId)
                }
                startActivity(intent)
                finish()
            }
        }
    }
    
    private fun formatAnswer(answer: Double, type: PracticeType): String {
        return when (type) {
            PracticeType.FRACTION_COMPARE,
            PracticeType.INCREMENT_COMPARE,
            PracticeType.BASE_PERIOD_COMPARE -> {
                if (answer > 0) "大于" else "小于"
            }
            else -> {
                // 如果是整数，不显示小数点
                if (answer == answer.toInt().toDouble()) {
                    answer.toInt().toString()
                } else {
                    // 保留2位小数
                    String.format("%.2f", answer)
                }
            }
        }
    }
    
    private fun isAnswerCorrect(correct: Double, user: Double, type: PracticeType): Boolean {
        return when (type) {
            PracticeType.THREE_DIGIT_DIV_FOUR -> {
                // 允许±2%误差
                if (Math.abs(correct) < 0.0001) {
                    // 如果正确答案接近0，使用绝对误差
                    Math.abs(correct - user) < 0.01
                } else {
                    val error = Math.abs(correct - user) / Math.abs(correct)
                    error <= 0.02
                }
            }
            PracticeType.ESTIMATE_PREVIOUS,
            PracticeType.ESTIMATE_GROWTH -> {
                // 允许±3%误差
                if (Math.abs(correct) < 0.0001) {
                    Math.abs(correct - user) < 0.01
                } else {
                    val error = Math.abs(correct - user) / Math.abs(correct)
                    error <= 0.03
                }
            }
            PracticeType.PERCENTAGE_CALC -> {
                // 允许±2%误差
                if (Math.abs(correct) < 0.0001) {
                    Math.abs(correct - user) < 0.01
                } else {
                    val error = Math.abs(correct - user) / Math.abs(correct)
                    error <= 0.02
                }
            }
            // 所有其他计算题：允许±5%误差
            PracticeType.TWO_DIGIT_ADD_SUB,
            PracticeType.THREE_DIGIT_ADD,
            PracticeType.THREE_DIGIT_SUB,
            PracticeType.THREE_DIGIT_ADD_SUB,
            PracticeType.MIXED_ADD_SUB,
            PracticeType.TWO_DIGIT_MUL_ONE,
            PracticeType.TWO_DIGIT_MUL_11,
            PracticeType.TWO_DIGIT_MUL_15,
            PracticeType.TWO_DIGIT_MUL_TWO,
            PracticeType.THREE_DIGIT_MUL_ONE,
            PracticeType.THREE_DIGIT_DIV_ONE,
            PracticeType.THREE_DIGIT_DIV_TWO,
            PracticeType.FIVE_DIGIT_DIV_THREE,
            PracticeType.MULTI_NUMBER_ADD,
            PracticeType.ROUND_TO_HUNDRED,
            PracticeType.COMMON_SQUARES,
            PracticeType.MULTIPLICATION_ESTIMATE,
            PracticeType.FRACTION_CALC_NUM_LESS,
            PracticeType.FRACTION_CALC_NUM_MORE,
            PracticeType.BASE_PERIOD_PROPORTION,
            PracticeType.ANNUAL_AVERAGE,
            PracticeType.ANNUAL_GROWTH_RATE -> {
                // 允许±5%误差
                if (Math.abs(correct) < 0.0001) {
                    // 如果正确答案接近0，使用绝对误差
                    Math.abs(correct - user) < 0.01
                } else {
                    val error = Math.abs(correct - user) / Math.abs(correct)
                    error <= 0.05
                }
            }
            else -> {
                // 比较题和选择题：精确匹配
                Math.abs(correct - user) < 0.01
            }
        }
    }
    
    private fun restartPractice() {
        stopTimer()
        startPractice()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopTimer()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

