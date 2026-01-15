# 批量题目分析 API 规范

## POST /api/questions/analyze/batch

批量上传多个题目图片和相关数据，一次性获取多个题目的内容。

### 请求参数

**Content-Type: multipart/form-data**

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| questions | Array | ✅ | 题目数组，每个元素包含以下字段： |
| questions[].image | File | ✅ | 题目截图文件 |
| questions[].raw_text | String | ❌ | OCR识别的原始文本 |
| questions[].question_text | String | ❌ | 前端提取的题干 |
| questions[].options | String | ❌ | 前端提取的选项，JSON字符串格式 |
| questions[].question_type | String | ❌ | 题目类型，默认 "TEXT" |
| questions[].force_reanalyze | Boolean | ❌ | 是否强制重新AI分析 |

**注意**：由于 multipart/form-data 的限制，实际实现时可以使用以下格式：
- `images[]`: 多个图片文件
- `raw_texts[]`: 对应的原始文本数组（JSON字符串）
- `question_texts[]`: 对应的题干数组（JSON字符串）
- `options_array[]`: 对应的选项数组（JSON字符串数组的JSON字符串）
- `question_types[]`: 对应的题目类型数组（JSON字符串）
- `force_reanalyze`: 布尔值，统一应用到所有题目

或者使用 JSON 格式（推荐）：
- `questions`: JSON数组，每个元素包含图片的 base64 编码和元数据

### 响应格式

**Content-Type: application/json**

```json
{
  "results": [
    {
      "success": true,
      "question": {
        "id": "uuid-1234",
        "screenshot": "/uploads/2025/12/04/q1234.png",
        "raw_text": "某城市人口为A，增长率为…",
        "question_text": "完整题干",
        "question_type": "单选",
        "options": ["A. ...", "B. ..."],
        "from_cache": true,
        "is_duplicate": false,
        "saved_to_db": true
      },
      "error": null
    },
    {
      "success": false,
      "question": null,
      "error": {
        "code": 400,
        "message": "图片格式不支持"
      }
    }
  ],
  "total": 2,
  "success_count": 1,
  "failed_count": 1
}
```

### 业务逻辑

1. 接收批量题目列表
2. 对每个题目执行与单个接口相同的处理逻辑：
   - 图片哈希检查
   - 文本相似度检查
   - AI处理（如需要）
3. 返回每个题目的处理结果
4. 支持部分成功：即使某些题目处理失败，其他题目仍可成功返回

### 错误处理

- **400 Bad Request**：请求参数错误
- **500 Internal Server Error**：服务器内部错误
- 部分失败时，响应中会包含每个题目的成功/失败状态

### 注意事项

1. **批量大小限制**：建议单次请求不超过 20 个题目
2. **超时设置**：批量处理可能需要较长时间，建议超时时间设置为 60 秒
3. **并发控制**：后端应控制批量处理的并发数，避免过载
4. **错误处理**：部分题目失败不应影响其他题目的处理
