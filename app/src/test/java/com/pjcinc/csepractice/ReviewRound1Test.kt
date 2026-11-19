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

/** Fixes from the first outside review (2026-11-20). */
class ReviewRound1Test {

    private val topics = Planner.CATEGORIES.mapIndexed { i, c -> Topic("t$i", c, "T$i", true, "", "") }
    private val sizes = topics.map { TopicStat(it.id, 100, 0) }
    private fun est(stats: List<TopicStat>) = Planner.estimate(Planner.topicProgress(topics, sizes, stats))
    private fun facts(today: Long, hour: Int = 12) =
        Game.SessionFacts(QuizMode.SMART, 10, 7, 0, 3, 0, today, hour, 50, emptyMap())

    @Test fun one_easy_subject_never_shows_a_passing_estimate() {
        // the reviewer's case: 18 of 20 General Information, nothing else tried
        val r = est(listOf(TopicStat("t3", 20, 18)))
        assertTrue(r.shown)
        assertFalse(r.allSubjectsTried)
        assertTrue("got ${r.overall}", r.overall < Planner.PASS_MARK)
        val line = Encourage.readinessLine("en", r) { it }
        assertFalse(line, line.contains("You're there"))
        assertTrue(line, line.contains("Try "))
    }

    @Test fun three_perfect_subjects_still_below_pass_until_the_fourth_is_tried() {
        val three = est(topics.take(3).map { TopicStat(it.id, 40, 40) })
        assertTrue("got ${three.overall}", three.overall < Planner.PASS_MARK)
        val four = est(topics.map { TopicStat(it.id, 40, 40) })
        assertTrue(four.allSubjectsTried)
        assertTrue("got ${four.overall}", four.overall >= Planner.PASS_MARK)
    }

    @Test fun a_weak_user_is_not_raised_by_untried_subjects() {
        // 30% on one subject: untried subjects count at the user's own (lower) level, not at 50
        val r = est(listOf(TopicStat("t0", 30, 9)))
        assertTrue("got ${r.overall}", r.overall <= 40)
    }

    @Test fun clock_set_backward_keeps_the_streak() {
        var s = UserState()
        for (d in 100L..129L) s = Game.reward(s, facts(d)).newState
        assertEquals(30, s.streak)
        val back = Game.reward(s, facts(120))
        assertEquals(30, back.newState.streak)
        assertFalse(back.streakExtended)
        assertEquals(129, back.newState.lastActiveDay)
        // and the next real day still counts
        assertEquals(31, Game.reward(back.newState, facts(130)).newState.streak)
        assertEquals(0, Game.daysAway(s, 120))
    }

    @Test fun night_owl_covers_midnight_to_4am() {
        for (h in listOf(22, 23, 0, 1, 3)) assertTrue("hour $h",
            Game.reward(UserState(), facts(100, h)).newBadges.any { it.id == "night_owl" })
        for (h in 4..6) assertTrue("hour $h",
            Game.reward(UserState(), facts(100, h)).newBadges.any { it.id == "early_bird" })
        val noon = Game.reward(UserState(), facts(100, 12)).newBadges.map { it.id }
        assertFalse(noon.contains("night_owl") || noon.contains("early_bird"))
    }

    @Test fun a_perfect_topic_is_never_called_weakest() {
        // Steve's phone: vocabulary 100% was shown as "Focus: your weakest topic"
        val perfect = Planner.topicProgress(topics, sizes, listOf(TopicStat("t1", 3, 3), TopicStat("t0", 10, 9)))
        assertTrue(Planner.focusTopics(perfect).isEmpty())
        val mixed = Planner.topicProgress(topics, sizes, listOf(TopicStat("t1", 3, 3), TopicStat("t0", 10, 5), TopicStat("t2", 5, 3)))
        assertEquals(listOf("t0", "t2"), Planner.focusTopics(mixed).map { it.topic.id })
        // fewer than 3 answers is not enough to call anything weak
        assertTrue(Planner.focusTopics(Planner.topicProgress(topics, sizes, listOf(TopicStat("t0", 2, 0)))).isEmpty())
    }

    @Test fun a_topic_is_praised_once_per_session() {
        val p = Encourage.praise("en", listOf(Encourage.TopicDelta("Fractions", 3, 3, 10, 4)),
            streak = 1, streakExtended = false, freezesUsed = 0, weeklyGain = null, total = 10, correct = 8)
        assertEquals(p.toString(), 1, p.count { it.contains("Fractions") })
    }
}
