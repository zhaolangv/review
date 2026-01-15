# APK 体积分析报告

## 📊 总体情况

根据代码库分析，APK 体积较大的主要原因如下：

---

## 🔍 主要大文件（已确认）

### 1. **字体文件** - 7.11 MB ⚠️ **最大文件**
- **文件**: `app/src/main/assets/fonts/calligraphy.ttf`
- **大小**: 7.11 MB
- **说明**: 书法字体文件，用于手写显示
- **建议**: 
  - 考虑使用系统字体替代
  - 或者将字体文件移至动态功能模块
  - 或者使用更小的字体子集（只包含常用字符）

### 2. **原生库文件** - 约 5.68 MB
- **libpaddle_lite_jni.so**: 3.81 MB
- **libc++_shared.so**: 1.01 MB
- **libhiai_ir.so**: 0.78 MB
- **libhiai.so**: 0.08 MB
- **libhiai_ir_build.so**: 0.04 MB
- **说明**: Paddle Lite OCR 所需原生库
- **建议**: 已配置为只打包 arm64-v8a，这是正确的 ✅

### 3. **OCR 模型文件** - 约 4.62 MB ⚠️ **当前未被使用**
- **rec_crnn.nb** (识别模型): 3.36 MB
- **det_db.nb** (检测模型): 0.84 MB
- **cls.nb** (分类模型): 0.42 MB
- **说明**: PaddleOCR 模型文件，用于 OCR 识别
- **⚠️ 重要发现**: 
  - **这些模型文件当前已被禁用，不会影响功能！**
  - `PaddleOcrHelper.init()` 方法直接返回 `false`，PaddleOCR 功能已被临时禁用
  - 应用实际使用的是 **ML Kit OCR** 作为主要的 OCR 引擎
  - 代码中虽然调用了 PaddleOCR，但初始化失败后会回退到 ML Kit
- **建议**: 
  - ✅ **可以安全移除**这些模型文件（减少 4.62 MB）
  - 如果将来要启用 PaddleOCR，再重新添加

### 4. **图片资源** - 约 1.4 MB
- **img.png**: 0.78 MB
- **decor_side_chiikawa_1.png**: 0.6 MB
- **其他 PNG 文件**: ~0.02 MB × 10+ = ~0.2 MB
- **说明**: 装饰性图片资源
- **建议**: 
  - 将 PNG 转换为 WebP 格式（可减少 30-50%）
  - 压缩图片质量
  - 移除不必要的装饰图片

---

## 📦 依赖库（推测体积）

### 1. **ML Kit** - 估计 15-25 MB
- `mlkit-common`: ML Kit 基础库
- `mlkit-text-recognition`: 文本识别
- `mlkit-text-recognition-chinese`: 中文文本识别（**可能较大**）
- `mlkit-digital-ink`: 数字墨水识别
- **说明**: ML Kit 中文识别包包含模型数据，体积较大
- **建议**: 
  - 考虑是否必须使用 ML Kit（已有 PaddleOCR）
  - 或使用动态功能模块

### 2. **TensorFlow Lite** - 估计 5-10 MB
- `tensorflow-lite`: 2.13.0
- **说明**: 用于 TrOCR 模型推理（但项目中似乎未使用）
- **建议**: 如果未使用 TrOCR，可以移除此依赖

### 3. **OpenCV** - 估计 10-30 MB ⚠️ **可能很大，当前未被使用**
- **来源**: `app/src/main/cpp/CMakeLists.txt` 中链接了 OpenCV
- **说明**: OpenCV 是一个完整的计算机视觉库，体积很大
- **当前用途**: 用于 PaddleOCR 的 C++ 代码中的图像预处理（`bitmap_to_cv_mat`, `resize_img` 等）
- **⚠️ 重要发现**: 
  - **OpenCV 当前未被使用，不会影响功能！**
  - OpenCV 只在 PaddleOCR 的 C++ JNI 代码中使用
  - 由于 PaddleOCR 已被禁用，OpenCV 的 C++ 代码也不会被执行
- **建议**: 
  - ✅ **可以考虑移除 OpenCV 依赖**（减少 10-30 MB）
  - 移除方法：从 `CMakeLists.txt` 中删除 OpenCV 相关代码
  - 或者保留代码但移除 OpenCV 库（如果编译时未链接，体积不会增加）

### 4. **Paddle Lite** - 估计 5-10 MB ⚠️ **当前未被使用**
- Paddle Lite Java API（JAR 文件）：0.01 MB（已确认很小）
- Paddle Lite 原生库：5.68 MB（libpaddle_lite_jni.so 等）
- **说明**: PaddleOCR 的核心库
- **⚠️ 重要发现**: 
  - **Paddle Lite 当前未被使用，不会影响功能！**
  - 由于 PaddleOCR 已被禁用，Paddle Lite 库不会被加载
- **建议**: 
  - ✅ **可以考虑移除 Paddle Lite 原生库**（减少约 5.68 MB）
  - 保留 Java API JAR 文件不影响（只有 0.01 MB）

### 5. **其他依赖** - 估计 10-15 MB
- AndroidX 库集合（Room, Lifecycle, Activity, Fragment, RecyclerView 等）
- Material Design 组件
- Coil（图片加载）
- Retrofit + OkHttp + Gson（网络请求）
- Kotlin 标准库和协程
- PhotoView（图片缩放）

---

## 🔧 构建配置分析

### ✅ 已优化的配置
1. **只打包 arm64-v8a**: ✅
   ```kotlin
   ndk {
       abiFilters += listOf("arm64-v8a")
   }
   ```

2. **启用代码混淆**: ✅
   ```kotlin
   isMinifyEnabled = true
   ```

3. **启用资源压缩**: ✅
   ```kotlin
   isShrinkResources = true
   ```

4. **只保留中文和英文资源**: ✅
   ```kotlin
   resourceConfigurations += listOf("zh", "zh-rCN", "en")
   ```

### ❌ 未优化的配置
1. **未启用 App Bundle**: 
   - 如果发布到 Google Play，建议使用 AAB 格式
   - 用户只下载需要的资源

2. **未启用动态功能模块**:
   - OCR 功能可以移至动态功能模块
   - 字体文件可以移至动态功能模块

---

## 📈 体积估算

| 类别 | 估算大小 | 说明 |
|------|---------|------|
| 字体文件 | 7.11 MB | ✅ 已确认 |
| 原生库 | 5.68 MB | ✅ 已确认 |
| OCR 模型 | 4.62 MB | ✅ 已确认 |
| 图片资源 | 1.4 MB | ✅ 已确认 |
| ML Kit | 15-25 MB | ⚠️ 推测（中文识别包较大） |
| TensorFlow Lite | 5-10 MB | ⚠️ 推测 |
| OpenCV | 10-30 MB | ⚠️ 推测（**可能最大**） |
| Paddle Lite | 5-10 MB | ⚠️ 推测 |
| 其他依赖 | 10-15 MB | ⚠️ 推测 |
| **总计** | **约 65-115 MB** |  |

---

## 🎯 优化建议（按优先级）

### 🔴 高优先级（立即优化，可减少 30-50 MB）

1. **评估 OpenCV 的使用**
   - 检查是否真的需要完整的 OpenCV
   - 如果只是做简单的图像操作（resize, convert），可以用 Android 原生 API 替代
   - **预期减少**: 10-30 MB

2. **移除未使用的 TensorFlow Lite**
   - 检查 TrOCR 是否真的在使用
   - 如果未使用，移除 TensorFlow Lite 依赖
   - **预期减少**: 5-10 MB

3. **优化字体文件**
   - 使用字体子集（只包含常用字符）
   - 或移至动态功能模块
   - 或使用系统字体
   - **预期减少**: 5-7 MB

### 🟡 中优先级（中期优化，可减少 10-20 MB）

4. **将 OCR 功能移至动态功能模块**
   - PaddleOCR 模型和库
   - ML Kit（如果必须使用）
   - **预期减少**: 15-25 MB（但不会减少初始下载体积）

5. **优化图片资源**
   - 将 PNG 转换为 WebP
   - 压缩图片质量
   - **预期减少**: 0.5-1 MB

6. **评估 ML Kit 的必要性**
   - 如果 PaddleOCR 已满足需求，考虑移除 ML Kit
   - **预期减少**: 15-25 MB

### 🟢 低优先级（长期优化，可减少 5-10 MB）

7. **启用 App Bundle**
   - 使用 AAB 格式发布
   - 用户只下载需要的资源

8. **进一步压缩原生库**
   - 使用更激进的编译优化
   - 移除未使用的符号

---

## 🔍 快速检查清单

使用 Android Studio 的 APK Analyzer 检查实际 APK：

1. **Build** → **Analyze APK**
2. 选择生成的 APK 文件
3. 查看各部分的实际大小：
   - `lib/` - 原生库大小
   - `assets/` - 资源文件大小
   - `res/` - 资源文件大小
   - `classes.dex` - 代码大小
   - `META-INF/` - 元数据大小

---

## 📝 总结

**当前 APK 体积较大的主要原因：**

1. **ML Kit 中文识别包**（估计 15-25 MB）- ⚠️ 需要确认
2. **字体文件**（7.11 MB）- ✅ 已确认
3. **PaddleOCR 相关组件**（约 20-40 MB）- ✅ 已确认，但**当前未被使用**，可以移除
   - OCR 模型文件：4.62 MB
   - Paddle Lite 原生库：5.68 MB
   - OpenCV（如果包含）：10-30 MB
4. **其他依赖库**（估计 10-15 MB）

**建议优先执行：**
1. ✅ **立即移除 PaddleOCR 相关组件**（不会影响功能，可减少 20-40 MB）
   - 移除 `app/src/main/assets/paddleocr/` 目录
   - 移除 `app/src/main/jniLibs/` 中的 Paddle Lite 库
   - 从 `CMakeLists.txt` 中移除 OpenCV 依赖
2. 使用 APK Analyzer 查看实际 APK 组成
3. 确认 TensorFlow Lite 是否在使用
4. 评估是否真的需要 ML Kit（如果移除 PaddleOCR，ML Kit 是唯一的 OCR 方案）

