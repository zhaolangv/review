package com.gongkao.cuotifupan.data

/**
 * 笔记/记忆卡片数据类
 * 用于在UI层传递笔记数据
 */
data class NoteItem(
    val id: String,
    val type: String = "note",  // "note" 或 "flashcard"
    val content: String = "",    // 笔记内容（type="note"时使用）
    val front: String = "",     // 记忆卡片正面/提示（type="flashcard"时使用）
    val back: String = "",      // 记忆卡片背面/内容（type="flashcard"时使用）
    val timestamp: Long = System.currentTimeMillis()
)

