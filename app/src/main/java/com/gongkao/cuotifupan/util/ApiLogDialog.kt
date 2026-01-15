package com.gongkao.cuotifupan.util

import android.app.Activity
import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.SnapReviewApplication

/**
 * API请求日志弹窗工具类
 * 用于在请求完成后显示详细的请求和响应信息
 */
object ApiLogDialog {
    
    private const val TAG = "ApiLogDialog"
    
    // 是否启用日志弹窗（可以通过SharedPreferences控制）
    // 已禁用弹窗显示，只保留日志记录功能
    private var isEnabled: Boolean = false
    
    init {
        // 禁用弹窗显示
        isEnabled = false
        Log.e(TAG, "ApiLogDialog初始化，isEnabled=$isEnabled（弹窗已禁用）")
    }
    
    // 当前显示的对话框
    private var currentDialog: AlertDialog? = null
    
    /**
     * 显示API请求日志弹窗
     * @param context Context（如果为null，会尝试使用Application Context）
     * @param requestInfo 请求信息
     * @param responseInfo 响应信息
     * @param errorInfo 错误信息（如果有）
     */
    fun showLogDialog(
        context: Context?,
        requestInfo: RequestInfo,
        responseInfo: ResponseInfo? = null,
        errorInfo: ErrorInfo? = null
    ) {
        Log.e(TAG, "========== showLogDialog 被调用 ==========")
        Log.e(TAG, "isEnabled: $isEnabled")
        Log.e(TAG, "context: $context")
        Log.e(TAG, "requestInfo: ${requestInfo.url}")
        Log.e(TAG, "responseInfo: $responseInfo")
        Log.e(TAG, "errorInfo: $errorInfo")
        
        if (!isEnabled) {
            Log.w(TAG, "日志弹窗已禁用，跳过显示")
            return
        }
        
        val ctx = context ?: SnapReviewApplication.getInstance()
        if (ctx == null) {
            Log.e(TAG, "❌ Context为null，无法显示弹窗")
            return
        }
        
        Log.e(TAG, "使用的Context类型: ${ctx.javaClass.simpleName}")
        
        // 如果context不是Activity，尝试获取当前Activity或使用Application Context
        val activity = if (ctx is Activity) {
            Log.e(TAG, "Context是Activity，直接使用")
            ctx
        } else {
            // 尝试从Application获取当前Activity
            val currentActivity = getCurrentActivity(ctx)
            if (currentActivity != null) {
                Log.e(TAG, "通过getCurrentActivity获取到Activity")
                currentActivity
            } else {
                // 如果无法获取Activity，使用Application Context
                // 但需要确保有合适的窗口类型
                Log.w(TAG, "无法获取Activity，尝试使用Application Context显示弹窗")
                ctx
            }
        }
        
        // 在主线程显示对话框
        if (activity is Activity) {
            Log.e(TAG, "准备在主线程显示弹窗")
            // 使用Handler确保在主线程
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    Log.e(TAG, "在主线程执行showDialogInternal")
                    showDialogInternal(activity, requestInfo, responseInfo, errorInfo)
                    Log.e(TAG, "showDialogInternal执行完成")
                } catch (e: Exception) {
                    Log.e(TAG, "显示日志弹窗失败", e)
                    e.printStackTrace()
                }
            }
        } else {
            Log.e(TAG, "❌ 无法显示弹窗：context不是Activity类型")
            // 即使不是Activity，也尝试显示（可能会失败，但至少记录日志）
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    // 尝试使用Application Context创建对话框（可能会失败）
                    Log.e(TAG, "尝试使用非Activity Context显示弹窗")
                    // 这里不调用showDialogInternal，因为需要Activity
                    android.widget.Toast.makeText(ctx, "API请求完成，但无法显示日志弹窗（需要Activity）", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "显示Toast也失败", e)
                }
            }
        }
    }
    
    /**
     * 内部方法：显示对话框
     */
    private fun showDialogInternal(
        activity: Activity,
        requestInfo: RequestInfo,
        responseInfo: ResponseInfo?,
        errorInfo: ErrorInfo?
    ) {
        // 如果已有对话框显示，先关闭
        currentDialog?.dismiss()
        
        // 构建日志内容
        val logContent = buildLogContent(requestInfo, responseInfo, errorInfo)
        
        // 创建对话框视图
        val dialogView = LayoutInflater.from(activity).inflate(R.layout.dialog_api_log, null)
        val logTextView = dialogView.findViewById<TextView>(R.id.logTextView)
        val scrollView = dialogView.findViewById<ScrollView>(R.id.logScrollView)
        
        // 设置日志文本
        logTextView.text = logContent
        logTextView.movementMethod = ScrollingMovementMethod()
        
        // 创建对话框
        currentDialog = AlertDialog.Builder(activity)
            .setTitle("API请求日志")
            .setView(dialogView)
            .setPositiveButton("关闭") { dialog, _ ->
                dialog.dismiss()
                currentDialog = null
            }
            .setNegativeButton("复制") { dialog, _ ->
                copyToClipboard(activity, logContent)
                dialog.dismiss()
                currentDialog = null
            }
            .setOnDismissListener {
                currentDialog = null
            }
            .create()
        
        currentDialog?.show()
        
        // 自动滚动到底部（显示最新内容）
        scrollView.post {
            scrollView.fullScroll(android.view.View.FOCUS_DOWN)
        }
    }
    
    /**
     * 构建日志内容
     */
    private fun buildLogContent(
        requestInfo: RequestInfo,
        responseInfo: ResponseInfo?,
        errorInfo: ErrorInfo?
    ): String {
        val sb = StringBuilder()
        
        sb.append("========== HTTP请求信息 ==========\n")
        sb.append("时间: ${requestInfo.timestamp}\n")
        sb.append("URL: ${requestInfo.url}\n")
        sb.append("方法: ${requestInfo.method}\n")
        if (requestInfo.headers.isNotEmpty()) {
            sb.append("请求头:\n")
            requestInfo.headers.forEach { (key, value) ->
                sb.append("  $key: $value\n")
            }
        }
        if (requestInfo.body != null) {
            sb.append("请求体: ${requestInfo.body}\n")
        }
        sb.append("\n")
        
        if (errorInfo != null) {
            sb.append("========== 请求异常 ==========\n")
            sb.append("异常类型: ${errorInfo.exceptionType}\n")
            sb.append("异常消息: ${errorInfo.message}\n")
            if (errorInfo.stackTrace.isNotEmpty()) {
                sb.append("堆栈跟踪:\n${errorInfo.stackTrace}\n")
            }
            sb.append("耗时: ${errorInfo.duration}ms\n")
            sb.append("\n")
        } else if (responseInfo != null) {
            sb.append("========== HTTP响应信息 ==========\n")
            sb.append("状态码: ${responseInfo.statusCode}\n")
            sb.append("响应消息: ${responseInfo.message}\n")
            sb.append("耗时: ${responseInfo.duration}ms\n")
            if (responseInfo.headers.isNotEmpty()) {
                sb.append("响应头:\n")
                responseInfo.headers.forEach { (key, value) ->
                    sb.append("  $key: $value\n")
                }
            }
            if (responseInfo.body != null) {
                val bodyPreview = if (responseInfo.body.length > 500) {
                    responseInfo.body.take(500) + "\n...(内容过长，已截断)"
                } else {
                    responseInfo.body
                }
                sb.append("响应体: $bodyPreview\n")
            }
            sb.append("\n")
        }
        
        return sb.toString()
    }
    
    /**
     * 复制日志到剪贴板
     */
    private fun copyToClipboard(context: Context, text: String) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("API日志", text)
            clipboard.setPrimaryClip(clip)
            android.widget.Toast.makeText(context, "日志已复制到剪贴板", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e(TAG, "复制到剪贴板失败", e)
        }
    }
    
    /**
     * 获取当前活动的Activity（简单实现）
     */
    private fun getCurrentActivity(context: Context): Activity? {
        // 这是一个简化的实现，实际应用中可能需要更复杂的Activity管理
        // 这里返回null，让调用者处理
        return null
    }
    
    /**
     * 设置是否启用日志弹窗
     */
    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
    }
    
    /**
     * 检查是否启用
     */
    fun isEnabled(): Boolean = isEnabled
    
    /**
     * 请求信息数据类
     */
    data class RequestInfo(
        val url: String,
        val method: String,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val timestamp: String = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
    )
    
    /**
     * 响应信息数据类
     */
    data class ResponseInfo(
        val statusCode: Int,
        val message: String,
        val headers: Map<String, String> = emptyMap(),
        val body: String? = null,
        val duration: Long
    )
    
    /**
     * 错误信息数据类
     */
    data class ErrorInfo(
        val exceptionType: String,
        val message: String?,
        val stackTrace: String = "",
        val duration: Long
    )
}

