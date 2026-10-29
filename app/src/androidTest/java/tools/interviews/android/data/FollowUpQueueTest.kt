package tools.interviews.android.data

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.*
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import tools.interviews.android.data.account.AccountOperationGate
import tools.interviews.android.data.api.*

@RunWith(AndroidJUnit4::class)
class FollowUpQueueTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val name = "follow-ups-${UUID.randomUUID()}"
    private lateinit var db: InterviewDatabase
    private val gate = AccountOperationGate()
    private var owner = "a"
    private val due = "2026-01-01T00:00:00.000Z"
    private val remote = Remote()

    @Before
    fun setup() {
        open()
    }

    private fun open() {
        db =
            Room.databaseBuilder(context, InterviewDatabase::class.java, name)
                .addMigrations(*InterviewDatabase.ALL_MIGRATIONS)
                .build()
    }

    @After
    fun close() {
        db.close()
        context.deleteDatabase(name)
    }

    private fun queue() =
        FollowUpQueue(
            db.toolDraftDao(),
            gate,
            remote,
            { owner },
            {},
            { block -> db.withTransaction { block() } },
        )

    private inner class Remote : FollowUpWrites {
        val records = mutableMapOf<String, JsonObject>()
        val attempts = mutableListOf<String>()
        var loseResponse = false
        var failedApplication: Int? = null
        var switchOwner = false

        override suspend fun recordFollowUp(body: JsonObject): JsonObject {
            val mutation = body.get("clientMutationId").asString
            attempts += mutation
            val id = body.get("applicationId").asInt
            if (id == failedApplication) throw IOException("Offline")
            val result =
                records.getOrPut("$owner:$mutation") {
                    JsonObject().apply {
                        addProperty("id", records.size + 1)
                        addProperty("sentAt", "2026-01-02T12:00:00Z")
                    }
                }
            if (switchOwner) owner = "b"
            if (loseResponse) {
                loseResponse = false
                throw IOException("Response lost")
            }
            return result
        }
    }

    @Test
    fun lostResponseSurvivesRestartAndReplaysOneRecordedFollowUp() = runBlocking {
        assertTrue(queue().enqueue("a", 7, due))
        remote.loseResponse = true
        assertEquals(1, queue().push("a"))
        assertTrue(queue().states("a")[7]!!.get("pending").asBoolean)
        db.close()
        open()
        assertEquals(0, queue().push("a"))
        assertEquals(1, remote.records.size)
        assertEquals(1, remote.attempts.distinct().size)
        assertFalse(queue().states("a")[7]!!.get("pending").asBoolean)
        assertFalse(queue().enqueue("a", 7, due))
    }

    @Test
    fun laterDueDatesGetNewIdentitiesButRepeatedClicksCannotDuplicatePendingRecords() =
        runBlocking {
            assertTrue(queue().enqueue("a", 7, due))
            assertFalse(queue().enqueue("a", 7, due))
            queue().push("a")
            assertTrue(queue().enqueue("a", 7, "2026-01-09T00:00:00.000Z"))
            assertTrue(queue().states("a")[7]!!.get("pending").asBoolean)
            queue().push("a")
            assertEquals(2, remote.records.size)
            assertEquals(2, remote.attempts.distinct().size)
        }

    @Test
    fun accountSwitchRetainsTheOriginalQueueWithoutReplayIntoAnotherAccount() = runBlocking {
        queue().enqueue("a", 7, due)
        remote.switchOwner = true
        assertTrue(runCatching { queue().push("a") }.exceptionOrNull() is CancellationException)
        assertTrue(db.toolDraftDao().prefix("a", "pending:").isNotEmpty())
        assertTrue(queue().states("b").isEmpty())
        remote.switchOwner = false
        assertEquals(0, queue().push("b"))
        assertEquals(1, remote.records.size)
    }

    @Test
    fun partialFailuresDoNotBlockUnrelatedApplicationRecords() = runBlocking {
        queue().enqueue("a", 7, due)
        queue().enqueue("a", 8, due)
        remote.failedApplication = 7
        assertEquals(1, queue().push("a"))
        val states = queue().states("a")
        assertTrue(states[7]!!.get("pending").asBoolean)
        assertFalse(states[8]!!.get("pending").asBoolean)
        assertTrue(states[7]!!.has("error"))
        assertEquals(1, remote.records.size)
    }

    @Test
    fun ordinarySyncDoesNotClaimSuccessWhenFollowUpRecordsNeedRetry() = runBlocking {
        val sync =
            SyncService(
                db.interviewDao(),
                db.companyDao(),
                currentAccount = { "a" },
                remoteFactory = { error("Download must wait for the failed queued write") },
                additionalWrites = { throw IOException("Follow-up needs retry") },
            )
        sync.syncAll()
        assertNotNull(sync.syncError.value)
        assertNull(sync.lastSyncDate.value)
    }
}
