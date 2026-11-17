package com.pjcinc.csepractice.domain

import com.pjcinc.csepractice.data.Question

enum class QuizMode(val label: String) {
    SMART("Smart practice"),
    TOPIC("Topic practice"),
    CUSTOM("Custom practice"),
    DAILY("Daily challenge"),
    EXAM("Exam mode"),
    REVIEW("Review missed"),
    MISTAKES("Retry mistakes");

    val isExam: Boolean get() = this == EXAM
}

/**
 * A session in progress. Saved to DataStore (as JSON) after every change so it survives
 * Back, app switching and the phone killing the app. Only plain lists — Gson-friendly.
 */
data class QuizState(
    val mode: QuizMode,
    val title: String,
    val questionIds: List<Long>,
    /** order[i][displayPos] = canonical option index (0..3) */
    val order: List<List<Int>>,
    /** answers[i] = display position chosen, or -1 */
    val answers: List<Int>,
    /** feedback already shown for question i (answer locked) */
    val revealed: List<Boolean>,
    val hinted: List<Boolean>,
    val marked: List<Boolean>,
    val timeMs: List<Long>,
    val index: Int = 0,
    val elapsedMs: Long = 0,
    /** 0 = no limit */
    val timeLimitSec: Int = 0,
    val instantFeedback: Boolean = true,
    /** questions that came from the spaced-repetition queue */
    val reviewIds: List<Long> = emptyList(),
    val startedAt: Long = System.currentTimeMillis()
) {
    val size: Int get() = questionIds.size
    val answeredCount: Int get() = answers.count { it >= 0 }
    val remainingSec: Int get() = if (timeLimitSec == 0) 0 else (timeLimitSec - (elapsedMs / 1000).toInt()).coerceAtLeast(0)

    fun canonicalChosen(i: Int): Int = answers[i].let { if (it < 0) -1 else order[i][it] }

    companion object {
        fun create(
            mode: QuizMode,
            title: String,
            questions: List<Question>,
            instant: Boolean,
            timeLimitSec: Int = 0,
            reviewIds: List<Long> = emptyList(),
            shuffleOptions: Boolean = true
        ): QuizState {
            val n = questions.size
            return QuizState(
                mode = mode,
                title = title,
                questionIds = questions.map { it.id },
                order = questions.map { q -> optionOrder(q, shuffleOptions) },
                answers = List(n) { -1 },
                revealed = List(n) { false },
                hinted = List(n) { false },
                marked = List(n) { false },
                timeMs = List(n) { 0L },
                timeLimitSec = timeLimitSec,
                instantFeedback = instant && !mode.isExam,
                reviewIds = reviewIds
            )
        }

        /** Shuffle, but keep choices like "All of the above" / "Cannot determine" last. */
        private fun optionOrder(q: Question, shuffle: Boolean): List<Int> {
            if (!shuffle) return listOf(0, 1, 2, 3)
            val opts = q.options()
            // "Both A and B" refers to letters: shuffling would make it point at the wrong options
            if (opts.any { Regex("\\b[A-D] (and|or) [A-D]\\b").containsMatchIn(it) }) return listOf(0, 1, 2, 3)
            val pinned = Regex("(?i)^(all of the above|none of the above|both|both a and b|none|cannot (be )?determined?|no implication\\.?)$")
            val fixed = (0..3).filter { pinned.matches(opts[it].trim()) }
            val free = (0..3).filter { it !in fixed }.shuffled()
            return free + fixed
        }
    }
}

data class ReviewItem(
    val question: Question,
    /** canonical index chosen, -1 = skipped */
    val chosen: Int,
    val marked: Boolean
) {
    val isCorrect: Boolean get() = chosen == question.correctIndex()
}

data class CategoryScore(val category: String, val correct: Int, val total: Int)

data class SessionResult(
    val mode: QuizMode,
    val title: String,
    val total: Int,
    val correct: Int,
    val timeMs: Long,
    val categories: List<CategoryScore>,
    val reward: Game.Reward,
    val items: List<ReviewItem>,
    /** specific, true sentences about this session */
    val praise: List<String> = emptyList(),
    /** one-time milestones reached in this session */
    val celebrations: List<Encourage.Celebration> = emptyList(),
    /** first exam-mode score at the passing rating: big celebration + share card */
    val pasado: Boolean = false,
    val readiness: Planner.Readiness? = null,
    val weeklyGain: Int? = null
) {
    val percent: Int get() = if (total == 0) 0 else correct * 100 / total
}
