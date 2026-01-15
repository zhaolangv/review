# 手写擦除 API 更新说明

## 更新概述

根据最新的 API 文档，已更新 Android 客户端的手写擦除功能实现，确保与后端 API 完全兼容。

---

## 主要更新内容

### 1. ✅ 添加 `device_id` 参数（必需）

**更新文件**：
- `QuestionApiService.kt` - 添加 `device_id` 参数到 API 接口
- `HandwritingRemovalService.kt` - 自动获取并传递设备ID

**实现**：
```kotlin
// 自动获取设备ID
val deviceId = VersionChecker(appContext).getDeviceId()

// 传递到API
val deviceIdBody = deviceId.toRequestBody("text/plain".toMediaTypeOrNull())
ApiClient.questionApiService.removeHandwriting(
    image = imagePart,
    deviceId = deviceIdBody,  // 新增：必需参数
    saveToServer = null
)
```

### 2. ✅ 完善响应数据模型

**更新文件**：
- `HandwritingRemovalResponse.kt` - 添加配额相关字段

**新增字段**：
- `imageFormat`: 图片格式（"jpeg" 或 "png"）
- `remainingQuota`: 剩余使用次数配额（Pro功能）
- `usedCount`: 本月已使用次数（Pro功能）
- `monthlyQuota`: 每月总配额（Pro功能）

### 3. ✅ 增强错误处理

**更新文件**：
- `HandwritingRemovalService.kt` - 添加 `HandwritingRemovalException` 异常类
- 所有调用处 - 正确处理 Pro 相关错误

**处理的错误码**：
- `NOT_PRO_USER` (403) - 用户不是Pro用户
- `PRO_EXPIRED` (403) - Pro服务已过期
- `QUOTA_EXCEEDED` (403) - 配额已用完
- `INVALID_REQUEST` (400) - 请求参数错误
- `SERVER_ERROR` (500) - 服务器错误

**错误处理示例**：
```kotlin
try {
    val processedBitmap = HandwritingRemovalService.removeHandwriting(bitmap)
    // 处理成功
} catch (e: HandwritingRemovalService.HandwritingRemovalException) {
    // 显示用户友好的错误消息
    Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
}
```

### 4. ✅ 改用 Retrofit（统一API调用方式）

**更新文件**：
- `HandwritingRemovalService.kt` - 从 OkHttp 改为使用 Retrofit

**优势**：
- 与项目其他API调用方式一致
- 自动处理JSON序列化/反序列化
- 更好的类型安全

### 5. ✅ 更新所有调用处

**更新的文件**：
- `QuestionCardAdapter.kt` - 单个题目手写擦除
- `CameraCaptureActivity.kt` - 拍照时手写擦除
- `QuestionsFragment.kt` - 批量手写擦除

**改进**：
- 所有调用处都正确处理 `HandwritingRemovalException`
- 显示用户友好的错误消息
- 记录详细的日志信息

---

## API 接口规范

### 请求格式

```
POST /api/handwriting/remove
Content-Type: multipart/form-data
```

**必需参数**：
- `image`: 图片文件（File）
- `device_id`: 设备ID（String）

**可选参数**：
- `save_to_server`: 是否保存到服务器（"true"/"false"，默认"false"）

### 响应格式

**成功响应** (200 OK)：
```json
{
  "success": true,
  "data": {
    "image_url": "uploads/abc123_cleaned.jpg",
    "image_base64": "...",
    "image_data_url": "data:image/jpeg;base64,...",
    "image_format": "jpeg",
    "filename": "abc123_cleaned.jpg",
    "provider": "youdao",
    "remaining_quota": 50,
    "used_count": 10,
    "monthly_quota": 60
  }
}
```

**错误响应** (400/403/500)：
```json
{
  "success": false,
  "error": "NOT_PRO_USER",
  "message": "您还不是Pro用户，无法使用此功能"
}
```

---

## 使用示例

### 基本使用

```kotlin
// 1. 初始化服务（在Application或Activity中）
HandwritingRemovalService.init(context)

// 2. 调用手写擦除
lifecycleScope.launch {
    try {
        val processedBitmap = withContext(Dispatchers.IO) {
            HandwritingRemovalService.removeHandwriting(bitmap)
        }
        
        // 处理成功
        imageView.setImageBitmap(processedBitmap)
        Toast.makeText(context, "手写擦除完成", Toast.LENGTH_SHORT).show()
        
    } catch (e: HandwritingRemovalService.HandwritingRemovalException) {
        // 处理Pro相关错误
        when (e.errorCode) {
            "NOT_PRO_USER" -> {
                // 引导用户激活Pro
                startActivity(Intent(context, RedemptionCodeActivity::class.java))
            }
            "PRO_EXPIRED" -> {
                // 提示用户续费
                Toast.makeText(context, "Pro服务已过期，请续费", Toast.LENGTH_LONG).show()
            }
            "QUOTA_EXCEEDED" -> {
                // 提示用户配额已用完
                Toast.makeText(context, "本月使用次数已达上限", Toast.LENGTH_LONG).show()
            }
            else -> {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
        }
    } catch (e: Exception) {
        // 处理其他错误
        Toast.makeText(context, "手写擦除失败: ${e.message}", Toast.LENGTH_LONG).show()
    }
}
```

---

## 配额信息显示（可选）

如果需要在UI中显示配额信息，可以从响应中获取：

```kotlin
// 注意：当前实现中，配额信息在响应中，但需要修改服务以返回完整响应
// 或者可以在成功回调中显示配额信息
```

---

## 测试建议

### 测试用例

1. **正常使用（Pro用户）**：
   - 验证设备ID正确传递
   - 验证图片成功处理
   - 验证配额信息正确显示

2. **非Pro用户**：
   - 验证返回 `NOT_PRO_USER` 错误
   - 验证错误消息正确显示

3. **Pro过期**：
   - 验证返回 `PRO_EXPIRED` 错误
   - 验证错误消息正确显示

4. **配额用完**：
   - 验证返回 `QUOTA_EXCEEDED` 错误
   - 验证错误消息正确显示

5. **网络错误**：
   - 验证网络异常正确处理
   - 验证用户友好的错误提示

---

## 注意事项

1. **设备ID获取**：
   - 使用 `VersionChecker.getDeviceId()` 获取设备ID
   - 设备ID在应用生命周期内保持不变

2. **错误处理**：
   - 所有调用处都应该捕获 `HandwritingRemovalException`
   - 根据错误码显示相应的用户提示

3. **配额管理**：
   - 配额信息在每次成功调用后更新
   - 可以在UI中显示剩余配额（可选）

4. **图片格式**：
   - 支持多种图片格式（png, jpg, jpeg, gif, bmp）
   - 返回的图片格式可能与原图不同

---

## 相关文件

- `app/src/main/java/com/gongkao/cuotifupan/api/QuestionApiService.kt`
- `app/src/main/java/com/gongkao/cuotifupan/api/HandwritingRemovalResponse.kt`
- `app/src/main/java/com/gongkao/cuotifupan/api/HandwritingRemovalService.kt`
- `app/src/main/java/com/gongkao/cuotifupan/ui/QuestionCardAdapter.kt`
- `app/src/main/java/com/gongkao/cuotifupan/ui/CameraCaptureActivity.kt`
- `app/src/main/java/com/gongkao/cuotifupan/ui/QuestionsFragment.kt`

---

**更新完成！** ✅

所有更改已与最新的 API 文档保持一致，确保客户端可以正确调用后端接口并处理所有错误情况。

