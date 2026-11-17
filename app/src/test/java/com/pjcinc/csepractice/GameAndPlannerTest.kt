package com.pjcinc.csepractice

import com.pjcinc.csepractice.data.QuestionIndex
import com.pjcinc.csepractice.data.UserState
import com.pjcinc.csepractice.domain.Game
import com.pjcinc.csepractice.domain.Planner
import com.pjcinc.csepractice.domain.QuizMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameAndPlannerTest {

    private fun facts(mode: QuizMode = QuizMode.SMART, total: Int = 10, correct: Int = 7, today: Long = 100, hour: Int = 12) =
        Game.SessionFacts(mode, total, correct, 0, total - correct, 0, today, hour, 50, emptyMap())

    @Test fun levels_are_monotonic_and_match_thresholds() {
        assertEquals(1, Game.levelFor(0))
        assertEquals(1, Game.levelFor(99))
        assertEquals(2, Game.levelFor(100))
        assertEquals(3, Game.levelFor(300))
        var last = 1
        for (xp in 0..20_000 step 37) { val l = Game.levelFor(xp); assertTrue(l >= last); last = l }
    }

    @Test fun xp_breakdown_sums_to_total() {
        val r = Game.reward(UserState(), facts())
        assertEquals(r.breakdown.sumOf { it.second }, r.xpGained)
        // 7×10 + 3×2 + 20 finish + 5 streak-day-1 bonus
        assertEquals(70 + 6 + 20 + 5, r.xpGained)
    }

    @Test fun streak_extends_on_consecutive_days_and_resets_after_gap() {
        val d1 = Game.reward(UserState(), facts(today = 100)).newState
        assertEquals(1, d1.streak)
        val sameDay = Game.reward(d1, facts(today = 100))
        assertEquals(1, sameDay.newState.streak)
        assertFalse(sameDay.streakExtended)
        val d2 = Game.reward(d1, facts(today = 101)).newState
        assertEquals(2, d2.streak)
        val gap = Game.reward(d2, facts(today = 105)).newState
        assertEquals(1, gap.streak)
        assertEquals(2, gap.bestStreak)
        assertEquals(0, Game.liveStreak(d2, 103))
    }

    @Test fun daily_challenge_pays_once_per_day() {
        val first = Game.reward(UserState(), facts(mode = QuizMode.DAILY))
        assertTrue(first.breakdown.any { it.first == "Daily challenge" })
        val again = Game.reward(first.newState, facts(mode = QuizMode.DAILY))
        assertFalse(again.breakdown.any { it.first == "Daily challenge" })
    }

    @Test fun badges_awarded_once() {
        val r1 = Game.reward(UserState(), facts(total = 10, correct = 10))
        assertTrue(r1.newBadges.any { it.id == "perfect_10" })
        val r2 = Game.reward(r1.newState, facts(total = 10, correct = 10, today = 101))
        assertFalse(r2.newBadges.any { it.id == "perfect_10" })
    }

    private val index = buildList {
        var id = 1L
        repeat(500) { add(QuestionIndex(id++, "Numerical Ability", "num_percent")) }
        repeat(60) { add(QuestionIndex(id++, "Verbal Ability", "verb_vocab")) }
        repeat(60) { add(QuestionIndex(id++, "Analytical Ability", "ana_logic")) }
        repeat(60) { add(QuestionIndex(id++, "General Information", "gi_constitution")) }
    }
    private val syllabus = setOf("num_percent", "verb_vocab", "ana_logic", "gi_constitution")

    @Test fun custom_draw_is_balanced_even_when_one_subject_dominates_the_bank() {
        val ids = Planner.custom(index, Planner.CATEGORIES, emptySet(), 40, Random(1))
        val byCat = ids.map { id -> index.first { it.id == id }.category }.groupingBy { it }.eachCount()
        Planner.CATEGORIES.forEach { assertEquals("balanced $it", 10, byCat[it]) }
        assertEquals(40, ids.toSet().size)
    }

    @Test fun daily_is_same_for_same_day_and_differs_across_days() {
        assertEquals(Planner.daily(index, syllabus, 2000), Planner.daily(index, syllabus, 2000))
        assertTrue(Planner.daily(index, syllabus, 2000) != Planner.daily(index, syllabus, 2001))
    }

    @Test fun smart_includes_due_reviews_and_weak_topic_and_no_duplicates() {
        val due = listOf(1L, 2L, 3L)
        val (ids, reviews) = Planner.smart(index, syllabus, emptySet(), due, listOf("gi_constitution"), 15, Random(3))
        assertEquals(15, ids.size)
        assertEquals(15, ids.toSet().size)
        assertTrue(ids.containsAll(reviews))
        assertTrue(reviews.isNotEmpty())
        assertTrue(ids.any { id -> index.first { it.id == id }.subtopic == "gi_constitution" })
    }

    @Test fun unseen_questions_come_first_in_topic_drills() {
        val seen = (1L..490L).toSet()
        val ids = Planner.topic(index, "num_percent", seen, 10, Random(2))
        assertTrue(ids.all { it !in seen })
    }
}
