package com.gongkao.cuotifupan.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 独立笔记数据访问对象
 */
@Dao
interface StandaloneNoteDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: StandaloneNote): Long
    
    @Update
    suspend fun update(note: StandaloneNote)
    
    @Delete
    suspend fun delete(note: StandaloneNote)
    
    @Query("SELECT * FROM standalone_notes ORDER BY updatedAt DESC")
    fun getAllNotes(): LiveData<List<StandaloneNote>>
    
    @Query("SELECT * FROM standalone_notes ORDER BY updatedAt DESC")
    suspend fun getAllNotesSync(): List<StandaloneNote>
    
    @Query("SELECT * FROM standalone_notes ORDER BY createdAt DESC")
    fun getAllNotesOrderByCreatedTime(): LiveData<List<StandaloneNote>>
    
    @Query("SELECT * FROM standalone_notes WHERE isFavorite = 1 ORDER BY updatedAt DESC")
    fun getFavoriteNotes(): LiveData<List<StandaloneNote>>
    
    @Query("SELECT * FROM standalone_notes WHERE tags LIKE '%' || :tag || '%' ORDER BY updatedAt DESC")
    fun getNotesByTag(tag: String): LiveData<List<StandaloneNote>>
    
    @Query("SELECT * FROM standalone_notes WHERE content LIKE '%' || :keyword || '%' ORDER BY updatedAt DESC")
    fun searchNotes(keyword: String): LiveData<List<StandaloneNote>>
    
    @Query("SELECT * FROM standalone_notes WHERE id = :id")
    suspend fun getNoteById(id: String): StandaloneNote?
    
    @Query("SELECT * FROM standalone_notes WHERE questionId IS NULL ORDER BY updatedAt DESC")
    fun getIndependentNotes(): LiveData<List<StandaloneNote>>
    
    @Query("SELECT * FROM standalone_notes WHERE questionId = :questionId")
    suspend fun getNotesByQuestionId(questionId: String): List<StandaloneNote>
    
    @Query("DELETE FROM standalone_notes")
    suspend fun deleteAll()
}

