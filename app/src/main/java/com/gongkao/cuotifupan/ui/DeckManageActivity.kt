package com.gongkao.cuotifupan.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.FlashcardDeck
import com.gongkao.cuotifupan.data.FlashcardDeckDao
import com.gongkao.cuotifupan.ui.FlashcardReviewActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 卡包管理页面（类似 Anki 的卡包列表）
 */
class DeckManageActivity : AppCompatActivity() {
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var addDeckButton: Button
    private lateinit var adapter: DeckAdapter
    
    // 存储展开状态的卡包 ID（类似 Anki 的展开状态管理）
    private val expandedDeckIds = mutableSetOf<String>()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_deck_manage)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "卡包管理"
        
        recyclerView = findViewById(R.id.deckRecyclerView)
        addDeckButton = findViewById(R.id.addDeckButton)
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        adapter = DeckAdapter(
            onDeckClick = { deck ->
                // 点击卡包名称，直接开始复习（类似 Anki 的行为）
                startReview(deck)
            },
            onDeckLongClick = { deck ->
                // 长按或点击菜单按钮显示操作菜单（类似 Anki）
                showDeckOptions(deck)
            },
            onToggleExpand = { deckWithStats ->
                // 切换展开/折叠状态（类似 Anki）
                val deckId = deckWithStats.deck.id
                val wasExpanded = expandedDeckIds.contains(deckId)
                if (wasExpanded) {
                    expandedDeckIds.remove(deckId)
                    android.util.Log.d("DeckManage", "折叠卡包: ${deckWithStats.deck.name}")
                } else {
                    expandedDeckIds.add(deckId)
                    android.util.Log.d("DeckManage", "展开卡包: ${deckWithStats.deck.name}")
                }
                loadDecks() // 重新加载以刷新显示
            }
        )
        recyclerView.adapter = adapter
        
        addDeckButton.setOnClickListener {
            showAddDeckDialog()
        }
        
        loadDecks()
    }
    
    private fun loadDecks() {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@DeckManageActivity)
            val currentTime = System.currentTimeMillis()
            
            // 获取所有卡包
            val allDecks = withContext(Dispatchers.IO) {
                database.flashcardDeckDao().getAllDecksSync()
            }
            
            // 构建层级结构列表（包含所有卡包，按层级显示）
            val decksWithStats = buildDeckHierarchy(allDecks, currentTime, database, expandedDeckIds)
            
            adapter.submitList(decksWithStats)
        }
    }
    
    /**
     * 构建卡包层级结构列表（递归显示所有卡包，类似 Anki 的树形显示）
     */
    private suspend fun buildDeckHierarchy(
        allDecks: List<FlashcardDeck>,
        currentTime: Long,
        database: AppDatabase,
        expandedDeckIds: Set<String>
    ): List<DeckWithStatistics> = withContext(Dispatchers.IO) {
        val result = mutableListOf<DeckWithStatistics>()
        
        // 构建父子关系映射
        val childrenMap = mutableMapOf<String?, MutableList<FlashcardDeck>>()
        allDecks.forEach { deck ->
            val parentId = deck.parentId
            if (!childrenMap.containsKey(parentId)) {
                childrenMap[parentId] = mutableListOf()
            }
            childrenMap[parentId]?.add(deck)
        }
        
        // 递归函数：添加卡包及其子卡包（在 withContext 内部，可以使用 suspend）
        suspend fun addDeckRecursive(deck: FlashcardDeck, level: Int, parentExpanded: Boolean = true) {
            // 获取该卡包的统计信息（仅直接属于该卡包的卡片，不包括子卡包）
            val statistics = database.flashcardDeckDao().getDeckStatistics(deck.id, currentTime)
            
            // 检查是否有子卡包
            val childDecks = childrenMap[deck.id] ?: emptyList()
            val hasChildren = childDecks.isNotEmpty()
            // 检查是否展开：如果在 expandedDeckIds 中，则展开；否则折叠
            val isExpanded = expandedDeckIds.contains(deck.id)
            
            // 只有当父卡包展开时才显示当前卡包
            if (parentExpanded) {
                // 将层级信息添加到数据中
                result.add(DeckWithStatistics(deck, statistics, level, isExpanded, hasChildren))
            }
            
            // 如果当前卡包展开且有子卡包，递归添加子卡包
            val shouldShowChildren = parentExpanded && isExpanded
            if (shouldShowChildren) {
                childDecks.forEach { childDeck ->
                    addDeckRecursive(childDeck, level + 1, shouldShowChildren)
                }
            }
        }
        
        // 从根卡包开始递归添加
        val rootDecks = childrenMap[null] ?: emptyList()
        // 在 withContext 内部，可以直接调用 suspend 函数
        // 使用 kotlinx.coroutines 的 runBlocking 或直接调用
        for (rootDeck in rootDecks) {
            addDeckRecursive(rootDeck, 0)
        }
        
        result
    }
    
    private fun showAddDeckDialog(parentDeck: FlashcardDeck? = null) {
        val editText = EditText(this)
        editText.hint = "输入卡包名称"
        
        val title = if (parentDeck != null) {
            "在\"${parentDeck.name}\"下新建子卡包"
        } else {
            "新建卡包"
        }
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createDeck(name, parentDeck?.id)
                } else {
                    Toast.makeText(this, "请输入卡包名称", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun createDeck(name: String, parentId: String? = null) {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@DeckManageActivity)
            val deck = FlashcardDeck(name = name, parentId = parentId)
            withContext(Dispatchers.IO) {
                database.flashcardDeckDao().insert(deck)
            }
            Toast.makeText(this@DeckManageActivity, "卡包已创建", Toast.LENGTH_SHORT).show()
            loadDecks()
        }
    }
    
    private fun showDeckOptions(deck: FlashcardDeck) {
        val options = mutableListOf<String>()
        options.add("开始复习")
        options.add("编辑")
        options.add("删除")
        options.add("新建子卡包")
        options.add("查看卡片")
        
        AlertDialog.Builder(this)
            .setTitle(deck.name)
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> startReview(deck)
                    1 -> showDeckEditDialog(deck)
                    2 -> showDeleteDeckDialog(deck)
                    3 -> showAddDeckDialog(deck)
                    4 -> {
                        // 跳转到卡片列表，按卡包筛选
                        // 这里可以通过 Intent 传递 deckId，然后在 FlashcardsFragment 中筛选
                        finish()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    /**
     * 开始复习指定卡包
     */
    private fun startReview(deck: FlashcardDeck) {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@DeckManageActivity)
            val currentTime = System.currentTimeMillis()
            
            // 检查该卡包是否有需要复习的卡片
            val allDeckIds = mutableListOf(deck.id)
            allDeckIds.addAll(withContext(Dispatchers.IO) {
                database.flashcardDeckDao().getAllDescendantDeckIds(deck.id)
            })
            
            val dueCount = withContext(Dispatchers.IO) {
                database.standaloneFlashcardDao().getDueCardsByDeckIds(allDeckIds, currentTime).size
            }
            
            if (dueCount == 0) {
                Toast.makeText(this@DeckManageActivity, "该卡包没有需要复习的卡片", Toast.LENGTH_SHORT).show()
                return@launch
            }
            
            // 跳转到复习页面
            val intent = Intent(this@DeckManageActivity, FlashcardReviewActivity::class.java)
            intent.putExtra("deck_id", deck.id)
            intent.putExtra("deck_name", deck.name)
            startActivity(intent)
        }
    }
    
    private fun showDeckEditDialog(deck: FlashcardDeck) {
        val editText = EditText(this)
        editText.setText(deck.name)
        editText.hint = "输入卡包名称"
        
        AlertDialog.Builder(this)
            .setTitle("编辑卡包")
            .setView(editText)
            .setPositiveButton("保存") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    updateDeck(deck.copy(name = newName, updatedAt = System.currentTimeMillis()))
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun updateDeck(deck: FlashcardDeck) {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@DeckManageActivity)
            withContext(Dispatchers.IO) {
                database.flashcardDeckDao().update(deck)
            }
            Toast.makeText(this@DeckManageActivity, "卡包已更新", Toast.LENGTH_SHORT).show()
            loadDecks()
        }
    }
    
    private fun showDeleteDeckDialog(deck: FlashcardDeck) {
        AlertDialog.Builder(this)
            .setTitle("删除卡包")
            .setMessage("确定要删除卡包\"${deck.name}\"吗？\n注意：此操作不会删除卡包中的卡片，卡片将变为未分类状态。")
            .setPositiveButton("删除") { _, _ ->
                deleteDeck(deck)
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun deleteDeck(deck: FlashcardDeck) {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@DeckManageActivity)
            withContext(Dispatchers.IO) {
                // 递归删除卡包及其所有子卡包（Anki 风格）
                deleteDeckRecursive(database, deck.id)
            }
            Toast.makeText(this@DeckManageActivity, "卡包已删除", Toast.LENGTH_SHORT).show()
            loadDecks()
        }
    }
    
    /**
     * 递归删除卡包及其所有子卡包（类似 Anki 的删除行为）
     */
    private suspend fun deleteDeckRecursive(database: AppDatabase, deckId: String) {
        // 获取所有子卡包
        val childDecks = database.flashcardDeckDao().getChildDecksSync(deckId)
        
        // 递归删除子卡包
        for (childDeck in childDecks) {
            deleteDeckRecursive(database, childDeck.id)
        }
        
        // 将该卡包下的所有卡片的 deckId 设置为 null
        val cards = database.standaloneFlashcardDao().getFlashcardsByDeck(deckId)
        for (card in cards) {
            val updatedCard = card.copy(deckId = null)
            database.standaloneFlashcardDao().update(updatedCard)
        }
        
        // 删除卡包本身
        val deck = database.flashcardDeckDao().getDeckById(deckId)
        if (deck != null) {
            database.flashcardDeckDao().delete(deck)
        }
    }
    
    /**
     * 卡包与统计信息数据类（类似 Anki 的 Deck 显示）
     */
    data class DeckWithStatistics(
        val deck: FlashcardDeck,
        val statistics: FlashcardDeckDao.DeckStatistics,
        val level: Int = 0,  // 层级深度（0=根卡包，1=一级子卡包，以此类推）
        val isExpanded: Boolean = false,  // 是否展开（用于显示/隐藏子卡包）
        val hasChildren: Boolean = false  // 是否有子卡包
    )
    
    /**
     * 卡包列表适配器（类似 Anki 的卡包列表显示，支持展开/折叠）
     */
    class DeckAdapter(
        private val onDeckClick: (FlashcardDeck) -> Unit,
        private val onDeckLongClick: (FlashcardDeck) -> Unit,
        private val onToggleExpand: ((DeckWithStatistics) -> Unit)? = null
    ) : androidx.recyclerview.widget.ListAdapter<DeckWithStatistics, DeckAdapter.DeckViewHolder>(
        DeckDiffCallback()
    ) {
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeckViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_deck, parent, false)
            return DeckViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: DeckViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
        
        inner class DeckViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
            private val expandIcon: TextView = itemView.findViewById(R.id.expandIcon)
            private val deckNameText: TextView = itemView.findViewById(R.id.deckNameText)
            private val cardCountText: TextView = itemView.findViewById(R.id.cardCountText)
            private val menuButton: ImageButton = itemView.findViewById(R.id.menuButton)
            
            fun bind(deckWithStats: DeckWithStatistics) {
                val deck = deckWithStats.deck
                val stats = deckWithStats.statistics
                val level = deckWithStats.level
                val hasChildren = deckWithStats.hasChildren
                val isExpanded = deckWithStats.isExpanded
                
                // 根据层级设置缩进（类似 Anki）
                val indent = level * 24 // 每层缩进24dp
                
                // 设置展开/折叠图标（类似 Anki 的 ▶/▼）
                if (hasChildren) {
                    expandIcon.visibility = View.VISIBLE
                    expandIcon.text = if (isExpanded) "▼" else "▶"
                } else {
                    expandIcon.visibility = View.GONE
                }
                
                // 设置缩进：通过左边距实现
                val expandIconParams = expandIcon.layoutParams as? androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
                expandIconParams?.let {
                    val marginPx = (indent * itemView.context.resources.displayMetrics.density).toInt()
                    it.leftMargin = marginPx
                    expandIcon.layoutParams = it
                }
                
                // 设置卡包名称（不带展开图标文本）
                deckNameText.text = deck.name
                
                // 显示详细统计信息（类似 Anki）：新卡 | 学习 | 复习 | 总数
                val statsText = buildString {
                    if (stats.newCount > 0) append("新:${stats.newCount} ")
                    if (stats.learningCount > 0) append("学习:${stats.learningCount} ")
                    if (stats.reviewCount > 0) append("复习:${stats.reviewCount} ")
                    if (stats.total > 0) {
                        if (isNotEmpty()) append("| ")
                        append("共${stats.total}张")
                    } else {
                        append("空卡包")
                    }
                }
                cardCountText.text = statsText
                
                // 展开/折叠图标点击事件（类似 Anki）
                expandIcon.setOnClickListener {
                    if (hasChildren && onToggleExpand != null) {
                        onToggleExpand.invoke(deckWithStats)
                    }
                }
                
                // 卡包名称点击事件：直接开始复习（类似 Anki 的行为）
                deckNameText.setOnClickListener {
                    // 点击卡包名称直接开始复习
                    onDeckClick(deck)
                }
                
                // 操作菜单按钮点击事件（类似 Anki 的三个点菜单）
                menuButton.setOnClickListener {
                    onDeckLongClick(deck)
                }
                
                // 长按整个 item 也显示操作菜单（类似 Anki 的长按行为）
                itemView.setOnLongClickListener {
                    onDeckLongClick(deck)
                    true
                }
                
                // 移除 itemView 的点击事件，因为现在由子视图处理
                itemView.setOnClickListener(null)
            }
        }
        
        class DeckDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<DeckWithStatistics>() {
            override fun areItemsTheSame(oldItem: DeckWithStatistics, newItem: DeckWithStatistics): Boolean {
                return oldItem.deck.id == newItem.deck.id
            }
            
            override fun areContentsTheSame(oldItem: DeckWithStatistics, newItem: DeckWithStatistics): Boolean {
                return oldItem == newItem
            }
        }
    }
}

