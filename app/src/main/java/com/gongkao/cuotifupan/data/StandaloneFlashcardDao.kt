package com.gongkao.cuotifupan.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 独立记忆卡片数据访问对象
 */
@Dao
interface StandaloneFlashcardDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(flashcard: StandaloneFlashcard): Long
    
    @Update
    suspend fun update(flashcard: StandaloneFlashcard)
    
    @Delete
    suspend fun delete(flashcard: StandaloneFlashcard)
    
    @Query("SELECT * FROM standalone_flashcards ORDER BY updatedAt DESC")
    fun getAllFlashcards(): LiveData<List<StandaloneFlashcard>>
    
    @Query("SELECT * FROM standalone_flashcards ORDER BY updatedAt DESC")
    suspend fun getAllFlashcardsSync(): List<StandaloneFlashcard>
    
    @Query("SELECT * FROM standalone_flashcards ORDER BY createdAt DESC")
    fun getAllFlashcardsOrderByCreatedTime(): LiveData<List<StandaloneFlashcard>>
    
    @Query("SELECT * FROM standalone_flashcards WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteFlashcards(): LiveData<List<StandaloneFlashcard>>
    
    @Query("SELECT * FROM standalone_flashcards WHERE tags LIKE '%' || :tag || '%' ORDER BY updatedAt DESC")
    fun getFlashcardsByTag(tag: String): LiveData<List<StandaloneFlashcard>>
    
    @Query("SELECT * FROM standalone_flashcards WHERE front LIKE '%' || :keyword || '%' OR back LIKE '%' || :keyword || '%' ORDER BY updatedAt DESC")
    fun searchFlashcards(keyword: String): LiveData<List<StandaloneFlashcard>>
    
    @Query("SELECT * FROM standalone_flashcards WHERE id = :id")
    suspend fun getFlashcardById(id: String): StandaloneFlashcard?
    
    @Query("SELECT * FROM standalone_flashcards WHERE questionId IS NULL ORDER BY updatedAt DESC")
    fun getIndependentFlashcards(): LiveData<List<StandaloneFlashcard>>
    
    @Query("SELECT * FROM standalone_flashcards WHERE questionId = :questionId")
    suspend fun getFlashcardsByQuestionId(questionId: String): List<StandaloneFlashcard>
    
    @Query("SELECT * FROM standalone_flashcards WHERE reviewState = :state ORDER BY updatedAt DESC")
    fun getFlashcardsByReviewState(state: String): LiveData<List<StandaloneFlashcard>>
    
    /**
     * 获取今日需要复习的卡片（新卡片 + 学习中的卡片 + 到期的复习卡片）
     */
    @Query("SELECT * FROM standalone_flashcards WHERE reviewState IN ('new', 'learning') OR (reviewState IN ('review', 'relearning') AND nextReviewTime <= :currentTime) ORDER BY nextReviewTime ASC, createdAt ASC")
    suspend fun getDueCards(currentTime: Long = System.currentTimeMillis()): List<StandaloneFlashcard>
    
    /**
     * 获取新卡片数量
     */
    @Query("SELECT COUNT(*) FROM standalone_flashcards WHERE reviewState = 'new'")
    suspend fun getNewCardCount(): Int
    
    /**
     * 获取今日学习中的卡片数量
     */
    @Query("SELECT COUNT(*) FROM standalone_flashcards WHERE reviewState = 'learning'")
    suspend fun getLearningCardCount(): Int
    
    /**
     * 获取今日到期的复习卡片数量
     */
    @Query("SELECT COUNT(*) FROM standalone_flashcards WHERE reviewState IN ('review', 'relearning') AND nextReviewTime <= :currentTime")
    suspend fun getDueReviewCardCount(currentTime: Long = System.currentTimeMillis()): Int
    
    /**
     * 按卡包查询卡片
     */
    @Query("SELECT * FROM standalone_flashcards WHERE deckId = :deckId ORDER BY updatedAt DESC")
    suspend fun getFlashcardsByDeck(deckId: String): List<StandaloneFlashcard>
    
    @Query("SELECT * FROM standalone_flashcards WHERE deckId = :deckId ORDER BY updatedAt DESC")
    fun getFlashcardsByDeckLive(deckId: String): LiveData<List<StandaloneFlashcard>>
    
    @Query("DELETE FROM standalone_flashcards")
    suspend fun deleteAll()
}

