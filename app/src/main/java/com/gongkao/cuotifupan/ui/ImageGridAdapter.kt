package com.gongkao.cuotifupan.ui

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.gongkao.cuotifupan.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import java.io.File

/**
 * 图片网格适配器
 */
class ImageGridAdapter(
    private val selectedImages: MutableSet<String>,
    private val onImageClick: (ManualImportActivity.ImageInfo, Int) -> Unit,
    private val onItemClick: (ManualImportActivity.ImageInfo) -> Unit
) : ListAdapter<ManualImportActivity.ImageInfo, ImageGridAdapter.ViewHolder>(DiffCallback()) {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_grid, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val imageInfo = getItem(position)
        holder.bind(imageInfo, position)
    }
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val imageView: ImageView = itemView.findViewById(R.id.imageView)
        private val checkBoxContainer: View = itemView.findViewById(R.id.checkBoxContainer)
        private val checkIcon: ImageView = itemView.findViewById(R.id.checkIcon)
        private var currentPath: String? = null
        
        fun bind(imageInfo: ManualImportActivity.ImageInfo, position: Int) {
            // 设置选择状态
            val isSelected = selectedImages.contains(imageInfo.path)
            checkBoxContainer.isSelected = isSelected
            checkIcon.visibility = if (isSelected) View.VISIBLE else View.GONE
            
            // 选择框点击事件（选择/取消选择）
            checkBoxContainer.setOnClickListener {
                onItemClick(imageInfo)
            }
            
            // 图片点击事件（放大查看）
            imageView.setOnClickListener {
                onImageClick(imageInfo, position)
            }
            
            // 整个item点击事件（切换选择状态）
            itemView.setOnClickListener {
                // 如果点击的不是选择框或图片，则切换选择状态
                onItemClick(imageInfo)
            }
            
            // 加载缩略图（避免重复加载）
            if (currentPath != imageInfo.path) {
                currentPath = imageInfo.path
                imageView.setImageBitmap(null) // 先清空
                
                CoroutineScope(Dispatchers.Main).launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        loadThumbnail(imageInfo.path)
                    }
                    // 检查路径是否还是当前项（避免列表滚动时显示错误图片）
                    if (currentPath == imageInfo.path) {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                        } else {
                            // 如果 bitmap 为 null，显示占位图
                            imageView.setImageResource(android.R.drawable.ic_menu_report_image)
                        }
                    }
                }
            }
        }
        
        private suspend fun loadThumbnail(path: String): android.graphics.Bitmap? {
            return try {
                val context = itemView.context
                
                // 先获取图片尺寸（使用 ContentResolver）
                val (width, height) = com.gongkao.cuotifupan.util.ImageAccessHelper.getImageSize(context, path)
                
                // 如果解码失败，返回null
                if (width <= 0 || height <= 0) {
                    android.util.Log.w("ImageGridAdapter", "⚠️ 图片解码失败: $path (width=$width, height=$height)")
                    return null
                }
                
                // 计算缩放比例
                val reqWidth = 300
                val reqHeight = 300
                val inSampleSize = calculateInSampleSize(width, height, reqWidth, reqHeight)
                
                // 解码缩略图
                val options = BitmapFactory.Options().apply {
                    this.inSampleSize = inSampleSize
                }
                val bitmap = com.gongkao.cuotifupan.util.ImageAccessHelper.decodeBitmap(context, path, options)
                
                if (bitmap == null) {
                    android.util.Log.w("ImageGridAdapter", "⚠️ 图片解码返回null: $path")
                }
                
                bitmap
            } catch (e: Exception) {
                android.util.Log.e("ImageGridAdapter", "加载缩略图失败: $path", e)
                null
            }
        }
        
        private fun calculateInSampleSize(
            width: Int,
            height: Int,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            var inSampleSize = 1
            
            if (height > reqHeight || width > reqWidth) {
                val halfHeight = height / 2
                val halfWidth = width / 2
                
                while ((halfHeight / inSampleSize) >= reqHeight &&
                    (halfWidth / inSampleSize) >= reqWidth) {
                    inSampleSize *= 2
                }
            }
            
            return inSampleSize
        }
    }
    
    private class DiffCallback : DiffUtil.ItemCallback<ManualImportActivity.ImageInfo>() {
        override fun areItemsTheSame(
            oldItem: ManualImportActivity.ImageInfo,
            newItem: ManualImportActivity.ImageInfo
        ): Boolean = oldItem.id == newItem.id
        
        override fun areContentsTheSame(
            oldItem: ManualImportActivity.ImageInfo,
            newItem: ManualImportActivity.ImageInfo
        ): Boolean = oldItem == newItem
    }
}

