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
import com.pjcinc.csepractice.data.ExamOutcome
import com.pjcinc.csepractice.data.ReadinessSnapshot
import com.pjcinc.csepractice.data.ReviewCard
import com.pjcinc.csepractice.data.TopicStat
import com.pjcinc.csepractice.data.SessionCategoryResult
import com.pjcinc.csepractice.domain.CategoryScore
import com.pjcinc.csepractice.domain.Encourage
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
import java.time.ZoneId
import java.util.UUID

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
                    .map { TopicStat(it.key, it.value.size, 0) }, stats)
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

    private suspend fun topicSizes(): List<TopicStat> =
        dao.getIndex().groupBy { it.subtopic }.map { TopicStat(it.key, it.value.size, 0) }

    suspend fun readinessNow(): Planner.Readiness = withContext(Dispatchers.IO) {
        Planner.estimate(Planner.topicProgress(topics(), topicSizes(), dao.topicStatsNow()))
    }

    suspend fun finish(state: QuizState): SessionResult = withContext(Dispatchers.IO) {
        val questions = questionsFor(state)
        val today = today()
        val items = questions.mapIndexed { i, q -> ReviewItem(q, state.canonicalChosen(i), state.marked[i]) }
        val sizes = topicSizes()
        val priorStats = dao.topicStatsNow()
        val priorProgress = Planner.topicProgress(topics(), sizes, priorStats)
        val readinessBefore = Planner.estimate(priorProgress)
        val priorBest = dao.bestScore()

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

        // ---- encouragement: computed only from what actually happened ----
        val lang = old.language
        val afterProgress = Planner.topicProgress(topics(), sizes, dao.topicStatsNow())
        val readiness = Planner.estimate(afterProgress)
        val startOfDay = LocalDate.ofEpochDay(today).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        dao.upsertSnapshot(
            ReadinessSnapshot(
                day = today,
                overall = readiness.overall,
                numerical = readiness.bySubject["Numerical Ability"] ?: -1,
                verbal = readiness.bySubject["Verbal Ability"] ?: -1,
                analytical = readiness.bySubject["Analytical Ability"] ?: -1,
                genInfo = readiness.bySubject["General Information"] ?: -1,
                answered = readiness.answered,
                minutesPractised = (dao.timeSince(startOfDay) / 60_000).toInt(),
                uuid = dao.snapshotOn(today)?.uuid ?: UUID.randomUUID().toString()
            )
        )
        val weekAgo = dao.snapshotOnOrBefore(today - 7)
        val weeklyGain = if (weekAgo != null && weekAgo.answered >= Planner.MIN_ANSWERS_FOR_READINESS && readiness.shown)
            readiness.overall - weekAgo.overall else null

        val topicNames = topics().associate { it.id to it.name }
        val priorById = priorStats.associateBy { it.subtopic }
        val deltas = items.groupBy { it.question.subtopic }.map { (t, xs) ->
            Encourage.TopicDelta(
                topicNames[t] ?: t, xs.count { it.isCorrect }, xs.count { it.chosen >= 0 },
                priorById[t]?.n ?: 0, priorById[t]?.correct ?: 0
            )
        }
        val praise = Encourage.praise(
            lang, deltas, reward.newState.streak, reward.streakExtended,
            reward.freezesUsed, weeklyGain, total, correct
        )

        val seen = old.milestones
        val celebrations = mutableListOf<Encourage.Celebration>()
        if (readiness.answered >= 50) for (th in listOf(70, 75, Planner.PASS_MARK)) {
            if (readinessBefore.overall < th && readiness.overall >= th && "ready_$th" !in seen) {
                celebrations += Encourage.readinessCelebration(lang, th)
            }
        }
        val priorTp = priorProgress.associateBy { it.topic.id }
        afterProgress.filter { it.topic.syllabus && it.answered >= 8 && it.status == "Strong" }.forEach { tp ->
            val before = priorTp[tp.topic.id]
            if (before != null && before.status != "Strong" && before.answered >= 5 && "strong_${tp.topic.id}" !in seen) {
                celebrations += Encourage.strongTopic(lang, tp.topic.id, tp.topic.name)
            }
        }
        val percent = if (total == 0) 0 else correct * 100 / total
        if (total >= 10 && priorBest != null && percent > priorBest) celebrations += Encourage.personalBest(lang, percent, priorBest)
        if (state.mode == QuizMode.EXAM && "first_mock" !in seen) celebrations += Encourage.firstMock(lang)
        if (reward.freezeEarned) celebrations += Encourage.freezeEarned(lang)
        val pasado = state.mode == QuizMode.EXAM && total >= 20 && percent >= Planner.PASS_MARK && "pasado" !in seen
        val oneTime = celebrations.map { it.id }.filter { it != "best" && it != "freeze" }.toSet() +
            (if (pasado) setOf("pasado") else emptySet())
        if (oneTime.isNotEmpty()) prefs.addMilestones(oneTime)

        SessionResult(
            state.mode, state.title, total, correct, state.elapsedMs, perCat, reward, items,
            praise = praise, celebrations = celebrations, pasado = pasado, readiness = readiness, weeklyGain = weeklyGain
        )
    }

    suspend fun recordExamOutcome(passed: Boolean, examDay: Long, level: String) = withContext(Dispatchers.IO) {
        val r = Planner.estimate(Planner.topicProgress(topics(), topicSizes(), dao.topicStatsNow()))
        dao.insertOutcome(
            ExamOutcome(
                examDay = examDay, level = level, outcome = if (passed) "PASSED" else "NOT_PASSED",
                readinessAtExam = if (r.shown) r.overall else -1
            )
        )
        prefs.markOutcomeAsked(examDay)
    }

    suspend fun resetProgress() = withContext(Dispatchers.IO) {
        dao.clearAttempts()
        dao.clearCards()
        dao.clearAllSessions()
        dao.clearSnapshots()
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
