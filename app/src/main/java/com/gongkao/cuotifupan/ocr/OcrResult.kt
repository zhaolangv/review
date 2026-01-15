package com.gongkao.cuotifupan.ocr

/**
 * OCR 识别结果
 */
data class OcrResult(
    val rawText: String,
    val lines: List<String>,
    val textBlocks: List<TextBlock> = emptyList(),  // 新增：带位置的文字块
    val success: Boolean = true,
    val errorMessage: String? = null
)

