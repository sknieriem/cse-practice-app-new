package com.pjcinc.csepractice.domain

import com.pjcinc.csepractice.data.QuestionIndex
import com.pjcinc.csepractice.data.TopicStat
import kotlin.math.ceil
import kotlin.random.Random

data class Topic(
    val id: String,
    val category: String,
    val name: String,
    val syllabus: Boolean,
    val tip: String,
    val tipTl: String
)

/** Per-topic view used by Home / Practice / Progress. */
data class TopicProgress(
    val topic: Topic,
    val questions: Int,
    val answered: Int,
    val correct: Int
) {
    /** Smoothed accuracy (Laplace): starts at 50% and moves with evidence. */
    val skill: Double get() = (correct + 1.0) / (answered + 2.0)
    val accuracyPercent: Int get() = if (answered == 0) 0 else correct * 100 / answered
    val status: String
        get() = when {
            answered < 5 -> "New"
            skill >= 0.8 -> "Strong"
            skill >= 0.6 -> "Getting there"
            else -> "Needs work"
        }
}

/**
 * Offline personalization: picks what to practice next from the user's own answer history.
 * No network, no AI — deterministic rules that a reviewer can check.
 */
object Planner {

    val CATEGORIES = listOf("Numerical Ability", "Verbal Ability", "Analytical Ability", "General Information")
    private val LEARNED_INTERVALS = listOf(0, 1, 3, 7, 14) // box 1..4 -> days until next review

    fun nextReviewDay(today: Long, newBox: Int): Long =
        today + LEARNED_INTERVALS.getOrElse(newBox.coerceIn(1, 4)) { 14 }

    fun topicProgress(topics: List<Topic>, sizes: List<TopicStat>, stats: List<TopicStat>): List<TopicProgress> {
        val size = sizes.associate { it.subtopic to it.n }
        val st = stats.associateBy { it.subtopic }
        return topics.map { t ->
            TopicProgress(t, size[t.id] ?: 0, st[t.id]?.n ?: 0, st[t.id]?.correct ?: 0)
        }.filter { it.questions > 0 }
    }

    /**
     * Topics worth a "Focus" suggestion: tried at least [minAnswers] times AND below the pass mark,
     * weakest first. A topic the user is already passing is never called "weakest", even if it is
     * their lowest one; when nothing is below the pass mark this is empty and no Focus card shows.
     */
    fun focusTopics(progress: List<TopicProgress>, minAnswers: Int = 3): List<TopicProgress> =
        weakest(progress).filter { it.answered >= minAnswers && it.accuracyPercent < PASS_MARK }

    /** Weakest syllabus topics first. Topics never tried rank as "unknown" (skill 0.5). */
    fun weakest(progress: List<TopicProgress>): List<TopicProgress> =
        progress.filter { it.topic.syllabus }.sortedWith(compareBy({ it.skill }, { -it.questions }))

    const val PASS_MARK = 80
    /** Conservative stand-in score for a subject the user has not tried yet. */
    const val UNTRIED_SUBJECT = 50
    /** Readiness is only shown after this many answers — fewer is guesswork. */
    const val MIN_ANSWERS_FOR_READINESS = 20

    data class Readiness(
        /** estimated % correct on a balanced exam, 0..100 */
        val overall: Int,
        /** category -> estimate, or -1 when the subject has not been tried */
        val bySubject: Map<String, Int>,
        val answered: Int,
        val topicsPractised: Int,
        val topicsTotal: Int,
        val weakestSubject: String?
    ) {
        val shown: Boolean get() = answered >= MIN_ANSWERS_FOR_READINESS
        val allSubjectsTried: Boolean get() = bySubject.values.none { it < 0 }
        val gapToPass: Int get() = (PASS_MARK - overall).coerceAtLeast(0)
    }

    /**
     * Estimated exam score from practice answers (syllabus topics only).
     * Each topic's accuracy is shrunk toward the user's overall accuracy, so a topic with 1 answer
     * can't swing the number. Subjects weigh equally. A subject not tried yet counts at
     * [UNTRIED_SUBJECT] (or the user's own level if that is lower), so drilling one easy subject can
     * never show a passing estimate for the whole exam. The overall estimate stays below the pass mark
     * until all four subjects have been tried.
     * Honest by construction: it is an estimate of % correct, the same scale as the 80 pass mark.
     */
    fun estimate(progress: List<TopicProgress>): Readiness {
        val syl = progress.filter { it.topic.syllabus }
        val n = syl.sumOf { it.answered }
        val c = syl.sumOf { it.correct }
        val g = (c + 3.0) / (n + 5.0)
        val bySubject = CATEGORIES.associateWith { cat ->
            val ts = syl.filter { it.topic.category == cat }
            val w = ts.sumOf { it.questions }
            if (w == 0 || ts.sumOf { it.answered } == 0) -1
            else (100 * ts.sumOf { t -> (t.correct + 4 * g) / (t.answered + 4) * t.questions } / w).toInt()
        }
        val tried = bySubject.values.filter { it >= 0 }
        val untried = minOf(UNTRIED_SUBJECT.toDouble(), 100 * g)
        val raw = if (n == 0) 0 else
            ((tried.sum() + (CATEGORIES.size - tried.size) * untried) / CATEGORIES.size).toInt()
        val overall = if (tried.size < CATEGORIES.size) minOf(raw, PASS_MARK - 1) else raw
        val weakest = bySubject.filterValues { it >= 0 }.minByOrNull { it.value }?.key
        return Readiness(overall, bySubject, n, syl.count { it.answered >= 3 }, syl.size, weakest)
    }

    /** Per-subject number for cards: the estimate, or -1 = not started / not enough answers yet. */
    fun readiness(progress: List<TopicProgress>, category: String): Int =
        estimate(progress).let { if (it.shown) it.bySubject[category] ?: -1 else -1 }

    /** Smart session: due reviews, then weakest topics, then unseen questions across subjects. */
    fun smart(
        index: List<QuestionIndex>,
        syllabusTopics: Set<String>,
        seen: Set<Long>,
        due: List<Long>,
        weakTopics: List<String>,
        n: Int,
        rnd: Random = Random.Default
    ): Pair<List<Long>, List<Long>> {
        val picked = LinkedHashSet<Long>()
        val reviews = due.take((n * 0.4).toInt().coerceAtLeast(if (due.isNotEmpty()) 1 else 0))
        picked += reviews
        val pool = index.filter { it.subtopic in syllabusTopics }
        val weakQuota = (n * 0.4).toInt()
        for (t in weakTopics.take(3)) {
            if (picked.size >= reviews.size + weakQuota) break
            val inTopic = pool.filter { it.subtopic == t && it.id !in picked }
            val fresh = inTopic.filter { it.id !in seen }.shuffled(rnd)
            val old = inTopic.filter { it.id in seen }.shuffled(rnd)
            (fresh + old).take(((weakQuota + 2) / 3).coerceAtLeast(1)).forEach { picked += it.id }
        }
        fill(pool, seen, picked, n, rnd)
        return picked.toList().take(n).shuffled(rnd) to reviews
    }

    /** Topic drill: unseen first. */
    fun topic(index: List<QuestionIndex>, topicId: String, seen: Set<Long>, n: Int, rnd: Random = Random.Default): List<Long> {
        val inTopic = index.filter { it.subtopic == topicId }
        val fresh = inTopic.filter { it.id !in seen }.shuffled(rnd)
        val old = inTopic.filter { it.id in seen }.shuffled(rnd)
        return (fresh + old).take(n).map { it.id }
    }

    /** Custom practice: the chosen subjects, balanced (fixes the old "46% Numerical" draw). */
    fun custom(index: List<QuestionIndex>, categories: List<String>, seen: Set<Long>, n: Int, rnd: Random = Random.Default): List<Long> =
        balanced(index.filter { it.category in categories }, categories, seen, n, rnd)

    /** Same 10 questions for everyone on a given day; balanced across the four subjects. */
    fun daily(index: List<QuestionIndex>, syllabusTopics: Set<String>, day: Long, n: Int = 10): List<Long> {
        val rnd = Random(day * 7919 + 17)
        return balanced(index.filter { it.subtopic in syllabusTopics }, CATEGORIES, emptySet(), n, rnd)
    }

    /** Exam mode: balanced across subjects, syllabus topics only, unseen first. */
    fun exam(index: List<QuestionIndex>, syllabusTopics: Set<String>, seen: Set<Long>, n: Int, rnd: Random = Random.Default): List<Long> =
        balanced(index.filter { it.subtopic in syllabusTopics }, CATEGORIES, seen, n, rnd)

    private fun balanced(pool: List<QuestionIndex>, categories: List<String>, seen: Set<Long>, n: Int, rnd: Random): List<Long> {
        val byCat = categories.associateWith { c ->
            val qs = pool.filter { it.category == c }
            (qs.filter { it.id !in seen }.shuffled(rnd) + qs.filter { it.id in seen }.shuffled(rnd)).map { it.id }
        }.filterValues { it.isNotEmpty() }
        if (byCat.isEmpty()) return emptyList()
        val out = mutableListOf<Long>()
        val cursors = byCat.mapValues { 0 }.toMutableMap()
        while (out.size < n) {
            var progressed = false
            for ((c, ids) in byCat) {
                val i = cursors.getValue(c)
                if (i < ids.size && out.size < n) {
                    out += ids[i]; cursors[c] = i + 1; progressed = true
                }
            }
            if (!progressed) break
        }
        return out.shuffled(rnd)
    }

    private fun fill(pool: List<QuestionIndex>, seen: Set<Long>, picked: LinkedHashSet<Long>, n: Int, rnd: Random) {
        if (picked.size >= n) return
        val rest = pool.filter { it.id !in picked }
        val add = balanced(rest, CATEGORIES, seen, n - picked.size, rnd)
        picked += add
    }

    /** Questions per day to see every syllabus question once before the exam. */
    fun recommendedPerDay(unseenSyllabus: Int, daysLeft: Long): Int? {
        if (daysLeft <= 0) return null
        return ceil(unseenSyllabus / daysLeft.toDouble()).toInt().coerceIn(10, 100)
    }
}
