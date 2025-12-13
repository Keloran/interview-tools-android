package tools.interviews.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import tools.interviews.android.model.Interview

@Database(entities = [Interview::class], version = 2, exportSchema = false)
@TypeConverters(Converters::class)
abstract class InterviewDatabase : RoomDatabase() {
    abstract fun interviewDao(): InterviewDao

    companion object {
        @Volatile
        private var INSTANCE: InterviewDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE interviews ADD COLUMN metadataJSON TEXT")
            }
        }

        fun getDatabase(context: Context): InterviewDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InterviewDatabase::class.java,
                    "interview_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
