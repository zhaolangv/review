# 兑换码"只能使用一次"实现指南

## 核心原理

确保一个兑换码只能使用一次，需要解决**并发问题**。即使多个用户同时激活同一个兑换码，也应该只有一个能成功。

## 实现方案

### 方案一：数据库事务 + 行锁定（推荐）

这是最可靠的方法，使用数据库的ACID特性来保证。

#### 1. 数据库表结构

```sql
CREATE TABLE redemption_codes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) UNIQUE NOT NULL,
    product_id VARCHAR(50) NOT NULL,
    duration_days INT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    
    -- 关键字段：使用状态
    used BOOLEAN DEFAULT FALSE NOT NULL,
    used_at TIMESTAMP NULL,
    used_by_device_id VARCHAR(100) NULL,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_code (code),
    INDEX idx_used (used)
);
```

#### 2. 激活逻辑（Python示例）

```python
def activate_code(code, device_id):
    """激活兑换码（确保只能使用一次）"""
    
    # 使用数据库事务
    with db.transaction():
        # 1. 查询并锁定行（SELECT FOR UPDATE）
        redemption_code = db.query(
            "SELECT * FROM redemption_codes WHERE code = ? FOR UPDATE",
            code
        ).first()
        
        if not redemption_code:
            return error("INVALID_CODE")
        
        # 2. 检查是否已使用
        if redemption_code.used:
            return error("CODE_ALREADY_USED")
        
        # 3. 检查是否过期
        if redemption_code.expires_at < now():
            return error("CODE_EXPIRED")
        
        # 4. 原子更新（关键：WHERE条件确保只有未使用的才能更新）
        rows_affected = db.execute(
            """
            UPDATE redemption_codes 
            SET used = TRUE, 
                used_at = NOW(), 
                used_by_device_id = ?
            WHERE code = ? AND used = FALSE
            """,
            device_id, code
        ).rowcount
        
        # 5. 检查更新是否成功
        if rows_affected == 0:
            # 说明兑换码已被其他请求使用
            return error("CODE_ALREADY_USED")
        
        # 6. 激活Pro服务
        activate_pro_service(device_id, redemption_code.duration_days)
        
        return success()
```

#### 3. 关键点说明

**SELECT FOR UPDATE**：
- 锁定查询到的行，其他事务无法修改
- 直到当前事务提交或回滚才释放锁

**条件更新**：
- `WHERE code = ? AND used = FALSE`
- 只有未使用的兑换码才能被更新
- 如果 `rowcount == 0`，说明已被使用

**事务保护**：
- 所有操作在一个事务中
- 要么全部成功，要么全部回滚

### 方案二：使用Redis分布式锁（适用于分布式系统）

如果使用Redis，可以使用分布式锁：

```python
import redis
import time

redis_client = redis.Redis()

def activate_code_with_lock(code, device_id):
    """使用Redis锁确保只能使用一次"""
    
    lock_key = f"redemption_code_lock:{code}"
    lock_timeout = 5  # 5秒超时
    
    # 1. 获取锁
    lock_acquired = redis_client.set(
        lock_key, 
        device_id, 
        nx=True,  # 只在不存在时设置
        ex=lock_timeout  # 过期时间
    )
    
    if not lock_acquired:
        return error("CODE_ALREADY_USED", "兑换码正在被使用，请稍后重试")
    
    try:
        # 2. 查询兑换码
        redemption_code = db.query(
            "SELECT * FROM redemption_codes WHERE code = ?",
            code
        ).first()
        
        if not redemption_code:
            return error("INVALID_CODE")
        
        if redemption_code.used:
            return error("CODE_ALREADY_USED")
        
        # 3. 更新状态
        db.execute(
            "UPDATE redemption_codes SET used = TRUE, used_at = NOW() WHERE code = ?",
            code
        )
        
        # 4. 激活服务
        activate_pro_service(device_id, redemption_code.duration_days)
        
        return success()
        
    finally:
        # 5. 释放锁
        redis_client.delete(lock_key)
```

## 测试验证

### 并发测试脚本

```python
import threading
import requests
import time

def test_concurrent_activation():
    """测试并发激活"""
    code = "TEST-CODE-123"
    results = []
    errors = []
    
    def activate():
        try:
            response = requests.post('http://localhost:5000/api/redeem/activate', json={
                'code': code,
                'device_id': f'device_{threading.current_thread().ident}'
            }, timeout=10)
            results.append(response.json())
        except Exception as e:
            errors.append(str(e))
    
    # 创建20个线程同时激活
    threads = []
    for i in range(20):
        t = threading.Thread(target=activate)
        threads.append(t)
    
    # 同时启动所有线程
    start_time = time.time()
    for t in threads:
        t.start()
    
    # 等待所有线程完成
    for t in threads:
        t.join()
    
    end_time = time.time()
    
    # 统计结果
    success_count = sum(1 for r in results if r.get('success'))
    failed_count = len(results) - success_count
    
    print(f"总请求数: {len(results)}")
    print(f"成功数: {success_count}")
    print(f"失败数: {failed_count}")
    print(f"耗时: {end_time - start_time:.2f}秒")
    print(f"错误: {errors}")
    
    # 验证：应该只有1个成功
    assert success_count == 1, f"应该有1个成功，实际有{success_count}个"
    print("✅ 测试通过：兑换码只能使用一次")

if __name__ == '__main__':
    test_concurrent_activation()
```

## 常见问题

### Q1: 为什么需要 SELECT FOR UPDATE？

**A**: 防止并发问题。两个请求同时查询到"未使用"状态，然后都尝试激活。

### Q2: 为什么需要条件更新（WHERE used = FALSE）？

**A**: 双重保险。即使有请求绕过了SELECT FOR UPDATE，条件更新也能防止重复激活。

### Q3: 如果 rowcount == 0 怎么办？

**A**: 说明兑换码已被使用，返回 `CODE_ALREADY_USED` 错误。

### Q4: 性能影响？

**A**: SELECT FOR UPDATE 会锁定行，但通常激活操作很快（<100ms），影响很小。

## 总结

确保兑换码只能使用一次的关键：

1. ✅ **数据库字段**：`used`、`used_at`、`used_by_device_id`
2. ✅ **事务保护**：所有操作在一个事务中
3. ✅ **行锁定**：`SELECT FOR UPDATE`
4. ✅ **条件更新**：`WHERE used = FALSE`
5. ✅ **结果检查**：验证 `rowcount > 0`

按照这个方案实现，即使有100个并发请求，也只有一个能成功激活。

