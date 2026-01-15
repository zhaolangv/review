package com.gongkao.cuotifupan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.gongkao.cuotifupan.ui.QuestionsFragment
import com.gongkao.cuotifupan.ui.NotesAndCardsFragment
import com.gongkao.cuotifupan.ui.ProFragment
import com.gongkao.cuotifupan.ui.FlashcardReviewActivity
import com.gongkao.cuotifupan.ui.practice.MathPracticeFragment
import com.gongkao.cuotifupan.service.ImageMonitorService
import com.gongkao.cuotifupan.service.NotificationHelper
import com.gongkao.cuotifupan.ui.QuestionAdapter
import com.gongkao.cuotifupan.ui.QuestionDetailCardActivity
import com.gongkao.cuotifupan.ui.ManualImportActivity
import com.gongkao.cuotifupan.ui.TagSortDialog
import com.gongkao.cuotifupan.ui.TagManager
import com.gongkao.cuotifupan.ui.TagEditDialog
import org.json.JSONArray
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.gongkao.cuotifupan.util.PreferencesManager
import com.gongkao.cuotifupan.util.ImageScanner
import com.gongkao.cuotifupan.viewmodel.QuestionViewModel
import android.util.Log
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.util.ImageSyncManager

class MainActivity : AppCompatActivity() {
    
    private lateinit var bottomNavigation: BottomNavigationView
    private var previousQuestionCount = 0 // 上一次的题目数量，用于检测新题目
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            // 权限授予后，显示扫描选择对话框，让用户选择是否扫描
            showScanChoiceDialog()
        } else {
            Toast.makeText(this, "需要相册权限才能检测题目", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // 设置ActionBar标题为应用名称
        supportActionBar?.title = getString(R.string.app_name)
        
        // 创建通知渠道
        NotificationHelper.createNotificationChannel(this)
        
        // 设置底部导航栏
        bottomNavigation = findViewById(R.id.bottomNavigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            Log.d("MainActivity", "底部导航栏点击: ${item.itemId}")
            val result = when (item.itemId) {
                R.id.nav_questions -> {
                    Log.d("MainActivity", "切换到题目Fragment")
                    switchFragment(QuestionsFragment())
                    // 切换到首页时恢复应用名称
                    supportActionBar?.title = getString(R.string.app_name)
                    true
                }
                R.id.nav_notes_and_cards -> {
                    Log.d("MainActivity", "切换到笔记和卡片Fragment")
                    switchFragment(NotesAndCardsFragment())
                    true
                }
                R.id.nav_practice -> {
                    Log.d("MainActivity", "切换到练习Fragment")
                    switchFragment(MathPracticeFragment())
                    true
                }
                R.id.nav_pro -> {
                    Log.d("MainActivity", "切换到Pro Fragment")
                    switchFragment(ProFragment())
                    true
                }
                else -> {
                    Log.w("MainActivity", "未知的导航项: ${item.itemId}")
                    false
                }
            }
            Log.d("MainActivity", "导航结果: $result")
            result
        }
        
        // 检查是否需要跳转到特定页面
        val navigateTo = intent.getStringExtra("navigate_to")
        if (navigateTo == "pro") {
            // 跳转到 Pro 页面
            switchFragment(ProFragment())
            bottomNavigation.selectedItemId = R.id.nav_pro
            // 清除 extra，避免下次启动时重复跳转
            intent.removeExtra("navigate_to")
        } else if (savedInstanceState == null) {
            // 默认显示题目列表
            switchFragment(QuestionsFragment())
            bottomNavigation.selectedItemId = R.id.nav_questions
        }
        
        // 设置当前Activity，用于API日志弹窗
        com.gongkao.cuotifupan.api.ApiClient.setCurrentActivity(this)
        
        // 检查版本更新
        checkVersionOnStartup()
        
        // 请求权限
        requestPermissions()
    }
    
    // 移除 MainActivity 的菜单，让 Fragment 的菜单显示
    // Pro 功能现在在底部导航栏的第四个页面
    
    override fun onResume() {
        super.onResume()
        // 确保Activity引用是最新的
        com.gongkao.cuotifupan.api.ApiClient.setCurrentActivity(this)
    }
    
    override fun onPause() {
        super.onPause()
        // 清除Activity引用（可选，避免内存泄漏）
        // com.gongkao.cuotifupan.api.ApiClient.setCurrentActivity(null)
    }
    
    private fun switchFragment(fragment: Fragment) {
        try {
            Log.d("MainActivity", "切换Fragment: ${fragment.javaClass.simpleName}")
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit()
        } catch (e: Exception) {
            Log.e("MainActivity", "切换Fragment失败", e)
            e.printStackTrace()
            Toast.makeText(this, "切换页面失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 获取当前显示的Fragment
     */
    private fun getCurrentFragment(): Fragment? {
        return supportFragmentManager.findFragmentById(R.id.fragmentContainer)
    }
    
    /**
     * 更新ProFragment的配额显示（如果当前可见）
     */
    private fun updateProFragmentQuota() {
        val currentFragment = getCurrentFragment()
        if (currentFragment is com.gongkao.cuotifupan.ui.ProFragment) {
            currentFragment.refreshStatus()
        }
    }
    
    private fun checkVersionOnStartup() {
        Log.e("MainActivity", "========== 开始版本检查 ==========")
        android.util.Log.e("MainActivity", "========== 开始版本检查 ==========")
        val versionChecker = com.gongkao.cuotifupan.util.VersionChecker(this)
        Log.e("MainActivity", "VersionChecker创建完成")
        android.util.Log.e("MainActivity", "VersionChecker创建完成")
        versionChecker.checkVersion(
            onUpdateRequired = { latestVersion, downloadUrl, releaseNotes, required ->
                Log.e("MainActivity", "需要更新: $latestVersion")
                android.util.Log.e("MainActivity", "需要更新: $latestVersion")
                // 显示更新对话框
                showUpdateDialog(latestVersion, downloadUrl, releaseNotes, required)
                // 版本检查完成，更新ProFragment配额（如果可见）
                updateProFragmentQuota()
            },
            onNoUpdate = {
                Log.e("MainActivity", "应用已是最新版本")
                android.util.Log.e("MainActivity", "应用已是最新版本")
                // 版本检查完成，更新ProFragment配额（如果可见）
                updateProFragmentQuota()
            },
            onError = { error ->
                Log.e("MainActivity", "版本检查失败: $error")
                android.util.Log.e("MainActivity", "版本检查失败: $error")
                // 显示Toast提示用户
                android.widget.Toast.makeText(this, "版本检查失败: $error", android.widget.Toast.LENGTH_LONG).show()
                // 即使失败也尝试更新配额（可能本地已有缓存）
                updateProFragmentQuota()
            }
        )
        Log.e("MainActivity", "版本检查调用完成")
        android.util.Log.e("MainActivity", "版本检查调用完成")
    }
    
    private fun showUpdateDialog(version: String, downloadUrl: String, releaseNotes: String, required: Boolean) {
        val versionChecker = com.gongkao.cuotifupan.util.VersionChecker(this)
        
        val message = if (releaseNotes.isNotBlank()) {
            "新版本 $version 已发布\n\n$releaseNotes"
        } else {
            "新版本 $version 已发布，建议立即更新"
        }
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("发现新版本")
            .setMessage(message)
            .setPositiveButton("立即更新") { _, _ ->
                // 下载APK
                versionChecker.downloadAPK(downloadUrl, "app-v$version.apk")
                Toast.makeText(this, "开始下载更新...", Toast.LENGTH_SHORT).show()
            }
        
        // 如果不是必需更新，添加"稍后"按钮
        if (!required) {
            dialog.setNegativeButton("稍后", null)
        }
        
        dialog.setCancelable(!required) // 必需更新时不可取消
        dialog.show()
    }
    
    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            // 后台服务已禁用，不再需要通知权限
            // permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11-12
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            // WRITE_EXTERNAL_STORAGE 在 Android 11+ 已废弃，但仍可请求
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            // Android 10及以下
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        val needRequest = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needRequest) {
            // 检查是否已经显示过权限说明
            val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            val hasShownPermissionExplanation = prefs.getBoolean("has_shown_permission_explanation", false)
            
            if (!hasShownPermissionExplanation) {
                // 首次请求权限，先显示说明对话框
                showPermissionExplanationDialog(permissions.toTypedArray())
            } else {
                // 已经显示过说明，直接请求权限
            permissionLauncher.launch(permissions.toTypedArray())
            }
        } else {
            // 权限已授予，显示扫描选择对话框，让用户选择是否扫描
            showScanChoiceDialog()
        }
    }
    
    /**
     * 显示权限说明对话框
     */
    private fun showPermissionExplanationDialog(permissions: Array<String>) {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_explanation_title)
            .setMessage(R.string.permission_explanation_message)
            .setPositiveButton(R.string.permission_explanation_agree) { dialog, _ ->
                // 标记已显示过说明
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_shown_permission_explanation", true).apply()
                
                // 请求权限
                permissionLauncher.launch(permissions)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.permission_explanation_later) { dialog, _ ->
                // 用户选择稍后，标记已显示过说明
                val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("has_shown_permission_explanation", true).apply()
                
                // 显示提示信息
                Toast.makeText(this, "您可以在设置中手动授予权限，或在使用相关功能时再次提示", Toast.LENGTH_LONG).show()
                dialog.dismiss()
            }
            .setCancelable(false) // 不允许点击外部取消
            .show()
    }
    
    /**
     * 显示扫描选择对话框，让用户选择是否扫描和扫描数量
     */
    private fun showScanChoiceDialog() {
        // 检查是否已经询问过扫描选择（避免重复弹出）
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hasShownScanChoice = prefs.getBoolean("has_shown_scan_choice", false)
        
        // 如果是首次授予权限，或者用户之前选择过"稍后"，则显示对话框
        // 如果用户之前选择过扫描，则不再弹出（可以通过菜单手动扫描）
        if (!hasShownScanChoice) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_scan_choice, null)
            val scanCountInput = dialogView.findViewById<android.widget.EditText>(R.id.scanCountInput)
            
            // 设置默认扫描数量
            val isFirstLaunch = PreferencesManager.isFirstLaunch(this)
            val defaultCount = if (isFirstLaunch) 50 else 50
            scanCountInput.setText(defaultCount.toString())
            
            val dialog = AlertDialog.Builder(this)
                .setTitle("扫描相册")
                .setView(dialogView)
                .setPositiveButton("立即扫描") { _, _ ->
                    val countText = scanCountInput.text.toString()
                    val scanCount = try {
                        countText.toInt().coerceIn(1, 500) // 限制在1-500之间
                    } catch (e: Exception) {
                        defaultCount
                    }
                    
                    // 标记已显示过选择对话框
                    prefs.edit().putBoolean("has_shown_scan_choice", true).apply()
                    
                    // 执行扫描
                    startImageMonitoring(scanCount, isFirstLaunch)
                }
                .setNegativeButton("稍后") { _, _ ->
                    // 标记已显示过选择对话框，但用户选择稍后，下次启动时不再自动弹出
                    prefs.edit().putBoolean("has_shown_scan_choice", true).apply()
                }
                .setCancelable(false)
                .show()
            
            // 设置输入框焦点和选中文本
            scanCountInput.requestFocus()
            scanCountInput.selectAll()
        }
    }
    
    /**
     * 开始扫描图片
     * @param scanCount 扫描数量
     * @param isFirstLaunch 是否首次启动
     */
    private fun startImageMonitoring(scanCount: Int = 50, isFirstLaunch: Boolean = false) {
        lifecycleScope.launch {
            if (isFirstLaunch) {
                Log.i("MainActivity", "🎉 首次启动，开始扫描相册中的题目...")
                
                // 显示扫描对话框
                showScanningDialog()
                
                // 执行首次扫描
                performInitialScan(scanCount)
                
                // 隐藏扫描对话框
                hideScanningDialog()
                
                // 标记首次启动完成
                PreferencesManager.setFirstLaunchCompleted(this@MainActivity)
            } else {
                Log.d("MainActivity", "执行完整同步对比...")
                
                // 执行完整同步对比
                performFullSync(scanCount)
            }
        }
    }
    
    /**
     * 执行完整同步对比
     * 包括：检查新图片、识别题目、检测删除、对比应用和相册
     * @param scanLimit 扫描数量限制
     */
    private fun performFullSync(scanLimit: Int = 50) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    // 使用 ImageSyncManager 执行完整同步
                    val result = ImageSyncManager.performFullSync(
                        context = this@MainActivity,
                        scanLimit = scanLimit,
                        onProgress = { progressText ->
                            Log.d("MainActivity", "同步进度: $progressText")
                        }
                    )
                    
                    // 输出同步结果
                    withContext(Dispatchers.Main) {
                        if (result.newQuestionsFound > 0 || result.invalidQuestionsDeleted > 0 || result.deletedImagesCount > 0) {
                            val message = buildString {
                                if (result.newQuestionsFound > 0) {
                                    append("发现 ${result.newQuestionsFound} 道新题目")
                                }
                                if (result.invalidQuestionsDeleted > 0) {
                                    if (isNotEmpty()) append("，")
                                    append("删除 ${result.invalidQuestionsDeleted} 条无效记录")
                                }
                                if (result.deletedImagesCount > 0) {
                                    if (isNotEmpty()) append("，")
                                    append("发现 ${result.deletedImagesCount} 张图片被删除")
                                }
                            }
                            Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "完整同步失败", e)
                }
            }
        }
    }
    
    /**
     * 执行首次启动时的扫描
     * @param scanLimit 扫描数量限制
     */
    private suspend fun performInitialScan(scanLimit: Int = 50) {
        try {
            Log.i("MainActivity", "开始扫描最近 $scanLimit 张图片...")
            
            // 使用 ImageScanner 工具类进行扫描
            withContext(Dispatchers.IO) {
                ImageScanner.scanRecentImages(
                    this@MainActivity, 
                    scanLimit, 
                    isFirstLaunch = true,
                    onProgress = { progressText ->
                        updateScanningProgress(progressText)
                    }
                )
            }
            
            Log.i("MainActivity", "✅ 首次扫描完成")
            withContext(Dispatchers.Main) {
                Toast.makeText(this@MainActivity, "扫描完成", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "首次扫描失败", e)
            withContext(Dispatchers.Main) {
                hideScanningDialog()
                Toast.makeText(this@MainActivity, "扫描失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 显示扫描对话框
     */
    private fun showScanningDialog() {
        if (scanningDialog == null) {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_scanning, null)
            scanningProgressText = dialogView.findViewById(R.id.progressText)
            
            scanningDialog = AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false) // 不允许取消
                .create()
            
            // 设置对话框样式 - 半透明，可以看到背景
            scanningDialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
            scanningDialog?.window?.setDimAmount(0.0f) // 不遮挡背景，让用户看到卡片出现
            
            // 设置对话框位置在屏幕中央
            scanningDialog?.window?.setGravity(android.view.Gravity.CENTER)
        }
        
        // 更新进度文本
        scanningProgressText?.text = "正在扫描前50张图片是否为题目..."
        
        scanningDialog?.show()
    }
    
    /**
     * 更新扫描进度文本
     */
    private fun updateScanningProgress(text: String) {
        runOnUiThread {
            scanningProgressText?.text = text
        }
    }
    
    /**
     * 隐藏扫描对话框
     */
    private fun hideScanningDialog() {
        scanningDialog?.dismiss()
        scanningDialog = null
    }
    
    private var batchActionBar: View? = null
    private var scanningDialog: androidx.appcompat.app.AlertDialog? = null
    private var scanningProgressText: android.widget.TextView? = null
    
    fun showBatchActionBar() {
        if (batchActionBar == null) {
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(android.graphics.Color.parseColor("#E0E0E0"))
                setPadding(16, 16, 16, 16)
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                )
                
                val addTagButton = Button(this@MainActivity).apply {
                    text = "添加标签"
                    setOnClickListener {
                        // 通过 Fragment 处理批量操作
                        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                        when (fragment) {
                            is QuestionsFragment -> fragment.showBatchTagDialog()
                            is NotesAndCardsFragment -> fragment.showBatchTagDialog()
                        }
                    }
                }
                
                val deleteButton = Button(this@MainActivity).apply {
                    text = "批量删除"
                    setOnClickListener {
                        // 通过 Fragment 处理批量删除
                        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                        when (fragment) {
                            is QuestionsFragment -> fragment.showBatchDeleteDialog()
                            is NotesAndCardsFragment -> fragment.showBatchDeleteDialog()
                        }
                    }
                }
                
                val removeHandwritingButton = Button(this@MainActivity).apply {
                    text = "批量擦写"
                    setOnClickListener {
                        // 通过 Fragment 处理批量擦写
                        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                        if (fragment is QuestionsFragment) {
                            fragment.showBatchRemoveHandwritingDialog()
                        }
                    }
                }
                
                val cancelButton = Button(this@MainActivity).apply {
                    text = "取消"
                    setOnClickListener {
                        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
                        when (fragment) {
                            is QuestionsFragment -> fragment.exitBatchMode()
                            is NotesAndCardsFragment -> fragment.exitBatchMode()
                        }
                    }
                }
                
                addView(addTagButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, 4, 0)
                })
                addView(deleteButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, 4, 0)
                })
                addView(removeHandwritingButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(0, 0, 4, 0)
                })
                addView(cancelButton, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                
                // 存储按钮引用以便动态显示/隐藏
                tag = removeHandwritingButton
            }
            
            val rootView = findViewById<android.view.ViewGroup>(android.R.id.content)
            rootView.addView(layout)
            batchActionBar = layout
        }
        
        // 根据当前Fragment动态显示/隐藏批量擦写按钮（仅在QuestionsFragment时显示）
        val fragment = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
        val removeHandwritingButton = batchActionBar?.tag as? Button
        removeHandwritingButton?.visibility = if (fragment is QuestionsFragment) View.VISIBLE else View.GONE
        
        batchActionBar?.visibility = View.VISIBLE
    }
    
    fun hideBatchActionBar() {
        batchActionBar?.visibility = View.GONE
    }
    
    
    override fun onDestroy() {
        super.onDestroy()
        // 隐藏扫描对话框
        hideScanningDialog()
        // 后台服务已禁用
        // 注意：这里不停止服务，让它一直运行
        // 如果需要停止，可以添加一个按钮调用 ImageMonitorService.stop(this)
    }
}


