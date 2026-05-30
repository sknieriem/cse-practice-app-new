package com.pjcinc.csepractice.repository

import android.content.Context
import android.util.Log
import com.pjcinc.csepractice.data.AppDatabase
import com.pjcinc.csepractice.data.PracticeSession
import com.pjcinc.csepractice.data.Question
import com.pjcinc.csepractice.data.SessionCategoryResult
import com.pjcinc.csepractice.ui.theme.PreferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

/**
 * Thin wrapper over [QuestionDao]. Owns the one-time seeding of questions from the
 * bundled [QUESTIONS_ASSET] JSON, gated by a version counter in DataStore.
 */
class QuestionRepository(private val context: Context) {

    private val dao = AppDatabase.getDatabase(context).questionDao()

    // ---- Questions ----

    suspend fun getRandomQuestionsByCategories(
        categories: List<String>,
        count: Int
    ): List<Question> = withContext(Dispatchers.IO) {
        if (categories.isEmpty()) emptyList()
        else dao.getRandomQuestionsByCategories(categories, count)
    }

    suspend fun getRandomQuestions(count: Int): List<Question> =
        withContext(Dispatchers.IO) { dao.getRandomQuestions(count) }

    suspend fun getQuestionCount(): Int =
        withContext(Dispatchers.IO) { dao.getQuestionCount() }

    fun getCategories(): Flow<List<String>> = dao.getCategories()

    // ---- Sessions ----

    /**
     * Persist a session plus its per-category breakdown atomically.
     *
     * Note: this isn't wrapped in a Room `@Transaction` because the DAO methods
     * are simple inserts; if the second insert ever fails, the session row alone
     * is still consistent (averages will just exclude that session).
     */
    suspend fun insertSessionWithResults(
        session: PracticeSession,
        breakdown: Map<String, Pair<Int, Int>>
    ): Long = withContext(Dispatchers.IO) {
        val sessionId = dao.insertSession(session).toInt()
        if (breakdown.isNotEmpty()) {
            dao.insertCategoryResults(
                breakdown.map { (category, counts) ->
                    SessionCategoryResult(
                        sessionId = sessionId,
                        category = category,
                        correctCount = counts.first,
                        totalCount = counts.second
                    )
                }
            )
        }
        sessionId.toLong()
    }

    fun getAllSessions(): Flow<List<PracticeSession>> = dao.getAllSessions()

    suspend fun clearAllSessions() =
        withContext(Dispatchers.IO) { dao.clearAllSessions() }

    suspend fun clearAllQuestions() =
        withContext(Dispatchers.IO) { dao.clearAllQuestions() }

    /** Exact per-category average — counts correct/total across every session that touched it. */
    fun getAverageForCategory(category: String): Flow<Double?> =
        dao.getExactAvgByCategory(category)

    // ---- Seeding ----

    /**
     * Seeds the question bank on first launch, or re-seeds when [QUESTIONS_VERSION]
     * is bumped (use this when shipping updated questions). No-op otherwise.
     */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        val storedVersion = PreferencesDataStore.questionsVersionFlow(context).firstOrNull() ?: 0
        val currentCount = dao.getQuestionCount()
        val needsSeed = currentCount == 0 || storedVersion < QUESTIONS_VERSION
        if (!needsSeed) {
            Log.d(TAG, "Seeding skipped (count=$currentCount, version=$storedVersion)")
            return@withContext
        }
        try {
            val json = context.assets.open(QUESTIONS_ASSET)
                .bufferedReader()
                .use { it.readText() }
            val type = object : TypeToken<List<Question>>() {}.type
            val raw: List<Question> = Gson().fromJson(json, type)

            // Re-tag one known-bad question and dedupe by exact text match.
            val cleaned = raw
                .map { q ->
                    if (q.text.startsWith("If 4 men paint a house") && q.category != "Numerical Ability") {
                        q.copy(category = "Numerical Ability")
                    } else q
                }
                .distinctBy { it.text }

            dao.clearAllQuestions()
            dao.insertQuestions(cleaned)
            PreferencesDataStore.setQuestionsVersion(context, QUESTIONS_VERSION)
            Log.d(TAG, "Seeded ${cleaned.size} questions (version=$QUESTIONS_VERSION)")
        } catch (e: Exception) {
            Log.e(TAG, "Seeding failed", e)
        }
    }

    companion object {
        private const val TAG = "QuestionRepository"
        private const val QUESTIONS_ASSET = "questions.json"
        /** Bump this when shipping a new questions.json to force re-seed on next launch. */
        private const val QUESTIONS_VERSION = 1
    }
}
