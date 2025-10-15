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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

sealed class UiState {
    object Initial : UiState()
    object Loading : UiState()
    data class Success(val questions: List<Question>) : UiState()
    data class Error(val message: String) : UiState()
    data class Review(val correctAnswers: List<Pair<Question, Boolean>>) : UiState()
}

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

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _selectedCategories = MutableStateFlow<List<String>>(emptyList())
    val selectedCategories: StateFlow<List<String>> = _selectedCategories.asStateFlow()

    private var startTime = 0L

    val sessions: Flow<List<PracticeSession>> = repository.getAllSessions()

    val categories: Flow<List<String>> = repository.getCategories()

    init {
        viewModelScope.launch {
            try {
                // Expect Flow<Int> and collect within coroutine
                val totalQuestionsFlow: Flow<Int> = repository.getTotalQuestionCount()
                val totalQuestions: Int? = withContext(viewModelScope.coroutineContext) {
                    totalQuestionsFlow.firstOrNull()
                }
                val total = totalQuestions ?: 0
                Log.d("VM", "Total questions in DB: $total")
                if (total == 0) {
                    repository.seedQuestionsIfEmpty()
                } else {
                    Log.d("VM", "DB has $total questions, checking categories")
                    val allCats = categories.firstOrNull() ?: emptyList()
                    allCats.forEach { cat ->
                        val countFlow: Flow<Int> = repository.getQuestionCountByCategory(cat)
                        val count: Int? = withContext(viewModelScope.coroutineContext) {
                            countFlow.firstOrNull()
                        }
                        val countValue = count ?: 0
                        Log.d("VM", "Category '$cat' has $countValue questions")
                        if (cat == "General Information" && countValue == 0) {
                            Log.w("VM", "No questions in General Information - reseeding")
                            repository.clearAllQuestions()
                            repository.seedQuestionsIfEmpty()
                        }
                    }
                }
                _uiState.value = UiState.Initial
            } catch (e: Exception) {
                Log.e("VM", "Init failed: ${e.message}")
                _uiState.value = UiState.Error("Failed to initialize data. Restart app.")
            }
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
        _uiState.value = UiState.Loading
        startTime = System.currentTimeMillis()
        val categories = _selectedCategories.value
        Log.d("VM", "Starting practice for categories: $categories, num: $numQuestions")
        if (categories.isEmpty()) {
            loadAllQuestions(numQuestions)
        } else {
            loadQuestionsForCategories(categories, numQuestions)
        }
    }

    fun practiceWeakAreas(numQuestions: Int = 10, threshold: Double = 70.0) {
        viewModelScope.launch {
            val allCats = categories.firstOrNull() ?: emptyList()
            if (allCats.isEmpty()) {
                _uiState.value = UiState.Error("No categories available")
                return@launch
            }
            val weak = mutableListOf<String>()
            for (cat in allCats) {
                val avgFlow: Flow<Double> = getAverageForCategory(cat)
                val avg: Double? = withContext(viewModelScope.coroutineContext) {
                    avgFlow.firstOrNull()
                }
                val average = avg ?: 0.0
                if (average < threshold) {
                    weak.add(cat)
                }
            }
            _selectedCategories.value = weak
            if (weak.isNotEmpty()) {
                startPractice(numQuestions)
            } else {
                _uiState.value = UiState.Error("No weak areas (all averages >= $threshold%)")
            }
        }
    }

    private fun loadQuestionsForCategories(categories: List<String>, count: Int) {
        viewModelScope.launch {
            try {
                val loadedQuestions = repository.getRandomQuestionsByCategories(categories, count)
                Log.d("VM", "Loaded ${loadedQuestions.size} questions for $categories")
                if (loadedQuestions.isEmpty()) {
                    Log.e("VM", "No questions loaded for categories: $categories")
                    _uiState.value = UiState.Error("No questions available in selected categories. Try another or reset data.")
                } else {
                    _questions.value = loadedQuestions
                    _uiState.value = UiState.Success(loadedQuestions)
                }
            } catch (e: Exception) {
                Log.e("VM", "Load failed: ${e.message}")
                _uiState.value = UiState.Error("Failed to load questions.")
            }
        }
    }

    private fun loadAllQuestions(count: Int) {
        viewModelScope.launch {
            val questionsFlow = repository.getRandomQuestions(count)
            val loadedQuestions = questionsFlow.firstOrNull()
            Log.d("VM", "Loaded ${loadedQuestions?.size ?: 0} questions overall")
            if (loadedQuestions == null || loadedQuestions.isEmpty()) {
                Log.e("VM", "No questions loaded overall")
                _uiState.value = UiState.Error("No questions available. Check app data or reset.")
            } else {
                _questions.value = loadedQuestions
                _uiState.value = UiState.Success(loadedQuestions)
            }
        }
    }

    fun getAverageForCategory(category: String): Flow<Double> = repository.getAverageForCategory(category)

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
        val correctAnswers = mutableListOf<Pair<Question, Boolean>>()
        var correct = 0
        _questions.value.forEachIndexed { index, question ->
            val selected = _selectedAnswers.value[index]
            val correctIndex = "ABCD".indexOf(question.correctAnswer)
            val isCorrect = selected == correctIndex
            if (isCorrect) correct++
            correctAnswers.add(question to isCorrect)
        }
        _score.value = if (total > 0) (correct * 100) / total else 0
        val timeTaken = System.currentTimeMillis() - startTime
        viewModelScope.launch {
            repository.insertSession(
                PracticeSession(
                    score = _score.value,
                    correctCount = correct,
                    totalQuestions = total,
                    categories = _selectedCategories.value.joinToString(","),
                    timeTaken = timeTaken
                )
            )
            _uiState.value = UiState.Review(correctAnswers)
        }
    }

    fun resetForNewSession() {
        _questions.value = emptyList()
        _currentIndex.value = 0
        _selectedAnswers.value = emptyMap()
        _score.value = 0
        _selectedCategories.value = emptyList()
        _uiState.value = UiState.Initial
    }
}