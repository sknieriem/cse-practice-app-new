// app/src/main/java/com/example/csepractice/data/PracticeSession.kt
package com.example.csepractice.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_sessions")
data class PracticeSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long = System.currentTimeMillis(),
    val score: Int,  // e.g., 80 (percentage)
    val correctCount: Int,
    val totalQuestions: Int,
    val categories: String = "",  // Comma-separated or "All"
    val timeTaken: Long = 0  // Total time in milliseconds
)