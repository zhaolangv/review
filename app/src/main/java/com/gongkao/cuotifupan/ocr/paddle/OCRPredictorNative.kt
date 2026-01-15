package com.gongkao.cuotifupan.ocr.paddle

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * PaddleOCR Native 包装类
 * 使用 C++ JNI 实现，提供完整的检测+识别功能
 * 
 * 注意：需要 libNative.so 库（通过 CMake 编译）
 */
object OCRPredictorNative {
    private const val TAG = "OCRPredictorNative"
    
    private val isSOLoaded = AtomicBoolean(false)
    
    /**
     * 加载原生库
     */
    private fun loadLibrary() {
        if (!isSOLoaded.get() && isSOLoaded.compareAndSet(false, true)) {
            try {
                System.loadLibrary("Native")
                Log.d(TAG, "✅ Native 库加载成功")
            } catch (e: Throwable) {
                Log.e(TAG, "❌ 加载 libNative.so 失败", e)
                throw RuntimeException("Load libNative.so failed, please check it exists in apk file.", e)
            }
        }
    }
    
    /**
     * 配置类
     */
    data class Config(
        val useOpencl: Int = 0,
        val cpuThreadNum: Int = 4,
        val cpuPower: String = "LITE_POWER_HIGH",
        val detModelPath: String,
        val recModelPath: String,
        val clsModelPath: String = "" // 分类模型（可选）
    )
    
    /**
     * OCR 结果数据类
     */
    data class OCRResult(
        val points: List<List<Int>>,  // 文本区域顶点坐标 [[x1,y1,x2,y2,...]]
        val wordIndex: List<Int>,     // 识别的字符索引列表
        val score: Float,             // 置信度
        val clsLabel: Int = 0,        // 分类标签
        val clsScore: Float = 0f      // 分类置信度
    ) {
        /**
         * 将字符索引转换为文本字符串
         */
        fun toText(wordLabels: List<String>): String {
            return wordIndex.mapNotNull { index ->
                if (index >= 0 && index < wordLabels.size) {
                    wordLabels[index]
                } else {
                    null
                }
            }.joinToString("")
        }
    }
    
    private var nativePointer: Long = 0
    
    /**
     * 初始化 OCR 预测器
     */
    fun init(config: Config): Boolean {
        try {
            loadLibrary()
            nativePointer = init(
                config.detModelPath,
                config.recModelPath,
                config.clsModelPath,
                config.useOpencl,
                config.cpuThreadNum,
                config.cpuPower
            )
            Log.i(TAG, "OCR 预测器初始化: $nativePointer")
            return nativePointer != 0L
        } catch (e: Exception) {
            Log.e(TAG, "初始化失败", e)
            return false
        }
    }
    
    /**
     * 运行 OCR 识别
     * @param originalImage 原始图片
     * @param maxSizeLen 最大边长（用于检测模型输入缩放）
     * @param runDet 是否运行检测模型（1=运行，0=不运行）
     * @param runCls 是否运行分类模型（1=运行，0=不运行）
     * @param runRec 是否运行识别模型（1=运行，0=不运行）
     * @return OCR 结果列表
     */
    fun runImage(
        originalImage: Bitmap,
        maxSizeLen: Int = 960,
        runDet: Int = 1,
        runCls: Int = 0,
        runRec: Int = 1
    ): List<OCRResult> {
        if (nativePointer == 0L) {
            Log.e(TAG, "预测器未初始化")
            return emptyList()
        }
        
        try {
                val rawResults = forward(nativePointer, originalImage, maxSizeLen, runDet, runCls, runRec)
            return postprocess(rawResults)
        } catch (e: Exception) {
            Log.e(TAG, "OCR 识别失败", e)
            return emptyList()
        }
    }
    
    /**
     * 释放资源
     */
    fun release() {
        if (nativePointer != 0L) {
            try {
                    release(nativePointer)
                nativePointer = 0
                Log.d(TAG, "资源已释放")
            } catch (e: Exception) {
                Log.e(TAG, "释放资源失败", e)
            }
        }
    }
    
    /**
     * 后处理：将原始 float 数组转换为 OCRResult 列表
     * 格式：每个结果包含：
     *   - point_num (float, 转为 int)
     *   - word_num (float, 转为 int)
     *   - score (float)
     *   - points (point_num * 2 floats, 转为 int)
     *   - word_index (word_num floats, 转为 int)
     *   - cls_label (float, 转为 int)
     *   - cls_score (float)
     */
    private fun postprocess(raw: FloatArray): List<OCRResult> {
        val results = mutableListOf<OCRResult>()
        var begin = 0
        
        while (begin < raw.size) {
            if (begin + 2 >= raw.size) {
                break
            }
            
            val pointNum = Math.round(raw[begin]).toInt()
            val wordNum = Math.round(raw[begin + 1]).toInt()
            
            val requiredSize = begin + 2 + 1 + pointNum * 2 + wordNum + 2
            if (requiredSize > raw.size) {
                Log.w(TAG, "数据格式错误，跳过。需要 $requiredSize 但只有 ${raw.size}")
                break
            }
            
            val result = parse(raw, begin + 2, pointNum, wordNum)
            results.add(result)
            begin += 2 + 1 + pointNum * 2 + wordNum + 2
        }
        
        return results
    }
    
    /**
     * 解析单个 OCR 结果
     */
    private fun parse(raw: FloatArray, begin: Int, pointNum: Int, wordNum: Int): OCRResult {
        var current = begin
        val score = raw[current]
        current++
        
        // 解析坐标点（使用 Math.round 以确保正确的四舍五入）
        val points = mutableListOf<List<Int>>()
        val pointList = mutableListOf<Int>()
        for (i in 0 until pointNum) {
            pointList.add(Math.round(raw[current + i * 2]))
            pointList.add(Math.round(raw[current + i * 2 + 1]))
        }
        points.add(pointList)
        current += pointNum * 2
        
        // 解析字符索引（使用 Math.round）
        val wordIndex = mutableListOf<Int>()
        for (i in 0 until wordNum) {
            wordIndex.add(Math.round(raw[current + i]))
        }
        current += wordNum
        
        val clsLabel = if (current < raw.size) Math.round(raw[current]).toInt() else 0
        val clsScore = if (current + 1 < raw.size) raw[current + 1] else 0f
        
        return OCRResult(points, wordIndex, score, clsLabel, clsScore)
    }
    
    // JNI 方法声明（方法名必须与 C++ 中的函数名匹配，去掉 'native' 前缀）
    private external fun init(
        detModelPath: String,
        recModelPath: String,
        clsModelPath: String,
        useOpencl: Int,
        threadNum: Int,
        cpuMode: String
    ): Long
    
    private external fun forward(
        pointer: Long,
        originalImage: Bitmap,
        maxSizeLen: Int,
        runDet: Int,
        runCls: Int,
        runRec: Int
    ): FloatArray
    
    private external fun release(pointer: Long)
}

