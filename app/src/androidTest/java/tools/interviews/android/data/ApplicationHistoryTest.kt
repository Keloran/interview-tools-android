package tools.interviews.android.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import tools.interviews.android.data.account.AccountOperationGate
import tools.interviews.android.data.api.*
import java.time.LocalDate
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ApplicationHistoryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val name = "history-${UUID.randomUUID()}"
    private lateinit var db: InterviewDatabase
    private var owner = "account-a"
    private var replies = 0
    private var nextReply: String? = null
    private var duringRequest: () -> Unit = {}
    private val json = """{"id":7,"applicationDate":"2026-01-01T00:00:00Z","firstResponseAt":null,"interviews":[],"followUps":[],"availabilityLinks":[]}"""
    private val remote = object : ApplicationTimelineRemote {
        override suspend fun timeline(id: Int): JsonObject { duringRequest(); return JsonParser.parseString(json).asJsonObject }
        override suspend fun firstReply(id: Int, body: JsonObject): JsonObject {
            replies++
            nextReply = body.get("firstResponseAt").takeUnless { it.isJsonNull }?.asString
            duringRequest()
            return body
        }
    }
    private fun open() { db = Room.databaseBuilder(context, InterviewDatabase::class.java, name).addMigrations(*InterviewDatabase.ALL_MIGRATIONS).build() }
    private fun history() = ApplicationHistory(db.applicationTimelineDao(), remote, AccountOperationGate(), { owner }, {})
    @Before fun setup() { open() }
    @After fun cleanup() { db.close(); context.deleteDatabase(name) }

    @Test fun timelineSurvivesRestartAndIsPartitionedAndClearedWithAccount() = runBlocking {
        history().refresh(owner, 7)
        db.close(); open()
        assertNotNull(history().cached(owner, 7))
        owner = "account-b"
        assertNull(history().cached(owner, 7))
        history().refresh(owner, 7)
        db.clearAccountCache("account-a", false)
        assertNull(db.applicationTimelineDao().get("account-a", 7))
        assertNotNull(db.applicationTimelineDao().get("account-b", 7))
    }
    @Test fun accountSwitchCannotCacheAnOldResponseOrConfirmAnOldWrite() = runBlocking {
        duringRequest = { owner = "account-b" }
        try { history().refresh("account-a", 7); fail("Old response accepted") } catch (_: CancellationException) { }
        assertNull(db.applicationTimelineDao().get("account-a", 7))
        assertNull(db.applicationTimelineDao().get("account-b", 7))
        owner = "account-a"
        duringRequest = {}
        history().refresh(owner, 7)
        duringRequest = { owner = "account-b" }
        try { history().saveFirstReply("account-a", 7, LocalDate.of(2026,1,2), LocalDate.of(2026,1,1)); fail("Old write confirmed") } catch (_: CancellationException) { }
        owner = "account-a"
        assertNull(history().cached(owner, 7)!!.firstResponseAt)
    }
    @Test fun explicitReplyIsCachedAndCanBeClearedWithoutEstimates() = runBlocking {
        history().refresh(owner, 7)
        val first = LocalDate.of(2026,1,2)
        assertEquals(first.toString(), history().saveFirstReply(owner, 7, first, LocalDate.of(2026,1,1)))
        assertEquals(first.toString(), nextReply)
        db.close(); open()
        assertEquals(first.toString(), history().cached(owner, 7)!!.firstResponseAt)
        assertNull(history().saveFirstReply(owner, 7, null, LocalDate.of(2026,1,1)))
        assertNull(nextReply)
        assertNull(history().cached(owner, 7)!!.firstResponseAt)
    }
    @Test fun invalidReplyDatesNeverReachNetwork() = runBlocking {
        for (date in listOf(LocalDate.of(2025,12,31), LocalDate.of(2026,2,1))) {
            try { history().saveFirstReply(owner, 7, date, LocalDate.of(2026,1,1), LocalDate.of(2026,1,20)); fail("Invalid date saved") } catch (_: IllegalArgumentException) { }
        }
        assertEquals(0, replies)
    }
}
