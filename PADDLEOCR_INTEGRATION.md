# PaddleOCR Android 集成指南

## 📋 概述

PaddleOCR 没有现成的 Maven 依赖，需要手动集成 Paddle Lite SDK 和模型文件。

## 🔧 集成步骤

### 步骤1：下载 Paddle Lite 预编译库

从 PaddleOCR 官方 GitHub 下载 Android Demo：

```bash
git clone https://github.com/PaddlePaddle/PaddleOCR.git
cd PaddleOCR/deploy/android_demo
```

或直接下载：
- https://github.com/PaddlePaddle/PaddleOCR/tree/main/deploy/android_demo

### 步骤2：复制必要文件

从 Android Demo 中复制以下文件到你的项目：

```
android_demo/app/src/main/
├── cpp/                          → app/src/main/cpp/
├── jniLibs/                      → app/src/main/jniLibs/
│   ├── arm64-v8a/
│   │   └── libpaddle_lite_jni.so
│   └── armeabi-v7a/
│       └── libpaddle_lite_jni.so
└── assets/models/                → app/src/main/assets/paddleocr_models/
    ├── ch_PP-OCRv4_det_infer/
    ├── ch_PP-OCRv4_rec_infer/
    └── ppocr_keys_v1.txt
```

### 步骤3：配置 build.gradle.kts

```kotlin
android {
    // 添加 NDK 配置
    defaultConfig {
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    
    // 配置 CMake
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}
```

### 步骤4：创建 JNI 接口

创建 `PaddleOCRNative.kt`：

```kotlin
package com.gongkao.cuotifupan.ocr

class PaddleOCRNative {
    companion object {
        init {
            System.loadLibrary("paddle_lite_jni")
            System.loadLibrary("Native") // 你的 JNI 库
        }
    }
    
    external fun init(
        detModelPath: String,
        recModelPath: String,
        keysPath: String
    ): Boolean
    
    external fun recognize(bitmap: android.graphics.Bitmap): String
    
    external fun release()
}
```

### 步骤5：编写 C++ 代码

参考 PaddleOCR Android Demo 的 `native-lib.cpp` 文件。

## ⚠️ 注意事项

1. **模型文件大小**：约 20-30MB，会增加 APK 体积
2. **首次加载较慢**：模型初始化需要 2-5 秒
3. **内存占用**：运行时约需 100-200MB 内存
4. **兼容性**：需要 arm64-v8a 或 armeabi-v7a 架构

## 🔄 替代方案

如果手动集成太复杂，可以考虑：

### 方案1：在线 API（最简单，效果最好）
- 百度手写识别 API：https://ai.baidu.com/tech/ocr/handwriting
- 准确率 90%+，需要网络

### 方案2：继续使用 ML Kit（当前方案）
- 简单，但手写识别效果一般

### 方案3：使用 WebView + PaddleOCR.js
- 通过 WebView 运行 JavaScript 版本的 PaddleOCR
- 不需要 NDK，但性能较低

## 📥 模型下载

PaddleOCR 预训练模型：
- 检测模型：https://paddleocr.bj.bcebos.com/PP-OCRv4/chinese/ch_PP-OCRv4_det_infer.tar
- 识别模型：https://paddleocr.bj.bcebos.com/PP-OCRv4/chinese/ch_PP-OCRv4_rec_infer.tar
- 字典文件：https://gitee.com/paddlepaddle/PaddleOCR/raw/release/2.7/ppocr/utils/ppocr_keys_v1.txt

## 🎯 推荐

考虑到集成复杂度和实际效果，**推荐使用在线 API 方案**：
- 集成简单（只需 HTTP 请求）
- 手写识别准确率最高
- 支持各种书写风格

