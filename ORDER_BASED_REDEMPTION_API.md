# 基于订单号的兑换码领取 API 文档

## 概述

此接口用于根据订单号领取兑换码。用户在其他平台购买商品后，使用订单号在网页上领取对应的兑换码。

**核心要求**：
1. ✅ 订单号必须真实存在（与订单系统对接验证）
2. ✅ 同一个订单号只能领取一次
3. ✅ 订单号必须与商品ID匹配
4. ✅ 防止订单号被猜测或枚举

---

## 接口：根据订单号领取兑换码

### 接口信息

- **URL**: `/api/redeem/claim`
- **方法**: `POST`
- **Content-Type**: `application/json`

### 请求参数

```json
{
  "order_id": "TB20260108123456789",
  "product_id": "basic",
  "device_id": "web_abc123xyz456"
}
```

### 字段说明

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `order_id` | string | 是 | 订单号（从其他平台获取） |
| `product_id` | string | 是 | 商品ID（如：basic, standard, advanced） |
| `device_id` | string | 是 | 设备ID（网页端自动生成，用于防刷） |

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

### 响应（失败）

**状态码**: `400 Bad Request` 或 `500 Internal Server Error`

```json
{
  "success": false,
  "error": "ORDER_NOT_FOUND",
  "message": "订单号不存在或无效"
}
```

### 错误码

| 错误码 | 状态码 | 说明 |
|--------|--------|------|
| `INVALID_REQUEST` | 400 | 请求数据无效（缺少必需字段） |
| `ORDER_NOT_FOUND` | 400 | 订单号不存在或无效 |
| `ORDER_ALREADY_CLAIMED` | 400 | 订单号已被使用（已领取过） |
| `ORDER_PRODUCT_MISMATCH` | 400 | 订单号对应的商品与请求的商品不匹配 |
| `ORDER_EXPIRED` | 400 | 订单已过期（可选，如果订单有有效期） |
| `PRODUCT_NOT_FOUND` | 400 | 商品不存在 |
| `SERVER_ERROR` | 500 | 服务器错误 |

---

## 实现要点

### 1. 订单号验证

#### 方案一：与订单系统对接（推荐）

如果你的订单系统有API，可以调用验证：

```python
def verify_order(order_id, product_id):
    """
    验证订单号是否真实存在
    """
    # 调用订单系统API
    order_response = requests.get(
        f"{ORDER_SYSTEM_API}/api/orders/{order_id}",
        headers={"Authorization": f"Bearer {API_TOKEN}"}
    )
    
    if order_response.status_code != 200:
        return None  # 订单不存在
    
    order_data = order_response.json()
    
    # 验证订单状态
    if order_data.get('status') != 'paid':
        return None  # 订单未支付
    
    # 验证商品匹配
    if order_data.get('product_id') != product_id:
        return None  # 商品不匹配
    
    return order_data
```

#### 方案二：维护订单数据库

如果订单系统无法对接，可以维护一个订单数据库：

```sql
CREATE TABLE orders (
    order_id VARCHAR(100) PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL,
    platform VARCHAR(50) NOT NULL,  -- 平台：taobao, xiaohongshu, etc.
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL,  -- pending, paid, cancelled
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,  -- 订单有效期（可选）
    INDEX idx_status (status),
    INDEX idx_product (product_id)
);
```

### 2. 防重复领取

使用数据库记录已领取的订单号：

```sql
CREATE TABLE order_redemptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(100) UNIQUE NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    device_id VARCHAR(100),
    redemption_code VARCHAR(50) NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order (order_id)
);
```

### 3. 完整领取逻辑

```python
def claim_redemption_code_by_order(order_id, product_id, device_id):
    """
    根据订单号领取兑换码
    """
    # 使用数据库事务
    with db.transaction():
        # 1. 检查订单号是否已领取（防止重复）
        existing_redemption = db.query(
            "SELECT * FROM order_redemptions WHERE order_id = ? FOR UPDATE",
            order_id
        ).first()
        
        if existing_redemption:
            return error("ORDER_ALREADY_CLAIMED", "该订单号已领取过兑换码")
        
        # 2. 验证订单号是否真实（与订单系统对接或查询订单数据库）
        order = verify_order(order_id, product_id)
        
        if not order:
            return error("ORDER_NOT_FOUND", "订单号不存在或无效")
        
        # 3. 验证订单状态
        if order.get('status') != 'paid':
            return error("ORDER_NOT_PAID", "订单未支付")
        
        # 4. 验证商品匹配
        if order.get('product_id') != product_id:
            return error("ORDER_PRODUCT_MISMATCH", "订单号对应的商品与请求的商品不匹配")
        
        # 5. 检查订单是否过期（可选）
        if order.get('expires_at') and order.get('expires_at') < now():
            return error("ORDER_EXPIRED", "订单已过期")
        
        # 6. 获取商品信息
        product = get_product(product_id)
        if not product:
            return error("PRODUCT_NOT_FOUND", "商品不存在")
        
        # 7. 生成兑换码
        code = generate_redemption_code()
        expires_at = calculate_expires_at(product.duration_days)
        
        # 8. 保存兑换码
        save_redemption_code(code, product_id, device_id, expires_at)
        
        # 9. 记录订单号已领取（关键：防止重复领取）
        db.execute(
            """
            INSERT INTO order_redemptions (order_id, product_id, device_id, redemption_code)
            VALUES (?, ?, ?, ?)
            """,
            order_id, product_id, device_id, code
        )
        
        # 10. 返回兑换码
        return success({
            "code": code,
            "expires_at": expires_at,
            "duration_days": product.duration_days
        })
```

### 4. 订单号安全性

#### 4.1 防止订单号被猜测

- **订单号格式**：使用足够长的随机字符串（如：`TB20260108123456789`）
- **验证频率限制**：限制每个IP/设备的验证次数
- **记录失败尝试**：记录无效订单号的验证尝试，检测异常行为

#### 4.2 订单号签名（可选，增强安全性）

如果订单系统支持，可以使用签名验证：

```python
import hmac
import hashlib

def verify_order_signature(order_id, product_id, signature):
    """
    验证订单号签名
    """
    # 使用共享密钥生成签名
    secret_key = "your_secret_key"
    message = f"{order_id}:{product_id}"
    expected_signature = hmac.new(
        secret_key.encode(),
        message.encode(),
        hashlib.sha256
    ).hexdigest()
    
    return hmac.compare_digest(signature, expected_signature)
```

#### 4.3 订单号格式验证

```python
def validate_order_format(order_id, platform):
    """
    验证订单号格式
    """
    # 不同平台的订单号格式不同
    patterns = {
        'taobao': r'^TB\d{13,}$',  # 淘宝订单号
        'xiaohongshu': r'^XHS\d{12,}$',  # 小红书订单号
        'douyin': r'^DY\d{12,}$',  # 抖音订单号
    }
    
    pattern = patterns.get(platform)
    if pattern and not re.match(pattern, order_id):
        return False
    
    return True
```

### 5. 数据库设计

```sql
-- 订单表（如果维护自己的订单数据库）
CREATE TABLE orders (
    order_id VARCHAR(100) PRIMARY KEY,
    product_id VARCHAR(50) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    paid_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NULL,
    INDEX idx_status (status),
    INDEX idx_product (product_id)
);

-- 订单领取记录表（防止重复领取）
CREATE TABLE order_redemptions (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(100) UNIQUE NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    device_id VARCHAR(100),
    redemption_code VARCHAR(50) NOT NULL,
    claimed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50),
    INDEX idx_order (order_id),
    INDEX idx_code (redemption_code)
);

-- 订单验证失败记录（用于检测异常）
CREATE TABLE order_verification_failures (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id VARCHAR(100) NOT NULL,
    product_id VARCHAR(50),
    device_id VARCHAR(100),
    ip_address VARCHAR(50),
    failure_reason VARCHAR(200),
    attempted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order (order_id),
    INDEX idx_ip (ip_address),
    INDEX idx_time (attempted_at)
);
```

### 6. 安全建议

1. **频率限制**：
   - 限制每个IP地址的验证频率（如：每分钟最多10次）
   - 限制每个设备ID的验证频率（如：每天最多20次）

2. **异常检测**：
   - 记录所有验证失败的订单号
   - 检测短时间内大量无效订单号验证（可能是暴力破解）
   - 自动封禁异常IP

3. **订单号验证**：
   - 与订单系统实时对接验证
   - 验证订单状态（必须是已支付）
   - 验证商品匹配

4. **日志记录**：
   - 记录所有领取操作
   - 记录订单号、设备ID、IP地址
   - 便于审计和问题排查

### 7. 完整实现示例（Python/Flask）

```python
from flask import Flask, request, jsonify
from sqlalchemy import create_engine, text
from datetime import datetime, timedelta
import requests
import redis

app = Flask(__name__)
engine = create_engine('mysql://user:pass@localhost/db')
redis_client = redis.Redis()

# 订单系统API配置
ORDER_SYSTEM_API = "https://order-system.example.com"
ORDER_API_TOKEN = "your_api_token"

@app.route('/api/redeem/claim', methods=['POST'])
def claim_by_order():
    data = request.json
    order_id = data.get('order_id')
    product_id = data.get('product_id')
    device_id = data.get('device_id')
    ip_address = request.remote_addr
    
    # 1. 参数验证
    if not order_id or not product_id or not device_id:
        return jsonify({
            'success': False,
            'error': 'INVALID_REQUEST',
            'message': '缺少必需参数'
        }), 400
    
    # 2. 频率限制
    rate_limit_key = f"claim_rate_limit:{ip_address}"
    attempts = redis_client.get(rate_limit_key)
    if attempts and int(attempts) >= 10:
        return jsonify({
            'success': False,
            'error': 'RATE_LIMIT_EXCEEDED',
            'message': '请求过于频繁，请稍后再试'
        }), 429
    
    # 使用数据库事务
    with engine.begin() as conn:
        # 3. 检查订单号是否已领取
        result = conn.execute(
            text("""
                SELECT * FROM order_redemptions 
                WHERE order_id = :order_id 
                FOR UPDATE
            """),
            {'order_id': order_id}
        )
        existing = result.fetchone()
        
        if existing:
            return jsonify({
                'success': False,
                'error': 'ORDER_ALREADY_CLAIMED',
                'message': '该订单号已领取过兑换码'
            }), 400
        
        # 4. 验证订单号（与订单系统对接）
        try:
            order_response = requests.get(
                f"{ORDER_SYSTEM_API}/api/orders/{order_id}",
                headers={"Authorization": f"Bearer {ORDER_API_TOKEN}"},
                timeout=5
            )
            
            if order_response.status_code != 200:
                # 记录验证失败
                conn.execute(
                    text("""
                        INSERT INTO order_verification_failures 
                        (order_id, product_id, device_id, ip_address, failure_reason)
                        VALUES (:order_id, :product_id, :device_id, :ip, :reason)
                    """),
                    {
                        'order_id': order_id,
                        'product_id': product_id,
                        'device_id': device_id,
                        'ip': ip_address,
                        'reason': 'ORDER_NOT_FOUND'
                    }
                )
                
                return jsonify({
                    'success': False,
                    'error': 'ORDER_NOT_FOUND',
                    'message': '订单号不存在或无效'
                }), 400
            
            order_data = order_response.json()
            
            # 5. 验证订单状态
            if order_data.get('status') != 'paid':
                return jsonify({
                    'success': False,
                    'error': 'ORDER_NOT_PAID',
                    'message': '订单未支付'
                }), 400
            
            # 6. 验证商品匹配
            if order_data.get('product_id') != product_id:
                return jsonify({
                    'success': False,
                    'error': 'ORDER_PRODUCT_MISMATCH',
                    'message': '订单号对应的商品与请求的商品不匹配'
                }), 400
            
        except requests.RequestException as e:
            return jsonify({
                'success': False,
                'error': 'SERVER_ERROR',
                'message': '订单验证服务暂时不可用，请稍后重试'
            }), 500
        
        # 7. 获取商品信息
        product_result = conn.execute(
            text("SELECT * FROM products WHERE id = :product_id"),
            {'product_id': product_id}
        )
        product = product_result.fetchone()
        
        if not product:
            return jsonify({
                'success': False,
                'error': 'PRODUCT_NOT_FOUND',
                'message': '商品不存在'
            }), 400
        
        # 8. 生成兑换码
        code = generate_redemption_code()
        expires_at = datetime.now() + timedelta(days=product.duration_days)
        
        # 9. 保存兑换码
        conn.execute(
            text("""
                INSERT INTO redemption_codes 
                (code, product_id, device_id, expires_at)
                VALUES (:code, :product_id, :device_id, :expires_at)
            """),
            {
                'code': code,
                'product_id': product_id,
                'device_id': device_id,
                'expires_at': expires_at
            }
        )
        
        # 10. 记录订单号已领取（关键：防止重复）
        conn.execute(
            text("""
                INSERT INTO order_redemptions 
                (order_id, product_id, device_id, redemption_code, ip_address)
                VALUES (:order_id, :product_id, :device_id, :code, :ip)
            """),
            {
                'order_id': order_id,
                'product_id': product_id,
                'device_id': device_id,
                'code': code,
                'ip': ip_address
            }
        )
        
        # 11. 更新频率限制
        redis_client.incr(rate_limit_key)
        redis_client.expire(rate_limit_key, 60)  # 1分钟过期
        
        # 12. 返回成功
        return jsonify({
            'success': True,
            'message': '兑换码领取成功',
            'data': {
                'code': code,
                'expires_at': expires_at.isoformat(),
                'duration_days': product.duration_days
            }
        })

def generate_redemption_code():
    """生成兑换码"""
    import random
    import string
    
    prefix = "MHYDET"
    part1 = ''.join(random.choices(string.ascii_uppercase + string.digits, k=6))
    part2 = ''.join(random.choices(string.ascii_uppercase + string.digits, k=6))
    part3 = ''.join(random.choices(string.ascii_uppercase + string.digits, k=3))
    
    return f"{prefix}-{part1}-{part2}-{part3}"

if __name__ == '__main__':
    app.run(debug=True)
```

---

## 测试建议

### 测试用例

1. **正常领取**：
   - 使用有效的订单号和正确的商品ID
   - 验证返回的兑换码格式正确
   - 验证订单号被标记为已领取

2. **重复领取**：
   - 使用同一个订单号领取两次
   - 第二次应该返回 `ORDER_ALREADY_CLAIMED` 错误

3. **无效订单号**：
   - 使用不存在的订单号
   - 应该返回 `ORDER_NOT_FOUND` 错误

4. **商品不匹配**：
   - 使用订单号A（对应商品basic）请求商品standard
   - 应该返回 `ORDER_PRODUCT_MISMATCH` 错误

5. **未支付订单**：
   - 使用未支付的订单号
   - 应该返回 `ORDER_NOT_PAID` 错误

---

## 总结

确保订单号真实且只能领取一次的关键：

1. ✅ **订单号验证**：与订单系统对接，验证订单真实性
2. ✅ **防重复领取**：使用数据库记录已领取的订单号
3. ✅ **商品匹配**：验证订单号对应的商品与请求的商品一致
4. ✅ **事务保护**：使用数据库事务确保操作的原子性
5. ✅ **频率限制**：限制验证频率，防止暴力破解
6. ✅ **异常检测**：记录失败尝试，检测异常行为

按照这个方案实现，可以确保：
- ✅ 订单号必须是真实的（通过订单系统验证）
- ✅ 同一个订单号只能领取一次
- ✅ 防止订单号被猜测或枚举

