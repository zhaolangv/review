package com.gongkao.cuotifupan.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.gongkao.cuotifupan.R
import com.gongkao.cuotifupan.util.ProManager

/**
 * 如何领取兑换码页面
 */
class HowToGetRedeemCodeActivity : AppCompatActivity() {
    
    private lateinit var descriptionText: TextView
    private lateinit var redeemLinkButton: Button
    private lateinit var activateButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_how_to_get_redeem_code)
        
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "如何领取兑换码"
        
        descriptionText = findViewById(R.id.descriptionText)
        redeemLinkButton = findViewById(R.id.redeemLinkButton)
        activateButton = findViewById(R.id.activateButton)
        
        // 检查并更新链接显示
        updateLinkDisplay()
        
        // 跳转到激活页面
        activateButton.setOnClickListener {
            val intent = Intent(this, RedemptionCodeActivity::class.java)
            startActivity(intent)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // 在 onResume 时重新检查链接（如果版本检查在后台完成，页面会自动更新）
        updateLinkDisplay()
    }
    
    /**
     * 检查并更新链接显示
     */
    private fun updateLinkDisplay() {
        // 检查是否有兑换码链接
        val redeemCodeUrl = ProManager.getRedeemCodeUrl(this)
        
        // 添加日志以便调试
        android.util.Log.d("HowToGetRedeemCode", "读取兑换码链接: redeemCodeUrl=$redeemCodeUrl")
        android.util.Log.d("HowToGetRedeemCode", "链接检查: isNull=${redeemCodeUrl == null}, isBlank=${redeemCodeUrl.isNullOrBlank()}")
        
        if (!redeemCodeUrl.isNullOrBlank()) {
            // 有链接，显示按钮
            android.util.Log.d("HowToGetRedeemCode", "显示兑换码链接按钮")
            redeemLinkButton.visibility = android.view.View.VISIBLE
            redeemLinkButton.setOnClickListener {
                android.util.Log.d("HowToGetRedeemCode", "按钮被点击，准备打开链接: $redeemCodeUrl")
                openRedeemCodeLink(redeemCodeUrl)
            }
            descriptionText.text = "兑换码可以通过以下方式领取：\n\n1. 点击下方按钮前往领取页面\n2. 获取兑换码后，返回本应用激活\n3. 每个兑换码只能使用一次"
        } else {
            // 没有链接，隐藏按钮，显示提示
            android.util.Log.d("HowToGetRedeemCode", "隐藏兑换码链接按钮（链接为空或null）")
            redeemLinkButton.visibility = android.view.View.GONE
            descriptionText.text = "兑换码领取方式：\n\n目前暂时还没有兑换码，请稍后再试。\n\n如有兑换码，请点击下方按钮进入激活页面输入兑换码。"
        }
    }
    
    /**
     * 打开兑换码链接（优先使用小红书，未安装则用浏览器）
     */
    private fun openRedeemCodeLink(url: String) {
        android.util.Log.d("HowToGetRedeemCode", "开始打开链接: $url")
        
        // 检查是否是小红书链接
        val isXhsLink = url.contains("xhslink.com", ignoreCase = true)
        android.util.Log.d("HowToGetRedeemCode", "是否小红书链接: $isXhsLink")
        
        if (isXhsLink) {
            // 尝试使用小红书应用打开
            try {
                android.util.Log.d("HowToGetRedeemCode", "尝试使用小红书应用打开链接")
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    setPackage("com.xingin.xhs")  // 小红书国内版包名
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
                android.util.Log.d("HowToGetRedeemCode", "成功使用小红书应用打开链接")
                return
            } catch (e: ActivityNotFoundException) {
                android.util.Log.w("HowToGetRedeemCode", "小红书应用未安装，尝试使用浏览器", e)
            } catch (e: Exception) {
                android.util.Log.e("HowToGetRedeemCode", "使用小红书应用打开失败，尝试浏览器", e)
            }
        }
        
        // 使用浏览器打开（或者小红书未安装时的后备方案）
        try {
            android.util.Log.d("HowToGetRedeemCode", "尝试使用浏览器打开链接")
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            // 检查是否有可以处理该 Intent 的应用
            val packageManager = packageManager
            val activities = packageManager.queryIntentActivities(browserIntent, 0)
            android.util.Log.d("HowToGetRedeemCode", "可以处理该链接的应用数量: ${activities.size}")
            
            if (activities.isEmpty()) {
                Toast.makeText(this, "没有可以打开该链接的应用", Toast.LENGTH_LONG).show()
                android.util.Log.e("HowToGetRedeemCode", "没有应用可以处理该链接")
                return
            }
            
            startActivity(browserIntent)
            android.util.Log.d("HowToGetRedeemCode", "成功使用浏览器打开链接")
        } catch (e: Exception) {
            android.util.Log.e("HowToGetRedeemCode", "打开链接失败", e)
            Toast.makeText(this, "无法打开链接: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

