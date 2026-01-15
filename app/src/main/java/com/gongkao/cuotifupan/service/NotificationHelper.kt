package com.gongkao.cuotifupan.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.gongkao.cuotifupan.MainActivity
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.ui.QuestionConfirmDialogActivity

/**
 * 通知助手
 */
object NotificationHelper {
    
    private const val CHANNEL_ID = "question_detected"
    private const val CHANNEL_NAME = "题目检测"
    private const val NOTIFICATION_ID = 1001
    
    /**
     * 创建通知渠道
     */
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "检测到题目时的通知"
                // 允许全屏 Intent（Android 10+）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setAllowBubbles(true)
                }
                // 设置声音和振动
                enableVibration(true)
                enableLights(true)
            }
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 显示题目检测通知（直接启动对话框 Activity，可在后台弹出）
     */
    fun showQuestionDetectedNotification(context: Context, question: Question) {
        createNotificationChannel(context)
        
        // 直接启动对话框 Activity（不通过通知）
        val intent = Intent(context, QuestionConfirmDialogActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("question_id", question.id)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // 如果直接启动失败，使用通知
            val pendingIntent = PendingIntent.getActivity(
                context,
                question.id.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("检测到一道题目")
                .setContentText("点击查看详情")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setFullScreenIntent(pendingIntent, true) // 全屏 Intent，立即显示
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setDefaults(NotificationCompat.DEFAULT_ALL) // 声音、振动等
                .build()
            
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(question.id.hashCode(), notification)
        }
    }
}

