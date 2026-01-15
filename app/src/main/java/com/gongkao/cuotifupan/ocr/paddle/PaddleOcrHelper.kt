package com.gongkao.cuotifupan.ocr.paddle

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.util.Log
import com.baidu.paddle.lite.MobileConfig
import com.baidu.paddle.lite.PaddlePredictor
import com.baidu.paddle.lite.PowerMode
import com.baidu.paddle.lite.Tensor
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * PaddleOCR 辅助类
 * 使用 Paddle Lite Java API 进行文字识别
 * 完整版：检测+识别流程（det + rec）
 */
object PaddleOcrHelper {
    
    private const val TAG = "PaddleOcrHelper"
    
    // 模型文件路径（在 assets 中）
    private const val MODELS_PATH = "paddleocr/models"
    private const val LABELS_PATH = "paddleocr/labels/ppocr_keys_v1.txt"
    
    // 模型文件名
    private const val DET_MODEL_NAME = "det_db.nb"
    private const val REC_MODEL_NAME = "rec_crnn.nb"
    
    // 检测模型输入尺寸（会被缩放到这个尺寸）
    private const val DET_MAX_SIDE_LEN = 960
    
    // 识别模型输入尺寸
    private const val REC_IMAGE_HEIGHT = 32
    private const val REC_IMAGE_WIDTH = 320
    
    // 检测后处理参数
    private const val DET_THRESHOLD = 0.3f
    private const val DET_DB_THRESH = 0.3f
    private const val DET_DB_BOX_THRESH = 0.6f
    private const val DET_DB_UNCLIP_RATIO = 1.5f
    private const val DET_DB_MAX_CANDIDATES = 1000
    
    // 文本区域信息
    data class TextRegion(
        val points: List<Pair<Int, Int>>,  // 文本区域顶点坐标
        val text: String                    // 识别文本
    )
    
    private var detPredictor: PaddlePredictor? = null
    private var recPredictor: PaddlePredictor? = null
    private var wordLabels: MutableList<String> = mutableListOf()
    private var isInitializedFlag = false
    private var useDetModel = false  // 是否使用检测模型
    private var useNativeImpl = false  // 是否使用 C++ JNI 实现
    
    /**
     * 初始化 PaddleOCR
     * 优先尝试使用 C++ JNI 实现（完整的检测+识别），
     * 如果失败则回退到 Java API 实现（仅识别模型）
     * 
     * 注意：当前 Paddle Lite 库版本是 tiny_publish，缺少 hard_swish 算子
     * 需要下载 with_extra 版本才能正常使用
     * 临时禁用 PaddleOCR，使用 ML Kit 作为替代
     */
    fun init(context: Context): Boolean {
        // 临时禁用 PaddleOCR - Paddle Lite 库需要替换为 with_extra 版本
        // 当前 tiny_publish 版本缺少 hard_swish 算子会导致崩溃
        Log.w(TAG, "⚠️ PaddleOCR 已临时禁用 - Paddle Lite 库需要替换为 with_extra 版本")
        Log.w(TAG, "   请使用 ML Kit OCR 作为替代")
        return false
        
        /* 原始代码 - 等待 Paddle Lite 库更新后启用
        if (isInitializedFlag) {
            return true
        }
        
        try {
            // 1. 复制模型文件到缓存目录
            val cacheDir = context.cacheDir
            val modelsCacheDir = File(cacheDir, MODELS_PATH)
            if (!modelsCacheDir.exists()) {
                modelsCacheDir.mkdirs()
            }
            
            val detModelFile = File(modelsCacheDir, DET_MODEL_NAME)
            val recModelFile = File(modelsCacheDir, REC_MODEL_NAME)
            
            copyAssetFile(context, "$MODELS_PATH/$DET_MODEL_NAME", detModelFile)
            copyAssetFile(context, "$MODELS_PATH/$REC_MODEL_NAME", recModelFile)
            
            // 2. 加载字典（两种实现都需要）
            if (!loadLabels(context)) {
                Log.e(TAG, "加载字典失败")
                return false
            }
            
            // 3. 优先尝试使用 C++ JNI 实现（完整的检测+识别）
            try {
                val nativeConfig = OCRPredictorNative.Config(
                    detModelPath = detModelFile.absolutePath,
                    recModelPath = recModelFile.absolutePath,
                    clsModelPath = "" // 不使用分类模型
                )
                
                if (OCRPredictorNative.init(nativeConfig)) {
                    useNativeImpl = true
                    isInitializedFlag = true
                    Log.i(TAG, "✅ 使用 C++ JNI 实现（完整的检测+识别功能）")
                    return true
                } else {
                    Log.w(TAG, "C++ JNI 实现初始化失败，回退到 Java API 实现")
                }
            } catch (e: Exception) {
                Log.w(TAG, "C++ JNI 实现初始化异常，回退到 Java API 实现", e)
            }
            
            // 4. 回退到 Java API 实现（仅识别模型）
            try {
                // 加载原生库
                System.loadLibrary("paddle_lite_jni")
                Log.d(TAG, "Paddle Lite JNI 库加载成功")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Paddle Lite JNI 库加载失败", e)
                return false
            }
            
            // 初始化识别模型（必需）
            val recConfig = MobileConfig().apply {
                modelFromFile = recModelFile.absolutePath
                threads = 4
                powerMode = PowerMode.LITE_POWER_HIGH
            }
            
            recPredictor = PaddlePredictor.createPaddlePredictor(recConfig)
            if (recPredictor == null) {
                Log.e(TAG, "创建识别预测器失败")
                return false
            }
            
            // 检测模型暂时禁用（避免崩溃）
            useDetModel = false
            detPredictor = null
            useNativeImpl = false
            
            isInitializedFlag = true
            Log.d(TAG, "✅ 使用 Java API 实现（仅识别模型，适合单字/单行）")
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "PaddleOCR 初始化失败", e)
            return false
        }
        原始代码结束 */
    }
    
    /**
     * 复制资源文件到缓存目录
     */
    private fun copyAssetFile(context: Context, assetPath: String, destFile: File) {
        if (destFile.exists() && destFile.length() > 0) {
            return  // 已经存在且非空，跳过
        }
        
        try {
            context.assets.open(assetPath).use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "复制文件成功: $assetPath -> ${destFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "复制文件失败: $assetPath", e)
            throw e
        }
    }
    
    /**
     * 加载字典文件
     */
    private fun loadLabels(context: Context): Boolean {
        wordLabels.clear()
        wordLabels.add("")  // 索引0：CTC blank
        
        try {
            context.assets.open(LABELS_PATH).bufferedReader().use { reader ->
                reader.forEachLine { line ->
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) {
                        wordLabels.add(trimmed)
                    }
                }
            }
            Log.d(TAG, "字典加载成功，共 ${wordLabels.size} 个字符（包括blank）")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "字典加载失败", e)
            return false
        }
    }
    
    /**
     * 识别图片中的文字
     * @param bitmap 要识别的图片
     * @return 识别结果文本，失败返回 null
     */
    fun recognizeText(bitmap: Bitmap): String? {
        if (!isInitializedFlag) {
            Log.e(TAG, "PaddleOCR 未初始化")
            return null
        }
        
        try {
            // 如果使用 C++ JNI 实现，使用完整的检测+识别流程
            if (useNativeImpl) {
                return recognizeTextWithNative(bitmap)
            }
            
            // 否则使用 Java API 实现
            if (recPredictor == null) {
                Log.e(TAG, "识别预测器未初始化")
                return null
            }
            
            // 如果检测模型可用，使用检测+识别流程（识别整张图片）
            // 否则使用直接识别（仅识别单字/单行）
            if (useDetModel && detPredictor != null) {
                return recognizeTextWithDetection(bitmap)
            } else {
                Log.d(TAG, "检测模型不可用，使用直接识别模式（仅适合单字/单行）")
                return recognizeTextDirect(bitmap)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "识别失败", e)
            return null
        }
    }
    
    /**
     * 使用 C++ JNI 实现进行识别（完整的检测+识别）
     */
    private fun recognizeTextWithNative(bitmap: Bitmap): String? {
        try {
            Log.d(TAG, "使用 C++ JNI 实现识别，图片尺寸: ${bitmap.width}x${bitmap.height}")
            
            val results = OCRPredictorNative.runImage(
                originalImage = bitmap,
                maxSizeLen = DET_MAX_SIDE_LEN,
                runDet = 1,  // 运行检测模型
                runCls = 0,  // 不运行分类模型
                runRec = 1   // 运行识别模型
            )
            
            if (results.isEmpty()) {
                Log.w(TAG, "C++ JNI 实现未检测到任何文本")
                return null
            }
            
            Log.d(TAG, "C++ JNI 实现检测到 ${results.size} 个文本区域")
            
            // 将所有识别结果合并为一行文本
            val textBuilder = StringBuilder()
            results.forEachIndexed { index, result ->
                val text = result.toText(wordLabels)
                if (text.isNotBlank()) {
                    if (textBuilder.isNotEmpty()) {
                        textBuilder.append("\n")
                    }
                    textBuilder.append(text)
                    Log.d(TAG, "文本区域 ${index + 1}: '$text' (置信度: ${result.score})")
                }
            }
            
            val finalText = textBuilder.toString()
            Log.d(TAG, "C++ JNI 实现识别完成，结果: '$finalText'")
            return finalText
            
        } catch (e: Exception) {
            Log.e(TAG, "C++ JNI 实现识别失败", e)
            return null
        }
    }
    
    /**
     * 使用检测+识别流程识别整张图片的文字
     */
    private fun recognizeTextWithDetection(bitmap: Bitmap): String? {
        try {
            Log.d(TAG, "开始检测+识别流程，图片尺寸: ${bitmap.width}x${bitmap.height}")
            
            // 1. 使用检测模型检测文本区域
            val textRegions = detectTextRegions(bitmap)
            if (textRegions.isEmpty()) {
                Log.w(TAG, "未检测到文本区域，检测流程可能失败")
                return null
            }
            
            Log.d(TAG, "✅ 检测到 ${textRegions.size} 个文本区域")
            
            // 2. 对每个文本区域进行识别
            val recognizedTexts = mutableListOf<String>()
            textRegions.forEachIndexed { index, region ->
                Log.d(TAG, "识别文本区域 ${index + 1}/${textRegions.size}")
                val text = recognizeTextRegion(bitmap, region)
                if (text != null && text.isNotBlank()) {
                    recognizedTexts.add(text)
                    Log.d(TAG, "  区域 ${index + 1} 识别结果: $text")
                } else {
                    Log.w(TAG, "  区域 ${index + 1} 识别结果为空")
                }
            }
            
            // 3. 合并所有识别结果（按从上到下、从左到右的顺序）
            val result = recognizedTexts.joinToString("\n")
            Log.d(TAG, "✅ 识别完成，共 ${recognizedTexts.size} 个文本区域，总长度: ${result.length}")
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "检测+识别流程失败", e)
            return null
        }
    }
    
    /**
     * 检测文本区域
     */
    private fun detectTextRegions(bitmap: Bitmap): List<List<Pair<Int, Int>>> {
        try {
            // 1. 预处理图片（缩放、归一化）
            val (processedBitmap, scaleX, scaleY) = preprocessDetImage(bitmap)
            
            // 2. 运行检测模型
            val inputTensor = detPredictor!!.getInput(0)
            val inputWidth = processedBitmap.width
            val inputHeight = processedBitmap.height
            
            inputTensor.resize(longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong()))
            val inputData = bitmapToFloatArray(processedBitmap, normalizeType = NormalizeType.DET)
            inputTensor.setData(inputData)
            
            detPredictor!!.run()
            
            // 3. 获取输出（概率图）
            val outputTensor = detPredictor!!.getOutput(0)
            val outputShape = outputTensor.shape()
            val outputData = outputTensor.floatData
            
            Log.d(TAG, "检测输出形状: ${outputShape.contentToString()}")
            
            // 4. 后处理：从概率图提取文本区域
            val textRegions = postProcessDetection(
                outputData, 
                outputShape, 
                inputWidth, 
                inputHeight,
                scaleX,
                scaleY,
                bitmap.width,
                bitmap.height
            )
            
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            
            return textRegions
            
        } catch (e: Exception) {
            Log.e(TAG, "文本检测失败", e)
            return emptyList()
        }
    }
    
    /**
     * 预处理检测图片
     * 注意：检测模型需要 RGB 彩色图，不是灰度图
     */
    private fun preprocessDetImage(bitmap: Bitmap): Triple<Bitmap, Float, Float> {
        val srcWidth = bitmap.width.toFloat()
        val srcHeight = bitmap.height.toFloat()
        
        // 计算缩放比例（保持长宽比，最长边不超过 DET_MAX_SIDE_LEN）
        val scale = DET_MAX_SIDE_LEN / max(srcWidth, srcHeight)
        val newWidth = (srcWidth * scale).toInt()
        val newHeight = (srcHeight * scale).toInt()
        
        // 缩放到目标尺寸（保持 RGB 彩色，不转换为灰度）
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        
        // 确保是 ARGB_8888 格式（RGB 彩色图）
        val rgbBitmap = if (scaledBitmap.config == Bitmap.Config.ARGB_8888) {
            scaledBitmap
        } else {
            val convertedBitmap = Bitmap.createBitmap(newWidth, newHeight, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(convertedBitmap)
            canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
            convertedBitmap
        }
        
        if (rgbBitmap != bitmap && rgbBitmap != scaledBitmap) {
            if (scaledBitmap != bitmap) {
                scaledBitmap.recycle()
            }
        }
        
        val scaleX = srcWidth / newWidth
        val scaleY = srcHeight / newHeight
        
        return Triple(rgbBitmap, scaleX, scaleY)
    }
    
    /**
     * 检测后处理：从概率图提取文本区域
     * 简化实现版本：使用阈值化和简单的文本区域检测
     * 
     * 注意：这是简化实现，不包含完整的轮廓检测和多边形拟合。
     * 完整实现需要 OpenCV Java API 或大量算法代码。
     */
    private fun postProcessDetection(
        outputData: FloatArray,
        outputShape: LongArray,
        inputWidth: Int,
        inputHeight: Int,
        scaleX: Float,
        scaleY: Float,
        originalWidth: Int,
        originalHeight: Int
    ): List<List<Pair<Int, Int>>> {
        if (outputShape.size < 4) {
            Log.e(TAG, "检测输出形状不正确: ${outputShape.contentToString()}")
            return emptyList()
        }
        
        val batchSize = outputShape[0].toInt()
        val channels = outputShape[1].toInt()
        val height = outputShape[2].toInt()
        val width = outputShape[3].toInt()
        
        Log.d(TAG, "检测输出: batch=$batchSize, channels=$channels, height=$height, width=$width, 数据大小=${outputData.size}")
        
        // 提取概率图（输出格式为 [batch, channels, height, width]）
        // DB 模型通常输出单通道概率图，数据布局为 NCHW
        val totalSize = batchSize * channels * height * width
        if (outputData.size < totalSize) {
            Log.e(TAG, "输出数据大小不足: ${outputData.size} < $totalSize")
            return emptyList()
        }
        
        val probMap = FloatArray(height * width)
        
        // DB 模型输出通常是 [1, 1, height, width]，数据是连续存储的（NCHW 格式）
        if (batchSize == 1 && channels == 1) {
            // 简单情况：直接复制（NCHW 格式，batch=0, channel=0）
            val copySize = min(outputData.size, probMap.size)
            System.arraycopy(outputData, 0, probMap, 0, copySize)
            Log.d(TAG, "概率图提取完成（NCHW格式），复制了 $copySize 个数据点")
        } else {
            // 复杂情况：需要根据 NCHW 格式提取数据
            // NCHW: [batch][channel][height][width]
            // index = (n * C + c) * (H * W) + (h * W + w)
            var copiedCount = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val dstIndex = y * width + x
                    // 提取 batch=0, channel=0 的数据
                    val srcIndex = (0 * channels + 0) * (height * width) + (y * width + x)
                    if (srcIndex < outputData.size) {
                        probMap[dstIndex] = outputData[srcIndex]
                        copiedCount++
                    }
                }
            }
            Log.d(TAG, "概率图提取完成（复杂NCHW格式），复制了 $copiedCount 个数据点")
        }
        
        // 1. 阈值化（DB 后处理阈值通常为 0.3）
        val threshold = 0.3f
        val binaryMap = BooleanArray(height * width)
        var textPixelCount = 0
        for (i in probMap.indices) {
            binaryMap[i] = probMap[i] > threshold
            if (binaryMap[i]) textPixelCount++
        }
        Log.d(TAG, "阈值化完成（阈值=$threshold），文本像素数: $textPixelCount / ${binaryMap.size} (${textPixelCount * 100f / binaryMap.size}%)")
        
        // 2. 简单的文本区域检测（使用水平投影和垂直投影）
        val textRegions = detectTextRegionsSimple(binaryMap, width, height)
        
        if (textRegions.isEmpty()) {
            Log.w(TAG, "未检测到文本区域（投影方法失败）")
            return emptyList()
        }
        
        // 3. 坐标转换：从模型输入尺寸转换回原图尺寸
        val scaledRegions = textRegions.map { region ->
            region.map { point ->
                val scaledX = (point.first / scaleX).toInt().coerceIn(0, originalWidth - 1)
                val scaledY = (point.second / scaleY).toInt().coerceIn(0, originalHeight - 1)
                Pair(scaledX, scaledY)
            }
        }
        
        Log.d(TAG, "检测到 ${scaledRegions.size} 个文本区域")
        return scaledRegions
    }
    
    /**
     * 简单的文本区域检测（使用投影方法）
     * 这是简化实现，不包含完整的轮廓检测和多边形拟合
     */
    private fun detectTextRegionsSimple(
        binaryMap: BooleanArray,
        width: Int,
        height: Int
    ): List<List<Pair<Int, Int>>> {
        // 计算水平投影（每一行的文本像素数量）
        val horizontalProjection = IntArray(height)
        for (y in 0 until height) {
            var count = 0
            for (x in 0 until width) {
                if (binaryMap[y * width + x]) {
                    count++
                }
            }
            horizontalProjection[y] = count
        }
        
        // 计算垂直投影（每一列的文本像素数量）
        val verticalProjection = IntArray(width)
        for (x in 0 until width) {
            var count = 0
            for (y in 0 until height) {
                if (binaryMap[y * width + x]) {
                    count++
                }
            }
            verticalProjection[x] = count
        }
        
        // 使用投影分割文本行（水平方向）
        val textLines = mutableListOf<Pair<Int, Int>>()
        val minLineHeight = 5 // 最小行高
        
        var inLine = false
        var lineStart = -1
        
        for (y in 0 until height) {
            val projection = horizontalProjection[y]
            val hasText = projection > width * 0.05f // 阈值：文本像素占比 > 5%
            
            if (hasText && !inLine) {
                lineStart = y
                inLine = true
            } else if (!hasText && inLine) {
                val lineEnd = y - 1
                if (lineEnd - lineStart >= minLineHeight) {
                    textLines.add(Pair(lineStart, lineEnd))
                }
                inLine = false
            }
        }
        
        // 处理最后一行
        if (inLine && height - lineStart >= minLineHeight) {
            textLines.add(Pair(lineStart, height - 1))
        }
        
        if (textLines.isEmpty()) {
            return emptyList()
        }
        
        // 对每一行，检测文本列（垂直方向）
        val regions = mutableListOf<List<Pair<Int, Int>>>()
        val minColumnWidth = 5 // 最小列宽
        
        for ((lineStartY, lineEndY) in textLines) {
            var inColumn = false
            var columnStart = -1
            
            for (x in 0 until width) {
                // 检查这一列在当前行范围内是否有文本
                var hasText = false
                for (y in lineStartY..lineEndY) {
                    if (binaryMap[y * width + x]) {
                        hasText = true
                        break
                    }
                }
                
                // 或者使用垂直投影
                val projection = verticalProjection[x]
                hasText = hasText || (projection > height * 0.1f)
                
                if (hasText && !inColumn) {
                    columnStart = x
                    inColumn = true
                } else if (!hasText && inColumn) {
                    val columnEnd = x - 1
                    if (columnEnd - columnStart >= minColumnWidth) {
                        // 创建矩形区域（4个顶点）
                        val region = listOf(
                            Pair(columnStart, lineStartY),      // 左上
                            Pair(columnEnd, lineStartY),        // 右上
                            Pair(columnEnd, lineEndY),          // 右下
                            Pair(columnStart, lineEndY)         // 左下
                        )
                        regions.add(region)
                    }
                    inColumn = false
                }
            }
            
            // 处理最后一列
            if (inColumn && width - columnStart >= minColumnWidth) {
                val region = listOf(
                    Pair(columnStart, lineStartY),
                    Pair(width - 1, lineStartY),
                    Pair(width - 1, lineEndY),
                    Pair(columnStart, lineEndY)
                )
                regions.add(region)
            }
        }
        
        return regions
    }
    
    /**
     * 识别单个文本区域
     */
    private fun recognizeTextRegion(bitmap: Bitmap, region: List<Pair<Int, Int>>): String? {
        try {
            // 1. 裁剪文本区域
            val regionBitmap = cropTextRegion(bitmap, region)
            if (regionBitmap == null) {
                return null
            }
            
            // 2. 使用识别模型识别
            val text = recognizeTextDirect(regionBitmap)
            
            // 回收裁剪的 bitmap
            if (regionBitmap != bitmap) {
                regionBitmap.recycle()
            }
            
            return text
            
        } catch (e: Exception) {
            Log.e(TAG, "识别文本区域失败", e)
            return null
        }
    }
    
    /**
     * 裁剪文本区域
     */
    private fun cropTextRegion(bitmap: Bitmap, region: List<Pair<Int, Int>>): Bitmap? {
        if (region.size < 4) {
            return null
        }
        
        // 计算边界框
        val minX = region.minOf { it.first }.coerceAtLeast(0)
        val maxX = region.maxOf { it.first }.coerceAtMost(bitmap.width - 1)
        val minY = region.minOf { it.second }.coerceAtLeast(0)
        val maxY = region.maxOf { it.second }.coerceAtMost(bitmap.height - 1)
        
        if (minX >= maxX || minY >= maxY) {
            return null
        }
        
        return Bitmap.createBitmap(bitmap, minX, minY, maxX - minX, maxY - minY)
    }
    
    /**
     * 直接使用识别模型识别（不经过检测）
     * 适用于手写单字场景
     */
    private fun recognizeTextDirect(bitmap: Bitmap): String? {
        try {
            // 1. 预处理图片
            val processedBitmap = preprocessRecImage(bitmap)
            
            // 2. 转换为输入张量
            val inputTensor = recPredictor!!.getInput(0)
            val inputWidth = processedBitmap.width
            val inputHeight = processedBitmap.height
            
            // 设置输入形状: [batch, channel, height, width]
            inputTensor.resize(longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong()))
            
            // 填充输入数据
            val inputData = bitmapToFloatArray(processedBitmap, normalizeType = NormalizeType.REC)
            inputTensor.setData(inputData)
            
            // 3. 运行推理
            recPredictor!!.run()
            
            // 4. 获取输出
            val outputTensor = recPredictor!!.getOutput(0)
            val outputShape = outputTensor.shape()
            val outputData = outputTensor.floatData
            
            Log.d(TAG, "识别输出形状: ${outputShape.contentToString()}")
            
            // 5. CTC 解码
            val result = ctcDecode(outputData, outputShape)
            
            // 回收处理后的 bitmap
            if (processedBitmap != bitmap) {
                processedBitmap.recycle()
            }
            
            return result
            
        } catch (e: Exception) {
            Log.e(TAG, "直接识别失败", e)
            return null
        }
    }
    
    /**
     * 预处理识别图片
     */
    private fun preprocessRecImage(bitmap: Bitmap): Bitmap {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        
        // 按高度等比缩放到 32 像素
        val ratio = REC_IMAGE_HEIGHT.toFloat() / srcHeight
        var newWidth = (srcWidth * ratio).toInt()
        
        // 限制最大宽度
        if (newWidth > REC_IMAGE_WIDTH) {
            newWidth = REC_IMAGE_WIDTH
        }
        
        if (newWidth < 1) {
            newWidth = 1
        }
        
        // 缩放到目标尺寸
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, REC_IMAGE_HEIGHT, true)
        
        // 转换为灰度图
        val grayBitmap = Bitmap.createBitmap(newWidth, REC_IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(grayBitmap)
        val paint = android.graphics.Paint()
        val colorMatrix = android.graphics.ColorMatrix().apply {
            setSaturation(0f)
        }
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        canvas.drawBitmap(scaledBitmap, 0f, 0f, paint)
        
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        
        return grayBitmap
    }
    
    /**
     * 归一化类型
     */
    private enum class NormalizeType {
        DET,  // 检测模型归一化: (pixel - 127.5) / 127.5
        REC   // 识别模型归一化: (pixel / 255.0 - 0.5) / 0.5 = (pixel - 127.5) / 127.5
    }
    
    /**
     * 将 Bitmap 转换为归一化的浮点数组
     * 格式: CHW (Channel, Height, Width)
     * 
     * 检测模型（DET）: 需要 RGB 彩色图，使用标准归一化
     * 识别模型（REC）: 可以使用灰度图，简化归一化
     */
    private fun bitmapToFloatArray(bitmap: Bitmap, normalizeType: NormalizeType): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val channelSize = width * height
        val floatArray = FloatArray(3 * channelSize)
        
        when (normalizeType) {
            NormalizeType.DET -> {
                // 检测模型：使用 RGB 彩色图，标准归一化
                // PaddleOCR DB 模型使用: (pixel / 255.0 - mean) / std
                // mean: [0.485, 0.456, 0.406]
                // std: [0.229, 0.224, 0.225]
                val meanR = 0.485f
                val meanG = 0.456f
                val meanB = 0.406f
                val stdR = 0.229f
                val stdG = 0.224f
                val stdB = 0.225f
                
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    val r = Color.red(pixel) / 255.0f
                    val g = Color.green(pixel) / 255.0f
                    val b = Color.blue(pixel) / 255.0f
                    
                    // 标准归一化: (pixel / 255.0 - mean) / std
                    floatArray[i] = (r - meanR) / stdR
                    floatArray[channelSize + i] = (g - meanG) / stdG
                    floatArray[2 * channelSize + i] = (b - meanB) / stdB
                }
            }
            NormalizeType.REC -> {
                // 识别模型：使用灰度图，简化归一化
                for (i in pixels.indices) {
                    val pixel = pixels[i]
                    val gray = Color.red(pixel)  // 灰度图 RGB 值相同
                    
                    // 归一化: (pixel / 255.0 - 0.5) / 0.5 = (pixel - 127.5) / 127.5
                    val normalized = (gray / 255.0f - 0.5f) / 0.5f
                    
                    // 复制到3个通道（R=G=B）
                    floatArray[i] = normalized
                    floatArray[channelSize + i] = normalized
                    floatArray[2 * channelSize + i] = normalized
                }
            }
        }
        
        return floatArray
    }
    
    /**
     * CTC 贪婪解码
     */
    private fun ctcDecode(outputData: FloatArray, outputShape: LongArray): String {
        if (outputShape.size < 3) {
            Log.e(TAG, "输出形状不正确: ${outputShape.contentToString()}")
            return ""
        }
        
        val seqLen = outputShape[1].toInt()
        val numClasses = outputShape[2].toInt()
        
        val result = StringBuilder()
        var lastIndex = -1
        
        for (t in 0 until seqLen) {
            var maxIndex = 0
            var maxProb = Float.NEGATIVE_INFINITY
            
            val offset = t * numClasses
            
            for (c in 0 until numClasses) {
                val prob = outputData[offset + c]
                if (prob > maxProb) {
                    maxProb = prob
                    maxIndex = c
                }
            }
            
            // CTC 解码：跳过 blank 和重复字符
            if (maxIndex != 0 && maxIndex != lastIndex) {
                if (maxIndex > 0 && maxIndex < wordLabels.size) {
                    val char = wordLabels[maxIndex]
                    if (char.isNotEmpty()) {
                        result.append(char)
                    }
                }
            }
            lastIndex = maxIndex
        }
        
        val decodedText = result.toString()
        Log.d(TAG, "识别结果: '$decodedText'")
        return decodedText
    }
    
    /**
     * 释放资源
     */
    fun release() {
        if (useNativeImpl) {
            OCRPredictorNative.release()
        }
        
        detPredictor = null
        recPredictor = null
        wordLabels.clear()
        isInitializedFlag = false
        useDetModel = false
        useNativeImpl = false
        Log.d(TAG, "PaddleOCR 资源已释放")
    }
    
    /**
     * 检查是否已初始化
     */
    fun isInitialized(): Boolean = isInitializedFlag
}
