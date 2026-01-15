package com.gongkao.cuotifupan.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import com.gongkao.cuotifupan.data.AppDatabase
import com.gongkao.cuotifupan.data.Question
import com.gongkao.cuotifupan.data.QuestionDao
import kotlinx.coroutines.launch

/**
 * 题目 ViewModel
 */
class QuestionViewModel(application: Application) : AndroidViewModel(application) {
    
    private val questionDao: QuestionDao
    val allQuestions: LiveData<List<Question>>
    val allQuestionsOrderByTime: LiveData<List<Question>>
    
    // 排序模式：TIME（按时间）、TAG（按标签）
    var sortMode: SortMode = SortMode.TIME
        private set
    
    enum class SortMode {
        TIME, TAG
    }
    
    init {
        val database = AppDatabase.getDatabase(application)
        questionDao = database.questionDao()
        allQuestions = questionDao.getAllQuestions()
        allQuestionsOrderByTime = questionDao.getAllQuestionsOrderByTime()
    }
    
    fun setSortMode(mode: SortMode) {
        sortMode = mode
    }
    
    fun getQuestionsByTag(tag: String): LiveData<List<Question>> {
        return questionDao.getQuestionsByTag(tag)
    }
    
    fun getQuestionsByReviewState(state: String): LiveData<List<Question>> {
        return questionDao.getQuestionsByReviewState(state)
    }
    
    fun insert(question: Question) = viewModelScope.launch {
        questionDao.insert(question)
    }
    
    fun update(question: Question) = viewModelScope.launch {
        questionDao.update(question)
    }
    
    fun delete(question: Question) = viewModelScope.launch {
        questionDao.delete(question)
    }
    
    fun deleteAll() = viewModelScope.launch {
        questionDao.deleteAll()
    }
    
    suspend fun getQuestionById(id: String): Question? {
        return questionDao.getQuestionById(id)
    }
}

