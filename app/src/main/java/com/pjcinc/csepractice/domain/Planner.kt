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

    /** Weakest syllabus topics first. Topics never tried rank as "unknown" (skill 0.5). */
    fun weakest(progress: List<TopicProgress>): List<TopicProgress> =
        progress.filter { it.topic.syllabus }.sortedWith(compareBy({ it.skill }, { -it.questions }))

    /**
     * Readiness for a subject, 0..100: each syllabus topic contributes its accuracy, weighted by its
     * share of the subject's questions — but only once it has been practised. Untried topics count
     * as 0, so readiness grows with BOTH coverage and accuracy and never looks better than it is.
     * A topic's evidence is capped at full weight after 10 answers.
     */
    fun readiness(progress: List<TopicProgress>, category: String): Int {
        val ts = progress.filter { it.topic.category == category && it.topic.syllabus }
        val w = ts.sumOf { it.questions }
        if (w == 0) return 0
        val score = ts.sumOf { t ->
            if (t.answered == 0) 0.0
            else (t.correct.toDouble() / t.answered) * (t.answered.coerceAtMost(10) / 10.0) * t.questions
        }
        return (100 * score / w).toInt()
    }

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
