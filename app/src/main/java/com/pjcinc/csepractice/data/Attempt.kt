package com.pjcinc.csepractice.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One answered (or skipped) question. Raw material for weak-topic detection and review. */
@Entity(tableName = "attempts", indices = [Index("questionId"), Index("subtopic"), Index("day")])
data class Attempt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long,
    val category: String,
    val subtopic: String,
    /** Canonical option index 0..3 the user picked; -1 = skipped. */
    val chosen: Int,
    val correct: Boolean,
    val usedHint: Boolean,
    val timeMs: Long,
    val mode: String,
    /** LocalDate.toEpochDay() */
    val day: Long,
    val timestamp: Long = System.currentTimeMillis()
)

/** Leitner spaced-repetition card. A question enters when answered wrong. */
@Entity(tableName = "review_cards")
data class ReviewCard(
    @PrimaryKey val questionId: Long,
    /** 1..5; 5 = learned */
    val box: Int,
    /** epoch day when it is next due */
    val dueDay: Long
)

data class TopicStat(val subtopic: String, val n: Int, val correct: Int)
data class CategoryStat(val category: String, val n: Int, val correct: Int)
data class QuestionIndex(val id: Long, val category: String, val subtopic: String)
