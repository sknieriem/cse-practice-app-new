package com.pjcinc.csepractice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {

    // ---- Questions ----

    @Insert
    suspend fun insertQuestions(questions: List<Question>)

    @Query("DELETE FROM questions_table")
    suspend fun clearAllQuestions()

    @Query("SELECT COUNT(*) FROM questions_table")
    suspend fun getQuestionCount(): Int

    @Query("SELECT COUNT(*) FROM questions_table WHERE category = :category")
    suspend fun getQuestionCountByCategory(category: String): Int

    @Query("SELECT DISTINCT category FROM questions_table ORDER BY category ASC")
    fun getCategories(): Flow<List<String>>

    @Query("SELECT * FROM questions_table ORDER BY RANDOM() LIMIT :count")
    suspend fun getRandomQuestions(count: Int): List<Question>

    @Query("SELECT * FROM questions_table WHERE category IN (:categories) ORDER BY RANDOM() LIMIT :count")
    suspend fun getRandomQuestionsByCategories(categories: List<String>, count: Int): List<Question>

    // ---- Sessions ----

    @Insert
    suspend fun insertSession(session: PracticeSession): Long

    @Query("SELECT * FROM practice_sessions ORDER BY date DESC")
    fun getAllSessions(): Flow<List<PracticeSession>>

    @Query("DELETE FROM practice_sessions")
    suspend fun clearAllSessions()

    // ---- Per-category results ----

    @Insert
    suspend fun insertCategoryResults(results: List<SessionCategoryResult>)

    /**
     * Exact per-category average: sum of correct answers across all sessions
     * divided by sum of questions asked in that category, expressed as a percentage.
     * Returns null if the category has never been practiced.
     */
    @Query(
        """
        SELECT CASE WHEN SUM(totalCount) = 0 THEN NULL
                    ELSE (SUM(correctCount) * 100.0) / SUM(totalCount)
               END
        FROM session_category_results
        WHERE category = :category
        """
    )
    fun getExactAvgByCategory(category: String): Flow<Double?>
}
