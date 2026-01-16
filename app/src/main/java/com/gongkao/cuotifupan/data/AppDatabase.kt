package com.gongkao.cuotifupan.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room 数据库
 */
@Database(entities = [Question::class, ExcludedImage::class, StandaloneNote::class, StandaloneFlashcard::class, MathPracticeSession::class, ScannedImage::class, FlashcardDeck::class], version = 14, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    
    abstract fun questionDao(): QuestionDao
    abstract fun excludedImageDao(): ExcludedImageDao
    abstract fun standaloneNoteDao(): StandaloneNoteDao
    abstract fun standaloneFlashcardDao(): StandaloneFlashcardDao
    abstract fun mathPracticeSessionDao(): MathPracticeSessionDao
    abstract fun scannedImageDao(): ScannedImageDao
    abstract fun flashcardDeckDao(): FlashcardDeckDao
    
    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null
        
        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "snap_review_database"
                )
                .addMigrations(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_13)
                .build()
                INSTANCE = instance
                instance
            }
        }
        
        /**
         * 数据库迁移：从版本8到版本9
         * 添加 originalImagePath 和 cleanedImagePath 字段
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加新字段，默认值为NULL
                database.execSQL("ALTER TABLE questions ADD COLUMN originalImagePath TEXT")
                database.execSQL("ALTER TABLE questions ADD COLUMN cleanedImagePath TEXT")
            }
        }
        
        /**
         * 数据库迁移：从版本9到版本10
         * 添加 annotationPath 字段（图层标注功能）
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE questions ADD COLUMN annotationPath TEXT")
            }
        }
        
        /**
         * 数据库迁移：从版本10到版本11
         * 为 StandaloneFlashcard 添加间隔重复字段（Anki 风格）
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加间隔重复相关字段
                database.execSQL("ALTER TABLE standalone_flashcards ADD COLUMN nextReviewTime INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE standalone_flashcards ADD COLUMN interval INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE standalone_flashcards ADD COLUMN easeFactor REAL NOT NULL DEFAULT 2.5")
                database.execSQL("ALTER TABLE standalone_flashcards ADD COLUMN reviewCount INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE standalone_flashcards ADD COLUMN consecutiveCorrect INTEGER NOT NULL DEFAULT 0")
                
                // 将旧的 reviewState 值迁移到新值
                // unreviewed -> new, mastered -> review, not_mastered -> relearning
                database.execSQL("UPDATE standalone_flashcards SET reviewState = 'new' WHERE reviewState = 'unreviewed'")
                database.execSQL("UPDATE standalone_flashcards SET reviewState = 'review' WHERE reviewState = 'mastered'")
                database.execSQL("UPDATE standalone_flashcards SET reviewState = 'relearning' WHERE reviewState = 'not_mastered'")
            }
        }
        
        /**
         * 数据库迁移：从版本11到版本12
         * 添加卡包（Deck）系统和 deckId 字段
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 创建卡包表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS flashcard_decks (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        parentId TEXT,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        description TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // 为卡片表添加 deckId 字段
                database.execSQL("ALTER TABLE standalone_flashcards ADD COLUMN deckId TEXT")
            }
        }
        
        /**
         * 数据库迁移：从版本12到版本13
         * 添加图片支持字段（Anki 风格）
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 添加正面和背面图片路径字段
                database.execSQL("ALTER TABLE standalone_flashcards ADD COLUMN frontImagePath TEXT")
                database.execSQL("ALTER TABLE standalone_flashcards ADD COLUMN backImagePath TEXT")
            }
        }
        
        /**
         * 数据库迁移：从版本13到版本14
         * 添加 hiddenOptionsImagePath 字段（清除对错痕迹功能）
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE questions ADD COLUMN hiddenOptionsImagePath TEXT")
            }
        }
        
        /**
         * 数据库迁移：从版本14降级到版本13
         * 删除 hiddenOptionsImagePath 字段（该功能已移除）
         * SQLite不支持直接删除列，需要重建表
         */
        private val MIGRATION_14_13 = object : Migration(14, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // SQLite不支持直接删除列，需要重建表
                // 1. 创建临时新表（不包含hiddenOptionsImagePath列）
                database.execSQL("""
                    CREATE TABLE questions_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        imagePath TEXT NOT NULL,
                        originalImagePath TEXT,
                        cleanedImagePath TEXT,
                        annotationPath TEXT,
                        rawText TEXT NOT NULL,
                        questionText TEXT NOT NULL,
                        frontendRawText TEXT,
                        options TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        reviewState TEXT NOT NULL,
                        userNotes TEXT NOT NULL,
                        confidence REAL NOT NULL,
                        questionType TEXT NOT NULL,
                        backendQuestionId TEXT,
                        backendQuestionText TEXT,
                        answerLoaded INTEGER NOT NULL,
                        correctAnswer TEXT,
                        explanation TEXT,
                        tags TEXT NOT NULL
                    )
                """.trimIndent())
                
                // 2. 复制数据（排除hiddenOptionsImagePath列）
                database.execSQL("""
                    INSERT INTO questions_new (
                        id, imagePath, originalImagePath, cleanedImagePath, annotationPath,
                        rawText, questionText, frontendRawText, options, createdAt,
                        reviewState, userNotes, confidence, questionType, backendQuestionId,
                        backendQuestionText, answerLoaded, correctAnswer, explanation, tags
                    )
                    SELECT 
                        id, imagePath, originalImagePath, cleanedImagePath, annotationPath,
                        rawText, questionText, frontendRawText, options, createdAt,
                        reviewState, userNotes, confidence, questionType, backendQuestionId,
                        backendQuestionText, answerLoaded, correctAnswer, explanation, tags
                    FROM questions
                """.trimIndent())
                
                // 3. 删除旧表
                database.execSQL("DROP TABLE questions")
                
                // 4. 重命名新表
                database.execSQL("ALTER TABLE questions_new RENAME TO questions")
            }
        }
    }
}

