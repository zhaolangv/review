# 前后端接口对接文档

## 概述

本文档详细说明前端（Android App）与后端API的对接规范，包括接口地址、请求参数、响应格式、调用示例等。

---

## 基础信息

### 后端服务器地址
```
https://830jg058pm01.vicp.fun
```

### 接口基础路径
```
/api/questions
```

---

## 接口列表

| 序号 | 接口 | 方法 | 说明 | 优先级 |
|------|------|------|------|--------|
| 1 | `/api/questions/analyze` | POST | 题目内容分析（获取题干、选项等） | ⭐⭐⭐ 核心接口 |
| 2 | `/api/questions/{question_id}/detail` | GET | 获取题目详情（答案、解析、标签等） | ⭐⭐⭐ 核心接口 |
| 3 | `/api/questions/{question_id}/notes` | GET | 获取题目的笔记/评论列表 | ⭐⭐ 重要 |
| 4 | `/api/questions/{question_id}/notes` | POST | 创建笔记/评论 | ⭐⭐ 重要 |
| 5 | `/api/notes/{note_id}/like` | POST | 点赞/取消点赞笔记 | ⭐ 可选 |
| 6 | `/api/notes/{note_id}` | DELETE | 删除笔记 | ⭐ 可选 |

**说明**：
- ⭐⭐⭐ 核心接口：必须实现
- ⭐⭐ 重要：建议实现
- ⭐ 可选：根据需求实现

---

## 接口详情

### POST /api/questions/analyze

上传题目图片和相关数据，获取题目内容（题干、选项等）。**此接口只返回题目内容，不返回答案和解析。**

**使用场景**：
- 批量处理时，快速获取题目内容用于去重和显示
- 首次检测到题目时，获取完整的题干和选项

#### 请求格式

**Content-Type**: `multipart/form-data`

#### 请求参数

| 参数名 | 类型 | 必填 | 说明 | 前端字段名 |
|--------|------|------|------|-----------|
| `image` | File | ✅ | 题目截图文件（jpg/png/gif/bmp，建议不超过10MB） | `image` |
| `raw_text` | String | ❌ | 前端OCR识别的原始文本（所有文字），如果提供可加速处理 | `raw_text` |
| `question_text` | String | ❌ | 前端提取的题干（可能不完整或不准确），如果提供可加速处理 | `question_text` |
| `options` | String/Array | ❌ | 前端提取的选项，JSON字符串格式：`["A. 选项A", "B. 选项B", ...]` | `options` |
| `question_type` | String | ❌ | 题目类型，默认 `"TEXT"`（文字题）或 `"GRAPHIC"`（图推题） | `question_type` |
| `force_reanalyze` | Boolean | ❌ | 是否强制重新AI分析，默认 `false`。当 `true` 时，即使发现重复题也会调用AI重新分析，并更新数据库 | `force_reanalyze` |

#### 响应格式

**Content-Type**: `application/json`

**HTTP状态码**:
- `200 OK`: 请求成功
- `400 Bad Request`: 请求参数错误
- `500 Internal Server Error`: 服务器内部错误
- `503 Service Unavailable`: 服务暂时不可用（如AI服务限流）

#### 成功响应示例（只返回题目内容）

```json
{
  "id": "uuid-1234",
  "screenshot": "/uploads/2025/12/04/q1234.png",
  "raw_text": "某城市人口为A，增长率为…（OCR 文本）",
  "question_text": "完整题干（AI提取或已有数据）",
  "question_type": "单选",
  "options": ["A. ...", "B. ...", "C. ...", "D. ..."],
  "ocr_confidence": 0.92,
  
  "from_cache": true,
  "is_duplicate": true,
  "saved_to_db": false,
  "similarity_score": 0.92,
  "matched_question_id": "uuid-1223"
}
```

**注意**：此接口**不返回**答案、解析、标签、知识点等详细信息，这些需要通过详情接口获取。

#### 错误响应示例

```json
{
  "error": "错误信息",
  "code": 400,
  "details": "详细错误说明（可选）"
}
```

#### 响应字段说明

##### 基础字段

| 字段名 | 类型 | 说明 | 前端使用 |
|--------|------|------|---------|
| `id` | String | 后端返回的题目ID（UUID） | 保存到 `Question.backendQuestionId` |
| `screenshot` | String | 图片在服务器上的路径 | 可选，用于显示 |
| `raw_text` | String | OCR原始文本 | 更新 `Question.rawText` |
| `question_text` | String | 完整题干（AI提取） | 保存到 `Question.backendQuestionText` |
| `question_type` | String | 题目类型 | 更新 `Question.questionType` |
| `options` | Array[String] | 选项列表 | 更新 `Question.options`（JSON字符串） |

##### 其他字段

| 字段名 | 类型 | 说明 | 前端使用 |
|--------|------|------|---------|
| `ocr_confidence` | Number | OCR置信度 | 用于显示OCR质量 |

##### 缓存和去重字段（重要）

| 字段名 | 类型 | 说明 | 前端使用 |
|--------|------|------|---------|
| `from_cache` | Boolean | `true`表示来自缓存（快速返回，未调用AI），`false`表示调用了AI | 用于日志和调试 |
| `is_duplicate` | Boolean | `true`表示是重复题（数据库中已存在），`false`表示新题 | 用于判断是否需要保存 |
| `saved_to_db` | Boolean | `true`表示新存入数据库或更新了数据库，`false`表示仅读取已有数据 | 用于判断是否需要更新本地数据库 |
| `similarity_score` | Number | 相似度分数（0-1），仅在`is_duplicate=true`时返回 | 用于显示匹配度 |
| `matched_question_id` | String | 匹配到的题目ID，仅在`is_duplicate=true`时返回 | 用于关联显示 |

---

### GET /api/questions/{question_id}/detail

获取题目的详细信息，包括答案、解析、标签、知识点等。**此接口返回答案解析和分类信息。**

**使用场景**：
- 用户打开题目详情页时，按需加载完整答案和解析
- 需要查看题目的标签、知识点等分类信息时

#### 请求参数

| 参数名 | 类型 | 位置 | 必填 | 说明 |
|--------|------|------|------|------|
| `question_id` | String | Path | ✅ | 题目ID（后端返回的 `id`） |

#### 响应格式

**Content-Type**: `application/json`

#### 成功响应示例

```json
{
  "id": "uuid-1234",
  "question_id": "uuid-1234",
  
  "answer_versions": [
    {
      "id": "ans_001",
      "source_name": "粉笔",
      "source_type": "机构",
      "answer": "B",
      "explanation": "先算出……（粉笔解析）",
      "confidence": 0.9,
      "is_user_preferred": true,
      "created_at": "2025-12-04",
      "updated_at": "2025-12-04"
    },
    {
      "id": "ans_002",
      "source_name": "华图",
      "source_type": "机构",
      "answer": "C",
      "explanation": "华图给出的思路是……",
      "confidence": 0.85,
      "is_user_preferred": false,
      "created_at": "2025-12-04"
    },
    {
      "id": "ans_003",
      "source_name": "AI",
      "source_type": "AI",
      "answer": "B",
      "explanation": "AI 推理：……",
      "confidence": 0.7,
      "is_user_preferred": false,
      "created_at": "2025-12-04"
    }
  ],
  
  "correct_answer": "B",
  "explanation": "采用粉笔解析+AI 补充理解",
  
  "tags": ["行测-数量关系", "比例"],
  "knowledge_points": ["比率与比例", "增长率计算"],
  "source": "微博-考研帮",
  "source_url": "https://weibo.com/xxx",
  "similar_questions": ["uuid-1223", "uuid-1209"],
  
  "encountered_date": "2025-12-03",
  "is_error": true,
  "error_reason": "审题不清",
  "difficulty": 3,
  "time_spent": 120,
  "last_reviewed": "2025-12-04",
  "attempts": 2,
  "spaced_repetition": {
    "ef": 2.5,
    "interval": 3,
    "repetitions": 1
  },
  "user_note": "常见陷阱：没有把单位统一",
  "priority": "中",
  
  "created_at": "2025-12-04",
  "updated_at": "2025-12-04"
}
```

#### 响应字段说明

##### 答案相关字段

| 字段名 | 类型 | 说明 | 前端使用 |
|--------|------|------|---------|
| `answer_versions` | Array | 多来源答案版本列表 | 显示在详情页，用户可选择 |
| `correct_answer` | String | 正确答案（汇总） | 保存到 `Question.correctAnswer` |
| `explanation` | String | 详细解析（汇总） | 保存到 `Question.explanation` |

##### 答案版本对象 (`answer_versions[]`)

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `id` | String | 答案版本ID |
| `source_name` | String | 来源名称（如"粉笔"、"华图"、"AI"） |
| `source_type` | String | 来源类型（"机构" 或 "AI"） |
| `answer` | String | 该来源的答案 |
| `explanation` | String | 该来源的解析 |
| `confidence` | Number | 置信度（0-1） |
| `is_user_preferred` | Boolean | 是否用户偏好 |
| `created_at` | String | 创建时间 |
| `updated_at` | String | 更新时间 |

##### 分类和标签字段

| 字段名 | 类型 | 说明 | 前端使用 |
|--------|------|------|---------|
| `tags` | Array[String] | 标签列表（如：["行测-数量关系", "比例"]） | 用于题目分类和筛选 |
| `knowledge_points` | Array[String] | 知识点列表（如：["比率与比例", "增长率计算"]） | 用于知识点统计和复习 |
| `source` | String | 题目来源 | 显示题目来源信息 |
| `source_url` | String | 来源URL | 可点击跳转到来源 |
| `similar_questions` | Array[String] | 相似题目ID列表 | 用于推荐相似题目 |

##### 学习进度字段

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `encountered_date` | String | 遇到日期 |
| `is_error` | Boolean | 是否做错 |
| `error_reason` | String | 错误原因 |
| `difficulty` | Number | 难度（1-5） |
| `time_spent` | Number | 用时（秒） |
| `last_reviewed` | String | 最后复习时间 |
| `attempts` | Number | 尝试次数 |
| `spaced_repetition` | Object | 间隔重复数据 |
| `user_note` | String | 用户笔记 |
| `priority` | String | 优先级 |

---

## 笔记/评论相关接口

### GET /api/questions/{question_id}/notes

获取题目的所有公开笔记/评论。

#### 请求参数

| 参数名 | 类型 | 位置 | 必填 | 说明 |
|--------|------|------|------|------|
| `question_id` | String | Path | ✅ | 题目ID |

#### 响应格式

**Content-Type**: `application/json`

```json
{
  "notes": [
    {
      "id": "note_001",
      "question_id": "uuid-1234",
      "user": {
        "id": "user_001",
        "username": "张三",
        "avatar": "/avatars/user_001.jpg"
      },
      "content": "这道题的陷阱在于...",
      "note_type": "insight",
      "parent_id": null,
      "likes_count": 5,
      "is_pinned": false,
      "is_liked": false,
      "replies_count": 2,
      "created_at": "2025-12-04T10:00:00Z",
      "updated_at": "2025-12-04T10:00:00Z"
    }
  ],
  "total": 10
}
```

#### 响应字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `notes` | Array | 笔记/评论列表 |
| `total` | Number | 总数量 |
| `notes[].id` | String | 笔记ID |
| `notes[].question_id` | String | 题目ID |
| `notes[].user` | Object | 用户信息 |
| `notes[].user.id` | String | 用户ID |
| `notes[].user.username` | String | 用户名 |
| `notes[].user.avatar` | String | 头像URL |
| `notes[].content` | String | 笔记内容 |
| `notes[].note_type` | String | 笔记类型：`"note"`（笔记）、`"comment"`（评论）、`"insight"`（心得） |
| `notes[].parent_id` | String | 父评论ID（如果是回复） |
| `notes[].likes_count` | Number | 点赞数 |
| `notes[].is_pinned` | Boolean | 是否置顶 |
| `notes[].is_liked` | Boolean | 当前用户是否点赞 |
| `notes[].replies_count` | Number | 回复数量 |
| `notes[].created_at` | String | 创建时间（ISO 8601格式） |
| `notes[].updated_at` | String | 更新时间（ISO 8601格式） |

---

### POST /api/questions/{question_id}/notes

创建笔记/评论。

#### 请求参数

| 参数名 | 类型 | 位置 | 必填 | 说明 |
|--------|------|------|------|------|
| `question_id` | String | Path | ✅ | 题目ID |
| `content` | String | Body | ✅ | 笔记/评论内容 |
| `note_type` | String | Body | ❌ | 笔记类型：`"note"`、`"comment"`、`"insight"`，默认 `"comment"` |
| `parent_id` | String | Body | ❌ | 如果是回复，传入父评论ID |

#### 请求格式

**Content-Type**: `application/json`

```json
{
  "content": "这道题的陷阱在于...",
  "note_type": "insight",
  "parent_id": null
}
```

#### 响应格式

**Content-Type**: `application/json`

```json
{
  "id": "note_001",
  "question_id": "uuid-1234",
  "user": {
    "id": "user_001",
    "username": "张三",
    "avatar": "/avatars/user_001.jpg"
  },
  "content": "这道题的陷阱在于...",
  "note_type": "insight",
  "parent_id": null,
  "likes_count": 0,
  "is_pinned": false,
  "is_liked": false,
  "replies_count": 0,
  "created_at": "2025-12-04T10:00:00Z",
  "updated_at": "2025-12-04T10:00:00Z"
}
```

---

### POST /api/notes/{note_id}/like

点赞/取消点赞笔记。

#### 请求参数

| 参数名 | 类型 | 位置 | 必填 | 说明 |
|--------|------|------|------|------|
| `note_id` | String | Path | ✅ | 笔记ID |

#### 响应格式

**Content-Type**: `application/json`

```json
{
  "id": "note_001",
  "likes_count": 6,
  "is_liked": true
}
```

**说明**：
- 如果之前未点赞，执行点赞操作，`is_liked` 变为 `true`，`likes_count` 增加1
- 如果之前已点赞，执行取消点赞操作，`is_liked` 变为 `false`，`likes_count` 减少1

---

### DELETE /api/notes/{note_id}

删除笔记（软删除）。

#### 请求参数

| 参数名 | 类型 | 位置 | 必填 | 说明 |
|--------|------|------|------|------|
| `note_id` | String | Path | ✅ | 笔记ID |

#### 响应格式

**Content-Type**: `application/json`

```json
{
  "success": true,
  "message": "笔记已删除"
}
```

**说明**：
- 只有笔记的创建者可以删除
- 删除是软删除，数据不会真正删除，只是标记为已删除

---

## 前端调用示例

### 1. 使用 Retrofit 接口定义

```kotlin
interface QuestionApiService {
    // 题目内容分析接口（只返回题目内容，不返回答案）
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
    
    // 获取题目详情（答案、解析、标签等）
    @GET("api/questions/{question_id}/detail")
    suspend fun getQuestionDetail(
        @Path("question_id") questionId: String
    ): Response<QuestionDetailResponse>
    
    // 获取笔记列表
    @GET("api/questions/{question_id}/notes")
    suspend fun getQuestionNotes(
        @Path("question_id") questionId: String
    ): Response<QuestionNotesResponse>
    
    // 创建笔记
    @POST("api/questions/{question_id}/notes")
    suspend fun createNote(
        @Path("question_id") questionId: String,
        @Body request: CreateNoteRequest
    ): Response<NoteResponse>
    
    // 点赞/取消点赞笔记
    @POST("api/notes/{note_id}/like")
    suspend fun likeNote(
        @Path("note_id") noteId: String
    ): Response<LikeNoteResponse>
    
    // 删除笔记
    @DELETE("api/notes/{note_id}")
    suspend fun deleteNote(
        @Path("note_id") noteId: String
    ): Response<DeleteNoteResponse>
}

// 请求和响应数据类
data class CreateNoteRequest(
    val content: String,
    val note_type: String? = "comment",
    val parent_id: String? = null
)

data class QuestionNotesResponse(
    val notes: List<NoteResponse>,
    val total: Int
)

data class NoteResponse(
    val id: String,
    val question_id: String,
    val user: UserInfo,
    val content: String,
    val note_type: String,
    val parent_id: String?,
    val likes_count: Int,
    val is_pinned: Boolean,
    val is_liked: Boolean,
    val replies_count: Int,
    val created_at: String,
    val updated_at: String
)

data class UserInfo(
    val id: String,
    val username: String,
    val avatar: String?
)

data class LikeNoteResponse(
    val id: String,
    val likes_count: Int,
    val is_liked: Boolean
)

data class DeleteNoteResponse(
    val success: Boolean,
    val message: String
)

// 题目内容响应（只包含题目内容，不包含答案）
data class QuestionContentResponse(
    val id: String,
    val screenshot: String?,
    val raw_text: String,
    val question_text: String,
    val question_type: String,
    val options: List<String>,
    val ocr_confidence: Double?,
    val from_cache: Boolean?,
    val is_duplicate: Boolean?,
    val saved_to_db: Boolean?,
    val similarity_score: Double?,
    val matched_question_id: String?
)

// 题目详情响应（包含答案、解析、标签等）
data class QuestionDetailResponse(
    val id: String,
    val question_id: String,
    val answer_versions: List<AnswerVersion>,
    val correct_answer: String?,
    val explanation: String?,
    val tags: List<String>?,
    val knowledge_points: List<String>?,
    val source: String?,
    val source_url: String?,
    val similar_questions: List<String>?,
    val encountered_date: String?,
    val is_error: Boolean?,
    val error_reason: String?,
    val difficulty: Int?,
    val time_spent: Int?,
    val last_reviewed: String?,
    val attempts: Int?,
    val spaced_repetition: SpacedRepetition?,
    val user_note: String?,
    val priority: String?,
    val created_at: String?,
    val updated_at: String?
)

data class AnswerVersion(
    val id: String,
    val source_name: String,
    val source_type: String,
    val answer: String,
    val explanation: String,
    val confidence: Double,
    val is_user_preferred: Boolean,
    val created_at: String?,
    val updated_at: String?
)

data class SpacedRepetition(
    val ef: Double,
    val interval: Int,
    val repetitions: Int
)
```

### 2. 获取题目详情示例

```kotlin
suspend fun getQuestionDetail(questionId: String): QuestionDetailResponse? {
    try {
        val response = ApiClient.questionApiService.getQuestionDetail(questionId)
        if (response.isSuccessful && response.body() != null) {
            return response.body()
        } else {
            throw Exception("请求失败: ${response.message()}")
        }
    } catch (e: Exception) {
        Log.e(TAG, "获取题目详情失败", e)
        throw e
    }
}
```

### 3. 直接调用题目分析示例（不使用队列）

```kotlin
suspend fun analyzeQuestionDirectly(question: Question): QuestionContentResponse? {
    try {
        // 准备图片文件
        val imageFile = File(question.imagePath)
        if (!imageFile.exists()) {
            throw Exception("图片文件不存在")
        }
        
        // 创建 MultipartBody.Part
        val requestFile = imageFile.asRequestBody("image/*".toMediaTypeOrNull())
        val imagePart = MultipartBody.Part.createFormData("image", imageFile.name, requestFile)
        
        // 创建其他字段（可选参数）
        val rawTextPart = question.rawText.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())
        val questionTextPart = question.questionText.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())
        val optionsPart = question.options.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())
        val questionTypePart = question.questionType.takeIf { it.isNotBlank() }
            ?.toRequestBody("text/plain".toMediaTypeOrNull())
        val forceReanalyzePart = null // 或 "true".toRequestBody("text/plain".toMediaTypeOrNull())
        
        // 发送请求
        val response = ApiClient.questionApiService.analyzeQuestion(
            image = imagePart,
            rawText = rawTextPart,
            questionText = questionTextPart,
            options = optionsPart,
            questionType = questionTypePart,
            forceReanalyze = forceReanalyzePart
        )
        
        if (response.isSuccessful && response.body() != null) {
            return response.body()
        } else {
            throw Exception("请求失败: ${response.message()}")
        }
    } catch (e: Exception) {
        Log.e(TAG, "分析题目失败", e)
        throw e
    }
}
```

### 4. 获取笔记列表示例

```kotlin
suspend fun getQuestionNotes(questionId: String): List<NoteResponse>? {
    try {
        val response = ApiClient.questionApiService.getQuestionNotes(questionId)
        if (response.isSuccessful && response.body() != null) {
            return response.body()!!.notes
        } else {
            Log.e(TAG, "获取笔记失败: ${response.message()}")
            return null
        }
    } catch (e: Exception) {
        Log.e(TAG, "获取笔记异常", e)
        return null
    }
}
```

### 5. 创建笔记示例

```kotlin
suspend fun createNote(
    questionId: String,
    content: String,
    noteType: String = "comment",
    parentId: String? = null
): NoteResponse? {
    try {
        val request = CreateNoteRequest(
            content = content,
            note_type = noteType,
            parent_id = parentId
        )
        val response = ApiClient.questionApiService.createNote(questionId, request)
        if (response.isSuccessful && response.body() != null) {
            return response.body()
        } else {
            Log.e(TAG, "创建笔记失败: ${response.message()}")
            return null
        }
    } catch (e: Exception) {
        Log.e(TAG, "创建笔记异常", e)
        return null
    }
}
```

### 6. 点赞笔记示例

```kotlin
suspend fun toggleLikeNote(noteId: String): LikeNoteResponse? {
    try {
        val response = ApiClient.questionApiService.likeNote(noteId)
        if (response.isSuccessful && response.body() != null) {
            return response.body()
        } else {
            Log.e(TAG, "点赞失败: ${response.message()}")
            return null
        }
    } catch (e: Exception) {
        Log.e(TAG, "点赞异常", e)
        return null
    }
}
```

### 7. 删除笔记示例

```kotlin
suspend fun deleteNote(noteId: String): Boolean {
    try {
        val response = ApiClient.questionApiService.deleteNote(noteId)
        if (response.isSuccessful && response.body() != null) {
            return response.body()!!.success
        } else {
            Log.e(TAG, "删除笔记失败: ${response.message()}")
            return false
        }
    } catch (e: Exception) {
        Log.e(TAG, "删除笔记异常", e)
        return false
    }
}
```

### 8. 使用队列管理器调用（推荐，仅用于题目内容分析）

```kotlin
// 批量处理时：只获取题目内容
QuestionApiQueue.enqueue(
    question = question,
    loadAnswer = false, // 只获取题目内容
    onSuccess = { response ->
                // 更新题目信息（只保存题目内容）
                val updatedQuestion = question.copy(
                    backendQuestionId = response.id,
                    backendQuestionText = response.question_text ?: question.questionText,
                    answerLoaded = false // 答案未加载
                )
                database.questionDao().update(updatedQuestion)
    },
    onError = { error ->
        // 处理错误
        Log.e(TAG, "获取题目内容失败", error)
    }
)

// 详情页：加载完整答案和解析
lifecycleScope.launch {
    try {
        val detailResponse = ApiClient.questionApiService.getQuestionDetail(question.backendQuestionId ?: return@launch)
        if (detailResponse.isSuccessful && detailResponse.body() != null) {
            val detail = detailResponse.body()!!
            
            // 更新题目信息（保存答案）
            val updatedQuestion = question.copy(
                answerLoaded = true, // 答案已加载
                correctAnswer = detail.correct_answer,
                explanation = detail.explanation
            )
            database.questionDao().update(updatedQuestion)
            
            // 显示答案详情
            displayAnswerDetails(detail)
        }
    } catch (e: Exception) {
        Log.e(TAG, "获取答案失败", e)
    }
}
```

---

## 业务逻辑说明

### 后端处理流程

1. **接收请求**：验证图片文件格式和大小
2. **图片哈希检查**（如果 `force_reanalyze=false`）：
   - 计算图片感知哈希（pHash）
   - 查询数据库，如果找到完全匹配（哈希相同），直接返回
   - 设置 `from_cache=true`, `is_duplicate=true`, `saved_to_db=false`
3. **文本相似度检查**（如果图片哈希不匹配且 `force_reanalyze=false`）：
   - 使用 `raw_text` 或后端OCR结果计算文本指纹
   - 查询数据库，如果相似度 > 85%，返回匹配的题目
   - 设置 `from_cache=true`, `is_duplicate=true`, `saved_to_db=false`, `similarity_score=相似度`, `matched_question_id=匹配的题目ID`
4. **AI处理**（如果未找到匹配或 `force_reanalyze=true`）：
   - 如果前端未提供OCR结果，后端自行进行OCR识别
   - 调用AI提取完整题干、选项、生成答案和解析
   - 如果 `is_duplicate=true` 且 `force_reanalyze=true`：更新数据库中的题目和答案版本
   - 如果 `is_duplicate=false`：保存新题目到数据库
   - 设置 `from_cache=false`, `saved_to_db=true`
5. **返回响应**：返回完整的题目详情数据

### 前端处理流程

#### 批量处理场景（应用启动时）

```
检测到文字题
  ↓
调用 POST /api/questions/analyze（只获取题目内容）
  ↓
获取题目内容（快速，可能来自缓存）
  ↓
检查响应字段：
  - from_cache: 是否来自缓存
  - is_duplicate: 是否重复题
  - saved_to_db: 是否存入数据库
  ↓
更新数据库（只保存题目内容，answerLoaded=false）
  ↓
显示通知
```

#### 用户打开详情页

```
检查 answerLoaded
  ↓
如果已加载 → 直接从数据库显示答案
  ↓
如果未加载 → 调用 GET /api/questions/{question_id}/detail
  ↓
获取完整答案和解析（包括answer_versions、tags、knowledge_points等）
  ↓
更新数据库（保存答案，answerLoaded=true）
  ↓
显示完整答案（包括answer_versions列表、标签、知识点等）
```

---

## 错误处理

### 常见错误码

| HTTP状态码 | 说明 | 前端处理建议 |
|-----------|------|------------|
| `200 OK` | 请求成功 | 正常处理响应数据 |
| `400 Bad Request` | 请求参数错误（如图片格式不支持、文件过大） | 提示用户检查图片格式和大小 |
| `500 Internal Server Error` | 服务器内部错误（如AI调用失败） | 提示用户稍后重试 |
| `503 Service Unavailable` | 服务暂时不可用（如AI服务限流） | 提示用户稍后重试，或加入重试队列 |

### 前端错误处理示例

```kotlin
try {
    val response = ApiClient.questionApiService.analyzeQuestion(...)
    
    when {
        response.isSuccessful && response.body() != null -> {
            // 成功处理
            handleSuccess(response.body()!!)
        }
        response.code() == 400 -> {
            // 参数错误
            showError("请求参数错误，请检查图片格式和大小")
        }
        response.code() == 500 -> {
            // 服务器错误
            showError("服务器错误，请稍后重试")
        }
        response.code() == 503 -> {
            // 服务不可用
            showError("服务暂时不可用，请稍后重试")
        }
        else -> {
            // 其他错误
            showError("请求失败: ${response.message()}")
        }
    }
} catch (e: IOException) {
    // 网络错误
    showError("网络连接失败，请检查网络设置")
} catch (e: Exception) {
    // 其他异常
    showError("发生错误: ${e.message}")
}
```

---

## 性能优化建议

### 1. 请求队列管理

- 使用 `QuestionApiQueue` 控制并发数（最多3个同时请求）
- 批量处理时调用 `/api/questions/analyze`，只获取题目内容
- 详情页调用 `/api/questions/{question_id}/detail`，按需加载完整答案和解析

### 2. 缓存策略

- 检查 `from_cache` 字段，如果为 `true`，说明是快速返回
- 检查 `is_duplicate` 字段，如果为 `true`，说明是重复题
- 根据 `saved_to_db` 决定是否需要更新本地数据库

### 3. 超时设置

- 连接超时：30秒
- 读取超时：30秒
- 写入超时：30秒

### 4. 重试机制

- 网络错误：自动重试3次，每次间隔2秒
- 服务器错误（500/503）：提示用户手动重试

---

## 数据同步说明

### 前端数据库字段映射

| 后端响应字段 | 前端数据库字段 | 说明 |
|------------|--------------|------|
| 接口 | 后端响应字段 | 前端数据库字段 | 说明 |
|------|------------|--------------|------|
| `/api/questions/analyze` | `id` | `Question.backendQuestionId` | 后端题目ID |
| `/api/questions/analyze` | `question_text` | `Question.backendQuestionText` | 完整题干（优先显示） |
| `/api/questions/{id}/detail` | `correct_answer` | `Question.correctAnswer` | 正确答案 |
| `/api/questions/{id}/detail` | `explanation` | `Question.explanation` | 解析 |
| - | - | `Question.answerLoaded` | 答案是否已加载（前端维护） |

### 数据更新策略

1. **批量处理时**（调用 `/api/questions/analyze`）：
   - 只更新 `backendQuestionId` 和 `backendQuestionText`
   - 设置 `answerLoaded = false`
   - 不更新答案相关字段
   - 不保存标签、知识点等分类信息

2. **详情页加载时**（调用 `/api/questions/{id}/detail`）：
   - 更新答案字段：`correctAnswer` 和 `explanation`
   - 设置 `answerLoaded = true`
   - 保存 `answer_versions` 到内存（不存数据库）
   - 保存 `tags`、`knowledge_points` 等到内存（用于显示，不存数据库）

---

## 测试建议

### 1. 正常流程测试

- ✅ 上传新题目，获取完整数据
- ✅ 上传重复题目，验证去重逻辑
- ✅ 强制重新分析（`force_reanalyze=true`）

### 2. 边界情况测试

- ✅ 图片文件不存在
- ✅ 图片格式不支持
- ✅ 图片文件过大（>10MB）
- ✅ 网络连接失败
- ✅ 服务器返回错误

### 3. 性能测试

- ✅ 批量处理100张图片
- ✅ 并发请求控制（最多3个）
- ✅ 队列处理性能

---

## 更新日志

### v1.0.0 (2025-12-05)
- 初始版本
- 支持题目分析和答案获取
- 支持缓存和去重
- 支持队列管理
- 支持笔记/评论功能（获取、创建、点赞、删除）

---

## 联系方式

如有问题，请联系后端开发团队。
