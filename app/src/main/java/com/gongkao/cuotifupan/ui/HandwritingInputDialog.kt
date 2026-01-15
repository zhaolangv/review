package com.gongkao.cuotifupan.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.api.HandwritingRecognitionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 手写输入对话框
 * 提供手写板，支持手写文字识别
 */
class HandwritingInputDialog(
    context: Context,
    private val onTextConfirmed: (String) -> Unit
) : Dialog(context, com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog) {

    private lateinit var handwritingInputView: HandwritingInputView
    private lateinit var btnUndo: ImageButton
    private lateinit var btnClear: ImageButton
    private lateinit var colorBlack: View
    private lateinit var colorBlue: View
    private lateinit var colorRed: View
    private lateinit var recognitionLabel: TextView
    private lateinit var recognizedTextEdit: EditText
    private lateinit var btnCancel: Button
    private lateinit var btnRecognize: Button
    private lateinit var btnConfirm: Button

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_handwriting_input)
        
        // 设置对话框宽度
        window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        
        initViews()
        setupListeners()
    }
    
    private fun initViews() {
        handwritingInputView = findViewById(R.id.handwritingInputView)
        btnUndo = findViewById(R.id.btnUndo)
        btnClear = findViewById(R.id.btnClear)
        colorBlack = findViewById(R.id.colorBlack)
        colorBlue = findViewById(R.id.colorBlue)
        colorRed = findViewById(R.id.colorRed)
        recognitionLabel = findViewById(R.id.recognitionLabel)
        recognizedTextEdit = findViewById(R.id.recognizedTextEdit)
        btnCancel = findViewById(R.id.btnCancel)
        btnRecognize = findViewById(R.id.btnRecognize)
        btnConfirm = findViewById(R.id.btnConfirm)
        
        // 默认选中黑色
        updateColorSelection(colorBlack)
    }
    
    private fun setupListeners() {
        // 撤销
        btnUndo.setOnClickListener {
            handwritingInputView.undo()
        }
        
        // 清除
        btnClear.setOnClickListener {
            handwritingInputView.clear()
            recognitionLabel.visibility = View.GONE
            recognizedTextEdit.visibility = View.GONE
            recognizedTextEdit.text.clear()
            btnConfirm.isEnabled = false
        }
        
        // 颜色选择
        colorBlack.setOnClickListener {
            handwritingInputView.setBrushColor(Color.BLACK)
            updateColorSelection(colorBlack)
        }
        colorBlue.setOnClickListener {
            handwritingInputView.setBrushColor(Color.parseColor("#007AFF"))
            updateColorSelection(colorBlue)
        }
        colorRed.setOnClickListener {
            handwritingInputView.setBrushColor(Color.parseColor("#FF3B30"))
            updateColorSelection(colorRed)
        }
        
        // 取消
        btnCancel.setOnClickListener {
            dismiss()
        }
        
        // 识别
        btnRecognize.setOnClickListener {
            recognizeHandwriting()
        }
        
        // 确认
        btnConfirm.setOnClickListener {
            val text = recognizedTextEdit.text.toString().trim()
            if (text.isNotBlank()) {
                onTextConfirmed(text)
                dismiss()
            } else {
                Toast.makeText(context, "请输入或识别文字内容", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 手写内容变化时
        handwritingInputView.onContentChanged = {
            // 有内容时可以识别
            btnRecognize.isEnabled = handwritingInputView.hasContent()
        }
        
        // 识别结果编辑时
        recognizedTextEdit.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                btnConfirm.isEnabled = !s.isNullOrBlank()
            }
        })
    }
    
    private fun updateColorSelection(selectedView: View) {
        // 重置所有颜色选择器大小
        colorBlack.scaleX = 1.0f
        colorBlack.scaleY = 1.0f
        colorBlue.scaleX = 1.0f
        colorBlue.scaleY = 1.0f
        colorRed.scaleX = 1.0f
        colorRed.scaleY = 1.0f
        
        // 放大选中的颜色
        selectedView.scaleX = 1.3f
        selectedView.scaleY = 1.3f
    }
    
    private fun recognizeHandwriting() {
        if (!handwritingInputView.hasContent()) {
            Toast.makeText(context, "请先书写内容", Toast.LENGTH_SHORT).show()
            return
        }
        
        val strokes = handwritingInputView.getStrokes()
        if (strokes.isEmpty()) {
            Toast.makeText(context, "获取手写内容失败", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 显示加载状态
        btnRecognize.isEnabled = false
        btnRecognize.text = "识别中..."
        
        scope.launch {
            try {
                val result = HandwritingRecognitionService.recognizeHandwriting(strokes)
                
                withContext(Dispatchers.Main) {
                    btnRecognize.isEnabled = true
                    btnRecognize.text = "识别"
                    
                    if (result != null && result.isNotBlank()) {
                        // 显示识别结果
                        recognitionLabel.visibility = View.VISIBLE
                        recognizedTextEdit.visibility = View.VISIBLE
                        recognizedTextEdit.setText(result)
                        recognizedTextEdit.setSelection(result.length)
                        btnConfirm.isEnabled = true
                    } else {
                        Toast.makeText(context, "未识别到文字，请重新书写", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    btnRecognize.isEnabled = true
                    btnRecognize.text = "识别"
                    Toast.makeText(context, "识别出错: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

