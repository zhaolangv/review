package com.gongkao.cuotifupan.ui

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

/**
 * 标签管理器
 * 定义预设标签和标签结构，管理用户自定义标签
 */
object TagManager {
    
    private const val PREFS_NAME = "tag_manager_prefs"
    private const val KEY_USER_TAGS = "user_tags"
    
    /**
     * 预设标签结构（简化版，只保留最基础的标签）
     */
    val presetTags = mapOf(
        "分类" to listOf(
            "基础" to listOf("行测", "申论")
        )
    )
    
    /**
     * 获取所有系统标签的扁平列表（用于搜索和选择）
     * 直接返回标签名，不包含层级结构
     */
    fun getAllSystemTags(): List<String> {
        val tags = mutableListOf<String>()
        presetTags.forEach { (category, subCategories) ->
            subCategories.forEach { (subCategory, items) ->
                items.forEach { item ->
                    // 直接返回标签名，不包含层级
                    tags.add(item)
                }
            }
        }
        return tags
    }
    
    /**
     * 获取所有标签（系统标签 + 用户自定义标签）
     */
    fun getAllTags(context: Context): List<String> {
        val tags = getAllSystemTags().toMutableList()
        tags.addAll(getUserTags(context))
        return tags.distinct()
    }
    
    /**
     * 获取用户自定义标签
     */
    fun getUserTags(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tagsJson = prefs.getString(KEY_USER_TAGS, "[]") ?: "[]"
        return try {
            JSONArray(tagsJson).let { array ->
                (0 until array.length()).map { array.getString(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 添加用户自定义标签
     */
    fun addUserTag(context: Context, tag: String) {
        if (tag.isBlank()) return
        
        val userTags = getUserTags(context).toMutableList()
        if (!userTags.contains(tag)) {
            userTags.add(tag)
            saveUserTags(context, userTags)
        }
    }
    
    /**
     * 保存用户自定义标签列表
     */
    private fun saveUserTags(context: Context, tags: List<String>) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tagsJson = JSONArray(tags).toString()
        prefs.edit().putString(KEY_USER_TAGS, tagsJson).apply()
    }
    
    /**
     * 判断标签是否为系统标签
     */
    fun isSystemTag(tag: String): Boolean {
        return getAllSystemTags().contains(tag)
    }
    
    /**
     * 格式化标签显示
     */
    fun formatTag(tag: String): String {
        return tag.replace("-", " > ")
    }
    
    /**
     * 解析标签层级
     */
    fun parseTag(tag: String): Triple<String, String, String>? {
        val parts = tag.split("-")
        return if (parts.size == 3) {
            Triple(parts[0], parts[1], parts[2])
        } else {
            null
        }
    }
    
    /**
     * 解析标签JSON字符串为列表
     */
    fun parseTags(tagsJson: String): List<String> {
        if (tagsJson.isBlank()) return emptyList()
        return try {
            JSONArray(tagsJson).let { array ->
                (0 until array.length()).map { array.getString(it) }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 格式化标签列表为显示文本（用逗号分隔）
     */
    fun formatTags(tags: List<String>): String {
        return tags.joinToString(", ")
    }
    
    /**
     * 格式化标签列表为JSON字符串
     */
    fun formatTagsToJson(tags: List<String>): String {
        return JSONArray(tags).toString()
    }
    
    /**
     * 获取第一个标签（用于排序）
     */
    fun getFirstTag(tagsJson: String): String {
        val tags = parseTags(tagsJson)
        return tags.firstOrNull() ?: ""
    }
}
