package com.gongkao.cuotifupan.api

import retrofit2.Response
import retrofit2.http.*

/**
 * 数据迁移相关 API 接口
 */
interface MigrationApiService {
    
    /**
     * 创建迁移码（导出数据）
     * 
     * @param request 迁移数据请求
     */
    @POST("api/migration/create")
    suspend fun createMigration(
        @Body request: CreateMigrationRequest
    ): Response<CreateMigrationResponse>
    
    /**
     * 获取迁移数据（导入数据）
     * 
     * @param request 获取迁移数据请求
     */
    @POST("api/migration/retrieve")
    suspend fun retrieveMigration(
        @Body request: RetrieveMigrationRequest
    ): Response<RetrieveMigrationResponse>
    
    /**
     * 确认迁移完成（删除数据）
     * 
     * @param request 确认迁移请求
     */
    @POST("api/migration/confirm")
    suspend fun confirmMigration(
        @Body request: ConfirmMigrationRequest
    ): Response<ConfirmMigrationResponse>
}

/**
 * 创建迁移码请求
 */
data class CreateMigrationRequest(
    val device_id: String,
    val data: MigrationData,
    val images: List<MigrationImage>
)

/**
 * 迁移数据
 */
data class MigrationData(
    val questions: List<QuestionMigrationData>,
    val notes: List<NoteMigrationData>,
    val flashcards: List<FlashcardMigrationData>
)

/**
 * 题目迁移数据
 */
data class QuestionMigrationData(
    val id: String,
    val imagePath: String,
    val originalImagePath: String?,
    val cleanedImagePath: String?,
    val hiddenOptionsImagePath: String?,
    val rawText: String,
    val questionText: String,
    val frontendRawText: String?,
    val options: String,
    val createdAt: Long,
    val reviewState: String,
    val userNotes: String,
    val confidence: Float,
    val questionType: String,
    val backendQuestionId: String?,
    val backendQuestionText: String?,
    val answerLoaded: Boolean,
    val correctAnswer: String?,
    val explanation: String?,
    val tags: String
)

/**
 * 笔记迁移数据
 */
data class NoteMigrationData(
    val id: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: String,
    val questionId: String?,
    val isFavorite: Boolean
)

/**
 * 记忆卡片迁移数据
 */
data class FlashcardMigrationData(
    val id: String,
    val front: String,
    val back: String,
    val createdAt: Long,
    val updatedAt: Long,
    val tags: String,
    val questionId: String?,
    val isFavorite: Boolean,
    val reviewState: String
)

/**
 * 迁移图片信息
 */
data class MigrationImage(
    val question_id: String,
    val image_type: String, // "main", "original", "cleaned"
    val image_base64: String // Base64编码的图片数据
)

/**
 * 创建迁移码响应
 */
data class CreateMigrationResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val data: CreateMigrationData? = null
)

data class CreateMigrationData(
    val migration_code: String,
    val expires_at: String,
    val data_size: Long,
    val image_count: Int
)

/**
 * 获取迁移数据请求
 */
data class RetrieveMigrationRequest(
    val migration_code: String,
    val device_id: String
)

/**
 * 获取迁移数据响应
 */
data class RetrieveMigrationResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val data: RetrieveMigrationData? = null
)

data class RetrieveMigrationData(
    val questions: List<QuestionMigrationData>,
    val notes: List<NoteMigrationData>,
    val flashcards: List<FlashcardMigrationData>,
    val images: List<MigrationImageInfo>,
    val created_at: String,
    val expires_at: String
)

/**
 * 迁移图片信息（包含URL）
 */
data class MigrationImageInfo(
    val question_id: String,
    val image_type: String, // "main", "original", "cleaned"
    val image_url: String // 图片下载URL
)

/**
 * 确认迁移完成请求
 */
data class ConfirmMigrationRequest(
    val migration_code: String,
    val device_id: String
)

/**
 * 确认迁移完成响应
 */
data class ConfirmMigrationResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null
)

