package com.gongkao.cuotifupan.ocr

import android.graphics.Point
import android.graphics.Rect

/**
 * 文字块（包含位置信息）
 */
data class TextBlock(
    val text: String,
    val boundingBox: Rect,
    val lines: List<TextLine> = emptyList()
)

/**
 * 文字行
 * 包含边界框和角点信息（用于倾斜检测）
 */
data class TextLine(
    val text: String,
    val boundingBox: Rect,
    val cornerPoints: List<Point> = emptyList()  // 四个角点（左上、右上、右下、左下），用于倾斜检测
)

