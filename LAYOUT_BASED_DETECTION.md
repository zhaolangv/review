# 基于布局特征的题目识别改进

## 📋 问题描述

部分题目图片中，部分内容被遮挡（如红色/绿色笔触、标记等），导致：
1. 选项标记（A/B/C/D）可能识别不出来
2. 文字内容可能识别不完整
3. 但布局结构（对齐、间距、位置）仍然存在

## ✅ 解决方案

### 核心思路

**从依赖文字内容识别，转向依赖布局结构识别**

即使部分遮挡，题目的布局特征仍然存在：
- 选项之间的垂直间距相似
- 选项左对齐
- 选项在图片下半部分
- 题目在上半部分，选项在下半部分，有明显分离

### 已实现的改进

#### 1. 增强布局替代检测器（`LayoutAlternativeDetector`）

**改进前：**
- 要求下半部分≥3个短行
- 对齐分数≥0.7
- 短行长度<20字符

**改进后：**
- 要求下半部分≥2个短行（降低要求）
- 对齐分数≥0.6（放宽要求）
- 短行长度<30字符（放宽限制，部分遮挡时可能识别不完整）
- 添加间距一致性检测
- 综合评分：对齐分数(60%) + 间距一致性(40%)

#### 2. 增强打分器（`ScoreCalculator`）

**改进前：**
- 没有至少2个选项标记时，所有特征都不给分（`baseFeatureWeight = 0.0`）

**改进后：**
- 检查是否有强布局特征：
  - 短行簇数量≥3
  - 选项左对齐分数>0.7
  - 选项间距一致性>0.7
  - 题目与选项位置分离度>0.7
- 如果布局特征很强，即使选项标记<2，也给50%权重（`baseFeatureWeight = 0.5`）

#### 3. 优化决策逻辑

**改进前：**
- 没有选项标记时，需要90分以上才能auto_add，60分以上才能confirm

**改进后：**
- 没有选项标记但有强布局特征时：
  - 50分以上可以auto_add（从90降低到50）
  - 30分以上可以confirm（从60降低到30）
- 没有选项标记且没有强布局特征时：
  - 仍然需要90分以上才能auto_add
  - 仍然需要60分以上才能confirm

### 布局特征说明

#### 1. 短行簇检测
- 检测下半部分是否有≥3个短行（长度<20字符）
- 这些短行通常是对齐的选项

#### 2. 选项左对齐度
- 检测包含选项标记的行的左边界是否对齐
- 即使选项标记被遮挡，选项内容的左边界仍然对齐

#### 3. 选项间距一致性
- 检测选项之间的垂直间距是否相似
- 即使部分遮挡，间距仍然一致

#### 4. 题目与选项位置分离度
- 检测题目（上半部分）和选项（下半部分）是否有明显分离
- 即使部分遮挡，位置分离仍然明显

## 🔧 技术细节

### 布局替代检测算法

```kotlin
// 1. 获取下半部分的短行（放宽长度限制）
val bottomHalfLines = textBlocks
    .filter { it.boundingBox.top >= midY }
    .flatMap { it.lines }
    .filter { 
        val trimmed = it.text.trim()
        trimmed.length < 30 && trimmed.length >= 2  // 放宽到30字符
    }

// 2. 检查X坐标对齐（放宽对齐要求）
val alignmentScore = if (avgX > 0) {
    max(0.0, 1.0 - (maxDeviation / avgX) / 0.4)  // 从0.3放宽到0.4
} else {
    0.0
}

// 3. 检查垂直间距一致性
val spacingConsistency = if (spacings.size >= 2) {
    max(0.0, 1.0 - (stdDev / avgSpacing) / 0.3)
} else {
    0.0
}

// 4. 综合评分
val combinedScore = alignmentScore * 0.6 + spacingConsistency * 0.4

// 5. 判断（放宽条件）
val isValid = bottomHalfLines.size >= 2 && combinedScore >= 0.6
```

### 强布局特征判断

```kotlin
val hasStrongLayoutFeatures = 
    features.shortLineClusterCount >= 3 || 
    features.optionLeftAlignmentScore > 0.7 ||
    features.optionSpacingConsistency > 0.7 ||
    features.questionOptionSeparation > 0.7
```

## 📊 效果

### 预期效果

1. **部分遮挡的题目也能识别**
   - 即使选项标记被遮挡，也能通过布局特征识别
   - 即使文字识别不完整，也能通过布局结构识别

2. **降低误识别率**
   - 仍然要求至少2个选项标记或强布局特征
   - 没有强布局特征时，仍然需要高分数

3. **提高识别准确率**
   - 对部分遮挡的题目更鲁棒
   - 对文字识别不完整的题目更鲁棒

## 🎯 使用建议

### 如果识别效果不理想

1. **调整布局特征阈值**
   ```kotlin
   // 在 ScoreCalculator 中
   val hasStrongLayoutFeatures = 
       features.shortLineClusterCount >= 2 ||  // 从3降低到2
       features.optionLeftAlignmentScore > 0.6 ||  // 从0.7降低到0.6
       features.optionSpacingConsistency > 0.6 ||
       features.questionOptionSeparation > 0.6
   ```

2. **调整布局替代检测阈值**
   ```kotlin
   // 在 LayoutAlternativeDetector 中
   val isValid = bottomHalfLines.size >= 2 && combinedScore >= 0.5  // 从0.6降低到0.5
   ```

3. **调整决策阈值**
   ```kotlin
   // 在 ScoreCalculator 中
   when {
       normalizedScore >= 40.0 -> "auto_add"  // 从50降低到40
       normalizedScore >= 25.0 -> "confirm"   // 从30降低到25
       else -> "ignore"
   }
   ```

## 📝 日志输出

布局特征检测时会输出日志：
```
ScoreCalculator: 选项标记数: X, hasOptions: true/false, baseFeatureWeight: 1.0/0.5/0.0
ScoreCalculator: 决策: auto_add/confirm/ignore (选项标记=0, 分数XX.XX, 强布局特征=true/false)
```

## 🔍 验证方法

1. **查看日志**
   - 检查是否有"强布局特征"的日志
   - 检查决策是否基于布局特征

2. **对比识别结果**
   - 部分遮挡前后的识别结果对比
   - 检查是否通过布局特征识别

