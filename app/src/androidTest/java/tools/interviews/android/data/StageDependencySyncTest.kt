package tools.interviews.android.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import tools.interviews.android.data.api.*
import tools.interviews.android.model.*
import java.io.IOException
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class StageDependencySyncTest {
    private lateinit var db: InterviewDatabase
    private val calls = mutableListOf<CreateInterviewRequest>()
    private var failParent = false
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, InterviewDatabase::class.java).build() }
    @After fun close() { db.close() }
    private fun draft(id: Long, owner: String = "account-a") = Interview(id = id, ownerId = owner,
        jobTitle = "Engineer", companyName = "Example", stage = InterviewStage.APPLIED,
        applicationDate = LocalDate.of(2026, 1, 1), outcome = InterviewOutcome.AWAITING_RESPONSE)
    private val writes = object : SyncWrites {
        override suspend fun createInterview(request: CreateInterviewRequest): APIInterview {
            calls += request
            if (failParent) throw IOException("Offline")
            return APIInterview(id = 100 + calls.size, jobTitle = request.jobTitle, interviewer = null,
                company = APICompany(10, request.companyName), clientCompany = null,
                stage = APIStage(1, request.stage), stageMethod = APIStageMethod(request.stageMethodId ?: 1, "Teams"),
                applicationDate = "2026-01-01", date = request.date, deadline = request.deadline,
                outcome = "AWAITING_RESPONSE", notes = request.notes, metadata = null, link = request.interviewLink,
                applicationId = 77, parentId = request.previousInterviewId, durationMinutes = request.durationMinutes)
        }
        override suspend fun updateInterview(id: Int, request: UpdateInterviewRequest): APIInterview = error("Unexpected update")
    }
    private fun service() = SyncService(db.interviewDao(), db.companyDao(), writes = writes, currentAccount = { "account-a" })

    @Test fun unsyncedParentUploadsFirstAndChildJoinsItsApplication() = runBlocking {
        db.interviewDao().insert(draft(1))
        val child = draft(2).copy(previousLocalId = 1, durationMinutes = 45, serverMethodId = 20)
        db.interviewDao().insert(child)
        service().pushInterview(child)
        assertEquals(2, calls.size)
        assertNull(calls[0].previousInterviewId)
        assertEquals(101, calls[1].previousInterviewId)
        assertEquals(45, calls[1].durationMinutes)
        assertEquals(20, calls[1].stageMethodId)
        val saved = db.interviewDao().getInterviewById(2, "account-a")!!
        assertEquals(77, saved.applicationId)
        assertEquals(101, saved.parentId)
        assertEquals(102, saved.serverId)
        service().pushInterview(saved)
        assertEquals("Acknowledged identity prevents a second create", 2, calls.size)
    }
    @Test fun existingParentSendsItsServerIdWithoutCreatingAnotherParent() = runBlocking {
        db.interviewDao().insert(draft(1).copy(serverId = 900, applicationId = 77))
        val child = draft(2).copy(previousLocalId = 1)
        db.interviewDao().insert(child)
        service().pushInterview(child)
        assertEquals(1, calls.size)
        assertEquals(900, calls.single().previousInterviewId)
    }
    @Test fun failedParentNeverUploadsChildAsANewApplication() = runBlocking {
        db.interviewDao().insert(draft(1))
        val child = draft(2).copy(previousLocalId = 1)
        db.interviewDao().insert(child)
        failParent = true
        assertTrue(runCatching { service().pushInterview(child) }.isFailure)
        assertEquals(1, calls.size)
        assertNull(db.interviewDao().getInterviewById(2, "account-a")!!.serverId)
    }
    @Test fun failedParentIsNotRetriedAgainThroughItsChildInTheSameBatch() = runBlocking {
        db.interviewDao().insert(draft(1))
        db.interviewDao().insert(draft(2).copy(previousLocalId = 1))
        failParent = true
        val sync = service()
        sync.syncAll()
        assertEquals(1, calls.size)
        assertNotNull(sync.syncError.value)
        assertNull(sync.lastSyncDate.value)
        assertNull(db.interviewDao().getInterviewById(2, "account-a")!!.serverId)
    }

    @Test fun parentFromAnotherAccountNeverReplaysIntoCurrentAccount() = runBlocking {
        db.interviewDao().insert(draft(1, "account-b"))
        val child = draft(2).copy(previousLocalId = 1)
        db.interviewDao().insert(child)
        assertTrue(runCatching { service().pushInterview(child) }.isFailure)
        assertTrue(calls.isEmpty())
    }
    @Test fun corruptParentCyclesFailBeforeAnyNetworkWrite() = runBlocking {
        val child = draft(1).copy(previousLocalId = 1)
        db.interviewDao().insert(child)
        assertTrue(runCatching { service().pushInterview(child) }.isFailure)
        assertTrue(calls.isEmpty())
    }
}
