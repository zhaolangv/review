# 兑换码领取页面设置指南

## 商品配置

当前配置了三个档次的商品：

| 商品ID | 名称 | 价格 | 次数 | 描述 |
|--------|------|------|------|------|
| `basic` | 基础档 Pro 服务 | ¥5/月 | 60次/月 | 适合轻度使用 |
| `standard` | 标准档 Pro 服务 | ¥10/月 | 140次/月 | 适合日常使用 |
| `advanced` | 高级档 Pro 服务 | ¥25/月 | 350次/月 | 适合重度使用 |

## 页面访问方式

### 方式一：使用URL参数（推荐）

使用同一个HTML文件，通过URL参数区分不同商品：

#### 基础档页面链接
```
https://your-domain.com/redeem_code_page.html?product=basic
```

#### 标准档页面链接
```
https://your-domain.com/redeem_code_page.html?product=standard
```

#### 高级档页面链接
```
https://your-domain.com/redeem_code_page.html?product=advanced
```

### 方式二：创建独立页面（可选）

如果需要完全独立的页面，可以创建三个HTML文件：

1. **`redeem_basic.html`** - 基础档
2. **`redeem_standard.html`** - 标准档
3. **`redeem_advanced.html`** - 高级档

每个文件只需要修改 `PRODUCTS` 配置，只保留对应的商品。

## 在其他平台使用

### 淘宝/天猫

在商品详情页或自动回复中，发送对应的链接：

```
【基础档 Pro 服务 - ¥5/月】
购买后请点击以下链接领取兑换码：
https://your-domain.com/redeem_code_page.html?product=basic

【标准档 Pro 服务 - ¥10/月】
购买后请点击以下链接领取兑换码：
https://your-domain.com/redeem_code_page.html?product=standard

【高级档 Pro 服务 - ¥25/月】
购买后请点击以下链接领取兑换码：
https://your-domain.com/redeem_code_page.html?product=advanced
```

### 小红书/抖音

在商品描述或私信中发送链接：

```
📱 错题复盘 Pro 服务

基础档：¥5/月，60次/月
👉 https://your-domain.com/redeem_code_page.html?product=basic

标准档：¥10/月，140次/月
👉 https://your-domain.com/redeem_code_page.html?product=standard

高级档：¥25/月，350次/月
👉 https://your-domain.com/redeem_code_page.html?product=advanced
```

### 微信/QQ

可以直接发送链接，或者生成二维码：

```html
<!-- 可以添加二维码生成功能 -->
<div class="qr-code">
    <img src="https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=YOUR_URL" 
         alt="扫码领取兑换码">
</div>
```

## 自定义页面样式

如果需要为不同档次设置不同的颜色或样式，可以修改CSS：

```javascript
// 在 initPage() 函数中添加
function initPage() {
    const productType = getUrlParameter('product') || 'basic';
    const product = PRODUCTS[productType] || PRODUCTS['basic'];
    
    // 根据商品类型设置不同的主题色
    const themeColors = {
        'basic': { primary: '#4CAF50', gradient: 'linear-gradient(135deg, #4CAF50 0%, #45a049 100%)' },
        'standard': { primary: '#2196F3', gradient: 'linear-gradient(135deg, #2196F3 0%, #1976D2 100%)' },
        'advanced': { primary: '#FF9800', gradient: 'linear-gradient(135deg, #FF9800 0%, #F57C00 100%)' }
    };
    
    const colors = themeColors[productType] || themeColors['basic'];
    document.documentElement.style.setProperty('--primary-color', colors.primary);
    document.body.style.background = colors.gradient;
}
```

## 短链接（可选）

如果URL太长，可以使用短链接服务：

1. **使用短链接服务**（如：bit.ly, t.cn）
2. **自定义域名**（如：redeem.example.com/basic）
3. **生成二维码**，用户扫码访问

## 测试清单

- [ ] 基础档页面正常显示（`?product=basic`）
- [ ] 标准档页面正常显示（`?product=standard`）
- [ ] 高级档页面正常显示（`?product=advanced`）
- [ ] 商品信息（名称、价格、次数）正确
- [ ] 设备ID自动生成
- [ ] 领取兑换码功能正常
- [ ] 复制兑换码功能正常
- [ ] 移动端显示正常
- [ ] 不同浏览器测试通过

## 常见问题

### Q: 如何添加更多商品？

**A**: 在 `PRODUCTS` 对象中添加新商品：

```javascript
const PRODUCTS = {
    // ... 现有商品
    'custom': {
        name: '自定义商品',
        desc: '商品描述',
        price: '¥XX/月',
        productId: 'custom',
        times: 'XX次/月'
    }
};
```

然后使用链接：`?product=custom`

### Q: 如何修改商品价格？

**A**: 直接修改 `PRODUCTS` 对象中对应商品的 `price` 字段。

### Q: 如何设置默认商品？

**A**: 在 `initPage()` 函数中修改：

```javascript
const productType = getUrlParameter('product') || 'basic'; // 默认是 basic
```

### Q: 如何隐藏设备ID输入框？

**A**: 如果不需要显示设备ID，可以在CSS中隐藏：

```css
.form-group {
    display: none; /* 隐藏整个输入框组 */
}
```

## 部署建议

1. **HTTPS**: 建议使用HTTPS部署，保护用户数据
2. **CDN**: 使用CDN加速，提升访问速度
3. **缓存**: 设置适当的缓存策略
4. **监控**: 添加访问统计和错误监控

## 联系信息

如有问题，请联系技术支持。

