package com.gongkao.cuotifupan.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.gongkao.cuotifupan.service.FloatingCaptureService
import com.gongkao.cuotifupan.service.ScreenCaptureService

/**
 * 透明Activity，用于请求屏幕录制权限并启动截图服务
 */
class ScreenCaptureActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ScreenCaptureActivity"
        
        fun start(context: Context) {
            val intent = Intent(context, ScreenCaptureActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    private val mediaProjectionManager by lazy {
        getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Log.d(TAG, "屏幕录制权限已授予")
            // 启动截图服务
            ScreenCaptureService.start(this, result.resultCode, result.data!!)
        } else {
            Log.d(TAG, "屏幕录制权限被拒绝")
            Toast.makeText(this, "需要屏幕录制权限才能截图", Toast.LENGTH_SHORT).show()
            // 恢复悬浮按钮显示
            FloatingCaptureService.showFloatingButton()
        }
        finish()
    }

    private var hasRequestedPermission = false
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate: Activity已创建, savedInstanceState=$savedInstanceState")
        
        // 不设置布局，因为这是透明Activity
        // setContentView 不需要，因为Activity是透明的
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Activity已恢复, hasRequestedPermission=$hasRequestedPermission")
        
        // 在 onResume 中请求权限，确保 Activity 完全可见
        if (!hasRequestedPermission) {
            hasRequestedPermission = true
            // 使用 Handler 延迟请求，确保 Activity 完全显示
            handler.postDelayed({
                Log.d(TAG, "handler.postDelayed: 准备请求权限")
                if (!isFinishing && !isDestroyed) {
                    requestScreenCapturePermission()
                } else {
                    Log.w(TAG, "Activity已销毁，取消权限请求")
                }
            }, 300)
        }
    }
    
    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart: Activity已启动")
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause: Activity已暂停")
    }
    
    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop: Activity已停止")
    }

    private fun requestScreenCapturePermission() {
        try {
            Log.d(TAG, "开始请求屏幕录制权限")
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            Log.d(TAG, "创建权限请求Intent成功，准备启动")
            screenCaptureLauncher.launch(captureIntent)
            Log.d(TAG, "权限请求对话框已启动")
        } catch (e: Exception) {
            Log.e(TAG, "请求屏幕录制权限失败", e)
            e.printStackTrace()
            Toast.makeText(this, "请求权限失败: ${e.message}", Toast.LENGTH_SHORT).show()
            // 恢复悬浮按钮显示
            FloatingCaptureService.showFloatingButton()
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Activity已销毁")
        // 取消所有待处理的请求
        handler.removeCallbacksAndMessages(null)
        // 确保悬浮按钮恢复显示
        FloatingCaptureService.showFloatingButton()
    }
}
