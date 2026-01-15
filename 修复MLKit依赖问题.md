# 修复 ML Kit Digital Ink 依赖版本问题

## 问题描述

错误信息：
```
java.lang.NoClassDefFoundError: Failed resolution of: Lcom/google/mlkit/common/sdkinternal/LibraryVersion;
at com.google.mlkit:common@@18.11.0
```

## 解决方案

### 方案 1：升级 digital-ink-recognition（推荐）

已将 `mlkitDigitalInk` 从 `16.0.0` 升级到 `18.0.0`，与 `mlkitCommon 18.11.0` 兼容。

### 方案 2：如果方案 1 不行，使用兼容版本

如果升级后仍有问题，可以尝试：
- `mlkitCommon = "17.1.0"`
- `mlkitDigitalInk = "17.0.0"`

## 必须执行的步骤

### 1. 清理 Gradle 缓存（重要！）

在 Android Studio 中：
- 点击菜单 **File → Invalidate Caches / Restart**
- 选择 **Invalidate and Restart**

或者手动清理：
```bash
# Windows PowerShell
Remove-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches\modules-2\files-2.1\com.google.mlkit" -ErrorAction SilentlyContinue
```

### 2. 同步 Gradle
- 点击 **File → Sync Project with Gradle Files**
- 等待同步完成

### 3. 清理项目
- 点击 **Build → Clean Project**

### 4. 完全卸载应用
在手机上：
- **设置 → 应用 → 错题复盘 → 卸载**

或者用 adb：
```bash
adb uninstall com.gongkao.cuotifupan
```

### 5. 重新构建并安装
- 点击 **Build → Rebuild Project**
- 然后点击 **Run** 安装到设备

## 验证

安装后，打开手写识别界面，应该不再出现 `NoClassDefFoundError` 错误。

如果仍有问题，请检查 Logcat 中的依赖版本信息。

