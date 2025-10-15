package com.example.csepractice.repository

import android.content.Context
import com.example.csepractice.data.AppDatabase
import com.example.csepractice.data.Question
import com.example.csepractice.data.PracticeSession
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class QuestionRepository(private val context: Context) {
    val dao = AppDatabase.getDatabase(context).questionDao()

    fun getRandomQuestionsByCategories(categories: List<String>, count: Int): Flow<List<Question>> =
        dao.getRandomQuestionsByCategories(categories, count)

    fun getRandomQuestions(count: Int): Flow<List<Question>> = dao.getRandomQuestions(count)

    suspend fun insertSession(session: PracticeSession) = dao.insertSession(session)

    fun getAllSessions(): Flow<List<PracticeSession>> = dao.getAllSessions()

    suspend fun clearAllSessions() {
        dao.clearAllSessions()
    }

    suspend fun seedQuestionsIfEmpty() {
        // Check if DB is empty
        val questionsFlow = dao.getRandomQuestions(1)
        val questions = questionsFlow.firstOrNull()
        if (questions.isNullOrEmpty()) {
            val json = context.assets.open("questions.json").bufferedReader().use { it.readText() }
            val type = object : TypeToken<List<Question>>() {}.type
            val questions: List<Question> = Gson().fromJson(json, type)
            dao.insertQuestions(questions)
        }
    }
}