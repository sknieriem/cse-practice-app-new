package com.pjcinc.csepractice.domain

import com.pjcinc.csepractice.data.Question

/**
 * Optional online "Ask the tutor". OFF in this build (BuildConfig.TUTOR_ENABLED = false).
 *
 * Design (not shipped yet):
 *  - The app sends {deviceId, questionId, chosen option, the user's follow-up text} to Steve's
 *    server (tools/tutor_server.py). The server holds the Anthropic API key and calls Claude;
 *    the phone never sees a key and never calls an AI provider directly.
 *  - The server enforces a small monthly quota per subscriber, so cost per user stays bounded.
 *  - Everything else in the app keeps working offline.
 */
interface TutorClient {
    sealed class Reply {
        data class Answer(val text: String, val remainingThisMonth: Int) : Reply()
        data class Unavailable(val reason: String) : Reply()
    }

    suspend fun ask(question: Question, chosenCanonical: Int, message: String): Reply
}

/** What this build uses. */
object DisabledTutor : TutorClient {
    override suspend fun ask(question: Question, chosenCanonical: Int, message: String) =
        TutorClient.Reply.Unavailable("The online tutor is not available in this version.")
}
