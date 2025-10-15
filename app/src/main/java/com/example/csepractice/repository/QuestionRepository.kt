package com.example.csepractice.repository

import android.content.Context
import android.util.Log
import com.example.csepractice.data.AppDatabase
import com.example.csepractice.data.Question
import com.example.csepractice.data.PracticeSession
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext

class QuestionRepository(private val context: Context) {
    private val dao = AppDatabase.getDatabase(context).questionDao()

    suspend fun getRandomQuestionsByCategories(categories: List<String>, count: Int): List<Question> {
        if (categories.isEmpty()) return emptyList()

        val questionsPerCategory = count / categories.size
        val remainder = count % categories.size
        Log.d("Repo", "Fetching $count questions, ~$questionsPerCategory per category from $categories (with $remainder remainder)")

        val allQuestions = mutableListOf<Question>()
        categories.forEachIndexed { index, cat ->
            val thisCount = questionsPerCategory + if (index < remainder) 1 else 0
            val questionsFlow = dao.getRandomQuestionsByCategory(cat, thisCount)
            val questions = withContext(Dispatchers.IO) {
                questionsFlow.firstOrNull() ?: emptyList()
            }
            allQuestions.addAll(questions)
            Log.d("Repo", "Fetched $thisCount questions from category '$cat' (actual: ${questions.size})")
        }

        return allQuestions.shuffled()  // Optional: Shuffle the combined list for overall randomness
    }

    fun getRandomQuestions(count: Int): Flow<List<Question>> = dao.getRandomQuestions(count)

    suspend fun insertSession(session: PracticeSession) = dao.insertSession(session)

    fun getAllSessions(): Flow<List<PracticeSession>> = dao.getAllSessions()

    suspend fun clearAllSessions() {
        dao.clearAllSessions()
        Log.d("Repo", "Cleared all practice sessions")
    }

    suspend fun clearAllQuestions() {
        dao.clearAllQuestions()
        Log.d("Repo", "Cleared all questions from database")
    }

    suspend fun seedQuestionsIfEmpty() {
        val questionsFlow = dao.getRandomQuestions(1)
        val questions = questionsFlow.firstOrNull()
        if (questions.isNullOrEmpty()) {
            seedQuestions()
        } else {
            Log.d("Repo", "DB already has questions, skipping seed")
            logCategoryCounts()
        }
    }

    private suspend fun seedQuestions() {
        withContext(Dispatchers.IO) {
            try {
                clearAllQuestions()
                val json = context.assets.open("questions.json").bufferedReader().use { it.readText() }
                val type = object : TypeToken<List<Question>>() {}.type
                val questionsList: List<Question> = Gson().fromJson(json, type)
                Log.d("Repo", "Read ${questionsList.size} questions from questions.json")
                val updatedQuestions = questionsList.map { question ->
                    if (question.text.startsWith("If 4 men paint a house")) {
                        question.copy(category = "Numerical Ability")
                    } else {
                        question
                    }
                }
                Log.d("Repo", "Reassigned 'Unknown' question to Numerical Ability if present")
                val uniqueQuestions = updatedQuestions
                    .groupBy { it.text }
                    .map { it.value.first() }
                Log.d("Repo", "Deduplicated to ${uniqueQuestions.size} questions")
                dao.insertQuestions(uniqueQuestions)
                Log.d("Repo", "Seeded ${uniqueQuestions.size} questions")
                logCategoryCounts()
                val requiredFields = listOf("text", "optionA", "optionB", "optionC", "optionD", "correctAnswer", "category", "explanation")
                val issues = uniqueQuestions.filter { q ->
                    requiredFields.any { field ->
                        q.javaClass.getDeclaredField(field).get(q)?.toString().isNullOrEmpty()
                    }
                }
                if (issues.isNotEmpty()) {
                    Log.d("Repo", "Questions with missing or empty fields:")
                    issues.forEach { q ->
                        val missing = requiredFields.filter { q.javaClass.getDeclaredField(it).get(q)?.toString().isNullOrEmpty() }
                        Log.d("Repo", "Question: ${q.text.take(50)}... | Missing/Empty: $missing")
                    }
                } else {
                    Log.d("Repo", "No questions with missing or empty fields")
                }
            } catch (e: Exception) {
                Log.e("Repo", "JSON seeding failed: ${e.message}")
            }
        }
    }

    private suspend fun logCategoryCounts() {
        val categories = listOf("Verbal Ability", "Analytical Ability", "General Information", "Numerical Ability", "Unknown")
        categories.forEach { category ->
            val count = dao.getQuestionCountByCategory(category).firstOrNull() ?: 0
            Log.d("Repo", "Category '$category' has $count questions")
        }
    }

    fun getQuestionCountByCategory(category: String): Flow<Int> = dao.getQuestionCountByCategory(category)

    fun getCategories(): Flow<List<String>> {
        return dao.getCategories()
    }

    fun getTotalQuestionCount(): Flow<Int> = dao.getTotalQuestionCount()

    fun getAverageForCategory(category: String): Flow<Double> {
        return dao.getAvgByCategory(category)
    }
}