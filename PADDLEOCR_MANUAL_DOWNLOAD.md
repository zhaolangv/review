# PaddleOCR 手动下载指南

由于 PaddleOCR Android Demo 项目较旧，不兼容 Java 21，我们直接手动下载需要的文件。

## 📥 需要下载的文件

### 1. Paddle Lite 库文件 (.so)
**下载地址：** https://paddleocr.bj.bcebos.com/libs/paddle_lite_libs_v2_10.tar.gz

**解压后需要：**
- `libpaddle_lite_jni.so` (arm64-v8a 和 armeabi-v7a 两个版本)
- 复制到：`app/src/main/jniLibs/arm64-v8a/` 和 `app/src/main/jniLibs/armeabi-v7a/`

### 2. OCR 模型文件
**下载地址：** https://paddleocr.bj.bcebos.com/PP-OCRv2/lite/ch_PP-OCRv2.tar.gz

**解压后需要：**
- `ch_PP-OCRv2_det_slim_infer.nb` (检测模型)
- `ch_PP-OCRv2_rec_slim_infer.nb` (识别模型)
- 复制到：`app/src/main/assets/paddleocr/models/`

### 3. 字典文件
**下载地址：** https://paddleocr.bj.bcebos.com/dygraph_v2.0/lite/ch_dict.tar.gz

**解压后需要：**
- `ppocr_keys_v1.txt`
- 复制到：`app/src/main/assets/paddleocr/labels/`

## 🔧 手动下载步骤

### 方式1：使用 PowerShell 脚本（推荐）

1. 运行 `download_paddleocr_files.ps1`
2. 脚本会自动下载并解压文件

### 方式2：手动下载

1. **下载文件：**
   - 用浏览器打开上面的链接，下载 3 个 .tar.gz 文件

2. **解压文件：**
   - 使用 7-Zip 或 WinRAR 解压
   - Windows 10+ 也可以使用命令行：`tar -xzf 文件名.tar.gz`

3. **复制文件：**
   ```
   app/src/main/
   ├── jniLibs/
   │   ├── arm64-v8a/
   │   │   └── libpaddle_lite_jni.so
   │   └── armeabi-v7a/
   │       └── libpaddle_lite_jni.so
   └── assets/
       └── paddleocr/
           ├── models/
           │   ├── ch_PP-OCRv2_det_slim_infer.nb
           │   └── ch_PP-OCRv2_rec_slim_infer.nb
           └── labels/
               └── ppocr_keys_v1.txt
   ```

## ⚠️ 注意事项

- .so 文件需要两个架构版本（arm64-v8a 和 armeabi-v7a）
- 模型文件总大小约 10-15MB
- 字典文件很小（几KB）

## ✅ 下载完成后

下载并复制文件后，告诉我，我会帮你：
1. 复制必要的 C++ 源码
2. 配置 CMakeLists.txt
3. 编写 Kotlin 调用代码
4. 集成到 HandwritingRecognitionService

