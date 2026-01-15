package com.gongkao.cuotifupan.data

import androidx.lifecycle.LiveData
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * 数学练习会话数据访问对象
 */
@Dao
interface MathPracticeSessionDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: MathPracticeSession): Long
    
    @Update
    suspend fun update(session: MathPracticeSession)
    
    @Delete
    suspend fun delete(session: MathPracticeSession)
    
    @Query("SELECT * FROM math_practice_sessions ORDER BY startTime DESC")
    fun getAllSessions(): LiveData<List<MathPracticeSession>>
    
    @Query("SELECT * FROM math_practice_sessions ORDER BY startTime DESC")
    suspend fun getAllSessionsSync(): List<MathPracticeSession>
    
    @Query("SELECT * FROM math_practice_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): MathPracticeSession?
    
    @Query("SELECT * FROM math_practice_sessions WHERE practiceType = :practiceType ORDER BY startTime DESC")
    fun getSessionsByType(practiceType: String): LiveData<List<MathPracticeSession>>
    
    @Query("DELETE FROM math_practice_sessions")
    suspend fun deleteAll()
}

