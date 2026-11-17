package com.pjcinc.csepractice.domain

/**
 * PHASE 2 (not in this build): opt-in "study buddies". See docs/PHASE2_COMMUNITY.md.
 *
 * Rules baked into the interface so they can't be forgotten later:
 *  - Nothing leaves the phone without explicit consent (PH Data Privacy Act, RA 10173).
 *  - Nickname only; no contacts, no location, no raw answers — daily readiness snapshots only.
 *  - Leagues of ~20 at a similar readiness level, ranked by IMPROVEMENT, never raw score.
 *  - The app never shows anyone a bottom rank; "top 30%" only when true.
 *  - Cohort lines ("people like you reached 80% in ~X weeks") only when the server has enough
 *    real data; never an invented number.
 */
interface CommunitySync {
    data class Consent(val nickname: String, val acceptedPolicyVersion: Int, val acceptedAt: Long)

    sealed class LeagueView {
        /** e.g. "You're in the top 30% for improvement this week" — only when true */
        data class TopShare(val percent: Int) : LeagueView()
        /** otherwise: the user's own progress, no rank */
        data class OwnProgress(val pointsThisWeek: Int) : LeagueView()
        data object Unavailable : LeagueView()
    }

    suspend fun enable(consent: Consent): Boolean
    suspend fun disableAndDeleteMyData(): Boolean
    /** Uploads unsynced ReadinessSnapshot + ExamOutcome rows (they carry uuid + synced flags). */
    suspend fun syncNow(): Boolean
    suspend fun myLeague(): LeagueView
    /** "Users with your score passed X%" — null until calibrated on enough reported results. */
    suspend fun passRateAtScore(score: Int): Int?
}

/** This build: community features are off; nothing is ever sent. */
object CommunityOff : CommunitySync {
    override suspend fun enable(consent: CommunitySync.Consent) = false
    override suspend fun disableAndDeleteMyData() = true
    override suspend fun syncNow() = false
    override suspend fun myLeague() = CommunitySync.LeagueView.Unavailable
    override suspend fun passRateAtScore(score: Int): Int? = null
}
