package com.gongkao.cuotifupan.ui

/**
 * 图片路径缓存（用于在Activity之间传递大量数据，避免TransactionTooLargeException）
 */
object ImagePathCache {
    private var imagePaths: List<String>? = null
    
    /**
     * 设置图片路径列表
     */
    fun setImagePaths(paths: List<String>) {
        imagePaths = paths
    }
    
    /**
     * 获取图片路径列表
     */
    fun getImagePaths(): List<String>? = imagePaths
    
    /**
     * 清除缓存
     */
    fun clear() {
        imagePaths = null
    }
}

