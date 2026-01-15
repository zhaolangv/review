package com.gongkao.cuotifupan.ui

import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.tabs.TabLayout
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.util.DataMigrationManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.TimeZone

/**
 * 数据迁移页面
 * 支持导出和导入数据
 */
class DataMigrationActivity : AppCompatActivity() {
    
    private lateinit var tabLayout: TabLayout
    private lateinit var exportLayout: View
    private lateinit var importLayout: View
    
    // 导出相关
    private lateinit var exportButton: Button
    private lateinit var exportProgressBar: ProgressBar
    private lateinit var exportProgressText: TextView
    private lateinit var exportResultCard: CardView
    private lateinit var exportResultTitle: TextView
    private lateinit var exportResultCodeLabel: TextView
    private lateinit var migrationCodeLayout: View
    private lateinit var exportResultCode: TextView
    private lateinit var copyCodeButton: ImageButton
    private lateinit var exportResultExpiresLabel: TextView
    private lateinit var exportResultExpires: TextView
    
    private var countDownTimer: CountDownTimer? = null
    
    // 导入相关
    private lateinit var migrationCodeInput: EditText
    private lateinit var importButton: Button
    private lateinit var importProgressBar: ProgressBar
    private lateinit var importProgressText: TextView
    private lateinit var importResultText: TextView
    
    private lateinit var migrationManager: DataMigrationManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_data_migration)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "数据迁移"
        
        migrationManager = DataMigrationManager(this)
        
        initViews()
        setupTabs()
    }
    
    private fun initViews() {
        tabLayout = findViewById(R.id.tabLayout)
        exportLayout = findViewById(R.id.exportLayout)
        importLayout = findViewById(R.id.importLayout)
        
        // 导出相关
        exportButton = findViewById(R.id.exportButton)
        exportProgressBar = findViewById(R.id.exportProgressBar)
        exportProgressText = findViewById(R.id.exportProgressText)
        exportResultCard = findViewById(R.id.exportResultCard)
        exportResultTitle = findViewById(R.id.exportResultTitle)
        exportResultCodeLabel = findViewById(R.id.exportResultCodeLabel)
        migrationCodeLayout = findViewById(R.id.migrationCodeLayout)
        exportResultCode = findViewById(R.id.exportResultCode)
        copyCodeButton = findViewById(R.id.copyCodeButton)
        exportResultExpiresLabel = findViewById(R.id.exportResultExpiresLabel)
        exportResultExpires = findViewById(R.id.exportResultExpires)
        
        // 导入相关
        migrationCodeInput = findViewById(R.id.migrationCodeInput)
        importButton = findViewById(R.id.importButton)
        importProgressBar = findViewById(R.id.importProgressBar)
        importProgressText = findViewById(R.id.importProgressText)
        importResultText = findViewById(R.id.importResultText)
        
        exportButton.setOnClickListener {
            startExport()
        }
        
        importButton.setOnClickListener {
            startImport()
        }
        
        copyCodeButton.setOnClickListener {
            copyMigrationCode()
        }
    }
    
    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("导出数据"))
        tabLayout.addTab(tabLayout.newTab().setText("导入数据"))
        
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        exportLayout.visibility = View.VISIBLE
                        importLayout.visibility = View.GONE
                    }
                    1 -> {
                        exportLayout.visibility = View.GONE
                        importLayout.visibility = View.VISIBLE
                    }
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    
    private fun startExport() {
        exportButton.isEnabled = false
        exportProgressBar.visibility = View.VISIBLE
        exportProgressText.visibility = View.VISIBLE
        exportResultCard.visibility = View.GONE
        
        lifecycleScope.launch {
            val result = migrationManager.exportData { current, total, message ->
                runOnUiThread {
                    exportProgressText.text = "$message ($current%)"
                }
            }
            
            exportButton.isEnabled = true
            exportProgressBar.visibility = View.GONE
            exportProgressText.visibility = View.GONE
            
            exportResultCard.visibility = View.VISIBLE
            
            if (result != null) {
                // 成功
                exportResultTitle.text = "✅ 导出成功！"
                exportResultTitle.setTextColor(getColor(android.R.color.holo_green_dark))
                exportResultCode.text = result.migrationCode
                exportResultCode.setTextColor(getColor(android.R.color.black))
                exportResultCodeLabel.visibility = View.VISIBLE
                migrationCodeLayout.visibility = View.VISIBLE
                exportResultExpiresLabel.visibility = View.VISIBLE
                exportResultExpires.visibility = View.VISIBLE
                copyCodeButton.visibility = View.VISIBLE
                
                // 解析过期时间并启动倒计时
                startCountDown(result.expiresAt)
                
                // 自动复制到剪贴板
                copyMigrationCode(result.migrationCode, showToast = true)
            } else {
                // 失败
                exportResultTitle.text = "❌ 导出失败"
                exportResultTitle.setTextColor(getColor(android.R.color.holo_red_dark))
                exportResultCode.text = "请检查网络连接后重试"
                exportResultCode.setTextColor(getColor(android.R.color.holo_red_dark))
                exportResultCodeLabel.visibility = View.GONE
                migrationCodeLayout.visibility = View.GONE
                exportResultExpiresLabel.visibility = View.GONE
                exportResultExpires.visibility = View.GONE
                copyCodeButton.visibility = View.GONE
            }
        }
    }
    
    /**
     * 启动倒计时
     */
    private fun startCountDown(expiresAtString: String) {
        try {
            // 解析ISO 8601格式的时间（支持带时区和不带时区）
            val expiresAt = when {
                expiresAtString.contains("T") && expiresAtString.contains("Z") -> {
                    // 带Z的UTC时间，如 "2026-01-15T23:59:59Z"
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault())
                    dateFormat.timeZone = TimeZone.getTimeZone("UTC")
                    dateFormat.parse(expiresAtString)
                }
                expiresAtString.contains("T") && expiresAtString.contains("+") -> {
                    // 带时区偏移，如 "2026-01-15T23:59:59+08:00"
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                    dateFormat.parse(expiresAtString)
                }
                expiresAtString.contains("T") -> {
                    // 不带时区，如 "2026-01-15T23:59:59"
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                    dateFormat.parse(expiresAtString)
                }
                else -> {
                    // 简单日期时间格式
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    dateFormat.parse(expiresAtString)
                }
            }
            
            if (expiresAt != null) {
                val now = System.currentTimeMillis()
                val remaining = expiresAt.time - now
                
                if (remaining > 0) {
                    // 启动倒计时
                    countDownTimer?.cancel()
                    countDownTimer = object : CountDownTimer(remaining, 1000) {
                        override fun onTick(millisUntilFinished: Long) {
                            updateCountDownText(millisUntilFinished)
                        }
                        
                        override fun onFinish() {
                            exportResultExpires.text = "⏰ 已过期"
                            exportResultExpires.setTextColor(getColor(android.R.color.holo_red_dark))
                        }
                    }.start()
                } else {
                    exportResultExpires.text = "⏰ 已过期"
                    exportResultExpires.setTextColor(getColor(android.R.color.holo_red_dark))
                }
            }
        } catch (e: Exception) {
            // 如果解析失败，显示原始时间
            exportResultExpires.text = "过期时间: $expiresAtString"
        }
    }
    
    /**
     * 更新倒计时文本
     */
    private fun updateCountDownText(millisUntilFinished: Long) {
        val seconds = millisUntilFinished / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        val timeText = if (hours > 0) {
            String.format("⏰ 剩余时间: %d小时%d分%d秒", hours, minutes, secs)
        } else if (minutes > 0) {
            String.format("⏰ 剩余时间: %d分%d秒", minutes, secs)
        } else {
            String.format("⏰ 剩余时间: %d秒", secs)
        }
        
        exportResultExpires.text = timeText
        
        // 如果剩余时间少于1小时，显示红色警告
        if (seconds < 3600) {
            exportResultExpires.setTextColor(getColor(android.R.color.holo_red_dark))
        } else {
            exportResultExpires.setTextColor(getColor(android.R.color.holo_green_dark))
        }
    }
    
    /**
     * 复制迁移码
     */
    private fun copyMigrationCode(code: String? = null, showToast: Boolean = false) {
        val codeToCopy = code ?: exportResultCode.text.toString()
        if (codeToCopy.isNotEmpty() && !codeToCopy.startsWith("❌")) {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("迁移码", codeToCopy)
            clipboard.setPrimaryClip(clip)
            if (showToast) {
                Toast.makeText(this, "迁移码已复制到剪贴板", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun startImport() {
        val migrationCode = migrationCodeInput.text.toString().trim()
        
        if (migrationCode.isEmpty()) {
            Toast.makeText(this, "请输入迁移码", Toast.LENGTH_SHORT).show()
            return
        }
        
        // 格式化迁移码（移除空格和连字符，然后重新格式化）
        val formattedCode = formatMigrationCode(migrationCode)
        migrationCodeInput.setText(formattedCode)
        
        importButton.isEnabled = false
        importProgressBar.visibility = View.VISIBLE
        importProgressText.visibility = View.VISIBLE
        importResultText.visibility = View.GONE
        
        lifecycleScope.launch {
            val result = migrationManager.importData(formattedCode) { current, total, message ->
                runOnUiThread {
                    importProgressText.text = "$message ($current%)"
                }
            }
            
            importButton.isEnabled = true
            importProgressBar.visibility = View.GONE
            importProgressText.visibility = View.GONE
            
            if (result != null) {
                importResultText.visibility = View.VISIBLE
                importResultText.text = "✅ 导入成功！\n\n" +
                        "题目：${result.questionsCount} 道\n" +
                        "笔记：${result.notesCount} 条\n" +
                        "记忆卡片：${result.flashcardsCount} 张\n" +
                        "图片：${result.imagesCount} 张"
                importResultText.setTextColor(getColor(android.R.color.holo_green_dark))
                
                Toast.makeText(this@DataMigrationActivity, "数据导入成功", Toast.LENGTH_LONG).show()
            } else {
                importResultText.visibility = View.VISIBLE
                importResultText.text = "❌ 导入失败，请检查迁移码是否正确或网络连接"
                importResultText.setTextColor(getColor(android.R.color.holo_red_dark))
            }
        }
    }
    
    /**
     * 格式化迁移码（添加连字符）
     * 例如: MIGRABC123XYZ456789 -> MIGR-ABC123-XYZ456-789
     */
    private fun formatMigrationCode(code: String): String {
        // 移除所有非字母数字字符
        val cleanCode = code.replace("[^A-Z0-9]".toRegex(), "").uppercase()
        
        // 如果以MIGR开头，去掉MIGR前缀
        val codeWithoutPrefix = if (cleanCode.startsWith("MIGR")) {
            cleanCode.substring(4)
        } else {
            cleanCode
        }
        
        // 每6个字符添加一个连字符
        val formatted = codeWithoutPrefix.chunked(6).joinToString("-")
        
        return "MIGR-$formatted"
    }
    
    override fun onDestroy() {
        super.onDestroy()
        countDownTimer?.cancel()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

