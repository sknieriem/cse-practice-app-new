package com.pjcinc.csepractice.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * One readiness reading per day (latest wins). Powers "+9 points since last week" offline, and is
 * the shape phase-2 opt-in sync will upload (aggregates only — never raw answers).
 */
@Entity(tableName = "readiness_snapshots")
data class ReadinessSnapshot(
    /** LocalDate.toEpochDay() */
    @PrimaryKey val day: Long,
    val overall: Int,
    val numerical: Int,
    val verbal: Int,
    val analytical: Int,
    val genInfo: Int,
    val answered: Int,
    val minutesPractised: Int,
    val uuid: String = UUID.randomUUID().toString(),
    val updatedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)

/**
 * The user's REAL exam result, self-reported after exam day. Offline it drives the celebration or
 * the re-plan; in phase 2 (opt-in) it calibrates "users with your score passed X%".
 */
@Entity(tableName = "exam_outcomes")
data class ExamOutcome(
    @PrimaryKey val uuid: String = UUID.randomUUID().toString(),
    val examDay: Long,
    val level: String,
    /** "PASSED" | "NOT_PASSED" */
    val outcome: String,
    /** app readiness estimate when the exam happened, -1 if unknown */
    val readinessAtExam: Int,
    val reportedAt: Long = System.currentTimeMillis(),
    val synced: Boolean = false
)
