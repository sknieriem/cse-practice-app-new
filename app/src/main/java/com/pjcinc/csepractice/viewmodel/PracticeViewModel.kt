package com.pjcinc.csepractice.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pjcinc.csepractice.R
import com.pjcinc.csepractice.data.PracticeSession
import com.pjcinc.csepractice.data.Question
import com.pjcinc.csepractice.repository.QuestionRepository
import com.pjcinc.csepractice.ui.theme.PreferencesDataStore
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * UI state for the practice screen. Mutually exclusive: only one branch is ever rendered.
 *
 * [Finished] replaces the previous "if (score > 0) { ... }" sibling-block hack so
 * the results view can't render alongside [InProgress].
 */
sealed class UiState {
    data object Initial : UiState()
    data object Loading : UiState()
    data class InProgress(val questions: List<RuntimeQuestion>) : UiState()
    data class Finished(val score: Int, val correct: Int, val total: Int) : UiState()
    data class Review(val items: List<QuestionResult>) : UiState()
    data class Error(val message: String) : UiState()
}

/**
 * Runtime view of a [Question] for a single session: the option order has been
 * shuffled so that consecutive sessions don't rely on positional cues, and we
 * remember which display position maps to the source's correct answer.
 */
data class RuntimeQuestion(
    val source: Question,
    /** Option text in display order. Always length 4. */
    val displayOptions: List<String>,
    /** Index into [displayOptions] that holds the canonically correct answer. */
    val correctDisplayIndex: Int
)

/** Result of one answered question in this session. [wasMarked] is for the review UI. */
data class QuestionResult(
    val question: Question,
    val isCorrect: Boolean,
    val wasMarked: Boolean = false
)

class PracticeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = QuestionRepository(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _questions = MutableStateFlow<List<RuntimeQuestion>>(emptyList())
    val questions: StateFlow<List<RuntimeQuestion>> = _questions.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    /** Map of questionIndex -> selected display-option index. */
    private val _selectedAnswers = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val selectedAnswers: StateFlow<Map<Int, Int>> = _selectedAnswers.asStateFlow()

    private val _selectedCategories = MutableStateFlow<List<String>>(emptyList())
    val selectedCategories: StateFlow<List<String>> = _selectedCategories.asStateFlow()

    /** Set of question indices the user has marked for review. */
    private val _markedForReview = MutableStateFlow<Set<Int>>(emptySet())
    val markedForReview: StateFlow<Set<Int>> = _markedForReview.asStateFlow()

    /** Elapsed seconds since `startPractice`. Driven by a timer coroutine. */
    private val _elapsedSeconds = MutableStateFlow(0)
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    /** Whether the instant-feedback setting is on (read from DataStore). */
    val instantFeedback: Flow<Boolean> =
        PreferencesDataStore.instantFeedbackFlow(getApplication<Application>().applicationContext)

    private var startTime = 0L
    private var timerJob: Job? = null

    val sessions: Flow<List<PracticeSession>> = repository.getAllSessions()
    val categories: Flow<List<String>> = repository.getCategories()

    init {
        viewModelScope.launch {
            try {
                repository.seedIfNeeded()
                _uiState.value = UiState.Initial
            } catch (e: Exception) {
                Log.e(TAG, "Init failed", e)
                _uiState.value = UiState.Error(
                    message = string(R.string.err_init_failed)
                )
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
            _uiState.value = UiState.Error(
                message = string(R.string.select_at_least_one_category)
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
                        message = string(R.string.err_no_questions_for_categories)
                    )
                } else {
                    val runtime = loaded.map { it.toRuntimeShuffled() }
                    _questions.value = runtime
                    _currentIndex.value = 0
                    _selectedAnswers.value = emptyMap()
                    _markedForReview.value = emptySet()
                    _uiState.value = UiState.InProgress(runtime)
                    startTimer()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Load failed", e)
                _uiState.value = UiState.Error(
                    message = string(R.string.err_load_failed)
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
                    message = string(R.string.err_no_weak_areas, threshold.toInt())
                )
            } else {
                _selectedCategories.value = weak
                startPractice(numQuestions)
            }
        }
    }

    fun getAverageForCategory(category: String): Flow<Double?> =
        repository.getAverageForCategory(category)

    /** Records that the user selected [optionIndex] (an index into displayOptions) for [questionIndex]. */
    fun selectAnswer(questionIndex: Int, optionIndex: Int) {
        _selectedAnswers.update { it + (questionIndex to optionIndex) }
    }

    fun nextQuestion() {
        if (_currentIndex.value < _questions.value.size - 1) _currentIndex.value += 1
    }

    fun previousQuestion() {
        if (_currentIndex.value > 0) _currentIndex.value -= 1
    }

    /** Toggle whether the currently visible question is marked for later review. */
    fun toggleMarkedForReview(questionIndex: Int) {
        _markedForReview.update {
            if (it.contains(questionIndex)) it - questionIndex else it + questionIndex
        }
    }

    /** Returns true iff the answer the user already chose for [questionIndex] is correct.
     *  Only meaningful after the user has selected an option for that question. */
    fun isSelectionCorrect(questionIndex: Int): Boolean? {
        val q = _questions.value.getOrNull(questionIndex) ?: return null
        val chosen = _selectedAnswers.value[questionIndex] ?: return null
        return chosen == q.correctDisplayIndex
    }

    /** Tallies correct answers, persists a [PracticeSession] with per-category breakdown,
     *  stops the timer, and transitions to Finished. */
    fun calculateScore() {
        val total = _questions.value.size
        if (total == 0) return
        stopTimer()

        val results = _questions.value.mapIndexed { index, rq ->
            val selectedDisplayIndex = _selectedAnswers.value[index]
            val isCorrect = selectedDisplayIndex == rq.correctDisplayIndex
            QuestionResult(
                question = rq.source,
                isCorrect = isCorrect,
                wasMarked = _markedForReview.value.contains(index)
            )
        }
        val correct = results.count { it.isCorrect }
        val scorePct = (correct * 100) / total
        val timeTaken = System.currentTimeMillis() - startTime

        val breakdown: Map<String, Pair<Int, Int>> = results
            .groupBy { it.question.category }
            .mapValues { (_, qrs) -> qrs.count { it.isCorrect } to qrs.size }

        viewModelScope.launch {
            repository.insertSessionWithResults(
                session = PracticeSession(
                    score = scorePct,
                    correctCount = correct,
                    totalQuestions = total,
                    categories = _selectedCategories.value.joinToString(","),
                    timeTaken = timeTaken
                ),
                breakdown = breakdown
            )
            pendingReview = results
            _uiState.value = UiState.Finished(scorePct, correct, total)
        }
    }

    private var pendingReview: List<QuestionResult> = emptyList()

    /** Move from the finished screen into the per-question review. */
    fun showReview() {
        if (pendingReview.isNotEmpty()) _uiState.value = UiState.Review(pendingReview)
    }

    fun resetForNewSession() {
        stopTimer()
        _questions.value = emptyList()
        _currentIndex.value = 0
        _selectedAnswers.value = emptyMap()
        _selectedCategories.value = emptyList()
        _markedForReview.value = emptySet()
        _elapsedSeconds.value = 0
        pendingReview = emptyList()
        _uiState.value = UiState.Initial
    }

    private fun startTimer() {
        timerJob?.cancel()
        _elapsedSeconds.value = 0
        timerJob = viewModelScope.launch {
            while (isActive) {
                _elapsedSeconds.value = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                delay(1000)
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /** Convenience for resolving a string resource from inside the ViewModel. */
    private fun string(resId: Int, vararg args: Any): String =
        getApplication<Application>().getString(resId, *args)

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }

    companion object {
        private const val TAG = "PracticeViewModel"
        private const val CORRECT_KEY = "ABCD"

        /** Shuffle the four options of [this] question and remember the new correct index. */
        private fun Question.toRuntimeShuffled(): RuntimeQuestion {
            val originalOptions = listOf(optionA, optionB, optionC, optionD)
            val correctOriginalIndex = CORRECT_KEY.indexOf(correctAnswer).coerceAtLeast(0)
            // Build (originalIndex, text) pairs, shuffle, then read off the new correct index.
            val pairs = originalOptions.mapIndexed { idx, txt -> idx to txt }.shuffled()
            val displayOptions = pairs.map { it.second }
            val newCorrectIndex = pairs.indexOfFirst { it.first == correctOriginalIndex }
            return RuntimeQuestion(
                source = this,
                displayOptions = displayOptions,
                correctDisplayIndex = newCorrectIndex
            )
        }
    }
}
