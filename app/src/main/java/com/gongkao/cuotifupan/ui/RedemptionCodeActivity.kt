package com.gongkao.cuotifupan.ui

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.api.ApiClient
import com.gongkao.cuotifupan.util.ProManager
import com.gongkao.cuotifupan.util.VersionChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 兑换码激活页面
 */
class RedemptionCodeActivity : AppCompatActivity() {
    
    private lateinit var codeInput: EditText
    private lateinit var verifyButton: Button
    private lateinit var activateButton: Button
    private lateinit var statusText: TextView
    private lateinit var proStatusText: TextView
    
    private var verifiedCode: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_redemption_code)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "激活 Pro 服务"
        
        codeInput = findViewById(R.id.codeInput)
        verifyButton = findViewById(R.id.verifyButton)
        activateButton = findViewById(R.id.activateButton)
        statusText = findViewById(R.id.statusText)
        proStatusText = findViewById(R.id.proStatusText)
        
        // 验证按钮
        verifyButton.setOnClickListener {
            verifyCode()
        }
        
        // 激活按钮
        activateButton.setOnClickListener {
            activateCode()
        }
        
        // 更新 Pro 状态显示
        updateProStatus()
    }
    
    private fun verifyCode() {
        val code = codeInput.text.toString().trim().uppercase()
        
        if (code.isEmpty()) {
            Toast.makeText(this, "请输入兑换码", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 格式化兑换码（移除空格和连字符，然后重新格式化）
        val formattedCode = formatCode(code)
        codeInput.setText(formattedCode)
        
        verifyButton.isEnabled = false
        statusText.text = "正在验证兑换码..."
        
        lifecycleScope.launch {
            try {
                val versionChecker = com.gongkao.cuotifupan.util.VersionChecker(this@RedemptionCodeActivity)
                val deviceId = versionChecker.getDeviceId()
                val request = com.gongkao.cuotifupan.api.VerifyCodeRequest(
                    code = formattedCode,
                    device_id = deviceId
                )
                
                val response = withContext(Dispatchers.IO) {
                    ApiClient.redemptionApiService.verifyCode(request)
                }
                
                withContext(Dispatchers.Main) {
                    verifyButton.isEnabled = true
                    
                    if (response.isSuccessful && response.body()?.success == true) {
                        val data = response.body()?.data
                        if (data != null && data.valid) {
                            verifiedCode = formattedCode
                            activateButton.isEnabled = true
                            
                            val message = if (data.duration_days != null) {
                                "兑换码有效，可激活 ${data.duration_days} 天 Pro 服务"
                            } else {
                                "兑换码有效，可以激活"
                            }
                            statusText.text = message
                            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
                        } else {
                            statusText.text = "兑换码无效"
                            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                            activateButton.isEnabled = false
                        }
                    } else {
                        val errorMessage = response.body()?.message ?: "验证失败"
                        statusText.text = errorMessage
                        statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                        activateButton.isEnabled = false
                        Toast.makeText(this@RedemptionCodeActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    verifyButton.isEnabled = true
                    val errorMessage = when (e) {
                        is java.net.SocketTimeoutException -> "请求超时，请检查网络连接后重试"
                        is java.net.ConnectException -> "无法连接到服务器，请检查网络"
                        is java.net.UnknownHostException -> "无法解析服务器地址，请检查网络"
                        is java.io.IOException -> "网络错误: ${e.message ?: "请检查网络连接"}"
                        else -> "验证失败: ${e.message ?: "未知错误"}"
                    }
                    statusText.text = errorMessage
                    statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                    activateButton.isEnabled = false
                    Toast.makeText(this@RedemptionCodeActivity, errorMessage, Toast.LENGTH_LONG).show()
                    android.util.Log.e("RedemptionCode", "验证兑换码失败", e)
                }
            }
        }
    }
    
    private fun activateCode() {
        val code = verifiedCode ?: codeInput.text.toString().trim().uppercase()
        
        if (code.isEmpty()) {
            Toast.makeText(this, "请先验证兑换码", Toast.LENGTH_SHORT).show()
            return
        }
        
        activateButton.isEnabled = false
        statusText.text = "正在激活 Pro 服务..."
        
        lifecycleScope.launch {
            try {
                val versionChecker = com.gongkao.cuotifupan.util.VersionChecker(this@RedemptionCodeActivity)
                val deviceId = versionChecker.getDeviceId()
                val request = com.gongkao.cuotifupan.api.ActivateCodeRequest(
                    code = code,
                    device_id = deviceId
                )
                
                val response = withContext(Dispatchers.IO) {
                    ApiClient.redemptionApiService.activateCode(request)
                }
                
                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && response.body()?.success == true) {
                        val proStatus = response.body()?.data?.pro_status
                        if (proStatus != null && proStatus.is_pro) {
                            // 激活成功，保存 Pro 状态
                            ProManager.activatePro(
                                this@RedemptionCodeActivity,
                                proStatus.activated_at ?: "",
                                proStatus.expires_at ?: ""
                            )
                            
                            // 更新配额信息（包括下一周期配额）
                            if (proStatus.monthly_quota != null && 
                                proStatus.remaining_quota != null && 
                                proStatus.used_count != null) {
                                ProManager.updateQuota(
                                    this@RedemptionCodeActivity,
                                    monthlyQuota = proStatus.monthly_quota,
                                    remainingQuota = proStatus.remaining_quota,
                                    usedCount = proStatus.used_count,
                                    nextPeriodQuota = proStatus.next_period_quota ?: 0,
                                    tier = proStatus.tier,
                                    tierName = proStatus.tier_name
                                )
                            }
                            
                            // 更新免费额度信息（如果有）
                            if (proStatus.free_quota != null) {
                                val freeQuota = proStatus.free_quota
                                ProManager.updateFreeQuota(
                                    this@RedemptionCodeActivity,
                                    totalQuota = freeQuota.total_quota,
                                    usedCount = freeQuota.used_count,
                                    remainingQuota = freeQuota.remaining_quota,
                                    isAvailable = freeQuota.is_available
                                )
                            }
                            
                            statusText.text = "Pro 服务激活成功！"
                            statusText.setTextColor(getColor(android.R.color.holo_green_dark))
                            activateButton.isEnabled = false
                            codeInput.isEnabled = false
                            verifyButton.isEnabled = false
                            
                            updateProStatus()
                            
                            Toast.makeText(this@RedemptionCodeActivity, "Pro 服务已激活", Toast.LENGTH_LONG).show()
                        } else {
                            statusText.text = "激活失败"
                            statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                            activateButton.isEnabled = true
                            Toast.makeText(this@RedemptionCodeActivity, "激活失败", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val errorMessage = response.body()?.message ?: "激活失败"
                        statusText.text = errorMessage
                        statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                        activateButton.isEnabled = true
                        Toast.makeText(this@RedemptionCodeActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    activateButton.isEnabled = true
                    val errorMessage = when (e) {
                        is java.net.SocketTimeoutException -> "请求超时，请检查网络连接后重试"
                        is java.net.ConnectException -> "无法连接到服务器，请检查网络"
                        is java.net.UnknownHostException -> "无法解析服务器地址，请检查网络"
                        is java.io.IOException -> "网络错误: ${e.message ?: "请检查网络连接"}"
                        else -> "激活失败: ${e.message ?: "未知错误"}"
                    }
                    statusText.text = errorMessage
                    statusText.setTextColor(getColor(android.R.color.holo_red_dark))
                    Toast.makeText(this@RedemptionCodeActivity, errorMessage, Toast.LENGTH_LONG).show()
                    android.util.Log.e("RedemptionCode", "激活兑换码失败", e)
                }
            }
        }
    }
    
    private fun updateProStatus() {
        if (ProManager.isPro(this)) {
            val expiresAt = ProManager.getExpiresAtFormatted(this)
            val daysRemaining = ProManager.getDaysRemaining(this)
            proStatusText.text = "✅ Pro 服务已激活\n到期时间: $expiresAt\n剩余天数: $daysRemaining 天"
            proStatusText.setTextColor(getColor(android.R.color.holo_green_dark))
        } else {
            proStatusText.text = "❌ Pro 服务未激活"
            proStatusText.setTextColor(getColor(android.R.color.holo_red_dark))
        }
    }
    
    /**
     * 格式化兑换码（添加连字符）
     * 例如: ABC123XYZ456789 -> ABC123-XYZ456-789
     */
    private fun formatCode(code: String): String {
        // 移除所有非字母数字字符
        val cleanCode = code.replace("[^A-Z0-9]".toRegex(), "")
        
        // 每6个字符添加一个连字符
        return cleanCode.chunked(6).joinToString("-")
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

