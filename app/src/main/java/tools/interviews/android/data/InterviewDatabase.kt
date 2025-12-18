package tools.interviews.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import tools.interviews.android.model.Company
import tools.interviews.android.model.Interview

@Database(entities = [Interview::class, Company::class], version = 5, exportSchema = false)
@TypeConverters(Converters::class)
abstract class InterviewDatabase : RoomDatabase() {
    abstract fun interviewDao(): InterviewDao
    abstract fun companyDao(): CompanyDao

    companion object {
        @Volatile
        private var INSTANCE: InterviewDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE interviews ADD COLUMN metadataJSON TEXT")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE interviews ADD COLUMN serverId INTEGER")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create companies table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS companies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        serverId INTEGER,
                        name TEXT NOT NULL
                    )
                """)
                // Create unique index on serverId
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_companies_serverId ON companies (serverId)")

                // Migrate existing company names to companies table
                db.execSQL("""
                    INSERT INTO companies (name)
                    SELECT DISTINCT companyName FROM interviews WHERE companyName IS NOT NULL
                """)

                // SQLite doesn't support adding foreign keys via ALTER TABLE
                // We need to recreate the table with the foreign key constraint

                // Create new interviews table with foreign key
                db.execSQL("""
                    CREATE TABLE interviews_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        serverId INTEGER,
                        companyId INTEGER,
                        jobTitle TEXT NOT NULL,
                        companyName TEXT NOT NULL,
                        clientCompany TEXT,
                        stage TEXT NOT NULL,
                        method TEXT,
                        outcome TEXT NOT NULL,
                        applicationDate TEXT NOT NULL,
                        interviewDate TEXT,
                        deadline TEXT,
                        interviewer TEXT,
                        link TEXT,
                        jobListing TEXT,
                        notes TEXT,
                        metadataJSON TEXT,
                        FOREIGN KEY (companyId) REFERENCES companies(id) ON DELETE SET NULL
                    )
                """)

                // Copy data from old table to new table, setting companyId
                db.execSQL("""
                    INSERT INTO interviews_new (id, serverId, companyId, jobTitle, companyName, clientCompany, stage, method, outcome, applicationDate, interviewDate, deadline, interviewer, link, jobListing, notes, metadataJSON)
                    SELECT
                        i.id, i.serverId, c.id, i.jobTitle, i.companyName, i.clientCompany, i.stage, i.method, i.outcome, i.applicationDate, i.interviewDate, i.deadline, i.interviewer, i.link, i.jobListing, i.notes, i.metadataJSON
                    FROM interviews i
                    LEFT JOIN companies c ON c.name = i.companyName
                """)

                // Drop old table
                db.execSQL("DROP TABLE interviews")

                // Rename new table
                db.execSQL("ALTER TABLE interviews_new RENAME TO interviews")

                // Create index on companyId
                db.execSQL("CREATE INDEX IF NOT EXISTS index_interviews_companyId ON interviews (companyId)")
            }
        }

        // Migration to fix the foreign key constraint if version 4 migration was partial
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create companies table if it doesn't exist
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS companies (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        serverId INTEGER,
                        name TEXT NOT NULL
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_companies_serverId ON companies (serverId)")

                // Insert missing companies from interviews
                db.execSQL("""
                    INSERT OR IGNORE INTO companies (name)
                    SELECT DISTINCT companyName FROM interviews
                    WHERE companyName IS NOT NULL
                    AND companyName NOT IN (SELECT name FROM companies)
                """)

                // Recreate interviews table with proper foreign key
                db.execSQL("""
                    CREATE TABLE interviews_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        serverId INTEGER,
                        companyId INTEGER,
                        jobTitle TEXT NOT NULL,
                        companyName TEXT NOT NULL,
                        clientCompany TEXT,
                        stage TEXT NOT NULL,
                        method TEXT,
                        outcome TEXT NOT NULL,
                        applicationDate TEXT NOT NULL,
                        interviewDate TEXT,
                        deadline TEXT,
                        interviewer TEXT,
                        link TEXT,
                        jobListing TEXT,
                        notes TEXT,
                        metadataJSON TEXT,
                        FOREIGN KEY (companyId) REFERENCES companies(id) ON DELETE SET NULL
                    )
                """)

                // Copy data, updating companyId if null
                db.execSQL("""
                    INSERT INTO interviews_new (id, serverId, companyId, jobTitle, companyName, clientCompany, stage, method, outcome, applicationDate, interviewDate, deadline, interviewer, link, jobListing, notes, metadataJSON)
                    SELECT
                        i.id, i.serverId, COALESCE(i.companyId, c.id), i.jobTitle, i.companyName, i.clientCompany, i.stage, i.method, i.outcome, i.applicationDate, i.interviewDate, i.deadline, i.interviewer, i.link, i.jobListing, i.notes, i.metadataJSON
                    FROM interviews i
                    LEFT JOIN companies c ON c.name = i.companyName
                """)

                db.execSQL("DROP TABLE interviews")
                db.execSQL("ALTER TABLE interviews_new RENAME TO interviews")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_interviews_companyId ON interviews (companyId)")
            }
        }

        fun getDatabase(context: Context): InterviewDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InterviewDatabase::class.java,
                    "interview_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
