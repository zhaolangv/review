package com.gongkao.cuotifupan.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.InputStream

/**
 * 图片访问助手 - 兼容 Android 10+ Scoped Storage
 * 
 * Android 10+ 不能直接通过文件路径访问其他应用的媒体文件（即使有权限）
 * 必须通过 MediaStore URI + ContentResolver 访问
 */
object ImageAccessHelper {
    
    private const val TAG = "ImageAccessHelper"
    
    /**
     * 根据文件路径获取 MediaStore URI
     * @param context Context
     * @param imagePath 文件路径
     * @return MediaStore URI，如果找不到返回 null
     */
    fun getImageUri(context: Context, imagePath: String): Uri? {
        try {
            // 如果路径已经是 content:// URI，直接返回
            if (imagePath.startsWith("content://")) {
                return Uri.parse(imagePath)
            }
            
            // 如果路径是 file:// URI，提取路径
            val path = if (imagePath.startsWith("file://")) {
                imagePath.substring(7)
            } else {
                imagePath
            }
            
            // 查询 MediaStore 获取 URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA
            )
            
            val selection = "${MediaStore.Images.Media.DATA}=?"
            val selectionArgs = arrayOf(path)
            
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val cursor = context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )
            
            cursor?.use {
                if (it.moveToFirst()) {
                    val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Images.Media._ID))
                    return Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                }
            }
            
            // 如果在 MediaStore 中找不到，可能是应用私有文件，尝试直接访问
            val file = File(path)
            if (file.exists()) {
                return Uri.fromFile(file)
            }
            
            Log.w(TAG, "无法找到图片的 URI: $imagePath")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "获取图片 URI 失败: $imagePath", e)
            return null
        }
    }
    
    /**
     * 解码图片（完整）
     * @param context Context
     * @param imagePath 文件路径或 URI
     * @return Bitmap，失败返回 null
     */
    fun decodeBitmap(context: Context, imagePath: String): Bitmap? {
        return try {
            val uri = getImageUri(context, imagePath) ?: return null
            
            // 尝试通过 ContentResolver 访问
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解码图片失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 解码图片（带选项）
     * @param context Context
     * @param imagePath 文件路径或 URI
     * @param options BitmapFactory.Options
     * @return Bitmap，失败返回 null
     */
    fun decodeBitmap(context: Context, imagePath: String, options: BitmapFactory.Options): Bitmap? {
        return try {
            val uri = getImageUri(context, imagePath) ?: return null
            
            // 如果是仅解码边界，需要先读取一次
            if (options.inJustDecodeBounds) {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, options)
                }
                // inJustDecodeBounds 模式返回 null 是正常的
                return null
            }
            
            // 正常解码
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
        } catch (e: Exception) {
            Log.e(TAG, "解码图片失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 获取图片的 InputStream
     * @param context Context
     * @param imagePath 文件路径或 URI
     * @return InputStream，失败返回 null
     */
    fun openInputStream(context: Context, imagePath: String): InputStream? {
        return try {
            val uri = getImageUri(context, imagePath) ?: return null
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            Log.e(TAG, "打开图片流失败: $imagePath", e)
            null
        }
    }
    
    /**
     * 检查图片文件是否有效
     * @param context Context
     * @param imagePath 文件路径或 URI
     * @return true 如果文件有效
     */
    fun isValidImage(context: Context, imagePath: String): Boolean {
        return try {
            val uri = getImageUri(context, imagePath) ?: return false
            
            // 尝试解码边界信息
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            // 检查是否成功解码
            options.outWidth > 0 && options.outHeight > 0
        } catch (e: Exception) {
            Log.w(TAG, "验证图片失败: $imagePath", e)
            false
        }
    }
    
    /**
     * 获取图片尺寸
     * @param context Context
     * @param imagePath 文件路径或 URI
     * @return Pair<width, height>，失败返回 Pair(0, 0)
     */
    fun getImageSize(context: Context, imagePath: String): Pair<Int, Int> {
        return try {
            val uri = getImageUri(context, imagePath) ?: return Pair(0, 0)
            
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            Pair(options.outWidth, options.outHeight)
        } catch (e: Exception) {
            Log.e(TAG, "获取图片尺寸失败: $imagePath", e)
            Pair(0, 0)
        }
    }
    
    /**
     * 复制图片到应用私有目录（如果需要长期保存）
     * @param context Context
     * @param imagePath 源文件路径或 URI
     * @param destFile 目标文件
     * @return true 如果复制成功
     */
    fun copyToPrivateStorage(context: Context, imagePath: String, destFile: File): Boolean {
        return try {
            val uri = getImageUri(context, imagePath) ?: return false
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            destFile.exists() && destFile.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "复制图片失败: $imagePath", e)
            false
        }
    }
    
    /**
     * 保存图片到应用的永久存储目录（用于题目图片）
     * @param context Context
     * @param imagePath 源文件路径或 URI
     * @param questionId 题目ID（用于生成唯一文件名）
     * @return 保存后的文件路径，失败返回 null
     */
    fun saveImageToPermanentStorage(context: Context, imagePath: String, questionId: String): String? {
        return try {
            // 如果图片已经在公共存储目录（DCIM/Camera），不需要复制到应用私有目录
            // 直接使用公共存储目录的路径，这样用户从相册删除图片时，题目也会被删除
            val sourceFile = File(imagePath)
            if (sourceFile.exists() && imagePath.contains("/DCIM/Camera/")) {
                Log.d(TAG, "图片已在公共存储目录，直接使用: $imagePath")
                return imagePath
            }
            
            // 如果图片已经在 files 目录的 questions 子目录，不需要复制
            if (imagePath.startsWith(File(context.filesDir, "questions").absolutePath)) {
                Log.d(TAG, "图片已在永久存储目录，直接使用: $imagePath")
                return imagePath
            }
            
            // cache 目录的文件应该复制到永久存储（cache 目录可能被系统清理）
            // 如果是 cache 目录的文件，需要复制到 files/questions 目录
            
            // 创建 questions 目录
            val questionsDir = File(context.filesDir, "questions")
            if (!questionsDir.exists()) {
                questionsDir.mkdirs()
            }
            
            // 从源路径获取文件扩展名
            val extension = sourceFile.extension.ifEmpty { "jpg" }
            
            // 生成唯一文件名：question_{questionId}.{extension}
            val destFile = File(questionsDir, "question_${questionId}.$extension")
            
            // 复制文件
            val success = copyToPrivateStorage(context, imagePath, destFile)
            if (success) {
                Log.d(TAG, "✅ 图片已保存到永久存储: ${destFile.absolutePath}")
                destFile.absolutePath
            } else {
                Log.e(TAG, "❌ 保存图片到永久存储失败: $imagePath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存图片到永久存储失败: $imagePath", e)
            null
        }
    }
}

