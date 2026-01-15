package com.gongkao.cuotifupan.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.service.FloatingCaptureService
import com.gongkao.cuotifupan.util.ProManager
import com.gongkao.cuotifupan.util.VersionChecker

/**
 * Pro 服务页面
 */
class ProFragment : Fragment() {
    
    private lateinit var proStatusText: TextView
    private lateinit var quotaText: TextView
    private lateinit var currentPeriodCard: androidx.cardview.widget.CardView
    private lateinit var nextPeriodCard: androidx.cardview.widget.CardView
    private lateinit var nextPeriodQuotaText: TextView
    private lateinit var activateButton: Button
    private lateinit var migrationButton: Button
    private lateinit var deviceIdText: TextView
    private lateinit var copyDeviceIdButton: ImageButton
    private lateinit var floatingCaptureSwitch: SwitchMaterial
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pro, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Pro 服务"
        
        proStatusText = view.findViewById(R.id.proStatusText)
        quotaText = view.findViewById(R.id.quotaText)
        currentPeriodCard = view.findViewById(R.id.currentPeriodCard)
        nextPeriodCard = view.findViewById(R.id.nextPeriodCard)
        nextPeriodQuotaText = view.findViewById(R.id.nextPeriodQuotaText)
        activateButton = view.findViewById(R.id.activateButton)
        migrationButton = view.findViewById(R.id.migrationButton)
        deviceIdText = view.findViewById(R.id.deviceIdText)
        copyDeviceIdButton = view.findViewById(R.id.copyDeviceIdButton)
        
        activateButton.setOnClickListener {
            val intent = Intent(requireContext(), RedemptionCodeActivity::class.java)
            startActivity(intent)
        }
        
        migrationButton.setOnClickListener {
            val intent = Intent(requireContext(), DataMigrationActivity::class.java)
            startActivity(intent)
        }
        
        // 显示设备ID
        updateDeviceId()
        
        // 复制设备ID按钮
        copyDeviceIdButton.setOnClickListener {
            copyDeviceIdToClipboard()
        }
        
        // 悬浮截图开关
        floatingCaptureSwitch = view.findViewById(R.id.floatingCaptureSwitch)
        floatingCaptureSwitch.isChecked = FloatingCaptureService.isRunning
        floatingCaptureSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                startFloatingCaptureService()
            } else {
                stopFloatingCaptureService()
            }
        }
        
        updateProStatus()
    }
    
    /**
     * 启动悬浮截图服务
     */
    private fun startFloatingCaptureService() {
        val ctx = requireContext()
        
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(ctx)) {
            Toast.makeText(ctx, "请授予悬浮窗权限", Toast.LENGTH_LONG).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${ctx.packageName}")
            )
            startActivity(intent)
            floatingCaptureSwitch.isChecked = false
            return
        }
        
        // 启动服务
        val serviceIntent = Intent(ctx, FloatingCaptureService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ctx.startForegroundService(serviceIntent)
        } else {
            ctx.startService(serviceIntent)
        }
        Toast.makeText(ctx, "悬浮快捷入口已启动", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 停止悬浮截图服务
     */
    private fun stopFloatingCaptureService() {
        val ctx = requireContext()
        val serviceIntent = Intent(ctx, FloatingCaptureService::class.java)
        ctx.stopService(serviceIntent)
        Toast.makeText(ctx, "悬浮快捷入口已停止", Toast.LENGTH_SHORT).show()
    }
    
    override fun onResume() {
        super.onResume()
        // 从激活页面返回时更新状态
        updateProStatus()
        // 更新悬浮截图开关状态
        if (::floatingCaptureSwitch.isInitialized) {
            floatingCaptureSwitch.isChecked = FloatingCaptureService.isRunning
        }
    }
    
    /**
     * 公开方法，供外部调用更新状态（如版本检查后更新配额）
     */
    fun refreshStatus() {
        if (::proStatusText.isInitialized) {
            updateDeviceId()
            updateProStatus()
        }
    }
    
    private fun updateProStatus() {
        val ctx = requireContext()
        val tierName = ProManager.getTierName(ctx) ?: ""
        val tierDisplay = if (tierName.isNotEmpty()) " - $tierName" else ""
        
        if (ProManager.isPro(ctx)) {
            val expiresAt = ProManager.getExpiresAtFormatted(ctx)
            val daysRemaining = ProManager.getDaysRemaining(ctx)
            proStatusText.text = "✅ Pro 服务已激活$tierDisplay\n到期时间: $expiresAt\n剩余天数: $daysRemaining 天"
            proStatusText.setTextColor(ctx.getColor(android.R.color.holo_green_dark))
            activateButton.text = "续费/激活新码"
        } else {
            proStatusText.text = "❌ Pro 服务未激活"
            proStatusText.setTextColor(ctx.getColor(android.R.color.holo_red_dark))
            activateButton.text = "激活 Pro 服务"
        }
        
        // 显示配额信息
        updateQuotaDisplay()
    }
    
    private fun updateQuotaDisplay() {
        val ctx = requireContext()
        
        // 检查是否有免费额度信息（即使剩余为0，只要total > 0就应该显示）
        val freeTotal = ProManager.getFreeTotalQuota(ctx)
        val freeUsed = ProManager.getFreeUsedCount(ctx)
        val freeRemaining = ProManager.getFreeRemainingQuota(ctx)
        val isFreeAvailable = ProManager.isFreeQuotaAvailable(ctx)
        
        // 检查是否有Pro配额信息（即使剩余为0，只要monthly > 0就应该显示）
        val monthly = ProManager.getMonthlyQuota(ctx)
        val proRemaining = ProManager.getRemainingQuota(ctx)
        val proUsed = ProManager.getUsedCount(ctx)
        val isPro = ProManager.isPro(ctx)
        
        // 优先显示免费额度，如果免费额度有信息（total > 0）就显示，即使剩余为0
        if (isFreeAvailable && freeTotal > 0) {
            // 显示当前周期卡片
            currentPeriodCard.visibility = View.VISIBLE
            
            // 显示免费额度（即使剩余为0也显示）
            val usagePercent = if (freeTotal > 0) (freeUsed * 100 / freeTotal) else 0
            val remainingPercent = 100 - usagePercent
            
            quotaText.text = "免费试用配额\n" +
                    "配额: $freeRemaining / $freeTotal 次\n" +
                    "已用: $freeUsed 次 | 剩余: ${remainingPercent}%"
            quotaText.visibility = View.VISIBLE
            
            // 根据剩余配额设置颜色
            when {
                freeRemaining <= 0 -> quotaText.setTextColor(ctx.getColor(android.R.color.holo_red_dark))
                freeRemaining <= freeTotal * 0.2 -> quotaText.setTextColor(ctx.getColor(android.R.color.holo_orange_dark))
                else -> quotaText.setTextColor(ctx.getColor(android.R.color.holo_green_dark))
            }
            
            // 隐藏下一周期卡片（免费额度不显示下一周期）
            nextPeriodCard.visibility = View.GONE
        } 
        // 如果有Pro配额信息（monthly > 0），根据剩余配额决定显示方式
        else if (isPro && monthly > 0) {
            // 显示当前周期卡片
            currentPeriodCard.visibility = View.VISIBLE
            
            val expiresAt = ProManager.getExpiresAt(ctx)
            val usagePercent = if (monthly > 0) (proUsed * 100 / monthly) else 0
            val remainingPercent = 100 - usagePercent
            
            // 如果剩余配额为0，只显示配额信息，不显示周期信息
            if (proRemaining <= 0) {
                quotaText.text = "配额: $proRemaining / $monthly 次\n" +
                        "已用: $proUsed 次 | 剩余: ${remainingPercent}%\n" +
                        "额度已用尽"
                quotaText.visibility = View.VISIBLE
                quotaText.setTextColor(ctx.getColor(android.R.color.holo_red_dark))
            } else {
                // 剩余配额 > 0，显示完整信息（包括周期信息）
                quotaText.text = "到期时间: ${ProManager.getExpiresAtFormatted(ctx)}\n" +
                        "剩余天数: ${ProManager.getDaysRemaining(ctx)} 天\n" +
                        "配额: $proRemaining / $monthly 次\n" +
                        "已用: $proUsed 次 | 剩余: ${remainingPercent}%"
                quotaText.visibility = View.VISIBLE
                
                // 根据剩余配额设置颜色
                when {
                    proRemaining <= monthly * 0.2 -> quotaText.setTextColor(ctx.getColor(android.R.color.holo_orange_dark))
                    else -> quotaText.setTextColor(ctx.getColor(android.R.color.holo_green_dark))
                }
            }
            
            // 显示下一周期预付费配额（只有在剩余配额 > 0 时才显示）
            if (proRemaining > 0) {
                val nextPeriodQuota = ProManager.getNextPeriodQuota(ctx)
                if (nextPeriodQuota > 0 && expiresAt != null) {
                    // 计算下一周期的开始时间（当前周期到期后的第一天）
                    val nextPeriodStart = try {
                        val expiresDate = parseExpiresAt(expiresAt)
                        if (expiresDate != null) {
                            val calendar = java.util.Calendar.getInstance()
                            calendar.time = expiresDate
                            calendar.add(java.util.Calendar.DAY_OF_MONTH, 1)
                            val displayFormat = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.getDefault())
                            displayFormat.format(calendar.time)
                        } else {
                            "下月1日"
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("ProFragment", "计算下一周期开始时间失败", e)
                        "下月1日"
                    }
                    
                    nextPeriodQuotaText.text = "配额: $nextPeriodQuota 次\n" +
                            "将在 $nextPeriodStart 自动激活\n" +
                            "✅ 续费成功，无需担心"
                    nextPeriodCard.visibility = View.VISIBLE
                } else {
                    nextPeriodCard.visibility = View.GONE
                }
            } else {
                // 配额用尽时隐藏下一周期卡片
                nextPeriodCard.visibility = View.GONE
            }
        } else {
            // 完全没有配额信息（total = 0 或 monthly = 0），隐藏整个当前周期卡片
            currentPeriodCard.visibility = View.GONE
            nextPeriodCard.visibility = View.GONE
        }
    }
    
    /**
     * 解析 expires_at 日期字符串（支持多种 ISO 8601 格式）
     * 复用 ProManager 的解析逻辑
     */
    private fun parseExpiresAt(dateString: String?): java.util.Date? {
        if (dateString.isNullOrBlank()) return null
        
        // 预处理：移除微秒部分（如果存在）
        var processedDateString = dateString
        val microsecondsPattern = Regex("\\.(\\d{3})(\\d{3})([\\+\\-]|Z|$)")
        processedDateString = microsecondsPattern.replace(processedDateString) { matchResult ->
            val milliseconds = matchResult.groupValues[1]
            val zone = matchResult.groupValues[3]
            ".$milliseconds$zone"
        }
        
        // 预处理：将 'Z' 转换为 '+0000'
        processedDateString = processedDateString.replace("Z", "+0000")
        
        // 尝试多种日期格式
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSX",
            "yyyy-MM-dd'T'HH:mm:ssXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX"
        )
        
        for (format in formats) {
            try {
                val sdf = java.text.SimpleDateFormat(format, java.util.Locale.getDefault())
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                val date = sdf.parse(processedDateString)
                if (date != null) {
                    return date
                }
            } catch (e: Exception) {
                // 继续尝试下一个格式
            }
        }
        
        return null
    }
    
    /**
     * 更新设备ID显示
     */
    private fun updateDeviceId() {
        val ctx = requireContext()
        val versionChecker = VersionChecker(ctx)
        val deviceId = versionChecker.getDeviceId()
        deviceIdText.text = deviceId
    }
    
    /**
     * 复制设备ID到剪贴板
     */
    private fun copyDeviceIdToClipboard() {
        val ctx = requireContext()
        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("设备ID", deviceIdText.text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(ctx, "设备ID已复制到剪贴板", Toast.LENGTH_SHORT).show()
    }
}

