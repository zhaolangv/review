package com.gongkao.cuotifupan.data

import androidx.lifecycle.LiveData
import androidx.room.*

/**
 * 题目数据访问对象
 */
@Dao
interface QuestionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(question: Question): Long
    
    @Update
    suspend fun update(question: Question)
    
    @Delete
    suspend fun delete(question: Question)
    
    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    fun getAllQuestions(): LiveData<List<Question>>
    
    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    suspend fun getAllQuestionsSync(): List<Question>
    
    @Query("SELECT * FROM questions ORDER BY createdAt DESC")
    fun getAllQuestionsOrderByTime(): LiveData<List<Question>>
    
    @Query("SELECT * FROM questions WHERE userNotes IS NOT NULL AND userNotes != '' ORDER BY createdAt DESC")
    fun getAllQuestionsOrderByNotes(): LiveData<List<Question>>
    
    @Query("SELECT * FROM questions WHERE tags LIKE '%' || :tag || '%' ORDER BY createdAt DESC")
    fun getQuestionsByTag(tag: String): LiveData<List<Question>>
    
    @Query("SELECT * FROM questions WHERE tags LIKE '%' || :tag || '%' AND userNotes IS NOT NULL AND userNotes != '' ORDER BY createdAt DESC")
    fun getQuestionsByTagOrderByNotes(tag: String): LiveData<List<Question>>
    
    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestionById(id: String): Question?
    
    @Query("SELECT * FROM questions WHERE reviewState = :state ORDER BY createdAt DESC")
    fun getQuestionsByReviewState(state: String): LiveData<List<Question>>
    
    @Query("DELETE FROM questions")
    suspend fun deleteAll()
}

