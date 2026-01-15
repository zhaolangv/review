# 设备ID说明

## 设备ID的来源

### 1. App端（Android应用）

App中的设备ID通过 `VersionChecker.getDeviceId()` 获取：

```kotlin
fun getDeviceId(): String {
    // 优先使用Android ID（系统唯一标识）
    Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ANDROID_ID
    ) ?: run {
        // 如果Android ID不可用，使用本地存储的UUID
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        var deviceId = prefs.getString("device_id", null)
        if (deviceId == null) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", deviceId).apply()
        }
        deviceId
    }
}
```

**特点**：
- 格式：Android ID（如：`9774d56d682e549c`）或 UUID（如：`550e8400-e29b-41d4-a716-446655440000`）
- 每个设备唯一
- 用于激活兑换码时标识设备

### 2. 网页端（兑换码领取页面）

网页中的设备ID是自动生成的：

```javascript
function generateDeviceId() {
    let deviceId = localStorage.getItem('deviceId');
    if (!deviceId) {
        // 生成格式：web_ + 随机字符串
        deviceId = 'web_' + 
            Math.random().toString(36).substring(2, 15) + 
            Math.random().toString(36).substring(2, 15) + 
            Date.now().toString(36);
        localStorage.setItem('deviceId', deviceId);
    }
    return deviceId;
}
```

**特点**：
- 格式：`web_xxxxx`（如：`web_abc123xyz456def789`）
- 存储在浏览器的localStorage中
- 用于网页端防刷（限制每个浏览器/设备的领取次数）

## 为什么两个设备ID不同？

这是**正常且合理的**，因为：

1. **网页端**：只是用来**领取兑换码**，设备ID用于防刷
2. **App端**：用来**激活兑换码**，设备ID用于标识真实设备

**流程**：
```
用户购买商品 
  ↓
在网页领取兑换码（使用网页设备ID：web_xxx）
  ↓
在App中输入兑换码（使用App设备ID：Android ID）
  ↓
激活成功
```

## 后端处理建议

### 方案一：设备ID仅用于防刷（推荐）

网页端的设备ID只用于限制领取频率，不需要与App设备ID匹配：

```python
# 领取兑换码时
def claim_redemption_code(device_id, product_id):
    # device_id 可以是 web_xxx 或 Android ID
    # 只用于限制领取频率
    check_device_limit(device_id)  # 每天最多3次
    # 生成兑换码，不绑定设备ID
    code = generate_code(product_id)
    return code

# 激活兑换码时
def activate_code(code, device_id):
    # device_id 是 App 中的 Android ID
    # 用于标识真实设备
    activate(code, device_id)
```

### 方案二：允许用户输入App设备ID（可选）

如果需要在网页端就绑定设备，可以让用户输入App中的设备ID：

1. 在App中显示设备ID（让用户复制）
2. 在网页中提供输入框
3. 用户输入后，领取的兑换码直接绑定到该设备

**优点**：可以提前绑定设备，防止兑换码被他人使用

**缺点**：用户体验较差，需要多一步操作

## 当前实现

当前网页代码使用的是**方案一**：
- 网页自动生成设备ID（格式：`web_xxx`）
- 仅用于防刷，不绑定设备
- 用户领取兑换码后，可以在任何设备上使用
- 激活时使用App中的设备ID

## 安全考虑

1. **网页端防刷**：限制每个设备ID的领取频率（如每天3次）
2. **兑换码激活**：激活时验证设备ID，防止兑换码被滥用
3. **IP限制**：可以添加IP地址限制，进一步防止刷取

## 总结

- **网页设备ID**：`web_xxx`，用于防刷，自动生成
- **App设备ID**：Android ID 或 UUID，用于标识真实设备
- **两者不同是正常的**，不需要匹配
- 兑换码领取和激活是两个独立的步骤

