package com.example.csepractice.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-category breakdown of a single [PracticeSession]. One row per category
 * that appeared in the session.
 *
 * Together with [PracticeSession.score] (the overall %), this table is what
 * the "Category Averages" UI uses for an *exact* per-category average — no more
 * approximation via `LIKE '%cat%'` matching.
 *
 * The foreign key cascades on delete so clearing sessions also drops these rows.
 */
@Entity(
    tableName = "session_category_results",
    foreignKeys = [
        ForeignKey(
            entity = PracticeSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId"), Index("category")]
)
data class SessionCategoryResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Int,
    val category: String,
    val correctCount: Int,
    val totalCount: Int
) {
    /** Percentage 0–100. Returns 0 if [totalCount] is 0 (shouldn't happen but defensive). */
    val percent: Int get() = if (totalCount == 0) 0 else (correctCount * 100) / totalCount
}
