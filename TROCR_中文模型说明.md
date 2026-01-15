# TrOCR 中文模型说明

## ✅ 找到了中文 TrOCR 模型！

有两个中文 TrOCR 模型可以选择：

### 1. 微软官方中文模型（推荐）⭐

**模型名称**: `microsoft/trocr-base-handwritten-zh`

**特点**:
- ✅ 微软官方提供
- ✅ 专门针对中文手写体训练
- ✅ 手写体准确率：88.4%
- ✅ 印刷体准确率：97.1%
- ✅ 可直接从 HuggingFace 下载
- ✅ 模型质量稳定

**使用方法**:
转换脚本已更新，默认使用此模型。

### 2. 社区中文模型

**模型名称**: `chineseocr/trocr-chinese`

**特点**:
- ✅ 社区维护的中文 TrOCR 实现
- ✅ 支持单行、多行、横竖排文字识别
- ✅ 支持不规则文字（如印章、公式等）
- ⚠️  可能需要从 GitHub 下载

**GitHub**: https://github.com/chineseocr/trocr-chinese

## 🚀 使用中文模型转换

转换脚本已更新，运行时会让你选择模型：

```powershell
.\venv_trocr\Scripts\Activate.ps1
python convert_trocr_to_tflite.py
```

**选择说明**:
- 输入 `1` 或直接回车：使用微软官方中文模型（推荐）
- 输入 `2`：使用社区中文模型
- 输入 `3`：使用英文模型

## 📊 模型对比

| 模型 | 语言 | 来源 | 推荐度 |
|------|------|------|--------|
| microsoft/trocr-base-handwritten-zh | 中文 | 微软官方 | ⭐⭐⭐⭐⭐ |
| chineseocr/trocr-chinese | 中文 | 社区 | ⭐⭐⭐⭐ |
| microsoft/trocr-base-handwritten | 英文 | 微软官方 | ⭐⭐⭐ |

## 💡 建议

**推荐使用 `microsoft/trocr-base-handwritten-zh`**，因为：
1. 官方支持，质量稳定
2. 专门为中文手写优化
3. 易于下载和使用
4. 准确率较高（88.4%）

## ⚠️ 注意事项

1. **转换时间**: 转换过程可能需要 10-30 分钟
2. **模型大小**: 转换后的模型可能较大（100-300MB）
3. **网络要求**: 首次运行需要下载模型（可能需要几分钟）
4. **内存要求**: 建议 8GB+ 内存

## 📝 下一步

运行转换脚本，选择中文模型：

```powershell
.\venv_trocr\Scripts\Activate.ps1
python convert_trocr_to_tflite.py
# 选择 1 (中文模型)
# 输入 y 继续
```

