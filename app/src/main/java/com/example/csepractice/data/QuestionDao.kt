package com.example.csepractice.data

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

    /**
     * Average score across all sessions whose categories list contains [category].
     * NOTE: this is approximate when sessions mix multiple categories — the session's
     * overall score is attributed to each category it touched. For an exact per-category
     * average, store per-category breakdowns per session (left as a follow-up).
     */
    @Query(
        """
        SELECT AVG(score) FROM practice_sessions
        WHERE categories LIKE '%' || :category || '%'
        """
    )
    fun getAvgByCategory(category: String): Flow<Double?>
}
