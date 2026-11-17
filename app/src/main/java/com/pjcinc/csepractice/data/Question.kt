package com.pjcinc.csepractice.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One exam item. Built by tools/build_content.py into assets/questions.json and seeded into Room.
 * [id] is stable across content versions (crc32 of the original text + options), so a user's
 * attempt history survives content updates. Optional fields are null when not yet written.
 */
@Entity(tableName = "questions", indices = [Index("category"), Index("subtopic")])
data class Question(
    @PrimaryKey val id: Long,
    val category: String,
    val subtopic: String,
    val passage: String?,
    val text: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    /** "A".."D" */
    val correctAnswer: String,
    val explanation: String,
    val steps: String?,
    val hint: String?,
    val whyA: String?,
    val whyB: String?,
    val whyC: String?,
    val whyD: String?,
    val explanationTl: String?,
    val stepsTl: String?,
    val hintTl: String?,
    val difficulty: Int?,
    val timeSensitive: Boolean?,
    val source: String?
) {
    fun options(): List<String> = listOf(optionA, optionB, optionC, optionD)
    fun correctIndex(): Int = "ABCD".indexOf(correctAnswer).coerceAtLeast(0)
    fun whyWrong(canonicalIndex: Int): String? = when (canonicalIndex) {
        0 -> whyA
        1 -> whyB
        2 -> whyC
        3 -> whyD
        else -> null
    }
}
