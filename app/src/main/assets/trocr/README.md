# TrOCR 模型文件

请将 TrOCR TensorFlow Lite 模型文件放置在此目录下，命名为 `model.tflite`。

## 如何获取模型文件

1. **从 PyTorch 转换**（需要 Python 环境）：
   - 下载 TrOCR 模型（中文手写识别版本）
   - 转换为 TensorFlow Lite 格式
   - 量化优化以减少模型大小

2. **查找现成模型**：
   - GitHub 搜索：`trocr tflite android`
   - Hugging Face：查找是否有现成的 TFLite 模型
   - 社区资源：查看是否有其他开发者分享的转换模型

详细说明请参考项目根目录的 `TrOCR_INTEGRATION_GUIDE.md`。

## 文件结构

```
app/src/main/assets/trocr/
└── model.tflite  ← 将模型文件放在这里
```

## 注意事项

- 模型文件较大（可能 50-100MB），会增加 APK 体积
- 如果模型文件不存在，应用会自动回退到 PaddleOCR 或 ML Kit
- 模型文件会在首次运行时从 assets 复制到缓存目录

