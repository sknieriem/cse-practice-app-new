package com.pjcinc.csepractice.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_sessions")
data class PracticeSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long = System.currentTimeMillis(),
    /** percentage 0..100 */
    val score: Int,
    val correctCount: Int,
    val totalQuestions: Int,
    val categories: String = "",
    /** milliseconds */
    val timeTaken: Long = 0,
    val mode: String = "PRACTICE",
    val xpEarned: Int = 0
)
