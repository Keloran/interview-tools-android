package tools.interviews.android.data

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tools.interviews.android.data.account.AccountOwners
import tools.interviews.android.model.Company
import tools.interviews.android.model.Interview
import tools.interviews.android.model.*
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AccountOwnershipMigrationTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "owner-migration-${UUID.randomUUID()}"
    private lateinit var database: InterviewDatabase

    @Before fun migratePublishedDatabase() {
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            // Exact schema shipped at version 5, before ownership existed.
            old.execSQL("CREATE TABLE companies (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, serverId INTEGER, name TEXT NOT NULL)")
            old.execSQL("CREATE UNIQUE INDEX index_companies_serverId ON companies(serverId)")
            old.execSQL("CREATE TABLE interviews (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, serverId INTEGER, companyId INTEGER, jobTitle TEXT NOT NULL, companyName TEXT NOT NULL, clientCompany TEXT, stage TEXT NOT NULL, method TEXT, outcome TEXT NOT NULL, applicationDate TEXT NOT NULL, interviewDate TEXT, deadline TEXT, interviewer TEXT, link TEXT, jobListing TEXT, notes TEXT, metadataJSON TEXT, FOREIGN KEY(companyId) REFERENCES companies(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
            old.execSQL("CREATE INDEX index_interviews_companyId ON interviews(companyId)")
            old.execSQL("INSERT INTO companies(id,serverId,name) VALUES(1,10,'Older company')")
            old.execSQL("INSERT INTO interviews(id,serverId,companyId,jobTitle,companyName,stage,outcome,applicationDate,notes) VALUES(1,100,1,'Old account role','Older company','APPLIED','AWAITING_RESPONSE','2026-01-01','Private account notes')")
            old.execSQL("INSERT INTO interviews(id,companyId,jobTitle,companyName,stage,outcome,applicationDate,notes) VALUES(2,1,'Old local role','Older company','APPLIED','SCHEDULED','2026-01-02','Recoverable local notes')")
            old.version = 5
        }
        database = Room.databaseBuilder(context, InterviewDatabase::class.java, name)
            .addMigrations(*InterviewDatabase.ALL_MIGRATIONS).build()
    }
    @After fun close() {
        database.close()
        context.deleteDatabase(name)
    }

    @Test fun unknownStoredStatusesSurviveUpgradeWithTheirOriginalNames() = runBlocking {
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE interviews SET stage = 'FUTURE_PANEL', outcome = 'PAUSED_BY_EMPLOYER' WHERE id = 1")
            it.execSQL("UPDATE interviews SET outcome = 'NO_RESPONSE' WHERE id = 2")
        }
        val older = database.interviewDao().getLegacyByServerId(100)!!
        assertEquals(InterviewStage.UNKNOWN, older.stage)
        assertEquals(InterviewOutcome.UNKNOWN, older.outcome)
        assertEquals("FUTURE_PANEL", older.stageLabel)
        assertEquals("PAUSED_BY_EMPLOYER", older.outcomeLabel)
        assertFalse(older.isUpcomingRecord)
        assertEquals(InterviewOutcome.NO_RESPONSE, database.interviewDao().localOnly(AccountOwners.LEGACY).single().outcome)
    }

    @Test fun oldWallClockTimesRemainRecoverableWithoutAnInventedOffset() = runBlocking {
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE interviews SET stage = 'FIRST_STAGE', interviewDate = '2026-06-01T09:00' WHERE id = 1")
        }
        val old = database.interviewDao().getLegacyByServerId(100)!!
        assertEquals(java.time.LocalDateTime.of(2026, 6, 1, 9, 0), old.interviewDate)
        assertNull(old.interviewInstant)
        assertTrue(old.needsTimeConfirmation)
        assertTrue(runCatching { old.meetingWireValue() }.isFailure)
    }
    @Test fun oldOfferDeadlineMigratesAsACalendarDay() = runBlocking {
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE interviews SET stage = 'OFFER', deadline = '2026-06-01T23:59' WHERE id = 1")
        }
        val old = database.interviewDao().getLegacyByServerId(100)!!
        assertEquals(LocalDate.of(2026, 6, 1), old.offerRespondBy)
        assertNull(old.displayInterviewDate)
        assertEquals("2026-06-01T00:00:00Z", old.deadlineWireValue())
    }

    @Test fun upgradePreservesRowsWithoutGuessingTheirOwner() = runBlocking {
        assertEquals(InterviewDatabase.VERSION, database.openHelper.readableDatabase.version)
        assertTrue(database.interviewDao().getAllInterviews(AccountOwners.GUEST).first().isEmpty())
        assertTrue(database.interviewDao().getAllInterviews("account-b").first().isEmpty())
        val account = database.interviewDao().getLegacyByServerId(100)!!
        assertEquals("Private account notes", account.notes)
        assertEquals(1L, account.companyId)
        assertEquals("Recoverable local notes", database.interviewDao().localOnly(AccountOwners.LEGACY).single().notes)
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
    }

    @Test fun confirmedImportMovesOnlyLocalRowsAndPreservesDownloadedQuarantine() = runBlocking {
        val selected = MutableStateFlow("account-a")
        val repository = InterviewRepository(database.interviewDao(), database.companyDao(), owner = selected,
            transaction = { block -> database.withTransaction { block() } })
        assertTrue(database.interviewDao().getAllInterviews("account-a").first().isEmpty())
        repository.importLocal("account-a")
        val imported = database.interviewDao().getInterviewById(2, "account-a")!!
        assertEquals("Recoverable local notes", imported.notes)
        assertNull(imported.serverId)
        assertEquals("account-a", database.companyDao().getCompanyById(imported.companyId!!, "account-a")!!.ownerId)
        assertNotNull(database.interviewDao().getLegacyByServerId(100))
        assertNull(database.interviewDao().getInterviewById(1, "account-a"))
        assertTrue(database.interviewDao().getUnsyncedInterviews("account-b").isEmpty())
    }

    @Test fun deletionOfOneAccountPreservesOtherAccountPendingWorkAndGuestData() = runBlocking {
        val a = database.companyDao().insert(Company(name = "A", ownerId = "account-a", serverId = 11))
        val b = database.companyDao().insert(Company(name = "B", ownerId = "account-b", serverId = 12))
        database.interviewDao().insert(Interview(id = 3, ownerId = "account-a", companyId = a, serverId = 101,
            jobTitle = "A", companyName = "A", stage = InterviewStage.APPLIED, applicationDate = LocalDate.of(2026, 1, 1)))
        database.interviewDao().insert(Interview(id = 4, ownerId = "account-b", companyId = b,
            jobTitle = "B pending", companyName = "B", notes = "Unsynced B edit", stage = InterviewStage.APPLIED, applicationDate = LocalDate.of(2026, 1, 1)))
        database.clearAccountCache("account-a", clearGuests = false)
        assertNull(database.interviewDao().getInterviewById(3, "account-a"))
        assertEquals("Unsynced B edit", database.interviewDao().getInterviewById(4, "account-b")!!.notes)
        assertNotNull(database.companyDao().getCompanyById(b, "account-b"))
        assertEquals("Recoverable local notes", database.interviewDao().localOnly(AccountOwners.LEGACY).single().notes)
        assertNull(database.interviewDao().getLegacyByServerId(100)) // disposable old account cache is removed
    }

    @Test fun offlineRestoreStaysInGuestPartitionAndCanBeOpenedAfterRelaunch() = runBlocking {
        val guest = MutableStateFlow(AccountOwners.GUEST)
        val repository = InterviewRepository(database.interviewDao(), database.companyDao(), owner = guest,
            transaction = { block -> database.withTransaction { block() } })
        repository.importLocal(AccountOwners.GUEST, restoreLegacyOnly = true)
        assertEquals("Old local role", database.interviewDao().getInterviewById(2)!!.jobTitle)
        assertNotNull(database.interviewDao().getLegacyByServerId(100))
        database.close()
        database = Room.databaseBuilder(context, InterviewDatabase::class.java, name).build()
        assertEquals("Recoverable local notes", database.interviewDao().getInterviewById(2)!!.notes)
        assertTrue(database.interviewDao().getAllInterviews("account-a").first().isEmpty())
    }
}
