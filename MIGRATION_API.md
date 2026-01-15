# 数据迁移 API 接口文档

## 概述

数据迁移功能允许用户将数据（题目、笔记、记忆卡片及图片）从一台设备迁移到另一台设备。通过生成迁移码，用户可以在新设备上输入迁移码来导入所有数据。

**重要提示**：

- 迁移码有效期：**1天**

- 迁移码只能使用一次（获取数据后即被标记为已使用）

- 客户端下载完所有图片后，**必须调用确认接口**，服务器才会删除数据

---

## 1. 创建迁移码（导出数据）

### 接口信息

- **URL**: `/api/migration/create`

- **方法**: `POST`

- **Content-Type**: `application/json`

### 请求参数

```json
{
  "device_id": "193fadfa6ad72dd5",
  "data": {
    "questions": [
      {
        "id": "question-uuid-1",
        "imagePath": "/path/to/image.jpg",
        "originalImagePath": "/path/to/original.jpg",
        "cleanedImagePath": "/path/to/cleaned.jpg",
        "rawText": "题目原始文本",
        "questionText": "题目文本",
        "frontendRawText": "前端OCR文本",
        "options": "[\"A xxx\", \"B xxx\"]",
        "createdAt": 1704708000000,
        "reviewState": "unreviewed",
        "userNotes": "用户笔记",
        "confidence": 0.95,
        "questionType": "TEXT",
        "backendQuestionId": "backend-id-1",
        "backendQuestionText": "后端提取的题目",
        "answerLoaded": true,
        "correctAnswer": "A",
        "explanation": "解析内容",
        "tags": "[\"标签1\", \"标签2\"]"
      }
    ],
    "notes": [
      {
        "id": "note-uuid-1",
        "content": "笔记内容",
        "createdAt": 1704708000000,
        "updatedAt": 1704708000000,
        "tags": "[\"标签1\"]",
        "questionId": "question-uuid-1",
        "isFavorite": false
      }
    ],
    "flashcards": [
      {
        "id": "flashcard-uuid-1",
        "front": "正面",
        "back": "背面",
        "createdAt": 1704708000000,
        "updatedAt": 1704708000000,
        "tags": "[\"标签1\"]",
        "questionId": "question-uuid-1",
        "isFavorite": false,
        "reviewState": "unreviewed"
      }
    ]
  },
  "images": [
    {
      "question_id": "question-uuid-1",
      "image_type": "main",
      "image_base64": "iVBORw0KGgoAAAANSUhEUgAA..."
    },
    {
      "question_id": "question-uuid-1",
      "image_type": "original",
      "image_base64": "iVBORw0KGgoAAAANSUhEUgAA..."
    },
    {
      "question_id": "question-uuid-1",
      "image_type": "cleaned",
      "image_base64": "iVBORw0KGgoAAAANSUhEUgAA..."
    }
  ]
}
```

### 字段说明

#### 请求体字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `device_id` | string | 是 | 设备唯一标识 |
| `data` | object | 是 | 迁移数据对象 |
| `data.questions` | array | 是 | 题目数组（最多100道） |
| `data.notes` | array | 是 | 笔记数组 |
| `data.flashcards` | array | 是 | 记忆卡片数组 |
| `images` | array | 否 | 图片数组（最多300张） |

#### 图片对象字段

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `question_id` | string | 是 | 关联的题目ID |
| `image_type` | string | 是 | 图片类型：`"main"`（主图）、`"original"`（原图）、`"cleaned"`（擦写后的图） |
| `image_base64` | string | 是 | Base64编码的图片数据（JPEG格式，已压缩） |

**图片Base64格式**：

- 支持纯Base64字符串：`"iVBORw0KGgoAAAANSUhEUgAA..."`

- 支持data URI格式：`"data:image/jpeg;base64,iVBORw0KGgoAAAANSUhEUgAA..."`

### 响应（成功）

**状态码**: `200 OK`

```json
{
  "success": true,
  "message": "迁移码创建成功",
  "data": {
    "migration_code": "MIGR-ACKPJD-EGHTEM-QAWZ",
    "expires_at": "2026-01-09T14:22:41.867667+00:00",
    "data_size": 2969068,
    "image_count": 24
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 是否成功 |
| `message` | string | 消息 |
| `data.migration_code` | string | 迁移码（格式：`MIGR-XXXXXX-XXXXXX-XXX`） |
| `data.expires_at` | string | 过期时间（ISO 8601格式，UTC时区） |
| `data.data_size` | integer | 数据大小（字节） |
| `data.image_count` | integer | 图片数量 |

### 响应（失败）

**状态码**: `400 Bad Request` 或 `500 Internal Server Error`

```json
{
  "success": false,
  "error": "DATA_TOO_LARGE",
  "message": "数据过大（2.8MB），最多支持50MB",
  "data": null
}
```

### 错误码

| 错误码 | 状态码 | 说明 |
|--------|--------|------|
| `INVALID_REQUEST` | 400 | 请求数据无效（缺少必需字段） |
| `DATA_TOO_LARGE` | 400 | 数据过大（单次迁移不超过50MB） |
| `IMAGE_COUNT_EXCEEDED` | 400 | 图片数量过多（不超过300张） |
| `SERVER_ERROR` | 500 | 服务器错误 |

### 数据限制

- **单次迁移题目数量**：不超过100道

- **单次迁移总数据大小**：不超过50MB（JSON + 图片）

- **单次迁移图片数量**：不超过300张

- **图片大小限制**：单张图片不超过2MB（Base64编码前）

---

## 2. 获取迁移数据（导入数据）

### 接口信息

- **URL**: `/api/migration/retrieve`

- **方法**: `POST`

- **Content-Type**: `application/json`

### 请求参数

```json
{
  "migration_code": "MIGR-ACKPJD-EGHTEM-QAWZ",
  "device_id": "new_device_id"
}
```

### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `migration_code` | string | 是 | 迁移码 |
| `device_id` | string | 是 | 新设备的唯一标识 |

### 响应（成功）

**状态码**: `200 OK`

```json
{
  "success": true,
  "message": "迁移数据获取成功",
  "data": {
    "questions": [
      {
        "id": "question-uuid-1",
        "imagePath": "/path/to/image.jpg",
        "originalImagePath": "/path/to/original.jpg",
        "cleanedImagePath": "/path/to/cleaned.jpg",
        "rawText": "题目原始文本",
        "questionText": "题目文本",
        "frontendRawText": "前端OCR文本",
        "options": "[\"A xxx\", \"B xxx\"]",
        "createdAt": 1704708000000,
        "reviewState": "unreviewed",
        "userNotes": "用户笔记",
        "confidence": 0.95,
        "questionType": "TEXT",
        "backendQuestionId": "backend-id-1",
        "backendQuestionText": "后端提取的题目",
        "answerLoaded": true,
        "correctAnswer": "A",
        "explanation": "解析内容",
        "tags": "[\"标签1\", \"标签2\"]"
      }
    ],
    "notes": [
      {
        "id": "note-uuid-1",
        "content": "笔记内容",
        "createdAt": 1704708000000,
        "updatedAt": 1704708000000,
        "tags": "[\"标签1\"]",
        "questionId": "question-uuid-1",
        "isFavorite": false
      }
    ],
    "flashcards": [
      {
        "id": "flashcard-uuid-1",
        "front": "正面",
        "back": "背面",
        "createdAt": 1704708000000,
        "updatedAt": 1704708000000,
        "tags": "[\"标签1\"]",
        "questionId": "question-uuid-1",
        "isFavorite": false,
        "reviewState": "unreviewed"
      }
    ],
    "images": [
      {
        "question_id": "question-uuid-1",
        "image_type": "main",
        "image_url": "http://fupan.jnhongniang.xyz/migration/images/MIGR-ACKPJD-EGHTEM-QAWZ/main_question-uuid-1.jpg"
      },
      {
        "question_id": "question-uuid-1",
        "image_type": "original",
        "image_url": "http://fupan.jnhongniang.xyz/migration/images/MIGR-ACKPJD-EGHTEM-QAWZ/original_question-uuid-1.jpg"
      },
      {
        "question_id": "question-uuid-1",
        "image_type": "cleaned",
        "image_url": "http://fupan.jnhongniang.xyz/migration/images/MIGR-ACKPJD-EGHTEM-QAWZ/cleaned_question-uuid-1.jpg"
      }
    ],
    "created_at": "2026-01-08T13:58:49.238582",
    "expires_at": "2026-01-09T13:58:49.188540+00:00"
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `data.questions` | array | 题目数组（与创建时的格式相同） |
| `data.notes` | array | 笔记数组（与创建时的格式相同） |
| `data.flashcards` | array | 记忆卡片数组（与创建时的格式相同） |
| `data.images` | array | 图片信息数组 |
| `data.images[].question_id` | string | 关联的题目ID |
| `data.images[].image_type` | string | 图片类型：`"main"`、`"original"`、`"cleaned"` |
| `data.images[].image_url` | string | 图片下载URL（完整URL） |
| `data.created_at` | string | 迁移码创建时间（ISO 8601格式） |
| `data.expires_at` | string | 过期时间（ISO 8601格式，UTC时区） |

### 响应（失败）

**状态码**: `400 Bad Request` 或 `500 Internal Server Error`

```json
{
  "success": false,
  "error": "INVALID_CODE",
  "message": "迁移码无效或已过期",
  "data": null
}
```

### 错误码

| 错误码 | 状态码 | 说明 |
|--------|--------|------|
| `INVALID_REQUEST` | 400 | 请求数据无效（缺少必需字段） |
| `INVALID_CODE` | 400 | 迁移码不存在或格式错误 |
| `CODE_EXPIRED` | 400 | 迁移码已过期 |
| `SERVER_ERROR` | 500 | 服务器错误 |

### 重要提示

⚠️ **获取迁移数据后，迁移码会被标记为已使用，但数据不会立即删除**。客户端需要：

1. 下载所有图片

2. 调用确认接口（见下方）

3. 服务器收到确认后才会删除数据

---

## 3. 确认迁移完成（删除数据）

### 接口信息

- **URL**: `/api/migration/confirm`

- **方法**: `POST`

- **Content-Type**: `application/json`

### 请求参数

```json
{
  "migration_code": "MIGR-ACKPJD-EGHTEM-QAWZ",
  "device_id": "new_device_id"
}
```

### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `migration_code` | string | 是 | 迁移码 |
| `device_id` | string | 是 | 设备ID（必须与获取迁移数据时使用的device_id一致） |

### 响应（成功）

**状态码**: `200 OK`

```json
{
  "success": true,
  "message": "迁移数据已删除"
}
```

### 响应（失败）

**状态码**: `400 Bad Request` 或 `500 Internal Server Error`

```json
{
  "success": false,
  "error": "INVALID_CODE",
  "message": "迁移码不存在或已删除",
  "data": null
}
```

### 错误码

| 错误码 | 状态码 | 说明 |
|--------|--------|------|
| `INVALID_REQUEST` | 400 | 请求数据无效（缺少必需字段） |
| `INVALID_CODE` | 400 | 迁移码不存在或已删除 |
| `INVALID_REQUEST` | 400 | 设备ID不匹配（必须是使用该迁移码的设备） |
| `SERVER_ERROR` | 500 | 服务器错误 |

### 重要提示

✅ **客户端下载完所有图片后，必须调用此接口**，服务器才会删除数据。

如果客户端不调用此接口：

- 数据会保留1天

- 1天后由清理脚本自动删除

- 在此期间，图片仍然可以访问

---

## 完整流程示例

### 导出数据（原设备）

```kotlin
// 1. 准备数据
val questions = getLocalQuestions()
val notes = getLocalNotes()
val flashcards = getLocalFlashcards()
val images = getLocalImages() // 转换为Base64

// 2. 创建迁移码
val request = MigrationCreateRequest(
    device_id = deviceId,
    data = MigrationData(
        questions = questions,
        notes = notes,
        flashcards = flashcards
    ),
    images = images
)

val response = api.createMigrationCode(request)

// 3. 显示迁移码给用户
val migrationCode = response.data.migration_code
showMigrationCodeToUser(migrationCode)
```

### 导入数据（新设备）

```kotlin
// 1. 用户输入迁移码
val migrationCode = getUserInputMigrationCode()

// 2. 获取迁移数据
val migrationData = api.getMigrationData(
    migration_code = migrationCode,
    device_id = deviceId
)

// 3. 保存JSON数据到本地
saveQuestions(migrationData.data.questions)
saveNotes(migrationData.data.notes)
saveFlashcards(migrationData.data.flashcards)

// 4. 下载所有图片
val downloadTasks = migrationData.data.images.map { image ->
    downloadImage(
        url = image.image_url,
        questionId = image.question_id,
        imageType = image.image_type
    )
}

// 等待所有图片下载完成
awaitAll(downloadTasks)

// 5. 确认下载完成（重要！）
api.confirmMigrationComplete(
    migration_code = migrationCode,
    device_id = deviceId
)

// 6. 显示成功提示
showSuccessMessage("数据迁移完成！")
```

---

## 图片下载说明

### 图片URL格式

图片URL是完整的HTTP URL，格式如下：

```
http://fupan.jnhongniang.xyz/migration/images/{migration_code}/{image_type}_{question_id}.jpg
```

**示例**：

```
http://fupan.jnhongniang.xyz/migration/images/MIGR-ACKPJD-EGHTEM-QAWZ/main_2918cb18-8160-4013-a3ff-7b6d754a9233.jpg
```

### 图片类型

- `main`: 主图（题目截图）

- `original`: 原图（未处理）

- `cleaned`: 擦写后的图（已去除手写）

### 下载建议

1. **并发下载**：可以并发下载多张图片，提高速度

2. **错误重试**：如果下载失败，可以重试（数据在确认前不会删除）

3. **进度显示**：建议显示下载进度，提升用户体验

4. **下载完成确认**：所有图片下载完成后，**必须调用确认接口**

---

## 错误处理建议

### 1. 迁移码无效

```kotlin
if (error == "INVALID_CODE") {
    showError("迁移码无效，请检查是否正确")
}
```

### 2. 迁移码已过期

```kotlin
if (error == "CODE_EXPIRED") {
    showError("迁移码已过期（有效期1天），请重新生成")
}
```

### 3. 数据过大

```kotlin
if (error == "DATA_TOO_LARGE") {
    showError("数据过大，请减少题目数量或图片数量")
}
```

### 4. 图片下载失败

```kotlin
// 如果图片下载失败，可以重试
// 数据在确认前不会删除，可以多次尝试下载
if (downloadFailed) {
    retryDownload(imageUrl)
}
```

### 5. 确认接口调用失败

```kotlin
// 如果确认接口调用失败，可以重试
// 数据会保留1天，有足够时间重试
if (confirmFailed) {
    retryConfirm(migrationCode, deviceId)
}
```

---

## 注意事项

1. **迁移码格式**：`MIGR-XXXXXX-XXXXXX-XXX`（包含连字符）

2. **有效期**：迁移码有效期为1天，过期后无法使用

3. **一次性使用**：迁移码只能使用一次，获取数据后即被标记为已使用

4. **必须确认**：下载完图片后，必须调用确认接口，否则数据会保留1天

5. **设备ID验证**：确认接口会验证设备ID，确保是使用该迁移码的设备

6. **图片URL有效期**：图片URL在迁移码有效期内可用，确认删除后立即失效

---

## 测试建议

### 测试用例

1. **正常流程**：

   - 创建迁移码 → 获取数据 → 下载图片 → 确认完成

2. **错误处理**：

   - 无效迁移码

   - 过期迁移码

   - 数据过大

   - 图片下载失败

   - 确认接口调用失败

3. **边界情况**：

   - 空数据（无题目、无图片）

   - 最大数据量（100道题、300张图片）

   - 网络中断后重试

---

## 联系信息

如有问题，请联系后端开发人员。
