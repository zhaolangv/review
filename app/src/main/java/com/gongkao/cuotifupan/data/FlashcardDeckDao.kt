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
}

