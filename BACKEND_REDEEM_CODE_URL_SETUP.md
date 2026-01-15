# 后端兑换码链接配置说明

## 概述

为了让应用能够显示"如何领取兑换码"页面，后端需要在版本检查 API（`/api/version`）的响应中添加 `redeem_code_url` 字段。

## API 修改

### 接口：`GET /api/version`

#### 请求参数（不变）

- `client_version` (String): 客户端当前版本号
- `device_id` (String): 设备ID

#### 响应格式（需要添加字段）

在现有响应JSON中，添加一个**可选字段** `redeem_code_url`：

```json
{
  "service": "公考题库分析服务",
  "status": "online",
  "success": true,
  "timestamp": "2026-01-10T12:15:47.511127",
  "update": {
    "download_url": "/api/apk/download",
    "latest_version": "2.0.0",
    "release_notes": "",
    "required": false
  },
  "version": {
    "api_version": "2.0",
    "app_version": "2.0.0",
    "build_time": "2026-01-10T12:15:45.811189",
    "flask_version": "3.0.0",
    "platform": "Windows",
    "platform_version": "10.0.22631",
    "python_version": "3.14.2"
  },
  "pro": {
    "is_pro": true,
    "expires_at": "2026-03-09T15:59:59.999000+00:00",
    "monthly_quota": 60,
    "next_period_quota": 0,
    "remaining_quota": 60,
    "used_count": 0
  },
  "free_quota": {
    "total_quota": 5,
    "used_count": 0,
    "remaining_quota": 5,
    "is_available": true
  },
  "redeem_code_url": "http://xhslink.com/o/34D18MYRkBh"  // ← 新增字段（可选）
}
```

### 字段说明

#### `redeem_code_url` (String, 可选)

- **类型**: 字符串或 `null`
- **必填**: 否
- **说明**: 兑换码领取页面的链接地址
- **示例值**: 
  - `"http://xhslink.com/o/34D18MYRkBh"` （小红书链接）
  - `"https://your-domain.com/redeem_code_page.html?product=basic"` （自定义链接）
  - `null` 或省略字段（暂时没有兑换码）

## 实现示例

### Python Flask 示例

```python
from flask import Flask, jsonify, request
from datetime import datetime

app = Flask(__name__)

# 配置兑换码链接（可以从配置文件或数据库读取）
REDEEM_CODE_URL = "http://xhslink.com/o/34D18MYRkBh"  # 或者设置为 None

@app.route('/api/version', methods=['GET'])
def check_version():
    client_version = request.args.get('client_version', '')
    device_id = request.args.get('device_id', '')
    
    # ... 其他逻辑 ...
    
    # 构建响应
    response = {
        "service": "公考题库分析服务",
        "status": "online",
        "success": True,
        "timestamp": datetime.now().isoformat(),
        "update": {
            "download_url": "/api/apk/download",
            "latest_version": "2.0.0",
            "release_notes": "",
            "required": False
        },
        "version": {
            "api_version": "2.0",
            "app_version": "2.0.0",
            # ... 其他版本信息 ...
        },
        "pro": {
            # ... Pro信息 ...
        },
        "free_quota": {
            # ... 免费额度信息 ...
        }
    }
    
    # 添加兑换码链接（如果配置了）
    if REDEEM_CODE_URL:
        response["redeem_code_url"] = REDEEM_CODE_URL
    # 如果不配置，就不添加这个字段（或者设为 null）
    
    return jsonify(response)
```

### 动态配置（推荐）

可以从配置文件、数据库或环境变量中读取：

```python
import os

# 从环境变量读取（推荐用于生产环境）
REDEEM_CODE_URL = os.getenv('REDEEM_CODE_URL', None)

# 或从配置文件读取
# REDEEM_CODE_URL = config.get('redeem_code_url', None)

@app.route('/api/version', methods=['GET'])
def check_version():
    # ... 其他逻辑 ...
    
    response = {
        # ... 其他字段 ...
    }
    
    # 只有在配置了链接时才添加
    redeem_code_url = REDEEM_CODE_URL  # 或从数据库查询
    if redeem_code_url:
        response["redeem_code_url"] = redeem_code_url
    
    return jsonify(response)
```

### 数据库配置示例

如果需要从数据库动态读取：

```python
from models import Config  # 假设有一个配置表

@app.route('/api/version', methods=['GET'])
def check_version():
    # ... 其他逻辑 ...
    
    response = {
        # ... 其他字段 ...
    }
    
    # 从数据库读取兑换码链接配置
    config = Config.query.filter_by(key='redeem_code_url').first()
    if config and config.value:
        response["redeem_code_url"] = config.value
    
    return jsonify(response)
```

## 行为说明

### 1. 未配置链接（初始状态）

如果后端**不返回** `redeem_code_url` 字段，或返回 `null`：

**额度用尽弹窗：**
- 弹窗只显示一个"知道了"按钮
- 用户点击后关闭弹窗

**"如何领取兑换码"页面：**
- 如果用户手动进入该页面，会显示：
  - 说明文字："目前暂时还没有兑换码，请稍后再试。"
  - 隐藏"前往领取兑换码"按钮
  - 显示"前往激活 Pro 服务"按钮（用户可以直接输入兑换码）

### 2. 已配置链接

如果后端返回了 `redeem_code_url`（例如：`"http://xhslink.com/o/34D18MYRkBh"`）：

**额度用尽弹窗：**
- 弹窗显示两个按钮：
  - **"如何领取兑换码"**按钮（点击跳转到"如何领取兑换码"页面）
  - "知道了"按钮（点击关闭弹窗）

**"如何领取兑换码"页面：**
- 说明文字："兑换码可以通过以下方式领取：1. 点击下方按钮前往领取页面..."
  - **显示"前往领取兑换码"按钮**（点击后跳转到小红书或浏览器）
  - 显示"前往激活 Pro 服务"按钮（用户获取兑换码后可以激活）

### 3. 链接更新

- 链接可以随时更新，应用会在下次版本检查时自动获取最新链接
- 应用会将链接保存在本地，即使后续API不返回，用户也能看到之前保存的链接（直到应用重启或清除数据）
- **弹窗行为会立即响应**：如果之前没有链接（只显示"知道了"），配置链接后下次额度用尽时会显示"如何领取兑换码"按钮

## 测试建议

### 1. 测试未配置链接

```bash
# 确保响应中不包含 redeem_code_url 字段
curl "http://your-api.com/api/version?client_version=2.0.0&device_id=test123"
```

期望响应：
```json
{
  "success": true,
  // ... 其他字段，但没有 redeem_code_url ...
}
```

应用行为：
- **弹窗**：只显示"知道了"按钮
- **"如何领取兑换码"页面**：显示"暂时还没有兑换码"提示

### 2. 测试已配置链接

```bash
# 配置链接后
curl "http://your-api.com/api/version?client_version=2.0.0&device_id=test123"
```

期望响应：
```json
{
  "success": true,
  "redeem_code_url": "http://xhslink.com/o/34D18MYRkBh",
  // ... 其他字段 ...
}
```

应用行为：
- **弹窗**：显示"如何领取兑换码"和"知道了"两个按钮
- **"如何领取兑换码"页面**：显示"前往领取兑换码"按钮

### 3. 测试链接更新

- 先返回一个链接
- 等待应用获取
- 更新为另一个链接
- 等待下次版本检查
- 验证应用是否更新了链接

## 注意事项

1. **字段是可选的**：如果暂时没有兑换码，可以不返回这个字段，应用会正常显示"暂时还没有兑换码"的提示。

2. **链接格式**：可以是任何有效的URL：
   - HTTP/HTTPS链接
   - 小红书短链接（`http://xhslink.com/...`）
   - 自定义域名链接

3. **动态更新**：链接可以在后台随时更新，应用会在下次版本检查（应用启动或手动检查）时自动获取最新链接。

4. **向后兼容**：如果不添加这个字段，旧版本应用不会受到影响（字段是可选的）。

5. **安全性**：建议使用HTTPS链接，保护用户隐私。

## 配置方式建议

### 方案一：环境变量（推荐用于生产）

```bash
# .env 文件
REDEEM_CODE_URL=http://xhslink.com/o/34D18MYRkBh
```

```python
import os
REDEEM_CODE_URL = os.getenv('REDEEM_CODE_URL', None)
```

### 方案二：配置文件

```python
# config.py
REDEEM_CODE_URL = "http://xhslink.com/o/34D18MYRkBh"  # 可以随时修改
```

### 方案三：数据库配置表（推荐用于需要频繁更新）

```sql
CREATE TABLE app_config (
    key VARCHAR(50) PRIMARY KEY,
    value TEXT,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_config (key, value) VALUES ('redeem_code_url', 'http://xhslink.com/o/34D18MYRkBh');
```

## 完整示例代码

```python
from flask import Flask, jsonify, request
from datetime import datetime
import os

app = Flask(__name__)

# 从环境变量读取兑换码链接（如果没有配置则为 None）
REDEEM_CODE_URL = os.getenv('REDEEM_CODE_URL', None)

@app.route('/api/version', methods=['GET'])
def check_version():
    client_version = request.args.get('client_version', '')
    device_id = request.args.get('device_id', '')
    
    # 构建基础响应
    response = {
        "service": "公考题库分析服务",
        "status": "online",
        "success": True,
        "timestamp": datetime.now().isoformat(),
        "update": {
            "download_url": "/api/apk/download",
            "latest_version": "2.0.0",
            "release_notes": "",
            "required": False
        },
        "version": {
            "api_version": "2.0",
            "app_version": "2.0.0",
            "build_time": datetime.now().isoformat(),
            "flask_version": "3.0.0",
            "platform": "Linux",
            "platform_version": "5.4.0",
            "python_version": "3.9.0"
        }
    }
    
    # 添加Pro信息（如果有）
    # ... 查询用户Pro状态 ...
    
    # 添加免费额度信息（如果有）
    # ... 查询用户免费额度 ...
    
    # 添加兑换码链接（如果配置了）
    if REDEEM_CODE_URL:
        response["redeem_code_url"] = REDEEM_CODE_URL
    
    return jsonify(response)

if __name__ == '__main__':
    app.run(debug=True)
```

## 总结

后端只需要做一件事：

**在 `/api/version` 接口的响应JSON中添加一个可选字段 `redeem_code_url`**

- ✅ 如果暂时没有兑换码：**不返回这个字段**（或返回 `null`）
- ✅ 如果配置了兑换码链接：**返回链接字符串**（例如：`"http://xhslink.com/o/34D18MYRkBh"`）

应用会自动处理两种情况，无需额外配置！

