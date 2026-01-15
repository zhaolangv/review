package com.gongkao.cuotifupan.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 请求屏幕录制权限
        requestScreenCapturePermission()
    }

    private fun requestScreenCapturePermission() {
        try {
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(captureIntent)
        } catch (e: Exception) {
            Log.e(TAG, "请求屏幕录制权限失败", e)
            Toast.makeText(this, "请求权限失败: ${e.message}", Toast.LENGTH_SHORT).show()
            // 恢复悬浮按钮显示
            FloatingCaptureService.showFloatingButton()
            finish()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 确保悬浮按钮恢复显示
        FloatingCaptureService.showFloatingButton()
    }
}
