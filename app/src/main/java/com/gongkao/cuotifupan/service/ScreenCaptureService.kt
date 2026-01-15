package com.gongkao.cuotifupan.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.gongkao.cuotifupan.MainActivity
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
        private const val NOTIFICATION_ID = 1002
        
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
        releaseMediaProjection()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "屏幕截图",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "屏幕截图服务通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在截图...")
            .setContentText("正在截取屏幕")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
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
            
            // 延迟获取图片
            handler.postDelayed({
                try {
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
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
                            Toast.makeText(this, "未选择区域", Toast.LENGTH_SHORT).show()
                            fullScreenBitmap.recycle()
                        }
                        
                    } else {
                        Toast.makeText(this, "截图失败", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "获取截图失败", e)
                    Toast.makeText(this, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    // 恢复悬浮按钮并停止服务
                    FloatingCaptureService.showFloatingButton()
                    stopSelf()
                }
            }, 100)
            
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
                
                Log.d(TAG, "题目已创建: ${question.id}")
                
                handler.post {
                    Toast.makeText(this@ScreenCaptureService, "题目已导入", Toast.LENGTH_SHORT).show()
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

