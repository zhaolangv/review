package com.gongkao.cuotifupan.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 已排除图片数据访问对象
 */
@Dao
interface ExcludedImageDao {
    
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(excludedImage: ExcludedImage)
    
    @Query("SELECT * FROM excluded_images WHERE imagePath = :path")
    suspend fun getByPath(path: String): ExcludedImage?
    
    @Query("SELECT imagePath FROM excluded_images")
    suspend fun getAllPaths(): List<String>
    
    @Query("DELETE FROM excluded_images WHERE imagePath = :path")
    suspend fun deleteByPath(path: String)
    
    @Query("DELETE FROM excluded_images")
    suspend fun deleteAll()
}

