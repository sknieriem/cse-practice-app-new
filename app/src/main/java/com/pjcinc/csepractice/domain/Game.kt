package com.pjcinc.csepractice.domain

import com.pjcinc.csepractice.data.UserState

/** XP, levels, streaks and badges. Pure functions — easy to test, no I/O. */
object Game {

    const val XP_CORRECT = 10
    const val XP_CORRECT_WITH_HINT = 6
    const val XP_WRONG = 2
    const val XP_FINISH = 20
    const val XP_DAILY = 50
    const val XP_EXAM_PASS = 100
    const val PASS_PERCENT = 80

    /** Total XP needed to reach [level] (level 1 = 0 XP). 100, 300, 600, 1000, 1500, ... */
    fun xpForLevel(level: Int): Int = 50 * level * (level - 1)

    fun levelFor(xp: Int): Int {
        var l = 1
        while (xpForLevel(l + 1) <= xp) l++
        return l
    }

    private val TITLES = listOf(
        "Aplikante", "Reviewer", "Masipag na Reviewer", "Trainee", "Clerk",
        "Officer I", "Officer II", "Senior Officer", "Supervisor", "Chief",
        "Director", "Undersecretary", "CSE Master"
    )

    fun titleFor(level: Int): String = TITLES[(level - 1).coerceIn(0, TITLES.lastIndex)]

    data class LevelProgress(val level: Int, val title: String, val intoLevel: Int, val levelSpan: Int) {
        val fraction: Float get() = if (levelSpan == 0) 1f else intoLevel.toFloat() / levelSpan
    }

    fun progress(xp: Int): LevelProgress {
        val l = levelFor(xp)
        val base = xpForLevel(l)
        val next = xpForLevel(l + 1)
        return LevelProgress(l, titleFor(l), xp - base, next - base)
    }

    // ---- Badges -------------------------------------------------------------------------
    data class Badge(val id: String, val emoji: String, val title: String, val description: String)

    val BADGES = listOf(
        Badge("first_step", "🌱", "Unang Hakbang", "Finish your first session"),
        Badge("streak_3", "🔥", "3-Day Streak", "Practice 3 days in a row"),
        Badge("streak_7", "🔥", "1-Week Streak", "Practice 7 days in a row"),
        Badge("streak_30", "🏆", "30-Day Streak", "Practice 30 days in a row"),
        Badge("answers_100", "💯", "100 Answers", "Answer 100 questions"),
        Badge("answers_500", "📚", "500 Answers", "Answer 500 questions"),
        Badge("answers_1000", "🎓", "1,000 Answers", "Answer 1,000 questions"),
        Badge("perfect_10", "⭐", "Perfect!", "Score 100% on 10 or more questions"),
        Badge("daily_1", "📅", "Daily Challenger", "Finish a daily challenge"),
        Badge("daily_7", "🗓️", "Challenge Week", "Finish 7 daily challenges"),
        Badge("exam_pass", "🎯", "Passer", "Score 80% or more in Exam mode"),
        Badge("review_10", "🔁", "Comeback", "Get 10 previously-missed questions right"),
        Badge("master_num", "🧮", "Numerical Master", "80%+ on 50+ Numerical answers"),
        Badge("master_verb", "📖", "Verbal Master", "80%+ on 50+ Verbal answers"),
        Badge("master_ana", "🧩", "Analytical Master", "80%+ on 50+ Analytical answers"),
        Badge("master_gi", "🏛️", "Gen Info Master", "80%+ on 50+ General Information answers"),
        Badge("night_owl", "🦉", "Night Owl", "Finish a session after 10 PM"),
        Badge("early_bird", "🐓", "Early Bird", "Finish a session before 7 AM")
    )

    fun badge(id: String) = BADGES.firstOrNull { it.id == id }

    // ---- Scoring a finished session -------------------------------------------------------
    data class SessionFacts(
        val mode: QuizMode,
        val total: Int,
        val correct: Int,
        val correctWithHint: Int,
        val wrong: Int,
        val reviewsFixed: Int,
        val today: Long,
        val hourOfDay: Int,
        val totalAnsweredAllTime: Int,
        /** category -> (n, correct) over all time, including this session */
        val categoryTotals: Map<String, Pair<Int, Int>>
    )

    data class Reward(
        val xpGained: Int,
        val breakdown: List<Pair<String, Int>>,
        val newState: UserState,
        val newBadges: List<Badge>,
        val leveledUp: Boolean,
        val streakExtended: Boolean
    )

    fun reward(old: UserState, f: SessionFacts): Reward {
        val lines = mutableListOf<Pair<String, Int>>()
        val plain = f.correct - f.correctWithHint
        if (plain > 0) lines += "$plain correct × $XP_CORRECT" to plain * XP_CORRECT
        if (f.correctWithHint > 0) lines += "${f.correctWithHint} correct with hint × $XP_CORRECT_WITH_HINT" to f.correctWithHint * XP_CORRECT_WITH_HINT
        if (f.wrong > 0) lines += "${f.wrong} tried × $XP_WRONG" to f.wrong * XP_WRONG
        if (f.total > 0) lines += "Session finished" to XP_FINISH

        // streak: counts once per calendar day
        var streak = old.streak
        var extended = false
        if (old.lastActiveDay != f.today) {
            streak = if (old.lastActiveDay == f.today - 1) old.streak + 1 else 1
            extended = true
            val bonus = 5 * streak.coerceAtMost(10)
            lines += "Streak day $streak bonus" to bonus
        }
        val percent = if (f.total == 0) 0 else f.correct * 100 / f.total
        var dailyDone = old.dailyDoneDay
        var dailyCount = old.dailyCount
        if (f.mode == QuizMode.DAILY && old.dailyDoneDay != f.today) {
            dailyDone = f.today
            dailyCount += 1
            lines += "Daily challenge" to XP_DAILY
        }
        var examsPassed = old.examsPassed
        if (f.mode == QuizMode.EXAM && percent >= PASS_PERCENT && f.total >= 20) {
            examsPassed += 1
            lines += "Exam mode pass" to XP_EXAM_PASS
        }
        val gained = lines.sumOf { it.second }
        val reviews = old.reviewsCleared + f.reviewsFixed

        val earned = mutableSetOf<String>()
        earned += "first_step"
        if (streak >= 3) earned += "streak_3"
        if (streak >= 7) earned += "streak_7"
        if (streak >= 30) earned += "streak_30"
        if (f.totalAnsweredAllTime >= 100) earned += "answers_100"
        if (f.totalAnsweredAllTime >= 500) earned += "answers_500"
        if (f.totalAnsweredAllTime >= 1000) earned += "answers_1000"
        if (f.total >= 10 && f.correct == f.total) earned += "perfect_10"
        if (dailyCount >= 1) earned += "daily_1"
        if (dailyCount >= 7) earned += "daily_7"
        if (examsPassed >= 1) earned += "exam_pass"
        if (reviews >= 10) earned += "review_10"
        fun mastered(cat: String): Boolean {
            val (n, c) = f.categoryTotals[cat] ?: return false
            return n >= 50 && c * 100 >= 80 * n
        }
        if (mastered("Numerical Ability")) earned += "master_num"
        if (mastered("Verbal Ability")) earned += "master_verb"
        if (mastered("Analytical Ability")) earned += "master_ana"
        if (mastered("General Information")) earned += "master_gi"
        if (f.hourOfDay >= 22) earned += "night_owl"
        if (f.hourOfDay in 4..6) earned += "early_bird"

        val newBadgeIds = earned - old.badges
        val newXp = old.xp + gained
        val newState = old.copy(
            xp = newXp,
            streak = streak,
            bestStreak = maxOf(old.bestStreak, streak),
            lastActiveDay = f.today,
            badges = old.badges + newBadgeIds,
            dailyDoneDay = dailyDone,
            dailyCount = dailyCount,
            examsPassed = examsPassed,
            reviewsCleared = reviews
        )
        return Reward(
            xpGained = gained,
            breakdown = lines,
            newState = newState,
            newBadges = BADGES.filter { it.id in newBadgeIds },
            leveledUp = levelFor(newXp) > levelFor(old.xp),
            streakExtended = extended
        )
    }

    /** Streak shown on Home: it is broken if the user missed yesterday. */
    fun liveStreak(s: UserState, today: Long): Int =
        if (s.lastActiveDay == today || s.lastActiveDay == today - 1) s.streak else 0
}
