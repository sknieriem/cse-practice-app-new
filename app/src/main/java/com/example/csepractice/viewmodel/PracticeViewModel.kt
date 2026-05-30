package com.example.csepractice.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.csepractice.R
import com.example.csepractice.data.PracticeSession
import com.example.csepractice.data.Question
import com.example.csepractice.repository.QuestionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI state for the practice screen. Mutually exclusive: only one branch is ever rendered.
 *
 * Note: [Finished] replaces the previous "if (score > 0) { … }" sibling-block hack so
 * the results view can't render alongside [InProgress].
 */
sealed class UiState {
    data object Initial : UiState()
    data object Loading : UiState()
    data class InProgress(val questions: List<Question>) : UiState()
    data class Finished(val score: Int, val correct: Int, val total: Int) : UiState()
    data class Review(val items: List<QuestionResult>) : UiState()
    data class Error(val message: String) : UiState()
}

data class QuestionResult(val question: Question, val isCorrect: Boolean)

class PracticeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = QuestionRepository(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _questions = MutableStateFlow<List<Question>>(emptyList())
    val questions: StateFlow<List<Question>> = _questions.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    private val _selectedAnswers = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val selectedAnswers: StateFlow<Map<Int, Int>> = _selectedAnswers.asStateFlow()

    private val _selectedCategories = MutableStateFlow<List<String>>(emptyList())
    val selectedCategories: StateFlow<List<String>> = _selectedCategories.asStateFlow()

    private var startTime = 0L

    val sessions: Flow<List<PracticeSession>> = repository.getAllSessions()
    val categories: Flow<List<String>> = repository.getCategories()

    init {
        viewModelScope.launch {
            try {
                repository.seedIfNeeded()
                _uiState.value = UiState.Initial
            } catch (e: Exception) {
                Log.e(TAG, "Init failed", e)
                _uiState.value = UiState.Error(message = getApplication<Application>().getString(R.string.err_init_failed))
            }
        }
    }

    fun toggleCategory(category: String) {
        _selectedCategories.update { current ->
            if (current.contains(category)) current - category else current + category
        }
    }

    /** Starts a new practice session with [numQuestions] drawn from [selectedCategories]. */
    fun startPractice(numQuestions: Int) {
        val cats = _selectedCategories.value
        if (cats.isEmpty()) {
            // Caller should prevent this via the UI; guard here as a defensive measure.
            _uiState.value = UiState.Error(
                fallback = getApplication<Application>().getString(R.string.select_at_least_one_category)
            )
            return
        }
        _uiState.value = UiState.Loading
        startTime = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val loaded = repository.getRandomQuestionsByCategories(cats, numQuestions)
                if (loaded.isEmpty()) {
                    _uiState.value = UiState.Error(
                        fallback = getApplication<Application>().getString(R.string.err_no_questions_for_categories)
                    )
                } else {
                    _questions.value = loaded
                    _currentIndex.value = 0
                    _selectedAnswers.value = emptyMap()
                    _uiState.value = UiState.InProgress(loaded)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load failed", e)
                _uiState.value = UiState.Error(
                    fallback = getApplication<Application>().getString(R.string.err_load_failed)
                )
            }
        }
    }

    fun practiceWeakAreas(numQuestions: Int = 10, threshold: Double = 70.0) {
        viewModelScope.launch {
            val allCats = categories.firstOrNull().orEmpty()
            val weak = allCats.filter { cat ->
                val avg = getAverageForCategory(cat).firstOrNull() ?: 0.0
                avg < threshold
            }
            if (weak.isEmpty()) {
                _uiState.value = UiState.Error(
                    fallback = getApplication<Application>().getString(
                        R.string.err_no_weak_areas, threshold.toInt()
                    )
                )
            } else {
                _selectedCategories.value = weak
                startPractice(numQuestions)
            }
        }
    }

    fun getAverageForCategory(category: String): Flow<Double?> =
        repository.getAverageForCategory(category)

    fun selectAnswer(questionIndex: Int, optionIndex: Int) {
        _selectedAnswers.update { it + (questionIndex to optionIndex) }
    }

    fun nextQuestion() {
        if (_currentIndex.value < _questions.value.size - 1) _currentIndex.value += 1
    }

    fun previousQuestion() {
        if (_currentIndex.value > 0) _currentIndex.value -= 1
    }

    /** Tallies correct answers, persists a [PracticeSession], and transitions to Finished. */
    fun calculateScore() {
        val total = _questions.value.size
        if (total == 0) return

        val results = _questions.value.mapIndexed { index, q ->
            val selected = _selectedAnswers.value[index]
            val correctIndex = CORRECT_KEY.indexOf(q.correctAnswer)
            QuestionResult(question = q, isCorrect = selected == correctIndex)
        }
        val correct = results.count { it.isCorrect }
        val scorePct = (correct * 100) / total
        val timeTaken = System.currentTimeMillis() - startTime

        viewModelScope.launch {
            repository.insertSession(
                PracticeSession(
                    score = scorePct,
                    correctCount = correct,
                    totalQuestions = total,
                    categories = _selectedCategories.value.joinToString(","),
                    timeTaken = timeTaken
                )
            )
            // Stash the review list so the user can transition Finished -> Review.
            pendingReview = results
            _uiState.value = UiState.Finished(scorePct, correct, total)
        }
    }

    private var pendingReview: List<QuestionResult> = emptyList()

    /** Move from the finished screen into the per-question review. */
    fun showReview() {
        if (pendingReview.isNotEmpty()) _uiState.value = UiState.Review(pendingReview)
    }

    /** Return to the category selector. */
    fun resetForNewSession() {
        _questions.value = emptyList()
        _currentIndex.value = 0
        _selectedAnswers.value = emptyMap()
        _selectedCategories.value = emptyList()
        pendingReview = emptyList()
        _uiState.value = UiState.Initial
    }

    companion object {
        private const val TAG = "PracticeViewModel"
        private const val CORRECT_KEY = "ABCD"
    }
}
