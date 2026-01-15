package com.gongkao.cuotifupan.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
                // 点击卡包，可以编辑或查看卡片
                showDeckOptions(deck)
            },
            onDeckLongClick = { deck ->
                // 长按编辑或删除
                showDeckEditDialog(deck)
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
            val decks = withContext(Dispatchers.IO) {
                database.flashcardDeckDao().getRootDecksSync()
            }
            
            // 获取每个卡包的卡片数量
            val decksWithCount = decks.map { deck ->
                val count = withContext(Dispatchers.IO) {
                    database.flashcardDeckDao().getCardCount(deck.id)
                }
                DeckWithCount(deck, count)
            }
            
            adapter.submitList(decksWithCount)
        }
    }
    
    private fun showAddDeckDialog() {
        val editText = EditText(this)
        editText.hint = "输入卡包名称"
        
        AlertDialog.Builder(this)
            .setTitle("新建卡包")
            .setView(editText)
            .setPositiveButton("创建") { _, _ ->
                val name = editText.text.toString().trim()
                if (name.isNotEmpty()) {
                    createDeck(name)
                } else {
                    Toast.makeText(this, "请输入卡包名称", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun createDeck(name: String) {
        lifecycleScope.launch {
            val database = AppDatabase.getDatabase(this@DeckManageActivity)
            val deck = FlashcardDeck(name = name)
            withContext(Dispatchers.IO) {
                database.flashcardDeckDao().insert(deck)
            }
            Toast.makeText(this@DeckManageActivity, "卡包已创建", Toast.LENGTH_SHORT).show()
            loadDecks()
        }
    }
    
    private fun showDeckOptions(deck: FlashcardDeck) {
        AlertDialog.Builder(this)
            .setTitle(deck.name)
            .setItems(arrayOf("编辑", "删除", "查看卡片")) { _, which ->
                when (which) {
                    0 -> showDeckEditDialog(deck)
                    1 -> showDeleteDeckDialog(deck)
                    2 -> {
                        // 跳转到卡片列表，按卡包筛选
                        // 这里可以通过 Intent 传递 deckId，然后在 FlashcardsFragment 中筛选
                        finish()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
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
            // 先将该卡包下的所有卡片的 deckId 设置为 null
            val cards = withContext(Dispatchers.IO) {
                database.standaloneFlashcardDao().getFlashcardsByDeck(deck.id)
            }
            cards.forEach { card ->
                val updatedCard = card.copy(deckId = null)
                withContext(Dispatchers.IO) {
                    database.standaloneFlashcardDao().update(updatedCard)
                }
            }
            // 删除卡包
            withContext(Dispatchers.IO) {
                database.flashcardDeckDao().delete(deck)
            }
            Toast.makeText(this@DeckManageActivity, "卡包已删除", Toast.LENGTH_SHORT).show()
            loadDecks()
        }
    }
    
    /**
     * 卡包与卡片数量数据类
     */
    data class DeckWithCount(
        val deck: FlashcardDeck,
        val cardCount: Int
    )
    
    /**
     * 卡包列表适配器
     */
    class DeckAdapter(
        private val onDeckClick: (FlashcardDeck) -> Unit,
        private val onDeckLongClick: (FlashcardDeck) -> Unit
    ) : androidx.recyclerview.widget.ListAdapter<DeckWithCount, DeckAdapter.DeckViewHolder>(
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
            private val deckNameText: TextView = itemView.findViewById(R.id.deckNameText)
            private val cardCountText: TextView = itemView.findViewById(R.id.cardCountText)
            
            fun bind(deckWithCount: DeckWithCount) {
                deckNameText.text = deckWithCount.deck.name
                cardCountText.text = "${deckWithCount.cardCount} 张卡片"
                
                itemView.setOnClickListener {
                    onDeckClick(deckWithCount.deck)
                }
                
                itemView.setOnLongClickListener {
                    onDeckLongClick(deckWithCount.deck)
                    true
                }
            }
        }
        
        class DeckDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<DeckWithCount>() {
            override fun areItemsTheSame(oldItem: DeckWithCount, newItem: DeckWithCount): Boolean {
                return oldItem.deck.id == newItem.deck.id
            }
            
            override fun areContentsTheSame(oldItem: DeckWithCount, newItem: DeckWithCount): Boolean {
                return oldItem == newItem
            }
        }
    }
}

