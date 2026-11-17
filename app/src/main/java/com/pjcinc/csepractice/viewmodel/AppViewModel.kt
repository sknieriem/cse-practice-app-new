package com.pjcinc.csepractice.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pjcinc.csepractice.data.PracticeSession
import com.pjcinc.csepractice.data.Question
import com.pjcinc.csepractice.data.UserState
import com.pjcinc.csepractice.domain.Planner
import com.pjcinc.csepractice.domain.QuizMode
import com.pjcinc.csepractice.domain.QuizState
import com.pjcinc.csepractice.domain.SessionResult
import com.pjcinc.csepractice.domain.Topic
import com.pjcinc.csepractice.domain.TopicProgress
import com.pjcinc.csepractice.repository.QuestionRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Tab(val label: String) { HOME("Home"), PRACTICE("Practice"), PROGRESS("Progress"), SETTINGS("Settings") }

/** Full-screen flows that sit above the tabs. Only one at a time. */
sealed class Overlay {
    data object None : Overlay()
    data object Onboarding : Overlay()
    data object Quiz : Overlay()
    data class Result(val result: SessionResult) : Overlay()
    data class Review(val result: SessionResult, val onlyWrong: Boolean) : Overlay()
}

sealed class Boot {
    data object Loading : Boot()
    data object Ready : Boot()
    data class Failed(val message: String) : Boot()
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    val repo = QuestionRepository(app)
    private val dao = repo.dao

    private val _boot = MutableStateFlow<Boot>(Boot.Loading)
    val boot: StateFlow<Boot> = _boot.asStateFlow()

    val user: StateFlow<UserState?> = repo.prefs.user
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _tab = MutableStateFlow(Tab.HOME)
    val tab: StateFlow<Tab> = _tab.asStateFlow()

    private val _overlay = MutableStateFlow<Overlay>(Overlay.None)
    val overlay: StateFlow<Overlay> = _overlay.asStateFlow()

    /** Practice tab sub-page: a subject's topic list, or null for the hub. */
    private val _practiceCategory = MutableStateFlow<String?>(null)
    val practiceCategory: StateFlow<String?> = _practiceCategory.asStateFlow()

    private val _quiz = MutableStateFlow<QuizState?>(null)
    val quiz: StateFlow<QuizState?> = _quiz.asStateFlow()

    private val _quizQuestions = MutableStateFlow<List<Question>>(emptyList())
    val quizQuestions: StateFlow<List<Question>> = _quizQuestions.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val topics: List<Topic> get() = repo.topics()

    val topicProgress: StateFlow<List<TopicProgress>> =
        combine(dao.topicSizes(), dao.topicStats()) { sizes, stats ->
            runCatching { Planner.topicProgress(repo.topics(), sizes, stats) }.getOrDefault(emptyList())
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val todayFlow = MutableStateFlow(repo.today())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val answeredToday: StateFlow<Int> = todayFlow.flatMapLatest { dao.answeredOnDay(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val dueReviews: StateFlow<Int> = todayFlow.flatMapLatest { dao.dueCount(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val answeredTotal: StateFlow<Int> = dao.answeredCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val sessions: StateFlow<List<PracticeSession>> = dao.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** A saved, unfinished session — shown as "Continue" on Home. */
    val savedQuiz: StateFlow<QuizState?> = repo.prefs.activeQuiz
        .map { repo.loadActive(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var timerJob: Job? = null

    init {
        boot()
    }

    fun boot() {
        _boot.value = Boot.Loading
        viewModelScope.launch {
            try {
                repo.seedIfNeeded()
                repo.topics()
                val u = repo.prefs.current()
                if (!u.onboarded) _overlay.value = Overlay.Onboarding
                _boot.value = Boot.Ready
            } catch (e: Exception) {
                Log.e(TAG, "Boot failed", e)
                _boot.value = Boot.Failed("Could not load the question bank (${e.javaClass.simpleName}). Tap Retry.")
            }
        }
    }

    fun refreshDay() {
        todayFlow.value = repo.today()
    }

    // ---- navigation ---------------------------------------------------------------------

    fun selectTab(t: Tab) {
        _tab.value = t
        if (t != Tab.PRACTICE) _practiceCategory.value = null
    }

    fun openCategory(category: String?) {
        _tab.value = Tab.PRACTICE
        _practiceCategory.value = category
    }

    /** Handles system Back. Returns false when the app should close. */
    fun onBack(): Boolean {
        when (val o = _overlay.value) {
            Overlay.Quiz -> return true // the quiz screen shows its own exit dialog
            is Overlay.Review -> { _overlay.value = Overlay.Result(o.result); return true }
            is Overlay.Result -> { closeResult(); return true }
            Overlay.Onboarding -> return false
            Overlay.None -> Unit
        }
        if (_practiceCategory.value != null) { _practiceCategory.value = null; return true }
        if (_tab.value != Tab.HOME) { _tab.value = Tab.HOME; return true }
        return false
    }

    // ---- onboarding / settings ----------------------------------------------------------

    fun completeOnboarding(examDay: Long, level: String, goal: Int) = viewModelScope.launch {
        repo.prefs.setOnboarded(examDay, level, goal)
        _overlay.value = Overlay.None
    }

    fun setTheme(mode: String) = viewModelScope.launch { repo.prefs.setTheme(mode) }
    fun setInstant(on: Boolean) = viewModelScope.launch { repo.prefs.setInstantFeedback(on) }
    fun setLanguage(lang: String) = viewModelScope.launch { repo.prefs.setLanguage(lang) }
    fun setExamDay(day: Long) = viewModelScope.launch { repo.prefs.setExamDay(day) }
    fun setExamLevel(level: String) = viewModelScope.launch { repo.prefs.setExamLevel(level) }
    fun setDailyGoal(goal: Int) = viewModelScope.launch { repo.prefs.setDailyGoal(goal) }

    fun resetProgress() = viewModelScope.launch {
        repo.resetProgress()
        _messages.tryEmit("Progress reset.")
    }

    suspend fun unseenSyllabusCount(): Int {
        val seen = dao.seenQuestionIds().toSet()
        val syl = repo.syllabusTopicIds()
        return dao.getIndex().count { it.subtopic in syl && it.id !in seen }
    }

    // ---- quiz lifecycle -----------------------------------------------------------------

    fun start(
        mode: QuizMode,
        count: Int = 15,
        topicId: String? = null,
        categories: List<String> = Planner.CATEGORIES,
        explicitIds: List<Long>? = null
    ) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                val state = repo.buildQuiz(mode, count, topicId, categories, explicitIds)
                if (state == null || state.size == 0) {
                    _messages.tryEmit(
                        if (mode == QuizMode.REVIEW) "Nothing to review today — great job!" else "No questions available for that choice."
                    )
                } else {
                    openQuiz(state)
                }
            } finally {
                _busy.value = false
            }
        }
    }

    fun resume() = viewModelScope.launch {
        val s = savedQuiz.value ?: return@launch
        openQuiz(s)
    }

    private suspend fun openQuiz(state: QuizState) {
        val qs = repo.questionsFor(state)
        if (qs.size != state.size) {
            // content changed under a saved session; drop it rather than mis-score
            repo.saveActive(null)
            _messages.tryEmit("That saved session is no longer available.")
            return
        }
        _quizQuestions.value = qs
        _quiz.value = state
        _overlay.value = Overlay.Quiz
        repo.saveActive(state)
        startTimer()
    }

    private fun update(save: Boolean = true, f: (QuizState) -> QuizState) {
        val s = _quiz.value ?: return
        val n = f(s)
        _quiz.value = n
        if (save) viewModelScope.launch { repo.saveActive(n) }
    }

    fun answer(displayPos: Int) = update { s ->
        val i = s.index
        if (s.revealed[i]) return@update s
        s.copy(
            answers = s.answers.set(i, displayPos),
            revealed = if (s.instantFeedback) s.revealed.set(i, true) else s.revealed
        )
    }

    fun useHint() = update { s -> s.copy(hinted = s.hinted.set(s.index, true)) }
    fun toggleMark() = update { s -> s.copy(marked = s.marked.set(s.index, !s.marked[s.index])) }
    fun goTo(i: Int) = update { s -> s.copy(index = i.coerceIn(0, s.size - 1)) }
    fun next() = update { s -> s.copy(index = (s.index + 1).coerceAtMost(s.size - 1)) }
    fun prev() = update { s -> s.copy(index = (s.index - 1).coerceAtLeast(0)) }

    /** Leave the quiz but keep it saved for "Continue". */
    fun pauseQuiz() {
        stopTimer()
        _quiz.value?.let { s -> viewModelScope.launch { repo.saveActive(s) } }
        _overlay.value = Overlay.None
        _quiz.value = null
    }

    fun discardQuiz() {
        stopTimer()
        viewModelScope.launch { repo.saveActive(null) }
        _overlay.value = Overlay.None
        _quiz.value = null
    }

    fun discardSaved() = viewModelScope.launch { repo.saveActive(null) }

    fun submit() {
        val s = _quiz.value ?: return
        if (_busy.value) return
        stopTimer()
        _busy.value = true
        viewModelScope.launch {
            try {
                val result = repo.finish(s)
                _quiz.value = null
                refreshDay()
                _overlay.value = Overlay.Result(result)
            } catch (e: Exception) {
                Log.e(TAG, "Scoring failed", e)
                _messages.tryEmit("Could not save results: ${e.javaClass.simpleName}")
                startTimer()
            } finally {
                _busy.value = false
            }
        }
    }

    fun openReview(result: SessionResult, onlyWrong: Boolean) {
        _overlay.value = Overlay.Review(result, onlyWrong)
    }

    fun retryMistakes(result: SessionResult) {
        val ids = result.items.filter { !it.isCorrect }.map { it.question.id }
        if (ids.isEmpty()) return
        _overlay.value = Overlay.None
        start(QuizMode.MISTAKES, explicitIds = ids)
    }

    fun closeResult() {
        _overlay.value = Overlay.None
        _tab.value = Tab.HOME
    }

    /** App went to the background: stop the clock and save. */
    fun onAppStop() {
        if (_overlay.value != Overlay.Quiz) return
        stopTimer()
        _quiz.value?.let { s -> viewModelScope.launch { repo.saveActive(s) } }
    }

    fun onAppStart() {
        refreshDay()
        if (_overlay.value == Overlay.Quiz && _quiz.value != null && timerJob == null) startTimer()
    }

    // ---- timer --------------------------------------------------------------------------

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var last = System.currentTimeMillis()
            var ticks = 0
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val dt = (now - last).coerceIn(0, 5_000) // ignore huge jumps (phone slept)
                last = now
                ticks++
                update(save = ticks % 5 == 0) { s ->
                    s.copy(elapsedMs = s.elapsedMs + dt, timeMs = s.timeMs.set(s.index, s.timeMs[s.index] + dt))
                }
                val s = _quiz.value ?: break
                if (s.timeLimitSec > 0 && s.remainingSec == 0) {
                    _messages.tryEmit("Time's up! Your exam was submitted.")
                    submit()
                    break
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    override fun onCleared() {
        stopTimer()
        super.onCleared()
    }

    companion object {
        private const val TAG = "AppViewModel"
    }
}

private fun <T> List<T>.set(i: Int, v: T): List<T> = toMutableList().also { it[i] = v }
