package com.pjcinc.csepractice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    // ---- Questions ----
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<Question>)

    @Query("DELETE FROM questions")
    suspend fun clearAllQuestions()

    @Transaction
    suspend fun replaceQuestions(questions: List<Question>) {
        clearAllQuestions()
        insertQuestions(questions)
    }

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun getQuestionCount(): Int

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<Question>

    @Query("SELECT id, category, subtopic FROM questions")
    suspend fun getIndex(): List<QuestionIndex>

    @Query("SELECT subtopic, COUNT(*) AS n, 0 AS correct FROM questions GROUP BY subtopic")
    fun topicSizes(): Flow<List<TopicStat>>

    // ---- Attempts ----
    @Insert
    suspend fun insertAttempts(attempts: List<Attempt>)

    @Query("SELECT subtopic, COUNT(*) AS n, SUM(correct) AS correct FROM attempts WHERE chosen >= 0 GROUP BY subtopic")
    fun topicStats(): Flow<List<TopicStat>>

    @Query("SELECT subtopic, COUNT(*) AS n, SUM(correct) AS correct FROM attempts WHERE chosen >= 0 GROUP BY subtopic")
    suspend fun topicStatsNow(): List<TopicStat>

    @Query("SELECT category, COUNT(*) AS n, SUM(correct) AS correct FROM attempts WHERE chosen >= 0 GROUP BY category")
    suspend fun categoryStatsNow(): List<CategoryStat>

    @Query("SELECT COUNT(*) FROM attempts WHERE chosen >= 0")
    fun answeredCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM attempts WHERE chosen >= 0")
    suspend fun answeredCountNow(): Int

    @Query("SELECT COUNT(*) FROM attempts WHERE chosen >= 0 AND day = :day")
    fun answeredOnDay(day: Long): Flow<Int>

    @Query("SELECT DISTINCT questionId FROM attempts")
    suspend fun seenQuestionIds(): List<Long>

    @Query("DELETE FROM attempts")
    suspend fun clearAttempts()

    // ---- Spaced repetition ----
    @Query("SELECT * FROM review_cards WHERE questionId = :id")
    suspend fun getCard(id: Long): ReviewCard?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCard(card: ReviewCard)

    @Query("SELECT questionId FROM review_cards WHERE box < 5 AND dueDay <= :today ORDER BY dueDay ASC")
    suspend fun dueCardIds(today: Long): List<Long>

    @Query("SELECT COUNT(*) FROM review_cards WHERE box < 5 AND dueDay <= :today")
    fun dueCount(today: Long): Flow<Int>

    @Query("DELETE FROM review_cards")
    suspend fun clearCards()

    // ---- Sessions ----
    @Insert
    suspend fun insertSession(session: PracticeSession): Long

    @Query("SELECT * FROM practice_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<PracticeSession>>

    @Query("DELETE FROM practice_sessions")
    suspend fun clearAllSessions()

    @Insert
    suspend fun insertCategoryResults(results: List<SessionCategoryResult>)
}
