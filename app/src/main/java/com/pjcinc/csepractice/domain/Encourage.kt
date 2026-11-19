package com.pjcinc.csepractice.domain

import kotlin.math.abs

/**
 * Encouragement that is HONEST and SPECIFIC: every line is computed from what the user actually
 * did. No fake praise, no guilt, no comparisons with other people. English + Taglish, in the voice
 * of a supportive kuya/ate.
 */
object Encourage {

    fun t(lang: String, en: String, tl: String) = if (lang == "tl") tl else en

    private fun <T> pick(seed: Long, xs: List<T>): T = xs[(abs(seed) % xs.size).toInt()]

    // ---- per-question feedback ---------------------------------------------------------
    fun correctHeader(lang: String, seed: Long): String = pick(seed, if (lang == "tl")
        listOf("Tama! ✅", "Ang galing! ✅", "Ayos! ✅", "Yan ang sagot! ✅")
    else listOf("Correct! ✅", "Nice one! ✅", "That's it! ✅", "Well done! ✅"))

    fun wrongHeader(lang: String, seed: Long, letter: Char): String = pick(seed, if (lang == "tl")
        listOf(
            "Okay lang 'yan! Ang sagot ay $letter — ganito ang trick:",
            "Muntik na! $letter ang tama. Tingnan natin:",
            "Dito tayo natututo. $letter ang sagot — heto kung bakit:"
        )
    else listOf(
        "Not quite — and that's okay. It's $letter. Here's the trick:",
        "Almost! The answer is $letter. Let's learn this one:",
        "Mistakes are how we learn. It's $letter — here's why:"
    ))

    // ---- results ------------------------------------------------------------------------
    fun cheer(lang: String, percent: Int): String = when {
        percent == 100 -> t(lang, "Perfect score! 🏆", "Perfect! Ang galing mo! 🏆")
        percent >= Planner.PASS_MARK -> t(lang, "Passing level! Keep it up 💪", "Pasado level 'yan! Tuloy lang 💪")
        percent >= 60 -> t(lang, "Getting closer. Review the misses and you'll get there 📚", "Palapit na! I-review ang mga mali, kaya mo 'yan 📚")
        else -> t(lang, "Every question you try makes you stronger 🙌", "Bawat tanong, dagdag lakas. Kaya mo 'yan! 🙌")
    }

    data class TopicDelta(val topicName: String, val sessionCorrect: Int, val sessionTotal: Int, val priorAnswered: Int, val priorCorrect: Int)

    /**
     * Up to 3 specific, true sentences about this session. Ordered by how meaningful they are.
     */
    fun praise(
        lang: String,
        topics: List<TopicDelta>,
        streak: Int,
        streakExtended: Boolean,
        freezesUsed: Int,
        weeklyGain: Int?,
        total: Int,
        correct: Int
    ): List<String> {
        val out = mutableListOf<String>()
        val praised = mutableSetOf<String>()
        // improvement on a topic vs. their own past
        topics.filter { it.sessionTotal >= 3 && it.priorAnswered >= 3 }
            .map { it to (it.sessionCorrect.toDouble() / it.sessionTotal - it.priorCorrect.toDouble() / it.priorAnswered) }
            .filter { it.second >= 0.15 }
            .sortedByDescending { it.second }
            .take(2)
            .forEach { (d, _) ->
                praised += d.topicName
                val before = d.priorCorrect * 100 / d.priorAnswered
                out += t(lang,
                    "${d.topicName}: ${d.sessionCorrect} of ${d.sessionTotal} right — up from $before% before 📈",
                    "${d.topicName}: ${d.sessionCorrect}/${d.sessionTotal} tama — mula $before% dati 📈")
            }
        // perfect topic within the session
        if (out.size < 2) topics.filter { it.sessionTotal >= 3 && it.sessionCorrect == it.sessionTotal && it.topicName !in praised }
            .take(1).forEach {
                out += t(lang, "All ${it.sessionTotal} ${it.topicName} questions right! ⭐", "Lahat ng ${it.sessionTotal} sa ${it.topicName}, tama! ⭐")
            }
        if (weeklyGain != null && weeklyGain > 0) {
            out += t(lang, "Your estimated score is up $weeklyGain points since last week.", "Tumaas ng $weeklyGain points ang estimated score mo mula last week.")
        }
        if (freezesUsed > 0) {
            out += t(lang, "A streak freeze ❄️ kept your $streak-day streak safe.", "Na-save ng streak freeze ❄️ ang $streak-day streak mo.")
        } else if (streakExtended && streak >= 2) {
            out += t(lang, "$streak days in a row! 🔥", "$streak araw na sunod-sunod! 🔥")
        }
        if (out.isEmpty()) {
            out += t(lang,
                "You finished $total questions — $total steps closer to exam day.",
                "Natapos mo ang $total tanong — $total hakbang palapit sa exam day.")
        }
        return out.take(3)
    }

    // ---- one-time celebrations ----------------------------------------------------------
    data class Celebration(val id: String, val emoji: String, val title: String, val body: String)

    fun readinessCelebration(lang: String, threshold: Int): Celebration = when (threshold) {
        Planner.PASS_MARK -> Celebration("ready_80", "🎯", t(lang, "Estimated score: 80%!", "80% na ang estimated score mo!"),
            t(lang, "That's the passing rating. Keep practising to stay there.", "Yan ang passing rating! Panatilihin natin 'yan."))
        else -> Celebration("ready_$threshold", "📈", t(lang, "Estimated score passed $threshold%!", "Lumampas na sa $threshold% ang estimated score mo!"),
            t(lang, "${Planner.PASS_MARK - threshold} more points to the passing rating.", "${Planner.PASS_MARK - threshold} points na lang sa passing rating."))
    }

    fun strongTopic(lang: String, topicId: String, name: String) = Celebration(
        "strong_$topicId", "💪", t(lang, "$name is now a strong topic!", "Strong topic mo na ang $name!"),
        t(lang, "You turned it around with practice.", "Napag-aralan mo talaga. Galing!"))

    fun personalBest(lang: String, percent: Int, previous: Int) = Celebration(
        "best", "🏅", t(lang, "New personal best: $percent%!", "Bagong personal best: $percent%!"),
        t(lang, "Your previous best was $previous%.", "Ang dating best mo ay $previous%."))

    fun firstMock(lang: String) = Celebration(
        "first_mock", "⏱️", t(lang, "First mock exam done!", "Unang mock exam, tapos!"),
        t(lang, "Real exam practice under time pressure. That takes guts.", "Practice sa ilalim ng oras — hindi biro 'yan."))

    fun freezeEarned(lang: String) = Celebration(
        "freeze", "❄️", t(lang, "You earned a streak freeze!", "May streak freeze ka na!"),
        t(lang, "If life gets busy and you miss a day, your streak stays safe.", "Kung ma-busy ka at ma-miss ang isang araw, safe pa rin ang streak mo."))

    // ---- home ---------------------------------------------------------------------------
    fun comeback(lang: String, daysAway: Long, streakKept: Boolean): String = when {
        streakKept -> t(lang, "Welcome back! 👋 A streak freeze kept your streak safe.", "Welcome back! 👋 Safe pa rin ang streak mo dahil sa freeze.")
        daysAway >= 14 -> t(lang, "Welcome back! 👋 Everything you learned is saved. Let's start small today.", "Welcome back! 👋 Naka-save lahat ng natutunan mo. Simulan natin nang dahan-dahan.")
        else -> t(lang, "Welcome back! 👋 Your progress is saved. Let's pick up where you left off.", "Welcome back! 👋 Naka-save ang progress mo. Ituloy natin!")
    }

    fun readinessLine(lang: String, r: Planner.Readiness, subjectShort: (String) -> String): String {
        val weak = r.weakestSubject?.let { subjectShort(it) }
        val missing = Planner.CATEGORIES.filter { (r.bySubject[it] ?: -1) < 0 }.map(subjectShort)
        if (missing.isNotEmpty()) return t(lang,
            "You're at ${r.overall}%. Passing is ${Planner.PASS_MARK}%. Try ${missing.joinToString(", ")} to see your full exam estimate.",
            "Nasa ${r.overall}% ka. Ang pasado ay ${Planner.PASS_MARK}%. Subukan ang ${missing.joinToString(", ")} para makita ang buong estimate mo.")
        return if (r.overall >= Planner.PASS_MARK) t(lang,
            "You're at ${r.overall}%. Passing is ${Planner.PASS_MARK}%. You're there — keep it steady!",
            "Nasa ${r.overall}% ka. Ang pasado ay ${Planner.PASS_MARK}%. Pasok ka na — tuloy lang!")
        else t(lang,
            "You're at ${r.overall}%. Passing is ${Planner.PASS_MARK}%." + (weak?.let { " Your weakest area is $it." } ?: ""),
            "Nasa ${r.overall}% ka. Ang pasado ay ${Planner.PASS_MARK}%." + (weak?.let { " Pinakamahina: $it." } ?: ""))
    }

    // ---- exam day -----------------------------------------------------------------------
    /** Generic preparation items. The official list is on the CSC exam notice — the UI says so. */
    fun checklist(lang: String): List<Pair<String, String>> = listOf(
        "notice" to t(lang, "Exam notice / admission slip (check the official CSC list)", "Exam notice / admission slip (tingnan ang opisyal na listahan ng CSC)"),
        "id" to t(lang, "Valid ID as required in your notice", "Valid ID na hinihingi sa notice"),
        "pens" to t(lang, "Pens / pencils as instructed", "Ballpen / lapis ayon sa instructions"),
        "route" to t(lang, "Know your route — plan to arrive early", "Alamin ang daan — umalis nang maaga"),
        "rest" to t(lang, "Sleep early the night before", "Matulog nang maaga sa gabi bago ang exam"),
        "food" to t(lang, "Eat breakfast, bring water", "Mag-almusal, magdala ng tubig")
    )

    fun goodLuck(lang: String, name: String): String = t(lang,
        "You've prepared for this${if (name.isNotBlank()) ", $name" else ""}. Breathe, read each question carefully, and trust your practice. Good luck! 🍀",
        "Pinaghandaan mo 'to${if (name.isNotBlank()) ", $name" else ""}. Huminga nang malalim, basahing mabuti ang bawat tanong, at magtiwala sa sarili. Good luck! 🍀")

    fun notPassed(lang: String): String = t(lang,
        "Taking the exam took courage, and we're proud of you. Not passing this time doesn't erase what you learned. Your progress is saved — let's set the next date and build on everything you already know.",
        "Proud kami sa'yo dahil hinarap mo ang exam. Hindi nawawala ang lahat ng natutunan mo. Naka-save ang lahat ng progress mo — itakda natin ang susunod na petsa at ituloy ang laban.")
}
