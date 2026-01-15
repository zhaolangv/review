# 图形推理题过滤和排除机制

## 📋 当前检测机制

### 检测特征

1. **网格布局检测** (权重: 0.3)
   - 检测水平和垂直方向的规律性边缘
   - 阈值: 0.2

2. **图案规律性检测** (权重: 0.25)
   - 检测重复图案（如棋盘格、条纹）
   - 阈值: 0.4

3. **高对比度检测** (权重: 0.2)
   - 检测黑白对比（图形题特征）
   - 阈值: 0.3

4. **选项标记检测** (权重: 0.25) ⭐ **新增**
   - 检测图片底部是否有A/B/C/D选项标记
   - 通过检测字母形状特征来判断

5. **问号标记检测** (权重: 0.2) ⭐ **新增**
   - 检测图片中间是否有问号标记（缺失部分）

### 判断条件

**更严格的判断：**
- 必须同时满足：
  1. 检测到网格布局 (`hasGrid = true`)
  2. 检测到强信号之一：
     - 选项标记 (`hasOptionMarkers = true`) **或**
     - 问号标记 (`hasQuestionMark = true`) **或**
     - 规律图案 (`hasPattern = true`)
  3. 总分 >= 0.4

这样可以减少误检，只识别真正的图形推理题。

## 🔧 如何排除误检

### 方案1：提高检测阈值（已实现）

当前阈值已经比较严格：
- 必须同时有网格 + 强信号
- 总分 >= 0.4

如果仍有误检，可以进一步提高阈值：

```kotlin
// 在 GraphicQuestionDetector.kt 中
private const val FINAL_SCORE_THRESHOLD = 0.5f  // 提高到0.5
```

### 方案2：添加人工确认机制

**实现方式：**
1. 检测到的图形题标记为"待确认"状态
2. 在UI中显示"待确认"标签
3. 用户可以手动确认或删除

**代码修改：**
```kotlin
// 在保存图形题时，添加待确认标记
val question = Question(
    imagePath = imagePath,
    rawText = "[图形推理题-待确认] ${graphicResult.reason}",
    questionText = "图形推理题（待确认）",
    options = "",
    confidence = graphicResult.confidence,
    reviewState = "pending_confirmation"  // 新增状态
)
```

### 方案3：添加排除规则

**可以排除的情况：**
1. **纯色图片**：如果图片主要是单一颜色，不是图形题
2. **照片类图片**：如果检测到照片特征（如人脸、风景），不是图形题
3. **UI界面**：如果检测到UI元素（如按钮、图标），不是图形题

**实现示例：**
```kotlin
// 排除纯色图片
if (isSolidColorImage(bitmap)) {
    return GraphicQuestionResult(false, 0f, reason = "纯色图片，不是图形题")
}

// 排除照片类图片（可以检测是否有渐变、纹理等照片特征）
if (isPhotoLikeImage(bitmap)) {
    return GraphicQuestionResult(false, 0f, reason = "照片类图片，不是图形题")
}
```

### 方案4：使用ML Kit Image Labeling

**使用Google ML Kit的图像标签功能：**
```kotlin
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

fun excludeNonGraphicImages(bitmap: Bitmap): Boolean {
    val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    val image = InputImage.fromBitmap(bitmap, 0)
    
    // 检测图片标签
    labeler.process(image).addOnSuccessListener { labels ->
        // 排除照片、UI界面等
        val excludeLabels = listOf("person", "face", "landscape", "building", "food", "animal")
        val hasExcludeLabel = labels.any { label ->
            excludeLabels.any { exclude -> 
                label.text.contains(exclude, ignoreCase = true)
            }
        }
        return hasExcludeLabel
    }
    return false
}
```

## 📝 推荐方案

### 当前最佳方案：严格检测 + 人工确认

1. **严格检测**（已实现）
   - 必须同时有网格 + 强信号
   - 总分 >= 0.4

2. **人工确认机制**（建议添加）
   - 检测到的图形题标记为"待确认"
   - 在UI中显示，用户可以确认或删除

3. **排除明显非图形题**（可选）
   - 使用ML Kit Image Labeling排除照片、UI等

## 🎯 快速实现人工确认

如果需要快速实现人工确认，可以：

1. **在Question实体中添加状态字段**（如果还没有）
2. **在保存图形题时标记为"待确认"**
3. **在UI中显示待确认的题目，让用户确认**

这样可以：
- 不丢失可能的图形题
- 让用户手动筛选
- 逐步提高检测准确率

## 📊 当前检测结果分析

从你的日志看：
- ✅ 检测到网格布局
- ❌ 未检测到规律图案（regularity=0.097，低于阈值0.4）
- ✅ 检测到高对比度
- ❓ 选项标记和问号标记检测结果未知（需要新日志）

**当前得分：0.7**（网格0.3 + 对比度0.2 = 0.5，但需要检查是否有选项标记或问号标记）

如果检测到选项标记或问号标记，得分会更高，更可能是图形推理题。

