# 安装 Python 3.11/3.12 并转换 TrOCR

## 📋 步骤 1：安装 Python 3.11 或 3.12

### 下载 Python 3.11（推荐）

1. **访问下载页面**：
   - Python 3.11.9: https://www.python.org/downloads/release/python-3119/
   - 或 Python 3.12.4: https://www.python.org/downloads/release/python-3124/

2. **下载安装程序**：
   - 选择 "Windows installer (64-bit)"
   - 下载 `.exe` 文件

3. **安装 Python**：
   - 运行下载的安装程序
   - ✅ **重要**：勾选 "Add Python to PATH"
   - 选择 "Install Now" 或自定义安装路径

4. **验证安装**：
   ```powershell
   python --version
   # 应该显示 Python 3.11.x 或 3.12.x
   ```

## 📋 步骤 2：创建虚拟环境并安装依赖

打开 PowerShell，在项目目录运行：

```powershell
# 1. 创建虚拟环境
python -m venv venv_trocr

# 2. 激活虚拟环境
.\venv_trocr\Scripts\Activate.ps1

# 3. 升级 pip
python -m pip install --upgrade pip

# 4. 安装依赖（这可能需要 5-10 分钟）
pip install torch transformers tensorflow onnx onnx-tf

# 5. 验证安装
python -c "import torch; import transformers; import tensorflow; import onnx; print('所有依赖已安装')"
```

## 📋 步骤 3：运行转换脚本

```powershell
# 确保虚拟环境已激活（命令提示符前应该有 (venv_trocr)）
python convert_trocr_to_tflite.py
```

**注意**：
- 转换过程可能需要 **10-30 分钟**
- 需要下载模型文件（可能几GB）
- 需要较大内存（建议 8GB+）

## 📋 步骤 4：复制模型文件

如果转换成功，会生成 `trocr_model.tflite` 文件，将其复制到：

```powershell
Copy-Item trocr_model.tflite app\src\main\assets\trocr\model.tflite
```

## ⚠️ 常见问题

### 问题1：PowerShell 执行策略限制

如果激活虚拟环境失败，运行：
```powershell
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

### 问题2：TensorFlow 安装失败

如果 TensorFlow 安装失败，尝试：
```powershell
pip install tensorflow --upgrade
# 或指定版本
pip install tensorflow==2.15.0
```

### 问题3：转换失败

TrOCR 转换可能失败，因为它是序列到序列模型。如果失败：
1. 查看错误信息
2. 考虑使用 ONNX Runtime 方案（只转换到 ONNX）
3. 或继续使用 PaddleOCR

## 🚀 快速安装脚本

如果你想自动化安装，可以运行：

```powershell
# 运行自动安装脚本（需要先安装 Python 3.11/3.12）
.\setup_python_for_trocr.ps1
```

## 💡 提示

- 转换过程较长，建议在空闲时间进行
- 如果转换失败，应用会自动使用 PaddleOCR（当前方案）
- 可以考虑先使用 PaddleOCR，等找到现成的 TrOCR 模型后再切换

