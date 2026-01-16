package com.gongkao.cuotifupan.ui

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.api.HandwritingRecognitionService
import com.gongkao.cuotifupan.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 回调函数类型
 */
typealias OnResultCallback = (String) -> Unit

/**
 * 全屏手写笔记Activity
 * 支持在屏幕上手写，识别后转换为好看的字体显示
 * 支持"边写边识别"实时美化功能
 */
class ScreenHandwritingActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScreenHandwritingActivity"
        private const val EXTRA_ON_RESULT = "on_result"
        
        private var resultCallback: OnResultCallback? = null
        
        fun start(activity: AppCompatActivity, onResult: OnResultCallback?) {
            resultCallback = onResult
            val intent = Intent(activity, ScreenHandwritingActivity::class.java)
            activity.startActivity(intent)
        }
    }

    private lateinit var handwritingView: HandwritingInputView
    private lateinit var recognizedTextView: TextView
    private lateinit var btnRecognize: Button
    private lateinit var btnClear: Button
    private lateinit var btnConfirm: Button
    private lateinit var btnBack: ImageButton
    
    // 字体选择器容器
    private lateinit var fontSelector: LinearLayout
    private val fontButtons = mutableListOf<Button>()
    
    // 当前选中的字体
    private var currentFont: FontType = FontType.REGULAR
    
    // 边写边识别开关
    private var switchAutoRecognize: Switch? = null
    
    // 是否正在识别中（防止重复识别）
    private var isRecognizing = false
    
    // 已累积的识别结果
    private val recognizedTextBuilder = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_screen_handwriting)

        handwritingView = findViewById(R.id.handwritingView)
        recognizedTextView = findViewById(R.id.recognizedTextView)
        btnRecognize = findViewById(R.id.btnRecognize)
        btnClear = findViewById(R.id.btnClear)
        btnConfirm = findViewById(R.id.btnConfirm)
        btnBack = findViewById(R.id.btnBack)
        fontSelector = findViewById(R.id.fontSelector)
        switchAutoRecognize = findViewById(R.id.switchAutoRecognize)

        // 设置字体选择器
        setupFontSelector()
        
        // 设置边写边识别
        setupAutoRecognize()

        // 识别按钮
        btnRecognize.setOnClickListener {
            recognizeHandwriting(appendToExisting = false)
        }

        // 清除按钮
        btnClear.setOnClickListener {
            handwritingView.clear()
            recognizedTextView.text = ""
            recognizedTextBuilder.clear()
            Toast.makeText(this, "已清除所有内容", Toast.LENGTH_SHORT).show()
        }

        // 确认按钮
        btnConfirm.setOnClickListener {
            // 优先使用画布上的文字，如果没有则使用文本框中的文字
            val text = handwritingView.getRecognizedTextContent()
                .takeIf { it.isNotBlank() } 
                ?: recognizedTextView.text.toString()
            
            if (text.isNotBlank()) {
                resultCallback?.invoke(text)
                resultCallback = null
                finish()
            } else {
                Toast.makeText(this, "请先识别手写内容", Toast.LENGTH_SHORT).show()
            }
        }

        // 返回按钮
        btnBack.setOnClickListener {
            finish()
        }

        // 初始字体
        updateRecognizedTextFont()
    }
    
    /**
     * 设置边写边识别功能
     */
    private fun setupAutoRecognize() {
        // 边写边识别开关
        switchAutoRecognize?.setOnCheckedChangeListener { _, isChecked ->
            handwritingView.enableAutoRecognize = isChecked
            if (isChecked) {
                Toast.makeText(this, "边写边识别已开启，抬笔后自动识别", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 设置抬笔后的识别回调
        handwritingView.onStrokeFinished = {
            if (!isRecognizing && handwritingView.hasContent()) {
                recognizeHandwriting(appendToExisting = true)
            }
        }
        
        // 默认开启边写边识别
        switchAutoRecognize?.isChecked = true
        handwritingView.enableAutoRecognize = true
    }

    /**
     * 识别手写内容
     * @param appendToExisting 是否追加到已有内容（边写边识别模式）
     */
    private fun recognizeHandwriting(appendToExisting: Boolean = false) {
        val strokes = handwritingView.getStrokes()
        if (strokes.isEmpty()) {
            if (!appendToExisting) {
                Toast.makeText(this, "请先手写内容", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        // 防止重复识别
        if (isRecognizing) return
        isRecognizing = true

        if (!appendToExisting) {
            btnRecognize.isEnabled = false
            btnRecognize.text = "识别中..."
        }

        lifecycleScope.launch {
            try {
                val recognizedText = withContext(Dispatchers.IO) {
                    HandwritingRecognitionService.recognizeHandwriting(strokes)
                }

                withContext(Dispatchers.Main) {
                    isRecognizing = false
                    
                    if (!appendToExisting) {
                        btnRecognize.isEnabled = true
                        btnRecognize.text = "识别文字"
                    }

                    if (recognizedText != null && recognizedText.isNotBlank()) {
                        if (appendToExisting) {
                            // 边写边识别模式：直接在画布上显示文字
                            recognizedTextBuilder.append(recognizedText)
                            
                            // 在画布上添加已识别的文字（使用当前选中的字体）
                            // fontSize 设为 0 表示根据手写大小自动计算
                            val typeface = currentFont.getTypeface(this@ScreenHandwritingActivity)
                            Log.d(TAG, "添加文字到画布: '$recognizedText', 字体: ${currentFont.displayName}, 自动计算大小")
                            handwritingView.addRecognizedText(
                                text = recognizedText,
                                typeface = typeface,
                                fontSize = 0f, // 0 表示根据手写大小自动计算
                                textColor = android.graphics.Color.BLACK
                            )
                            
                            // 更新文本框显示（用于确认时使用）
                            recognizedTextView.text = recognizedTextBuilder.toString()
                            updateRecognizedTextFont()
                        } else {
                            // 普通模式：在画布上显示文字
                            recognizedTextBuilder.clear()
                            recognizedTextBuilder.append(recognizedText)
                            
                            // 在画布上添加已识别的文字（自动计算大小）
                            handwritingView.addRecognizedText(
                                text = recognizedText,
                                typeface = currentFont.getTypeface(this@ScreenHandwritingActivity),
                                fontSize = 0f, // 0 表示根据手写大小自动计算
                                textColor = android.graphics.Color.BLACK
                            )
                            
                            // 更新文本框显示
                            recognizedTextView.text = recognizedText
                            updateRecognizedTextFont()
                            Toast.makeText(this@ScreenHandwritingActivity, "识别成功", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        if (!appendToExisting) {
                            Toast.makeText(this@ScreenHandwritingActivity, "识别失败，请重试", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "识别失败", e)
                withContext(Dispatchers.Main) {
                    isRecognizing = false
                    if (!appendToExisting) {
                        btnRecognize.isEnabled = true
                        btnRecognize.text = "识别文字"
                        Toast.makeText(this@ScreenHandwritingActivity, "识别失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * 设置字体选择器
     */
    private fun setupFontSelector() {
        fontSelector.removeAllViews()
        fontButtons.clear()

        FontType.values().forEach { fontType ->
            val button = Button(this).apply {
                text = fontType.displayName
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginEnd = 8
                }
                setOnClickListener {
                    selectFont(fontType)
                }
            }
            fontSelector.addView(button)
            fontButtons.add(button)
        }

        // 默认选择书法字体（如果有 calligraphy.ttf 文件）
        // 如果没有，则使用常规字体
        try {
            val testTypeface = Typeface.createFromAsset(assets, "fonts/calligraphy.ttf")
            testTypeface?.let {
                selectFont(FontType.CALLIGRAPHY)
                Log.d(TAG, "默认使用书法字体（calligraphy.ttf 存在）")
            } ?: selectFont(FontType.REGULAR)
        } catch (e: Exception) {
            selectFont(FontType.REGULAR)
            Log.d(TAG, "默认使用常规字体（calligraphy.ttf 不存在）")
        }
    }

    /**
     * 选择字体
     * 字体设置只对接下来写的字有效，不影响已显示的文字
     */
    private fun selectFont(fontType: FontType) {
        currentFont = fontType
        updateFontButtonStates()
        updateRecognizedTextFont()
        
        // 不更新已显示的文字，只影响后续新写的字
        Log.d(TAG, "字体已切换为: ${fontType.displayName}，只对后续文字生效")
    }

    /**
     * 更新字体按钮状态
     */
    private fun updateFontButtonStates() {
        FontType.values().forEachIndexed { index, fontType ->
            val button = fontButtons.getOrNull(index)
            if (button != null) {
                button.isSelected = (fontType == currentFont)
                button.alpha = if (fontType == currentFont) 1.0f else 0.6f
            }
        }
    }

    /**
     * 更新识别文本的字体
     */
    private fun updateRecognizedTextFont() {
        val text = recognizedTextView.text.toString()
        if (text.isNotBlank()) {
            recognizedTextView.typeface = currentFont.getTypeface(this)
            recognizedTextView.textSize = currentFont.fontSizeSp.toFloat()
        }
    }

    /**
     * 字体类型枚举
     * 使用 assets/fonts/ 目录下的字体文件
     */
    enum class FontType(val displayName: String, val fontSizeSp: Int, val fontFileName: String) {
        REGULAR("常规", 18, "regular.ttf"),
        BOLD("粗体", 18, "bold.ttf"),
        ELEGANT("优雅", 20, "elegant.ttf"),
        CALLIGRAPHY("书法", 22, "calligraphy.ttf");

        /**
         * 获取字体 Typeface
         * 优先使用 assets/fonts/ 目录下的字体文件
         * 如果文件不存在，则回退到系统默认字体
         */
        fun getTypeface(context: Context): Typeface {
            return try {
                // 尝试从 assets 加载字体文件
                Log.d("FontType", "尝试加载字体: fonts/$fontFileName")
                val typeface = Typeface.createFromAsset(context.assets, "fonts/$fontFileName")
                Log.d("FontType", "✅ 成功加载字体: fonts/$fontFileName, 字体名称: ${typeface.toString()}")
                typeface
            } catch (e: RuntimeException) {
                // 字体文件不存在，使用系统默认字体
                Log.w("FontType", "⚠️ 字体文件 fonts/$fontFileName 不存在，使用系统默认字体")
                val fallbackTypeface = when (this) {
                    REGULAR -> Typeface.DEFAULT
                    BOLD -> Typeface.DEFAULT_BOLD
                    ELEGANT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
                    CALLIGRAPHY -> {
                        Log.w("FontType", "书法字体加载失败，使用 SERIF 作为替代")
                        Typeface.SERIF
                    }
                }
                Log.d("FontType", "使用系统默认字体: ${fallbackTypeface.toString()}")
                fallbackTypeface
            } catch (e: Exception) {
                // 其他异常，记录警告
                Log.e("FontType", "❌ 字体文件 fonts/$fontFileName 加载失败", e)
                val fallbackTypeface = when (this) {
                    REGULAR -> Typeface.DEFAULT
                    BOLD -> Typeface.DEFAULT_BOLD
                    ELEGANT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
                    CALLIGRAPHY -> Typeface.SERIF
                }
                fallbackTypeface
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理回调
        if (isFinishing) {
            resultCallback = null
        }
    }
}

