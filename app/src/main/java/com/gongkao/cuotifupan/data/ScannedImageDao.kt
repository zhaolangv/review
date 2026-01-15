package com.gongkao.cuotifupan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 扫描图片数据访问对象
 */
@Dao
interface ScannedImageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scannedImage: ScannedImage)
    
    @Query("SELECT * FROM scanned_images WHERE imagePath = :path")
    suspend fun getByPath(path: String): ScannedImage?
    
    @Query("SELECT * FROM scanned_images WHERE fileName = :fileName AND fileSize = :fileSize")
    suspend fun getByFileNameAndSize(fileName: String, fileSize: Long): ScannedImage?
    
    @Query("SELECT * FROM scanned_images WHERE isQuestion = 1 ORDER BY scannedAt DESC LIMIT :limit")
    suspend fun getRecentScannedQuestions(limit: Int): List<ScannedImage>
    
    @Query("SELECT * FROM scanned_images ORDER BY scannedAt DESC LIMIT :limit")
    suspend fun getRecentScanned(limit: Int): List<ScannedImage>
    
    @Query("SELECT imagePath FROM scanned_images")
    suspend fun getAllPaths(): List<String>
    
    @Query("SELECT imagePath FROM scanned_images WHERE isQuestion = 1")
    suspend fun getQuestionPaths(): List<String>
    
    @Query("DELETE FROM scanned_images WHERE imagePath = :path")
    suspend fun deleteByPath(path: String)
    
    @Query("DELETE FROM scanned_images")
    suspend fun deleteAll()
}

