# APK体积优化建议

## 📊 当前情况
- **APK大小**: 45.57 MB
- **主要问题**: 体积偏大，影响下载和安装体验

## 🔍 主要原因分析

### 1. **ML Kit OCR 中文识别模型** ⚠️ 最大原因
- **位置**: `com.google.mlkit:text-recognition-chinese`
- **问题**: ML Kit 的中文识别模型会打包到APK中，通常占用 **20-30MB**
- **影响**: 这是导致APK体积大的主要原因

### 2. **依赖库体积**
虽然已启用代码混淆和资源压缩，但以下库仍会占用空间：
- Room 数据库 (~2-3MB)
- Retrofit + OkHttp (~1-2MB)
- Coil 图片加载 (~500KB)
- Material Design (~1-2MB)
- 其他 AndroidX 库 (~3-5MB)

### 3. **资源文件**
- 图片资源（drawable中的PNG/WebP）
- 多密度图标（mipmap-*）

## ✅ 优化方案

### 方案1: 使用动态功能模块（推荐）⭐

将ML Kit OCR模型移到动态功能模块，用户首次使用时再下载：

```kotlin
// 在 app/build.gradle.kts 中添加
android {
    dynamicFeatures = mutableSetOf(":mlkit")
}

// 创建 mlkit/build.gradle.kts
plugins {
    id("com.android.dynamic-feature")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.gongkao.cuotifupan.mlkit"
    
    defaultConfig {
        minSdk = 24
    }
}

dependencies {
    implementation(project(":app"))
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.text.recognition.chinese)
}
```

**优点**: 
- 基础APK可减少 20-30MB
- 用户按需下载OCR功能
- 不影响核心功能

**缺点**: 
- 需要配置Play Core Library
- 首次使用OCR需要下载

### 方案2: 使用App Bundle + 按需下载模型

配置应用使用AAB格式，让Google Play自动优化分发：

```kotlin
// app/build.gradle.kts
android {
    bundle {
        language {
            enableSplit = true  // 按语言分包
        }
        density {
            enableSplit = true  // 按密度分包
        }
        abi {
            enableSplit = true  // 按架构分包
        }
    }
}
```

**优点**: 
- Google Play会自动优化
- 用户只下载需要的资源
- 无需代码改动

### 方案3: 优化ML Kit使用方式

如果必须包含在APK中，可以：

1. **使用基础版ML Kit**（如果支持）：
```kotlin
// 考虑是否可以使用基础版，体积更小
implementation("com.google.mlkit:text-recognition:16.0.1")
// 而不是中文专用版
```

2. **延迟加载模型**：
```kotlin
// 不在应用启动时初始化，只在需要时加载
private var mlKitRecognizer: TextRecognizer? = null

fun getRecognizer(): TextRecognizer {
    if (mlKitRecognizer == null) {
        mlKitRecognizer = TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
    }
    return mlKitRecognizer!!
}
```

### 方案4: 优化资源文件

1. **压缩图片资源**：
```bash
# 使用工具压缩PNG/WebP图片
# 推荐工具：TinyPNG, ImageOptim
```

2. **移除未使用的资源**：
```kotlin
// build.gradle.kts 中已启用
isShrinkResources = true  // ✅ 已配置
```

3. **使用WebP格式**：
```kotlin
// 将PNG转换为WebP，可减少30-50%体积
// Android Studio: Right-click image -> Convert to WebP
```

### 方案5: 启用R8完整模式

确保ProGuard规则优化到位：

```kotlin
// app/build.gradle.kts
android {
    buildTypes {
        release {
            isMinifyEnabled = true  // ✅ 已启用
            isShrinkResources = true  // ✅ 已启用
            // 确保使用优化版本
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),  // ✅ 已使用optimize版本
                "proguard-rules.pro"
            )
        }
    }
}
```

### 方案6: 移除未使用的依赖

检查并移除不需要的依赖：

```kotlin
// 检查这些是否真的需要：
// - kotlinx-coroutines-play-services (如果不用Play Services)
// - 某些测试库在release中是否被打包
```

## 📈 预期效果

| 优化方案 | 预计减少 | 实施难度 |
|---------|---------|---------|
| 动态功能模块 | 20-30MB | 中等 |
| App Bundle | 10-20MB | 简单 |
| 优化资源文件 | 2-5MB | 简单 |
| 移除未使用依赖 | 1-3MB | 简单 |

## 🚀 推荐实施步骤

### 第一步：快速优化（立即实施）
1. ✅ 检查并压缩图片资源
2. ✅ 移除未使用的依赖
3. ✅ 确保所有资源都使用WebP格式

### 第二步：中期优化（1-2天）
1. ⭐ 实施动态功能模块（最大收益）
2. 配置App Bundle

### 第三步：长期优化
1. 考虑使用更轻量的OCR方案
2. 持续监控APK体积

## 🔧 快速检查清单

- [ ] 检查 `app/release/app-release.apk` 实际大小
- [ ] 使用 `bundletool` 分析APK组成
- [ ] 检查是否有大型assets文件
- [ ] 验证资源压缩是否生效
- [ ] 检查ProGuard是否正常工作

## 📝 分析APK组成

使用以下命令分析APK：

```bash
# 使用 Android Studio 的 APK Analyzer
# Build -> Analyze APK -> 选择 app-release.apk

# 或使用命令行工具
bundletool build-apks --bundle=app-release.aab --output=app.apks
```

## 💡 额外建议

1. **监控体积**: 每次发布前检查APK大小
2. **版本对比**: 对比不同版本的体积变化
3. **用户反馈**: 关注用户对下载速度的反馈

---

**总结**: ML Kit中文识别模型是导致APK体积大的主要原因。建议优先实施动态功能模块方案，可以将APK体积减少到 **15-20MB** 左右。

