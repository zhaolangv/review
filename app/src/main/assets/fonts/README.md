# 字体库目录

## 📋 说明

此目录用于存放手写识别美化功能使用的字体文件。

## 📁 目录结构

```
app/src/main/assets/fonts/
├── regular.ttf      # 常规字体（推荐：思源黑体、苹方）
├── bold.ttf         # 粗体字体（推荐：思源黑体 Bold）
├── elegant.ttf      # 优雅字体（推荐：思源宋体、方正兰亭细黑）
└── calligraphy.ttf  # 书法字体（推荐：汉仪尚巍手书、方正字迹-吕建德行楷）
```

## 📥 字体文件来源

### 推荐字体（免费商用）

#### 1. 常规字体 (regular.ttf)
- **思源黑体** (Source Han Sans)
  - 下载：https://github.com/adobe-fonts/source-han-sans
  - 文件：`SourceHanSansCN-Regular.otf` 或 `.ttf`
- **苹方** (PingFang SC)
  - 系统自带，需要提取或使用替代字体

#### 2. 粗体字体 (bold.ttf)
- **思源黑体 Bold**
  - 下载：https://github.com/adobe-fonts/source-han-sans
  - 文件：`SourceHanSansCN-Bold.otf` 或 `.ttf`

#### 3. 优雅字体 (elegant.ttf)
- **思源宋体** (Source Han Serif)
  - 下载：https://github.com/adobe-fonts/source-han-serif
  - 文件：`SourceHanSerifCN-Regular.otf` 或 `.ttf`
- **方正兰亭细黑**
  - 需要购买授权

#### 4. 书法字体 (calligraphy.ttf)
- **玄冬楷书** ⭐ 推荐（免费商用）
  - 下载地址：https://github.com/Skr-ZERO/Xuandong-Kaishu
  - 授权：SIL Open Font License 1.1（允许商业使用）
  - 文件：`玄冬楷书.ttf` 或 `玄冬楷书.otf`
  - 重命名为：`calligraphy.ttf`
  - 特点：楷书风格，适合手写美化
- **汉仪尚巍手书**
  - 需要购买授权
- **方正字迹-吕建德行楷**
  - 需要购买授权

## 🔧 使用方法

1. **下载字体文件**
   - 从上述推荐来源下载字体文件
   - 确保字体文件支持中文（包含中文字符集）

2. **重命名并放置**
   - 将字体文件重命名为对应的文件名
   - 放置到 `app/src/main/assets/fonts/` 目录

3. **重新编译**
   - 字体文件会自动打包到 APK 中
   - 无需额外配置

## ⚠️ 注意事项

1. **字体授权**：
   - 确保使用的字体文件有商用授权
   - 思源字体系列（Adobe）可免费商用
   - 部分字体需要购买授权

2. **文件大小**：
   - 中文字体文件通常较大（5-20MB）
   - 会增加 APK 体积
   - 建议只包含必要的字体文件

3. **字符集支持**：
   - 确保字体文件包含常用中文字符
   - 建议使用 GB2312 或 GB18030 字符集

4. **文件格式**：
   - 支持 `.ttf` 和 `.otf` 格式
   - 推荐使用 `.ttf` 格式（兼容性更好）

## 📝 字体文件命名

必须使用以下文件名（区分大小写）：
- `regular.ttf` - 常规字体
- `bold.ttf` - 粗体字体
- `elegant.ttf` - 优雅字体
- `calligraphy.ttf` - 书法字体

如果某个字体文件不存在，系统会自动回退到默认字体。

## 🔍 验证字体是否加载成功

在应用运行时，查看 Logcat 日志：
- 如果字体加载失败，会输出警告信息
- 成功加载不会有额外日志（静默加载）

