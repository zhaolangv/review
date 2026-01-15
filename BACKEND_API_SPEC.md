# 后端 API 规范文档

## 接口说明

### POST /api/questions/analyze

上传题目图片和相关数据，获取详细解析和多个来源的答案。

#### 请求参数

**Content-Type: multipart/form-data**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| image | File | ✅ | 题目截图文件（jpg/png/gif/bmp，建议不超过10MB） |
| raw_text | String | ❌ | 前端OCR识别的原始文本（所有文字），如果提供可加速处理 |
| question_text | String | ❌ | 前端提取的题干（可能不完整或不准确），如果提供可加速处理 |
| options | String/Array | ❌ | 前端提取的选项，JSON字符串格式：`["A. 选项A", "B. 选项B", ...]` |
| question_type | String | ❌ | 题目类型，默认 `"TEXT"`（文字题）或 `"GRAPHIC"`（图推题） |
| force_reanalyze | Boolean | ❌ | 是否强制重新AI分析，默认 `false`。当 `true` 时，即使发现重复题也会调用AI重新分析，并更新数据库 |

#### 响应格式

**Content-Type: application/json**

```json
{
  "id": "uuid-1234",
  "screenshot": "/uploads/2025/12/04/q1234.png",
  "raw_text": "某城市人口为A，增长率为…（OCR 文本）",
  "question_text": "完整题干（AI提取或已有数据）",
  "question_type": "单选",
  "options": ["A. ...", "B. ...", "C. ...", "D. ..."],
  
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
  "ocr_confidence": 0.92,
  "similar_questions": ["uuid-1223", "uuid-1209"],
  "priority": "中",
  "created_at": "2025-12-04",
  "updated_at": "2025-12-04",
  
  "from_cache": true,
  "is_duplicate": true,
  "saved_to_db": false,
  "similarity_score": 0.92,
  "matched_question_id": "uuid-1223"
}
```

#### 业务逻辑

1. **接收请求**：验证图片文件格式和大小
2. **图片哈希检查**（如果 `force_reanalyze=false`）：
   - 计算图片感知哈希（pHash）
   - 查询数据库，如果找到完全匹配（哈希相同），直接返回，设置 `from_cache=true`, `is_duplicate=true`, `saved_to_db=false`
3. **文本相似度检查**（如果图片哈希不匹配且 `force_reanalyze=false`）：
   - 使用 `raw_text` 或后端OCR结果计算文本指纹
   - 查询数据库，如果相似度 > 85%，返回匹配的题目，设置 `from_cache=true`, `is_duplicate=true`, `saved_to_db=false`, `similarity_score=相似度`, `matched_question_id=匹配的题目ID`
4. **AI处理**（如果未找到匹配或 `force_reanalyze=true`）：
   - 如果前端未提供OCR结果，后端自行进行OCR识别
   - 调用AI提取完整题干、选项、生成答案和解析
   - 如果 `is_duplicate=true` 且 `force_reanalyze=true`：更新数据库中的题目和答案版本
   - 如果 `is_duplicate=false`：保存新题目到数据库
   - 设置 `from_cache=false`, `saved_to_db=true`
5. **数据整合**：
   - 根据 `is_user_preferred` 或 `confidence` 确定 `correct_answer`
   - 合并多个解析生成最终的 `explanation`
   - 提取标签和知识点
6. **返回响应**：返回完整的题目详情数据

#### 响应字段说明

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `question_text` | String | 完整题干（AI提取或已有数据），优先于前端提供的题干 |
| `from_cache` | Boolean | `true`表示来自缓存（快速返回，未调用AI），`false`表示调用了AI |
| `is_duplicate` | Boolean | `true`表示是重复题（数据库中已存在），`false`表示新题 |
| `saved_to_db` | Boolean | `true`表示新存入数据库或更新了数据库，`false`表示仅读取已有数据 |
| `similarity_score` | Number | 相似度分数（0-1），仅在`is_duplicate=true`时返回，`null`表示新题 |
| `matched_question_id` | String | 匹配到的题目ID，仅在`is_duplicate=true`时返回，用于前端关联显示 |

#### 特殊情况处理

- **`force_reanalyze=true` 且 `is_duplicate=true`**：
  - 强制调用AI重新分析
  - 更新数据库中的已有题目（添加新的答案版本或更新现有答案）
  - 返回：`from_cache=false`, `is_duplicate=true`, `saved_to_db=true`, `similarity_score=相似度`, `matched_question_id=匹配的题目ID`

- **前端未提供OCR结果**：
  - 后端自行进行OCR识别
  - 处理时间可能稍长

#### 错误处理

- **400 Bad Request**：请求参数错误
- **500 Internal Server Error**：服务器内部错误
- 响应格式：
```json
{
  "error": "错误信息",
  "code": 400
}
```

#### 错误处理

- **400 Bad Request**：请求参数错误（如图片格式不支持、文件过大）
- **500 Internal Server Error**：服务器内部错误（如AI调用失败）
- **503 Service Unavailable**：服务暂时不可用（如AI服务限流）

响应格式：
```json
{
  "error": "错误信息",
  "code": 400,
  "details": "详细错误说明（可选）"
}
```

#### 注意事项

1. **图片大小限制**：建议限制单张图片不超过 10MB
2. **超时设置**：
   - 图片哈希检查：< 100ms
   - 文本相似度检查：< 500ms
   - AI调用：< 30秒
   - 总超时：< 35秒
3. **缓存策略**：
   - 使用Redis缓存热门题目的结果（按题目ID）
   - 缓存时间建议：24小时
4. **并发控制**：
   - AI调用限制并发数（如最多同时5个请求）
   - 超过限制时返回 `503` 或排队处理
5. **数据去重**：
   - 使用图片感知哈希（pHash）进行快速匹配
   - 使用文本相似度算法（编辑距离或关键词匹配）进行二次匹配
   - 相似度阈值建议：85%

## 笔记/评论相关 API（可选）

### GET /api/questions/{question_id}/notes

获取题目的所有公开笔记/评论。

#### 响应格式
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
      "is_liked": false,  // 当前用户是否点赞
      "replies_count": 2,  // 回复数量
      "created_at": "2025-12-04T10:00:00Z",
      "updated_at": "2025-12-04T10:00:00Z"
    }
  ],
  "total": 10
}
```

### POST /api/questions/{question_id}/notes

创建笔记/评论。

#### 请求参数
```json
{
  "content": "这道题的陷阱在于...",
  "note_type": "insight",  // "note"、"comment"、"insight"
  "parent_id": null  // 如果是回复，传入父评论ID
}
```

### POST /api/notes/{note_id}/like

点赞/取消点赞笔记。

### DELETE /api/notes/{note_id}

删除笔记（软删除）。

## AI 提示词建议

### 用于生成答案和解析的提示词模板

```
你是一位专业的公考行测题目解析专家。请根据以下题目信息，提供详细的解析和答案。

题目类型：{question_type}
题干：{question_text}
选项：
{options}

请按照以下格式回答：
1. 正确答案：[选项]
2. 解析：[详细解析过程，包括解题思路、关键步骤、易错点等]
3. 知识点：[相关知识点]
4. 难度评估：[1-5，1最简单，5最难]
5. 解题时间建议：[建议用时，单位：秒]

注意：
- 解析要详细、清晰，便于理解
- 如果题目有陷阱，请明确指出
- 如果涉及计算，请给出详细步骤
```

### 用于提取标签和知识点的提示词模板

```
请根据以下题目内容，提取相关的标签和知识点：

题目：{question_text}
选项：{options}

请返回 JSON 格式：
{
  "tags": ["标签1", "标签2"],
  "knowledge_points": ["知识点1", "知识点2"]
}

标签应该包括：
- 题型分类（如：数量关系、言语理解、判断推理等）
- 具体类型（如：比例、增长率、逻辑填空等）

知识点应该包括：
- 涉及的核心概念
- 解题方法
- 相关公式或规律
```

## 数据库设计建议

### 核心表结构

#### users 表（用户表）
- id (UUID, Primary Key)
- username (String, Unique) // 用户名或昵称
- email (String, Unique, Optional) // 邮箱（可选，用于登录）
- avatar (String, Optional) // 头像URL
- created_at (DateTime)
- updated_at (DateTime)

**说明**：
- 如果应用是单用户，可以简化或省略此表
- 如果支持多用户，此表是必需的

#### questions 表（题目表）
- id (UUID, Primary Key)
- screenshot (String, 图片路径)
- raw_text (Text)
- question_text (Text)
- question_type (String) // "TEXT" 或 "GRAPHIC"
- options (JSON)
- correct_answer (String)
- explanation (Text)
- tags (JSON)
- knowledge_points (JSON)
- source (String)
- source_url (String)
- created_at (DateTime)
- updated_at (DateTime)

**说明**：
- 移除了 `user_note` 字段，因为笔记将存储在单独的表中
- 题目是共享的，不归属于特定用户

#### answer_versions 表（答案版本表）
- id (UUID, Primary Key)
- question_id (UUID, Foreign Key → questions.id)
- source_name (String) // "粉笔"、"华图"、"AI" 等
- source_type (String) // "机构" 或 "AI"
- answer (String)
- explanation (Text)
- confidence (Float)
- is_user_preferred (Boolean) // 用户偏好（需要关联用户）
- user_id (UUID, Foreign Key → users.id, Optional) // 如果支持多用户偏好
- created_at (DateTime)
- updated_at (DateTime)

**索引**：
- question_id (Index)
- user_id (Index, Optional)

#### question_notes 表（题目笔记/评论表）⭐ **重要**
- id (UUID, Primary Key)
- question_id (UUID, Foreign Key → questions.id)
- user_id (UUID, Foreign Key → users.id)
- content (Text) // 笔记/评论内容
- note_type (String) // "note"（笔记）、"comment"（评论）、"insight"（心得）
- parent_id (UUID, Foreign Key → question_notes.id, Nullable) // 回复的父评论ID（支持嵌套回复）
- likes_count (Integer, Default: 0) // 点赞数
- is_pinned (Boolean, Default: false) // 是否置顶
- is_deleted (Boolean, Default: false) // 软删除标记
- created_at (DateTime)
- updated_at (DateTime)

**索引**：
- question_id (Index)
- user_id (Index)
- parent_id (Index)
- created_at (Index, Desc) // 用于按时间排序

**说明**：
- **必须单独一个表**：因为一道题可以有多个用户的多个笔记
- **支持嵌套回复**：通过 `parent_id` 实现评论的回复功能
- **软删除**：使用 `is_deleted` 标记，保留数据用于统计

#### note_likes 表（笔记点赞表）
- id (UUID, Primary Key)
- note_id (UUID, Foreign Key → question_notes.id)
- user_id (UUID, Foreign Key → users.id)
- created_at (DateTime)

**唯一约束**：
- (note_id, user_id) // 一个用户只能给一条笔记点一次赞

**索引**：
- note_id (Index)
- user_id (Index)

#### question_similarity 表（题目相似度表，用于去重）
- id (UUID, Primary Key)
- question_hash (String, Index) // 题目文本的哈希值
- question_id (UUID, Foreign Key → questions.id)
- created_at (DateTime)

#### user_question_progress 表（用户题目进度表）
- id (UUID, Primary Key)
- user_id (UUID, Foreign Key → users.id)
- question_id (UUID, Foreign Key → questions.id)
- review_state (String) // "unreviewed"、"mastered"、"not_mastered"
- user_note (Text, Optional) // 用户的个人笔记（区别于公开的 question_notes）
- is_error (Boolean, Default: false)
- error_reason (String, Optional)
- difficulty (Integer, Optional) // 用户认为的难度
- time_spent (Integer, Optional) // 用时（秒）
- attempts (Integer, Default: 0) // 尝试次数
- last_reviewed (DateTime, Optional)
- spaced_repetition (JSON, Optional) // 间隔重复数据
- created_at (DateTime)
- updated_at (DateTime)

**唯一约束**：
- (user_id, question_id) // 一个用户对一道题只有一条进度记录

**索引**：
- user_id (Index)
- question_id (Index)
- review_state (Index)

**说明**：
- 此表存储用户对题目的个人学习进度
- `user_note` 是用户的私人笔记，不会公开
- `question_notes` 是公开的笔记/评论，所有用户可见

### 表关系图

```
users (用户)
  ├── question_notes (笔记/评论) [1:N]
  ├── note_likes (点赞) [1:N]
  ├── user_question_progress (学习进度) [1:N]
  └── answer_versions.is_user_preferred (答案偏好) [1:N]

questions (题目)
  ├── answer_versions (答案版本) [1:N]
  ├── question_notes (笔记/评论) [1:N]
  ├── user_question_progress (用户进度) [1:N]
  └── question_similarity (相似度) [1:N]

question_notes (笔记/评论)
  ├── parent_id → question_notes (父评论) [N:1, 自关联]
  └── note_likes (点赞) [1:N]
```

### 设计说明

#### 1. 为什么需要单独的笔记表？

**优点**：
- ✅ **支持多用户**：一道题可以有多个用户的多个笔记
- ✅ **支持互动**：用户可以评论、回复、点赞
- ✅ **数据分离**：题目数据和用户笔记数据分离，便于管理
- ✅ **扩展性强**：可以添加更多功能（置顶、精选、举报等）
- ✅ **查询效率**：可以单独查询某道题的所有笔记，不需要加载整个题目数据

**如果放在 questions 表中**：
- ❌ 只能存储一个笔记（或需要 JSON 数组，查询不便）
- ❌ 无法支持多用户
- ❌ 无法支持回复、点赞等互动功能
- ❌ 数据耦合度高，难以扩展

#### 2. 表之间的关联关系

**必需的外键关系**：
- `question_notes.question_id` → `questions.id` （必需）
- `question_notes.user_id` → `users.id` （必需，如果支持多用户）
- `answer_versions.question_id` → `questions.id` （必需）
- `user_question_progress.question_id` → `questions.id` （必需）
- `user_question_progress.user_id` → `users.id` （必需，如果支持多用户）

**可选的关系**：
- `question_notes.parent_id` → `question_notes.id` （自关联，支持嵌套回复）
- `note_likes.note_id` → `question_notes.id` （支持点赞功能）

#### 3. 单用户 vs 多用户

**如果应用是单用户的**：
- 可以省略 `users` 表
- `question_notes.user_id` 可以为空或使用固定值
- `user_question_progress` 可以合并到 `questions` 表中（但建议保留，便于后续扩展）

**如果应用是多用户的**：
- 必须保留 `users` 表
- 所有外键关系都需要建立
- 需要考虑权限控制（用户只能编辑自己的笔记）

### 查询示例

#### 获取题目的所有公开笔记（按时间倒序）
```sql
SELECT n.*, u.username, u.avatar
FROM question_notes n
JOIN users u ON n.user_id = u.id
WHERE n.question_id = ? 
  AND n.is_deleted = false
  AND n.parent_id IS NULL  -- 只获取顶级评论
ORDER BY n.is_pinned DESC, n.created_at DESC
```

#### 获取笔记的回复（嵌套结构）
```sql
SELECT n.*, u.username, u.avatar
FROM question_notes n
JOIN users u ON n.user_id = u.id
WHERE n.parent_id = ?
  AND n.is_deleted = false
ORDER BY n.created_at ASC
```

#### 获取用户对题目的个人进度
```sql
SELECT *
FROM user_question_progress
WHERE user_id = ? AND question_id = ?
```

