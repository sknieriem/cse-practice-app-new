package com.pjcinc.csepractice.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Everything about the user that is not a row in Room: settings + game progress. */
data class UserState(
    val onboarded: Boolean = false,
    val themeMode: String = "system",
    val instantFeedback: Boolean = true,
    /** "en" or "tl" — default language for explanations */
    val language: String = "en",
    /** LocalDate.toEpochDay(), or -1 if not set */
    val examDay: Long = -1,
    val examLevel: String = "Professional",
    val dailyGoal: Int = 20,
    val xp: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveDay: Long = -1,
    val badges: Set<String> = emptySet(),
    val dailyDoneDay: Long = -1,
    val dailyCount: Int = 0,
    val examsPassed: Int = 0,
    val reviewsCleared: Int = 0,
    /** Streak freezes in the bank (earned 1 per 7-day streak, max 2). A missed day uses one. */
    val freezes: Int = 0,
    /** One-time celebrations already shown, e.g. "ready_70", "pasado", "strong_num_percent". */
    val milestones: Set<String> = emptySet(),
    /** Optional, local only: shown on share cards. */
    val firstName: String = "",
    /** "Who I'm doing this for" — shown on hard days. */
    val inspirationLine: String = "",
    val inspirationPhoto: String = "",
    /** Post-exam question snoozed until this epoch day ("still waiting for results"). */
    val outcomeSnoozeDay: Long = -1,
    /** Exam days already answered in the post-exam question. */
    val outcomeAskedFor: Set<String> = emptySet(),
    /** Exam-day checklist ticks, "day:item". */
    val checklist: Set<String> = emptySet()
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class Prefs(private val context: Context) {

    private object K {
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val THEME = stringPreferencesKey("theme_mode")
        val INSTANT = booleanPreferencesKey("instant_feedback_v2")
        val LANG = stringPreferencesKey("language")
        val EXAM_DAY = longPreferencesKey("exam_day")
        val EXAM_LEVEL = stringPreferencesKey("exam_level")
        val GOAL = intPreferencesKey("daily_goal")
        val XP = intPreferencesKey("xp")
        val STREAK = intPreferencesKey("streak")
        val BEST = intPreferencesKey("best_streak")
        val LAST_DAY = longPreferencesKey("last_active_day")
        val BADGES = stringSetPreferencesKey("badges")
        val DAILY_DONE = longPreferencesKey("daily_done_day")
        val DAILY_COUNT = intPreferencesKey("daily_count")
        val EXAMS_PASSED = intPreferencesKey("exams_passed")
        val REVIEWS = intPreferencesKey("reviews_cleared")
        val QVERSION = intPreferencesKey("questions_version_v3")
        val ACTIVE_QUIZ = stringPreferencesKey("active_quiz")
        val FREEZES = intPreferencesKey("freezes")
        val MILESTONES = stringSetPreferencesKey("milestones")
        val FIRST_NAME = stringPreferencesKey("first_name")
        val INSP_LINE = stringPreferencesKey("inspiration_line")
        val INSP_PHOTO = stringPreferencesKey("inspiration_photo")
        val SNOOZE = longPreferencesKey("outcome_snooze_day")
        val ASKED = stringSetPreferencesKey("outcome_asked_for")
        val CHECKLIST = stringSetPreferencesKey("exam_checklist")
    }

    val user: Flow<UserState> = context.dataStore.data.map { p ->
        UserState(
            onboarded = p[K.ONBOARDED] ?: false,
            themeMode = p[K.THEME] ?: "system",
            instantFeedback = p[K.INSTANT] ?: true,
            language = p[K.LANG] ?: "en",
            examDay = p[K.EXAM_DAY] ?: -1,
            examLevel = p[K.EXAM_LEVEL] ?: "Professional",
            dailyGoal = p[K.GOAL] ?: 20,
            xp = p[K.XP] ?: 0,
            streak = p[K.STREAK] ?: 0,
            bestStreak = p[K.BEST] ?: 0,
            lastActiveDay = p[K.LAST_DAY] ?: -1,
            badges = p[K.BADGES] ?: emptySet(),
            dailyDoneDay = p[K.DAILY_DONE] ?: -1,
            dailyCount = p[K.DAILY_COUNT] ?: 0,
            examsPassed = p[K.EXAMS_PASSED] ?: 0,
            reviewsCleared = p[K.REVIEWS] ?: 0,
            freezes = p[K.FREEZES] ?: 0,
            milestones = p[K.MILESTONES] ?: emptySet(),
            firstName = p[K.FIRST_NAME] ?: "",
            inspirationLine = p[K.INSP_LINE] ?: "",
            inspirationPhoto = p[K.INSP_PHOTO] ?: "",
            outcomeSnoozeDay = p[K.SNOOZE] ?: -1,
            outcomeAskedFor = p[K.ASKED] ?: emptySet(),
            checklist = p[K.CHECKLIST] ?: emptySet()
        )
    }

    suspend fun current(): UserState = user.first()

    val activeQuiz: Flow<String?> = context.dataStore.data.map { it[K.ACTIVE_QUIZ] }

    suspend fun setActiveQuiz(json: String?) = context.dataStore.edit {
        if (json == null) it.remove(K.ACTIVE_QUIZ) else it[K.ACTIVE_QUIZ] = json
    }

    suspend fun questionsVersion(): Int = context.dataStore.data.first()[K.QVERSION] ?: 0
    suspend fun setQuestionsVersion(v: Int) = context.dataStore.edit { it[K.QVERSION] = v }

    suspend fun setOnboarded(examDay: Long, level: String, goal: Int) = context.dataStore.edit {
        it[K.ONBOARDED] = true
        it[K.EXAM_DAY] = examDay
        it[K.EXAM_LEVEL] = level
        it[K.GOAL] = goal
    }

    suspend fun setTheme(mode: String) = context.dataStore.edit { it[K.THEME] = mode }
    suspend fun setInstantFeedback(on: Boolean) = context.dataStore.edit { it[K.INSTANT] = on }
    suspend fun setLanguage(lang: String) = context.dataStore.edit { it[K.LANG] = lang }
    suspend fun setExamDay(day: Long) = context.dataStore.edit { it[K.EXAM_DAY] = day }
    suspend fun setExamLevel(level: String) = context.dataStore.edit { it[K.EXAM_LEVEL] = level }
    suspend fun setDailyGoal(goal: Int) = context.dataStore.edit { it[K.GOAL] = goal }

    /** Writes the game fields of [s] back (used after a session is scored). */
    suspend fun saveGame(s: UserState) = context.dataStore.edit { p: MutablePreferences ->
        p[K.XP] = s.xp
        p[K.STREAK] = s.streak
        p[K.BEST] = s.bestStreak
        p[K.LAST_DAY] = s.lastActiveDay
        p[K.BADGES] = s.badges
        p[K.DAILY_DONE] = s.dailyDoneDay
        p[K.DAILY_COUNT] = s.dailyCount
        p[K.EXAMS_PASSED] = s.examsPassed
        p[K.REVIEWS] = s.reviewsCleared
        p[K.FREEZES] = s.freezes
        p[K.MILESTONES] = s.milestones
    }

    suspend fun addMilestones(ids: Set<String>) = context.dataStore.edit {
        it[K.MILESTONES] = (it[K.MILESTONES] ?: emptySet()) + ids
    }
    suspend fun setFirstName(v: String) = context.dataStore.edit { it[K.FIRST_NAME] = v.take(30) }
    suspend fun setInspiration(line: String, photo: String) = context.dataStore.edit {
        it[K.INSP_LINE] = line.take(120); it[K.INSP_PHOTO] = photo
    }
    suspend fun snoozeOutcome(untilDay: Long) = context.dataStore.edit { it[K.SNOOZE] = untilDay }
    suspend fun markOutcomeAsked(examDay: Long) = context.dataStore.edit {
        it[K.ASKED] = (it[K.ASKED] ?: emptySet()) + examDay.toString()
    }
    suspend fun toggleChecklist(key: String) = context.dataStore.edit {
        val cur = it[K.CHECKLIST] ?: emptySet()
        it[K.CHECKLIST] = if (key in cur) cur - key else cur + key
    }

    suspend fun resetProgress() = context.dataStore.edit {
        listOf(K.XP, K.STREAK, K.BEST, K.DAILY_COUNT, K.EXAMS_PASSED, K.REVIEWS, K.FREEZES).forEach { k -> it.remove(k) }
        listOf(K.LAST_DAY, K.DAILY_DONE).forEach { k -> it.remove(k) }
        it.remove(K.BADGES)
        it.remove(K.MILESTONES)
        it.remove(K.ACTIVE_QUIZ)
    }
}
