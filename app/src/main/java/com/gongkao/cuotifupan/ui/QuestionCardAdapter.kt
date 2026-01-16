package com.gongkao.cuotifupan.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.NoteItem
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.util.PreferencesManager
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import android.view.MotionEvent
import android.content.Intent
import android.widget.ImageButton
import android.util.Log
import android.widget.FrameLayout
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import android.graphics.BitmapFactory
import com.gongkao.cuotifupan.api.HandwritingRemovalService
import kotlinx.coroutines.withContext
import com.gongkao.cuotifupan.util.ImageEditor
import androidx.lifecycle.lifecycleScope

/**
 * 题目卡片适配器（用于 ViewPager2）
 */
class QuestionCardAdapter(
    private val onEditTags: (Question) -> Unit,
    private val onQuestionUpdate: (Question) -> Unit,
    private val onEditImage: (Question) -> Unit,
    private val onDelete: (Question) -> Unit,
    private val onImageClick: ((Question) -> Unit)? = null
) : ListAdapter<Question, QuestionCardAdapter.QuestionCardViewHolder>(QuestionDiffCallback()) {
    
    // 保存每个题目的输入类型状态（题目ID -> 输入类型）
    private val questionInputTypeMap = mutableMapOf<String, String>()
    private val questionNoteExpandedMap = mutableMapOf<String, Boolean>()
    private val questionFlashcardExpandedMap = mutableMapOf<String, Boolean>()
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuestionCardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_question_card, parent, false)
        return QuestionCardViewHolder(view, onQuestionUpdate, onEditImage, onDelete, onImageClick)
    }
    
    override fun onBindViewHolder(holder: QuestionCardViewHolder, position: Int) {
        val question = getItem(position)
        val savedInputType = questionInputTypeMap[question.id] ?: "note"
        val savedNoteExpanded = questionNoteExpandedMap[question.id] ?: true
        val savedFlashcardExpanded = questionFlashcardExpandedMap[question.id] ?: true
        holder.bind(question, onEditTags, savedInputType, savedNoteExpanded, savedFlashcardExpanded) { questionId, inputType, noteExpanded, flashcardExpanded ->
            questionInputTypeMap[questionId] = inputType
            questionNoteExpandedMap[questionId] = noteExpanded
            questionFlashcardExpandedMap[questionId] = flashcardExpanded
        }
    }
    
    // 存储当前 ViewHolder 的引用，用于外部调用
    private val viewHolders = mutableMapOf<Int, QuestionCardViewHolder>()
    
    override fun onViewAttachedToWindow(holder: QuestionCardViewHolder) {
        super.onViewAttachedToWindow(holder)
        viewHolders[holder.adapterPosition] = holder
    }
    
    override fun onViewDetachedFromWindow(holder: QuestionCardViewHolder) {
        super.onViewDetachedFromWindow(holder)
        viewHolders.remove(holder.adapterPosition)
    }
    
    fun notifyInputFocus(position: Int) {
        viewHolders[position]?.scrollToInputIfNeeded()
    }
    
    class QuestionCardViewHolder(
        itemView: View,
        private val onQuestionUpdate: (Question) -> Unit,
        private val onEditImage: (Question) -> Unit,
        private val onDelete: (Question) -> Unit,
        private val onImageClick: ((Question) -> Unit)? = null
    ) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.questionImageView)
        private val tagsRecyclerView: RecyclerView = itemView.findViewById(R.id.tagsContainer)
        private val notesRecyclerView: RecyclerView = itemView.findViewById(R.id.notesRecyclerView)
        private val addNoteEditText: EditText = itemView.findViewById(R.id.addNoteEditText)
        private val addNoteButton: Button = itemView.findViewById(R.id.addNoteButton)
        private val noteTypeButton: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.noteTypeButton)
        private val flashcardTypeButton: com.google.android.material.button.MaterialButton = itemView.findViewById(R.id.flashcardTypeButton)
        private val addNoteContainer: android.widget.LinearLayout = itemView.findViewById(R.id.addNoteContainer)
        private val addFlashcardContainer: android.widget.LinearLayout = itemView.findViewById(R.id.addFlashcardContainer)
        private val addFlashcardFrontEditText: EditText = itemView.findViewById(R.id.addFlashcardFrontEditText)
        private val addFlashcardBackEditText: EditText = itemView.findViewById(R.id.addFlashcardBackEditText)
        private val addFlashcardButton: Button = itemView.findViewById(R.id.addFlashcardButton)
        private val toggleNoteInputButton: android.widget.TextView = itemView.findViewById(R.id.toggleNoteInputButton)
        private val toggleFlashcardInputButton: android.widget.TextView = itemView.findViewById(R.id.toggleFlashcardInputButton)
        private val addNoteInputLayout: android.widget.LinearLayout = itemView.findViewById(R.id.addNoteInputLayout)
        private val addFlashcardInputLayout: android.widget.LinearLayout = itemView.findViewById(R.id.addFlashcardInputLayout)
        private val notesAdapter: NotesAdapter = NotesAdapter(
            onNoteDelete = { note -> deleteNote(note) },
            showSimpleFlashcard = true // 题目卡片页面使用简单格式
        )
        private var currentQuestion: Question? = null
        private var currentInputType: String = "note" // "note" 或 "flashcard"
        private var isNoteInputExpanded = true
        private var isFlashcardInputExpanded = true
        
        private val imageScrollView: androidx.core.widget.NestedScrollView = itemView.findViewById(R.id.imageScrollView)
        private val imageFrameLayout: FrameLayout = imageScrollView.parent as FrameLayout // 获取包含图片的 FrameLayout
        private val imageContainer: ViewGroup = imageFrameLayout.parent as ViewGroup // 获取图片区域的容器（RelativeLayout，有weight）
        private val notesScrollView: androidx.core.widget.NestedScrollView = itemView.findViewById(R.id.notesScrollView)
        private val notesContainer: android.widget.LinearLayout = itemView.findViewById(R.id.notesContainer)
        private val resizeHandle: View = itemView.findViewById(R.id.resizeHandle)
        private val editImageButton: ImageButton = itemView.findViewById(R.id.editImageButton)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.deleteButton)
        private val removeHandwritingButton: ImageButton = itemView.findViewById(R.id.removeHandwritingButton)
        private val btnHandwritingMode: ImageButton = itemView.findViewById(R.id.btnHandwritingMode)
        private val removeAnswerMarksButton: ImageButton = itemView.findViewById(R.id.removeAnswerMarksButton)
        private val toggleImageButton: ImageButton = itemView.findViewById(R.id.toggleImageButton)
        
        // 当前显示的图片类型：true = 擦写后，false = 原图
        private var showingCleanedImage: Boolean = true
        
        // 当前显示的图片类型：true = 清除对错痕迹后，false = 原图
        private var showingHiddenOptionsImage: Boolean = false
        
        /**
         * 设置图片为原图大小（宽度匹配视图，高度按比例）
         * 注意：现在使用 fitCenter，不再强制设置高度
         */
        private fun setImageSizeToOriginal() {
            // 不再强制设置图片高度，让 adjustViewBounds 和 fitCenter 自然处理
            // 只恢复滚动位置
            imageView.post {
                val scrollViewHeight = imageScrollView.height
                val drawable = imageView.drawable
                if (drawable != null) {
                    val imageHeight = imageView.height
                    val savedScrollY = PreferencesManager.getImageScrollPositionForQuestion(
                        imageView.context,
                        currentQuestion?.id ?: ""
                    )
                    
                    if (savedScrollY > 0 && savedScrollY < imageHeight) {
                        // 有保存的位置，且位置有效，恢复到保存的位置
                        imageScrollView.scrollTo(0, savedScrollY)
                        Log.d("QuestionCardAdapter", "恢复题目 ${currentQuestion?.id} 的滚动位置: $savedScrollY")
                    } else if (imageHeight > scrollViewHeight) {
                        // 没有保存的位置，滚动到中间位置
                        val scrollY = (imageHeight - scrollViewHeight) / 2
                        imageScrollView.smoothScrollTo(0, scrollY)
                    }
                }
            }
        }
        
        private var initialY: Float = 0f
        private var initialX: Float = 0f
        private var initialImageWeight: Float = 0f
        private var initialNotesWeight: Float = 0f
        private var isResizing: Boolean = false
        private var lastLayoutTime: Long = 0
        private val LAYOUT_THROTTLE_MS = 8L // 约120fps，更流畅的响应
        
        init {
            // 初始化手写擦除服务
            HandwritingRemovalService.init(itemView.context.applicationContext)
            
            // 设置笔记列表
            notesRecyclerView.layoutManager = LinearLayoutManager(itemView.context)
            notesRecyclerView.adapter = notesAdapter
            
            // 添加笔记按钮
            addNoteButton.setOnClickListener {
                addNote()
            }
            
            // 添加卡片按钮
            addFlashcardButton.setOnClickListener {
                addFlashcard()
            }
            
            // 类型切换按钮
            noteTypeButton.setOnClickListener {
                switchInputType("note")
            }
            flashcardTypeButton.setOnClickListener {
                switchInputType("flashcard")
            }
            
            // 折叠/展开按钮（展开时显示 ▼，折叠时显示 ▲）
            toggleNoteInputButton.setOnClickListener {
                isNoteInputExpanded = !isNoteInputExpanded
                addNoteInputLayout.visibility = if (isNoteInputExpanded) View.VISIBLE else View.GONE
                toggleNoteInputButton.text = if (isNoteInputExpanded) "▼" else "▲"
            }
            toggleFlashcardInputButton.setOnClickListener {
                isFlashcardInputExpanded = !isFlashcardInputExpanded
                addFlashcardInputLayout.visibility = if (isFlashcardInputExpanded) View.VISIBLE else View.GONE
                toggleFlashcardInputButton.text = if (isFlashcardInputExpanded) "▼" else "▲"
            }
            
            // 初始化折叠状态
            addNoteInputLayout.visibility = if (isNoteInputExpanded) View.VISIBLE else View.GONE
            addFlashcardInputLayout.visibility = if (isFlashcardInputExpanded) View.VISIBLE else View.GONE
            toggleNoteInputButton.text = if (isNoteInputExpanded) "▼" else "▲"
            toggleFlashcardInputButton.text = if (isFlashcardInputExpanded) "▼" else "▲"
            
            // 设置输入框焦点监听，自动滚动到输入框位置
            setupInputFocusListener()
            
            // 设置拖拽分隔线的触摸监听
            setupResizeHandle()
            
            // 设置编辑图片按钮
            editImageButton.setOnClickListener {
                val question = currentQuestion ?: return@setOnClickListener
                onEditImage(question)
            }
            
            // 设置手写模式按钮
            btnHandwritingMode.setOnClickListener {
                val question = currentQuestion ?: return@setOnClickListener
                HandwritingNoteActivity.start(itemView.context, question.id)
            }
            
            // 设置删除按钮
            deleteButton.setOnClickListener {
                val question = currentQuestion ?: return@setOnClickListener
                // 显示删除确认对话框
                androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                    .setTitle("删除题目")
                    .setMessage("确定要删除这道题目吗？此操作不可恢复。")
                    .setPositiveButton("删除") { _, _ ->
                        onDelete(question)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            
            // 设置手写擦除/还原按钮（合并为一个按钮）
            removeHandwritingButton.setOnClickListener {
                val question = currentQuestion ?: return@setOnClickListener
                // 如果已有擦除后的图片
                if (!question.cleanedImagePath.isNullOrBlank()) {
                    // 当前显示擦除后的图片 -> 还原到原图
                    if (showingCleanedImage) {
                        restoreOriginalImage(question)
                    } else {
                        // 当前显示原图 -> 直接切换到已擦除的图片（不重新请求API）
                        showCleanedImage(question)
                    }
                } else {
                    // 没有擦除后的图片，执行擦除（调用API）
                    removeHandwriting(question)
                }
            }
            
            // 设置清除对错痕迹按钮（支持切换）
            removeAnswerMarksButton.setOnClickListener {
                val question = currentQuestion ?: return@setOnClickListener
                // 如果已有清除后的图片
                if (!question.hiddenOptionsImagePath.isNullOrBlank()) {
                    // 当前显示清除后的图片 -> 还原到原图
                    if (showingHiddenOptionsImage) {
                        restoreOriginalFromHiddenOptions(question)
                    } else {
                        // 当前显示原图 -> 直接切换到已清除的图片（不重新处理）
                        showHiddenOptionsImage(question)
                    }
                } else {
                    // 没有清除后的图片，执行清除处理
                    hideOptions(question)
                }
            }
        }
        
        /**
         * 还原到原图
         */
        private fun restoreOriginalImage(question: Question) {
            // 切换到原图
            showingCleanedImage = false
            
            // 确定要显示的图片路径
            val imagePathToShow = question.originalImagePath ?: question.imagePath
            
            // 加载图片
            loadImage(imagePathToShow)
            
            // 更新按钮状态
            updateRemoveHandwritingButton()
        }
        
        /**
         * 显示已擦除的图片（不重新请求API，直接使用已有的擦除结果）
         */
        private fun showCleanedImage(question: Question) {
            // 切换到擦除后的图片
            showingCleanedImage = true
            
            // 使用已有的擦除后的图片路径
            val cleanedImagePath = question.cleanedImagePath ?: return
            
            // 检查文件是否存在
            val file = File(cleanedImagePath)
            if (!file.exists()) {
                // 如果文件不存在，说明之前的擦除结果丢失，需要重新擦除
                Log.w("QuestionCardAdapter", "擦除后的图片文件不存在，重新执行擦除: $cleanedImagePath")
                removeHandwriting(question)
                return
            }
            
            // 加载擦除后的图片
            loadImage(cleanedImagePath)
            
            // 更新按钮状态（现在显示"还原"按钮）
            updateRemoveHandwritingButton()
            
            Log.d("QuestionCardAdapter", "直接显示已擦除的图片: $cleanedImagePath")
        }
        
        /**
         * 更新擦除/还原按钮的状态和图标
         */
        private fun updateRemoveHandwritingButton() {
            val question = currentQuestion ?: return
            
            // 如果有擦除后的图片且当前显示擦除后的，显示"还原"按钮
            if (!question.cleanedImagePath.isNullOrBlank() && showingCleanedImage) {
                removeHandwritingButton.contentDescription = "还原原图"
                removeHandwritingButton.setImageResource(android.R.drawable.ic_menu_revert)
            } else {
                // 否则显示"擦除"按钮
                removeHandwritingButton.contentDescription = "擦除手写"
                removeHandwritingButton.setImageResource(android.R.drawable.ic_menu_revert)
            }
        }
        
        /**
         * 加载图片
         */
        private fun loadImage(imagePath: String) {
            val context = itemView.context
            val file = File(imagePath)
            
            // 先清除 ImageView 的图片和矩阵，确保重新加载
            imageView.setImageDrawable(null)
            imageView.imageMatrix = android.graphics.Matrix()
            imageView.layoutParams.height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            
            if (file.exists() && (imagePath.startsWith(context.filesDir.absolutePath) || 
                imagePath.startsWith(context.cacheDir.absolutePath) ||
                imagePath.contains("/DCIM/Camera/"))) {
                val fileLastModified = file.lastModified()
                val fileUri = android.net.Uri.fromFile(file)
                val uriWithTimestamp = fileUri.buildUpon()
                    .appendQueryParameter("mtime", fileLastModified.toString())
                    .build()
                
                imageView.load(uriWithTimestamp) {
                    listener(
                        onSuccess = { _, result ->
                            imageView.post {
                                setImageSizeToOriginal()
                            }
                        },
                        onError = { _, _ ->
                            // 加载失败，显示占位图
                            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                        }
                    )
                }
            } else if (com.gongkao.cuotifupan.util.ImageAccessHelper.isValidImage(context, imagePath)) {
                val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(context, imagePath)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    imageView.post {
                        setImageSizeToOriginal()
                    }
                } else {
                    imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                }
            } else {
                // 图片文件不存在，显示占位图
                imageView.setImageResource(android.R.drawable.ic_menu_report_image)
            }
        }
        
        private fun setupResizeHandle() {
            resizeHandle.setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isResizing = true
                        initialY = event.rawY
                        initialX = event.rawX
                        // 获取图片区域容器的 weight
                        initialImageWeight = (imageContainer.layoutParams as? android.widget.LinearLayout.LayoutParams)?.weight ?: 0.5f
                        // 获取 notesContainer 的 weight（笔记区域）
                        initialNotesWeight = (notesContainer.layoutParams as? android.widget.LinearLayout.LayoutParams)?.weight ?: 0.5f
                        // 阻止父视图（ViewPager2）拦截触摸事件，避免滑动切换题目
                        view.parent?.requestDisallowInterceptTouchEvent(true)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isResizing) {
                            // 检测屏幕方向：检查分隔线的尺寸（横屏时宽度小高度大，竖屏时宽度大高度小）
                            val isLandscape = resizeHandle.width < resizeHandle.height
                            
                            val totalWeight = initialImageWeight + initialNotesWeight
                            var deltaWeight: Float
                            
                            if (isLandscape) {
                                // 横屏模式：左右拖拽
                                val deltaX = event.rawX - initialX
                                // 获取包含图片区域和笔记区域的父布局（横向LinearLayout）
                                val parentContainer = resizeHandle.parent as? android.widget.LinearLayout
                                val parentWidth = parentContainer?.width ?: resizeHandle.parent?.let { (it as? View)?.width } ?: return@setOnTouchListener false
                                
                                // 计算新的weight比例（基于像素变化）
                                // 往右拉（deltaX > 0）应该增加图片区域，往左拉（deltaX < 0）应该减少图片区域
                                deltaWeight = (deltaX / parentWidth) * totalWeight
                            } else {
                                // 竖屏模式：上下拖拽
                            val deltaY = event.rawY - initialY
                                // 获取包含图片区域和笔记区域的父布局（纵向LinearLayout）
                                val parentContainer = resizeHandle.parent as? ViewGroup
                                val parentHeight = parentContainer?.height ?: (itemView.parent as? View)?.height ?: return@setOnTouchListener false
                            
                            // 计算新的weight比例（基于像素变化）
                            // 往上拉（deltaY < 0）应该增加图片区域，往下拉（deltaY > 0）应该减少图片区域
                                deltaWeight = (deltaY / parentHeight) * totalWeight
                            // 反转方向：往上拉增加图片区域，往下拉减少图片区域
                                deltaWeight = -deltaWeight
                            }
                            
                            var newImageWeight = initialImageWeight + deltaWeight
                            var newNotesWeight = initialNotesWeight - deltaWeight
                            
                            // 限制最小尺寸（图片区域至少20%，笔记区域至少30%）
                            val minImageWeight = totalWeight * 0.2f
                            val minNotesWeight = totalWeight * 0.3f
                            
                            newImageWeight = newImageWeight.coerceAtLeast(minImageWeight)
                            newNotesWeight = newNotesWeight.coerceAtLeast(minNotesWeight)
                            
                            // 确保总和不变
                            val total = newImageWeight + newNotesWeight
                            newImageWeight = newImageWeight * totalWeight / total
                            newNotesWeight = newNotesWeight * totalWeight / total
                            
                            // 应用新的weight到图片区域容器
                            (imageContainer.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let {
                                it.weight = newImageWeight
                                imageContainer.layoutParams = it
                            }
                            
                            // 应用新的weight到 notesContainer（笔记区域）
                            (notesContainer.layoutParams as? android.widget.LinearLayout.LayoutParams)?.let {
                                it.weight = newNotesWeight
                                notesContainer.layoutParams = it
                            }
                            
                            // 节流布局请求，提高拖动响应速度
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastLayoutTime >= LAYOUT_THROTTLE_MS) {
                                // 直接请求布局，不使用 post 避免延迟
                                val parent = itemView.parent as? ViewGroup
                                parent?.requestLayout()
                                lastLayoutTime = currentTime
                            }
                            // 无论是否请求布局，都更新视图以确保视觉反馈
                            imageScrollView.invalidate()
                            notesContainer.invalidate()
                            
                            true
                        } else {
                            false
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isResizing) {
                            isResizing = false
                            
                            // 恢复父视图的触摸事件拦截（允许 ViewPager2 正常滑动）
                            resizeHandle.parent?.requestDisallowInterceptTouchEvent(false)
                            
                            // 保存调整后的高度比例（为当前题目单独保存）
                            val question = currentQuestion
                            if (question != null) {
                                val imageParams = imageContainer.layoutParams as? android.widget.LinearLayout.LayoutParams
                                val notesParams = notesContainer.layoutParams as? android.widget.LinearLayout.LayoutParams
                                if (imageParams != null && notesParams != null) {
                                    val totalWeight = imageParams.weight + notesParams.weight
                                    if (totalWeight > 0) {
                                        val imageRatio = imageParams.weight / totalWeight
                                        PreferencesManager.saveImageHeightRatioForQuestion(itemView.context, question.id, imageRatio)
                                        Log.d("QuestionCardAdapter", "已保存题目 ${question.id} 的图片区域高度比例: $imageRatio (imageWeight=${imageParams.weight}, notesWeight=${notesParams.weight})")
                                    }
                                }
                            }
                            
                            // 确保最终布局更新
                            val parent = itemView.parent as? ViewGroup
                            parent?.requestLayout()
                            
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
        }
        
        private fun setupInputFocusListener() {
            // 监听笔记输入框焦点
            addNoteEditText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    scrollToInputView(view)
                }
            }
            
            // 监听卡片输入框焦点
            addFlashcardFrontEditText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    scrollToInputView(view)
                }
            }
            
            addFlashcardBackEditText.setOnFocusChangeListener { view, hasFocus ->
                if (hasFocus) {
                    scrollToInputView(view)
                }
            }
        }
        
        private fun scrollToInputView(inputView: View) {
            // 立即尝试一次
            scrollToInputIfNeeded()
            
            // 延迟一下，等待软键盘弹出，再尝试一次
            inputView.postDelayed({
                scrollToInputIfNeeded()
            }, 300) // 延迟300ms，等待软键盘弹出
            
            // 再延迟一次，确保软键盘完全弹出后再次调整
            inputView.postDelayed({
                scrollToInputIfNeeded()
            }, 600) // 延迟600ms，确保软键盘完全弹出
        }
        
        fun scrollToInputIfNeeded() {
            try {
                // 获取当前可见的输入框
                val inputView = when {
                    addNoteContainer.visibility == View.VISIBLE && addNoteInputLayout.visibility == View.VISIBLE -> {
                        addNoteEditText
                    }
                    addFlashcardContainer.visibility == View.VISIBLE && addFlashcardInputLayout.visibility == View.VISIBLE -> {
                        // 优先检查第二个输入框（背面），因为它更靠下
                        if (addFlashcardBackEditText.hasFocus() || addFlashcardBackEditText.text.isNotEmpty()) {
                            addFlashcardBackEditText
                        } else {
                            addFlashcardFrontEditText
                        }
                    }
                    else -> null
                } ?: return
                
                // 方法1: 使用 requestRectangleOnScreen 确保输入框可见
                // 这会自动滚动父容器，使输入框在可见区域内
                val rect = android.graphics.Rect()
                inputView.getHitRect(rect)
                // 扩展矩形，留出一些边距（底部留更多空间）
                rect.inset(-50, -200) // 底部留200px空间给软键盘
                
                // 从输入框向上查找可滚动的父容器
                var parent: View? = inputView.parent as? View
                while (parent != null && parent != itemView) {
                    if (parent is androidx.core.widget.NestedScrollView || 
                        parent is android.widget.ScrollView ||
                        parent is androidx.recyclerview.widget.RecyclerView) {
                        parent.requestRectangleOnScreen(rect, true)
                        break
                    }
                    parent = parent.parent as? View
                }
                
                // 方法2: 直接滚动整个 itemView
                itemView.post {
                    val location = IntArray(2)
                    inputView.getLocationInWindow(location)
                    val itemLocation = IntArray(2)
                    itemView.getLocationInWindow(itemLocation)
                    
                    val relativeRect = android.graphics.Rect()
                    relativeRect.left = location[0] - itemLocation[0]
                    relativeRect.top = location[1] - itemLocation[1]
                    relativeRect.right = relativeRect.left + inputView.width
                    relativeRect.bottom = relativeRect.top + inputView.height
                    relativeRect.inset(-100, -300) // 底部留300px空间
                    
                    itemView.requestRectangleOnScreen(relativeRect, true)
                }
                
                // 方法3: 如果还是不行，尝试滚动 ViewPager2 的父容器
                itemView.postDelayed({
                    val viewPager = itemView.parent?.parent?.parent as? androidx.viewpager2.widget.ViewPager2
                    if (viewPager != null) {
                        val location = IntArray(2)
                        inputView.getLocationInWindow(location)
                        val viewPagerLocation = IntArray(2)
                        viewPager.getLocationInWindow(viewPagerLocation)
                        
                        val relativeRect = android.graphics.Rect()
                        relativeRect.left = location[0] - viewPagerLocation[0]
                        relativeRect.top = location[1] - viewPagerLocation[1]
                        relativeRect.right = relativeRect.left + inputView.width
                        relativeRect.bottom = relativeRect.top + inputView.height
                        relativeRect.inset(-100, -300)
                        
                        viewPager.requestRectangleOnScreen(relativeRect, true)
                    }
                }, 50)
            } catch (e: Exception) {
                Log.e("QuestionCardAdapter", "Error scrolling to input", e)
            }
        }
        
        private var onStateChanged: ((String, String, Boolean, Boolean) -> Unit)? = null
        
        fun bind(
            question: Question, 
            onEditTags: (Question) -> Unit,
            savedInputType: String,
            savedNoteExpanded: Boolean,
            savedFlashcardExpanded: Boolean,
            onStateChanged: (String, String, Boolean, Boolean) -> Unit
        ) {
            this.onStateChanged = onStateChanged
            
            // 更新 currentQuestion
            currentQuestion = question
            
            // 使用传入的保存状态
            currentInputType = savedInputType
            isNoteInputExpanded = savedNoteExpanded
            isFlashcardInputExpanded = savedFlashcardExpanded
            
            // 设置图片显示状态和按钮状态
            if (!question.cleanedImagePath.isNullOrBlank()) {
                // 有擦写后的图片，默认显示擦写后的图片
                showingCleanedImage = true
            } else {
                // 没有擦写后的图片，显示原图
                showingCleanedImage = false
            }
            
            // 初始化清除对错痕迹的显示状态
            // 如果有清除后的图片路径，默认显示清除后的图片（类似擦写后的图片）
            if (!question.hiddenOptionsImagePath.isNullOrBlank()) {
                // 有清除后的图片，默认显示清除后的图片
                showingHiddenOptionsImage = true
            } else {
                // 没有清除后的图片，显示原图
                showingHiddenOptionsImage = false
            }
            
            // 隐藏原来的切换按钮（不再需要）
            toggleImageButton.visibility = View.GONE
            
            // 更新擦除/还原按钮状态
            updateRemoveHandwritingButton()
            
            Log.d("QuestionCardAdapter", "bind: question.id = ${question.id}, savedInputType = $savedInputType, currentInputType = $currentInputType")
            
            // 设置滚动监听，保存滚动位置（使用节流避免频繁保存）
            var lastSavedScrollY = -1
            var scrollSaveRunnable: Runnable? = null
            val scrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
            
            imageScrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
                // 节流：只在滚动停止500ms后保存，避免频繁写入
                scrollSaveRunnable?.let { scrollHandler.removeCallbacks(it) }
                scrollSaveRunnable = Runnable {
                    if (scrollY != lastSavedScrollY && scrollY >= 0 && question.id.isNotBlank()) {
                        PreferencesManager.saveImageScrollPositionForQuestion(itemView.context, question.id, scrollY)
                        lastSavedScrollY = scrollY
                        Log.d("QuestionCardAdapter", "保存题目 ${question.id} 的滚动位置: $scrollY")
            }
                }
                scrollHandler.postDelayed(scrollSaveRunnable!!, 500) // 500ms 后保存
            }
            
            // 恢复该题目保存的高度比例，如果没有保存则使用默认值
            val savedImageRatio = PreferencesManager.getImageHeightRatioForQuestion(itemView.context, question.id)
            val totalWeight = 1.0f // 默认总权重：0.5 + 0.5 = 1.0
            val imageWeight = savedImageRatio * totalWeight
            val notesWeight = (1.0f - savedImageRatio) * totalWeight
            
            Log.d("QuestionCardAdapter", "恢复题目 ${question.id} 的图片区域高度比例: $savedImageRatio (imageWeight=$imageWeight, notesWeight=$notesWeight)")
            
            // 应用保存的weight比例到图片区域容器
            val imageParams = imageContainer.layoutParams as? android.widget.LinearLayout.LayoutParams
            if (imageParams != null) {
                imageParams.weight = imageWeight
                imageContainer.layoutParams = imageParams
            }
            
            // 应用保存的weight比例到 notesContainer（笔记区域）
            val notesParams = notesContainer.layoutParams as? android.widget.LinearLayout.LayoutParams
            if (notesParams != null) {
                notesParams.weight = notesWeight
                notesScrollView.layoutParams = notesParams
            }
            
            // 强制应用布局，确保保存的比例立即生效
            itemView.post {
                (itemView.parent as? ViewGroup)?.requestLayout()
            }
            
            // 确定要显示的图片路径（优先级：清除对错痕迹后 > 擦写后 > 原图）
            val imagePathToShow = when {
                // 如果显示清除对错痕迹后的图片
                showingHiddenOptionsImage && !question.hiddenOptionsImagePath.isNullOrBlank() -> {
                    question.hiddenOptionsImagePath
                }
                // 如果显示擦写后的图片
                showingCleanedImage && !question.cleanedImagePath.isNullOrBlank() -> {
                    question.cleanedImagePath
                }
                // 否则显示原图
                else -> {
                    question.originalImagePath ?: question.imagePath
                }
            }
            
            // 加载图片（全图显示，保持原始宽高比）
            loadImage(imagePathToShow)
            
            // 更新清除对错痕迹按钮状态
            updateHideOptionsButton(question)
            
            // 设置图片点击事件，打开全屏查看页面
            imageView.setOnClickListener {
                onImageClick?.invoke(question)
            }
            
            // 解析标签
            val tags = try {
                if (question.tags.isNotBlank()) {
                    JSONArray(question.tags).let { array ->
                        (0 until array.length()).map { array.getString(it) }
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
            
            Log.d("QuestionCardAdapter", "题目 ${question.id} 的标签: $tags")
            
            // 直接使用保存的标签，不自动添加类型标签
            val allTags = tags.filter { it != "__NO_TYPE_TAG__" }.toMutableList() // 移除特殊标记（如果存在）
            
            Log.d("QuestionCardAdapter", "题目 ${question.id} 的最终标签列表: $allTags")
            
            // 显示标签（支持"增加标签"按钮）
            val tagsAdapter = TagChipAdapter(
                onAddTagClick = {
                    onEditTags(question)
                }
            )
            tagsRecyclerView.layoutManager = LinearLayoutManager(
                itemView.context,
                LinearLayoutManager.HORIZONTAL,
                false
            )
            tagsRecyclerView.adapter = tagsAdapter
            tagsAdapter.setTags(allTags)
            
            // 显示笔记（根据当前类型过滤）
            val allNotes = parseNotes(question.userNotes)
            val filteredNotes = if (currentInputType == "note") {
                allNotes.filter { it.type == "note" }
            } else {
                allNotes.filter { it.type == "flashcard" }
            }
            notesAdapter.submitList(filteredNotes)
            
            // 清空输入框（只在切换题目时清空，不在更新时清空）
            // 这里不清空，让用户继续输入
            
            // 保持当前输入类型和折叠状态，不重置
            // 确保 UI 状态与 currentInputType 一致
            if (currentInputType == "note") {
                noteTypeButton.isChecked = true
                flashcardTypeButton.isChecked = false
                addNoteContainer.visibility = View.VISIBLE
                addFlashcardContainer.visibility = View.GONE
            } else {
                noteTypeButton.isChecked = false
                flashcardTypeButton.isChecked = true
                addNoteContainer.visibility = View.GONE
                addFlashcardContainer.visibility = View.VISIBLE
            }
            
            // 保持折叠状态
            addNoteInputLayout.visibility = if (isNoteInputExpanded) View.VISIBLE else View.GONE
            addFlashcardInputLayout.visibility = if (isFlashcardInputExpanded) View.VISIBLE else View.GONE
            toggleNoteInputButton.text = if (isNoteInputExpanded) "▼" else "▲"
            toggleFlashcardInputButton.text = if (isFlashcardInputExpanded) "▼" else "▲"
        }
        
        private fun switchInputType(type: String) {
            currentInputType = type
            if (type == "note") {
                noteTypeButton.isChecked = true
                flashcardTypeButton.isChecked = false
                addNoteContainer.visibility = View.VISIBLE
                addFlashcardContainer.visibility = View.GONE
            } else {
                noteTypeButton.isChecked = false
                flashcardTypeButton.isChecked = true
                addNoteContainer.visibility = View.GONE
                addFlashcardContainer.visibility = View.VISIBLE
            }
            
            // 保存状态
            currentQuestion?.id?.let { questionId ->
                onStateChanged?.invoke(questionId, type, isNoteInputExpanded, isFlashcardInputExpanded)
            }
            
            // 根据类型过滤显示笔记列表
            val question = currentQuestion ?: return
            val allNotes = parseNotes(question.userNotes)
            val filteredNotes = if (type == "note") {
                allNotes.filter { it.type == "note" }
            } else {
                allNotes.filter { it.type == "flashcard" }
            }
            notesAdapter.submitList(filteredNotes)
        }
        
        private fun addFlashcard() {
            val frontText = addFlashcardFrontEditText.text.toString().trim()
            val backText = addFlashcardBackEditText.text.toString().trim()
            
            if (frontText.isBlank() || backText.isBlank()) {
                Toast.makeText(itemView.context, "请输入提示和内容", Toast.LENGTH_SHORT).show()
                return
            }
            
            val question = currentQuestion ?: return
            
            // 解析现有笔记
            val existingNotes = parseNotes(question.userNotes).toMutableList()
            
            // 添加新记忆卡片
            val newFlashcard = NoteItem(
                id = System.currentTimeMillis().toString(),
                type = "flashcard",
                content = "",
                front = frontText,
                back = backText,
                timestamp = System.currentTimeMillis()
            )
            existingNotes.add(newFlashcard)
            
            // 保存为JSON
            val notesJson = JSONArray().apply {
                existingNotes.forEach { note ->
                    put(JSONObject().apply {
                        put("id", note.id)
                        put("type", note.type)
                        if (note.type == "note") {
                            put("content", note.content)
                        } else {
                            put("front", note.front)
                            put("back", note.back)
                        }
                        put("timestamp", note.timestamp)
                    })
                }
            }.toString()
            
            // 更新题目
            val updatedQuestion = question.copy(userNotes = notesJson)
            
            // 关键：在调用 onQuestionUpdate 之前，先保存状态到 Map
            // 这样即使 submitList 触发重新 bind，状态也会被保持
            currentInputType = "flashcard"  // 强制设置为卡片模式
            onStateChanged?.invoke(question.id, "flashcard", isNoteInputExpanded, isFlashcardInputExpanded)
            Log.d("QuestionCardAdapter", "添加卡片：设置 currentInputType = flashcard, question.id = ${question.id}")
            
            // 同时创建独立的记忆卡片记录
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val database = com.gongkao.cuotifupan.data.AppDatabase.getDatabase(itemView.context)
                    val standaloneFlashcard = com.gongkao.cuotifupan.data.StandaloneFlashcard(
                        id = newFlashcard.id,
                        front = newFlashcard.front,
                        back = newFlashcard.back,
                        createdAt = newFlashcard.timestamp,
                        updatedAt = newFlashcard.timestamp,
                        questionId = question.id,
                        tags = question.tags, // 继承题目的标签
                        isFavorite = false,
                        reviewState = "new"  // 新卡片，使用 Anki 风格的状态
                    )
                    database.standaloneFlashcardDao().insert(standaloneFlashcard)
                    Log.d("QuestionCardAdapter", "已创建独立记忆卡片: id=${standaloneFlashcard.id}, front=${standaloneFlashcard.front}, questionId=${standaloneFlashcard.questionId}")
                } catch (e: Exception) {
                    Log.e("QuestionCardAdapter", "创建独立记忆卡片失败", e)
                }
            }
            
            // 立即更新 UI 状态，确保用户看到的是卡片输入区域
            noteTypeButton.isChecked = false
            flashcardTypeButton.isChecked = true
            addNoteContainer.visibility = View.GONE
            addFlashcardContainer.visibility = View.VISIBLE
            
            // 更新本地 currentQuestion
            currentQuestion = updatedQuestion
            
            // 刷新笔记列表（只显示卡片类型）
            val filteredNotes = existingNotes.filter { it.type == "flashcard" }
            notesAdapter.submitList(filteredNotes)
            addFlashcardFrontEditText.text.clear()
            addFlashcardBackEditText.text.clear()
            
            // 最后调用 onQuestionUpdate
            // 由于状态已保存到 Map，bind 方法会从 Map 中恢复状态
            onQuestionUpdate(updatedQuestion)
            
            Toast.makeText(itemView.context, "卡片已添加", Toast.LENGTH_SHORT).show()
        }
        
        private fun parseNotes(notesJson: String): List<NoteItem> {
            return try {
                if (notesJson.isNotBlank()) {
                    JSONArray(notesJson).let { array ->
                        (0 until array.length()).map { index ->
                            val noteObj = array.getJSONObject(index)
                            val type = noteObj.optString("type", "note")
                            // 向后兼容：如果没有 type 字段，检查是否有 content 字段
                            if (type == "note" && !noteObj.has("type") && noteObj.has("content")) {
                                NoteItem(
                                    id = noteObj.optString("id", index.toString()),
                                    type = "note",
                                    content = noteObj.optString("content", ""),
                                    front = "",
                                    back = "",
                                    timestamp = noteObj.optLong("timestamp", System.currentTimeMillis())
                                )
                            } else {
                                NoteItem(
                                    id = noteObj.optString("id", index.toString()),
                                    type = type,
                                    content = noteObj.optString("content", ""),
                                    front = noteObj.optString("front", ""),
                                    back = noteObj.optString("back", ""),
                                    timestamp = noteObj.optLong("timestamp", System.currentTimeMillis())
                                )
                            }
                        }
                    }
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                // 如果不是JSON格式，当作单条笔记处理
                if (notesJson.isNotBlank()) {
                    listOf(NoteItem(
                        id = "0",
                        type = "note",
                        content = notesJson,
                        front = "",
                        back = "",
                        timestamp = System.currentTimeMillis()
                    ))
                } else {
                    emptyList()
                }
            }
        }
        
        private fun addNote() {
            val noteText = addNoteEditText.text.toString().trim()
            if (noteText.isBlank()) {
                Toast.makeText(itemView.context, "请输入笔记内容", Toast.LENGTH_SHORT).show()
                return
            }
            
            val question = currentQuestion ?: return
            
            // 解析现有笔记
            val existingNotes = parseNotes(question.userNotes).toMutableList()
            
            // 添加新笔记
            val newNote = NoteItem(
                id = System.currentTimeMillis().toString(),
                type = "note",
                content = noteText,
                front = "",
                back = "",
                timestamp = System.currentTimeMillis()
            )
            existingNotes.add(newNote)
            
            // 保存为JSON
            val notesJson = JSONArray().apply {
                existingNotes.forEach { note ->
                    put(JSONObject().apply {
                        put("id", note.id)
                        put("type", note.type)
                        if (note.type == "note") {
                            put("content", note.content)
                        } else {
                            put("front", note.front)
                            put("back", note.back)
                        }
                        put("timestamp", note.timestamp)
                    })
                }
            }.toString()
            
            // 更新题目
            val updatedQuestion = question.copy(userNotes = notesJson)
            
            // 关键：在调用 onQuestionUpdate 之前，先保存状态到 Map
            currentInputType = "note"
            onStateChanged?.invoke(question.id, "note", isNoteInputExpanded, isFlashcardInputExpanded)
            
            // 同时创建独立的笔记记录
            GlobalScope.launch(Dispatchers.IO) {
                try {
                    val database = com.gongkao.cuotifupan.data.AppDatabase.getDatabase(itemView.context)
                    val standaloneNote = com.gongkao.cuotifupan.data.StandaloneNote(
                        id = newNote.id,
                        content = newNote.content,
                        createdAt = newNote.timestamp,
                        updatedAt = newNote.timestamp,
                        questionId = question.id,
                        tags = question.tags, // 继承题目的标签
                        isFavorite = false
                    )
                    database.standaloneNoteDao().insert(standaloneNote)
                    Log.d("QuestionCardAdapter", "已创建独立笔记: id=${standaloneNote.id}, content=${standaloneNote.content.take(50)}, questionId=${standaloneNote.questionId}")
                } catch (e: Exception) {
                    Log.e("QuestionCardAdapter", "创建独立笔记失败", e)
                }
            }
            
            // 立即更新 UI 状态
            noteTypeButton.isChecked = true
            flashcardTypeButton.isChecked = false
            addNoteContainer.visibility = View.VISIBLE
            addFlashcardContainer.visibility = View.GONE
            
            // 更新本地 currentQuestion
            currentQuestion = updatedQuestion
            
            // 刷新笔记列表（只显示笔记类型）
            val filteredNotes = existingNotes.filter { it.type == "note" }
            notesAdapter.submitList(filteredNotes)
            addNoteEditText.text.clear()
            
            // 最后调用 onQuestionUpdate
            // 由于状态已保存到 Map，bind 方法会从 Map 中恢复状态
            onQuestionUpdate(updatedQuestion)
            
            Toast.makeText(itemView.context, "笔记已添加", Toast.LENGTH_SHORT).show()
        }
        
        /**
         * 移除手写笔记
         */
        private fun removeHandwriting(question: Question) {
            // 显示确认对话框
            androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                .setTitle("擦除手写")
                .setMessage("确定要擦除此图片中的手写笔记吗？处理可能需要一些时间。")
                .setPositiveButton("确定") { _, _ ->
                    // 在后台线程执行手写擦除
                    GlobalScope.launch(Dispatchers.Main) {
                        // 显示加载提示
                        val loadingDialog = androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                            .setTitle("处理中")
                            .setMessage("正在擦除手写笔记，请稍候...")
                            .setCancelable(false)
                            .create()
                        
                        loadingDialog.show()
                        
                        try {
                            // 在IO线程加载图片并处理
                            val result = withContext(Dispatchers.IO) {
                                // 1. 确定原图路径（如果已有originalImagePath则使用，否则使用当前imagePath）
                                val originalImagePath = question.originalImagePath ?: question.imagePath
                                val imageFile = File(originalImagePath)
                                if (!imageFile.exists()) {
                                    Log.e("QuestionCardAdapter", "原图文件不存在: $originalImagePath")
                                    return@withContext null
                                }
                                
                                // 2. 从原图加载 Bitmap
                                val originalBitmap = if (originalImagePath.startsWith(itemView.context.filesDir.absolutePath) || 
                                    originalImagePath.startsWith(itemView.context.cacheDir.absolutePath) ||
                                    originalImagePath.contains("/DCIM/Camera/")) {
                                    // 应用私有文件或公共存储目录的文件，直接加载
                                    BitmapFactory.decodeFile(originalImagePath)
                                } else {
                                    // 使用 ImageAccessHelper 加载（兼容 MediaStore URI）
                                    com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(itemView.context, originalImagePath)
                                }
                                
                                if (originalBitmap == null) {
                                    Log.e("QuestionCardAdapter", "无法加载原图: $originalImagePath")
                                    return@withContext null
                                }
                                
                                // 3. 调用手写擦除服务
                                val processedBitmap = HandwritingRemovalService.removeHandwriting(originalBitmap)
                                originalBitmap.recycle()
                                    
                                    if (processedBitmap == null) {
                                        Log.e("QuestionCardAdapter", "手写擦除失败")
                                        return@withContext null
                                    }
                                    
                                    // 4. 保存擦写后的图片到新文件（questions目录下）
                                    val questionsDir = File(itemView.context.filesDir, "questions")
                                    if (!questionsDir.exists()) {
                                        questionsDir.mkdirs()
                                    }
                                    
                                    val extension = imageFile.extension.ifEmpty { "jpg" }
                                    val cleanedImageFile = File(questionsDir, "question_${question.id}_cleaned.$extension")
                                    
                                    val format = when (extension.lowercase()) {
                                        "png" -> android.graphics.Bitmap.CompressFormat.PNG
                                        "webp" -> android.graphics.Bitmap.CompressFormat.WEBP
                                        else -> android.graphics.Bitmap.CompressFormat.JPEG
                                    }
                                    
                                    val quality = if (format == android.graphics.Bitmap.CompressFormat.PNG) 100 else 90
                                    
                                    FileOutputStream(cleanedImageFile).use { out ->
                                        processedBitmap.compress(format, quality, out)
                                    }
                                    
                                    processedBitmap.recycle()
                                    
                                    Log.d("QuestionCardAdapter", "手写擦除成功，原图: $originalImagePath")
                                    Log.d("QuestionCardAdapter", "手写擦除成功，擦写后: ${cleanedImageFile.absolutePath}")
                                    
                                    // 返回原图路径和擦写后图片路径
                                    Pair(originalImagePath, cleanedImageFile.absolutePath)
                                    
                            }
                            
                            loadingDialog.dismiss()
                            
                            if (result != null) {
                                val (originalImagePath, cleanedImagePath) = result
                                
                                // 5. 更新题目数据
                                // 保存原图路径（如果还没有保存）
                                val finalOriginalImagePath = question.originalImagePath ?: originalImagePath
                                // 更新题目：保存原图路径、擦写后图片路径，并设置imagePath为擦写后的图片（默认显示擦写后的）
                                val updatedQuestion = question.copy(
                                    originalImagePath = finalOriginalImagePath,
                                    cleanedImagePath = cleanedImagePath,
                                    imagePath = cleanedImagePath  // 默认显示擦写后的图片
                                )
                                
                                // 6. 刷新图片显示（显示擦写后的图片）
                                val context = itemView.context
                                val file = File(cleanedImagePath)
                                if (file.exists()) {
                                    val fileLastModified = file.lastModified()
                                    val fileUri = android.net.Uri.fromFile(file)
                                    val uriWithTimestamp = fileUri.buildUpon()
                                        .appendQueryParameter("mtime", fileLastModified.toString())
                                        .build()
                                    
                                    imageView.load(uriWithTimestamp) {
                                        listener(
                                            onSuccess = { _, result ->
                                                imageView.post {
                                                    setImageSizeToOriginal()
                                                }
                                            },
                                            onError = { _, _ -> }
                                        )
                                    }
                                }
                                
                                // 7. 更新当前题目和数据库
                                currentQuestion = updatedQuestion
                                showingCleanedImage = true  // 擦除后默认显示擦除后的图片
                                onQuestionUpdate(updatedQuestion)
                                
                                // 8. 更新按钮状态（现在显示"还原"按钮）
                                updateRemoveHandwritingButton()
                                
                                Toast.makeText(itemView.context, "手写擦除完成", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(itemView.context, "手写擦除失败，请稍后重试", Toast.LENGTH_LONG).show()
                            }
                            
                        } catch (e: HandwritingRemovalService.HandwritingRemovalException) {
                            loadingDialog.dismiss()
                            Log.e("QuestionCardAdapter", "手写擦除异常: ${e.message}", e)
                            
                            // 对于额度用尽的情况，显示对话框提示
                            if (e.errorCode == "QUOTA_EXCEEDED") {
                                val context = itemView.context
                                val redeemCodeUrl = com.gongkao.cuotifupan.util.ProManager.getRedeemCodeUrl(context)
                                
                                val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(context)
                                    .setTitle("额度已用尽")
                                    .setMessage(e.message ?: "您的使用额度已用尽")
                                
                                // 如果有兑换码链接，显示"如何领取兑换码"按钮
                                if (redeemCodeUrl != null && redeemCodeUrl.isNotBlank()) {
                                    dialogBuilder.setPositiveButton("如何领取兑换码") { _, _ ->
                                        // 跳转到如何领取兑换码页面
                                        try {
                                            val intent = Intent(context, com.gongkao.cuotifupan.ui.HowToGetRedeemCodeActivity::class.java)
                                            context.startActivity(intent)
                                        } catch (ex: Exception) {
                                            Log.e("QuestionCardAdapter", "跳转失败", ex)
                                            Toast.makeText(context, "无法打开页面", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    dialogBuilder.setNegativeButton("知道了", null)
                                } else {
                                    // 没有兑换码链接，只显示"知道了"按钮
                                    dialogBuilder.setPositiveButton("知道了", null)
                                }
                                
                                dialogBuilder.show()
                            } else {
                                // 其他错误显示 Toast
                                Toast.makeText(itemView.context, e.message ?: "手写擦除失败", Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            loadingDialog.dismiss()
                            Log.e("QuestionCardAdapter", "手写擦除异常", e)
                            Toast.makeText(itemView.context, "手写擦除失败: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        private fun deleteNote(note: NoteItem) {
            val question = currentQuestion ?: return
            
            // 显示确认对话框
            androidx.appcompat.app.AlertDialog.Builder(itemView.context)
                .setTitle("删除笔记")
                .setMessage("确定要删除这条笔记吗？")
                .setPositiveButton("删除") { _, _ ->
                    // 解析现有笔记
                    val existingNotes = parseNotes(question.userNotes).toMutableList()
                    
                    // 删除指定笔记
                    existingNotes.removeAll { it.id == note.id }
                    
                    // 保存为JSON
                    val notesJson = if (existingNotes.isEmpty()) {
                        ""
                    } else {
                        JSONArray().apply {
                            existingNotes.forEach { noteItem ->
                                put(JSONObject().apply {
                                    put("id", noteItem.id)
                                    put("type", noteItem.type)
                                    if (noteItem.type == "note") {
                                        put("content", noteItem.content)
                                    } else {
                                        put("front", noteItem.front)
                                        put("back", noteItem.back)
                                    }
                                    put("timestamp", noteItem.timestamp)
                                })
                            }
                        }.toString()
                    }
                    
                    // 更新题目
                    val updatedQuestion = question.copy(userNotes = notesJson)
                    onQuestionUpdate(updatedQuestion)
                    
                    // 刷新笔记列表（根据当前类型过滤）
                    val filteredNotes = if (currentInputType == "note") {
                        existingNotes.filter { it.type == "note" }
                    } else {
                        existingNotes.filter { it.type == "flashcard" }
                    }
                    notesAdapter.submitList(filteredNotes)
                    
                    Toast.makeText(itemView.context, "笔记已删除", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
        
        /**
         * 清除粉笔错题截图的对错痕迹
         */
        private fun hideOptions(question: Question) {
            // 确定使用的原图路径（优先使用originalImagePath，如果不存在则使用imagePath）
            val originalImagePath = question.originalImagePath ?: question.imagePath
            
            if (!File(originalImagePath).exists()) {
                Toast.makeText(itemView.context, "图片文件不存在", Toast.LENGTH_SHORT).show()
                return
            }
            
            val context = itemView.context
            
            // 执行清除操作的函数
            fun performClear() {
                if (context is androidx.lifecycle.LifecycleOwner) {
                    context.lifecycleScope.launch {
                        // 显示进度提示
                        val progressDialog = android.app.ProgressDialog(context).apply {
                            setMessage("正在清除对错痕迹...")
                            setCancelable(false)
                            show()
                        }
                        
                        try {
                            // 初始化ImageEditor
                            ImageEditor.init(context)
                            
                            // 在后台线程执行清除操作
                            val hiddenImagePath = withContext(Dispatchers.IO) {
                                ImageEditor.hideOptions(originalImagePath)
                            }
                            
                            progressDialog.dismiss()
                            
                            if (hiddenImagePath != null) {
                                // 保存清除后的图片路径
                                val updatedQuestion = question.copy(
                                    hiddenOptionsImagePath = hiddenImagePath,
                                    originalImagePath = originalImagePath  // 确保originalImagePath存在
                                )
                                onQuestionUpdate(updatedQuestion)
                                
                                // 切换到清除后的图片
                                showingHiddenOptionsImage = true
                                loadImage(hiddenImagePath)
                                updateHideOptionsButton(updatedQuestion)
                                
                                Toast.makeText(context, "已清除对错痕迹", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "清除失败", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            progressDialog.dismiss()
                            Toast.makeText(context, "清除失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            // 检查是否需要显示提示（前5次显示）
            if (PreferencesManager.shouldShowClearMarksHint(context)) {
                // 显示确认对话框
                androidx.appcompat.app.AlertDialog.Builder(context)
                    .setTitle("清除对错痕迹")
                    .setMessage("此功能用于清除粉笔错题截图中的对错痕迹，包括：\n\n• 选项圆圈的对错标记（绿色/红色）\n• 底部的答案提示文字（如\"正确答案\"、\"你的答案\"等）\n• 其他红色和绿色标记\n\n同时会智能裁剪掉答案说明行及以下内容。\n\n此操作将生成一张新的图片，原图不会被删除。如果不满意，可以手动调整。")
                    .setPositiveButton("确定") { _, _ ->
                        // 增加提示显示次数
                        PreferencesManager.incrementClearMarksHintCount(context)
                        performClear()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                // 已经显示过5次，直接执行清除
                performClear()
            }
        }
        
        /**
         * 显示已清除的图片（不重新处理，直接使用已有的清除结果）
         */
        private fun showHiddenOptionsImage(question: Question) {
            val hiddenImagePath = question.hiddenOptionsImagePath ?: return
            
            val file = File(hiddenImagePath)
            if (!file.exists()) {
                Log.w("QuestionCardAdapter", "清除后的图片文件不存在，重新执行清除: $hiddenImagePath")
                hideOptions(question)
                return
            }
            
            showingHiddenOptionsImage = true
            loadImage(hiddenImagePath)
            updateHideOptionsButton(question)
            
            Log.d("QuestionCardAdapter", "直接显示已清除的图片: $hiddenImagePath")
        }
        
        /**
         * 还原到原图（从清除后的图片还原）
         */
        private fun restoreOriginalFromHiddenOptions(question: Question) {
            showingHiddenOptionsImage = false
            
            // 确定要显示的原图路径
            val originalImagePath = question.originalImagePath ?: question.imagePath
            
            // 加载图片
            loadImage(originalImagePath)
            updateHideOptionsButton(question)
        }
        
        /**
         * 更新清除对错痕迹按钮状态
         */
        private fun updateHideOptionsButton(question: Question) {
            // 根据当前状态更新按钮提示（可以在这里添加图标或文字变化）
            // 目前只是确保状态正确，UI上可能不需要明显变化
        }
    }
    
    class QuestionDiffCallback : DiffUtil.ItemCallback<Question>() {
        override fun areItemsTheSame(oldItem: Question, newItem: Question): Boolean {
            return oldItem.id == newItem.id
        }
        
        override fun areContentsTheSame(oldItem: Question, newItem: Question): Boolean {
            // 如果图片路径相同，检查文件修改时间，确保文件更新后能刷新
            if (oldItem.imagePath == newItem.imagePath) {
                val oldFile = File(oldItem.imagePath)
                val newFile = File(newItem.imagePath)
                // 如果文件修改时间不同，认为内容已更新，需要刷新
                if (oldFile.exists() && newFile.exists() && oldFile.lastModified() != newFile.lastModified()) {
                    return false
                }
            }
            return oldItem == newItem
        }
    }
}

