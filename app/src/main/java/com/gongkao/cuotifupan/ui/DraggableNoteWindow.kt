package com.gongkao.cuotifupan.ui

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.NoteItem
import com.gongkao.cuotifupan.data.Question
import org.json.JSONArray
import org.json.JSONObject

/**
 * 可拖拽的笔记浮动窗口
 * 用于在其他应用中显示笔记区域，方便边做题边记笔记
 */
class DraggableNoteWindow(
    private val context: Context,
    private val onNoteAdded: (String) -> Unit,
    private val onFlashcardAdded: (String, String) -> Unit,
    private val onWindowClosed: () -> Unit
) {
    companion object {
        private const val TAG = "DraggableNoteWindow"
    }

    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var floatingView: View? = null
    private var isWindowShown = false
    
    // UI组件
    private lateinit var notesRecyclerView: RecyclerView
    private lateinit var addNoteEditText: EditText
    private lateinit var addNoteButton: Button
    private lateinit var btnMinimize: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var dragHandle: View
    
    private var notesAdapter: NotesAdapter? = null
    private var currentNotes: MutableList<NoteItem> = mutableListOf()
    
    // 窗口参数
    private var windowParams: WindowManager.LayoutParams? = null
    
    /**
     * 显示浮动窗口
     */
    fun show() {
        if (isWindowShown) return
        
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
            Log.e(TAG, "没有悬浮窗权限")
            Toast.makeText(context, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }
        
        val inflater = LayoutInflater.from(context)
        floatingView = inflater.inflate(R.layout.layout_floating_notes, null)
        
        windowParams = WindowManager.LayoutParams().apply {
            width = (context.resources.displayMetrics.widthPixels * 0.9).toInt()
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 200
        }
        
        initViews()
        setupDragHandle()
        setupListeners()
        
        try {
            windowManager.addView(floatingView, windowParams)
            isWindowShown = true
            Log.d(TAG, "浮动笔记窗口已显示")
        } catch (e: Exception) {
            Log.e(TAG, "显示浮动窗口失败", e)
        }
    }
    
    /**
     * 隐藏浮动窗口
     */
    fun hide() {
        if (!isWindowShown) return
        
        try {
            windowManager.removeView(floatingView)
            isWindowShown = false
            floatingView = null
            Log.d(TAG, "浮动笔记窗口已隐藏")
        } catch (e: Exception) {
            Log.e(TAG, "隐藏浮动窗口失败", e)
        }
    }
    
    /**
     * 更新笔记列表
     */
    fun updateNotes(notes: List<NoteItem>) {
        currentNotes.clear()
        currentNotes.addAll(notes)
        notesAdapter?.submitList(notes.toList())
    }
    
    /**
     * 检查窗口是否显示中
     */
    fun isShowing(): Boolean = isWindowShown
    
    private fun initViews() {
        floatingView?.let { view ->
            notesRecyclerView = view.findViewById(R.id.floatingNotesRecyclerView)
            addNoteEditText = view.findViewById(R.id.floatingAddNoteEditText)
            addNoteButton = view.findViewById(R.id.floatingAddNoteButton)
            btnMinimize = view.findViewById(R.id.btnMinimizeNotes)
            btnClose = view.findViewById(R.id.btnCloseNotes)
            dragHandle = view.findViewById(R.id.floatingDragHandle)
            
            // 初始化笔记列表
            notesAdapter = NotesAdapter(
                onNoteDelete = { note ->
                    // 删除笔记（暂不实现，因为需要与Activity通信）
                    Toast.makeText(context, "请在主界面删除笔记", Toast.LENGTH_SHORT).show()
                },
                showSimpleFlashcard = true
            )
            notesRecyclerView.layoutManager = LinearLayoutManager(context)
            notesRecyclerView.adapter = notesAdapter
        }
    }
    
    private fun setupDragHandle() {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = windowParams?.x ?: 0
                    initialY = windowParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    windowParams?.let { params ->
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                else -> false
            }
        }
    }
    
    private fun setupListeners() {
        // 添加笔记按钮
        addNoteButton.setOnClickListener {
            val noteText = addNoteEditText.text.toString().trim()
            if (noteText.isNotBlank()) {
                onNoteAdded(noteText)
                addNoteEditText.text.clear()
            } else {
                Toast.makeText(context, "请输入笔记内容", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 最小化按钮
        btnMinimize.setOnClickListener {
            // 最小化为小按钮
            minimizeWindow()
        }
        
        // 关闭按钮
        btnClose.setOnClickListener {
            hide()
            onWindowClosed()
        }
    }
    
    private var isMinimized = false
    private var minimizedView: View? = null
    
    private fun minimizeWindow() {
        if (isMinimized) return
        
        try {
            windowManager.removeView(floatingView)
            
            // 创建最小化视图
            val inflater = LayoutInflater.from(context)
            minimizedView = inflater.inflate(R.layout.layout_floating_notes_minimized, null)
            
            val miniParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.END
                x = 16
                y = windowParams?.y ?: 200
            }
            
            // 最小化视图的拖动和点击
            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            
            minimizedView?.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = miniParams.x
                        initialY = miniParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        miniParams.x = initialX - (event.rawX - initialTouchX).toInt()
                        miniParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(minimizedView, miniParams)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        if (Math.abs(deltaX) < 10 && Math.abs(deltaY) < 10) {
                            // 点击展开
                            restoreWindow()
                        }
                        true
                    }
                    else -> false
                }
            }
            
            windowManager.addView(minimizedView, miniParams)
            isMinimized = true
            
        } catch (e: Exception) {
            Log.e(TAG, "最小化失败", e)
        }
    }
    
    private fun restoreWindow() {
        if (!isMinimized) return
        
        try {
            windowManager.removeView(minimizedView)
            minimizedView = null
            
            windowManager.addView(floatingView, windowParams)
            isMinimized = false
            
        } catch (e: Exception) {
            Log.e(TAG, "恢复窗口失败", e)
        }
    }
}

