package com.gongkao.cuotifupan.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 题目相关 API 接口
 */
interface QuestionApiService {
    
    /**
     * 上传题目图片和数据，获取题目内容（题干、选项等）
     * 此接口只返回题目内容，不返回答案和解析
     * 
     * @param image 图片文件（必需）
     * @param rawText OCR识别的原始文本（可选，如果提供可加速处理）
     * @param questionText 前端提取的题干（可选，可能不完整或不准确）
     * @param options 前端提取的选项，JSON字符串格式（可选）
     * @param questionType 题目类型，默认 "TEXT"（可选）
     * @param forceReanalyze 是否强制重新AI分析，默认 false（可选）
     */
    @Multipart
    @POST("api/questions/analyze")
    suspend fun analyzeQuestion(
        @Part image: MultipartBody.Part,
        @Part("raw_text") rawText: RequestBody? = null,
        @Part("question_text") questionText: RequestBody? = null,
        @Part("options") options: RequestBody? = null,
        @Part("question_type") questionType: RequestBody? = null,
        @Part("force_reanalyze") forceReanalyze: RequestBody? = null
    ): Response<QuestionContentResponse>
    
    /**
     * 批量上传题目图片和数据，获取多个题目的内容
     * 此接口只返回题目内容，不返回答案和解析
     * 
     * @param request 批量请求数据（JSON格式）
     */
    @POST("api/questions/extract/batch")
    suspend fun analyzeQuestionsBatch(
        @Body request: BatchQuestionRequest
    ): Response<BatchQuestionResponse>
    
    /**
     * 获取题目详情（答案、解析、标签、知识点等）
     * 此接口返回完整的答案解析和分类信息
     * 
     * @param questionId 题目ID（后端返回的id）
     */
    @GET("api/questions/{question_id}/detail")
    suspend fun getQuestionDetail(
        @Path("question_id") questionId: String
    ): Response<QuestionDetailResponse>
    
    /**
     * 提交异步批量处理任务
     * 立即返回任务ID，后端在后台处理
     * 
     * @param request 批量请求数据（JSON格式）
     */
    @POST("api/questions/extract/batch/async")
    suspend fun submitBatchAsync(
        @Body request: BatchQuestionRequest
    ): Response<AsyncTaskSubmitResponse>
    
    /**
     * 查询异步任务状态
     * 
     * @param taskId 任务ID
     */
    @GET("api/tasks/{task_id}/status")
    suspend fun getTaskStatus(
        @Path("task_id") taskId: String
    ): Response<TaskStatusResponse>
    
    /**
     * 获取异步任务结果
     * 
     * @param taskId 任务ID
     */
    @GET("api/tasks/{task_id}/result")
    suspend fun getTaskResult(
        @Path("task_id") taskId: String
    ): Response<TaskResultResponse>
    
    /**
     * 检查版本信息
     * 
     * @param clientVersion 客户端当前版本号
     * @param deviceId 设备ID
     */
    @GET("api/version")
    fun checkVersion(
        @Query("client_version") clientVersion: String,
        @Query("device_id") deviceId: String
    ): retrofit2.Call<VersionResponse>
    
    /**
     * 清除图片中的手写笔记
     * 系统会自动尝试两个服务（TextIn → 有道），确保高可用性
     * 
     * @param image 图片文件（必需），支持：png, jpg, jpeg, gif, bmp
     * @param deviceId 设备ID（必需），用于Pro配额检查和扣除
     * @param saveToServer 是否保存到服务器（"true"/"false"，默认"false"），可选
     */
    @Multipart
    @POST("api/handwriting/remove")
    suspend fun removeHandwriting(
        @Part image: MultipartBody.Part,
        @Part("device_id") deviceId: RequestBody,
        @Part("save_to_server") saveToServer: RequestBody? = null
    ): Response<HandwritingRemovalResponse>
}

