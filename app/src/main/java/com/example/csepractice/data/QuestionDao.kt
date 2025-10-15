package com.example.csepractice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface QuestionDao {
    @Insert
    suspend fun insertQuestions(questions: List<Question>)

    @Query("DELETE FROM questions_table")
    suspend fun clearAllQuestions()

    @Query("SELECT * FROM questions_table ORDER BY RANDOM() LIMIT :count")
    fun getRandomQuestions(count: Int): Flow<List<Question>>

    @Query("SELECT * FROM questions_table WHERE category IN (:categories) ORDER BY RANDOM() LIMIT :count")
    fun getRandomQuestionsByCategories(categories: List<String>, count: Int): Flow<List<Question>>

    @Insert
    suspend fun insertSession(session: PracticeSession)

    @Query("SELECT * FROM practice_sessions")
    fun getAllSessions(): Flow<List<PracticeSession>>

    @Query("DELETE FROM practice_sessions")
    suspend fun clearAllSessions()

    @Query("SELECT COUNT(*) FROM questions_table WHERE category = :category")
    fun getQuestionCountByCategory(category: String): Flow<Int>

    @Query("SELECT DISTINCT category FROM questions_table")
    fun getCategories(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM questions_table")
    fun getQuestionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions_table")
    fun getTotalQuestionCount(): Flow<Int>

    @Query("SELECT AVG(score) FROM practice_sessions WHERE categories LIKE '%' || :category || '%'")
    fun getAvgByCategory(category: String): Flow<Double>

    @Query("SELECT * FROM questions_table WHERE category = :category ORDER BY RANDOM() LIMIT :count")
    fun getRandomQuestionsByCategory(category: String, count: Int): Flow<List<Question>>
}