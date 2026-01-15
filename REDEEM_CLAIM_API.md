# 兑换码领取 API 接口文档（已更新：基于订单号）

## 概述

此接口用于根据订单号领取兑换码。用户在其他平台购买商品后，使用订单号在网页上领取对应的兑换码。

**重要更新**：此接口已改为基于订单号领取，确保订单号真实且只能领取一次。

详细实现请参考：`ORDER_BASED_REDEMPTION_API.md`

---

## 接口：领取兑换码

### 接口信息

- **URL**: `/api/redeem/claim`
- **方法**: `POST`
- **Content-Type**: `application/json`

### 请求参数

```json
{
  "device_id": "web_abc123xyz456",
  "product_id": "basic"
}
```

### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `device_id` | string | 是 | 设备ID（网页端自动生成） |
| `product_id` | string | 是 | 商品ID（如：basic, standard, advanced） |

### 商品ID说明

| 商品ID | 说明 | 对应服务 |
|--------|------|---------|
| `basic` | 基础档 | 60次/月手写擦除 |
| `standard` | 标准档 | 140次/月手写擦除 |
| `advanced` | 高级档 | 350次/月手写擦除 |

### 响应（成功）

**状态码**: `200 OK`

```json
{
  "success": true,
  "message": "兑换码领取成功",
  "data": {
    "code": "MHYDET-C9GQHG-X4CD",
    "expires_at": "2026-01-15T23:59:59",
    "duration_days": 30
  }
}
```

### 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 是否成功 |
| `message` | string | 消息 |
| `data.code` | string | 兑换码 |
| `data.expires_at` | string | 过期时间（ISO 8601格式） |
| `data.duration_days` | integer | 服务时长（天） |

### 响应（失败）

**状态码**: `400 Bad Request` 或 `500 Internal Server Error`

```json
{
  "success": false,
  "error": "OUT_OF_STOCK",
  "message": "库存不足，请稍后再试"
}
```

### 错误码

| 错误码 | 状态码 | 说明 |
|--------|--------|------|
| `INVALID_REQUEST` | 400 | 请求数据无效（缺少必需字段） |
| `PRODUCT_NOT_FOUND` | 400 | 商品不存在 |
| `OUT_OF_STOCK` | 400 | 库存不足 |
| `DEVICE_LIMIT_EXCEEDED` | 400 | 设备领取次数超限（建议限制：每个设备ID每天最多领取3次） |
| `SERVER_ERROR` | 500 | 服务器错误 |

---

## 实现要点

### 1. 商品管理

- 每个商品ID对应一个商品配置
- 商品配置包括：名称、描述、价格、库存、服务时长等
- 建议在数据库中存储商品信息

### 2. 库存管理

- 每个商品应该有独立的库存
- 领取兑换码时减少库存
- 库存为0时返回 `OUT_OF_STOCK` 错误

### 3. 设备限制

- 限制每个设备ID的领取频率（如：每天最多3次）
- 防止恶意刷取兑换码
- 可以使用Redis记录设备领取次数

### 4. 兑换码生成

- 生成格式：`MHYDET-XXXXXX-XXXXXX-XXX`
- 确保唯一性
- 记录到数据库，关联商品ID和设备ID

### 5. 数据库设计建议

```sql
-- 商品表
CREATE TABLE products (
    id VARCHAR(50) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    price DECIMAL(10, 2),
    stock INT DEFAULT 0,
    duration_days INT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 兑换码表
CREATE TABLE redemption_codes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) UNIQUE NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    device_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP NULL,
    FOREIGN KEY (product_id) REFERENCES products(id)
);

-- 设备领取记录表（用于限制频率）
CREATE TABLE device_claims (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    device_id VARCHAR(100) NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_device_date (device_id, claimed_at)
);
```

### 6. 初始化商品数据

```sql
INSERT INTO products (id, name, description, price, stock, duration_days) VALUES
('basic', '基础档 Pro 服务', '60次/月手写擦除', 4.00, 1000, 30),
('standard', '标准档 Pro 服务', '140次/月手写擦除', 8.00, 1000, 30),
('advanced', '高级档 Pro 服务', '350次/月手写擦除', 20.00, 1000, 30);
```

### 7. 领取逻辑伪代码

```python
def claim_redemption_code(device_id, product_id):
    # 1. 验证商品是否存在
    product = get_product(product_id)
    if not product:
        return error("PRODUCT_NOT_FOUND")
    
    # 2. 检查库存
    if product.stock <= 0:
        return error("OUT_OF_STOCK")
    
    # 3. 检查设备领取频率（每天最多3次）
    today_claims = count_device_claims_today(device_id)
    if today_claims >= 3:
        return error("DEVICE_LIMIT_EXCEEDED")
    
    # 4. 生成兑换码
    code = generate_redemption_code()
    expires_at = calculate_expires_at(product.duration_days)
    
    # 5. 保存兑换码
    save_redemption_code(code, product_id, device_id, expires_at)
    
    # 6. 减少库存
    decrease_product_stock(product_id)
    
    # 7. 记录领取
    record_device_claim(device_id, product_id)
    
    # 8. 返回兑换码
    return success({
        "code": code,
        "expires_at": expires_at,
        "duration_days": product.duration_days
    })
```

---

## 安全建议

1. **频率限制**: 限制每个设备ID的领取频率
2. **IP限制**: 可以添加IP地址限制
3. **验证码**: 可以添加图形验证码或短信验证码
4. **日志记录**: 记录所有领取操作，便于审计
5. **异常检测**: 检测异常领取行为（如短时间内大量领取）

---

## 测试建议

### 测试用例

1. **正常领取**:
   - 使用有效的商品ID和设备ID
   - 验证返回的兑换码格式正确
   - 验证库存减少

2. **错误处理**:
   - 无效的商品ID
   - 库存不足
   - 设备领取次数超限

3. **边界情况**:
   - 库存为0
   - 设备ID为空
   - 商品ID不存在

---

## 联系信息

如有问题，请联系后端开发人员。

