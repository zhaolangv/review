# TrOCR 模型获取指南

## 📋 概述

本指南提供几种获取 TrOCR 模型文件的方法。由于 TrOCR 模型转换较复杂，建议优先查找现成的转换模型。

## 🎯 方法1：查找现成的 TensorFlow Lite 模型（推荐）

### 搜索位置：

1. **GitHub**：
   - 搜索关键词：`trocr tflite android`
   - 搜索关键词：`trocr tensorflow lite`
   - 搜索关键词：`trocr-chinese tflite`

2. **Hugging Face**：
   - 访问：https://huggingface.co/models?search=trocr
   - 查找是否有转换好的 TFLite 模型

3. **社区资源**：
   - 查看是否有其他开发者分享的转换模型
   - 相关论坛和讨论区

## 🔧 方法2：使用 Python 脚本转换（需要 Python 环境）

### 前提条件：

1. **安装 Python 3.7+**
2. **安装依赖**：
```bash
pip install torch transformers tensorflow onnx onnx-tf
```

### 使用脚本：

1. **运行转换脚本**：
```bash
python convert_trocr_to_tflite.py
```

2. **注意事项**：
   - 转换过程可能需要 10-30 分钟
   - 需要较大内存（建议 8GB+）
   - TrOCR 是序列到序列模型，转换可能不完美
   - 最终模型文件可能较大（100-300MB）

### 脚本说明：

- 脚本会下载 TrOCR 模型（默认是英文手写识别模型）
- 转换为 ONNX 格式
- 再转换为 TensorFlow Lite 格式
- 如果转换失败，会提供错误信息和建议

## 🌏 方法3：使用中文 TrOCR 模型

### 中文模型来源：

1. **trocr-chinese** (GitHub)：
   - 仓库：https://github.com/chineseocr/trocr-chinese
   - 需要从 GitHub 下载模型文件
   - 然后使用转换脚本转换为 TFLite

2. **修改脚本使用中文模型**：
   - 编辑 `convert_trocr_to_tflite.py`
   - 修改 `model_name` 变量
   - 或从本地路径加载模型

## ⚠️ 方法4：使用 ONNX Runtime（替代方案）

如果 TensorFlow Lite 转换失败，可以考虑使用 ONNX Runtime：

1. **转换到 ONNX 即可**（跳过 TFLite 转换）
2. **在 Android 中使用 ONNX Runtime Mobile**
3. **修改 `TrOCROcrHelper.kt`** 使用 ONNX Runtime API

### 添加 ONNX Runtime 依赖：

在 `gradle/libs.versions.toml` 中添加：
```toml
onnxruntime = "1.16.0"
```

在 `app/build.gradle.kts` 中添加：
```kotlin
implementation("com.microsoft.onnxruntime:onnxruntime-android:1.16.0")
```

## 🚀 推荐方案

考虑到 TrOCR 模型转换的复杂性：

1. **优先查找现成的转换模型**（最快）
2. **如果找不到，尝试转换脚本**
3. **如果转换失败，考虑使用 ONNX Runtime**
4. **或者继续使用 PaddleOCR**（当前方案）

## 📁 模型文件放置位置

无论使用哪种方法获取模型，最终都需要将模型文件放置到：

```
app/src/main/assets/trocr/model.tflite
```

## ⚡ 快速开始

如果你有 Python 环境，可以直接运行：

```bash
# 1. 安装依赖
pip install torch transformers tensorflow onnx onnx-tf

# 2. 运行转换脚本
python convert_trocr_to_tflite.py

# 3. 将生成的 model.tflite 复制到 assets/trocr/ 目录
```

## 💡 提示

- 模型文件较大，会增加 APK 体积
- 如果没有模型文件，应用会自动回退到 PaddleOCR
- 转换过程可能需要多次尝试和调整

