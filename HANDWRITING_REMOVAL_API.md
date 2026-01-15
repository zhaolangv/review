# 手写擦除接口文档

## 📋 接口概述

手写擦除功能用于清除图片中的手写笔记。系统会自动尝试两个服务（TextIn → 有道），确保高可用性。

---

## 🔌 API 接口

### 接口地址

```
POST /api/handwriting/remove
```

### 请求格式

- **Content-Type**: `multipart/form-data`

- **方法**: POST

### 请求参数

| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| image | File | 是 | 需要处理的图片文件（支持：png, jpg, jpeg, gif, bmp） |
| save_to_server | String | 否 | 是否保存到服务器（"true"/"false"，默认"false"） |

---

## 📤 请求示例

### JavaScript (原生 Fetch API)

```javascript
async function removeHandwriting(imageFile) {
  try {
    const formData = new FormData();
    formData.append('image', imageFile);
    
    const response = await fetch('/api/handwriting/remove', {
      method: 'POST',
      body: formData
    });
    
    const result = await response.json();
    
    if (result.success) {
      console.log('处理成功！');
      console.log('图片URL:', result.data.image_url);
      console.log('使用服务:', result.data.provider);
      return result.data;
    } else {
      console.error('处理失败:', result.error);
      throw new Error(result.error);
    }
  } catch (error) {
    console.error('请求失败:', error);
    throw error;
  }
}

// 使用示例
const fileInput = document.querySelector('#imageInput');
fileInput.addEventListener('change', async (e) => {
  const file = e.target.files[0];
  if (file) {
    try {
      const result = await removeHandwriting(file);
      // 显示处理后的图片
      const img = document.createElement('img');
      img.src = `/uploads/${result.filename}`;
      document.body.appendChild(img);
    } catch (error) {
      alert('手写擦除失败：' + error.message);
    }
  }
});
```

### React 示例

```jsx
import React, { useState } from 'react';

function HandwritingRemover() {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const handleFileChange = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    setLoading(true);
    setError(null);
    setResult(null);

    try {
      const formData = new FormData();
      formData.append('image', file);

      const response = await fetch('/api/handwriting/remove', {
        method: 'POST',
        body: formData
      });

      const data = await response.json();

      if (data.success) {
        setResult(data.data);
      } else {
        setError(data.error || '处理失败');
      }
    } catch (err) {
      setError('网络错误：' + err.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <input
        type="file"
        accept="image/*"
        onChange={handleFileChange}
        disabled={loading}
      />
      
      {loading && <p>处理中...</p>}
      
      {error && <p style={{ color: 'red' }}>错误：{error}</p>}
      
      {result && (
        <div>
          <p>处理成功！使用服务：{result.provider}</p>
          <img
            src={`/uploads/${result.filename}`}
            alt="处理后的图片"
            style={{ maxWidth: '100%' }}
          />
        </div>
      )}
    </div>
  );
}
```

### Vue 示例

```vue
<template>
  <div>
    <input
      type="file"
      accept="image/*"
      @change="handleFileChange"
      :disabled="loading"
    />
    
    <p v-if="loading">处理中...</p>
    <p v-if="error" style="color: red">错误：{{ error }}</p>
    
    <div v-if="result">
      <p>处理成功！使用服务：{{ result.provider }}</p>
      <img
        :src="`/uploads/${result.filename}`"
        alt="处理后的图片"
        style="max-width: 100%"
      />
    </div>
  </div>
</template>

<script>
export default {
  data() {
    return {
      loading: false,
      result: null,
      error: null
    };
  },
  methods: {
    async handleFileChange(e) {
      const file = e.target.files[0];
      if (!file) return;

      this.loading = true;
      this.error = null;
      this.result = null;

      try {
        const formData = new FormData();
        formData.append('image', file);

        const response = await fetch('/api/handwriting/remove', {
          method: 'POST',
          body: formData
        });

        const data = await response.json();

        if (data.success) {
          this.result = data.data;
        } else {
          this.error = data.error || '处理失败';
        }
      } catch (err) {
        this.error = '网络错误：' + err.message;
      } finally {
        this.loading = false;
      }
    }
  }
};
</script>
```

### Axios 示例

```javascript
import axios from 'axios';

async function removeHandwriting(imageFile) {
  const formData = new FormData();
  formData.append('image', imageFile);
  
  try {
    const response = await axios.post('/api/handwriting/remove', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    });
    
    if (response.data.success) {
      return response.data.data;
    } else {
      throw new Error(response.data.error);
    }
  } catch (error) {
    if (error.response) {
      throw new Error(error.response.data.error || '处理失败');
    } else {
      throw new Error('网络错误：' + error.message);
    }
  }
}
```

---

## 📥 响应格式

### 成功响应

**HTTP Status**: 200

```json
{
  "success": true,
  "data": {
    "image_url": "uploads/abc123_cleaned.jpg",
    "filename": "abc123_cleaned.jpg",
    "provider": "youdao"
  }
}
```

**字段说明**：
- `success`: 布尔值，表示请求是否成功
- `data.image_url`: 处理后的图片相对路径（用于前端显示）
- `data.filename`: 处理后的图片文件名
- `data.provider`: 使用的服务提供商（`"youdao"` 或 `"textin"`）

### 错误响应

**HTTP Status**: 400 或 500

```json
{
  "success": false,
  "error": "错误信息",
  "code": 400
}
```

**常见错误**：
- `缺少图片文件` (400) - 请求中没有图片文件
- `图片文件为空` (400) - 上传的文件为空
- `手写擦除失败，请稍后重试` (500) - 两个服务都调用失败

---

## 🖼️ 图片显示

处理后的图片存储在 `uploads/` 目录下，前端可以通过以下方式显示：

### 方式1：直接使用相对路径（推荐）

```javascript
// 如果前端和后端在同一域名下
const imageUrl = `/uploads/${result.filename}`;
// 或
const imageUrl = result.data.image_url; // "uploads/xxx.jpg"
```

### 方式2：使用完整URL

```javascript
// 如果前端和后端在不同域名
const baseUrl = 'https://your-api-domain.com';
const imageUrl = `${baseUrl}/${result.data.image_url}`;
```

### 方式3：使用 img 标签

```html
<img src="/uploads/abc123_cleaned.jpg" alt="处理后的图片" />
```

---

## 📝 完整的前端实现建议

### 1. 文件选择

```javascript
// HTML
<input type="file" id="imageInput" accept="image/*" />

// JavaScript
const fileInput = document.getElementById('imageInput');
fileInput.addEventListener('change', async (e) => {
  const file = e.target.files[0];
  if (file) {
    // 验证文件类型
    const allowedTypes = ['image/png', 'image/jpeg', 'image/jpg', 'image/gif', 'image/bmp'];
    if (!allowedTypes.includes(file.type)) {
      alert('不支持的图片格式，请选择 PNG、JPG、GIF 或 BMP 格式的图片');
      return;
    }
    
    // 验证文件大小（可选，建议限制在10MB以内）
    if (file.size > 10 * 1024 * 1024) {
      alert('图片文件过大，请选择小于10MB的图片');
      return;
    }
    
    // 调用接口
    await processImage(file);
  }
});
```

### 2. 显示加载状态

```javascript
function showLoading() {
  // 显示加载动画或提示
  document.getElementById('loading').style.display = 'block';
}

function hideLoading() {
  document.getElementById('loading').style.display = 'none';
}
```

### 3. 错误处理

```javascript
function handleError(error) {
  let errorMessage = '处理失败，请稍后重试';
  
  if (error.message) {
    errorMessage = error.message;
  }
  
  // 显示错误提示
  alert(errorMessage);
  // 或使用 Toast、Modal 等UI组件
}
```

### 4. 显示结果

```javascript
function displayResult(result) {
  const resultContainer = document.getElementById('result');
  
  resultContainer.innerHTML = `
    <div>
      <p>处理成功！使用服务：${result.provider === 'youdao' ? '有道' : 'TextIn'}</p>
      <img src="/uploads/${result.filename}" alt="处理后的图片" style="max-width: 100%" />
      <button onclick="downloadImage('${result.filename}')">下载图片</button>
    </div>
  `;
}

function downloadImage(filename) {
  const link = document.createElement('a');
  link.href = `/uploads/${filename}`;
  link.download = filename;
  link.click();
}
```

### 5. 完整示例（带进度条）

```html
<!DOCTYPE html>
<html>
<head>
  <title>手写擦除工具</title>
</head>
<body>
  <div>
    <input type="file" id="imageInput" accept="image/*" />
    <div id="loading" style="display: none;">
      <p>处理中，请稍候...</p>
      <progress></progress>
    </div>
    <div id="error" style="display: none; color: red;"></div>
    <div id="result"></div>
  </div>

  <script>
    const fileInput = document.getElementById('imageInput');
    const loadingDiv = document.getElementById('loading');
    const errorDiv = document.getElementById('error');
    const resultDiv = document.getElementById('result');

    fileInput.addEventListener('change', async (e) => {
      const file = e.target.files[0];
      if (!file) return;

      // 显示加载状态
      loadingDiv.style.display = 'block';
      errorDiv.style.display = 'none';
      resultDiv.innerHTML = '';

      try {
        const formData = new FormData();
        formData.append('image', file);

        const response = await fetch('/api/handwriting/remove', {
          method: 'POST',
          body: formData
        });

        const data = await response.json();

        if (data.success) {
          // 显示结果
          resultDiv.innerHTML = `
            <div>
              <p>✅ 处理成功！使用服务：${data.data.provider === 'youdao' ? '有道' : 'TextIn'}</p>
              <img src="/uploads/${data.data.filename}" alt="处理后的图片" style="max-width: 100%; margin-top: 20px;" />
              <br/>
              <button onclick="downloadImage('${data.data.filename}')" style="margin-top: 10px; padding: 10px 20px;">
                下载图片
              </button>
            </div>
          `;
        } else {
          errorDiv.textContent = '错误：' + (data.error || '处理失败');
          errorDiv.style.display = 'block';
        }
      } catch (error) {
        errorDiv.textContent = '网络错误：' + error.message;
        errorDiv.style.display = 'block';
      } finally {
        loadingDiv.style.display = 'none';
      }
    });

    function downloadImage(filename) {
      const link = document.createElement('a');
      link.href = `/uploads/${filename}`;
      link.download = filename;
      link.click();
    }
  </script>
</body>
</html>
```

---

## 🔧 注意事项

1. **文件大小限制**：建议前端限制上传文件大小（如10MB以内），避免请求超时

2. **文件类型验证**：前端应验证文件类型，只允许图片格式

3. **加载状态**：处理可能需要几秒到几十秒，建议显示加载动画

4. **错误处理**：妥善处理网络错误和API错误

5. **图片显示**：确保后端配置了静态文件服务，可以访问 `uploads/` 目录

6. **跨域问题**：如果前后端分离，需要配置CORS

---

## 🚀 快速集成检查清单

- [ ] 创建文件选择输入框
- [ ] 实现文件上传功能（FormData）
- [ ] 调用 `/api/handwriting/remove` 接口
- [ ] 显示加载状态
- [ ] 处理成功响应，显示处理后的图片
- [ ] 处理错误响应，显示错误信息
- [ ] 实现图片下载功能（可选）
- [ ] 添加文件类型和大小验证（可选）

---

## 📞 测试接口

可以使用以下方式测试接口：

### 使用 curl

```bash
curl -X POST \
  http://localhost:5000/api/handwriting/remove \
  -F "image=@/path/to/your/image.jpg"
```

### 使用 Postman

1. 选择 POST 方法
2. URL: `http://localhost:5000/api/handwriting/remove`
3. Body 选择 `form-data`
4. Key 填写 `image`，类型选择 `File`
5. Value 选择要上传的图片文件
6. 点击 Send

---

**接口已就绪，可以直接使用！** 🎉