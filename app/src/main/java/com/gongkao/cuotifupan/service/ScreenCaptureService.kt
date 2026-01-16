package com.gongkao.cuotifupan.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.gongkao.cuotifupan.MainActivity
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.service.FloatingCaptureService
import com.gongkao.cuotifupan.ui.SelectRegionActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 截图服务
 * 用于执行 MediaProjection 截图操作
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "screen_capture_channel"
        private const val SUCCESS_CHANNEL_ID = "import_success_channel"
        private const val NOTIFICATION_ID = 1002
        private const val SUCCESS_NOTIFICATION_ID = 1003
        
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        
        /**
         * 启动截图服务
         */
        fun start(context: Context, resultCode: Int, resultData: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java).apply {
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, resultData)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var successTipView: View? = null
    private var isTipViewAdded = false
    private var isShowingSuccessTip = false  // 防止重复显示
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        
        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        
        if (resultCode == 0 || resultData == null) {
            Log.e(TAG, "无效的MediaProjection数据")
            stopSelf()
            return START_NOT_STICKY
        }
        
        // 启动前台服务（必须在获得授权后立即启动）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        // 执行截图
        performCapture(resultCode, resultData)
        
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        removeSuccessTip()
        isShowingSuccessTip = false  // 确保重置标志
        releaseMediaProjection()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 创建用于截图服务的通知渠道（低重要性，不打扰）
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "屏幕截图",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "屏幕截图服务通知"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
            }
            
            // 创建用于成功提示的通知渠道（高重要性，明显提示）
            val successChannelId = "import_success_channel"
            val successChannel = NotificationChannel(
                successChannelId,
                "导入成功提示",
                NotificationManager.IMPORTANCE_HIGH  // 高重要性，确保用户能看到
            ).apply {
                description = "题目导入成功通知"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC  // 锁屏可见
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(serviceChannel)
            notificationManager.createNotificationChannel(successChannel)
        }
    }
    
    private fun createNotification(title: String = "正在截图...", text: String = "正在截取屏幕"): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(false)  // 改为 false，允许用户关闭
            .setAutoCancel(true)  // 点击后自动关闭
            .build()
    }
    
    /**
     * 显示导入成功的小弹窗提示
     */
    private fun showSuccessTip() {
        // 防止重复显示
        if (isShowingSuccessTip) {
            Log.d(TAG, "导入成功提示已在显示中，跳过重复调用")
            return
        }
        
        try {
            isShowingSuccessTip = true
            
            // 检查悬浮窗权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
                Log.w(TAG, "没有悬浮窗权限，无法显示提示弹窗")
                // 如果没有权限，使用 Toast
                handler.post {
                    Toast.makeText(this, "✅ 题目已成功导入！", Toast.LENGTH_LONG).show()
                    // 延迟重置标志，避免立即重复调用
                    handler.postDelayed({
                        isShowingSuccessTip = false
                    }, 1000)
                }
                return
            }
            
            handler.post {
                // 移除之前的提示（如果存在）
                removeSuccessTip()
                
                // 创建提示视图
                val inflater = LayoutInflater.from(this)
                successTipView = inflater.inflate(R.layout.layout_success_tip, null)
                
                // 设置文本
                val tipText = successTipView?.findViewById<TextView>(R.id.tipText)
                tipText?.text = "✅ 题目已成功导入！"
                
                // 设置初始透明，用于淡入动画
                successTipView?.alpha = 0f
                
                // 创建窗口参数
                val params = WindowManager.LayoutParams().apply {
                    width = WindowManager.LayoutParams.WRAP_CONTENT
                    height = WindowManager.LayoutParams.WRAP_CONTENT
                    type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    } else {
                        @Suppress("DEPRECATION")
                        WindowManager.LayoutParams.TYPE_PHONE
                    }
                    flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                    format = PixelFormat.TRANSLUCENT
                    gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
                    y = 200  // 距离顶部200像素
                }
                
                // 添加到窗口管理器
                windowManager.addView(successTipView, params)
                isTipViewAdded = true
                
                // 淡入动画
                successTipView?.animate()
                    ?.alpha(1f)
                    ?.setDuration(300)
                    ?.start()
                
                Log.d(TAG, "导入成功提示弹窗已显示")
                
                // 3秒后自动消失
                handler.postDelayed({
                    removeSuccessTip()
                    // 重置标志，允许下次显示
                    isShowingSuccessTip = false
                }, 3000)
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示导入成功提示失败", e)
            // 失败时使用 Toast
            handler.post {
                Toast.makeText(this, "✅ 题目已成功导入！", Toast.LENGTH_LONG).show()
                // 延迟重置标志
                handler.postDelayed({
                    isShowingSuccessTip = false
                }, 1000)
            }
        }
    }
    
    /**
     * 移除成功提示弹窗
     */
    private fun removeSuccessTip() {
        if (isTipViewAdded && successTipView != null) {
            handler.post {
                try {
                    // 淡出动画
                    successTipView?.animate()
                        ?.alpha(0f)
                        ?.setDuration(300)
                        ?.withEndAction {
                            try {
                                windowManager.removeView(successTipView)
                                isTipViewAdded = false
                                successTipView = null
                                isShowingSuccessTip = false  // 重置标志
                                Log.d(TAG, "导入成功提示弹窗已移除")
                            } catch (e: Exception) {
                                Log.e(TAG, "移除提示弹窗失败", e)
                                isShowingSuccessTip = false  // 即使失败也重置标志
                            }
                        }
                        ?.start()
                } catch (e: Exception) {
                    Log.e(TAG, "移除提示弹窗失败", e)
                    // 如果动画失败，直接移除
                    try {
                        windowManager.removeView(successTipView)
                        isTipViewAdded = false
                        successTipView = null
                        isShowingSuccessTip = false  // 重置标志
                    } catch (e2: Exception) {
                        Log.e(TAG, "强制移除提示弹窗失败", e2)
                        isShowingSuccessTip = false  // 即使失败也重置标志
                    }
                }
            }
        } else {
            // 如果没有弹窗，直接重置标志
            isShowingSuccessTip = false
        }
    }
    
    private fun performCapture(resultCode: Int, resultData: Intent) {
        try {
            val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
            
            if (mediaProjection == null) {
                Log.e(TAG, "无法创建MediaProjection")
                Toast.makeText(this, "截图失败", Toast.LENGTH_SHORT).show()
                stopSelf()
                return
            }
            
            // Android 14+ 要求注册回调
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        Log.d(TAG, "MediaProjection stopped")
                        releaseMediaProjection()
                    }
                }, handler)
            }
            
            captureScreen()
        } catch (e: Exception) {
            Log.e(TAG, "截图失败", e)
            Toast.makeText(this, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }
    
    private fun captureScreen() {
        try {
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(metrics)
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels
            val screenDensity = metrics.densityDpi
            
            imageReader = ImageReader.newInstance(
                screenWidth, screenHeight,
                PixelFormat.RGBA_8888, 2
            )
            
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenCapture",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, handler
            )
            
            // 等待 VirtualDisplay 准备就绪，增加延迟时间并多次尝试
            var attemptCount = 0
            val maxAttempts = 5
            val attemptDelay = 200L  // 每次尝试间隔 200ms
            
            fun tryCapture(delay: Long = attemptDelay) {
                handler.postDelayed({
                    try {
                        val image = imageReader?.acquireLatestImage()
                        if (image != null) {
                            attemptCount = maxAttempts  // 成功获取，停止重试
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * screenWidth
                            
                            val bitmap = Bitmap.createBitmap(
                                screenWidth + rowPadding / pixelStride,
                                screenHeight,
                                Bitmap.Config.ARGB_8888
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            image.close()
                            
                            // 裁剪掉padding
                            var fullScreenBitmap = if (rowPadding > 0) {
                                val cropped = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                                bitmap.recycle()
                                cropped
                            } else {
                                bitmap
                            }
                            
                            // 根据预先选择的区域裁剪
                            val region = SelectRegionActivity.selectedRegion
                            if (region != null && region.width() > 10 && region.height() > 10) {
                                val left = region.left.toInt().coerceIn(0, screenWidth - 1)
                                val top = region.top.toInt().coerceIn(0, screenHeight - 1)
                                val right = region.right.toInt().coerceIn(left + 1, screenWidth)
                                val bottom = region.bottom.toInt().coerceIn(top + 1, screenHeight)
                                
                                val croppedBitmap = Bitmap.createBitmap(
                                    fullScreenBitmap,
                                    left, top,
                                    right - left,
                                    bottom - top
                                )
                                fullScreenBitmap.recycle()
                                
                                // 保存并创建题目
                                saveAndCreateQuestion(croppedBitmap)
                                croppedBitmap.recycle()
                                
                                // 清除选区
                                SelectRegionActivity.selectedRegion = null
                            } else {
                                Log.w(TAG, "未选择区域")
                                fullScreenBitmap.recycle()
                                handler.post {
                                    Toast.makeText(this@ScreenCaptureService, "未选择区域", Toast.LENGTH_SHORT).show()
                                }
                            }
                            
                            // 恢复悬浮按钮并停止服务
                            FloatingCaptureService.showFloatingButton()
                            stopSelf()
                        } else {
                            // 图像未准备好，重试
                            attemptCount++
                            if (attemptCount < maxAttempts) {
                                Log.d(TAG, "图像未准备好，重试 ($attemptCount/$maxAttempts)")
                                tryCapture(attemptDelay)
                            } else {
                                Log.e(TAG, "截图失败：无法获取图像")
                                handler.post {
                                    Toast.makeText(this@ScreenCaptureService, "截图失败：无法获取图像", Toast.LENGTH_SHORT).show()
                                }
                                FloatingCaptureService.showFloatingButton()
                                stopSelf()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "获取截图失败", e)
                        handler.post {
                            Toast.makeText(this@ScreenCaptureService, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                        FloatingCaptureService.showFloatingButton()
                        stopSelf()
                    }
                }, delay)  // 添加延迟时间参数
            }
            
            // 开始第一次尝试
            tryCapture()
            
        } catch (e: Exception) {
            Log.e(TAG, "截图失败", e)
            Toast.makeText(this, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }
    
    private fun releaseMediaProjection() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }
    
    /**
     * 保存截图并创建题目
     */
    private fun saveAndCreateQuestion(bitmap: Bitmap) {
        Log.d(TAG, "saveAndCreateQuestion 被调用")
        // 创建bitmap副本，因为这是异步操作，原bitmap可能会被回收
        val config = bitmap.config ?: Bitmap.Config.ARGB_8888
        val bitmapCopy = bitmap.copy(config, false) ?: run {
            Log.e(TAG, "无法创建bitmap副本")
            handler.post {
                Toast.makeText(this, "截图处理失败", Toast.LENGTH_SHORT).show()
            }
            FloatingCaptureService.showFloatingButton()
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "开始保存题目到数据库")
            try {
                // 保存到文件
                val questionsDir = File(filesDir, "questions")
                if (!questionsDir.exists()) {
                    questionsDir.mkdirs()
                }
                
                val fileName = "question_${System.currentTimeMillis()}.jpg"
                val file = File(questionsDir, fileName)
                
                FileOutputStream(file).use { out ->
                    bitmapCopy.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                
                // 回收副本
                bitmapCopy.recycle()
                
                // 创建题目
                val database = AppDatabase.getDatabase(this@ScreenCaptureService)
                val question = Question(
                    id = UUID.randomUUID().toString(),
                    imagePath = file.absolutePath,
                    rawText = "",
                    questionText = "快速截图导入",
                    createdAt = System.currentTimeMillis(),
                    reviewState = "unreviewed"
                )
                database.questionDao().insert(question)
                
                Log.d(TAG, "题目已创建: ${question.id}, isShowingSuccessTip=$isShowingSuccessTip")
                
                handler.post {
                    Log.d(TAG, "准备显示导入成功提示, isShowingSuccessTip=$isShowingSuccessTip")
                    // 显示悬浮提示弹窗（用户在其他应用时也能看到）
                    showSuccessTip()
                    Log.d(TAG, "导入成功提示调用完成, isShowingSuccessTip=$isShowingSuccessTip")
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存题目失败", e)
                // 确保回收bitmap
                if (!bitmapCopy.isRecycled) {
                    bitmapCopy.recycle()
                }
                handler.post {
                    Toast.makeText(this@ScreenCaptureService, "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

