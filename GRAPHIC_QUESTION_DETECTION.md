# 图形推理题识别方案

## 📋 问题分析

### 当前系统的局限性

1. **OCR只能识别文字**
   - ML Kit OCR只能识别图片中的文字
   - 对于纯图形、图案、推理题等没有文字的内容，OCR无法识别
   - 图形推理题（如Raven's Progressive Matrices）主要是黑白圆圈图案，没有文字

2. **题目检测器依赖OCR结果**
   - `QuestionDetector` 基于OCR识别的文本进行特征提取
   - 如果OCR结果为空，无法进行题目检测

### 图形推理题的特点

- **视觉特征：**
  - 通常有规律的网格布局（如3x3、4x4）
  - 包含重复的图形模式（圆圈、方块等）
  - 有选项标记（A、B、C、D）
  - 可能有问号标记缺失部分

- **布局特征：**
  - 主题目区域（网格）
  - 选项区域（通常在下方）
  - 选项标记（A、B、C、D）

## 🎯 解决方案

### 方案1：图像分类识别题目类型（推荐）

**思路：** 使用图像分类模型识别图片是否为题目，以及题目类型（文字题 vs 图形题）

**实现步骤：**

1. **使用ML Kit Image Labeling**
   - 识别图片中的对象和场景
   - 检测是否有"网格"、"图案"、"图表"等标签

2. **使用自定义分类模型**
   - 训练或使用预训练模型识别题目类型
   - 输入：图片
   - 输出：题目类型（文字题/图形题/非题目）

### 方案2：计算机视觉检测图形模式

**思路：** 使用计算机视觉技术检测图形推理题的特征

**检测特征：**
- 网格布局检测（3x3、4x4等）
- 重复图案检测（圆圈、方块等）
- 选项标记检测（A、B、C、D）
- 问号标记检测

### 方案3：混合检测（OCR + 图像分析）

**思路：** 结合OCR和图像分析

**流程：**
1. 先尝试OCR识别
2. 如果OCR结果为空，进行图像分析
3. 检测图形模式特征
4. 如果检测到图形题特征，标记为"图形推理题"

## 💡 推荐实现：方案3（混合检测）

### 实现步骤

#### 1. 创建图形题检测器

```kotlin
class GraphicQuestionDetector {
    /**
     * 检测是否为图形推理题
     */
    fun detectGraphicQuestion(bitmap: Bitmap): GraphicQuestionResult {
        // 1. 检测网格布局
        val hasGrid = detectGridLayout(bitmap)
        
        // 2. 检测重复图案
        val hasPattern = detectRepeatedPattern(bitmap)
        
        // 3. 检测选项标记（A、B、C、D）
        val hasOptions = detectOptionMarkers(bitmap)
        
        // 4. 综合判断
        val isGraphicQuestion = hasGrid && (hasPattern || hasOptions)
        
        return GraphicQuestionResult(
            isGraphicQuestion = isGraphicQuestion,
            hasGrid = hasGrid,
            hasPattern = hasPattern,
            hasOptions = hasOptions
        )
    }
}
```

#### 2. 修改ImageMonitorService

在OCR结果为空时，进行图形题检测：

```kotlin
// 如果OCR结果为空，尝试检测图形题
if (ocrResult.rawText.isBlank()) {
    val graphicDetector = GraphicQuestionDetector()
    val graphicResult = graphicDetector.detectGraphicQuestion(bitmap)
    
    if (graphicResult.isGraphicQuestion) {
        // 保存为图形推理题
        val question = Question(
            imagePath = imagePath,
            rawText = "[图形推理题]",
            questionText = "图形推理题（需要人工识别）",
            options = emptyList(),
            confidence = 0.7f,
            questionType = "graphic"
        )
        // 保存到数据库...
    }
}
```

#### 3. 使用ML Kit Image Labeling（简单方案）

```kotlin
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions

fun detectGraphicQuestionWithMLKit(bitmap: Bitmap): Boolean {
    val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
    val image = InputImage.fromBitmap(bitmap, 0)
    
    labeler.process(image).addOnSuccessListener { labels ->
        // 检查是否有相关标签
        val hasGraphicLabels = labels.any { label ->
            label.text.contains("grid", ignoreCase = true) ||
            label.text.contains("pattern", ignoreCase = true) ||
            label.text.contains("chart", ignoreCase = true) ||
            label.text.contains("diagram", ignoreCase = true)
        }
        return hasGraphicLabels
    }
    return false
}
```

## 🔧 快速实现：使用图像特征检测

### 简化版实现

检测以下特征：
1. **图像复杂度**：图形题通常有规律的图案
2. **颜色分布**：黑白对比明显
3. **边缘检测**：有规律的网格边缘
4. **选项标记检测**：在图片底部检测A、B、C、D

### 代码示例

```kotlin
class SimpleGraphicQuestionDetector {
    fun isGraphicQuestion(bitmap: Bitmap): Boolean {
        // 1. 检查图像复杂度（图形题通常有规律的图案）
        val complexity = calculateImageComplexity(bitmap)
        
        // 2. 检查颜色分布（图形题通常是黑白对比）
        val colorDistribution = analyzeColorDistribution(bitmap)
        
        // 3. 检查是否有网格特征
        val hasGrid = detectGridFeatures(bitmap)
        
        // 4. 检查底部是否有选项标记区域
        val hasOptionArea = detectOptionArea(bitmap)
        
        return (hasGrid || hasOptionArea) && complexity > threshold
    }
}
```

## 📝 建议的实现顺序

1. **第一步：简单标记**
   - 当OCR结果为空时，标记为"可能的图形题"
   - 保存图片，让用户手动确认

2. **第二步：使用ML Kit Image Labeling**
   - 集成ML Kit的图像标签功能
   - 检测是否有"网格"、"图表"等标签

3. **第三步：自定义检测算法**
   - 实现网格检测
   - 实现选项标记检测
   - 实现图案识别

## 🎯 当前可用的快速方案

**最简单的实现：**
- 当OCR结果为空时，检查图片特征
- 如果图片有规律的结构（如网格），标记为"可能的图形推理题"
- 保存图片，让用户手动确认和分类

这样可以：
1. 不丢失图形题图片
2. 让用户知道这是图形题
3. 后续可以手动处理或使用更高级的算法

