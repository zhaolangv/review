# 兑换码 API 设计文档

## 1. 验证兑换码

### 请求
```
POST /api/redeem/verify
Content-Type: application/json

{
  "code": "ABC123-XYZ456-789",
  "device_id": "193fadfa6ad72dd5"
}
```

### 响应（成功）
```json
{
  "success": true,
  "message": "兑换码验证成功",
  "data": {
    "code": "ABC123-XYZ456-789",
    "valid": true,
    "expires_at": "2026-12-31T23:59:59",
    "duration_days": 365,
    "pro_status": {
      "is_pro": true,
      "activated_at": "2026-01-08T15:40:00",
      "expires_at": "2027-01-08T15:40:00"
    }
  }
}
```

### 响应（失败）
```json
{
  "success": false,
  "error": "INVALID_CODE",
  "message": "兑换码无效或已使用",
  "data": null
}
```

## 2. 激活兑换码

### 请求
```
POST /api/redeem/activate
Content-Type: application/json

{
  "code": "ABC123-XYZ456-789",
  "device_id": "193fadfa6ad72dd5"
}
```

### 响应（成功）
```json
{
  "success": true,
  "message": "Pro 服务已激活",
  "data": {
    "pro_status": {
      "is_pro": true,
      "activated_at": "2026-01-08T15:40:00",
      "expires_at": "2027-01-08T15:40:00"
    }
  }
}
```

### 响应（失败）
```json
{
  "success": false,
  "error": "CODE_ALREADY_USED",
  "message": "兑换码已被使用",
  "data": null
}
```

## 3. 查询 Pro 状态

### 请求
```
GET /api/pro/status?device_id=193fadfa6ad72dd5
```

### 响应
```json
{
  "success": true,
  "data": {
    "is_pro": true,
    "activated_at": "2026-01-08T15:40:00",
    "expires_at": "2027-01-08T15:40:00",
    "days_remaining": 365
  }
}
```

## 错误码说明

- `INVALID_CODE`: 兑换码不存在或格式错误
- `CODE_ALREADY_USED`: 兑换码已被使用
- `CODE_EXPIRED`: 兑换码已过期
- `DEVICE_LIMIT_EXCEEDED`: 该设备已使用过兑换码（如果需要限制）
- `SERVER_ERROR`: 服务器错误

---

## 实现要点：确保兑换码只能使用一次

### 1. 数据库设计

```sql
CREATE TABLE redemption_codes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) UNIQUE NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    duration_days INT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    
    -- 使用状态字段
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP NULL,
    used_by_device_id VARCHAR(100) NULL,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_code (code),
    INDEX idx_used (used),
    INDEX idx_expires (expires_at)
);
```

### 2. 验证兑换码逻辑

```python
def verify_code(code, device_id):
    """
    验证兑换码（不激活，只检查状态）
    """
    # 1. 查询兑换码
    redemption_code = db.query(
        "SELECT * FROM redemption_codes WHERE code = ?", 
        code
    ).first()
    
    if not redemption_code:
        return error("INVALID_CODE", "兑换码不存在")
    
    # 2. 检查是否已使用
    if redemption_code.used:
        return error("CODE_ALREADY_USED", "兑换码已被使用")
    
    # 3. 检查是否过期
    if redemption_code.expires_at < now():
        return error("CODE_EXPIRED", "兑换码已过期")
    
    # 4. 返回验证结果
    return success({
        "code": code,
        "valid": True,
        "expires_at": redemption_code.expires_at,
        "duration_days": redemption_code.duration_days
    })
```

### 3. 激活兑换码逻辑（关键：确保只能使用一次）

```python
def activate_code(code, device_id):
    """
    激活兑换码（使用事务确保原子性）
    """
    # 使用数据库事务确保操作的原子性
    with db.transaction():
        # 1. 查询兑换码（使用 SELECT FOR UPDATE 锁定行）
        redemption_code = db.query(
            "SELECT * FROM redemption_codes WHERE code = ? FOR UPDATE", 
            code
        ).first()
        
        if not redemption_code:
            return error("INVALID_CODE", "兑换码不存在")
        
        # 2. 检查是否已使用（双重检查）
        if redemption_code.used:
            return error("CODE_ALREADY_USED", "兑换码已被使用")
        
        # 3. 检查是否过期
        if redemption_code.expires_at < now():
            return error("CODE_EXPIRED", "兑换码已过期")
        
        # 4. 标记为已使用（关键步骤）
        db.execute(
            """
            UPDATE redemption_codes 
            SET used = TRUE, 
                used_at = NOW(), 
                used_by_device_id = ?
            WHERE code = ? AND used = FALSE
            """,
            device_id, code
        )
        
        # 5. 检查更新是否成功（防止并发问题）
        rows_affected = db.rowcount()
        if rows_affected == 0:
            # 如果更新失败，说明兑换码已被其他请求使用
            return error("CODE_ALREADY_USED", "兑换码已被使用")
        
        # 6. 激活Pro服务
        expires_at = calculate_expires_at(redemption_code.duration_days)
        activate_pro_service(device_id, expires_at)
        
        # 7. 返回成功
        return success({
            "pro_status": {
                "is_pro": True,
                "activated_at": now(),
                "expires_at": expires_at
            }
        })
```

### 4. 关键实现要点

#### 4.1 使用数据库事务

确保激活操作的原子性，防止并发问题：

```python
# 使用事务
with db.transaction():
    # 所有操作要么全部成功，要么全部回滚
    check_and_mark_used(code)
    activate_pro_service(device_id)
```

#### 4.2 使用 SELECT FOR UPDATE

在查询时锁定行，防止并发修改：

```sql
SELECT * FROM redemption_codes WHERE code = ? FOR UPDATE
```

#### 4.3 使用条件更新

在UPDATE时添加条件，确保只有未使用的兑换码才能被更新：

```sql
UPDATE redemption_codes 
SET used = TRUE, used_at = NOW(), used_by_device_id = ?
WHERE code = ? AND used = FALSE
```

如果 `rows_affected == 0`，说明兑换码已被使用。

#### 4.4 使用唯一索引

确保兑换码的唯一性：

```sql
CREATE UNIQUE INDEX idx_code ON redemption_codes(code);
```

### 5. 并发安全示例

假设两个用户同时激活同一个兑换码：

```
时间线：
T1: 用户A查询兑换码（未使用）
T2: 用户B查询兑换码（未使用）
T3: 用户A更新兑换码（used = TRUE）
T4: 用户B尝试更新兑换码（失败，因为 used = FALSE 条件不满足）
T5: 用户B收到 "CODE_ALREADY_USED" 错误
```

### 6. 完整实现示例（Python/Flask）

```python
from flask import Flask, request, jsonify
from sqlalchemy import create_engine, text
from datetime import datetime, timedelta

app = Flask(__name__)
engine = create_engine('mysql://user:pass@localhost/db')

@app.route('/api/redeem/activate', methods=['POST'])
def activate_code():
    data = request.json
    code = data.get('code')
    device_id = data.get('device_id')
    
    if not code or not device_id:
        return jsonify({
            'success': False,
            'error': 'INVALID_REQUEST',
            'message': '缺少必需参数'
        }), 400
    
    # 使用事务
    with engine.begin() as conn:
        # 1. 查询并锁定行
        result = conn.execute(
            text("""
                SELECT * FROM redemption_codes 
                WHERE code = :code 
                FOR UPDATE
            """),
            {'code': code}
        )
        redemption_code = result.fetchone()
        
        if not redemption_code:
            return jsonify({
                'success': False,
                'error': 'INVALID_CODE',
                'message': '兑换码不存在'
            }), 400
        
        # 2. 检查是否已使用
        if redemption_code.used:
            return jsonify({
                'success': False,
                'error': 'CODE_ALREADY_USED',
                'message': '兑换码已被使用'
            }), 400
        
        # 3. 检查是否过期
        if redemption_code.expires_at < datetime.now():
            return jsonify({
                'success': False,
                'error': 'CODE_EXPIRED',
                'message': '兑换码已过期'
            }), 400
        
        # 4. 标记为已使用（条件更新）
        result = conn.execute(
            text("""
                UPDATE redemption_codes 
                SET used = TRUE, 
                    used_at = NOW(), 
                    used_by_device_id = :device_id
                WHERE code = :code AND used = FALSE
            """),
            {'code': code, 'device_id': device_id}
        )
        
        # 5. 检查更新是否成功
        if result.rowcount == 0:
            return jsonify({
                'success': False,
                'error': 'CODE_ALREADY_USED',
                'message': '兑换码已被使用'
            }), 400
        
        # 6. 激活Pro服务
        expires_at = datetime.now() + timedelta(days=redemption_code.duration_days)
        conn.execute(
            text("""
                INSERT INTO pro_activations (device_id, activated_at, expires_at)
                VALUES (:device_id, NOW(), :expires_at)
                ON DUPLICATE KEY UPDATE 
                    activated_at = NOW(),
                    expires_at = :expires_at
            """),
            {'device_id': device_id, 'expires_at': expires_at}
        )
        
        # 7. 返回成功
        return jsonify({
            'success': True,
            'message': 'Pro 服务已激活',
            'data': {
                'pro_status': {
                    'is_pro': True,
                    'activated_at': datetime.now().isoformat(),
                    'expires_at': expires_at.isoformat()
                }
            }
        })
```

### 7. 测试建议

#### 7.1 并发测试

```python
import threading
import requests

def test_concurrent_activation():
    """测试并发激活同一个兑换码"""
    code = "TEST-CODE-123"
    results = []
    
    def activate():
        response = requests.post('/api/redeem/activate', json={
            'code': code,
            'device_id': f'device_{threading.current_thread().ident}'
        })
        results.append(response.json())
    
    # 创建10个线程同时激活
    threads = [threading.Thread(target=activate) for _ in range(10)]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    
    # 应该只有1个成功，9个失败
    success_count = sum(1 for r in results if r.get('success'))
    assert success_count == 1, f"应该有1个成功，实际有{success_count}个"
```

### 8. 总结

确保兑换码只能使用一次的关键点：

1. ✅ **数据库字段**：添加 `used`、`used_at`、`used_by_device_id` 字段
2. ✅ **事务保护**：使用数据库事务确保原子性
3. ✅ **行锁定**：使用 `SELECT FOR UPDATE` 锁定行
4. ✅ **条件更新**：UPDATE时添加 `WHERE used = FALSE` 条件
5. ✅ **检查结果**：检查 `rowcount` 确保更新成功
6. ✅ **双重检查**：在激活前再次检查 `used` 状态

这样即使有并发请求，也只有一个能成功激活。

