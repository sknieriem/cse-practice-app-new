package com.pjcinc.csepractice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.pjcinc.csepractice.BuildConfig

@Database(
    entities = [Question::class, PracticeSession::class, SessionCategoryResult::class,
        Attempt::class, ReviewCard::class, ReadinessSnapshot::class, ExamOutcome::class],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /** Real migrations for store builds. v4 is the first schema shipped to the store: add
         *  Migration(4, 5) etc. here for every later schema change (schemas are exported to app/schemas). */
        val MIGRATIONS: Array<Migration> = arrayOf()

        /**
         * Debug and tester (beta) builds may reset the database when the schema changes; store
         * (release) builds never do, so a missing migration fails loudly in testing instead of
         * silently erasing a paying user's attempts, streak and review cards.
         */
        fun getDatabase(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "cse_practice_database"
                )
                    .addMigrations(*MIGRATIONS)
                    .apply { if (BuildConfig.DB_RESET_ON_SCHEMA_CHANGE) fallbackToDestructiveMigration(true) }
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
