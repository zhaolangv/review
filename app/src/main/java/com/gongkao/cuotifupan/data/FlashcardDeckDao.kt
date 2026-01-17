package com.gongkao.cuotifupan.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 卡包数据访问对象
 */
@Dao
interface FlashcardDeckDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(deck: FlashcardDeck): Long
    
    @Update
    suspend fun update(deck: FlashcardDeck)
    
    @Delete
    suspend fun delete(deck: FlashcardDeck)
    
    @Query("SELECT * FROM flashcard_decks WHERE parentId IS NULL ORDER BY sortOrder ASC, name ASC")
    fun getRootDecks(): LiveData<List<FlashcardDeck>>
    
    @Query("SELECT * FROM flashcard_decks WHERE parentId IS NULL ORDER BY sortOrder ASC, name ASC")
    suspend fun getRootDecksSync(): List<FlashcardDeck>
    
    @Query("SELECT * FROM flashcard_decks WHERE parentId = :parentId ORDER BY sortOrder ASC, name ASC")
    fun getChildDecks(parentId: String): LiveData<List<FlashcardDeck>>
    
    @Query("SELECT * FROM flashcard_decks WHERE parentId = :parentId ORDER BY sortOrder ASC, name ASC")
    suspend fun getChildDecksSync(parentId: String): List<FlashcardDeck>
    
    @Query("SELECT * FROM flashcard_decks WHERE id = :id")
    suspend fun getDeckById(id: String): FlashcardDeck?
    
    @Query("SELECT * FROM flashcard_decks ORDER BY sortOrder ASC, name ASC")
    suspend fun getAllDecksSync(): List<FlashcardDeck>
    
    @Query("SELECT COUNT(*) FROM standalone_flashcards WHERE deckId = :deckId")
    suspend fun getCardCount(deckId: String): Int
    
    /**
     * 获取卡包的新卡数量
     */
    @Query("SELECT COUNT(*) FROM standalone_flashcards WHERE deckId = :deckId AND reviewState = 'new'")
    suspend fun getNewCardCount(deckId: String): Int
    
    /**
     * 获取卡包的学习卡数量
     */
    @Query("SELECT COUNT(*) FROM standalone_flashcards WHERE deckId = :deckId AND reviewState = 'learning'")
    suspend fun getLearningCardCount(deckId: String): Int
    
    /**
     * 获取卡包的复习卡数量
     */
    @Query("SELECT COUNT(*) FROM standalone_flashcards WHERE deckId = :deckId AND reviewState IN ('review', 'relearning') AND nextReviewTime <= :currentTime")
    suspend fun getReviewCardCount(deckId: String, currentTime: Long): Int
    
    /**
     * 获取卡包的统计信息
     */
    suspend fun getDeckStatistics(deckId: String, currentTime: Long): DeckStatistics {
        val total = getCardCount(deckId)
        val newCount = getNewCardCount(deckId)
        val learningCount = getLearningCardCount(deckId)
        val reviewCount = getReviewCardCount(deckId, currentTime)
        return DeckStatistics(total, newCount, learningCount, reviewCount)
    }
    
    /**
     * 获取卡包及其所有子卡包的统计信息（递归）
     */
    suspend fun getDeckStatisticsRecursive(deckId: String, currentTime: Long): DeckStatistics {
        // 获取直接属于该卡包的统计
        val directStats = getDeckStatistics(deckId, currentTime)
        
        // 获取所有子卡包
        val childDecks = getChildDecksSync(deckId)
        
        // 递归统计子卡包
        var totalStats = directStats
        for (childDeck in childDecks) {
            val childStats = getDeckStatisticsRecursive(childDeck.id, currentTime)
            totalStats = DeckStatistics(
                total = totalStats.total + childStats.total,
                newCount = totalStats.newCount + childStats.newCount,
                learningCount = totalStats.learningCount + childStats.learningCount,
                reviewCount = totalStats.reviewCount + childStats.reviewCount
            )
        }
        
        return totalStats
    }
    
    /**
     * 获取所有后代卡包 ID（包括子卡包、子卡包的子卡包等）
     */
    suspend fun getAllDescendantDeckIds(deckId: String): List<String> {
        val result = mutableListOf<String>()
        val childDecks = getChildDecksSync(deckId)
        for (childDeck in childDecks) {
            result.add(childDeck.id)
            result.addAll(getAllDescendantDeckIds(childDeck.id))
        }
        return result
    }
    
    /**
     * 卡包统计信息数据类
     */
    data class DeckStatistics(
        val total: Int,
        val newCount: Int,
        val learningCount: Int,
        val reviewCount: Int
    )
}

