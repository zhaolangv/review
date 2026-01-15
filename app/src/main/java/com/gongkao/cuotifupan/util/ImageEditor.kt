package com.gongkao.cuotifupan.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.media.ExifInterface
import android.util.Log
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * 图片编辑工具类
 * 支持裁剪和旋转功能
 */
object ImageEditor {
    
    private const val TAG = "ImageEditor"
    
    // 存储Context，用于获取设备ID
    private var context: android.content.Context? = null
    
    /**
     * 初始化ImageEditor（设置Context）
     */
    fun init(context: android.content.Context) {
        this.context = context.applicationContext
    }
    
    /**
     * 旋转图片
     * @param imagePath 原图片路径
     * @param degrees 旋转角度（90, 180, 270等）
     * @return 旋转后的图片路径，如果失败返回null
     */
    fun rotateImage(imagePath: String, degrees: Int): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "图片文件不存在: $imagePath")
                return null
            }
            
            // 读取原图
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
            if (originalBitmap == null) {
                Log.e(TAG, "无法读取图片: $imagePath")
                return null
            }
            
            // 创建旋转矩阵
            val matrix = Matrix().apply {
                postRotate(degrees.toFloat())
            }
            
            // 旋转图片
            val rotatedBitmap = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
            
            // 释放原图内存
            originalBitmap.recycle()
            
            // 保存旋转后的图片
            val outputPath = saveBitmap(rotatedBitmap, imagePath)
            rotatedBitmap.recycle()
            
            Log.i(TAG, "图片旋转成功: $imagePath -> $outputPath (角度: $degrees°)")
            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "旋转图片失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 裁剪图片
     * @param imagePath 原图片路径
     * @param x 裁剪区域左上角X坐标
     * @param y 裁剪区域左上角Y坐标
     * @param width 裁剪区域宽度
     * @param height 裁剪区域高度
     * @return 裁剪后的图片路径，如果失败返回null
     */
    fun cropImage(imagePath: String, x: Int, y: Int, width: Int, height: Int): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "图片文件不存在: $imagePath")
                return null
            }
            
            // 读取原图
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
            if (originalBitmap == null) {
                Log.e(TAG, "无法读取图片: $imagePath")
                return null
            }
            
            // 验证裁剪参数
            val validX = x.coerceIn(0, originalBitmap.width)
            val validY = y.coerceIn(0, originalBitmap.height)
            val validWidth = width.coerceIn(1, originalBitmap.width - validX)
            val validHeight = height.coerceIn(1, originalBitmap.height - validY)
            
            // 裁剪图片
            val croppedBitmap = Bitmap.createBitmap(
                originalBitmap,
                validX,
                validY,
                validWidth,
                validHeight
            )
            
            // 释放原图内存
            originalBitmap.recycle()
            
            // 保存裁剪后的图片
            val outputPath = saveBitmap(croppedBitmap, imagePath)
            croppedBitmap.recycle()
            
            Log.i(TAG, "图片裁剪成功: $imagePath -> $outputPath (区域: $validX,$validY ${validWidth}x$validHeight)")
            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "裁剪图片失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 智能裁剪图片（自动检测题目区域，去除上下空白）
     * 使用简单的边缘检测算法
     * @param imagePath 原图片路径
     * @param margin 保留的边距（像素）
     * @return 裁剪后的图片路径，如果失败返回null
     */
    fun smartCropImage(imagePath: String, margin: Int = 10): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "图片文件不存在: $imagePath")
                return null
            }
            
            // 读取原图
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
            if (originalBitmap == null) {
                Log.e(TAG, "无法读取图片: $imagePath")
                return null
            }
            
            // 检测内容区域（简单的边缘检测）
            val bounds = detectContentBounds(originalBitmap)
            
            if (bounds == null) {
                Log.w(TAG, "无法检测内容区域，使用原图")
                originalBitmap.recycle()
                return imagePath
            }
            
            // 添加边距
            val x = (bounds.left - margin).coerceAtLeast(0)
            val y = (bounds.top - margin).coerceAtLeast(0)
            val width = (bounds.right - bounds.left + 2 * margin).coerceAtMost(originalBitmap.width - x)
            val height = (bounds.bottom - bounds.top + 2 * margin).coerceAtMost(originalBitmap.height - y)
            
            // 裁剪图片
            val croppedBitmap = Bitmap.createBitmap(
                originalBitmap,
                x,
                y,
                width,
                height
            )
            
            // 释放原图内存
            originalBitmap.recycle()
            
            // 保存裁剪后的图片
            val outputPath = saveBitmap(croppedBitmap, imagePath)
            croppedBitmap.recycle()
            
            Log.i(TAG, "智能裁剪成功: $imagePath -> $outputPath (区域: $x,$y ${width}x$height)")
            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "智能裁剪失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 检测图片内容边界
     * 使用改进的算法：排除顶部和底部的UI元素（状态栏、导航栏等）
     */
    private fun detectContentBounds(bitmap: Bitmap): android.graphics.Rect? {
        val width = bitmap.width
        val height = bitmap.height
        
        // 阈值：RGB值小于此值认为是内容（非空白）
        val threshold = 240
        
        // 排除顶部区域（通常是状态栏和导航栏，约占高度的15%）
        val topSkipRatio = 0.15f
        val topSkip = (height * topSkipRatio).toInt()
        
        // 排除底部区域（通常是导航栏，约占高度的10%）
        val bottomSkipRatio = 0.10f
        val bottomSkip = (height * bottomSkipRatio).toInt()
        
        var top = -1
        var bottom = -1
        var left = -1
        var right = -1
        
        // 从上到下扫描，找到第一个非空白行（跳过顶部UI区域）
        for (y in topSkip until height - bottomSkip) {
            var hasContent = false
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                
                // 如果RGB值都小于阈值，认为是内容
                if (r < threshold || g < threshold || b < threshold) {
                    hasContent = true
                    break
                }
            }
            if (hasContent) {
                top = y
                break
            }
        }
        
        // 从下到上扫描，找到最后一个非空白行（跳过底部UI区域）
        for (y in height - bottomSkip - 1 downTo topSkip) {
            var hasContent = false
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                
                if (r < threshold || g < threshold || b < threshold) {
                    hasContent = true
                    break
                }
            }
            if (hasContent) {
                bottom = y
                break
            }
        }
        
        // 从左到右扫描，找到第一个非空白列
        for (x in 0 until width) {
            var hasContent = false
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                
                if (r < threshold || g < threshold || b < threshold) {
                    hasContent = true
                    break
                }
            }
            if (hasContent) {
                left = x
                break
            }
        }
        
        // 从右到左扫描，找到最后一个非空白列
        for (x in width - 1 downTo 0) {
            var hasContent = false
            for (y in 0 until height) {
                val pixel = bitmap.getPixel(x, y)
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                
                if (r < threshold || g < threshold || b < threshold) {
                    hasContent = true
                    break
                }
            }
            if (hasContent) {
                right = x
                break
            }
        }
        
        // 验证边界
        if (top == -1 || bottom == -1 || left == -1 || right == -1) {
            return null
        }
        
        if (top >= bottom || left >= right) {
            return null
        }
        
        return android.graphics.Rect(left, top, right, bottom)
    }
    
    /**
     * 保存 Bitmap 到文件
     * @param bitmap 要保存的图片
     * @param originalPath 原图片路径（用于生成新文件名）
     * @return 保存后的文件路径
     */
    private fun saveBitmap(bitmap: Bitmap, originalPath: String): String? {
        return try {
            val originalFile = File(originalPath)
            val parentDir = originalFile.parentFile
            val fileName = originalFile.nameWithoutExtension
            val extension = originalFile.extension.ifEmpty { "jpg" }
            
            // 生成新文件名（添加时间戳避免覆盖）
            val timestamp = System.currentTimeMillis()
            val newFileName = "${fileName}_edited_$timestamp.$extension"
            val outputFile = File(parentDir, newFileName)
            
            // 保存图片
            FileOutputStream(outputFile).use { out ->
                val format = when (extension.lowercase()) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }
                val quality = if (format == Bitmap.CompressFormat.PNG) 100 else 90
                bitmap.compress(format, quality, out)
            }
            
            // 如果保存成功，删除原图（可选，这里保留原图）
            // originalFile.delete()
            
            outputFile.absolutePath
        } catch (e: IOException) {
            Log.e(TAG, "保存图片失败", e)
            null
        }
    }
    
    /**
     * 自动处理图片：不进行任何处理，直接返回原路径
     * @param imagePath 原图片路径
     * @return 原图片路径（不做任何处理）
     */
    fun autoProcessImage(imagePath: String): String {
        // 不再进行任何自动处理，直接返回原路径
        return imagePath
    }
    
    /**
     * 基于OCR结果精确裁剪题目区域
     * @param imagePath 原图片路径
     * @param textBlocks OCR识别的文字块列表（包含位置信息）
     * @return 裁剪后的图片路径，如果失败返回null
     */
    fun cropBasedOnOcr(imagePath: String, textBlocks: List<com.gongkao.cuotifupan.ocr.TextBlock>): String? {
        return try {
            if (textBlocks.isEmpty()) {
                Log.w(TAG, "OCR文字块为空，无法基于OCR裁剪")
                return null
            }
            
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "图片文件不存在: $imagePath")
                return null
            }
            
            // 读取原图
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
            if (originalBitmap == null) {
                Log.e(TAG, "无法读取图片: $imagePath")
                return null
            }
            
            // 计算所有文字块的边界框
            var minLeft = originalBitmap.width
            var minTop = originalBitmap.height
            var maxRight = 0
            var maxBottom = 0
            
            textBlocks.forEach { block ->
                val box = block.boundingBox
                if (box.left < minLeft) minLeft = box.left
                if (box.top < minTop) minTop = box.top
                if (box.right > maxRight) maxRight = box.right
                if (box.bottom > maxBottom) maxBottom = box.bottom
            }
            
            // 添加边距（10%的边距）
            val marginX = (maxRight - minLeft) * 0.1f
            val marginY = (maxBottom - minTop) * 0.1f
            
            val x = (minLeft - marginX).toInt().coerceAtLeast(0)
            val y = (minTop - marginY).toInt().coerceAtLeast(0)
            val width = ((maxRight - minLeft) + 2 * marginX).toInt().coerceAtMost(originalBitmap.width - x)
            val height = ((maxBottom - minTop) + 2 * marginY).toInt().coerceAtMost(originalBitmap.height - y)
            
            // 验证裁剪区域
            if (width <= 0 || height <= 0 || x >= originalBitmap.width || y >= originalBitmap.height) {
                Log.w(TAG, "OCR裁剪区域无效，使用原图")
                originalBitmap.recycle()
                return null
            }
            
            // 裁剪图片
            val croppedBitmap = Bitmap.createBitmap(
                originalBitmap,
                x,
                y,
                width,
                height
            )
            
            // 释放原图内存
            originalBitmap.recycle()
            
            // 保存裁剪后的图片
            val outputPath = saveBitmap(croppedBitmap, imagePath)
            croppedBitmap.recycle()
            
            Log.i(TAG, "基于OCR裁剪成功: $imagePath -> $outputPath (区域: $x,$y ${width}x$height)")
            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "基于OCR裁剪失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 自动旋转图片（根据EXIF方向信息）
     * @param imagePath 原图片路径
     * @return 旋转后的图片路径，如果不需要旋转或失败返回null
     */
    private fun autoRotateImage(imagePath: String): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                return null
            }
            
            // 读取EXIF方向信息
            val exif = ExifInterface(imagePath)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            // 计算需要旋转的角度
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                ExifInterface.ORIENTATION_NORMAL -> return null // 不需要旋转
                else -> return null
            }
            
            // 执行旋转
            rotateImage(imagePath, degrees)
        } catch (e: Exception) {
            Log.e(TAG, "自动旋转失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 获取图片信息
     */
    fun getImageInfo(imagePath: String): ImageInfo? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                return null
            }
            
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(imagePath, options)
            
            ImageInfo(
                width = options.outWidth,
                height = options.outHeight,
                size = file.length(),
                mimeType = options.outMimeType
            )
        } catch (e: Exception) {
            Log.e(TAG, "获取图片信息失败", e)
            null
        }
    }
    
    data class ImageInfo(
        val width: Int,
        val height: Int,
        val size: Long,
        val mimeType: String?
    )
    
    /**
     * 清除图片中的手写笔记，保留打印文字
     * 使用后端API进行处理（推荐方法）
     * 
     * @param imagePath 原图片路径
     * @param intensity 清除强度（0.0-1.0），值越大清除越彻底，但可能误删打印文字
     * @return 处理后的图片路径，如果失败返回null
     */
    fun removeHandwrittenNotes(imagePath: String, intensity: Float = 0.6f): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "图片文件不存在: $imagePath")
                return null
            }
            
            Log.i(TAG, "开始清除手写笔记（使用API）: $imagePath (强度: $intensity)")
            
            // 使用API处理
            val resultPath = removeHandwritingViaAPI(imagePath, intensity)
            
            if (resultPath != null) {
                Log.i(TAG, "清除手写笔记成功: $imagePath -> $resultPath")
            } else {
                Log.e(TAG, "清除手写笔记失败")
            }
            
            resultPath
        } catch (e: Exception) {
            Log.e(TAG, "清除手写笔记失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 通过API清除手写笔记
     * 使用新的接口规范：/api/handwriting/remove
     */
    private fun removeHandwritingViaAPI(imagePath: String, intensity: Float): String? {
        return try {
            val file = File(imagePath)
            
            // 获取设备ID
            val appContext = context ?: throw IllegalStateException("ImageEditor未初始化，请先调用init()")
            val deviceId = VersionChecker(appContext).getDeviceId()
            
            // 创建 MultipartBody.Part
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val imagePart = MultipartBody.Part.createFormData("image", file.name, requestFile)
            val deviceIdBody = deviceId.toRequestBody("text/plain".toMediaTypeOrNull())
            
            // 注意：新接口不再使用intensity参数，系统自动处理
            // save_to_server 参数可选，默认为false（不保存到服务器）
            
            // 调用API（使用runBlocking在IO线程执行）
            val response = kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    com.gongkao.cuotifupan.api.ApiClient.questionApiService.removeHandwriting(
                        image = imagePart,
                        deviceId = deviceIdBody,
                        saveToServer = null  // 不保存到服务器，直接返回图片数据
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "API调用失败", e)
                    null
                }
            }
            
            if (response == null || !response.isSuccessful || response.body() == null) {
                Log.e(TAG, "API请求失败: ${response?.code()}, ${response?.message()}")
                return null
            }
            
            val apiResponse = response.body()!!
            if (!apiResponse.success) {
                val errorMsg = apiResponse.error ?: "未知错误"
                Log.e(TAG, "API处理失败: $errorMsg")
                return null
            }
            
            // 处理返回的图片（新格式：data.image_url）
            val imageUrl = apiResponse.data?.imageUrl
            if (imageUrl.isNullOrBlank()) {
                Log.e(TAG, "API响应中没有图片URL")
                return null
            }
            
            Log.d(TAG, "手写擦除成功，使用服务: ${apiResponse.data?.provider}")
            Log.d(TAG, "图片URL: $imageUrl")
            
            // 构建完整URL并下载图片
            val fullUrl = if (imageUrl.startsWith("http")) {
                imageUrl
            } else {
                // 相对路径，需要拼接BASE_URL
                val baseUrl = com.gongkao.cuotifupan.api.ApiClient.BASE_URL.trimEnd('/')
                "$baseUrl/$imageUrl"
            }
            
            val resultBitmap = downloadImageFromUrl(fullUrl)
            
            if (resultBitmap == null) {
                Log.e(TAG, "无法下载处理后的图片")
                return null
            }
            
            // 保存处理后的图片
            val outputPath = saveBitmap(resultBitmap, imagePath)
            resultBitmap.recycle()
            
            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "API处理失败", e)
            null
        }
    }
    
    /**
     * 解码Base64图片
     */
    private fun decodeBase64Image(base64: String): Bitmap? {
        return try {
            val imageBytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Base64解码失败", e)
            null
        }
    }
    
    /**
     * 从URL下载图片
     */
    private fun downloadImageFromUrl(url: String): Bitmap? {
        return try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            
            if (response.isSuccessful) {
                val inputStream = response.body?.byteStream()
                BitmapFactory.decodeStream(inputStream)
            } else {
                Log.e(TAG, "下载图片失败: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "下载图片失败", e)
            null
        }
    }
    
    /**
     * 使用OCR辅助处理图片，移除手写笔记
     * 新算法思路：
     * 1. 使用OCR识别打印文字的位置
     * 2. 在文字位置周围保留一定区域（保护打印文字）
     * 3. 清除其他区域的所有内容（包括手写笔记）
     * 4. 使用图像修复填充清除的区域
     */
    private fun processRemoveHandwritingWithOCR(bitmap: Bitmap, imagePath: String, intensity: Float): Bitmap? {
        return try {
            // 使用OCR识别打印文字位置
            val ocrResult = recognizeTextForHandwritingRemoval(imagePath)
            
            if (ocrResult == null || ocrResult.textBlocks.isEmpty()) {
                Log.w(TAG, "OCR识别失败或没有文字，使用备用方法")
                // 如果OCR失败，使用基于颜色的方法作为备用
                return processRemoveHandwritingByColor(bitmap, intensity)
            }
            
            Log.i(TAG, "OCR识别成功，找到 ${ocrResult.textBlocks.size} 个文字块")
            
            // 创建保护区域掩码（打印文字区域）
            val protectMask = createTextProtectionMask(bitmap, ocrResult.textBlocks, intensity)
            
            // 创建清除掩码（非文字区域，需要清除手写）
            val clearMask = createClearMask(protectMask)
            
            // 使用图像修复填充清除区域
            val resultBitmap = inpaintHandwriting(bitmap, clearMask)
            
            // 释放中间资源
            protectMask.recycle()
            clearMask.recycle()
            
            resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "OCR辅助处理失败，使用备用方法", e)
            // 失败时使用备用方法
            processRemoveHandwritingByColor(bitmap, intensity)
        }
    }
    
    /**
     * OCR识别文字（用于手写清除）
     */
    private fun recognizeTextForHandwritingRemoval(imagePath: String): com.gongkao.cuotifupan.ocr.OcrResult? {
        return try {
            val recognizer = com.gongkao.cuotifupan.ocr.TextRecognizer()
            // 使用同步方式（在IO线程中）
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
                recognizer.recognizeText(imagePath)
            }
        } catch (e: Exception) {
            Log.e(TAG, "OCR识别失败", e)
            null
        }
    }
    
    /**
     * 创建文字保护掩码（打印文字区域需要保护，不清除）
     */
    private fun createTextProtectionMask(bitmap: Bitmap, textBlocks: List<com.gongkao.cuotifupan.ocr.TextBlock>, intensity: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(maskBitmap)
        val paint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        
        // 保护区域边距（根据强度调整，强度越大边距越小）
        val margin = (10 - intensity * 5).toInt().coerceAtLeast(3)
        
        // 为每个文字块创建保护区域
        textBlocks.forEach { block ->
            val box = block.boundingBox
            // 扩展边界框，保护文字周围的区域
            val left = (box.left - margin).coerceAtLeast(0)
            val top = (box.top - margin).coerceAtLeast(0)
            val right = (box.right + margin).coerceAtMost(width)
            val bottom = (box.bottom + margin).coerceAtMost(height)
            
            canvas.drawRect(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                bottom.toFloat(),
                paint
            )
        }
        
        // 对保护区域进行膨胀，确保覆盖完整
        val pixels = IntArray(width * height)
        maskBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val dilatedPixels = dilateMask(pixels, width, height, (3 + intensity * 2).toInt())
        maskBitmap.setPixels(dilatedPixels, 0, width, 0, 0, width, height)
        
        return maskBitmap
    }
    
    /**
     * 创建清除掩码（需要清除的区域）
     */
    private fun createClearMask(protectMask: Bitmap): Bitmap {
        val width = protectMask.width
        val height = protectMask.height
        val clearMask = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val protectPixels = IntArray(width * height)
        val clearPixels = IntArray(width * height)
        protectMask.getPixels(protectPixels, 0, width, 0, 0, width, height)
        
        // 反转掩码：保护区域是白色，清除区域也应该是白色
        for (i in protectPixels.indices) {
            val value = Color.red(protectPixels[i])
            // 如果不在保护区域，标记为需要清除
            clearPixels[i] = if (value < 128) Color.WHITE else Color.TRANSPARENT
        }
        
        clearMask.setPixels(clearPixels, 0, width, 0, 0, width, height)
        return clearMask
    }
    
    /**
     * 备用方法：基于颜色的清除（当OCR失败时使用）
     */
    private fun processRemoveHandwritingByColor(bitmap: Bitmap, intensity: Float): Bitmap? {
        return try {
            val maskBitmap = detectHandwritingByColor(bitmap, intensity)
            val resultBitmap = inpaintHandwriting(bitmap, maskBitmap)
            maskBitmap.recycle()
            resultBitmap
        } catch (e: Exception) {
            Log.e(TAG, "基于颜色的清除失败", e)
            null
        }
    }
    
    /**
     * 基于颜色检测手写区域
     * 手写笔迹特征：通常是蓝色、红色、绿色等彩色笔，而打印文字是黑色
     */
    private fun detectHandwritingByColor(bitmap: Bitmap, intensity: Float): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val maskPixels = IntArray(width * height)
        
        // 颜色检测参数（根据强度调整）
        val colorThreshold = (50 + intensity * 100).toInt() // 50-150，值越大越严格
        val blackThreshold = 80 // 黑色阈值（RGB都小于此值认为是黑色/打印文字）
        
        // 第一遍：基于颜色检测手写区域
        for (i in pixels.indices) {
            val pixel = pixels[i]
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            
            // 计算是否为黑色（打印文字）
            val isBlack = r < blackThreshold && g < blackThreshold && b < blackThreshold
            
            // 计算颜色的饱和度（彩色程度）
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val saturation = if (max > 0) (max - min).toFloat() / max else 0f
            
            // 计算颜色的亮度
            val brightness = (r + g + b) / 3f
            
            // 检测是否为彩色手写笔迹
            // 条件：不是黑色，且有一定的饱和度，且亮度适中
            val isColorful = !isBlack && 
                            saturation > (0.1 + intensity * 0.2) && // 饱和度阈值
                            brightness > 30 && brightness < 220 // 亮度范围
            
            // 检测是否为深色手写（如深蓝色、深红色笔）
            val isDarkColor = !isBlack && 
                             brightness < 100 && 
                             (abs(r - g) > colorThreshold || abs(g - b) > colorThreshold || abs(r - b) > colorThreshold)
            
            if (isColorful || isDarkColor) {
                maskPixels[i] = Color.WHITE // 标记为手写区域
            } else {
                maskPixels[i] = Color.TRANSPARENT
            }
        }
        
        // 形态学操作：膨胀和腐蚀，连接手写区域，去除噪声
        val dilatedPixels = dilateMask(maskPixels, width, height, (2 + intensity * 3).toInt())
        val erodedPixels = erodeMask(dilatedPixels, width, height, (1 + intensity).toInt())
        
        // 再次膨胀，确保覆盖完整的手写区域
        val finalPixels = dilateMask(erodedPixels, width, height, (1 + intensity * 2).toInt())
        
        maskBitmap.setPixels(finalPixels, 0, width, 0, 0, width, height)
        
        return maskBitmap
    }
    
    /**
     * 计算自适应阈值
     */
    private fun calculateAdaptiveThreshold(bitmap: Bitmap): Int {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var sum = 0L
        var count = 0
        
        // 采样计算平均灰度值
        val step = max(1, min(width, height) / 50) // 采样步长
        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val gray = Color.red(pixels[y * width + x])
                sum += gray
                count++
            }
        }
        
        return if (count > 0) (sum / count).toInt() else 128
    }
    
    /**
     * 检测细线特征
     */
    private fun detectThinLine(bitmap: Bitmap, x: Int, y: Int, threshold: Int): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        
        if (x <= 0 || x >= width - 1 || y <= 0 || y >= height - 1) {
            return false
        }
        
        val centerGray = Color.red(bitmap.getPixel(x, y))
        if (centerGray >= threshold) return false
        
        // 检查周围像素的对比度
        var contrastCount = 0
        val neighbors = listOf(
            bitmap.getPixel(x - 1, y),
            bitmap.getPixel(x + 1, y),
            bitmap.getPixel(x, y - 1),
            bitmap.getPixel(x, y + 1)
        )
        
        neighbors.forEach { pixel ->
            val neighborGray = Color.red(pixel)
            if (abs(neighborGray - centerGray) > 30) { // 对比度阈值
                contrastCount++
            }
        }
        
        // 如果周围有高对比度，可能是细线
        return contrastCount >= 2
    }
    
    /**
     * 检测不规则区域
     */
    private fun detectIrregularRegion(bitmap: Bitmap, x: Int, y: Int): Boolean {
        val width = bitmap.width
        val height = bitmap.height
        
        if (x <= 1 || x >= width - 2 || y <= 1 || y >= height - 2) {
            return false
        }
        
        // 计算3x3区域的方差
        val grays = mutableListOf<Int>()
        for (dy in -1..1) {
            for (dx in -1..1) {
                val gray = Color.red(bitmap.getPixel(x + dx, y + dy))
                grays.add(gray)
            }
        }
        
        val mean = grays.average()
        val variance = grays.map { (it - mean) * (it - mean) }.average()
        
        // 方差大表示不规则
        return variance > 200
    }
    
    /**
     * 膨胀操作（扩大手写区域）
     */
    private fun dilateMask(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val result = IntArray(pixels.size)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var maxValue = 0
                
                // 检查周围区域
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        val ny = y + dy
                        
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            val value = Color.red(pixels[ny * width + nx])
                            maxValue = max(maxValue, value)
                        }
                    }
                }
                
                result[y * width + x] = if (maxValue > 128) Color.WHITE else Color.TRANSPARENT
            }
        }
        
        return result
    }
    
    /**
     * 腐蚀操作（缩小手写区域）
     */
    private fun erodeMask(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val result = IntArray(pixels.size)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                var minValue = 255
                
                // 检查周围区域
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val nx = x + dx
                        val ny = y + dy
                        
                        if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                            val value = Color.red(pixels[ny * width + nx])
                            minValue = min(minValue, value)
                        }
                    }
                }
                
                result[y * width + x] = if (minValue < 128) Color.WHITE else Color.TRANSPARENT
            }
        }
        
        return result
    }
    
    /**
     * 图像修复：填充手写区域
     * 使用改进的邻域加权平均算法，距离越近权重越大
     */
    private fun inpaintHandwriting(originalBitmap: Bitmap, maskBitmap: Bitmap): Bitmap {
        val width = originalBitmap.width
        val height = originalBitmap.height
        val resultBitmap = originalBitmap.copy(originalBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        
        val originalPixels = IntArray(width * height)
        val maskPixels = IntArray(width * height)
        originalBitmap.getPixels(originalPixels, 0, width, 0, 0, width, height)
        maskBitmap.getPixels(maskPixels, 0, width, 0, 0, width, height)
        
        val resultPixels = originalPixels.copyOf()
        
        // 修复手写区域（多遍处理，逐步修复）
        val maxRadius = 15 // 最大搜索半径
        val iterations = 3 // 迭代次数
        
        repeat(iterations) { iteration ->
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val maskIndex = y * width + x
                    val maskValue = Color.red(maskPixels[maskIndex])
                    
                    // 如果是手写区域（白色），进行修复
                    if (maskValue > 128) {
                        // 使用周围非手写区域的像素加权平均
                        var rSum = 0.0
                        var gSum = 0.0
                        var bSum = 0.0
                        var weightSum = 0.0
                        
                        // 根据迭代次数调整搜索半径
                        val radius = (maxRadius * (1.0 - iteration * 0.3)).toInt().coerceAtLeast(3)
                        
                        for (dy in -radius..radius) {
                            for (dx in -radius..radius) {
                                val nx = x + dx
                                val ny = y + dy
                                
                                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                                    val neighborMaskIndex = ny * width + nx
                                    val neighborMaskValue = Color.red(maskPixels[neighborMaskIndex])
                                    
                                    // 只使用非手写区域的像素
                                    if (neighborMaskValue < 128) {
                                        val neighborPixel = resultPixels[neighborMaskIndex] // 使用已修复的像素
                                        val distance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                                        
                                        // 距离越近权重越大（高斯权重）
                                        val weight = if (distance > 0) {
                                            1.0 / (1.0 + distance * distance)
                                        } else {
                                            1.0
                                        }
                                        
                                        rSum += Color.red(neighborPixel) * weight
                                        gSum += Color.green(neighborPixel) * weight
                                        bSum += Color.blue(neighborPixel) * weight
                                        weightSum += weight
                                    }
                                }
                            }
                        }
                        
                        // 如果有足够的非手写像素，使用加权平均值
                        if (weightSum > 0.1) {
                            val r = (rSum / weightSum).toInt().coerceIn(0, 255)
                            val g = (gSum / weightSum).toInt().coerceIn(0, 255)
                            val b = (bSum / weightSum).toInt().coerceIn(0, 255)
                            resultPixels[maskIndex] = Color.rgb(r, g, b)
                        }
                    }
                }
            }
            
            // 更新结果像素数组
            resultBitmap.setPixels(resultPixels, 0, width, 0, 0, width, height)
        }
        
        return resultBitmap
    }
    
    /**
     * 粉笔截图处理器 - 简化稳定版
     * 只替换彩色圆圈，不裁剪图片
     */
    
    data class Circle(
        val cx: Int,
        val cy: Int,
        val radius: Int,
        var letter: String = "?"
    )
    
    /**
     * 遮挡选项A、B、C、D（一键隐藏答案）
     * 使用 fenbi-cleaner-android 的 ImageProcessor 简化稳定版处理逻辑
     * @param imagePath 原图片路径
     * @return 处理后的图片路径，如果失败返回null
     */
    fun hideOptions(imagePath: String): String? {
        return try {
            val file = File(imagePath)
            if (!file.exists()) {
                Log.e(TAG, "图片文件不存在: $imagePath")
                return null
            }
            
            // 读取原图
            val originalBitmap = BitmapFactory.decodeFile(imagePath)
            if (originalBitmap == null) {
                Log.e(TAG, "无法读取图片: $imagePath")
                return null
            }
            
            // 使用 FenbiImageProcessor 处理
            val processor = ImageProcessor()
            val processedBitmap = processor.process(originalBitmap)
            
            // 释放原图内存
            originalBitmap.recycle()
            
            // 保存处理后的图片
            val outputPath = saveBitmap(processedBitmap, imagePath)
            processedBitmap.recycle()
            
            Log.i(TAG, "处理粉笔图片成功: $imagePath -> $outputPath")
            outputPath
        } catch (e: Exception) {
            Log.e(TAG, "处理粉笔图片失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 处理粉笔截图 - 简化稳定版，只替换彩色圆圈，不裁剪图片
     * @param bitmap 原始截图
     * @return 处理后的图片
     */
    private fun processFenbiImage(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // 创建可编辑副本
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(width * height)
        result.getPixels(pixels, 0, width, 0, 0, width, height)
        
        // 1. 检测所有彩色圆圈（红色/绿色）
        val coloredCircles = detectColoredCircles(pixels, width, height)
        
        if (coloredCircles.isEmpty()) {
            return result // 没有彩色圆圈，返回原图
        }
        
        // 2. 确定字母顺序（按Y坐标排序）
        coloredCircles.sortBy { it.cy }
        
        // 3. 根据Y坐标位置分配字母
        // 假设选项区域在图片高度的25%-75%之间
        val optionAreaTop = height * 0.25
        val optionAreaBottom = height * 0.75
        val optionAreaHeight = optionAreaBottom - optionAreaTop
        
        for (circle in coloredCircles) {
            // 根据相对位置推断字母
            val relativeY = (circle.cy - optionAreaTop) / optionAreaHeight
            val index = when {
                relativeY < 0.25 -> 0  // A
                relativeY < 0.50 -> 1  // B
                relativeY < 0.75 -> 2  // C
                else -> 3              // D
            }
            circle.letter = listOf("A", "B", "C", "D").getOrElse(index) { "?" }
        }
        
        // 4. 处理每个彩色圆圈
        for (circle in coloredCircles) {
            replaceCircle(pixels, width, height, circle)
        }
        
        // 5. 清除右上角红X（如果有）
        clearRedX(pixels, width, height)
        
        // 6. 写回像素
        result.setPixels(pixels, 0, width, 0, 0, width, height)
        
        // 不裁剪，直接返回
        return result
    }

    /**
     * 检测彩色圆圈
     */
    private fun detectColoredCircles(pixels: IntArray, width: Int, height: Int): MutableList<Circle> {
        val circles = mutableListOf<Circle>()
        val visited = BooleanArray(width * height)
        
        // 只在左侧1/3区域搜索
        val searchRight = width / 3
        val searchTop = (height * 0.15).toInt()
        val searchBottom = (height * 0.80).toInt()
        
        for (y in searchTop until searchBottom) {
            for (x in 0 until searchRight) {
                val idx = y * width + x
                if (visited[idx]) continue
                
                val color = pixels[idx]
                if (isGreenOrRed(color)) {
                    // 找到彩色像素，进行洪水填充
                    val region = floodFill(pixels, width, height, x, y, visited)
                    
                    // 过滤太小或太大的区域
                    if (region.size in 500..100000) {
                        val circle = fitCircle(region)
                        if (circle != null && circle.radius in 15..100) {
                            circles.add(circle)
                        }
                    }
                }
            }
        }
        
        return circles
    }

    /**
     * 判断是否为绿色或红色
     */
    private fun isGreenOrRed(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        
        val hsv = FloatArray(3)
        Color.RGBToHSV(r, g, b, hsv)
        
        val h = hsv[0]
        val s = hsv[1]
        val v = hsv[2]
        
        // 绿色: H在80-160度，饱和度和亮度足够
        val isGreen = h in 80f..160f && s > 0.2f && v > 0.3f
        
        // 红色: H在0-30度或330-360度
        val isRed = (h in 0f..30f || h in 330f..360f) && s > 0.2f && v > 0.3f
        
        return isGreen || isRed
    }

    /**
     * 洪水填充找连通区域
     */
    private fun floodFill(
        pixels: IntArray,
        width: Int,
        height: Int,
        startX: Int,
        startY: Int,
        visited: BooleanArray
    ): List<Pair<Int, Int>> {
        val region = mutableListOf<Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        queue.add(Pair(startX, startY))
        
        while (queue.isNotEmpty() && region.size < 100000) {
            val (x, y) = queue.removeFirst()
            
            if (x < 0 || x >= width || y < 0 || y >= height) continue
            
            val idx = y * width + x
            if (visited[idx]) continue
            if (!isGreenOrRed(pixels[idx])) continue
            
            visited[idx] = true
            region.add(Pair(x, y))
            
            queue.add(Pair(x + 1, y))
            queue.add(Pair(x - 1, y))
            queue.add(Pair(x, y + 1))
            queue.add(Pair(x, y - 1))
        }
        
        return region
    }

    /**
     * 拟合圆
     */
    private fun fitCircle(region: List<Pair<Int, Int>>): Circle? {
        if (region.isEmpty()) return null
        
        val cx = region.map { it.first }.average().toInt()
        val cy = region.map { it.second }.average().toInt()
        val radius = region.map {
            sqrt(((it.first - cx) * (it.first - cx) + (it.second - cy) * (it.second - cy)).toDouble())
        }.average().toInt()
        
        return Circle(cx, cy, radius)
    }

    /**
     * 替换彩色圆圈为灰色圆圈+字母
     */
    private fun replaceCircle(pixels: IntArray, width: Int, height: Int, circle: Circle) {
        val cx = circle.cx
        val cy = circle.cy
        val radius = circle.radius
        
        // 颜色
        val borderGray = Color.rgb(235, 235, 235)
        val letterGray = Color.rgb(165, 167, 180)
        
        // 1. 白色覆盖原圆圈
        val coverRadius = radius + 15
        for (dy in -coverRadius..coverRadius) {
            for (dx in -coverRadius..coverRadius) {
                val px = cx + dx
                val py = cy + dy
                if (px < 0 || px >= width || py < 0 || py >= height) continue
                
                val dist = sqrt((dx * dx + dy * dy).toDouble())
                if (dist <= coverRadius) {
                    pixels[py * width + px] = Color.WHITE
                }
            }
        }
        
        // 2. 清除周围残留颜色
        val cleanRadius = coverRadius + 10
        for (dy in -cleanRadius..cleanRadius) {
            for (dx in -cleanRadius..cleanRadius) {
                val px = cx + dx
                val py = cy + dy
                if (px < 0 || px >= width || py < 0 || py >= height) continue
                
                val dist = sqrt((dx * dx + dy * dy).toDouble())
                if (dist <= cleanRadius) {
                    val c = pixels[py * width + px]
                    val hsv = FloatArray(3)
                    Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), hsv)
                    if (hsv[1] > 0.05f) { // 有颜色
                        pixels[py * width + px] = Color.WHITE
                    }
                }
            }
        }
        
        // 3. 画灰色圆圈边框
        val circleR = (width * 0.049).toInt().coerceIn(35, 55) // 自适应大小
        for (angle in 0 until 360) {
            val rad = Math.toRadians(angle.toDouble())
            for (t in -1..1) {
                val px = (cx + (circleR + t) * Math.cos(rad)).toInt()
                val py = (cy + (circleR + t) * Math.sin(rad)).toInt()
                if (px in 0 until width && py in 0 until height) {
                    pixels[py * width + px] = borderGray
                }
            }
        }
        
        // 4. 画字母
        drawLetter(pixels, width, height, cx, cy, circle.letter, letterGray)
    }

    /**
     * 画字母
     */
    private fun drawLetter(
        pixels: IntArray,
        width: Int,
        height: Int,
        cx: Int,
        cy: Int,
        letter: String,
        color: Int
    ) {
        // 5x7点阵字母
        val patterns = mapOf(
            "A" to arrayOf(
                "00100",
                "01010",
                "10001",
                "10001",
                "11111",
                "10001",
                "10001"
            ),
            "B" to arrayOf(
                "11110",
                "10001",
                "10001",
                "11110",
                "10001",
                "10001",
                "11110"
            ),
            "C" to arrayOf(
                "01110",
                "10001",
                "10000",
                "10000",
                "10000",
                "10001",
                "01110"
            ),
            "D" to arrayOf(
                "11110",
                "10001",
                "10001",
                "10001",
                "10001",
                "10001",
                "11110"
            )
        )
        
        val pattern = patterns[letter] ?: return
        
        // 根据屏幕宽度调整字母大小
        val scale = (width / 150).coerceIn(4, 8)
        val letterW = 5 * scale
        val letterH = 7 * scale
        
        val startX = cx - letterW / 2
        val startY = cy - letterH / 2
        
        for ((row, line) in pattern.withIndex()) {
            for ((col, ch) in line.withIndex()) {
                if (ch == '1') {
                    for (dy in 0 until scale) {
                        for (dx in 0 until scale) {
                            val px = startX + col * scale + dx
                            val py = startY + row * scale + dy
                            if (px in 0 until width && py in 0 until height) {
                                pixels[py * width + px] = color
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 清除右上角红X
     */
    private fun clearRedX(pixels: IntArray, width: Int, height: Int) {
        val topY = (height * 0.12).toInt()
        val leftX = (width * 0.7).toInt()
        
        for (y in 0 until topY) {
            for (x in leftX until width) {
                val c = pixels[y * width + x]
                val hsv = FloatArray(3)
                Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), hsv)
                
                // 红色
                if ((hsv[0] in 0f..30f || hsv[0] in 330f..360f) && hsv[1] > 0.3f) {
                    pixels[y * width + x] = Color.WHITE
                }
            }
        }
    }
    
}
