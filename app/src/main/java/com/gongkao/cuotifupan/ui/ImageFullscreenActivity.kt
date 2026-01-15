package com.gongkao.cuotifupan.ui

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.gongkao.cuotifupan.R
import com.github.chrisbanes.photoview.PhotoView
import java.io.File

/**
 * 全屏查看图片 Activity
 */
class ImageFullscreenActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var adapter: ImagePagerAdapter
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_fullscreen)
        
        // 隐藏系统UI，实现全屏
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )
        
        // 从缓存获取图片路径列表（避免TransactionTooLargeException）
        val imagePaths = ImagePathCache.getImagePaths() ?: emptyList()
        val currentPosition = intent.getIntExtra(EXTRA_CURRENT_POSITION, 0)
        
        if (imagePaths.isEmpty()) {
            finish()
            return
        }
        
        viewPager = findViewById(R.id.viewPager)
        adapter = ImagePagerAdapter(imagePaths) {
            // 点击图片退出全屏
            finish()
        }
        viewPager.adapter = adapter
        viewPager.currentItem = currentPosition
    }
    
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // 保持全屏
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // 清除缓存
        ImagePathCache.clear()
    }
    
    companion object {
        const val EXTRA_CURRENT_POSITION = "current_position"
    }
}

/**
 * ViewPager2 适配器
 */
class ImagePagerAdapter(
    private val imagePaths: List<String>,
    private val onImageClick: (Int) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<ImagePagerAdapter.ViewHolder>() {
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_fullscreen, parent, false)
        return ViewHolder(view, onImageClick)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(imagePaths[position], position)
    }
    
    override fun getItemCount(): Int = imagePaths.size
    
    inner class ViewHolder(
        itemView: View,
        private val onImageClick: (Int) -> Unit
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        private val photoView: PhotoView = itemView.findViewById(R.id.imageView)
        
        fun bind(imagePath: String, position: Int) {
            try {
                val file = File(imagePath)
                if (file.exists()) {
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    photoView.setImageBitmap(bitmap)
                    
                    // PhotoView 默认支持缩放，设置初始缩放类型
                    photoView.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                }
            } catch (e: Exception) {
                // 加载失败
            }
            
            // 点击图片退出全屏（PhotoView 支持双击缩放，单击退出）
            photoView.setOnClickListener {
                onImageClick(position)
            }
        }
    }
}

