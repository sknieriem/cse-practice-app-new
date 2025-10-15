package com.example.csepractice.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.csepractice.data.PracticeSession
import com.example.csepractice.data.Question
import com.example.csepractice.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

class PracticeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = QuestionRepository(application)
    private val dao = repository.dao  // For new queries

    private val _questions = MutableStateFlow<List<Question>>(emptyList())
    val questions: StateFlow<List<Question>> = _questions.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _selectedAnswers = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val selectedAnswers: StateFlow<Map<Int, Int>> = _selectedAnswers.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _selectedCategories = MutableStateFlow<List<String>>(emptyList())
    val selectedCategories: StateFlow<List<String>> = _selectedCategories.asStateFlow()

    val sessions: Flow<List<PracticeSession>> = repository.getAllSessions()

    val categories: Flow<List<String>> = dao.getCategories()  // New dynamic categories

    init {
        viewModelScope.launch {
            repository.seedQuestionsIfEmpty()
            // No auto-load; wait for startPractice
        }
    }

    fun toggleCategory(category: String) {
        val current = _selectedCategories.value.toMutableList()
        if (current.contains(category)) {
            current.remove(category)
        } else {
            current.add(category)
        }
        _selectedCategories.value = current
    }

    fun startPractice(numQuestions: Int) {
        val categories = _selectedCategories.value
        if (categories.isEmpty()) {
            loadAllQuestions(numQuestions)
        } else {
            loadQuestionsForCategories(categories, numQuestions)
        }
    }

    fun practiceWeakAreas(threshold: Double = 70.0) {
        viewModelScope.launch {
            val allCats = categories.first()  // Get list once
            val weak = mutableListOf<String>()
            for (cat in allCats) {
                val avg = getAverageForCategory(cat).first()  // Get avg once
                if (avg < threshold) {
                    weak.add(cat)
                }
            }
            _selectedCategories.value = weak
            if (weak.isNotEmpty()) {
                startPractice(10)  // Default to 10
            }  // Else, perhaps toast "No weak areas!"
        }
    }

    private fun loadQuestionsForCategories(categories: List<String>, count: Int) {
        viewModelScope.launch {
            val questionsFlow = repository.getRandomQuestionsByCategories(categories, count)
            questionsFlow.collect { loadedQuestions ->
                _questions.value = loadedQuestions
            }
        }
    }

    private fun loadAllQuestions(count: Int) {
        viewModelScope.launch {
            val questionsFlow = repository.getRandomQuestions(count)
            questionsFlow.collect { loadedQuestions ->
                _questions.value = loadedQuestions
            }
        }
    }

    fun getAverageForCategory(category: String): Flow<Double> = dao.getAvgByCategory(category)  // New

    fun selectAnswer(questionIndex: Int, optionIndex: Int) {
        _selectedAnswers.value = _selectedAnswers.value.toMutableMap().apply {
            this[questionIndex] = optionIndex
        }
    }

    fun nextQuestion() {
        if (_currentIndex.value < _questions.value.size - 1) {
            _currentIndex.value += 1
        }
    }

    fun previousQuestion() {
        if (_currentIndex.value > 0) {
            _currentIndex.value -= 1
        }
    }

    fun calculateScore() {
        val total = _questions.value.size
        var correct = 0
        _questions.value.forEachIndexed { index, question ->
            val selected = _selectedAnswers.value[index]
            val correctIndex = "ABCD".indexOf(question.correctAnswer)
            if (selected == correctIndex) {
                correct++
            }
        }
        _score.value = if (total > 0) (correct * 100) / total else 0
        viewModelScope.launch {
            repository.insertSession(
                PracticeSession(
                    score = _score.value,
                    correctCount = correct,
                    totalQuestions = total,
                    categories = _selectedCategories.value.joinToString(",")  // Save categories
                )
            )
        }
    }

    fun resetForNewSession() {
        _questions.value = emptyList()
        _currentIndex.value = 0
        _selectedAnswers.value = emptyMap()
        _score.value = 0
        _selectedCategories.value = emptyList()
    }
}