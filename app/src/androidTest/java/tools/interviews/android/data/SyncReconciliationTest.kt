package tools.interviews.android.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tools.interviews.android.data.api.APICompany
import tools.interviews.android.data.api.APIInterview
import tools.interviews.android.data.api.APIStage
import tools.interviews.android.data.api.SyncRemote
import tools.interviews.android.data.api.SyncService
import tools.interviews.android.model.Company
import tools.interviews.android.model.Interview
import tools.interviews.android.model.*

@RunWith(AndroidJUnit4::class)
class SyncReconciliationTest {
    private lateinit var db: InterviewDatabase
    private var account = "account-a"
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, InterviewDatabase::class.java).build() }
    @After fun close() { db.close() }
    private suspend fun seed() {
        db.companyDao().insert(Company(id = 1, serverId = 10, ownerId = "account-a", name = "Example"))
        for (id in listOf(1, 999)) db.interviewDao().insert(Interview(id = id.toLong(), serverId = id, ownerId = "account-a", companyId = 1,
            companyName = "Example", jobTitle = "Original $id", stage = InterviewStage.APPLIED, applicationDate = LocalDate.of(2026, 1, 1)))
    }
    private fun service(remote: SyncRemote, failCommit: Boolean = false) = SyncService(db.interviewDao(), db.companyDao(),
        currentAccount = { account }, remoteFactory = { remote }, transaction = { block -> db.withTransaction {
            block()
            if (failCommit) throw IOException("commit failed")
        } })

    @Test fun downloadedGhostedAndFutureStatusesNeverBecomeScheduled() = runBlocking {
        val remote = object : SyncRemote {
            override fun checkSession() {}
            override suspend fun companies() = listOf(APICompany(10, "Example"))
            override suspend fun interviews(take: Int, skip: Int) = listOf(
                row(1).copy(stage = APIStage(2, "First stage"), date = "2099-06-01T09:00:00Z", outcome = "NO_RESPONSE"),
                row(2).copy(stage = APIStage(99, "Future panel"), outcome = "PAUSED_BY_EMPLOYER"))
            override suspend fun interview(id: Int): APIInterview? = null
        }
        val sync = service(remote)
        sync.syncAll()
        assertNull(sync.syncError.value)
        val ghosted = db.interviewDao().getInterviewByServerId(1, account)!!
        assertEquals(InterviewOutcome.NO_RESPONSE, ghosted.outcome)
        assertFalse(ghosted.isUpcomingRecord)
        val future = db.interviewDao().getInterviewByServerId(2, account)!!
        assertEquals("Future panel", future.stageLabel)
        assertEquals("PAUSED_BY_EMPLOYER", future.outcomeLabel)
        assertFalse(future.isUpcomingRecord)
        assertNull(tools.interviews.android.data.api.InterviewRequests.update(future).stage)
        assertNull(tools.interviews.android.data.api.InterviewRequests.update(future).outcome)
    }

    @Test fun downloadedMeetingRetainsItsInstantInRoom() = runBlocking {
        val remote = object : SyncRemote {
            override fun checkSession() {}
            override suspend fun companies() = listOf(APICompany(10, "Example"))
            override suspend fun interviews(take: Int, skip: Int) = listOf(row(1).copy(
                stage = APIStage(2, "First stage"), date = "2026-06-01T10:00:00.123+01:00"))
            override suspend fun interview(id: Int): APIInterview? = null
        }
        val sync = service(remote)
        sync.syncAll()
        assertNull(sync.syncError.value)
        val downloaded = db.interviewDao().getInterviewByServerId(1, account)!!
        assertEquals(java.time.Instant.parse("2026-06-01T09:00:00.123Z"), downloaded.interviewInstant)
        assertNull(downloaded.interviewDate)
    }
    @Test fun offerDatesRemainCalendarDaysWithoutBecomingMeetings() = runBlocking {
        val remote = object : SyncRemote {
            override fun checkSession() {}
            override suspend fun companies() = listOf(APICompany(10, "Example"))
            override suspend fun interviews(take: Int, skip: Int) = listOf(row(1).copy(
                stage = APIStage(3, "Offer"), date = "2026-06-01T00:00:00Z", deadline = "2026-06-01T00:00:00Z"))
            override suspend fun interview(id: Int): APIInterview? = null
        }
        val sync = service(remote)
        sync.syncAll()
        assertNull(sync.syncError.value)
        val downloaded = db.interviewDao().getInterviewByServerId(1, account)!!
        assertEquals(LocalDate.of(2026, 6, 1), downloaded.offerRespondBy)
        assertNull(downloaded.interviewInstant)
        assertNull(downloaded.interviewDate)
    }

    @Test fun verified404RemovesOnlyTheMissingRecordAfterAllPagesSucceed() = runBlocking {
        seed()
        val service = service(Remote())
        service.syncAll()
        assertNull(service.syncError.value)
        assertNotNull(service.lastSyncDate.value)
        assertEquals("Updated 1", db.interviewDao().getInterviewById(1, "account-a")!!.jobTitle)
        assertNull(db.interviewDao().getInterviewById(999, "account-a"))
    }
    @Test fun failedSecondPagePreservesTheEntirePreviousCache() = runBlocking {
        seed()
        val remote = Remote().apply { failPage = true }
        val service = service(remote)
        service.syncAll()
        assertNotNull(service.syncError.value)
        assertNull(service.lastSyncDate.value)
        assertEquals("Original 1", db.interviewDao().getInterviewById(1, "account-a")!!.jobTitle)
        assertNotNull(db.interviewDao().getInterviewById(999, "account-a"))
    }
    @Test fun failedTransactionRollsBackUpdatesAndVerifiedDeletionsTogether() = runBlocking {
        seed()
        val service = service(Remote(), failCommit = true)
        service.syncAll()
        assertNotNull(service.syncError.value)
        assertEquals("Original 1", db.interviewDao().getInterviewById(1, "account-a")!!.jobTitle)
        assertNotNull(db.interviewDao().getInterviewById(999, "account-a"))
    }
    @Test fun accountChangeDuringDownloadLeavesOldAccountDataUntouched() = runBlocking {
        seed()
        val remote = Remote().apply { switchAccount = true }
        val service = service(remote)
        assertTrue(runCatching { service.syncAll() }.exceptionOrNull() is CancellationException)
        assertFalse(service.isSyncing.value)
        assertNull(service.lastSyncDate.value)
        assertEquals("Original 1", db.interviewDao().getInterviewById(1, "account-a")!!.jobTitle)
        assertNotNull(db.interviewDao().getInterviewById(999, "account-a"))
    }
    private inner class Remote : SyncRemote {
        var failPage = false
        var switchAccount = false
        override fun checkSession() {}
        override suspend fun companies() = listOf(APICompany(10, "Example"))
        override suspend fun interviews(take: Int, skip: Int): List<APIInterview> {
            if (switchAccount) account = "account-b"
            if (failPage && skip > 0) throw IOException("page failed")
            return if (failPage) (1..take).map(::row) else listOf(row(1))
        }
        override suspend fun interview(id: Int): APIInterview? = null
    }
    private fun row(id: Int) = APIInterview(id = id, jobTitle = "Updated $id", company = APICompany(10, "Example"),
        applicationDate = "2026-01-01T00:00:00Z", stage = APIStage(1, "Applied"), stageMethod = null,
        date = null, deadline = null, outcome = "AWAITING_RESPONSE", interviewer = null,
        clientCompany = null, notes = null, metadata = null, link = null)
}
