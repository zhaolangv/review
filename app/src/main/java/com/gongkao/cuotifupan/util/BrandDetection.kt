package com.gongkao.cuotifupan.util

import android.os.Build

/**
 * 手机品牌检测工具
 * 用于根据品牌决定是否需要提示用户选择"整个屏幕"还是"单个应用"
 */
object BrandDetection {
    
    /**
     * 判断是否需要显示屏幕录制选择提示
     * 某些品牌（如小米MIUI）会在权限对话框中提供"整个屏幕"和"单个应用"的选择
     * 
     * @return true 如果该品牌需要用户选择"整个屏幕"还是"单个应用"
     */
    fun needsScreenCaptureSelectionHint(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        
        // 小米（MIUI）：需要选择，显示提示
        if (brand.contains("xiaomi") || brand.contains("redmi") || 
            manufacturer.contains("xiaomi")) {
            return true
        }
        
        // vivo：不需要选择，不显示提示
        if (brand.contains("vivo") || manufacturer.contains("vivo")) {
            return false
        }
        
        // 华为（EMUI/HarmonyOS）：通常不需要选择
        if (brand.contains("huawei") || brand.contains("honor") || 
            manufacturer.contains("huawei")) {
            return false
        }
        
        // 三星（One UI）：通常不需要选择
        if (brand.contains("samsung") || manufacturer.contains("samsung")) {
            return false
        }
        
        // OPPO（ColorOS）：通常不需要选择
        if (brand.contains("oppo") || brand.contains("realme") || 
            brand.contains("oneplus") || manufacturer.contains("oppo")) {
            return false
        }
        
        // 其他品牌：默认不显示提示（因为大多数品牌不需要选择）
        // 如果用户反馈需要，可以后续添加
        return false
    }
    
    /**
     * 获取品牌名称（用于调试）
     */
    fun getBrandName(): String {
        return "Brand: ${Build.BRAND}, Manufacturer: ${Build.MANUFACTURER}"
    }
}

