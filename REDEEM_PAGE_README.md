# 兑换码领取网页使用说明

## 概述

这是一个兑换码领取网页，用户在其他平台（如淘宝、小红书等）购买兑换码后，可以通过此网页领取对应的兑换码。

## 功能特性

- ✅ 支持多个商品页面（通过URL参数区分）
- ✅ 自动生成设备ID
- ✅ 美观的响应式设计（适配手机和电脑）
- ✅ 一键复制兑换码
- ✅ 与后端API集成

## 使用方法

### 1. 部署网页

将 `redeem_code_page.html` 上传到你的服务器，确保可以通过HTTP访问。

### 2. 配置商品信息

在 `redeem_code_page.html` 中修改 `PRODUCTS` 对象，添加你的商品信息：

```javascript
const PRODUCTS = {
    'basic': {
        name: '基础档 Pro 服务',
        desc: '60次/月手写擦除，适合轻度使用',
        price: '¥4/月',
        productId: 'basic'
    },
    'standard': {
        name: '标准档 Pro 服务',
        desc: '140次/月手写擦除，适合日常使用',
        price: '¥8/月',
        productId: 'standard'
    },
    'advanced': {
        name: '高级档 Pro 服务',
        desc: '350次/月手写擦除，适合重度使用',
        price: '¥20/月',
        productId: 'advanced'
    },
    // 添加更多商品...
    'custom1': {
        name: '自定义商品1',
        desc: '商品描述',
        price: '¥XX/月',
        productId: 'custom1'
    }
};
```

### 3. 配置API地址

修改 `API_BASE_URL` 为你的后端API地址：

```javascript
const API_BASE_URL = 'http://fupan.jnhongniang.xyz';
```

### 4. 生成商品链接

为每个商品生成不同的链接：

- **基础档**: `https://your-domain.com/redeem_code_page.html?product=basic`
- **标准档**: `https://your-domain.com/redeem_code_page.html?product=standard`
- **高级档**: `https://your-domain.com/redeem_code_page.html?product=advanced`

### 5. 在其他平台使用

在其他平台（淘宝、小红书等）上架商品时，将对应的链接发送给用户。

## 后端API要求

网页需要调用后端API来领取兑换码。需要实现以下接口：

### 接口：领取兑换码

**URL**: `/api/redeem/claim`

**方法**: `POST`

**请求体**:
```json
{
    "device_id": "web_xxxxx",
    "product_id": "basic"
}
```

**响应（成功）**:
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

**响应（失败）**:
```json
{
    "success": false,
    "error": "PRODUCT_NOT_FOUND",
    "message": "商品不存在"
}
```

### 错误码

- `PRODUCT_NOT_FOUND`: 商品不存在
- `OUT_OF_STOCK`: 库存不足
- `DEVICE_LIMIT_EXCEEDED`: 设备领取次数超限
- `SERVER_ERROR`: 服务器错误

## 自定义样式

你可以修改CSS来自定义网页样式：

- 修改 `background` 渐变颜色
- 修改 `logo` 图标（当前是📚）
- 修改按钮颜色和样式
- 调整字体大小和间距

## 安全建议

1. **设备ID限制**: 后端应该限制每个设备ID的领取次数，防止滥用
2. **商品验证**: 后端应该验证 `product_id` 是否有效
3. **库存管理**: 后端应该管理每个商品的库存
4. **HTTPS**: 建议使用HTTPS部署，保护用户数据
5. **防刷机制**: 可以添加验证码或频率限制

## 示例链接

假设你的网页部署在 `https://redeem.example.com/redeem_code_page.html`：

- 基础档: `https://redeem.example.com/redeem_code_page.html?product=basic`
- 标准档: `https://redeem.example.com/redeem_code_page.html?product=standard`
- 高级档: `https://redeem.example.com/redeem_code_page.html?product=advanced`

## 测试

1. 打开网页，检查商品信息是否正确显示
2. 点击"领取兑换码"按钮
3. 检查是否能成功调用API并显示兑换码
4. 测试复制功能是否正常
5. 测试不同商品参数是否正确显示

## 注意事项

1. 确保后端API已实现 `/api/redeem/claim` 接口
2. 确保API支持CORS（跨域请求）
3. 设备ID存储在localStorage中，清除浏览器数据会重新生成
4. 建议为每个商品设置独立的库存和限制

## 后续优化建议

1. 添加验证码（防止机器人）
2. 添加领取记录查询
3. 添加使用说明和App下载链接
4. 添加客服联系方式
5. 添加常见问题（FAQ）

