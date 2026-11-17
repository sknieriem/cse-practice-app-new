package com.pjcinc.csepractice

import com.pjcinc.csepractice.data.TopicStat
import com.pjcinc.csepractice.data.UserState
import com.pjcinc.csepractice.domain.Encourage
import com.pjcinc.csepractice.domain.Game
import com.pjcinc.csepractice.domain.Planner
import com.pjcinc.csepractice.domain.QuizMode
import com.pjcinc.csepractice.domain.Topic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EncourageTest {

    private fun facts(today: Long) = Game.SessionFacts(QuizMode.SMART, 10, 7, 0, 3, 0, today, 12, 50, emptyMap())

    @Test fun freeze_covers_one_missed_day_and_is_spent() {
        val s = UserState(streak = 7, lastActiveDay = 100, freezes = 1)
        val r = Game.reward(s, facts(102))
        assertEquals(8, r.newState.streak)
        assertEquals(1, r.freezesUsed)
        assertEquals(0, r.newState.freezes)
    }

    @Test fun too_many_missed_days_restart_without_losing_best() {
        val s = UserState(streak = 7, bestStreak = 7, lastActiveDay = 100, freezes = 1)
        val r = Game.reward(s, facts(104))
        assertEquals(1, r.newState.streak)
        assertTrue(r.streakRestarted)
        assertEquals(7, r.newState.bestStreak)
    }

    @Test fun freeze_earned_every_7_days_capped() {
        val r = Game.reward(UserState(streak = 6, lastActiveDay = 99, freezes = 0), facts(100))
        assertTrue(r.freezeEarned)
        assertEquals(1, r.newState.freezes)
        val capped = Game.reward(UserState(streak = 13, lastActiveDay = 99, freezes = Game.MAX_FREEZES), facts(100))
        assertFalse(capped.freezeEarned)
    }

    @Test fun live_streak_honours_freezes() {
        val s = UserState(streak = 5, lastActiveDay = 100, freezes = 1)
        assertEquals(5, Game.liveStreak(s, 102))
        assertEquals(0, Game.liveStreak(s, 103))
    }

    private val topics = Planner.CATEGORIES.mapIndexed { i, c -> Topic("t$i", c, "T$i", true, "", "") }
    private val sizes = topics.map { TopicStat(it.id, 100, 0) }

    @Test fun readiness_hidden_until_enough_answers_and_tracks_accuracy() {
        val few = Planner.estimate(Planner.topicProgress(topics, sizes, listOf(TopicStat("t0", 5, 5))))
        assertFalse(few.shown)
        val good = Planner.estimate(Planner.topicProgress(topics, sizes, topics.map { TopicStat(it.id, 40, 36) }))
        assertTrue(good.shown)
        assertTrue("90% accuracy -> high estimate, got ${good.overall}", good.overall in 80..92)
        val poor = Planner.estimate(Planner.topicProgress(topics, sizes, topics.map { TopicStat(it.id, 40, 16) }))
        assertTrue("40% accuracy -> low estimate, got ${poor.overall}", poor.overall in 35..50)
    }

    @Test fun trying_a_new_subject_does_not_cost_points() {
        val one = Planner.estimate(Planner.topicProgress(topics, sizes, listOf(TopicStat("t0", 30, 21))))
        // weakest is only ever a subject the user has actually tried
        assertEquals("Numerical Ability", one.weakestSubject)
        assertEquals(-1, one.bySubject["Verbal Ability"])
    }

    @Test fun praise_is_specific_when_improved_and_honest_when_not() {
        val improved = Encourage.praise("en", listOf(Encourage.TopicDelta("Fractions", 4, 5, 10, 4)),
            streak = 1, streakExtended = true, freezesUsed = 0, weeklyGain = null, total = 10, correct = 6)
        assertTrue(improved.first(), improved.first().contains("Fractions: 4 of 5 right — up from 40%"))

        val flat = Encourage.praise("en", listOf(Encourage.TopicDelta("Fractions", 2, 5, 10, 5)),
            streak = 1, streakExtended = true, freezesUsed = 0, weeklyGain = null, total = 10, correct = 5)
        assertEquals(listOf("You finished 10 questions — 10 steps closer to exam day."), flat)
    }

    @Test fun wrong_answer_header_is_warm_and_names_the_answer() {
        val h = Encourage.wrongHeader("en", 123L, 'C')
        assertTrue(h.contains("C"))
        assertFalse(h.lowercase().contains("wrong"))
        assertTrue(Encourage.wrongHeader("tl", 5L, 'B').contains("B"))
    }
}
