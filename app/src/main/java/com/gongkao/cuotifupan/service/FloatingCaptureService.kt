package com.gongkao.cuotifupan.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.gongkao.cuotifupan.MainActivity
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.ui.SelectRegionActivity

/**
 * 悬浮窗服务
 * 提供一个悬浮按钮，点击后请求截图
 */
class FloatingCaptureService : Service() {

    companion object {
        private const val TAG = "FloatingCaptureService"
        private const val CHANNEL_ID = "floating_capture_channel"
        private const val NOTIFICATION_ID = 1001
        
        // 服务运行状态
        var isRunning = false
        
        // 服务实例引用
        private var instance: FloatingCaptureService? = null
        
        /**
         * 启动服务
         */
        fun start(context: Context) {
            val intent = Intent(context, FloatingCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        /**
         * 停止服务
         */
        fun stop(context: Context) {
            val intent = Intent(context, FloatingCaptureService::class.java)
            context.stopService(intent)
        }
        
        /**
         * 隐藏悬浮按钮（截图前调用）
         */
        fun hideFloatingButton() {
            instance?.floatingView?.visibility = View.INVISIBLE
        }
        
        /**
         * 显示悬浮按钮（截图后调用）
         */
        fun showFloatingButton() {
            instance?.floatingView?.visibility = View.VISIBLE
        }
    }

    private lateinit var windowManager: WindowManager
    internal var floatingView: View? = null
    private var isFloatingViewAdded = false
    private var floatingViewParams: WindowManager.LayoutParams? = null
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service onCreate")
        isRunning = true
        instance = this
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createNotification(), 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        showFloatingButtonView()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service onStartCommand")
        if (intent?.action == "STOP_SERVICE") {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service onDestroy")
        isRunning = false
        instance = null
        
        removeFloatingButton()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮截图",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮截图服务通知"
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
        
        // 停止服务的Intent
        val stopIntent = Intent(this, FloatingCaptureService::class.java).apply {
            action = "STOP_SERVICE"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("悬浮截图")
            .setContentText("点击悬浮按钮截取屏幕")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭", stopPendingIntent)
            .setOngoing(true)
            .build()
    }
    
    private fun showFloatingButtonView() {
        if (isFloatingViewAdded) return
        
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            Log.e(TAG, "没有悬浮窗权限")
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }
        
        val inflater = LayoutInflater.from(this)
        floatingView = inflater.inflate(R.layout.layout_floating_capture, null)
        
        floatingViewParams = WindowManager.LayoutParams().apply {
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
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }
        
        // 截图按钮点击事件
        val captureButton = floatingView?.findViewById<ImageButton>(R.id.btnCapture)
        val closeButton = floatingView?.findViewById<ImageButton>(R.id.btnCloseFloating)
        
        // 设置拖动功能 - 在整个悬浮窗上都可以拖动
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false
        var hasMoved = false
        
        // 先设置按钮的点击事件
        captureButton?.setOnClickListener {
            if (!hasMoved) {
                Log.d(TAG, "点击截图按钮")
                startCapture()
            }
        }
        
        closeButton?.setOnClickListener {
            if (!hasMoved) {
                Log.d(TAG, "点击关闭按钮")
                stopSelf()
            }
        }
        
        // 关键：让按钮不拦截触摸事件，让父视图处理
        captureButton?.isClickable = false
        captureButton?.isFocusable = false
        captureButton?.isLongClickable = false
        
        closeButton?.isClickable = false
        closeButton?.isFocusable = false
        closeButton?.isLongClickable = false
        
        // 在整个悬浮窗上设置拖动监听 - 使用 setOnTouchListener 拦截所有触摸事件
        floatingView?.setOnTouchListener { view, event ->
            Log.d(TAG, "onTouch: action=${event.action}, x=${event.x}, y=${event.y}, rawX=${event.rawX}, rawY=${event.rawY}")
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = floatingViewParams?.x ?: 0
                    initialY = floatingViewParams?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    hasMoved = false
                    Log.d(TAG, "ACTION_DOWN: initialX=$initialX, initialY=$initialY, rawX=$initialTouchX, rawY=$initialTouchY")
                    true // 返回 true 拦截事件
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - initialTouchX
                    val deltaY = event.rawY - initialTouchY
                    val absDeltaX = Math.abs(deltaX)
                    val absDeltaY = Math.abs(deltaY)
                    
                    Log.d(TAG, "ACTION_MOVE: deltaX=$deltaX, deltaY=$deltaY, absDeltaX=$absDeltaX, absDeltaY=$absDeltaY")
                    
                    // 如果移动距离超过阈值，认为是拖动
                    if (absDeltaX > 10 || absDeltaY > 10) {
                        hasMoved = true
                        if (!isDragging) {
                            isDragging = true
                            Log.d(TAG, "开始拖动")
                        }
                    }
                    
                    if (isDragging) {
                        floatingViewParams?.let { params ->
                            val newX = initialX + deltaX.toInt()
                            val newY = initialY + deltaY.toInt()
                            params.x = newX
                            params.y = newY
                            try {
                                windowManager.updateViewLayout(floatingView, params)
                                Log.d(TAG, "更新位置: x=$newX, y=$newY")
                            } catch (e: Exception) {
                                Log.e(TAG, "更新位置失败", e)
                            }
                        }
                    }
                    true // 返回 true 拦截事件
                }
                MotionEvent.ACTION_UP -> {
                    val moveDeltaX = Math.abs(event.rawX - initialTouchX)
                    val moveDeltaY = Math.abs(event.rawY - initialTouchY)
                    val totalMove = Math.sqrt((moveDeltaX * moveDeltaX + moveDeltaY * moveDeltaY).toDouble())
                    
                    Log.d(TAG, "ACTION_UP: hasMoved=$hasMoved, isDragging=$isDragging, totalMove=$totalMove")
                    
                    if (!hasMoved && totalMove < 20) {
                        // 如果没有拖动，检查是否点击了按钮（使用相对坐标）
                        val x = event.x
                        val y = event.y
                        
                        captureButton?.let { btn ->
                            val btnLeft = btn.left.toFloat()
                            val btnTop = btn.top.toFloat()
                            val btnRight = btn.right.toFloat()
                            val btnBottom = btn.bottom.toFloat()
                            
                            Log.d(TAG, "检查截图按钮: x=$x, y=$y, btnLeft=$btnLeft, btnTop=$btnTop, btnRight=$btnRight, btnBottom=$btnBottom")
                            
                            if (x >= btnLeft && x <= btnRight && y >= btnTop && y <= btnBottom) {
                                Log.d(TAG, "点击截图按钮区域")
                                startCapture()
                                return@setOnTouchListener true
                            }
                        }
                        
                        closeButton?.let { btn ->
                            val btnLeft = btn.left.toFloat()
                            val btnTop = btn.top.toFloat()
                            val btnRight = btn.right.toFloat()
                            val btnBottom = btn.bottom.toFloat()
                            
                            Log.d(TAG, "检查关闭按钮: x=$x, y=$y, btnLeft=$btnLeft, btnTop=$btnTop, btnRight=$btnRight, btnBottom=$btnBottom")
                            
                            if (x >= btnLeft && x <= btnRight && y >= btnTop && y <= btnBottom) {
                                Log.d(TAG, "点击关闭按钮区域")
                                stopSelf()
                                return@setOnTouchListener true
                            }
                        }
                    }
                    isDragging = false
                    hasMoved = false
                    true // 返回 true 拦截事件
                }
                MotionEvent.ACTION_CANCEL -> {
                    Log.d(TAG, "ACTION_CANCEL")
                    isDragging = false
                    hasMoved = false
                    true
                }
                else -> false
            }
        }
        
        try {
            windowManager.addView(floatingView, floatingViewParams)
            isFloatingViewAdded = true
            Log.d(TAG, "悬浮按钮已添加")
        } catch (e: Exception) {
            Log.e(TAG, "添加悬浮按钮失败", e)
        }
    }
    
    private fun removeFloatingButton() {
        if (isFloatingViewAdded && floatingView != null) {
            try {
                windowManager.removeView(floatingView)
                isFloatingViewAdded = false
                Log.d(TAG, "悬浮按钮已移除")
            } catch (e: Exception) {
                Log.e(TAG, "移除悬浮按钮失败", e)
            }
        }
    }
    
    /**
     * 开始截图流程
     */
    private fun startCapture() {
        Log.d(TAG, "开始截图流程")
        // 隐藏悬浮按钮
        floatingView?.visibility = View.INVISIBLE
        // 打开框选Activity
        SelectRegionActivity.start(this)
    }
}
