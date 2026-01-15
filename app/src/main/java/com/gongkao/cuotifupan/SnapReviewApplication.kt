package com.gongkao.cuotifupan

import android.app.Application
import android.content.Intent
import android.os.Build
import android.util.Log
import com.gongkao.cuotifupan.api.QuestionApiQueue
import com.gongkao.cuotifupan.service.ImageMonitorService
import com.gongkao.cuotifupan.service.NotificationHelper

/**
 * Application 类
 */
class SnapReviewApplication : Application() {
    
    companion object {
        @Volatile
        private var instance: SnapReviewApplication? = null
        
        fun getInstance(): SnapReviewApplication? {
            return instance
        }
    }
    
    private val TAG = "SnapReviewApp"
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        Log.d(TAG, "Application 启动")
        
        // 初始化API请求队列（确保队列处理器启动）
        QuestionApiQueue.activeRequestsCount
        Log.d(TAG, "API请求队列已初始化")
        
        // 创建通知渠道
        // NotificationHelper.createNotificationChannel(this)
        
        // 后台服务已禁用
        // 自动启动监听服务
        // startMonitoringService()
    }
    
    // 后台服务已禁用
    // private fun startMonitoringService() {
    //     try {
    //         // Application启动时不传递permission_granted，因为此时权限可能还没授予
    //         // 真正的扫描会在MainActivity中权限授予后触发
    //         val intent = Intent(this, ImageMonitorService::class.java)
    //         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    //             startForegroundService(intent)
    //         } else {
    //             startService(intent)
    //         }
    //         Log.d(TAG, "监听服务启动成功（等待权限授予后开始扫描）")
    //     } catch (e: Exception) {
    //         Log.e(TAG, "启动监听服务失败", e)
    //     }
    // }
}

