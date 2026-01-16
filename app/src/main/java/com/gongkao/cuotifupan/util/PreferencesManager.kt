package com.gongkao.cuotifupan.util

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog

/**
 * SharedPreferences 管理类
 */
object PreferencesManager {
    
    private const val PREFS_NAME = "snap_review_prefs"
    private const val KEY_LAST_PROCESSED_IMAGE_ID = "last_processed_image_id"
    private const val KEY_LAST_IMAGE_COUNT = "last_image_count"
    private const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
    private const val KEY_INITIAL_SCAN_COMPLETED = "initial_scan_completed"
    
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
    
    /**
     * 保存最后处理的图片 ID
     */
    fun saveLastProcessedImageId(context: Context, id: Long) {
        getPrefs(context).edit().putLong(KEY_LAST_PROCESSED_IMAGE_ID, id).apply()
    }
    
    /**
     * 获取最后处理的图片 ID
     */
    fun getLastProcessedImageId(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_PROCESSED_IMAGE_ID, 0)
    }
    
    /**
     * 保存图片总数
     */
    fun saveImageCount(context: Context, count: Int) {
        getPrefs(context).edit().putInt(KEY_LAST_IMAGE_COUNT, count).apply()
    }
    
    /**
     * 获取图片总数
     */
    fun getImageCount(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_IMAGE_COUNT, 0)
    }
    
    /**
     * 是否首次启动
     */
    fun isFirstLaunch(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_IS_FIRST_LAUNCH, true)
    }
    
    /**
     * 设置首次启动标记
     */
    fun setFirstLaunchCompleted(context: Context) {
        getPrefs(context).edit().putBoolean(KEY_IS_FIRST_LAUNCH, false).apply()
    }
    
    /**
     * 初始扫描是否完成
     */
    fun isInitialScanCompleted(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_INITIAL_SCAN_COMPLETED, false)
    }
    
    /**
     * 设置初始扫描完成
     */
    fun setInitialScanCompleted(context: Context, completed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_INITIAL_SCAN_COMPLETED, completed).apply()
    }
    
    /**
     * 保存最新图片的时间戳（用于快速判断是否有新图片）
     */
    fun saveLatestImageTimestamp(context: Context, timestamp: Long) {
        getPrefs(context).edit().putLong("latest_image_timestamp", timestamp).apply()
    }
    
    /**
     * 获取最新图片的时间戳
     */
    fun getLatestImageTimestamp(context: Context): Long {
        return getPrefs(context).getLong("latest_image_timestamp", 0)
    }
    
    /**
     * 保存指定题目的图片区域占总高度的比例（0.0-1.0之间，默认约0.714，即1.0/(1.0+0.4)）
     */
    fun saveImageHeightRatioForQuestion(context: Context, questionId: String, ratio: Float) {
        getPrefs(context).edit().putFloat("image_height_ratio_$questionId", ratio.coerceIn(0.2f, 0.85f)).apply()
    }
    
    /**
     * 获取指定题目的图片区域占总高度的比例（默认约0.714，即1.0/(1.0+0.4)）
     */
    fun getImageHeightRatioForQuestion(context: Context, questionId: String): Float {
        return getPrefs(context).getFloat("image_height_ratio_$questionId", 0.5f) // 0.5 / (0.5 + 0.5) = 0.5
    }
    
    /**
     * 保存图片区域占总高度的比例（0.0-1.0之间，默认约0.714，即1.0/(1.0+0.4)）
     * @deprecated 使用 saveImageHeightRatioForQuestion 代替，为每道题单独保存
     */
    @Deprecated("使用 saveImageHeightRatioForQuestion 代替")
    fun saveImageHeightRatio(context: Context, ratio: Float) {
        getPrefs(context).edit().putFloat("image_height_ratio", ratio.coerceIn(0.2f, 0.85f)).apply()
    }
    
    /**
     * 获取图片区域占总高度的比例（默认约0.714，即1.0/(1.0+0.4)）
     * @deprecated 使用 getImageHeightRatioForQuestion 代替，为每道题单独获取
     */
    @Deprecated("使用 getImageHeightRatioForQuestion 代替")
    fun getImageHeightRatio(context: Context): Float {
        return getPrefs(context).getFloat("image_height_ratio", 0.5f) // 0.5 / (0.5 + 0.5) = 0.5
    }
    
    /**
     * 保存指定题目的图片滚动位置
     */
    fun saveImageScrollPositionForQuestion(context: Context, questionId: String, scrollY: Int) {
        getPrefs(context).edit().putInt("image_scroll_position_$questionId", scrollY).apply()
    }
    
    /**
     * 获取指定题目的图片滚动位置（默认0，即顶部）
     */
    fun getImageScrollPositionForQuestion(context: Context, questionId: String): Int {
        return getPrefs(context).getInt("image_scroll_position_$questionId", 0)
    }
    
    /**
     * 保存前N张图片的信息（用于快速检查是否有变化）
     */
    fun saveTopImagesInfo(context: Context, imageInfos: List<Pair<String, Long>>) {
        val json = org.json.JSONArray()
        imageInfos.forEach { (fileName, fileSize) ->
            val obj = org.json.JSONObject()
            obj.put("fileName", fileName)
            obj.put("fileSize", fileSize)
            json.put(obj)
        }
        getPrefs(context).edit().putString("top_images_info", json.toString()).apply()
    }
    
    /**
     * 获取前N张图片的信息
     */
    fun getTopImagesInfo(context: Context): List<Pair<String, Long>> {
        val jsonString = getPrefs(context).getString("top_images_info", null) ?: return emptyList()
        return try {
            val json = org.json.JSONArray(jsonString)
            (0 until json.length()).map { i ->
                val obj = json.getJSONObject(i)
                Pair(obj.getString("fileName"), obj.getLong("fileSize"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取清除对错痕迹提示的显示次数
     */
    fun getClearMarksHintCount(context: Context): Int {
        return getPrefs(context).getInt("clear_marks_hint_count", 0)
    }
    
    /**
     * 增加清除对错痕迹提示的显示次数
     */
    fun incrementClearMarksHintCount(context: Context) {
        val currentCount = getClearMarksHintCount(context)
        getPrefs(context).edit().putInt("clear_marks_hint_count", currentCount + 1).apply()
    }
    
    /**
     * 是否应该显示清除对错痕迹的提示（前5次显示）
     */
    fun shouldShowClearMarksHint(context: Context): Boolean {
        return getClearMarksHintCount(context) < 5
    }
    
    /**
     * 通用的页面提示管理
     */
    
    /**
     * 获取指定页面的提示显示次数
     */
    fun getPageHintCount(context: Context, pageKey: String): Int {
        return getPrefs(context).getInt("page_hint_count_$pageKey", 0)
    }
    
    /**
     * 增加指定页面的提示显示次数
     */
    fun incrementPageHintCount(context: Context, pageKey: String) {
        val currentCount = getPageHintCount(context, pageKey)
        getPrefs(context).edit().putInt("page_hint_count_$pageKey", currentCount + 1).apply()
    }
    
    /**
     * 是否应该显示指定页面的提示（前3次显示）
     */
    fun shouldShowPageHint(context: Context, pageKey: String, maxCount: Int = 3): Boolean {
        return getPageHintCount(context, pageKey) < maxCount
    }
    
    /**
     * 页面提示的Key常量
     */
    object PageKeys {
        const val MAIN = "main"
        const val HANDWRITING_NOTE = "handwriting_note"
        const val IMAGE_EDIT = "image_edit"
        const val QUESTION_DETAIL = "question_detail"
        const val FLASHCARD_REVIEW = "flashcard_review"
        const val SCREEN_HANDWRITING = "screen_handwriting"
        const val MANUAL_IMPORT = "manual_import"
        const val CAMERA_CAPTURE = "camera_capture"
        const val NOTES_LIST = "notes_list"
        const val FLASHCARDS_LIST = "flashcards_list"
    }
    
    /**
     * 显示页面使用提示（如果应该显示）
     * @param context Activity上下文
     * @param pageKey 页面标识（使用PageKeys常量）
     * @param title 提示标题
     * @param message 提示内容
     * @param maxCount 最多显示次数，默认3次
     * @return 如果显示了提示对话框，返回true；否则返回false
     */
    fun showPageHintIfNeeded(
        context: Context,
        pageKey: String,
        title: String,
        message: String,
        maxCount: Int = 3
    ): Boolean {
        if (!shouldShowPageHint(context, pageKey, maxCount)) {
            return false
        }
        
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("知道了") { _, _ ->
                incrementPageHintCount(context, pageKey)
            }
            .setCancelable(true)
            .show()
        
        return true
    }
}

