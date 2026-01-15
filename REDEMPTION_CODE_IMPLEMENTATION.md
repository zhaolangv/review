# 兑换码系统实现说明

## 概述

这是一个完整的兑换码系统，允许用户通过兑换码激活 Pro 服务。系统包括前端实现和后端 API 设计文档。

## 已实现的功能

### 1. 前端实现

#### 文件列表
- `RedemptionCodeActivity.kt` - 兑换码激活页面
- `ProManager.kt` - Pro 服务状态管理器
- `RedemptionApiService.kt` - 兑换码 API 接口定义
- `activity_redemption_code.xml` - 兑换码页面布局
- `main_menu.xml` - 主菜单（包含"激活 Pro"选项）

#### 功能特性
- ✅ 兑换码输入和格式化（自动添加连字符）
- ✅ 兑换码验证（检查是否有效）
- ✅ 兑换码激活（激活 Pro 服务）
- ✅ Pro 状态显示（显示激活状态、到期时间、剩余天数）
- ✅ 本地状态管理（使用 SharedPreferences 存储）
- ✅ 自动过期检查（检查 Pro 服务是否过期）

### 2. 后端 API 设计

详见 `REDEMPTION_CODE_API.md` 文件，包含：
- 验证兑换码 API
- 激活兑换码 API
- 查询 Pro 状态 API

## 使用方法

### 用户端
1. 在应用主界面点击右上角菜单 → "激活 Pro"
2. 输入兑换码（格式：ABC123-XYZ456-789）
3. 点击"验证兑换码"检查是否有效
4. 点击"激活 Pro 服务"完成激活

### 开发者端

#### 检查 Pro 状态
```kotlin
import com.gongkao.cuotifupan.util.ProManager

// 检查是否为 Pro 用户
if (ProManager.isPro(context)) {
    // Pro 功能
} else {
    // 普通功能或显示升级提示
}

// 获取剩余天数
val daysRemaining = ProManager.getDaysRemaining(context)

// 获取过期时间（格式化）
val expiresAt = ProManager.getExpiresAtFormatted(context)
```

#### 在功能中限制 Pro
```kotlin
// 示例：手写擦除功能仅限 Pro 用户
if (!ProManager.isPro(this)) {
    Toast.makeText(this, "此功能需要 Pro 服务，请先激活", Toast.LENGTH_SHORT).show()
    // 可选：跳转到激活页面
    val intent = Intent(this, RedemptionCodeActivity::class.java)
    startActivity(intent)
    return
}
// 继续执行 Pro 功能
```

## 后端实现建议

### 数据库设计

#### 兑换码表 (redemption_codes)
```sql
CREATE TABLE redemption_codes (
    id VARCHAR(50) PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    duration_days INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    used_by_device_id VARCHAR(100)
);
```

#### Pro 激活记录表 (pro_activations)
```sql
CREATE TABLE pro_activations (
    id VARCHAR(50) PRIMARY KEY,
    device_id VARCHAR(100) UNIQUE NOT NULL,
    activated_at TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    redemption_code_id VARCHAR(50),
    FOREIGN KEY (redemption_code_id) REFERENCES redemption_codes(id)
);
```

### 后端实现要点

1. **生成兑换码**
   - 使用随机字符串生成（建议：18位，包含字母和数字）
   - 格式：每6位用连字符分隔（如：ABC123-XYZ456-789）
   - 保存到数据库，设置有效期

2. **验证兑换码**
   - 检查兑换码是否存在
   - 检查是否已使用
   - 检查是否过期
   - 返回有效期信息（天数）

3. **激活兑换码**
   - 再次验证兑换码（防止并发）
   - 标记兑换码为已使用
   - 创建或更新 Pro 激活记录
   - 返回激活信息

4. **查询 Pro 状态**
   - 根据 device_id 查询激活记录
   - 检查是否过期
   - 返回状态信息

## 安全建议

1. **兑换码生成**
   - 使用加密安全的随机数生成器
   - 避免使用可预测的模式
   - 定期清理过期的兑换码

2. **防重复使用**
   - 使用数据库事务确保原子性
   - 在激活时再次检查状态
   - 记录使用时间和设备ID

3. **设备限制**（可选）
   - 可以限制每个设备只能使用一次兑换码
   - 或者允许同一设备使用多个兑换码（延长有效期）

4. **API 安全**
   - 使用 HTTPS
   - 添加请求频率限制
   - 验证设备ID格式

## 测试建议

1. **测试用例**
   - 有效兑换码激活
   - 无效兑换码验证
   - 已使用兑换码激活
   - 过期兑换码激活
   - Pro 状态查询
   - 过期检查

2. **边界情况**
   - 网络错误处理
   - 服务器错误处理
   - 并发激活（同一兑换码）
   - 时区问题（过期时间）

## 后续扩展

1. **Pro 功能限制**
   - 在需要的地方添加 Pro 检查
   - 显示升级提示
   - 提供试用功能

2. **统计功能**
   - 兑换码使用统计
   - Pro 用户统计
   - 收入统计

3. **管理后台**
   - 生成兑换码
   - 查看使用情况
   - 管理 Pro 用户

## 注意事项

1. **设备ID**
   - 当前使用 `VersionChecker.getDeviceId()` 获取设备ID
   - 确保设备ID稳定（不会因应用重装而改变）

2. **时间同步**
   - 确保服务器和客户端时间同步
   - 使用服务器时间作为标准

3. **数据备份**
   - Pro 状态存储在本地，建议定期同步到服务器
   - 可以在应用启动时检查服务器状态

