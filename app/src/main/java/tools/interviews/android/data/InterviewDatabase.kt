package tools.interviews.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import tools.interviews.android.model.Interview

@Database(entities = [Interview::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class InterviewDatabase : RoomDatabase() {
    abstract fun interviewDao(): InterviewDao

    companion object {
        @Volatile
        private var INSTANCE: InterviewDatabase? = null

        fun getDatabase(context: Context): InterviewDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InterviewDatabase::class.java,
                    "interview_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
