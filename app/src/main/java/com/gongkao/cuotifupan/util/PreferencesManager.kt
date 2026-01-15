package com.gongkao.cuotifupan.util

import android.content.Context
import android.content.SharedPreferences

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
}

