package com.pjcinc.csepractice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Question::class, PracticeSession::class, SessionCategoryResult::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Migration v1 -> v2: adds the [SessionCategoryResult] table.
         * Existing sessions stay valid; their category breakdowns just won't appear
         * in averages (a new session is needed to repopulate them). This is the
         * conservative path; for an unpublished app you could swap this for
         * `fallbackToDestructiveMigration()` instead.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS session_category_results (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId INTEGER NOT NULL,
                        category TEXT NOT NULL,
                        correctCount INTEGER NOT NULL,
                        totalCount INTEGER NOT NULL,
                        FOREIGN KEY(sessionId) REFERENCES practice_sessions(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_session_category_results_sessionId " +
                        "ON session_category_results(sessionId)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_session_category_results_category " +
                        "ON session_category_results(category)"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cse_practice_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
