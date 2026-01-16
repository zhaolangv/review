package com.gongkao.cuotifupan.ui

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.api.HandwritingRecognitionService
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.viewmodel.QuestionViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.graphics.Typeface
import android.widget.Toast
import coil.load
import coil.request.ImageRequest
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import com.gongkao.cuotifupan.util.PreferencesManager

/**
 * 手写笔记模式 Activity
 * 功能：
 * - 无限画布（可滚动、缩放）
 * - 图层系统（添加、删除、显示/隐藏、调整顺序）
 * - 套索工具（选择、移动、删除、调整大小）
 * - 全屏手写（整个空白区域都可以写）
 */
class HandwritingNoteActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "HandwritingNoteActivity"
        
        fun start(context: Context, questionId: String? = null) {
            val intent = Intent(context, HandwritingNoteActivity::class.java)
            questionId?.let { intent.putExtra("question_id", it) }
            context.startActivity(intent)
        }
    }
    
    // 画布视图
    private lateinit var canvasView: InfiniteCanvasView
    
    // 工具栏
    private lateinit var toolbar: ViewGroup
    private lateinit var btnBack: ImageButton
    private lateinit var btnUndo: ImageButton
    private lateinit var btnRedo: ImageButton
    private lateinit var btnClear: ImageButton
    
    // 工具选择
    private var currentTool: DrawingTool = DrawingTool.PEN
    
    // 图层管理
    private lateinit var layerManager: LayerManager
    private lateinit var layerListView: RecyclerView
    
    // 题目相关
    private var questionId: String? = null
    private lateinit var viewModel: QuestionViewModel
    private var currentQuestion: Question? = null
    
    // 手写识别和字体
    private var currentFont: FontType = FontType.REGULAR
    private var isRecognizing = false
    
    // 底部工具栏按钮
    private lateinit var btnPencilToggle: ImageButton
    private lateinit var btnBeautifyToggle: ImageButton
    private lateinit var btnEraserBottom: ImageButton
    private lateinit var btnLayer: ImageButton
    private lateinit var btnLassoBottom: ImageButton
    private lateinit var btnModeToggle: ImageButton
    private lateinit var btnFont: ImageButton
    private lateinit var layerPanel: ViewGroup
    private lateinit var btnAddLayer: ImageButton
    private lateinit var btnRemoveLayer: ImageButton
    private lateinit var bottomPanelsContainer: ViewGroup
    private lateinit var optionPanel: ViewGroup
    private lateinit var lassoModeOptions: ViewGroup
    private lateinit var eraserSizeOptions: ViewGroup
    private lateinit var fontOptions: ViewGroup
    private lateinit var optionPanelTitle: TextView
    
    // 状态变量
    private var isPencilEnabled = true
    private var isBeautifyEnabled = true
    private var isWritingMode = true // true=书写模式, false=查看模式
    private var lassoMode: LassoMode = LassoMode.FREEHAND // 套索模式
    
    // PhotoView 变换同步相关
    private var photoViewDrawListener: android.view.ViewTreeObserver.OnDrawListener? = null
    private var canvasInitialX: Float = 0f
    private var canvasInitialY: Float = 0f
    private var photoViewInitialMatrix: Matrix? = null
    private var photoViewSyncHandler: android.os.Handler? = null
    private var photoViewSyncRunnable: Runnable? = null
    private var savedPhotoViewWidth: Int = 0  // 保存PhotoView的宽度（用于查看模式）
    private var savedPhotoViewHeight: Int = 0  // 保存PhotoView的高度（用于查看模式）
    
    enum class DrawingTool {
        PEN,        // 画笔
        ERASER,     // 橡皮擦
        LASSO,      // 套索工具
        MASK        // 胶布工具
    }
    
    enum class LassoMode {
        FREEHAND,   // 自由形状
        RECTANGLE,  // 矩形
        CIRCLE      // 圆形
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_handwriting_note)
        
        questionId = intent.getStringExtra("question_id")
        
        // 初始化 ViewModel
        viewModel = ViewModelProvider(this)[QuestionViewModel::class.java]
        
        initViews()
        setupToolbar()
        setupToolSelector()
        setupBottomToolbar()
        setupLayerManager()
        setupFontButton()
        setupHandwritingRecognition()
        loadQuestion()
        
        // 初始化时确保书写模式下 PhotoView 不可移动
        if (isWritingMode) {
            questionImageView.setZoomable(false)
            questionImageView.setOnTouchListener { _, _ -> true } // 阻止PhotoView处理触摸事件
        }
        
        // 为按钮添加长按功能说明
        setupButtonHints()
    }
    
    /**
     * 为按钮添加长按功能说明
     */
    private fun setupButtonHints() {
        // 顶部工具栏按钮
        btnBack.setOnLongClickListener {
            Toast.makeText(this, "返回：退出手写笔记页面", Toast.LENGTH_SHORT).show()
            true
        }
        btnUndo.setOnLongClickListener {
            Toast.makeText(this, "撤销：撤销上一步操作", Toast.LENGTH_SHORT).show()
            true
        }
        btnRedo.setOnLongClickListener {
            Toast.makeText(this, "重做：恢复刚才撤销的操作", Toast.LENGTH_SHORT).show()
            true
        }
        btnClear.setOnLongClickListener {
            Toast.makeText(this, "清除：清除当前图层的内容", Toast.LENGTH_SHORT).show()
            true
        }
        findViewById<ImageButton>(R.id.btnPen)?.setOnLongClickListener {
            Toast.makeText(this, "画笔：选择画笔工具，可以在图片上书写", Toast.LENGTH_SHORT).show()
            true
        }
        
        // 底部工具栏按钮
        btnPencilToggle.setOnLongClickListener {
            Toast.makeText(this, "笔触开关：开启后笔迹会有粗细变化，关闭后笔迹粗细一致", Toast.LENGTH_SHORT).show()
            true
        }
        btnBeautifyToggle.setOnLongClickListener {
            Toast.makeText(this, "实时美化：开启后，抬笔后会自动识别手写内容并转换为整洁的文字", Toast.LENGTH_SHORT).show()
            true
        }
        btnEraserBottom.setOnLongClickListener {
            Toast.makeText(this, "橡皮擦：选择橡皮擦工具，可以擦除已写的内容。再次点击可调整橡皮擦大小", Toast.LENGTH_SHORT).show()
            true
        }
        btnLayer.setOnLongClickListener {
            Toast.makeText(this, "图层管理：可以创建多个图层，每个图层可以独立显示/隐藏、删除", Toast.LENGTH_SHORT).show()
            true
        }
        btnLassoBottom.setOnLongClickListener {
            Toast.makeText(this, "套索工具：可以选择、移动、删除已写的内容。可以选择自由形状、矩形或圆形区域", Toast.LENGTH_SHORT).show()
            true
        }
        btnModeToggle.setOnLongClickListener {
            Toast.makeText(this, "模式切换：在书写模式和查看模式之间切换。查看模式下可以缩放和移动图片", Toast.LENGTH_SHORT).show()
            true
        }
        btnFont.setOnLongClickListener {
            Toast.makeText(this, "字体选择：选择实时美化后的文字字体样式（常规、粗体、优雅、书法）", Toast.LENGTH_SHORT).show()
            true
        }
        
        // 图层面板按钮
        btnAddLayer.setOnLongClickListener {
            Toast.makeText(this, "添加图层：创建一个新的图层", Toast.LENGTH_SHORT).show()
            true
        }
        btnRemoveLayer.setOnLongClickListener {
            Toast.makeText(this, "删除图层：删除当前选中的图层", Toast.LENGTH_SHORT).show()
            true
        }
        
        // 套索模式按钮
        findViewById<Button>(R.id.btnLassoFreehand)?.setOnLongClickListener {
            Toast.makeText(this, "自由形状：用手指自由绘制选择区域", Toast.LENGTH_SHORT).show()
            true
        }
        findViewById<Button>(R.id.btnLassoRectangle)?.setOnLongClickListener {
            Toast.makeText(this, "矩形：拖动绘制矩形选择区域", Toast.LENGTH_SHORT).show()
            true
        }
        findViewById<Button>(R.id.btnLassoCircle)?.setOnLongClickListener {
            Toast.makeText(this, "圆形：拖动绘制圆形选择区域", Toast.LENGTH_SHORT).show()
            true
        }
        
        // 橡皮擦大小按钮
        findViewById<Button>(R.id.btnEraserSmall)?.setOnLongClickListener {
            Toast.makeText(this, "小号橡皮擦：适合擦除细节", Toast.LENGTH_SHORT).show()
            true
        }
        findViewById<Button>(R.id.btnEraserMedium)?.setOnLongClickListener {
            Toast.makeText(this, "中号橡皮擦：适合一般擦除", Toast.LENGTH_SHORT).show()
            true
        }
        findViewById<Button>(R.id.btnEraserLarge)?.setOnLongClickListener {
            Toast.makeText(this, "大号橡皮擦：适合大面积擦除", Toast.LENGTH_SHORT).show()
            true
        }
        findViewById<Button>(R.id.btnEraserXLarge)?.setOnLongClickListener {
            Toast.makeText(this, "超大号橡皮擦：适合擦除整块内容", Toast.LENGTH_SHORT).show()
            true
        }
        
        // 字体选择按钮
        findViewById<Button>(R.id.btnFontRegular)?.setOnLongClickListener {
            Toast.makeText(this, "常规字体：标准的文字样式", Toast.LENGTH_SHORT).show()
            true
        }
        findViewById<Button>(R.id.btnFontBold)?.setOnLongClickListener {
            Toast.makeText(this, "粗体：加粗的文字样式", Toast.LENGTH_SHORT).show()
            true
        }
        findViewById<Button>(R.id.btnFontElegant)?.setOnLongClickListener {
            Toast.makeText(this, "优雅字体：优美的文字样式", Toast.LENGTH_SHORT).show()
            true
        }
        findViewById<Button>(R.id.btnFontCalligraphy)?.setOnLongClickListener {
            Toast.makeText(this, "书法字体：书法风格的文字样式", Toast.LENGTH_SHORT).show()
            true
        }
    }
    
    private lateinit var questionImageView: com.github.chrisbanes.photoview.PhotoView
    private lateinit var questionImageViewReplacement: android.widget.ImageView // 查看模式下使用的普通 ImageView
    
    private fun initViews() {
        canvasView = findViewById(R.id.canvasView)
        toolbar = findViewById(R.id.toolbar)
        btnBack = findViewById(R.id.btnBack)
        btnUndo = findViewById(R.id.btnUndo)
        btnRedo = findViewById(R.id.btnRedo)
        btnClear = findViewById(R.id.btnClear)
        layerListView = findViewById(R.id.layerListView)
        questionImageView = findViewById(R.id.questionImageView)
        questionImageViewReplacement = findViewById(R.id.questionImageViewReplacement)
        btnFont = findViewById(R.id.btnFont)
        
        // 底部工具栏
        btnPencilToggle = findViewById(R.id.btnPencilToggle)
        btnBeautifyToggle = findViewById(R.id.btnBeautifyToggle)
        btnEraserBottom = findViewById(R.id.btnEraserBottom)
        btnLayer = findViewById(R.id.btnLayer)
        btnLassoBottom = findViewById(R.id.btnLassoBottom)
        btnModeToggle = findViewById(R.id.btnModeToggle)
        bottomPanelsContainer = findViewById(R.id.bottomPanelsContainer)
        layerPanel = findViewById(R.id.layerPanel)
        btnAddLayer = findViewById(R.id.btnAddLayer)
        btnRemoveLayer = findViewById(R.id.btnRemoveLayer)
        optionPanel = findViewById(R.id.optionPanel)
        lassoModeOptions = findViewById(R.id.lassoModeOptions)
        eraserSizeOptions = findViewById(R.id.eraserSizeOptions)
        fontOptions = findViewById(R.id.fontOptions)
        optionPanelTitle = findViewById(R.id.optionPanelTitle)
    }
    
    private fun setupToolbar() {
        btnBack.setOnClickListener { finish() }
        btnUndo.setOnClickListener { 
            canvasView.undo()
            updateToolbarState()
        }
        btnRedo.setOnClickListener { 
            canvasView.redo()
            updateToolbarState()
        }
        btnClear.setOnClickListener {
            // 如果有套索，删除套索和选中的内容
            if (canvasView.hasLasso()) {
                canvasView.deleteLasso()
                Toast.makeText(this, "已删除套索和选中内容", Toast.LENGTH_SHORT).show()
            } else if (canvasView.getSelectedStrokeCount() > 0) {
                // 如果有选中的笔画，删除选中的
                canvasView.deleteSelectedStrokes()
                Toast.makeText(this, "已删除选中内容", Toast.LENGTH_SHORT).show()
            } else {
                // 否则清除当前图层
                canvasView.clearCurrentLayer()
            }
        }
    }
    
    private fun setupToolSelector() {
        // 工具选择器（所有按钮都在顶部工具栏中）
        // btnEraserBottom 和 btnLassoBottom 的点击事件在 setupBottomToolbar 中设置（有特殊逻辑）
        findViewById<ImageButton>(R.id.btnPen)?.setOnClickListener { 
            selectTool(DrawingTool.PEN)
        }
        
        selectTool(DrawingTool.PEN)
    }
    
    private fun setupBottomToolbar() {
        // Pencil开关
        btnPencilToggle.setOnClickListener {
            isPencilEnabled = !isPencilEnabled
            updatePencilButtonState()
            canvasView.setPencilEnabled(isPencilEnabled)
            Toast.makeText(this, if (isPencilEnabled) "笔触已开启" else "笔触已关闭", Toast.LENGTH_SHORT).show()
        }
        updatePencilButtonState()
        
        // 实时字迹美化开关
        btnBeautifyToggle.setOnClickListener {
            isBeautifyEnabled = !isBeautifyEnabled
            updateBeautifyButtonState()
            Toast.makeText(this, if (isBeautifyEnabled) "实时美化已开启" else "实时美化已关闭", Toast.LENGTH_SHORT).show()
        }
        updateBeautifyButtonState()
        
        // 橡皮擦（点击切换工具，再次点击切换显示/隐藏选项面板）
        btnEraserBottom.setOnClickListener {
            Log.d(TAG, "橡皮擦按钮点击: currentTool=$currentTool")
            Log.d(TAG, "optionPanel.visibility=${optionPanel.visibility}, eraserSizeOptions.visibility=${eraserSizeOptions.visibility}")
            
            if (currentTool == DrawingTool.ERASER) {
                // 如果已经是橡皮擦工具，切换选项面板显示/隐藏
                val isVisible = optionPanel.visibility == android.view.View.VISIBLE && 
                               eraserSizeOptions.visibility == android.view.View.VISIBLE
                Log.d(TAG, "当前是橡皮擦工具，面板可见性: $isVisible")
                
                if (isVisible) {
                    // 如果面板可见，关闭它
                    Log.d(TAG, "关闭橡皮擦选项面板")
                    optionPanel.visibility = android.view.View.GONE
                    lassoModeOptions.visibility = android.view.View.GONE
                    eraserSizeOptions.visibility = android.view.View.GONE
                    updateBottomPanelsContainerVisibility()
                    Log.d(TAG, "关闭后 optionPanel.visibility=${optionPanel.visibility}")
            } else {
                    // 如果面板不可见，显示它
                    Log.d(TAG, "显示橡皮擦选项面板")
                    showEraserSizePanel()
                }
            } else {
                // 第一次点击，切换到橡皮擦工具并显示选项面板
                Log.d(TAG, "第一次点击橡皮擦，切换到橡皮擦工具并显示选项面板")
                selectTool(DrawingTool.ERASER)
                showEraserSizePanel()
            }
        }
        
        // 图层按钮
        btnLayer.setOnClickListener {
            toggleLayerPanel()
        }
        
        // 套索工具（点击切换工具，再次点击切换显示/隐藏选项面板）
        btnLassoBottom.setOnClickListener {
            Log.d(TAG, "套索按钮点击: currentTool=$currentTool")
            Log.d(TAG, "optionPanel.visibility=${optionPanel.visibility}, lassoModeOptions.visibility=${lassoModeOptions.visibility}")
            
            if (currentTool == DrawingTool.LASSO) {
                // 如果已经是套索工具，切换选项面板显示/隐藏
                val isVisible = optionPanel.visibility == android.view.View.VISIBLE && 
                               lassoModeOptions.visibility == android.view.View.VISIBLE
                Log.d(TAG, "当前是套索工具，面板可见性: $isVisible")
                
                if (isVisible) {
                    // 如果面板可见，关闭它
                    Log.d(TAG, "关闭套索选项面板")
                    optionPanel.visibility = android.view.View.GONE
                    lassoModeOptions.visibility = android.view.View.GONE
                    eraserSizeOptions.visibility = android.view.View.GONE
                    updateBottomPanelsContainerVisibility()
                    Log.d(TAG, "关闭后 optionPanel.visibility=${optionPanel.visibility}")
            } else {
                    // 如果面板不可见，显示它
                    Log.d(TAG, "显示套索选项面板")
                    showLassoModePanel()
                }
            } else {
                // 第一次点击，切换到套索工具并显示选项面板
                Log.d(TAG, "第一次点击套索，切换到套索工具并显示选项面板")
                selectTool(DrawingTool.LASSO)
                canvasView.clearLassoSelection()
                showLassoModePanel()
                Toast.makeText(this, "套索工具：拖动画出选择区域\n选中后可拖动移动，点击清除删除", Toast.LENGTH_LONG).show()
            }
        }
        
        // 设置套索形状选择按钮（选择后自动关闭面板）
        findViewById<Button>(R.id.btnLassoFreehand)?.setOnClickListener {
            lassoMode = LassoMode.FREEHAND
            canvasView.setLassoMode(lassoMode)
            updateLassoModeButtons()
            hideOptionPanel()
            Toast.makeText(this, "已切换到: 自由形状", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnLassoRectangle)?.setOnClickListener {
            lassoMode = LassoMode.RECTANGLE
            canvasView.setLassoMode(lassoMode)
            updateLassoModeButtons()
            hideOptionPanel()
            Toast.makeText(this, "已切换到: 矩形", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnLassoCircle)?.setOnClickListener {
            lassoMode = LassoMode.CIRCLE
            canvasView.setLassoMode(lassoMode)
            updateLassoModeButtons()
            hideOptionPanel()
            Toast.makeText(this, "已切换到: 圆形", Toast.LENGTH_SHORT).show()
        }
        
        // 设置橡皮擦大小选择按钮（选择后自动关闭面板）
        findViewById<Button>(R.id.btnEraserSmall)?.setOnClickListener {
            canvasView.setEraserSize(10f)
            updateEraserSizeButtons()
            hideOptionPanel()
            Toast.makeText(this, "已设置为: 细 (10)", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnEraserMedium)?.setOnClickListener {
            canvasView.setEraserSize(20f)
            updateEraserSizeButtons()
            hideOptionPanel()
            Toast.makeText(this, "已设置为: 中 (20)", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnEraserLarge)?.setOnClickListener {
            canvasView.setEraserSize(40f)
            updateEraserSizeButtons()
            hideOptionPanel()
            Toast.makeText(this, "已设置为: 粗 (40)", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnEraserXLarge)?.setOnClickListener {
            canvasView.setEraserSize(60f)
            updateEraserSizeButtons()
            hideOptionPanel()
            Toast.makeText(this, "已设置为: 很粗 (60)", Toast.LENGTH_SHORT).show()
        }
        
        // 套索选择完成后的处理
        canvasView.onLassoSelectionComplete = {
            val count = canvasView.getSelectedStrokeCount()
            if (count > 0) {
                Toast.makeText(this, "已选择 $count 个笔画\n可以拖动移动，点击清除按钮删除", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "未选中任何笔画", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 模式切换（书写/查看）
        btnModeToggle.setOnClickListener {
            // 保存当前画布状态（在切换前）
            val savedX = canvasView.getTranslateX()
            val savedY = canvasView.getTranslateY()
            val savedScale = canvasView.getScaleFactor()
            
            // 在切换到查看模式前，保存PhotoView的尺寸（如果PhotoView可见）
            if (questionImageView.visibility == android.view.View.VISIBLE && questionImageView.width > 0 && questionImageView.height > 0) {
                savedPhotoViewWidth = questionImageView.width
                savedPhotoViewHeight = questionImageView.height
                Log.d(TAG, "保存PhotoView尺寸: ${savedPhotoViewWidth}x${savedPhotoViewHeight}")
            }
            
            isWritingMode = !isWritingMode
            updateModeButtonState()
            canvasView.setWritingMode(isWritingMode)
            
            // 验证画布状态确实保持不变（setWritingMode 不会重置状态）
            val afterX = canvasView.getTranslateX()
            val afterY = canvasView.getTranslateY()
            val afterScale = canvasView.getScaleFactor()
            Log.d(TAG, "模式切换: ${if (isWritingMode) "书写模式" else "查看模式"}")
            Log.d(TAG, "画布状态保持不变 - 切换前: translateX=$savedX, translateY=$savedY, scale=$savedScale")
            Log.d(TAG, "画布状态保持不变 - 切换后: translateX=$afterX, translateY=$afterY, scale=$afterScale")
            // 确保状态确实一致（理论上应该一致，因为 setWritingMode 只设置标志）
            if (Math.abs(afterX - savedX) > 0.1f || Math.abs(afterY - savedY) > 0.1f || Math.abs(afterScale - savedScale) > 0.001f) {
                Log.w(TAG, "警告：画布状态在模式切换时发生了变化！")
            }
            
            // 在查看模式下，使用普通 ImageView 替代 PhotoView，完全控制变换
            if (!isWritingMode) {
                Log.d(TAG, "切换到查看模式: PhotoView.visibility=${questionImageView.visibility}, drawable=${questionImageView.drawable != null}")
                
                // 如果替换的 ImageView 已经有 drawable，保持它（保存查看模式的最后状态）
                // 只有当替换的 ImageView 没有 drawable 且 PhotoView 有 drawable 时，才从 PhotoView 复制
                if (questionImageViewReplacement.drawable == null && questionImageView.drawable != null) {
                    questionImageViewReplacement.setImageDrawable(questionImageView.drawable)
                    Log.d(TAG, "从 PhotoView 复制 drawable 到替换 ImageView")
                } else {
                    Log.d(TAG, "保持替换 ImageView 的现有 drawable（保存查看模式的最后状态）")
                }
                
                // 先隐藏 PhotoView，再显示替换的 ImageView
                questionImageView.visibility = android.view.View.GONE
                questionImageViewReplacement.visibility = android.view.View.VISIBLE
                Log.d(TAG, "已切换到查看模式: replacementVisibility=${questionImageViewReplacement.visibility}, replacementDrawable=${questionImageViewReplacement.drawable != null}")
                
                // 确保画布可以接收触摸事件
                canvasView.isClickable = true
                canvasView.isFocusable = true
                
                // canvasInitialX 和 canvasInitialY 应该在图片首次加载时初始化（在 loadQuestion 中）
                // 这里不应该初始化它们
                
                // 保存当前的变换值，用于持续同步
                var currentTransformX = savedX
                var currentTransformY = savedY
                var currentTransformScale = savedScale
                
                // 设置画布变换回调，同步 ImageView 的变换
                canvasView.onCanvasTransformChanged = { translateX, translateY, scale ->
                    currentTransformX = translateX
                    currentTransformY = translateY
                    currentTransformScale = scale
                    // 立即同步到普通 ImageView
                    syncImageViewTransform(questionImageViewReplacement, translateX, translateY, scale)
                }
                
                // 初始化 ImageView 的变换（与画布同步，使用保存的状态）
                // 使用 post 延迟执行，确保视图已经完成布局（从 GONE 变为 VISIBLE 需要时间）
                questionImageViewReplacement.post {
                    Log.d(TAG, "进入查看模式，准备同步ImageView: translateX=$savedX, translateY=$savedY, scale=$savedScale, imageView.width=${questionImageViewReplacement.width}, imageView.height=${questionImageViewReplacement.height}, imageView.drawable=${questionImageViewReplacement.drawable != null}, visibility=${questionImageViewReplacement.visibility}")
                    // 先尝试同步（如果尺寸已可用），否则会通过布局监听器同步
                    syncImageViewTransform(questionImageViewReplacement, savedX, savedY, savedScale)
                    // 如果尺寸仍然无效，确保布局监听器已设置
                    if (questionImageViewReplacement.width <= 0 || questionImageViewReplacement.height <= 0) {
                        Log.d(TAG, "ImageView尺寸仍无效，强制设置布局监听器")
                        setupImageViewLayoutListener(questionImageViewReplacement, savedX, savedY, savedScale)
                    }
                }
            } else {
                // 书写模式下，隐藏普通 ImageView，显示 PhotoView
                questionImageViewReplacement.visibility = android.view.View.GONE
                questionImageView.visibility = android.view.View.VISIBLE
                
                // 移除变换回调
                canvasView.onCanvasTransformChanged = null
                
                // 清理布局监听器（避免内存泄漏）
                imageViewLayoutListener?.let { listener ->
                    questionImageViewReplacement.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                    imageViewLayoutListener = null
                }
                
                // 先隐藏替换的 ImageView，再显示 PhotoView（避免两个视图同时显示导致闪烁）
                questionImageViewReplacement.visibility = android.view.View.GONE
                questionImageView.visibility = android.view.View.VISIBLE
                
                // canvasInitialX 和 canvasInitialY 应该在图片首次加载时初始化（在 loadQuestion 中）
                // 这里不应该重置它们，否则会导致位置计算错误
                
                // 同步PhotoView的位置，使其与画布位置一致
                questionImageView.post {
                    Log.d(TAG, "进入书写模式，同步PhotoView位置: translateX=$savedX, translateY=$savedY, scale=$savedScale, canvasInitialX=$canvasInitialX, canvasInitialY=$canvasInitialY")
                    syncPhotoViewTransform(savedX, savedY, savedScale)
                    // 同步完成后，强制刷新画布视图，确保画布位置正确显示
                    canvasView.post {
                        canvasView.invalidate()
                        Log.d(TAG, "画布视图已刷新，当前translateX=${canvasView.getTranslateX()}, translateY=${canvasView.getTranslateY()}")
                    }
                }
                
                // 书写模式下，禁用PhotoView的所有交互，确保图片不可移动
                questionImageView.isClickable = false
                questionImageView.isFocusable = false
                questionImageView.isEnabled = false
                questionImageView.setOnTouchListener { _, _ -> 
                    // 返回true，阻止PhotoView处理触摸事件
                    true 
                }
                // 禁用PhotoView的缩放和平移
                questionImageView.setZoomable(false)
                
                Log.d(TAG, "进入书写模式，禁用PhotoView移动，PhotoView.drawable=${questionImageView.drawable != null}")
            }
            
            Toast.makeText(this, if (isWritingMode) "书写模式" else "查看模式（可滑动查看）", Toast.LENGTH_SHORT).show()
        }
        updateModeButtonState()
    }
    
    private fun syncImageViewTransform(imageView: android.widget.ImageView, translateX: Float, translateY: Float, scale: Float) {
        // 同步普通 ImageView 的变换（用于查看模式）
        Log.d(TAG, "syncImageViewTransform: drawable=${imageView.drawable != null}, viewWidth=${imageView.width}, viewHeight=${imageView.height}, translateX=$translateX, translateY=$translateY, scale=$scale, visibility=${imageView.visibility}")
        
        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        
        if (imageView.drawable != null && viewWidth > 0 && viewHeight > 0) {
            // 尺寸有效，直接应用变换
            applyImageViewTransform(imageView, translateX, translateY, scale)
        } else {
            Log.w(TAG, "syncImageViewTransform: drawable 或视图尺寸无效，设置布局监听器 (drawable=${imageView.drawable != null}, viewWidth=$viewWidth, viewHeight=$viewHeight)")
            // 如果视图尺寸无效，设置一个布局监听器，等待布局完成后再同步
            if (viewWidth <= 0 || viewHeight <= 0) {
                setupImageViewLayoutListener(imageView, translateX, translateY, scale)
            }
        }
    }
    
    private var imageViewLayoutListener: android.view.ViewTreeObserver.OnGlobalLayoutListener? = null
    
    private fun setupImageViewLayoutListener(imageView: android.widget.ImageView, translateX: Float, translateY: Float, scale: Float) {
        // 先检查尺寸是否已经有效
        val viewWidth = imageView.width.toFloat()
        val viewHeight = imageView.height.toFloat()
        
        Log.d(TAG, "setupImageViewLayoutListener: viewWidth=$viewWidth, viewHeight=$viewHeight, drawable=${imageView.drawable != null}, visibility=${imageView.visibility}")
        
        if (viewWidth > 0 && viewHeight > 0 && imageView.drawable != null) {
            // 尺寸已有效，直接同步，不需要监听器
            Log.d(TAG, "ImageView尺寸已有效，直接同步: viewWidth=$viewWidth, viewHeight=$viewHeight")
            // 直接调用同步逻辑，避免递归调用 setupImageViewLayoutListener
            applyImageViewTransform(imageView, translateX, translateY, scale)
            return
        }
        
        // 移除之前的监听器（如果存在）
        imageViewLayoutListener?.let { listener ->
            try {
                imageView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                Log.d(TAG, "移除旧的布局监听器")
            } catch (e: Exception) {
                Log.w(TAG, "移除旧布局监听器失败", e)
            }
        }
        
        // 创建新的布局监听器
        imageViewLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val currentViewWidth = imageView.width.toFloat()
            val currentViewHeight = imageView.height.toFloat()
            
            Log.d(TAG, "ImageView布局监听器触发: viewWidth=$currentViewWidth, viewHeight=$currentViewHeight, drawable=${imageView.drawable != null}, visibility=${imageView.visibility}")
            
            if (currentViewWidth > 0 && currentViewHeight > 0 && imageView.drawable != null) {
                // 视图已布局完成，同步变换（使用 applyImageViewTransform 避免递归）
                applyImageViewTransform(imageView, translateX, translateY, scale)
                
                // 移除监听器（只执行一次）
                imageViewLayoutListener?.let { listener ->
                    try {
                        imageView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                        imageViewLayoutListener = null
                        Log.d(TAG, "布局监听器已移除")
                    } catch (e: Exception) {
                        Log.w(TAG, "移除布局监听器失败", e)
                    }
                }
            } else {
                Log.d(TAG, "布局监听器触发但尺寸仍无效，继续等待")
            }
        }
        
        // 添加监听器
        try {
            imageView.viewTreeObserver.addOnGlobalLayoutListener(imageViewLayoutListener)
            Log.d(TAG, "已设置ImageView布局监听器，等待布局完成")
        } catch (e: Exception) {
            Log.e(TAG, "添加布局监听器失败", e)
        }
    }
    
    private fun applyImageViewTransform(imageView: android.widget.ImageView, translateX: Float, translateY: Float, scale: Float) {
        // 直接应用变换，不检查尺寸（由调用者确保尺寸有效）
        try {
            val matrix = Matrix()
            
            val drawable = imageView.drawable
            var viewWidth = imageView.width.toFloat()
            var viewHeight = imageView.height.toFloat()
            
            // 如果视图尺寸无效，尝试使用保存的PhotoView尺寸或canvasView的尺寸（它们使用相同的layoutParams）
            if (viewWidth <= 0 || viewHeight <= 0) {
                // 优先使用保存的PhotoView尺寸
                if (savedPhotoViewWidth > 0 && savedPhotoViewHeight > 0) {
                    viewWidth = savedPhotoViewWidth.toFloat()
                    viewHeight = savedPhotoViewHeight.toFloat()
                    Log.d(TAG, "使用保存的PhotoView尺寸: width=$viewWidth, height=$viewHeight")
                } else {
                    // 如果保存的尺寸不可用，使用canvasView的尺寸（它们使用相同的约束）
                    val canvasWidth = canvasView.width.toFloat()
                    val canvasHeight = canvasView.height.toFloat()
                    if (canvasWidth > 0 && canvasHeight > 0) {
                        viewWidth = canvasWidth
                        viewHeight = canvasHeight
                        Log.d(TAG, "使用canvasView尺寸作为参考: width=$viewWidth, height=$viewHeight")
                    }
                }
            }
            
            if (drawable != null && viewWidth > 0 && viewHeight > 0) {
                val drawableWidth = drawable.intrinsicWidth.toFloat()
                val drawableHeight = drawable.intrinsicHeight.toFloat()
                
                if (drawableWidth > 0 && drawableHeight > 0) {
                    // 计算基础缩放（使图片适应视图）
                    val scaleX = viewWidth / drawableWidth
                    val scaleY = viewHeight / drawableHeight
                    val baseScale = minOf(scaleX, scaleY)
                    
                    // 计算居中位置（初始位置）
                    val initialCenterX = (viewWidth - drawableWidth * baseScale) / 2
                    val initialCenterY = (viewHeight - drawableHeight * baseScale) / 2
                    
                    // 计算相对于初始位置的偏移
                    val relativeX = translateX - canvasInitialX
                    val relativeY = translateY - canvasInitialY
                    
                    // 构建变换矩阵
                    // 当画布向右移动（translateX增加）时，图片也应该向右移动（finalX增加）
                    matrix.setScale(baseScale * scale, baseScale * scale)
                    val finalX = initialCenterX + relativeX
                    val finalY = initialCenterY + relativeY
                    matrix.postTranslate(finalX, finalY)
                    
                    // 应用变换
                    imageView.scaleType = android.widget.ImageView.ScaleType.MATRIX
                    imageView.imageMatrix = matrix
                    imageView.invalidate()
                    
                    Log.d(TAG, "✅ ImageView变换应用成功: baseScale=$baseScale, finalTranslate=($finalX, $finalY), visibility=${imageView.visibility}, width=$viewWidth, height=$viewHeight")
                } else {
                    Log.w(TAG, "applyImageViewTransform: drawable 尺寸无效 (width=$drawableWidth, height=$drawableHeight)")
                }
            } else {
                Log.w(TAG, "applyImageViewTransform: drawable 或视图尺寸无效 (drawable=${drawable != null}, viewWidth=$viewWidth, viewHeight=$viewHeight)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "应用ImageView变换失败", e)
        }
    }
    
    private fun syncPhotoViewTransform(translateX: Float, translateY: Float, scale: Float) {
        // 同步PhotoView的变换，使图片和画布一起移动
        // 直接使用 imageMatrix 方法（与查看模式使用相同的逻辑，已验证可用）
        // 跳过反射方法，因为 PhotoView 的内部矩阵计算方式复杂且不可靠
        try {
        val matrix = Matrix()
            
            // 获取图片和视图的尺寸
            val drawable = questionImageView.drawable
            val viewWidth = questionImageView.width.toFloat()
            val viewHeight = questionImageView.height.toFloat()
            
            if (drawable != null && viewWidth > 0 && viewHeight > 0) {
                val drawableWidth = drawable.intrinsicWidth.toFloat()
                val drawableHeight = drawable.intrinsicHeight.toFloat()
                
                if (drawableWidth > 0 && drawableHeight > 0) {
                    // 计算基础缩放（使图片适应视图）
                    val scaleX = viewWidth / drawableWidth
                    val scaleY = viewHeight / drawableHeight
                    val baseScale = minOf(scaleX, scaleY)
                    
                    // 计算居中位置（初始位置）
                    val initialCenterX = (viewWidth - drawableWidth * baseScale) / 2
                    val initialCenterY = (viewHeight - drawableHeight * baseScale) / 2
                    
                    // 计算相对于初始位置的偏移
                    val relativeX = translateX - canvasInitialX
                    val relativeY = translateY - canvasInitialY
                    
                    // 构建变换矩阵：基础变换 + 相对偏移
                    // 当画布向右移动（translateX增加）时，图片也应该向右移动（finalX增加）
                    matrix.setScale(baseScale * scale, baseScale * scale)
                    val finalX = initialCenterX + relativeX
                    val finalY = initialCenterY + relativeY
                    matrix.postTranslate(finalX, finalY)
                    
                    Log.d(TAG, "PhotoView变换: baseScale=$baseScale, initialCenter=($initialCenterX, $initialCenterY), relativeOffset=($relativeX, $relativeY), canvasInitial=($canvasInitialX, $canvasInitialY), finalTranslate=($finalX, $finalY), scaleType=${questionImageView.scaleType}")
                } else {
                    // 如果图片尺寸无效，直接使用用户变换
        matrix.setScale(scale, scale)
        matrix.postTranslate(translateX, translateY)
                }
            } else {
                // 如果视图尺寸无效，直接使用用户变换
                matrix.setScale(scale, scale)
                matrix.postTranslate(translateX, translateY)
            }
            
            // PhotoView 不支持直接设置 ScaleType.MATRIX，所以我们直接操作 imageMatrix
            // 直接设置 imageMatrix，并立即强制应用
            try {
        questionImageView.imageMatrix = matrix
                questionImageView.invalidate()
                
                // 使用 Handler 持续设置，防止被 PhotoView 内部逻辑重置
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    questionImageView.imageMatrix = matrix
                    questionImageView.invalidate()
                }
                
                // 再次延迟设置，确保生效
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    questionImageView.imageMatrix = matrix
                    questionImageView.invalidate()
                }, 16) // 一帧的时间
            } catch (e: Exception) {
                Log.e(TAG, "设置 imageMatrix 失败", e)
            }
            
            Log.d(TAG, "同步PhotoView变换: translateX=$translateX, translateY=$translateY, scale=$scale, matrix=$matrix")
        } catch (e: Exception) {
            Log.e(TAG, "设置PhotoView变换失败", e)
        }
    }
    
    private fun updatePencilButtonState() {
        if (isPencilEnabled) {
            btnPencilToggle.setBackgroundColor(0x3300BCD4.toInt())
        } else {
            btnPencilToggle.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }
    
    private fun updateBeautifyButtonState() {
        if (isBeautifyEnabled) {
            btnBeautifyToggle.setBackgroundColor(0x3300BCD4.toInt())
        } else {
            btnBeautifyToggle.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }
    
    private fun updateModeButtonState() {
        if (isWritingMode) {
            btnModeToggle.setBackgroundColor(0x3300BCD4.toInt())
        } else {
            btnModeToggle.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }
    }
    
    private fun toggleLayerPanel() {
        if (layerPanel.visibility == android.view.View.VISIBLE) {
            layerPanel.visibility = android.view.View.GONE
        } else {
            layerPanel.visibility = android.view.View.VISIBLE
            // 确保图层面板有足够的宽度，并在布局完成后定位
            layerPanel.post {
                val layoutParams = layerPanel.layoutParams
                if (layoutParams.width < 200) {
                    layoutParams.width = (200 * resources.displayMetrics.density).toInt()
                    layerPanel.layoutParams = layoutParams
                }
                // 在布局完成后定位面板，确保位置计算准确
                positionPanelBelowButton(btnLayer, layerPanel)
            }
        }
        updateBottomPanelsContainerVisibility()
        Log.d(TAG, "切换图层面板: layerPanel.visibility=${layerPanel.visibility}, layerListView.childCount=${layerListView.childCount}")
    }
    
    private fun positionPanelBelowButton(button: ImageButton, panel: ViewGroup) {
        // 获取按钮在屏幕上的位置
        val buttonLocation = IntArray(2)
        button.getLocationOnScreen(buttonLocation)
        
        // 获取工具栏容器在屏幕上的位置（用于计算按钮相对于工具栏的位置）
        val toolbarContainer = findViewById<androidx.cardview.widget.CardView>(R.id.toolbarContainer)
        val toolbarLocation = IntArray(2)
        toolbarContainer?.getLocationOnScreen(toolbarLocation)
        
        // 获取面板容器在屏幕上的位置
        val containerLocation = IntArray(2)
        bottomPanelsContainer.getLocationOnScreen(containerLocation)
        
        // 计算按钮相对于面板容器的位置
        // 按钮的 X 坐标相对于屏幕，需要转换为相对于面板容器的坐标
        var relativeX = buttonLocation[0] - containerLocation[0]
        
        // 按钮的 Y 坐标：使用工具栏容器的底部位置
        // 因为面板容器的约束是 toBottomOf toolbarContainer，所以面板容器顶部应该就是工具栏容器底部
        val toolbarBottom = toolbarLocation[1] + (toolbarContainer?.height ?: 0)
        // 面板容器的顶部应该就是工具栏容器的底部（因为约束是 toBottomOf）
        // 所以 relativeY 应该从 0 开始，但我们需要让面板紧贴按钮
        // 按钮底部相对于工具栏底部的偏移
        val buttonBottom = buttonLocation[1] + button.height
        val buttonOffsetFromToolbarBottom = buttonBottom - toolbarBottom
        // 面板容器顶部相对于工具栏底部的偏移（应该是 0，因为约束是 toBottomOf）
        val containerOffsetFromToolbarBottom = containerLocation[1] - toolbarBottom
        // 最终 relativeY：直接使用按钮底部位置减去容器顶部位置，让面板紧贴按钮
        // 面板容器覆盖整个屏幕，所以容器顶部就是屏幕顶部（0）
        // 按钮底部在屏幕上的位置就是 relativeY
        var relativeY = buttonBottom - containerLocation[1]
        
        // 确保 relativeY 不小于 0（面板不能跑到按钮上方）
        if (relativeY < 0) {
            relativeY = 0
        }
        
        Log.d(TAG, "位置计算: buttonBottom=$buttonBottom, containerTop=${containerLocation[1]}, relativeY=$relativeY")
        
        // 设置面板的位置（使用 FrameLayout.LayoutParams）
        val layoutParams = panel.layoutParams as? FrameLayout.LayoutParams
            ?: FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        
        // 确保面板有足够的宽度（仅对选项面板）
        if (panel.id == R.id.optionPanel) {
            val minWidthPx = (180 * resources.displayMetrics.density).toInt()
            if (layoutParams.width < minWidthPx) {
                layoutParams.width = minWidthPx
            }
        }
        
        // 先设置位置，然后在 post 中调整以确保面板完全在屏幕内
        layoutParams.leftMargin = relativeX
        layoutParams.topMargin = relativeY
        panel.layoutParams = layoutParams
        
        // 确保面板不会超出屏幕边界
        panel.post {
            val screenWidth = resources.displayMetrics.widthPixels
            val marginPx = (16 * resources.displayMetrics.density).toInt()
            
            // 测量面板的实际宽度
            panel.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            val panelWidth = panel.measuredWidth
            
            // 计算面板右边界位置
            val panelRight = relativeX + panelWidth
            
            // 如果面板超出右边界，调整位置
            if (panelRight > screenWidth - marginPx) {
                relativeX = screenWidth - panelWidth - marginPx
            }
            
            // 确保不会超出左边界
            if (relativeX < marginPx) {
                relativeX = marginPx
                // 如果面板太宽，缩小宽度以适应屏幕
                if (panelWidth > screenWidth - marginPx * 2) {
                    layoutParams.width = screenWidth - marginPx * 2
                }
            }
            
            layoutParams.leftMargin = relativeX
            panel.layoutParams = layoutParams
        }
    }
    
    
    private fun showLassoModePanel() {
        optionPanelTitle.text = "选择形状"
        lassoModeOptions.visibility = android.view.View.VISIBLE
        eraserSizeOptions.visibility = android.view.View.GONE
        optionPanel.visibility = android.view.View.VISIBLE
        
        // 确保选项面板有足够的宽度
        optionPanel.post {
            val layoutParams = optionPanel.layoutParams
            if (layoutParams.width < 180) {
                layoutParams.width = (180 * resources.displayMetrics.density).toInt()
                optionPanel.layoutParams = layoutParams
            }
        }
        
        positionPanelBelowButton(btnLassoBottom, optionPanel)
        updateBottomPanelsContainerVisibility()
        updateLassoModeButtons()
        
        Log.d(TAG, "显示套索选项面板: optionPanel.visibility=${optionPanel.visibility}, lassoModeOptions.visibility=${lassoModeOptions.visibility}")
    }
    
    private fun updateLassoModeButtons() {
        val buttons = listOf(
            findViewById<Button>(R.id.btnLassoFreehand),
            findViewById<Button>(R.id.btnLassoRectangle),
            findViewById<Button>(R.id.btnLassoCircle)
        )
        val modes = listOf(LassoMode.FREEHAND, LassoMode.RECTANGLE, LassoMode.CIRCLE)
        buttons.forEachIndexed { index, button ->
            button?.let {
                if (lassoMode == modes[index]) {
                    // 选中状态：蓝色背景
                    it.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2196F3.toInt())
                } else {
                    // 未选中状态：灰色背景
                    it.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF666666.toInt())
                }
            }
        }
    }
    
    private fun showEraserSizePanel() {
        optionPanelTitle.text = "选择大小"
        lassoModeOptions.visibility = android.view.View.GONE
        eraserSizeOptions.visibility = android.view.View.VISIBLE
        optionPanel.visibility = android.view.View.VISIBLE
        positionPanelBelowButton(btnEraserBottom, optionPanel)
        updateBottomPanelsContainerVisibility()
        updateEraserSizeButtons()
    }
    
    private fun updateEraserSizeButtons() {
        val currentSize = canvasView.getEraserSize()
        val buttons = listOf(
            findViewById<Button>(R.id.btnEraserSmall),
            findViewById<Button>(R.id.btnEraserMedium),
            findViewById<Button>(R.id.btnEraserLarge),
            findViewById<Button>(R.id.btnEraserXLarge)
        )
        val sizes = floatArrayOf(10f, 20f, 40f, 60f)
        buttons.forEachIndexed { index, button ->
            button?.let {
                if (abs(sizes[index] - currentSize) < 1f) {
                    // 选中状态：蓝色背景
                    it.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2196F3.toInt())
                } else {
                    // 未选中状态：灰色背景
                    it.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF666666.toInt())
                }
            }
        }
    }
    
    private fun hideOptionPanel() {
        Log.d(TAG, "hideOptionPanel: 隐藏前 optionPanel.visibility=${optionPanel.visibility}")
        optionPanel.visibility = android.view.View.GONE
        lassoModeOptions.visibility = android.view.View.GONE
        eraserSizeOptions.visibility = android.view.View.GONE
        fontOptions.visibility = android.view.View.GONE
        updateBottomPanelsContainerVisibility()
        Log.d(TAG, "hideOptionPanel: 隐藏后 optionPanel.visibility=${optionPanel.visibility}")
    }
    
    private fun updateBottomPanelsContainerVisibility() {
        // 如果图层面板或选项面板任一可见，显示容器
        if (layerPanel.visibility == android.view.View.VISIBLE || optionPanel.visibility == android.view.View.VISIBLE) {
            bottomPanelsContainer.visibility = android.view.View.VISIBLE
        } else {
            bottomPanelsContainer.visibility = android.view.View.GONE
        }
    }
    
    private fun selectTool(tool: DrawingTool) {
        currentTool = tool
        canvasView.setTool(tool)
        
        // 更新所有按钮状态（使用背景色显示选中状态）
        findViewById<ImageButton>(R.id.btnPen)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        btnEraserBottom.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        btnLassoBottom.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        
        // 设置选中按钮的背景色
        when (tool) {
            DrawingTool.PEN -> findViewById<ImageButton>(R.id.btnPen)?.setBackgroundColor(0x3300BCD4.toInt())
            DrawingTool.ERASER -> {
                btnEraserBottom.setBackgroundColor(0x3300BCD4.toInt())
            }
            DrawingTool.LASSO -> {
                btnLassoBottom.setBackgroundColor(0x3300BCD4.toInt())
            }
            DrawingTool.MASK -> {} // 已移除胶布工具
        }
        
        Log.d(TAG, "工具切换为: $tool")
    }
    
    private fun setupLayerManager() {
        layerManager = LayerManager()
        
        // 添加默认图层
        layerManager.addLayer("图层 1", true)
        
        // 初始化画布
        canvasView.setLayerManager(layerManager)
        
        // 设置图层列表
        layerListView.layoutManager = LinearLayoutManager(this)
        layerListView.adapter = LayerListAdapter(layerManager) { layer ->
            // 切换图层显示/隐藏
            layerManager.toggleLayerVisibility(layer.id)
            canvasView.invalidate()
        }
        
        // 添加图层按钮
        btnAddLayer.setOnClickListener {
            val layerNumber = layerManager.getAllLayers().size + 1
            layerManager.addLayer("图层 $layerNumber", true)
            layerListView.adapter?.notifyDataSetChanged()
            // 确保图层面板高度根据内容自动调整
            layerPanel.requestLayout()
        }
        
        // 删除图层按钮
        btnRemoveLayer.setOnClickListener {
            val currentLayer = layerManager.getCurrentLayer()
            if (currentLayer != null && layerManager.getAllLayers().size > 1) {
                layerManager.removeLayer(currentLayer.id)
                layerListView.adapter?.notifyDataSetChanged()
                canvasView.invalidate()
                // 确保图层面板高度根据内容自动调整
                layerPanel.requestLayout()
                Toast.makeText(this, "图层已删除", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "至少保留一个图层", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun updateToolbarState() {
        btnUndo.isEnabled = canvasView.canUndo()
        btnRedo.isEnabled = canvasView.canRedo()
    }
    
    private fun loadQuestion() {
        questionId?.let { id ->
            // 加载题目信息（使用协程）
            lifecycleScope.launch {
                try {
                    val question = withContext(Dispatchers.IO) {
                        viewModel.getQuestionById(id)
                    }
                    question?.let {
                        currentQuestion = it
                        
                        // 加载题目图片
                        val imagePath = it.cleanedImagePath ?: it.imagePath
                        Log.d(TAG, "尝试加载题目图片: $imagePath")
                        if (imagePath.isNotBlank()) {
                            val imageFile = File(imagePath)
                            if (imageFile.exists()) {
                                questionImageView.visibility = android.view.View.VISIBLE
                                try {
                                    questionImageView.load(imageFile) {
                                        listener(
                                            onSuccess = { _, _ ->
                                                Log.d(TAG, "图片加载完成，drawable=${questionImageView.drawable != null}, isWritingMode=$isWritingMode")
                                                // 图片加载完成时，初始化 canvasInitialX 和 canvasInitialY（如果还未初始化）
                                                val currentX = canvasView.getTranslateX()
                                                val currentY = canvasView.getTranslateY()
                                                if (canvasInitialX == 0f && canvasInitialY == 0f) {
                                                    canvasInitialX = currentX
                                                    canvasInitialY = currentY
                                                    Log.d(TAG, "图片加载完成，初始化 canvasInitialX 和 canvasInitialY: ($canvasInitialX, $canvasInitialY)")
                                                }
                                                // 图片加载完成后，如果当前是查看模式，设置替换 ImageView 的 drawable
                                                if (!isWritingMode) {
                                                    questionImageViewReplacement.setImageDrawable(questionImageView.drawable)
                                                    questionImageViewReplacement.post {
                                                        val currentX = canvasView.getTranslateX()
                                                        val currentY = canvasView.getTranslateY()
                                                        val currentScale = canvasView.getScaleFactor()
                                                        Log.d(TAG, "图片加载完成，同步ImageView: translateX=$currentX, translateY=$currentY, scale=$currentScale")
                                                        syncImageViewTransform(questionImageViewReplacement, currentX, currentY, currentScale)
                                                        if (questionImageViewReplacement.width <= 0 || questionImageViewReplacement.height <= 0) {
                                                            setupImageViewLayoutListener(questionImageViewReplacement, currentX, currentY, currentScale)
                                                        }
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    Log.d(TAG, "✅ 题目图片加载成功: $imagePath")
                                } catch (e: Exception) {
                                    Log.e(TAG, "❌ 加载题目图片失败", e)
                                    questionImageView.visibility = android.view.View.GONE
                                }
                            } else {
                                Log.w(TAG, "⚠️ 题目图片文件不存在: $imagePath")
                                questionImageView.visibility = android.view.View.GONE
                            }
                        } else {
                            Log.d(TAG, "题目图片路径为空")
                            questionImageView.visibility = android.view.View.GONE
                        }
                        
                        Log.d(TAG, "题目加载成功: ${it.questionText}")
                    } ?: run {
                        questionImageView.visibility = android.view.View.GONE
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "加载题目失败", e)
                }
            }
        }
    }
    
    private fun setupFontButton() {
        // 字体按钮点击事件
        btnFont.setOnClickListener {
            Log.d(TAG, "字体按钮点击")
            Log.d(TAG, "optionPanel.visibility=${optionPanel.visibility}, fontOptions.visibility=${fontOptions.visibility}")
            
            // 检查字体选项面板是否已经显示
            val isVisible = optionPanel.visibility == android.view.View.VISIBLE && 
                           fontOptions.visibility == android.view.View.VISIBLE
            Log.d(TAG, "字体选项面板可见性: $isVisible")
            
            if (isVisible) {
                // 如果面板可见，关闭它
                Log.d(TAG, "关闭字体选项面板")
                hideOptionPanel()
            } else {
                // 如果面板不可见，显示它
                Log.d(TAG, "显示字体选项面板")
                showFontPanel()
            }
        }
        
        // 设置字体选择按钮
        findViewById<Button>(R.id.btnFontRegular)?.setOnClickListener {
            selectFont(FontType.REGULAR)
            hideOptionPanel()
            Toast.makeText(this, "已切换到: 常规", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnFontBold)?.setOnClickListener {
            selectFont(FontType.BOLD)
            hideOptionPanel()
            Toast.makeText(this, "已切换到: 粗体", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnFontElegant)?.setOnClickListener {
            selectFont(FontType.ELEGANT)
            hideOptionPanel()
            Toast.makeText(this, "已切换到: 优雅", Toast.LENGTH_SHORT).show()
        }
        
        findViewById<Button>(R.id.btnFontCalligraphy)?.setOnClickListener {
                selectFont(FontType.CALLIGRAPHY)
            hideOptionPanel()
            Toast.makeText(this, "已切换到: 书法", Toast.LENGTH_SHORT).show()
        }
        
        // 默认选择常规字体
            selectFont(FontType.REGULAR)
    }
    
    private fun showFontPanel() {
        optionPanelTitle.text = "选择字体"
        lassoModeOptions.visibility = android.view.View.GONE
        eraserSizeOptions.visibility = android.view.View.GONE
        fontOptions.visibility = android.view.View.VISIBLE
        optionPanel.visibility = android.view.View.VISIBLE
        
        // 使用 post 确保按钮位置已经正确计算
        optionPanel.post {
            positionPanelBelowButton(btnFont, optionPanel)
        }
        
        updateBottomPanelsContainerVisibility()
        updateFontButtons()
        Log.d(TAG, "显示字体选项面板: optionPanel.visibility=${optionPanel.visibility}, fontOptions.visibility=${fontOptions.visibility}")
    }
    
    private fun selectFont(fontType: FontType) {
        currentFont = fontType
        updateFontButtons()
        Log.d(TAG, "字体已切换为: ${fontType.displayName}")
    }
    
    private fun updateFontButtons() {
        val buttons = listOf(
            findViewById<Button>(R.id.btnFontRegular),
            findViewById<Button>(R.id.btnFontBold),
            findViewById<Button>(R.id.btnFontElegant),
            findViewById<Button>(R.id.btnFontCalligraphy)
        )
        val fontTypes = listOf(FontType.REGULAR, FontType.BOLD, FontType.ELEGANT, FontType.CALLIGRAPHY)
        buttons.forEachIndexed { index, button ->
            button?.let {
                if (currentFont == fontTypes[index]) {
                    // 选中状态：蓝色背景
                    it.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF2196F3.toInt())
            } else {
                    // 未选中状态：灰色背景
                    it.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF666666.toInt())
                }
            }
        }
    }
    
    private fun setupHandwritingRecognition() {
        // 设置画布的抬笔回调，自动识别
        canvasView.setOnStrokeFinished {
            recognizeCurrentStroke()
        }
    }
    
    private fun recognizeCurrentStroke() {
        // 如果实时美化未开启，不进行识别
        if (!isBeautifyEnabled) return
        
        // 如果当前工具是橡皮擦、套索或胶布工具，不进行识别
        if (currentTool == DrawingTool.ERASER || 
            currentTool == DrawingTool.LASSO || 
            currentTool == DrawingTool.MASK) {
            return
        }
        
        if (isRecognizing) return
        
        val strokes = canvasView.getCurrentStrokes()
        if (strokes.isEmpty()) return
        
        isRecognizing = true
        lifecycleScope.launch {
            try {
                val recognizedText = withContext(Dispatchers.IO) {
                    HandwritingRecognitionService.recognizeHandwriting(strokes)
                }
                
                if (recognizedText != null && recognizedText.isNotBlank()) {
                    // 在画布上添加识别后的文字（使用当前字体）
                    canvasView.addRecognizedText(
                        text = recognizedText,
                        typeface = currentFont.getTypeface(this@HandwritingNoteActivity),
                        fontSize = 0f, // 自动计算大小
                        textColor = android.graphics.Color.BLACK
                    )
                    Toast.makeText(this@HandwritingNoteActivity, "识别成功: $recognizedText", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "识别失败", e)
            } finally {
                isRecognizing = false
            }
        }
    }
    
    /**
     * 字体类型枚举（从 ScreenHandwritingActivity 复制）
     */
    enum class FontType(val displayName: String, val fontSizeSp: Int, val fontFileName: String) {
        REGULAR("常规", 18, "regular.ttf"),
        BOLD("粗体", 18, "bold.ttf"),
        ELEGANT("优雅", 20, "elegant.ttf"),
        CALLIGRAPHY("书法", 22, "calligraphy.ttf");

        fun getTypeface(context: Context): Typeface {
            return try {
                val typeface = Typeface.createFromAsset(context.assets, "fonts/$fontFileName")
                Log.d("FontType", "✅ 成功加载字体: fonts/$fontFileName")
                typeface
            } catch (e: RuntimeException) {
                // Font file not found, use system default
                when (this) {
                    REGULAR -> Typeface.DEFAULT
                    BOLD -> Typeface.DEFAULT_BOLD
                    ELEGANT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
                    CALLIGRAPHY -> Typeface.SERIF
                }
            } catch (e: Exception) {
                Log.w("FontType", "字体文件 fonts/$fontFileName 加载失败，使用系统默认字体", e)
                when (this) {
                    REGULAR -> Typeface.DEFAULT
                    BOLD -> Typeface.DEFAULT_BOLD
                    ELEGANT -> Typeface.create("sans-serif-light", Typeface.NORMAL)
                    CALLIGRAPHY -> Typeface.SERIF
                }
            }
        }
    }
}

/**
 * 图层管理器
 */
class LayerManager {
    private val layers = mutableListOf<Layer>()
    private var currentLayerId: Int = -1
    
    data class Layer(
        val id: Int,
        var name: String,
        var visible: Boolean = true,
        var locked: Boolean = false,
        val strokes: MutableList<InfiniteCanvasView.Stroke> = mutableListOf()
    )
    
    fun addLayer(name: String, visible: Boolean = true): Layer {
        val id = layers.size + 1
        val layer = Layer(id, name, visible)
        layers.add(layer)
        currentLayerId = id
        return layer
    }
    
    fun removeLayer(id: Int) {
        layers.removeAll { it.id == id }
        if (currentLayerId == id && layers.isNotEmpty()) {
            currentLayerId = layers.last().id
        }
    }
    
    fun getCurrentLayer(): Layer? {
        return layers.find { it.id == currentLayerId }
    }
    
    fun getAllLayers(): List<Layer> = layers.toList()
    
    fun toggleLayerVisibility(id: Int) {
        layers.find { it.id == id }?.let { it.visible = !it.visible }
    }
    
    fun setCurrentLayer(id: Int) {
        if (layers.any { it.id == id }) {
            currentLayerId = id
        }
    }
}

/**
 * 图层列表适配器
 */
class LayerListAdapter(
    private val layerManager: LayerManager,
    private val onVisibilityToggle: (LayerManager.Layer) -> Unit
) : RecyclerView.Adapter<LayerListAdapter.LayerViewHolder>() {
    
    class LayerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.layerNameText)
        val visibilityToggle: Switch = itemView.findViewById(R.id.layerVisibilityToggle)
        val deleteButton: ImageButton = itemView.findViewById(R.id.layerDeleteButton)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LayerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_layer, parent, false)
        return LayerViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: LayerViewHolder, position: Int) {
        val layers = layerManager.getAllLayers().reversed()
        if (position < layers.size) {
            val layer = layers[position]
            holder.nameText.text = layer.name
            holder.visibilityToggle.isChecked = layer.visible
            holder.visibilityToggle.setOnCheckedChangeListener { _, _ ->
                onVisibilityToggle(layer)
            }
            holder.deleteButton.setOnClickListener {
                layerManager.removeLayer(layer.id)
                notifyDataSetChanged()
            }
        }
    }
    
    override fun getItemCount(): Int = layerManager.getAllLayers().size
}

