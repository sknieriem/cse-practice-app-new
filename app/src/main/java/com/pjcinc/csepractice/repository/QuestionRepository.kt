package com.pjcinc.csepractice.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pjcinc.csepractice.data.AppDatabase
import com.pjcinc.csepractice.data.Attempt
import com.pjcinc.csepractice.data.PracticeSession
import com.pjcinc.csepractice.data.Prefs
import com.pjcinc.csepractice.data.Question
import com.pjcinc.csepractice.data.ReviewCard
import com.pjcinc.csepractice.data.SessionCategoryResult
import com.pjcinc.csepractice.domain.CategoryScore
import com.pjcinc.csepractice.domain.Game
import com.pjcinc.csepractice.domain.Planner
import com.pjcinc.csepractice.domain.QuizMode
import com.pjcinc.csepractice.domain.QuizState
import com.pjcinc.csepractice.domain.ReviewItem
import com.pjcinc.csepractice.domain.SessionResult
import com.pjcinc.csepractice.domain.Topic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/** Owns content seeding, session building, and scoring. Everything works offline. */
class QuestionRepository(private val context: Context) {

    val dao = AppDatabase.getDatabase(context).questionDao()
    val prefs = Prefs(context)
    private val gson = Gson()

    @Volatile
    private var topicsCache: List<Topic>? = null

    fun today(): Long = LocalDate.now().toEpochDay()

    // ---- content ------------------------------------------------------------------------

    fun topics(): List<Topic> = topicsCache ?: run {
        val json = context.assets.open(TOPICS_ASSET).bufferedReader().use { it.readText() }
        val list: List<Topic> = gson.fromJson(json, object : TypeToken<List<Topic>>() {}.type)
        list.also { topicsCache = it }
    }

    fun syllabusTopicIds(): Set<String> = topics().filter { it.syllabus }.map { it.id }.toSet()

    /**
     * Loads questions.json into Room on first launch or when [QUESTIONS_VERSION] changes.
     * Throws on failure so the UI can show an error instead of spinning forever.
     */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val stored = prefs.questionsVersion()
        if (stored == QUESTIONS_VERSION && dao.getQuestionCount() > 0) return@withContext
        val json = context.assets.open(QUESTIONS_ASSET).bufferedReader().use { it.readText() }
        val raw: List<Question> = gson.fromJson(json, object : TypeToken<List<Question>>() {}.type)
        // Gson bypasses Kotlin null-safety; repair anything required that came back null.
        val cleaned = raw.mapNotNull { q ->
            @Suppress("SENSELESS_COMPARISON")
            if (q.text == null || q.optionA == null || q.optionB == null || q.optionC == null ||
                q.optionD == null || q.correctAnswer == null || q.category == null
            ) null
            else q.copy(
                subtopic = q.subtopic ?: "other",
                explanation = q.explanation ?: ""
            )
        }
        require(cleaned.isNotEmpty()) { "questions.json is empty" }
        dao.replaceQuestions(cleaned)
        prefs.setQuestionsVersion(QUESTIONS_VERSION)
        Log.d(TAG, "Seeded ${cleaned.size} questions (v$QUESTIONS_VERSION)")
    }

    // ---- building sessions --------------------------------------------------------------

    suspend fun buildQuiz(
        mode: QuizMode,
        count: Int,
        topicId: String? = null,
        categories: List<String> = Planner.CATEGORIES,
        explicitIds: List<Long>? = null
    ): QuizState? = withContext(Dispatchers.IO) {
        val user = prefs.current()
        val index = dao.getIndex()
        val seen = dao.seenQuestionIds().toSet()
        val syllabus = syllabusTopicIds()
        val today = today()
        var reviewIds = emptyList<Long>()
        val ids: List<Long> = when (mode) {
            QuizMode.SMART -> {
                val stats = dao.topicStatsNow()
                val progress = Planner.topicProgress(topics(), index.groupBy { it.subtopic }
                    .map { com.pjcinc.csepractice.data.TopicStat(it.key, it.value.size, 0) }, stats)
                val weak = Planner.weakest(progress).filter { it.answered > 0 }.map { it.topic.id }
                val (picked, reviews) = Planner.smart(index, syllabus, seen, dao.dueCardIds(today), weak, count)
                reviewIds = reviews
                picked
            }
            QuizMode.TOPIC -> Planner.topic(index, topicId ?: return@withContext null, seen, count)
            QuizMode.CUSTOM -> Planner.custom(index, categories, seen, count)
            QuizMode.DAILY -> Planner.daily(index, syllabus, today)
            QuizMode.EXAM -> Planner.exam(index, syllabus, seen, count)
            QuizMode.REVIEW -> dao.dueCardIds(today).take(count).also { reviewIds = it }
            QuizMode.MISTAKES -> explicitIds.orEmpty()
        }
        if (ids.isEmpty()) return@withContext null
        val byId = dao.getByIds(ids).associateBy { it.id }
        val questions = ids.mapNotNull { byId[it] }
        val title = when (mode) {
            QuizMode.TOPIC -> topics().firstOrNull { it.id == topicId }?.name ?: mode.label
            QuizMode.CUSTOM -> if (categories.size == 1) categories.first() else "Mixed practice"
            else -> mode.label
        }
        QuizState.create(
            mode = mode,
            title = title,
            questions = questions,
            instant = user.instantFeedback,
            timeLimitSec = if (mode == QuizMode.EXAM) questions.size * EXAM_SECONDS_PER_ITEM else 0,
            reviewIds = reviewIds
        )
    }

    suspend fun questionsFor(state: QuizState): List<Question> = withContext(Dispatchers.IO) {
        val byId = dao.getByIds(state.questionIds).associateBy { it.id }
        state.questionIds.mapNotNull { byId[it] }
    }

    // ---- resume -------------------------------------------------------------------------

    suspend fun saveActive(state: QuizState?) = prefs.setActiveQuiz(state?.let { gson.toJson(it) })

    suspend fun loadActive(json: String?): QuizState? = try {
        json?.let { gson.fromJson(it, QuizState::class.java) }?.takeIf { it.size > 0 && it.answers.size == it.size }
    } catch (e: Exception) {
        Log.w(TAG, "Dropping unreadable saved session", e)
        null
    }

    // ---- scoring ------------------------------------------------------------------------

    suspend fun finish(state: QuizState): SessionResult = withContext(Dispatchers.IO) {
        val questions = questionsFor(state)
        val today = today()
        val items = questions.mapIndexed { i, q -> ReviewItem(q, state.canonicalChosen(i), state.marked[i]) }

        dao.insertAttempts(items.mapIndexed { i, it ->
            Attempt(
                questionId = it.question.id,
                category = it.question.category,
                subtopic = it.question.subtopic,
                chosen = it.chosen,
                correct = it.isCorrect,
                usedHint = state.hinted[i],
                timeMs = state.timeMs[i],
                mode = state.mode.name,
                day = today
            )
        })

        // Spaced repetition (Leitner): wrong -> box 1 tomorrow; right -> next box, longer gap.
        var reviewsFixed = 0
        for (it in items) {
            if (it.chosen < 0) continue
            val card = dao.getCard(it.question.id)
            if (!it.isCorrect) {
                dao.upsertCard(ReviewCard(it.question.id, 1, today + 1))
            } else if (card != null && card.box < 5) {
                val box = card.box + 1
                dao.upsertCard(ReviewCard(it.question.id, box, if (box >= 5) Long.MAX_VALUE / 2 else Planner.nextReviewDay(today, box)))
                if (it.question.id in state.reviewIds) reviewsFixed++
            }
        }

        val correct = items.count { it.isCorrect }
        val total = items.size
        val perCat = items.groupBy { it.question.category }
            .map { (c, xs) -> CategoryScore(c, xs.count { it.isCorrect }, xs.size) }
            .sortedBy { Planner.CATEGORIES.indexOf(it.category) }

        val allCats = dao.categoryStatsNow().associate { it.category to (it.n to it.correct) }
        val old = prefs.current()
        val reward = Game.reward(
            old, Game.SessionFacts(
                mode = state.mode,
                total = total,
                correct = correct,
                correctWithHint = items.indices.count { items[it].isCorrect && state.hinted[it] },
                wrong = items.count { it.chosen >= 0 && !it.isCorrect },
                reviewsFixed = reviewsFixed,
                today = today,
                hourOfDay = LocalTime.now().hour,
                totalAnsweredAllTime = dao.answeredCountNow(),
                categoryTotals = allCats
            )
        )
        prefs.saveGame(reward.newState)

        val sessionId = dao.insertSession(
            PracticeSession(
                score = if (total == 0) 0 else correct * 100 / total,
                correctCount = correct,
                totalQuestions = total,
                categories = perCat.joinToString(",") { it.category },
                timeTaken = state.elapsedMs,
                mode = state.mode.name,
                xpEarned = reward.xpGained
            )
        ).toInt()
        dao.insertCategoryResults(perCat.map { SessionCategoryResult(0, sessionId, it.category, it.correct, it.total) })
        prefs.setActiveQuiz(null)

        SessionResult(state.mode, state.title, total, correct, state.elapsedMs, perCat, reward, items)
    }

    suspend fun resetProgress() = withContext(Dispatchers.IO) {
        dao.clearAttempts()
        dao.clearCards()
        dao.clearAllSessions()
        prefs.resetProgress()
    }

    companion object {
        private const val TAG = "QuestionRepository"
        private const val QUESTIONS_ASSET = "questions.json"
        private const val TOPICS_ASSET = "topics.json"
        /** Bump when shipping a new questions.json so installed apps re-seed. */
        const val QUESTIONS_VERSION = 3
        /** Exam mode allows about one minute per item. */
        const val EXAM_SECONDS_PER_ITEM = 60
    }
}
